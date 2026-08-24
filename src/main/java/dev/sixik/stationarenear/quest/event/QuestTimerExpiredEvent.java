package dev.sixik.stationarenear.quest.event;

import dev.sixik.stationarenear.quest.data.QuestStationState;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

public class QuestTimerExpiredEvent extends Event {

    private final ServerLevel level;
    private final UUID stationId;
    private final QuestStationState stationState;

    public QuestTimerExpiredEvent(ServerLevel level, UUID stationId, QuestStationState stationState) {
        this.level = level;
        this.stationId = stationId;
        this.stationState = stationState;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public UUID getStationId() {
        return stationId;
    }

    public QuestStationState getStationState() {
        return stationState;
    }
}
