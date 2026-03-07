package su.firemine.fmvisuals.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class ToggleButton extends ButtonWidget {

    private boolean state;
    private final Consumer<Boolean> onChange;

    // Purple palette
    private static final int CLR_ON       = 0xFF6B21A8;
    private static final int CLR_ON_HOV   = 0xFF7c3aed;
    private static final int CLR_OFF      = 0xFF16162a;
    private static final int CLR_OFF_HOV  = 0xFF22223a;
    private static final int BORDER_ON    = 0xFFa855f7;
    private static final int BORDER_OFF   = 0xFF383858;

    public ToggleButton(int x, int y, int w, int h, boolean initial, Consumer<Boolean> onChange) {
        super(x, y, w, h, label(initial), btn -> {});
        this.state = initial;
        this.onChange = onChange;
    }

    private static Text label(boolean on) {
        return new LiteralText(on ? "ВКЛ" : "ВЫКЛ");
    }

    @Override
    public void onPress() {
        state = !state;
        setMessage(label(state));
        onChange.accept(state);
    }

    @Override
    public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        boolean hov = isHovered();
        int bg     = state ? (hov ? CLR_ON_HOV  : CLR_ON)  : (hov ? CLR_OFF_HOV : CLR_OFF);
        int border = state ? BORDER_ON : BORDER_OFF;
        int textClr = state ? 0xFFFFFFFF : 0xFF777799;

        // Background
        fill(matrices, this.x, this.y, this.x + this.width, this.y + this.height, bg);
        // Border
        fill(matrices, this.x, this.y,                    this.x + this.width, this.y + 1,              border);
        fill(matrices, this.x, this.y + this.height - 1,  this.x + this.width, this.y + this.height,    border);
        fill(matrices, this.x, this.y,                    this.x + 1,          this.y + this.height,    border);
        fill(matrices, this.x + this.width - 1, this.y,   this.x + this.width, this.y + this.height,    border);

        // Indicator dot when ON
        if (state) {
            fill(matrices, this.x + 5, this.y + this.height / 2 - 2,
                           this.x + 9, this.y + this.height / 2 + 2, 0xFFFFFFFF);
        }

        // Centered label
        net.minecraft.client.font.TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        Text msg = getMessage();
        int tw = tr.getWidth(msg);
        tr.draw(matrices, msg,
            this.x + (this.width - tw) / 2.0f + (state ? 2 : 0),
            this.y + (this.height - 8) / 2.0f,
            textClr);
    }
}
