package sayit;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Every pickup and every drop starts a 15s "say it" challenge.
 * Say the word (in the chosen language) = fine. Don't = you lose the item.
 *  - pickup: that many items are removed from your inventory
 *  - drop:   the dropped item entities nearby are removed from the world
 */
public class ChallengeManager {
    public enum Kind { PICKUP, DROP }

    public static final class Event {
        public String id; public Kind kind; public int amount;
        Event(String id, Kind kind, int amount) { this.id = id; this.kind = kind; this.amount = amount; }
    }
    public static final class Active {
        public Event ev; public String word; public ItemStack icon; public long startMs, endMs;
    }
    public static final class Flash {
        public ItemStack icon; public String title, sub; public int color; public long until; public boolean splash;
    }

    public Active active;
    public Flash flash;
    public String lastHeard = "";
    public int lastPct;
    public int waiting() { return queue.size(); }

    private final Deque<Event> queue = new ArrayDeque<>();
    private final Set<String> unlocked = new HashSet<>();
    private String progressKey = "";
    private final ConcurrentLinkedQueue<SpeechEngine.Heard> heardQ = new ConcurrentLinkedQueue<>();

    private Map<String, Integer> prevCounts = new HashMap<>();
    private String prevMainId = "";
    private boolean prevDropDown;
    private int grace;
    private boolean inWorld;

    /** called from the mic thread */
    public void onHeard(SpeechEngine.Heard h) { heardQ.add(h); }

    public void clear() { queue.clear(); active = null; heardQ.clear(); lastHeard = ""; }

    public void tick(MinecraftClient mc) {
        if (mc.player == null) { inWorld = false; clear(); return; }
        if (!inWorld) { inWorld = true; grace = 60; prevCounts = counts(mc.player); prevMainId = ""; }

        // unlocked items are remembered per world AND per language
        String pk = keyFor(mc) + "_" + Config.lang;
        if (!pk.equals(progressKey)) { progressKey = pk; loadProgress(); queue.clear(); active = null; }

        long now = System.currentTimeMillis();
        Map<String, Integer> counts = counts(mc.player);
        String mainId = idOf(mc.player.getMainHandStack());

        if (!Config.enabled) {
            clear(); prevCounts = counts; prevMainId = mainId; return;
        }

        // ---- detect events -----------------------------------------------------
        if (grace > 0) {
            grace--;                                  // inventory is still syncing after join
        } else {
            for (var e : counts.entrySet()) {
                int delta = e.getValue() - prevCounts.getOrDefault(e.getKey(), 0);
                if (delta > 0) enqueue(e.getKey(), Kind.PICKUP, delta);
            }
            boolean dropDown = mc.options.dropKey.isPressed();
            if (dropDown && !prevDropDown && mc.currentScreen == null && Words.has(prevMainId)) {
                enqueue(prevMainId, Kind.DROP, 1);
            }
            prevDropDown = dropDown;
        }
        prevCounts = counts;
        prevMainId = mainId;

        // ---- speech ---------------------------------------------------------------
        SpeechEngine.Heard h;
        while ((h = heardQ.poll()) != null) {
            if (active == null) continue;
            lastHeard = Translit.toLatin(h.text());
            lastPct = (int) Math.round(TextMatch.best(lastHeard, active.word) * 100);
            if (lastPct >= SayIt.MATCH_THRESHOLD * 100) { succeed(mc); break; }
        }

        // ---- start next / timeout -----------------------------------------------
        boolean flashing = flash != null && now < flash.until;
        if (active == null && !flashing && !queue.isEmpty()) start(queue.poll());
        if (active != null && now >= active.endMs) fail(mc);
    }

    private void enqueue(String id, Kind kind, int amount) {
        if (!Words.has(id) || unlocked.contains(id)) return;   // already unlocked = no challenge
        if (active != null && active.ev.id.equals(id) && active.ev.kind == kind) { active.ev.amount += amount; return; }
        for (Event q : queue) if (q.id.equals(id) && q.kind == kind) { q.amount += amount; return; }
        queue.add(new Event(id, kind, amount));
    }

    private void start(Event ev) {
        Active a = new Active();
        a.ev = ev;
        a.word = Words.get(ev.id);
        a.icon = new ItemStack(Registries.ITEM.get(Identifier.of("minecraft", ev.id)));
        a.startMs = System.currentTimeMillis();
        a.endMs = a.startMs + SayIt.CHALLENGE_SECONDS * 1000L;
        active = a;
        lastHeard = ""; lastPct = 0;
        SayIt.speech.reset();
    }

    private void succeed(MinecraftClient mc) {
        String id = active.ev.id;
        unlocked.add(id);
        saveProgress();
        queue.removeIf(e -> e.id.equals(id));
        flash = flash(active.icon, active.word, "UNLOCKED " + Math.min(100, lastPct) + "%", 0xFF55FF55);
        flash.splash = true;
        flash.until = System.currentTimeMillis() + 2200;
        mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f));
        active = null;
    }

    private void fail(MinecraftClient mc) {
        Event ev = active.ev;
        MinecraftServer server = mc.getServer();
        String sub;
        if (server != null) {
            UUID uuid = mc.player.getUuid();
            if (ev.kind == Kind.PICKUP) {
                int amt = ev.amount;
                server.execute(() -> removeFromInventory(server, uuid, ev.id, amt));
                sub = "LOST " + amt + (amt == 1 ? " ITEM" : " ITEMS");
            } else {
                server.execute(() -> removeDropped(server, uuid, ev.id));
                sub = "DROPPED ITEM LOST";
            }
        } else {
            sub = "CAN'T REMOVE (NO LOCAL WORLD)";
        }
        flash = flash(active.icon, active.word, sub, 0xFFFF5555);
        mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.ENTITY_ITEM_BREAK, 1.0f));
        active = null;
    }

    private Flash flash(ItemStack icon, String title, String sub, int color) {
        Flash f = new Flash();
        f.icon = icon; f.title = title; f.sub = sub; f.color = color;
        f.until = System.currentTimeMillis() + 1800;
        return f;
    }

    // ---- unlock persistence ------------------------------------------------
    public int unlockedCount() { return unlocked.size(); }

    public void resetUnlocked() { unlocked.clear(); saveProgress(); }

    private static String keyFor(MinecraftClient mc) {
        String k = "unknown";
        if (mc.getServer() != null) k = mc.getServer().getSaveProperties().getLevelName();
        else if (mc.getCurrentServerEntry() != null) k = mc.getCurrentServerEntry().address;
        return k.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }

    private Path progressFile() { return SayIt.dir().resolve("progress_" + progressKey + ".json"); }

    private void loadProgress() {
        unlocked.clear();
        try {
            if (Files.exists(progressFile())) {
                JsonObject o = JsonParser.parseString(Files.readString(progressFile())).getAsJsonObject();
                o.getAsJsonArray("unlocked").forEach(e -> unlocked.add(e.getAsString()));
            }
        } catch (Exception e) { SayIt.LOG.warn("Couldn't read progress", e); }
    }

    private void saveProgress() {
        try {
            Files.createDirectories(SayIt.dir());
            JsonObject o = new JsonObject();
            JsonArray arr = new JsonArray();
            unlocked.stream().sorted().forEach(arr::add);
            o.add("unlocked", arr);
            Files.writeString(progressFile(), new Gson().toJson(o));
        } catch (Exception e) { SayIt.LOG.warn("Couldn't save progress", e); }
    }

    // ---- inventory helpers -------------------------------------------------
    private static String idOf(ItemStack s) {
        return s.isEmpty() ? "" : Registries.ITEM.getId(s.getItem()).getPath();
    }

    private static Map<String, Integer> counts(PlayerEntity p) {
        Map<String, Integer> m = new HashMap<>();
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) addCount(m, inv.getStack(i));
        addCount(m, p.currentScreenHandler.getCursorStack());
        return m;
    }
    private static void addCount(Map<String, Integer> m, ItemStack s) {
        String id = idOf(s);
        if (!id.isEmpty() && Words.has(id)) m.merge(id, s.getCount(), Integer::sum);
    }

    private static void removeFromInventory(MinecraftServer server, UUID uuid, String id, int amount) {
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
        if (p == null) return;
        int left = amount;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size() && left > 0; i++) {
            ItemStack s = inv.getStack(i);
            if (idOf(s).equals(id)) {
                int take = Math.min(left, s.getCount());
                s.decrement(take);
                left -= take;
            }
        }
        ScreenHandler h = p.currentScreenHandler;
        ItemStack cur = h.getCursorStack();
        if (left > 0 && idOf(cur).equals(id)) cur.decrement(Math.min(left, cur.getCount()));
        p.playerScreenHandler.sendContentUpdates();
        h.sendContentUpdates();
    }

    private static void removeDropped(MinecraftServer server, UUID uuid, String id) {
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
        if (p == null) return;
        Box box = new Box(p.getX() - 12, p.getY() - 12, p.getZ() - 12, p.getX() + 12, p.getY() + 12, p.getZ() + 12);
        for (ServerWorld w : server.getWorlds()) {
            if (w.getPlayerByUuid(uuid) == null) continue;      // only the world the player is in
            for (ItemEntity e : w.getEntitiesByClass(ItemEntity.class, box, ie -> idOf(ie.getStack()).equals(id))) {
                e.discard();
            }
        }
    }
}
