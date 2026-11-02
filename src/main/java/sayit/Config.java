package sayit;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Config {
    public static String lang = "de";
    public static boolean showWord = true;   // show target word (true) or the English item name (false = hard mode)
    public static boolean enabled = true;
    /** false on the very first launch -> menu opens automatically */
    public static boolean existed = false;

    private static Path file() { return SayIt.dir().resolve("config.json"); }

    public static void load() {
        try {
            if (Files.exists(file())) {
                JsonObject o = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
                if (o.has("lang")) lang = o.get("lang").getAsString();
                if (o.has("showWord")) showWord = o.get("showWord").getAsBoolean();
                if (o.has("enabled")) enabled = o.get("enabled").getAsBoolean();
                existed = true;
            }
        } catch (Exception e) { SayIt.LOG.warn("Bad config", e); }
    }

    public static void save() {
        try {
            Files.createDirectories(SayIt.dir());
            JsonObject o = new JsonObject();
            o.addProperty("lang", lang);
            o.addProperty("showWord", showWord);
            o.addProperty("enabled", enabled);
            Files.writeString(file(), new Gson().toJson(o));
        } catch (Exception e) { SayIt.LOG.warn("Couldn't save config", e); }
    }
}
