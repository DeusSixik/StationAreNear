package dev.sixik.stationarenear.navigation;

import net.minecraftforge.common.ForgeConfigSpec;

public final class SolarNavigationConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue SECTOR_SIZE;
    public static final ForgeConfigSpec.IntValue SECTOR_RENDER_RADIUS;
    public static final ForgeConfigSpec.IntValue STARS_PER_SECTOR;
    public static final ForgeConfigSpec.IntValue ASTEROIDS_PER_SECTOR;
    public static final ForgeConfigSpec.DoubleValue ASTEROID_MIN_RADIUS;
    public static final ForgeConfigSpec.DoubleValue ASTEROID_MAX_RADIUS;
    public static final ForgeConfigSpec.DoubleValue ASTEROID_COLLISION_EVENT_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue RANDOM_STATION_CHANCE;
    public static final ForgeConfigSpec.DoubleValue STATION_MIN_RADIUS;
    public static final ForgeConfigSpec.DoubleValue STATION_MAX_RADIUS;
    public static final ForgeConfigSpec.IntValue DUNGEON_PREVIEW_MIN_ROOMS;
    public static final ForgeConfigSpec.IntValue DUNGEON_PREVIEW_MAX_ROOMS;
    public static final ForgeConfigSpec.DoubleValue SHIP_STATE_SYNC_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue STATION_UNLOAD_DISTANCE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("solar_navigation");
        SECTOR_SIZE = builder.comment("Size of one procedural navigation sector in UI world units.")
                .defineInRange("sector_size", 900, 256, 8192);
        SECTOR_RENDER_RADIUS = builder.comment("How many sectors around the ship are generated and rendered.")
                .defineInRange("sector_render_radius", 2, 1, 8);
        STARS_PER_SECTOR = builder.defineInRange("stars_per_sector", 16, 0, 128);
        ASTEROIDS_PER_SECTOR = builder.comment("How many asteroid collision objects spawn in each navigation sector.")
                .defineInRange("asteroids_per_sector", 7, 0, 64);
        ASTEROID_MIN_RADIUS = builder.defineInRange("asteroid_min_radius", 28.0D, 4.0D, 256.0D);
        ASTEROID_MAX_RADIUS = builder.defineInRange("asteroid_max_radius", 74.0D, 4.0D, 512.0D);
        ASTEROID_COLLISION_EVENT_COOLDOWN = builder.comment("Minimum seconds between asteroid collision events sent by one open navigation UI.")
                .defineInRange("asteroid_collision_event_cooldown", 0.75D, 0.05D, 10.0D);
        RANDOM_STATION_CHANCE = builder.comment("Chance from 0.0 to 1.0 that a sector contains a random station.")
                .defineInRange("random_station_chance", 0.34D, 0.0D, 1.0D);
        STATION_MIN_RADIUS = builder.defineInRange("station_min_radius", 54.0D, 16.0D, 256.0D);
        STATION_MAX_RADIUS = builder.defineInRange("station_max_radius", 78.0D, 16.0D, 512.0D);
        DUNGEON_PREVIEW_MIN_ROOMS = builder.defineInRange("dungeon_preview_min_rooms", 6, 1, 32);
        DUNGEON_PREVIEW_MAX_ROOMS = builder.defineInRange("dungeon_preview_max_rooms", 11, 1, 64);
        SHIP_STATE_SYNC_INTERVAL = builder.comment("Seconds between client ship state saves to server while navigation UI is open.")
                .defineInRange("ship_state_sync_interval", 0.5D, 0.1D, 10.0D);
        STATION_UNLOAD_DISTANCE = builder.comment("After docking, generated station blocks are cleared when the ship gets this far from the station marker.")
                .defineInRange("station_unload_distance", 620.0D, 64.0D, 8192.0D);
        builder.pop();
        SPEC = builder.build();
    }

    private SolarNavigationConfig() {
    }
}
