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
    private static final int W = 372;
    private static final int H = 268;

    private static final int C_DIM = 0xC9000000;
    private static final int C_PANEL = 0xF1121519;
    private static final int C_PANEL_INNER = 0xF1181C21;
    private static final int C_STROKE = 0xFF2D343C;
    private static final int C_ACCENT = 0xFFE4E8EC;
    private static final int C_TEXT = 0xFFF1F3F5;
    private static final int C_TEXT_MUTED = 0xFF98A1AA;
    private static final int C_TEXT_FAINT = 0xFF6C757D;
    private static final int C_ROW = 0xD3161A1F;
    private static final int C_ROW_HOVER = 0xE01D232A;
    private static final int C_PILL = 0xFF20262D;

    private static final int BTN_W = 82;
    private static final int BTN_H = 18;
    private static final int ROW_H = 38;
    private static final int ROWS  = 5;
    private static final int HDR_H = 54;
    private static final int PAD   = 20;

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

        addButton(new ToggleButton(bx, rowY(0) + (ROW_H - BTN_H) / 2, BTN_W, BTN_H,
            VisualsConfig.isFullbright(),
            VisualsConfig::setFullbright));

        addButton(new ToggleButton(bx, rowY(1) + (ROW_H - BTN_H) / 2, BTN_W, BTN_H,
            mc.options.entityShadows,
            enabled -> { mc.options.entityShadows = enabled; mc.options.write(); }));

        addButton(new ToggleButton(bx, rowY(2) + (ROW_H - BTN_H) / 2, BTN_W, BTN_H,
            mc.options.graphicsMode == GraphicsMode.FANCY,
            enabled -> {
                mc.options.graphicsMode = enabled ? GraphicsMode.FANCY : GraphicsMode.FAST;
                mc.options.write();
            }));

        addButton(new CycleButton(bx, rowY(3) + (ROW_H - BTN_H) / 2, BTN_W, BTN_H, mc));

        addButton(new ToggleButton(bx, rowY(4) + (ROW_H - BTN_H) / 2, BTN_W, BTN_H,
            mc.options.fullscreen,
            enabled -> {
                mc.options.fullscreen = enabled;
                mc.getWindow().toggleFullscreen();
                mc.options.write();
            }));

        addButton(new ButtonWidget(px() + W - 22, py() + 9, 14, 14,
            new LiteralText("×"), btn -> onClose()) {
            @Override
            public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
                boolean hov = isHovered();
                fill(matrices, this.x - 2, this.y - 2, this.x + this.width + 2, this.y + this.height + 2,
                    hov ? 0x2A242A31 : 0x160F1419);
                net.minecraft.client.font.TextRenderer tr = MinecraftClient.getInstance().textRenderer;
                Text msg = getMessage();
                int tw = tr.getWidth(msg);
                tr.drawWithShadow(matrices, msg,
                    this.x + (this.width - tw) / 2.0f,
                    this.y + (this.height - 8) / 2.0f,
                    hov ? 0xFFF4F6F8 : 0xFF7B858E);
            }
        });
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        int x = px(), y = py();

        fill(matrices, 0, 0, width, height, C_DIM);
        fill(matrices, x - 6, y - 6, x + W + 6, y + H + 6, 0x10000000);
        fill(matrices, x, y, x + W, y + H, C_PANEL);
        fill(matrices, x + 1, y + 1, x + W - 1, y + H - 1, C_PANEL_INNER);
        fill(matrices, x, y, x + W, y + 1, C_STROKE);
        fill(matrices, x, y + H - 1, x + W, y + H, C_STROKE);
        fill(matrices, x, y, x + 1, y + H, C_STROKE);
        fill(matrices, x + W - 1, y, x + W, y + H, C_STROKE);
        fill(matrices, x + PAD, y + 14, x + PAD + 62, y + 24, C_PILL);
        textRenderer.draw(matrices, "PROFILE", x + PAD + 9, y + 16, C_TEXT_MUTED);
        textRenderer.drawWithShadow(matrices, "Визуалы", x + PAD, y + 34, C_TEXT);
        String subtitle = "Чистый и спокойный интерфейс графики";
        textRenderer.draw(matrices, subtitle, x + PAD, y + 46, C_TEXT_MUTED);
        fill(matrices, x + PAD, y + HDR_H, x + W - PAD, y + HDR_H + 1, C_STROKE);

        for (int i = 0; i < ROWS; i++) {
            int ry = rowY(i);
            boolean rowHov = mouseX >= x + 8 && mouseX < x + W - 8
                && mouseY >= ry && mouseY < ry + ROW_H - 3;

            fill(matrices, x + 8, ry, x + W - 8, ry + ROW_H - 3, rowHov ? C_ROW_HOVER : C_ROW);
            fill(matrices, x + 8, ry, x + W - 8, ry + 1, 0x16FFFFFF);
            fill(matrices, x + 8, ry + ROW_H - 4, x + W - 8, ry + ROW_H - 3, rowHov ? 0xFF3B434C : C_STROKE);
            fill(matrices, x + 18, ry + 9, x + 20, ry + ROW_H - 11, rowHov ? C_ACCENT : 0xFF4E5964);

            textRenderer.draw(matrices, LABELS[i], x + PAD + 14, ry + 10, C_TEXT);
            textRenderer.draw(matrices, HINTS[i], x + PAD + 14, ry + 23, C_TEXT_MUTED);
        }

        String hint = "ESC - закрыть   RSHIFT - открыть в игре";
        textRenderer.draw(matrices, hint, x + PAD, y + H - 14, C_TEXT_FAINT);
        textRenderer.draw(matrices, "v1.2.3", x + W - PAD - textRenderer.getWidth("v1.2.3"), y + H - 14, C_TEXT_MUTED);

        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public boolean isPauseScreen()     { return false; }
}
