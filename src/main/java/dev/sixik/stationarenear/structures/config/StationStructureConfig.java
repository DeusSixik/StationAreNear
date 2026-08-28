package dev.sixik.stationarenear.structures.config;

import dev.sixik.stationarenear.structures.generation.StationGenerationSettings;
import dev.sixik.stationarenear.structures.util.StationStructureIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.LinkedHashMap;
import java.util.Map;

public record StationStructureConfig(
        ResourceLocation id,
        ResourceLocation pool,
        int maxFloors,
        int minRooms,
        int maxRooms,
        float minDanger,
        float maxDanger,
        Map<ResourceLocation, Integer> requiredPieces,
        Map<String, Integer> requiredPieceTags,
        Map<String, Integer> questElementSpawnSkips
) {
    public static final ResourceLocation DEFAULT_ID = StationStructureIds.normalize("default_station", "default_station");

    public StationStructureConfig {
        id = id == null ? DEFAULT_ID : id;
        pool = pool == null ? StationStructureIds.pool("space_station") : pool;
        maxFloors = Math.max(1, maxFloors);
        minRooms = Math.max(0, minRooms);
        maxRooms = Math.max(Math.max(1, minRooms), maxRooms);
        minDanger = Mth.clamp(minDanger, 0.0F, 1.0F);
        maxDanger = Mth.clamp(maxDanger, 0.0F, 1.0F);
        if (minDanger > maxDanger) {
            float oldMin = minDanger;
            minDanger = maxDanger;
            maxDanger = oldMin;
        }
        requiredPieces = copyPositiveResourceMap(requiredPieces);
        requiredPieceTags = copyPositiveStringMap(requiredPieceTags);
        questElementSpawnSkips = copyPositiveStringMap(questElementSpawnSkips);
    }

    public StationGenerationSettings createSettings(RandomSource random, long seed) {
        float danger = minDanger == maxDanger ? minDanger : Mth.lerp(random.nextFloat(), minDanger, maxDanger);
        return new StationGenerationSettings(
                pool,
                danger,
                true,
                maxFloors,
                minRooms,
                maxRooms,
                seed,
                requiredPieces,
                requiredPieceTags,
                questElementSpawnSkips
        );
    }

    public int requiredRoomsTotal() {
        int total = 0;
        for (int count : requiredPieces.values()) {
            total += count;
        }
        for (int count : requiredPieceTags.values()) {
            total += count;
        }
        return total;
    }

    private static Map<ResourceLocation, Integer> copyPositiveResourceMap(Map<ResourceLocation, Integer> map) {
        if (map == null || map.isEmpty()) {
            return Map.of();
        }
        Map<ResourceLocation, Integer> copy = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Integer> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0) {
                copy.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        return Map.copyOf(copy);
    }

    private static Map<String, Integer> copyPositiveStringMap(Map<String, Integer> map) {
        if (map == null || map.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(java.util.Locale.ROOT);
            if (!key.isBlank() && entry.getValue() != null && entry.getValue() > 0) {
                copy.merge(key, entry.getValue(), Integer::sum);
            }
        }
        return Map.copyOf(copy);
    }
}
