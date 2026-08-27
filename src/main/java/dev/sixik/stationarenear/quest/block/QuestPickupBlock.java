package dev.sixik.stationarenear.quest.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public interface QuestPickupBlock {

    default BlockPos pickupMasterPos(BlockPos pos, BlockState state) {
        return pos;
    }

    default ItemStack pickupStack(BlockState masterState) {
        return new ItemStack(masterState.getBlock().asItem());
    }

    default void pickupRemove(Level level, BlockPos masterPos, BlockState masterState) {
        level.destroyBlock(masterPos, false);
    }
}
