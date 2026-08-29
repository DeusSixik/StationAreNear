package dev.sixik.stationarenear.quest.item;

import dev.sixik.stationarenear.quest.block.WallMountedPanelBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class BrokenWallMountedPanelItem extends BlockItem {

    public BrokenWallMountedPanelItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Nullable
    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState state = super.getPlacementState(context);
        return state == null ? null : state.setValue(WallMountedPanelBlock.BROKEN, true);
    }
}