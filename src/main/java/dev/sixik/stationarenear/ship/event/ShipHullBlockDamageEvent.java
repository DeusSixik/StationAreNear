package dev.sixik.stationarenear.ship.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;

public class ShipHullBlockDamageEvent extends Event {

    private final ServerLevel level;
    private final BlockPos blockPos;
    private final boolean broken;

    public ShipHullBlockDamageEvent(ServerLevel level, BlockPos blockPos, boolean broken) {
        this.level = level;
        this.blockPos = blockPos.immutable();
        this.broken = broken;
    }

    public static boolean post(ServerLevel level, BlockPos blockPos, boolean broken) {
        return MinecraftForge.EVENT_BUS.post(new ShipHullBlockDamageEvent(level, blockPos, broken));
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos blockPos() {
        return blockPos;
    }

    public boolean broken() {
        return broken;
    }
}
