package dev.sixik.stationarenear.quest.data;

public record QuestDefinition(String id, Class<?> progressType, QuestObjectiveKind kind, QuestLocalization localization) {

    public QuestDefinition(String id, Class<?> progressType) {
        this(id, progressType, QuestObjectiveKind.CUSTOM, QuestLocalization.fallback(id));
    }

    public QuestDefinition(String id, Class<?> progressType, QuestObjectiveKind kind) {
        this(id, progressType, kind, QuestLocalization.fallback(id));
    }

    public QuestDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Quest id cannot be blank");
        }
        if (progressType == null) {
            throw new IllegalArgumentException("Quest progress type cannot be null");
        }
        kind = kind == null ? QuestObjectiveKind.CUSTOM : kind;
        localization = localization == null ? QuestLocalization.fallback(id) : localization;
    }
}
