package dev.sixik.stationarenear.ship.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;

public class PressureTightDoorRepairedEvent extends Event {

    private final ServerLevel level;
    private final Player player;
    private final BlockPos masterPos;

    public PressureTightDoorRepairedEvent(ServerLevel level, Player player, BlockPos masterPos) {
        this.level = level;
        this.player = player;
        this.masterPos = masterPos.immutable();
    }

    public ServerLevel getLevel() {
        return level;
    }

    public Player getPlayer() {
        return player;
    }

    public BlockPos getMasterPos() {
        return masterPos;
    }
}
