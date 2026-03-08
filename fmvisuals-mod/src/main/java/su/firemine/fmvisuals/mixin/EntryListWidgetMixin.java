package su.firemine.fmvisuals.mixin;

import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntryListWidget.class)
public class EntryListWidgetMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void fmDisableVanillaBackground(MatrixStack matrices, int mouseX, int mouseY,
                                           float delta, CallbackInfo ci) {
        ((EntryListWidgetAccessor) this).fmSetRenderBackground(false);
        ((EntryListWidgetAccessor) this).fmSetRenderDecorations(false);
    }
}
