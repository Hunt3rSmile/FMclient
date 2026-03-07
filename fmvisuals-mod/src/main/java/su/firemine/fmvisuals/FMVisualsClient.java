package su.firemine.fmvisuals;

import net.fabricmc.api.ClientModInitializer;

// Key handling is done via MinecraftClientMixin (no Fabric API required)
public class FMVisualsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Initialised via mixins only
    }
}
