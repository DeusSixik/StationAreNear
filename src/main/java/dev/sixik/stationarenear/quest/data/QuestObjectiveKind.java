package dev.sixik.stationarenear.quest.data;

public enum QuestObjectiveKind {
    CUSTOM(false),
    CLEAR_TRASH(false),
    PLACE_ITEM(true),
    REPAIR_BLOCK(true),
    BUILD_BLOCK(true),
    REPAIR_DOOR(true);

    private final boolean questZoneRequired;

    QuestObjectiveKind(boolean questZoneRequired) {
        this.questZoneRequired = questZoneRequired;
    }

    public boolean questZoneRequired() {
        return questZoneRequired;
    }
}
