package su.firemine.fmvisuals.mixin;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.firemine.fmvisuals.util.FMRenderer;

@Mixin(value = Screen.class, priority = 900)
public class ScreenMixin {

    @Shadow protected int width;
    @Shadow protected int height;
    @Shadow public TextRenderer textRenderer;

    @Inject(method = "renderBackground(Lnet/minecraft/client/util/math/MatrixStack;I)V",
            at = @At("HEAD"), cancellable = true)
    private void fmBackground(MatrixStack matrices, int vOffset, CallbackInfo ci) {
        if ((Object) this instanceof TitleScreen) return;
        FMRenderer.rect(matrices, 0, 0, this.width, this.height, 0xFF030308);
        FMRenderer.drawNetwork(matrices, this.width, this.height);
        ci.cancel();
    }

    @Inject(method = "renderBackground(Lnet/minecraft/client/util/math/MatrixStack;)V",
            at = @At("HEAD"), cancellable = true)
    private void fmBackgroundNoOffset(MatrixStack matrices, CallbackInfo ci) {
        if ((Object) this instanceof TitleScreen) return;
        FMRenderer.rect(matrices, 0, 0, this.width, this.height, 0xFF030308);
        FMRenderer.drawNetwork(matrices, this.width, this.height);
        ci.cancel();
    }

    @Inject(method = "renderBackgroundTexture(I)V",
            at = @At("HEAD"), cancellable = true)
    private void fmBackgroundTexture(int vOffset, CallbackInfo ci) {
        if ((Object) this instanceof TitleScreen) return;
        ci.cancel();
    }
}
