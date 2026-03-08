package su.firemine.fmvisuals.mixin;

import net.minecraft.client.gui.widget.EntryListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntryListWidget.class)
public interface EntryListWidgetAccessor {
    @Accessor("field_26846")
    void fmSetRenderBackground(boolean value);

    @Accessor("field_26847")
    void fmSetRenderDecorations(boolean value);
}
