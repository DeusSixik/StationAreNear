package dev.sixik.stationarenear.navigation.server;

import dev.sixik.stationarenear.navigation.SolarNavigationConfig;
import dev.sixik.stationarenear.structures.config.StationStructureConfigManager;
import dev.sixik.stationarenear.structures.generation.StationGenerationSettings;
import dev.sixik.stationarenear.structures.util.StationStructureIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

public final class SolarNavigationStationGenerationConfig {
    public static final ResourceLocation DEFAULT_POOL = StationStructureIds.pool("space_station");
    public static final int MIN_ROOMS = 10;
    public static final int MAX_FLOORS = 2;
    public static final int EXTRA_ROOMS_OVER_PREVIEW = 8;
    public static final boolean RANDOM_STATION = true;
    public static final float DEFAULT_DANGER = 0.35F;
    public static final float QUEST_DANGER = 0.55F;

    private SolarNavigationStationGenerationConfig() {
    }

    public static StationGenerationSettings create(ServerLevel level, boolean quest, long generationSeed) {
        float baseDanger = quest ? QUEST_DANGER : DEFAULT_DANGER;
        StationGenerationSettings fallbackSettings = fallback(quest, generationSeed);
        return StationStructureConfigManager.random(level.getRandom())
                .map(config -> config.createSettings(level.getRandom(), generationSeed, baseDanger))
                .orElse(fallbackSettings);
    }

    public static StationGenerationSettings fallback(boolean quest, long generationSeed) {
        float danger = quest ? QUEST_DANGER : DEFAULT_DANGER;
        int maxRooms = Math.max(MIN_ROOMS, SolarNavigationConfig.DUNGEON_PREVIEW_MAX_ROOMS.get() + EXTRA_ROOMS_OVER_PREVIEW);
        return new StationGenerationSettings(DEFAULT_POOL, danger, RANDOM_STATION, MAX_FLOORS, MIN_ROOMS, maxRooms, generationSeed);
    }
}
