package dev.sixik.stationarenear.core.mixin;

import dev.sixik.stationarenear.quest.item.HeavyApplianceBlockItem;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class SlotMixin {

    @Shadow
    @Final
    public Container container;

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void stationarenear$preventChestSlotPlacement(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (HeavyApplianceBlockItem.isRestrictedFromChests(stack)) {
            if (!(this.container instanceof Inventory)
                    && !(this.container instanceof CraftingContainer)
                    && !(this.container instanceof ResultContainer)) {
                cir.setReturnValue(false);
            }
        }
    }
}
