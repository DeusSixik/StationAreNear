package dev.sixik.stationarenear.quest.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

public class QuestMissionCompletedEvent extends Event {

    private final ServerLevel level;
    private final UUID stationId;

    public QuestMissionCompletedEvent(ServerLevel level, UUID stationId) {
        this.level = level;
        this.stationId = stationId;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public UUID getStationId() {
        return stationId;
    }
}
