package dev.sixik.stationarenear.ship.event;

import dev.sixik.stationarenear.ship.data.ShipState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

public class ShipHealthChangedEvent extends Event {

    private final ServerLevel level;
    private final BlockPos terminalPos;
    private final ShipState oldState;
    private final ShipState newState;
    private final String source;

    public ShipHealthChangedEvent(ServerLevel level, BlockPos terminalPos, ShipState oldState, ShipState newState, String source) {
        this.level = level;
        this.terminalPos = terminalPos;
        this.oldState = oldState;
        this.newState = newState;
        this.source = source == null || source.isBlank() ? "unknown" : source;
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

    public float oldHp() {
        return oldState.hp();
    }

    public float newHp() {
        return newState.hp();
    }

    public String source() {
        return source;
    }
}
