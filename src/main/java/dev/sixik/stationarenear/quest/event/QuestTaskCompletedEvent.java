package dev.sixik.stationarenear.quest.event;

import dev.sixik.stationarenear.quest.data.QuestDefinition;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

public class QuestTaskCompletedEvent extends Event {

    private final ServerLevel level;
    private final UUID stationId;
    private final QuestDefinition definition;
    private final Object progress;

    public QuestTaskCompletedEvent(ServerLevel level, UUID stationId, QuestDefinition definition, Object progress) {
        this.level = level;
        this.stationId = stationId;
        this.definition = definition;
        this.progress = progress;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public UUID getStationId() {
        return stationId;
    }

    public QuestDefinition getDefinition() {
        return definition;
    }

    public Object getProgress() {
        return progress;
    }
}
