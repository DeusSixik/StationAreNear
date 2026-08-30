package dev.sixik.stationarenear.quest.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;

public class GravitationPanelRepairedEvent extends Event {

    private final ServerLevel level;
    private final Player player;
    private final BlockPos pos;

    public GravitationPanelRepairedEvent(ServerLevel level, Player player, BlockPos pos) {
        this.level = level;
        this.player = player;
        this.pos = pos.immutable();
    }

    public ServerLevel getLevel() {
        return level;
    }

    public Player getPlayer() {
        return player;
    }

    public BlockPos getPos() {
        return pos;
    }
}
