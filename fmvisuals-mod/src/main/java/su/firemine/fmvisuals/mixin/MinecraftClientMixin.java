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

    private static boolean fmShiftWasDown = false;

    @Inject(method = "tick", at = @At("TAIL"))
    private void fmOnTick(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (client.getWindow() == null) return;

        boolean shiftDown = InputUtil.isKeyPressed(
            client.getWindow().getHandle(),
            GLFW.GLFW_KEY_LEFT_SHIFT
        );

        if (shiftDown && !fmShiftWasDown
                && client.currentScreen == null
                && client.player != null) {
            client.openScreen(new VisualsScreen());
        }
        fmShiftWasDown = shiftDown;
    }
}
