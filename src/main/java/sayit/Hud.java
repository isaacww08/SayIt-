package sayit;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public final class Hud {
    private static final int GOLD = 0xFFFFD83D, GREY = 0xFFAAAAAA, WHITE = 0xFFFFFFFF;

    public static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        TextRenderer tr = mc.textRenderer;
        ChallengeManager cm = SayIt.challenges;
        long now = System.currentTimeMillis();

        int x = 6, y = 6, w = 156, h = 42;

        if (cm.flash != null && now < cm.flash.until) {
            ChallengeManager.Flash f = cm.flash;
            panel(ctx, x, y, w, 30, f.color);
            ctx.drawItem(f.icon, x + 8, y + 7);
            ctx.drawText(tr, f.title, x + 30, y + 6, f.color, true);
            ctx.drawText(tr, f.sub, x + 30, y + 17, WHITE, true);
            if (f.splash) splash(ctx, tr, f, now, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
        } else if (cm.active != null) {
            ChallengeManager.Active a = cm.active;
            panel(ctx, x, y, w, h, GOLD);
            ctx.drawItem(a.icon, x + 8, y + 12);

            long left = Math.max(0, a.endMs - now);
            long total = a.endMs - a.startMs;
            String langName = Language.byCode(Config.lang).name().toUpperCase();
            String verb = a.ev.kind == ChallengeManager.Kind.PICKUP ? "PICKED UP" : "DROPPED";
            ctx.drawText(tr, "SAY IT IN " + langName, x + 30, y + 5, GREY, false);
            String t = (int) Math.ceil(left / 1000.0) + "s";
            ctx.drawText(tr, t, x + w - 6 - tr.getWidth(t), y + 5, GREY, false);

            String word = Config.showWord ? a.word : a.icon.getName().getString();
            ctx.drawText(tr, word, x + 30, y + 16, GOLD, true);

            int bx = x + 30, bw = w - 38;
            ctx.fill(bx, y + 29, bx + bw, y + 32, 0xFF333333);
            ctx.fill(bx, y + 29, bx + (int) (bw * (left / (double) total)), y + 32, GOLD);
            ctx.drawText(tr, verb + (a.ev.amount > 1 ? " x" + a.ev.amount : "") + "  -  listening...", x + 30, y + 33, GREY, false);

            if (!cm.lastHeard.isBlank()) {
                String heard = "THE MOD HEARD: \"" + cm.lastHeard + "\" " + cm.lastPct + "%";
                ctx.drawText(tr, heard, x, y + h + 4, WHITE, true);
            }
        }

        // bottom-left mic / queue indicator
        int sh = mc.getWindow().getScaledHeight();
        String dl = ModelDownloader.status;
        if (dl != null) {
            double pr = ModelDownloader.progress;
            String line = dl + (pr >= 0 ? "  " + (int) (pr * 100) + "%" : "...");
            ctx.drawText(tr, line, 3, sh - 20, 0xFFFFD83D, true);
        }
        boolean on = SayIt.speech != null && SayIt.speech.isRunning();
        boolean loading = SayIt.speech != null && SayIt.speech.loading;
        String status = loading ? "loading model..." : (on ? "mic on" : "no speech model for " + Config.lang + " - press K");
        if (cm.waiting() > 0) status = cm.waiting() + " waiting  |  " + status;
        if (Config.enabled) ctx.drawText(tr, status, 3, sh - 10, on ? 0xFF55FF55 : (loading ? 0xFFFFD83D : 0xFFFF5555), true);
    }

    private static final int[][] OUTLINE = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{-1,-1},{1,-1},{-1,1}};

    /** Big green "UNLOCKED" with the item icon and the word underneath. Fades out at the end. */
    private static void splash(DrawContext ctx, TextRenderer tr, ChallengeManager.Flash f, long now, int sw, int sh) {
        long left = f.until - now;
        int a = (int) (255 * Math.min(1.0, left / 400.0));
        if (a < 8) return;
        int green = (a << 24) | 0x55FF55, dark = (a << 24) | 0x0B2A0B, white = (a << 24) | 0xFFFFFF;

        String t = "UNLOCKED";
        float s = Math.min(4f, (sw - 100f) / tr.getWidth(t));
        float tw = tr.getWidth(t) * s, th = 8 * s;
        float iconS = 2f, iconW = 16 * iconS;
        float x0 = (sw - (iconW + 8 + tw)) / 2f, y0 = sh * 0.26f;

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x0, y0 + (th - 16 * iconS) / 2f);
        ctx.getMatrices().scale(iconS, iconS);
        ctx.drawItem(f.icon, 0, 0);
        ctx.getMatrices().popMatrix();

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x0 + iconW + 8, y0);
        ctx.getMatrices().scale(s, s);
        for (int[] o : OUTLINE) ctx.drawText(tr, t, o[0], o[1], dark, false);
        ctx.drawText(tr, t, 0, 0, green, false);
        ctx.getMatrices().popMatrix();

        float ws = 2f;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((sw - tr.getWidth(f.title) * ws) / 2f, y0 + th + 8);
        ctx.getMatrices().scale(ws, ws);
        ctx.drawText(tr, f.title, 0, 0, white, true);
        ctx.getMatrices().popMatrix();
    }

    private static void panel(DrawContext ctx, int x, int y, int w, int h, int accent) {
        ctx.fill(x, y, x + w, y + h, 0xCC101010);
        ctx.fill(x, y, x + 2, y + h, accent);
    }
}
