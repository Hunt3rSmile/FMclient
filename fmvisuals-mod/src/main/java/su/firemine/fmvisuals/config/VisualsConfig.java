package su.firemine.fmvisuals.config;

import net.minecraft.client.MinecraftClient;

public class VisualsConfig {

    private static boolean fullbright = false;
    private static double savedGamma  = 1.0;

    public static boolean isFullbright() {
        return fullbright;
    }

    public static void setFullbright(boolean enabled) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (enabled && !fullbright) {
            savedGamma = mc.options.gamma;
            mc.options.gamma = 100.0;
            fullbright = true;
        } else if (!enabled && fullbright) {
            mc.options.gamma = savedGamma;
            fullbright = false;
        }
        mc.options.write();
    }
}
