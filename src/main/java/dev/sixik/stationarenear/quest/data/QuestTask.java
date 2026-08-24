package dev.sixik.stationarenear.quest.data;

public record QuestTask(String id, int count) {

    public QuestTask {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Quest task id cannot be blank");
        }
        count = Math.max(1, count);
    }
}
