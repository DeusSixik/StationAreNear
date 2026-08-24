package dev.sixik.stationarenear.quest.event;

import dev.sixik.stationarenear.quest.data.QuestDefinition;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

public class QuestProgressChangedEvent extends Event {

    private final ServerLevel level;
    private final UUID stationId;
    private final QuestDefinition definition;
    private final Object oldProgress;
    private final Object newProgress;

    public QuestProgressChangedEvent(ServerLevel level, UUID stationId, QuestDefinition definition, Object oldProgress, Object newProgress) {
        this.level = level;
        this.stationId = stationId;
        this.definition = definition;
        this.oldProgress = oldProgress;
        this.newProgress = newProgress;
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

    public Object getOldProgress() {
        return oldProgress;
    }

    public Object getNewProgress() {
        return newProgress;
    }
}
