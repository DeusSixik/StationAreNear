package dev.sixik.stationarenear.quest.event;

import dev.sixik.stationarenear.quest.data.QuestStationState;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

public class QuestMissionFailedEvent extends Event {

    private final ServerLevel level;
    private final UUID stationId;
    private final QuestStationState stationState;
    private final String reason;

    public QuestMissionFailedEvent(ServerLevel level, UUID stationId, QuestStationState stationState, String reason) {
        this.level = level;
        this.stationId = stationId;
        this.stationState = stationState;
        this.reason = reason == null ? "" : reason;
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

    public String getReason() {
        return reason;
    }
}
