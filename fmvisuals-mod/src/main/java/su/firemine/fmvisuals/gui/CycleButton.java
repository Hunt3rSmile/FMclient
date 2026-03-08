package su.firemine.fmvisuals.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

public class CycleButton extends ButtonWidget {
    private static final int BG = 0xFF181D23;
    private static final int BG_HOVER = 0xFF20262D;
    private static final int EDGE = 0xFF3A434C;
    private static final int EDGE_HOVER = 0xFFC9D0D7;
    private static final int CHIP = 0xFFE4E9EE;
    private static final int TEXT = 0xFFE9EDF1;
    private static final int ARROW = 0xFF95A0AA;

    private static final String[]       LABELS  = { "Все", "Меньше", "Мин" };
    private static final ParticlesMode[] OPTIONS = ParticlesMode.values();

    private int index;
    private final MinecraftClient mc;

    public CycleButton(int x, int y, int w, int h, MinecraftClient mc) {
        super(x, y, w, h, new LiteralText(LABELS[mc.options.particles.ordinal()]), btn -> {});
        this.mc    = mc;
        this.index = mc.options.particles.ordinal();
    }

    @Override
    public void onPress() {
        index = (index + 1) % OPTIONS.length;
        mc.options.particles = OPTIONS[index];
        mc.options.write();
        setMessage(new LiteralText(LABELS[index]));
    }

    @Override
    public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        boolean hov = isHovered();
        int bg = hov ? BG_HOVER : BG;
        int edge = hov ? EDGE_HOVER : EDGE;

        fill(matrices, this.x, this.y, this.x + this.width, this.y + this.height, bg);
        fill(matrices, this.x, this.y, this.x + this.width, this.y + 1, edge);
        fill(matrices, this.x, this.y + this.height - 1, this.x + this.width, this.y + this.height, edge);
        fill(matrices, this.x, this.y, this.x + 1, this.y + this.height, edge);
        fill(matrices, this.x + this.width - 1, this.y, this.x + this.width, this.y + this.height, edge);
        fill(matrices, this.x + 1, this.y + 1, this.x + this.width - 1, this.y + 2, 0x12FFFFFF);
        fill(matrices, this.x + this.width / 2 - 1, this.y + 4, this.x + this.width / 2 + 1, this.y + this.height - 4, 0x143A434C);
        fill(matrices, this.x + 4, this.y + 4, this.x + 6, this.y + this.height - 4, CHIP);

        net.minecraft.client.font.TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        tr.draw(matrices, new LiteralText("<"), this.x + 14.0f, this.y + (this.height - 8) / 2.0f, ARROW);
        tr.draw(matrices, new LiteralText(">"), this.x + this.width - 18.0f, this.y + (this.height - 8) / 2.0f, ARROW);

        Text msg = getMessage();
        int tw = tr.getWidth(msg);
        tr.draw(matrices, msg,
            this.x + (this.width - tw) / 2.0f + 1.0f,
            this.y + (this.height - 8) / 2.0f,
            TEXT);
    }
}
