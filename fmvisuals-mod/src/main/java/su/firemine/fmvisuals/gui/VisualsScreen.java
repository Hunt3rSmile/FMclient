package su.firemine.fmvisuals.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import su.firemine.fmvisuals.config.VisualsConfig;

public class VisualsScreen extends Screen {

    private static final int W = 310;
    private static final int H = 220;

    private static final int C_BG      = 0xF5030310;
    private static final int C_ACCENT  = 0xFFa855f7;
    private static final int C_ACCENT2 = 0xFF7c3aed;
    private static final int C_TEXT    = 0xFFEEEEFF;
    private static final int C_HINT    = 0xFF55557a;
    private static final int C_DIVIDER = 0xFF141430;

    private static final int BTN_W = 70;
    private static final int BTN_H = 16;
    private static final int ROW_H = 32;
    private static final int ROWS  = 5;
    private static final int HDR_H = 26;
    private static final int PAD   = 14;

    private static final String[] LABELS = {
        "Полная яркость",
        "Тени сущностей",
        "Улучшенная графика",
        "Частицы",
        "Полный экран"
    };
    private static final String[] HINTS = {
        "Максимальная видимость в темноте",
        "Тени под мобами и игроком",
        "Листья, вода, облака, небо",
        "Снег, огонь, дым, взрывы",
        "Переключить полноэкранный режим"
    };

    public VisualsScreen() {
        super(new LiteralText("FMclient Visuals"));
    }

    private int px() { return (width  - W) / 2; }
    private int py() { return (height - H) / 2; }
    private int rowY(int row) { return py() + HDR_H + row * ROW_H; }

    @Override
    protected void init() {
        MinecraftClient mc = client;
        int bx = px() + W - BTN_W - PAD;

        // Row 0 – Fullbright
        addButton(new ToggleButton(bx, rowY(0) + (ROW_H - BTN_H) / 2, BTN_W, BTN_H,
            VisualsConfig.isFullbright(),
            VisualsConfig::setFullbright));

        // Row 1 – Entity shadows
        addButton(new ToggleButton(bx, rowY(1) + (ROW_H - BTN_H) / 2, BTN_W, BTN_H,
            mc.options.entityShadows,
            enabled -> { mc.options.entityShadows = enabled; mc.options.write(); }));

        // Row 2 – Fancy graphics (GraphicsMode.FANCY vs FAST)
        addButton(new ToggleButton(bx, rowY(2) + (ROW_H - BTN_H) / 2, BTN_W, BTN_H,
            mc.options.graphicsMode == GraphicsMode.FANCY,
            enabled -> {
                mc.options.graphicsMode = enabled ? GraphicsMode.FANCY : GraphicsMode.FAST;
                mc.options.write();
            }));

        // Row 3 – Particles cycle
        addButton(new CycleButton(bx, rowY(3) + (ROW_H - BTN_H) / 2, BTN_W, BTN_H, mc));

        // Row 4 – Fullscreen toggle
        addButton(new ToggleButton(bx, rowY(4) + (ROW_H - BTN_H) / 2, BTN_W, BTN_H,
            mc.options.fullscreen,
            enabled -> {
                mc.options.fullscreen = enabled;
                mc.getWindow().toggleFullscreen();
                mc.options.write();
            }));

        // Close button (top-right ×)
        addButton(new ButtonWidget(px() + W - 20, py() + 5, 14, 14,
            new LiteralText("×"), btn -> onClose()) {
            @Override
            public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
                boolean hov = isHovered();
                if (hov) fill(matrices, this.x - 1, this.y - 1,
                              this.x + this.width + 1, this.y + this.height + 1, 0x33FF4444);
                net.minecraft.client.font.TextRenderer tr = MinecraftClient.getInstance().textRenderer;
                Text msg = getMessage();
                int  tw  = tr.getWidth(msg);
                tr.drawWithShadow(matrices, msg,
                    this.x + (this.width - tw) / 2.0f,
                    this.y + (this.height - 8) / 2.0f,
                    hov ? 0xFFFF6666 : 0xFF777788);
            }
        });
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        int x = px(), y = py();

        // Dimmer
        fill(matrices, 0, 0, width, height, 0xAA000000);

        // Panel
        fill(matrices, x, y, x + W, y + H, C_BG);
        fill(matrices, x, y, x + W, y + 2, C_ACCENT);
        fill(matrices, x, y + 2, x + 2, y + H, C_ACCENT2);
        fill(matrices, x + 2, y + 2, x + W, y + HDR_H, 0xFF08081a);

        // Header
        textRenderer.drawWithShadow(matrices, "FMclient Visuals", x + PAD, y + 8, C_ACCENT);
        fill(matrices, x + 2, y + HDR_H, x + W, y + HDR_H + 1, C_DIVIDER);

        // Rows
        for (int i = 0; i < ROWS; i++) {
            int ry = rowY(i);
            boolean rowHov = mouseX >= x + 2 && mouseX < x + W
                          && mouseY >= ry    && mouseY < ry + ROW_H;

            if (rowHov) fill(matrices, x + 2, ry, x + W, ry + ROW_H, 0x0AFFFFFF);
            if (i < ROWS - 1) fill(matrices, x + PAD, ry + ROW_H - 1, x + W - PAD, ry + ROW_H, C_DIVIDER);

            // Accent dot
            fill(matrices, x + PAD, ry + ROW_H / 2 - 2, x + PAD + 3, ry + ROW_H / 2 + 2, C_ACCENT);

            textRenderer.draw(matrices, LABELS[i], x + PAD + 9, ry + 7,  C_TEXT);
            textRenderer.draw(matrices, HINTS[i],  x + PAD + 9, ry + 17, C_HINT);
        }

        String hint = "ESC — закрыть  •  LSHIFT — открыть из игры";
        textRenderer.draw(matrices, hint,
            x + W - textRenderer.getWidth(hint) - PAD,
            y + H - 10, 0xFF333355);

        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public boolean isPauseScreen()     { return false; }
}
