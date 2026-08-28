package dev.sixik.stationarenear.quest.event;

import dev.sixik.stationarenear.quest.data.QuestStationState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

public class PlayerQuestMissionCompletedEvent extends Event {

    private final ServerLevel level;
    private final ServerPlayer player;
    private final UUID stationId;
    private final String missionId;
    private final QuestStationState stationState;
    private final double moneyReward;

    public PlayerQuestMissionCompletedEvent(ServerLevel level, ServerPlayer player, UUID stationId, String missionId, QuestStationState stationState) {
        this.level = level;
        this.player = player;
        this.stationId = stationId;
        this.missionId = missionId == null ? "" : missionId;
        this.stationState = stationState;
        this.moneyReward = stationState == null ? 0.0D : stationState.moneyReward();
    }

    public ServerLevel getLevel() {
        return level;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public UUID getStationId() {
        return stationId;
    }

    public String getMissionId() {
        return missionId;
    }

    public QuestStationState getStationState() {
        return stationState;
    }

    public double getMoneyReward() {
        return moneyReward;
    }
}
