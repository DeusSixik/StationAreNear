package dev.sixik.stationarenear.ship.event;

import dev.sixik.stationarenear.ship.data.ShipState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

public class ShipDecompressionEvent extends Event {

    private final ServerLevel level;
    private final BlockPos terminalPos;
    private final ShipState oldState;
    private final ShipState newState;

    public ShipDecompressionEvent(ServerLevel level, BlockPos terminalPos, ShipState oldState, ShipState newState) {
        this.level = level;
        this.terminalPos = terminalPos;
        this.oldState = oldState;
        this.newState = newState;
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos terminalPos() {
        return terminalPos;
    }

    public ShipState oldState() {
        return oldState;
    }

    public ShipState newState() {
        return newState;
    }

    public boolean decompressed() {
        return newState.decompressed();
    }

    public String reason() {
        return newState.decompressionReason();
    }
}
