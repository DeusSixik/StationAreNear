package dev.sixik.stationarenear.structures.trigger;

import java.util.Locale;

public enum StationStructureTriggerType {
    MOB_SPAWN,
    OBJECT_PLACER,
    DOOR_TRIGGER,
    QUEST,
    QUEST_PLACE,
    OTHER;

    public static StationStructureTriggerType from(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "mob_spawn", "danger_mob_spawn" -> MOB_SPAWN;
            case "object_placer", "loot", "object_place", "object_pool" -> OBJECT_PLACER;
            case "door_trigger", "door_spawner", "door", "pressure_door", "pressure_tight_door" -> DOOR_TRIGGER;
            case "quest", "quest_trigger", "trigger_quest" -> QUEST;
            case "quest_place", "place_quest", "quest_place_trigger", "place_trigger" -> QUEST_PLACE;
            default -> OTHER;
        };
    }
}
