package su.firemine.fmvisuals.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.firemine.fmvisuals.gui.VisualsScreen;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    private static boolean fmRShiftWasDown = false;

    @Inject(method = "tick", at = @At("TAIL"))
    private void fmOnTick(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (client.getWindow() == null) return;

        boolean down = InputUtil.isKeyPressed(
            client.getWindow().getHandle(),
            GLFW.GLFW_KEY_RIGHT_SHIFT
        );

        if (down && !fmRShiftWasDown
                && client.currentScreen == null
                && client.player != null) {
            client.openScreen(new VisualsScreen());
        }
        fmRShiftWasDown = down;
    }
}
