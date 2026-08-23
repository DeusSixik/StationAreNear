package dev.sixik.stationarenear.structures.trigger;

import java.util.Locale;

public enum StationStructureTriggerType {
    MOB_SPAWN,
    OBJECT_PLACER,
    QUEST,
    OTHER;

    public static StationStructureTriggerType from(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "mob_spawn", "danger_mob_spawn" -> MOB_SPAWN;
            case "object_placer", "loot", "object_place", "object_pool" -> OBJECT_PLACER;
            case "quest", "quest_trigger", "trigger_quest" -> QUEST;
            default -> OTHER;
        };
    }
}
