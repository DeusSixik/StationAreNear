package dev.sixik.stationarenear.ship.client;

import dev.sixik.stationarenear.ship.block.PressureTightDoorBlock;
import dev.sixik.stationarenear.ship.block.entity.PressureTightDoorBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class PressureTightDoorRenderer extends GeoBlockRenderer<PressureTightDoorBlockEntity> {

    public PressureTightDoorRenderer(BlockEntityRendererProvider.Context context) {
        super(new PressureTightDoorModel());
    }

    @Override
    protected Direction getFacing(PressureTightDoorBlockEntity animatable) {
        BlockState state = animatable.getBlockState();
        if (state.hasProperty(PressureTightDoorBlock.FACING)) {
            return state.getValue(PressureTightDoorBlock.FACING);
        }
        return Direction.NORTH;
    }
}