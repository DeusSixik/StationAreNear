package dev.sixik.stationarenear.core.mixin.modules;

import dev.sixik.unigui.api.text.RichText;
import dev.sixik.unigui.widgets.interaction.Button;
import dev.sixik.unigui.widgets.interaction.ComboBox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ComboBox.class, remap = false)
public abstract class FixBug$ComboBoxMixin {

    @Shadow
    public abstract RichText selectedRichItem();

    @Shadow
    private RichText richPlaceholder;

    @Shadow
    @Final
    private Button headerButton;

    /**
     * @author Sixik
     * @reason
     */
    @Overwrite
    public void updateHeaderText() {
        RichText value = this.selectedRichItem().isEmpty() ? this.richPlaceholder : this.selectedRichItem();
        this.headerButton.richText(value);
    }
}
