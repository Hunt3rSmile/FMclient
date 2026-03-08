package su.firemine.fmvisuals.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.firemine.fmvisuals.util.FMRenderer;

@Mixin(SelectWorldScreen.class)
public abstract class SelectWorldScreenMixin extends Screen {

    protected SelectWorldScreenMixin() {
        super(new LiteralText(""));
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void fmRenderBackground(MatrixStack matrices, int mouseX, int mouseY,
                                    float delta, CallbackInfo ci) {
        FMRenderer.rect(matrices, 0, 0, this.width, this.height, 0xFF030308);
        FMRenderer.drawNetwork(matrices, this.width, this.height);
    }
}
