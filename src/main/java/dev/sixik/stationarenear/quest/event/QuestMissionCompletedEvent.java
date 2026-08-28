package dev.sixik.stationarenear.quest.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

public class QuestMissionCompletedEvent extends Event {

    private final ServerLevel level;
    private final UUID stationId;
    private final double moneyReward;

    public QuestMissionCompletedEvent(ServerLevel level, UUID stationId) {
        this(level, stationId, 0.0D);
    }

    public QuestMissionCompletedEvent(ServerLevel level, UUID stationId, double moneyReward) {
        this.level = level;
        this.stationId = stationId;
        this.moneyReward = Double.isFinite(moneyReward) ? Math.max(0.0D, moneyReward) : 0.0D;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public UUID getStationId() {
        return stationId;
    }

    public double getMoneyReward() {
        return moneyReward;
    }
}
