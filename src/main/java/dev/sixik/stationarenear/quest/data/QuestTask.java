package dev.sixik.stationarenear.quest.data;

public record QuestTask(String id, int count, String targetTriggerId) {

    public QuestTask(String id, int count) {
        this(id, count, "");
    }

    public QuestTask {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Quest task id cannot be blank");
        }
        count = Math.max(1, count);
        targetTriggerId = targetTriggerId == null ? "" : targetTriggerId.trim();
    }

    public QuestTask at(String triggerId) {
        return new QuestTask(id, count, triggerId);
    }
}
