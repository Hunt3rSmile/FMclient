package su.firemine.fmvisuals.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class ToggleButton extends ButtonWidget {
    private static final int BG_ON = 0xFFCDD4DB;
    private static final int BG_ON_HOVER = 0xFFD8DEE4;
    private static final int BG_OFF = 0xFF1B2026;
    private static final int BG_OFF_HOVER = 0xFF232931;
    private static final int EDGE_ON = 0xFFF2F4F7;
    private static final int EDGE_OFF = 0xFF39414A;
    private static final int KNOB_ON = 0xFF161A20;
    private static final int KNOB_OFF = 0xFFEAEFF4;
    private static final int TEXT_ON = 0xFF161A20;
    private static final int TEXT_OFF = 0xFF9DA7B1;

    private boolean state;
    private final Consumer<Boolean> onChange;

    public ToggleButton(int x, int y, int w, int h, boolean initial, Consumer<Boolean> onChange) {
        super(x, y, w, h, label(initial), btn -> {});
        this.state = initial;
        this.onChange = onChange;
    }

    private static Text label(boolean on) {
        return new LiteralText(on ? "ON" : "OFF");
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
        int bg = state ? (hov ? BG_ON_HOVER : BG_ON) : (hov ? BG_OFF_HOVER : BG_OFF);
        int edge = state ? EDGE_ON : EDGE_OFF;
        int knob = state ? KNOB_ON : KNOB_OFF;
        int textClr = state ? TEXT_ON : TEXT_OFF;
        int knobW = 16;
        int knobX = state ? this.x + this.width - knobW - 3 : this.x + 3;

        fill(matrices, this.x, this.y, this.x + this.width, this.y + this.height, bg);
        fill(matrices, this.x, this.y, this.x + this.width, this.y + 1, edge);
        fill(matrices, this.x, this.y + this.height - 1, this.x + this.width, this.y + this.height, edge);
        fill(matrices, this.x, this.y, this.x + 1, this.y + this.height, edge);
        fill(matrices, this.x + this.width - 1, this.y, this.x + this.width, this.y + this.height, edge);
        fill(matrices, this.x + 1, this.y + 1, this.x + this.width - 1, this.y + 2, state ? 0x28FFFFFF : 0x10FFFFFF);

        fill(matrices, knobX, this.y + 3, knobX + knobW, this.y + this.height - 3, knob);
        fill(matrices, knobX, this.y + 3, knobX + knobW, this.y + 4, state ? 0x18000000 : 0x2CFFFFFF);
        fill(matrices, knobX, this.y + this.height - 4, knobX + knobW, this.y + this.height - 3, state ? 0x30000000 : 0x18000000);

        net.minecraft.client.font.TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        Text msg = getMessage();
        int tw = tr.getWidth(msg);
        float labelX = state ? this.x + 10.0f : this.x + this.width - tw - 10.0f;
        tr.drawWithShadow(matrices, msg, labelX, this.y + (this.height - 8) / 2.0f, textClr);
    }
}
