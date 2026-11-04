package sayit;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class SayIt implements ClientModInitializer {
    public static final String ID = "sayit";
    public static final Logger LOG = LoggerFactory.getLogger(ID);

    /** Seconds you get after every pickup / drop. */
    public static final int CHALLENGE_SECONDS = 15;
    /** 0..1 – how close the heard word must be to the target. */
    public static final double MATCH_THRESHOLD = 0.80;

    public static SpeechEngine speech;
    public static ChallengeManager challenges;
    private static KeyBinding menuKey;

    public static Path dir() {
        return FabricLoader.getInstance().getGameDir().resolve("sayit");
    }

    /** .minecraft/sayit/models/<code>/  (also accepts the unzipped folder nested one level down) */
    public static Path modelDir(String code) {
        Path d = dir().resolve("models").resolve(code);
        if (looksLikeModel(d)) return d;
        if (Files.isDirectory(d)) {
            try (Stream<Path> s = Files.list(d)) {
                for (Path sub : (Iterable<Path>) s::iterator)
                    if (looksLikeModel(sub)) return sub;
            } catch (IOException ignored) {}
        }
        return d;
    }

    private static boolean looksLikeModel(Path d) {
        return Files.isDirectory(d.resolve("am")) || Files.isDirectory(d.resolve("conf"));
    }

    public static boolean modelInstalled(String code) {
        return looksLikeModel(modelDir(code));
    }

    /**
     * Switch language: reload words and start the recogniser IF its model is already installed.
     * This never downloads anything. Downloads only happen via downloadAndUse(), after the user consented.
     */
    public static void setLanguage(String code) {
        Config.lang = code;
        Config.save();
        challenges.clear();
        Thread t = new Thread(() -> {
            speech.stop();
            Words.load(code);
            if (!modelInstalled(code)) return;
            if (!Config.lang.equals(code)) return;
            if (!speech.start(modelDir(code))) LOG.warn("No working Vosk model for '{}' in {}", code, modelDir(code));
        }, "sayit-model-load");
        t.setDaemon(true);
        t.start();
    }

    /** Called ONLY from the consent screen's "Download" button. */
    public static void downloadAndUse(Language lang) {
        Thread t = new Thread(() -> {
            if (!ModelDownloader.ensure(lang)) return;
            if (!Config.lang.equals(lang.code())) return;   // user switched language meanwhile
            if (!speech.start(modelDir(lang.code()))) LOG.warn("Model installed but failed to start for '{}'", lang.code());
        }, "sayit-model-download");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void onInitializeClient() {
        Config.load();
        Words.load(Config.lang);
        challenges = new ChallengeManager();
        speech = new SpeechEngine(challenges::onHeard);
        setLanguage(Config.lang);

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(ID, "main"));
        menuKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.sayit.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            ui(mc);
            challenges.tick(mc);
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of(ID, "hud"), Hud::render);
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> speech.stop());
    }

    private static void ui(MinecraftClient mc) {
        while (menuKey.wasPressed()) {
            if (mc.currentScreen == null) mc.setScreen(new LanguageScreen());
        }
        // first launch: pop the menu once
        if (!Config.existed && mc.player != null && mc.currentScreen == null) {
            Config.existed = true;
            Config.save();
            mc.setScreen(new LanguageScreen());
        }
    }
}
