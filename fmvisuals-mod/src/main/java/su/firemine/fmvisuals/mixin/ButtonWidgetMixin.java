package su.firemine.fmvisuals.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.firemine.fmvisuals.gui.CycleButton;
import su.firemine.fmvisuals.gui.ToggleButton;
import su.firemine.fmvisuals.util.FMRenderer;

@Mixin(ButtonWidget.class)
public class ButtonWidgetMixin {

    @Inject(method = "renderButton", at = @At("HEAD"), cancellable = true)
    private void fmRenderButton(MatrixStack matrices, int mouseX, int mouseY,
                                float delta, CallbackInfo ci) {
        ButtonWidget button = (ButtonWidget) (Object) this;
        if (!button.visible) {
            ci.cancel();
            return;
        }

        if (button instanceof ToggleButton || button instanceof CycleButton) {
            return;
        }

        if (MinecraftClient.getInstance().textRenderer != null) {
            FMRenderer.drawWidgetButton(
                matrices,
                button,
                MinecraftClient.getInstance().textRenderer,
                mouseX,
                mouseY
            );
            ci.cancel();
        }
    }
}
