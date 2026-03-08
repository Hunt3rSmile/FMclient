package su.firemine.fmvisuals.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.firemine.fmvisuals.gui.CycleButton;
import su.firemine.fmvisuals.gui.ToggleButton;
import su.firemine.fmvisuals.util.FMRenderer;

@Mixin(ClickableWidget.class)
public class ClickableWidgetMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void fmRender(MatrixStack matrices, int mouseX, int mouseY,
                          float delta, CallbackInfo ci) {
        ClickableWidget widget = (ClickableWidget) (Object) this;
        if (!widget.visible) {
            ci.cancel();
            return;
        }

        if (widget instanceof ToggleButton || widget instanceof CycleButton) {
            return;
        }

        if (MinecraftClient.getInstance().textRenderer != null) {
            FMRenderer.drawWidgetButton(
                matrices,
                widget,
                MinecraftClient.getInstance().textRenderer,
                mouseX,
                mouseY
            );
            ci.cancel();
        }
    }
}
