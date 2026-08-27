package dev.sixik.stationarenear.ship.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class StationPressureTightDoorBlock extends PressureTightDoorBlock {

    public StationPressureTightDoorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void afterDoorPlaced(Level level, BlockPos masterPos) {
    }
}
