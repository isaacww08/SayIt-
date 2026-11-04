package sayit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Downloads + unpacks the small Vosk model for a language the first time it's selected. */
public final class ModelDownloader {
    /** shown in the HUD while non-null */
    public static volatile String status = null;
    /** 0..1, or -1 when unknown */
    public static volatile double progress = -1;

    private static volatile boolean busy = false;

    public static String url(Language lang) {
        return "https://alphacephei.com/vosk/models/" + lang.voskModel() + ".zip";
    }

    /**
     * Downloads the model. ONLY call this after the user has clicked "Download" on the consent screen.
     */
    public static boolean ensure(Language lang) {
        if (SayIt.modelInstalled(lang.code())) return true;
        if (busy) return false;
        busy = true;
        try { return doEnsure(lang); } finally { busy = false; }
    }

    private static boolean doEnsure(Language lang) {
        Path modelsDir = SayIt.dir().resolve("models");
        Path target = modelsDir.resolve(lang.code());
        Path tmp = modelsDir.resolve(lang.code() + ".zip.part");
        try {
            Files.createDirectories(modelsDir);
            status = "Downloading " + lang.name() + " speech model";
            progress = 0;

            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpRequest req = HttpRequest.newBuilder(
                    URI.create(url(lang))).build();
            HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode());
            long total = resp.headers().firstValueAsLong("content-length").orElse(-1);

            try (InputStream in = resp.body(); OutputStream out = Files.newOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                long done = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    if (total > 0) progress = done / (double) total;
                }
            }

            status = "Unpacking " + lang.name() + " model";
            progress = -1;
            try {
                unzip(tmp, target);
            } catch (IOException e) {
                deleteRecursively(target);
                throw e;
            }
            Files.deleteIfExists(tmp);
            status = null;
            return SayIt.modelInstalled(lang.code());
        } catch (Exception e) {
            SayIt.LOG.error("Model download failed", e);
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            fail("Model download failed: " + e.getMessage());
            return false;
        }
    }

    /** unzips and strips the single top-level folder the Vosk zips contain */
    private static void unzip(Path zip, Path target) throws IOException {
        Path root = target.toAbsolutePath().normalize();
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                String name = e.getName();
                int slash = name.indexOf('/');
                String rel = slash >= 0 ? name.substring(slash + 1) : name;
                if (rel.isEmpty()) continue;
                Path out = root.resolve(rel).normalize();
                if (!out.startsWith(root)) throw new IOException("bad zip entry " + name);   // zip-slip guard
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zin, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteRecursively(Path p) {
        if (!Files.exists(p)) return;
        try (Stream<Path> s = Files.walk(p)) {
            s.sorted(Comparator.reverseOrder()).forEach(x -> { try { Files.delete(x); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }

    private static void fail(String msg) {
        status = msg;
        progress = -1;
        Thread t = new Thread(() -> {
            try { Thread.sleep(8000); } catch (InterruptedException ignored) {}
            if (msg.equals(status)) status = null;
        });
        t.setDaemon(true);
        t.start();
    }
}
