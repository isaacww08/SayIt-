package sayit;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class LanguageScreen extends Screen {
    private int titleY;
    private String stateAtInit = "";

    public LanguageScreen() { super(Text.literal("Say It - Choose a language")); }

    /** changes whenever a model finishes installing / a download starts or ends -> triggers a rebuild */
    private static String stateKey() {
        StringBuilder sb = new StringBuilder(Config.lang).append('|')
                .append(ModelDownloader.status != null).append('|');
        for (Language l : Language.ALL) sb.append(SayIt.modelInstalled(l.code()) ? '1' : '0');
        return sb.toString();
    }

    @Override
    protected void init() {
        stateAtInit = stateKey();

        int cols = 2, bw = 150, bh = 20, gap = 4;
        int rows = (Language.ALL.size() + cols - 1) / cols;
        int totalH = rows * (bh + gap) + 8 + 2 * (bh + gap) + 6 + bh;
        int x0 = width / 2 - (bw * cols + gap) / 2;
        int y0 = Math.max(26, (height - totalH) / 2);
        titleY = y0 - 16;

        for (int i = 0; i < Language.ALL.size(); i++) {
            Language lang = Language.ALL.get(i);
            boolean sel = lang.code().equals(Config.lang);
            String suffix;
            if (SayIt.modelInstalled(lang.code())) suffix = "";
            else if (sel && ModelDownloader.status != null) suffix = "  (downloading...)";
            else suffix = "  (download)";
            String label = (sel ? "> " : "") + lang.name() + suffix;
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
                SayIt.setLanguage(lang.code());
                clearAndInit();
            }).dimensions(x0 + (i % cols) * (bw + gap), y0 + (i / cols) * (bh + gap), bw, bh).build());
        }

        int y = y0 + rows * (bh + gap) + 8;
        int full = bw * cols + gap;

        addDrawableChild(ButtonWidget.builder(Text.literal("Challenges: " + (Config.enabled ? "ON" : "OFF")), b -> {
            Config.enabled = !Config.enabled; Config.save(); clearAndInit();
        }).dimensions(x0, y, full, bh).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Show word: " + (Config.showWord ? "YES" : "NO (hard mode)")), b -> {
            Config.showWord = !Config.showWord; Config.save(); clearAndInit();
        }).dimensions(x0, y + bh + gap, full, bh).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(x0, y + 2 * (bh + gap) + 6, full, bh).build());
    }

    @Override
    public void tick() {
        super.tick();
        if (!stateKey().equals(stateAtInit)) clearAndInit();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, titleY, 0xFFFFFFFF);
        String hint = Words.size() + " words loaded";
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(hint), width / 2, height - 14, 0xFFAAAAAA);
    }
}
