package dev.sixik.stationarenear.quest.data;

public record QuestDefinition(String id, Class<?> progressType) {

    public QuestDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Quest id cannot be blank");
        }
        if (progressType == null) {
            throw new IllegalArgumentException("Quest progress type cannot be null");
        }
    }
}
