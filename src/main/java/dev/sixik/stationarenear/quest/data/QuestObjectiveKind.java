package dev.sixik.stationarenear.quest.data;

public enum QuestObjectiveKind {
    CUSTOM(false),
    CLEAR_TRASH(false),
    PLACE_ITEM(true),
    REPAIR_BLOCK(true),
    BUILD_BLOCK(true),
    REPAIR_DOOR(true),
    REPAIR_ELECTRIC_PANEL(true),
    REPAIR_GRAVITATION_PANEL(true),
    REPAIR_OXYGEN_PANEL(true);

    private final boolean questZoneRequired;

    QuestObjectiveKind(boolean questZoneRequired) {
        this.questZoneRequired = questZoneRequired;
    }

    public boolean questZoneRequired() {
        return questZoneRequired;
    }
}
