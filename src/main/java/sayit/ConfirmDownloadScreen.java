package sayit;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Nothing is downloaded unless the player clicks "Download" here. */
public class ConfirmDownloadScreen extends Screen {
    private final Screen parent;
    private final Language lang;
    private final List<OrderedText> lines = new ArrayList<>();

    public ConfirmDownloadScreen(Screen parent, Language lang) {
        super(Text.literal("Download speech model?"));
        this.parent = parent;
        this.lang = lang;
    }

    @Override
    protected void init() {
        lines.clear();
        int maxW = Math.min(width - 40, 340);
        String[] paragraphs = {
            "To hear you speak " + lang.name() + ", Say It needs a speech recognition model (about " + lang.approxMb() + " MB).",
            "",
            "Downloads: " + ModelDownloader.url(lang),
            "Saved to: <game folder>/sayit/models/" + lang.code(),
            "",
            "Speech recognition runs on your computer. Your voice is never uploaded.",
            "Nothing is downloaded unless you click Download."
        };
        for (String p : paragraphs) {
            if (p.isEmpty()) { lines.add(OrderedText.EMPTY); continue; }
            lines.addAll(textRenderer.wrapLines(Text.literal(p), maxW));
        }

        int bw = 110, y = Math.min(height - 30, 60 + lines.size() * 11 + 14);
        addDrawableChild(ButtonWidget.builder(Text.literal("Download"), b -> {
            SayIt.downloadAndUse(lang);
            client.setScreen(parent);
        }).dimensions(width / 2 - bw - 4, y, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> client.setScreen(parent))
                .dimensions(width / 2 + 4, y, bw, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 36, 0xFFFFD83D);
        int y = 56;
        for (OrderedText line : lines) {
            ctx.drawTextWithShadow(textRenderer, line, width / 2 - textRenderer.getWidth(line) / 2, y, 0xFFFFFFFF);
            y += 11;
        }
    }

    @Override
    public void close() { client.setScreen(parent); }
}
