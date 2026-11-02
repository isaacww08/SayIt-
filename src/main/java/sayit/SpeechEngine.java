package sayit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Mic capture (16 kHz mono) -> Vosk (offline) -> partial/final text callbacks. Restartable (language switch). */
public class SpeechEngine {
    public record Heard(String text, boolean fin) {}

    private final Consumer<Heard> listener;
    private Model model;
    private Recognizer rec;
    private TargetDataLine line;
    private Thread thread;
    private volatile boolean running;
    public volatile boolean loading;
    private volatile boolean resetFlag;

    public SpeechEngine(Consumer<Heard> listener) { this.listener = listener; }

    public boolean isRunning() { return running; }
    public void reset() { resetFlag = true; }

    public synchronized boolean start(Path modelDir) {
        stopInternal();
        loading = true;
        try {
            if (!Files.isDirectory(modelDir)) return false;
            LibVosk.setLogLevel(LogLevel.WARNINGS);
            model = new Model(modelDir.toString());
            rec = new Recognizer(model, 16000f);

            AudioFormat fmt = new AudioFormat(16000f, 16, 1, true, false);
            line = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, fmt));
            line.open(fmt);
            line.start();

            running = true;
            Recognizer r = rec; TargetDataLine l = line;
            thread = new Thread(() -> loop(r, l), "sayit-mic");
            thread.setDaemon(true);
            thread.start();
            return true;
        } catch (Throwable t) {
            SayIt.LOG.error("Speech engine failed to start", t);
            running = false;
            return false;
        } finally {
            loading = false;
        }
    }

    private void loop(Recognizer r, TargetDataLine l) {
        byte[] buf = new byte[4096];
        while (running && l == line) {
            int n = l.read(buf, 0, buf.length);
            if (n <= 0) continue;
            if (resetFlag) { r.reset(); resetFlag = false; }
            boolean fin = r.acceptWaveForm(buf, n);
            String json = fin ? r.getResult() : r.getPartialResult();
            try {
                JsonObject o = JsonParser.parseString(json).getAsJsonObject();
                String text = o.has("text") ? o.get("text").getAsString()
                            : o.has("partial") ? o.get("partial").getAsString() : "";
                if (!text.isBlank()) listener.accept(new Heard(text, fin));
            } catch (Exception ignored) {}
        }
    }

    private void stopInternal() {
        running = false;
        try { if (thread != null) thread.join(500); } catch (InterruptedException ignored) {}
        try { if (line != null) { line.stop(); line.close(); } } catch (Exception ignored) {}
        try { if (rec != null) rec.close(); } catch (Exception ignored) {}
        try { if (model != null) model.close(); } catch (Exception ignored) {}
        line = null; rec = null; model = null; thread = null;
    }

    public synchronized void stop() { stopInternal(); }
}
