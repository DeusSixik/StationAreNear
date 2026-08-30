package dev.sixik.stationarenear.core.mixin;

import dev.sixik.stationarenear.quest.item.HeavyApplianceBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStackHandler.class)
public abstract class ItemStackHandlerMixin {

    @Inject(method = "isItemValid", at = @At("HEAD"), cancellable = true, remap = false)
    private void stationarenear$preventItemStackHandlerValidity(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (HeavyApplianceBlockItem.isRestrictedFromChests(stack)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "insertItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void stationarenear$preventItemStackHandlerInsert(int slot, ItemStack stack, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if (HeavyApplianceBlockItem.isRestrictedFromChests(stack)) {
            cir.setReturnValue(stack);
        }
    }
}
