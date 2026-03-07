package su.firemine.fmvisuals.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

public class CycleButton extends ButtonWidget {

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
        boolean hov    = isHovered();
        int     bg     = hov ? 0xFF1a0a30 : 0xFF130820;
        int     border = hov ? 0xFFc084fc : 0xFF7c3aed;

        fill(matrices, this.x, this.y, this.x + this.width, this.y + this.height, bg);
        fill(matrices, this.x, this.y,                    this.x + this.width, this.y + 1,              border);
        fill(matrices, this.x, this.y + this.height - 1,  this.x + this.width, this.y + this.height,    border);
        fill(matrices, this.x, this.y,                    this.x + 1,          this.y + this.height,    border);
        fill(matrices, this.x + this.width - 1, this.y,   this.x + this.width, this.y + this.height,    border);

        net.minecraft.client.font.TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        tr.draw(matrices, new LiteralText("◀"), this.x + 4.0f,               this.y + (this.height - 8) / 2.0f, 0xFF555577);
        tr.draw(matrices, new LiteralText("▶"), this.x + this.width - 10.0f, this.y + (this.height - 8) / 2.0f, 0xFF555577);

        Text msg = getMessage();
        int  tw  = tr.getWidth(msg);
        tr.draw(matrices, msg,
            this.x + (this.width - tw) / 2.0f,
            this.y + (this.height - 8) / 2.0f,
            0xFFc084fc);
    }
}
