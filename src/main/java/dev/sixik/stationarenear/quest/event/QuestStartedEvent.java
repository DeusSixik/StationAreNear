package dev.sixik.stationarenear.quest.event;

import dev.sixik.stationarenear.quest.data.QuestTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class QuestStartedEvent extends Event {

    private final ServerLevel level;
    private final UUID stationId;
    private final List<QuestTask> tasks;
    private final Map<String, String> objectiveTexts;
    private final long durationMillis;
    private final String announcementText;

    public QuestStartedEvent(ServerLevel level, UUID stationId, List<QuestTask> tasks, Map<String, String> objectiveTexts, long durationMillis, String announcementText) {
        this.level = level;
        this.stationId = stationId;
        this.tasks = List.copyOf(tasks);
        this.objectiveTexts = Map.copyOf(objectiveTexts);
        this.durationMillis = durationMillis;
        this.announcementText = announcementText;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public UUID getStationId() {
        return stationId;
    }

    public List<QuestTask> getTasks() {
        return tasks;
    }

    public Map<String, String> getObjectiveTexts() {
        return objectiveTexts;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public String getAnnouncementText() {
        return announcementText;
    }
}
