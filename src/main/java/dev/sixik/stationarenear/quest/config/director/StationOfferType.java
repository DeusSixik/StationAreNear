package dev.sixik.stationarenear.quest.config.director;

import java.util.Locale;

public enum StationOfferType {
    MOB,
    BROKEN_DOOR,
    DOOR,
    ENERGY_FAILURE,
    OBJECT,
    CUSTOM;

    public static StationOfferType from(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MOB", "ENTITY" -> MOB;
            case "BROKEN_DOOR", "BROKEN_PRESSURE_DOOR", "PRESSURE_DOOR_BROKEN" -> BROKEN_DOOR;
            case "DOOR", "PRESSURE_DOOR" -> DOOR;
            case "ENERGY_FAILURE", "ELECTRIC_FAILURE", "POWER_FAILURE" -> ENERGY_FAILURE;
            case "OBJECT", "OBJECT_PLACER", "PROP" -> OBJECT;
            default -> CUSTOM;
        };
    }
}
