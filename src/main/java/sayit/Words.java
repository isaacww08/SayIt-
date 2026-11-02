package sayit;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * item registry path ("apple") -> word in the active language ("Apfel").
 * Built at runtime from Minecraft's own language file for that language (found via the asset index).
 */
public final class Words {
    private static volatile Map<String, String> map = new HashMap<>();
    private static final Gson GSON = new Gson();
    private static final java.lang.reflect.Type STR_MAP = new TypeToken<Map<String, String>>() {}.getType();

    public static void load(String code) {
        Map<String, String> m = new HashMap<>();

        // 1) tiny bundled fallback (German only) in case the assets can't be found
        try (InputStream in = Words.class.getResourceAsStream("/assets/sayit/words/" + code + ".json")) {
            if (in != null) m.putAll(GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), STR_MAP));
        } catch (Exception e) { SayIt.LOG.error("Bad bundled words for " + code, e); }

        // 2) the real thing: Minecraft's own lang file for this language
        try {
            m.putAll(fromGameAssets(Language.byCode(code).mcLang()));
        } catch (Exception e) {
            SayIt.LOG.warn("Couldn't read game language file for '{}': {}", code, e.toString());
        }

        // 3) optional user overrides: .minecraft/sayit/words/<code>.json
        Path f = SayIt.dir().resolve("words").resolve(code + ".json");
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                Map<String, String> o = GSON.fromJson(r, STR_MAP);
                m.putAll(o);
            } catch (Exception e) { SayIt.LOG.error("Bad words file " + f, e); }
        }
        if (Language.byCode(code).script() != Language.Script.LATIN) {
            Map<String, String> r = new HashMap<>();
            for (var e : m.entrySet()) r.put(e.getKey(), Translit.toLatin(e.getValue()).trim());
            m = r;
        }
        map = m;
        SayIt.LOG.info("Loaded {} words for '{}'", m.size(), code);
    }

    private static Map<String, String> fromGameAssets(String mcLang) throws IOException {
        Path assets = null;
        String index = null;
        String[] args = FabricLoader.getInstance().getLaunchArguments(false);
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--assetsDir")) assets = Path.of(args[i + 1]);
            if (args[i].equals("--assetIndex")) index = args[i + 1];
        }
        if (assets == null) assets = FabricLoader.getInstance().getGameDir().resolve("assets");

        Path indexFile = index != null ? assets.resolve("indexes").resolve(index + ".json") : null;
        if (indexFile == null || !Files.exists(indexFile)) {            // fall back to newest index
            try (Stream<Path> s = Files.list(assets.resolve("indexes"))) {
                indexFile = s.max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                             .orElseThrow(() -> new IOException("no asset index"));
            }
        }

        JsonObject objs = JsonParser.parseString(Files.readString(indexFile)).getAsJsonObject().getAsJsonObject("objects");
        var entry = objs.getAsJsonObject("minecraft/lang/" + mcLang + ".json");
        if (entry == null) throw new IOException(mcLang + ".json not in asset index");
        String hash = entry.get("hash").getAsString();
        Path langFile = assets.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);

        Map<String, String> lang;
        try (Reader r = Files.newBufferedReader(langFile, StandardCharsets.UTF_8)) {
            lang = GSON.fromJson(r, STR_MAP);
        }

        Map<String, String> out = new HashMap<>();
        for (String prefix : new String[]{"block.minecraft.", "item.minecraft."}) {   // items win over blocks
            for (var e : lang.entrySet()) {
                String k = e.getKey();
                if (!k.startsWith(prefix)) continue;
                String name = k.substring(prefix.length());
                if (!name.contains(".") && !e.getValue().contains("%")) out.put(name, e.getValue());
            }
        }
        return out;
    }

    public static boolean has(String id) { return map.containsKey(id); }
    public static String get(String id) { return map.get(id); }
    public static int size() { return map.size(); }
}
