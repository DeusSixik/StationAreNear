package dev.sixik.stationarenear.ship.event;

import dev.sixik.stationarenear.ship.data.ShipState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

public class ShipDestroyedEvent extends Event {

    private final ServerLevel level;
    private final BlockPos terminalPos;
    private final ShipState state;
    private final String damageSource;

    public ShipDestroyedEvent(ServerLevel level, BlockPos terminalPos, ShipState state, String damageSource) {
        this.level = level;
        this.terminalPos = terminalPos;
        this.state = state;
        this.damageSource = damageSource;
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos terminalPos() {
        return terminalPos;
    }

    public ShipState state() {
        return state;
    }

    public String damageSource() {
        return damageSource;
    }
}
