package dev.sixik.stationarenear.terminal.client;

import dev.sixik.stationarenear.terminal.block.TerminalBlock;
import dev.sixik.stationarenear.terminal.block.entity.TerminalBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class TerminalRenderer extends GeoBlockRenderer<TerminalBlockEntity> {

    public TerminalRenderer(BlockEntityRendererProvider.Context context) {
        super(new TerminalModel());
    }


    @Override
    public boolean shouldRenderOffScreen(TerminalBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    protected Direction getFacing(TerminalBlockEntity animatable) {
        BlockState state = animatable.getBlockState();
        if (state.hasProperty(TerminalBlock.FACING)) {
            return state.getValue(TerminalBlock.FACING);
        }
        return Direction.NORTH;
    }
}
