package su.firemine.fmvisuals.mixin;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.firemine.fmvisuals.util.FMRenderer;

import java.util.List;

@Mixin(value = Screen.class, priority = 900)
public class ScreenMixin {

    @Shadow protected int width;
    @Shadow protected int height;
    @Shadow public TextRenderer textRenderer;
    @Shadow protected List<ClickableWidget> buttons;

    @Inject(method = "render", at = @At("TAIL"))
    private void fmOverlay(MatrixStack matrices, int mouseX, int mouseY,
                            float delta, CallbackInfo ci) {
        if ((Object) this instanceof TitleScreen) return;
        if (this.textRenderer == null) return;

        int W = this.width, H = this.height;

        FMRenderer.rect(matrices, 0, 0, W, H, 0xFF060610);
        FMRenderer.rect(matrices, 0, 0,     W, 1,     0x55a855f7);
        FMRenderer.rect(matrices, 0, H - 1, W, H,     0x55a855f7);

        FMRenderer.drawRays(matrices, W, H);
        FMRenderer.drawParticles(matrices, W, H);
        FMRenderer.drawButtons(matrices, this.buttons, this.textRenderer, mouseX, mouseY);
    }
}
