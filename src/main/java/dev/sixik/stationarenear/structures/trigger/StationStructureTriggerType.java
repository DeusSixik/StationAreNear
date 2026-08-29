package dev.sixik.stationarenear.structures.trigger;

import dev.sixik.stationarenear.structures.util.TagsConstants;
import java.util.Locale;

public enum StationStructureTriggerType {
    MOB_SPAWN,
    OBJECT_PLACER,
    QUEST_OBJECT_PLACER,
    OBJECT_ZONE_PLACER,
    DOOR_TRIGGER,
    QUEST,
    QUEST_PLACE,
    OTHER;

    public static StationStructureTriggerType from(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case TagsConstants.Trigger.MOB_SPAWN, TagsConstants.Trigger.DANGER_MOB_SPAWN -> MOB_SPAWN;
            case TagsConstants.Trigger.OBJECT_PLACER, TagsConstants.Trigger.LOOT, TagsConstants.Trigger.OBJECT_PLACE, TagsConstants.Trigger.OBJECT_POOL -> OBJECT_PLACER;
            case TagsConstants.Trigger.QUEST_OBJECT_PLACER, TagsConstants.Trigger.QUEST_OBJECT_PLACE, TagsConstants.Trigger.QUEST_OBJECT_POOL -> QUEST_OBJECT_PLACER;
            case TagsConstants.Trigger.OBJECT_ZONE_PLACER, TagsConstants.Trigger.OBJECT_ZONE_PLACE, TagsConstants.Trigger.ZONE_OBJECT_PLACER, TagsConstants.Trigger.FLOOR_OBJECT_PLACER -> OBJECT_ZONE_PLACER;
            case TagsConstants.Trigger.DOOR_TRIGGER, TagsConstants.Trigger.DOOR_SPAWNER, TagsConstants.Trigger.DOOR, TagsConstants.Trigger.PRESSURE_DOOR, TagsConstants.Trigger.PRESSURE_TIGHT_DOOR -> DOOR_TRIGGER;
            case TagsConstants.Trigger.QUEST, TagsConstants.Trigger.QUEST_TRIGGER, TagsConstants.Trigger.TRIGGER_QUEST -> QUEST;
            case TagsConstants.Trigger.QUEST_PLACE, TagsConstants.Trigger.PLACE_QUEST, TagsConstants.Trigger.QUEST_PLACE_TRIGGER, TagsConstants.Trigger.PLACE_TRIGGER -> QUEST_PLACE;
            default -> OTHER;
        };
    }
}
