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
        float minDangerMultiplier,
        float maxDangerMultiplier,
        boolean questOnly,
        Map<ResourceLocation, Integer> requiredPieces,
        Map<String, Integer> requiredPieceTags,
        Map<String, Integer> questElementSpawnSkips
) {
    public static final ResourceLocation DEFAULT_ID = StationStructureIds.normalize("default_station", "default_station");
    public static final float DEFAULT_BASE_DANGER = 0.35F;

    public StationStructureConfig {
        id = id == null ? DEFAULT_ID : id;
        pool = pool == null ? StationStructureIds.pool("space_station") : pool;
        maxFloors = Math.max(1, maxFloors);
        minRooms = Math.max(0, minRooms);
        maxRooms = Math.max(Math.max(1, minRooms), maxRooms);
        minDangerMultiplier = sanitizeDangerMultiplier(minDangerMultiplier, 1.0F);
        maxDangerMultiplier = sanitizeDangerMultiplier(maxDangerMultiplier, 1.0F);
        if (minDangerMultiplier > maxDangerMultiplier) {
            float oldMin = minDangerMultiplier;
            minDangerMultiplier = maxDangerMultiplier;
            maxDangerMultiplier = oldMin;
        }
        requiredPieces = copyPositiveResourceMap(requiredPieces);
        requiredPieceTags = copyPositiveStringMap(requiredPieceTags);
        questElementSpawnSkips = copyPositiveStringMap(questElementSpawnSkips);
    }

    public StationGenerationSettings createSettings(RandomSource random, long seed) {
        return createSettings(random, seed, DEFAULT_BASE_DANGER);
    }

    public StationGenerationSettings createSettings(RandomSource random, long seed, float baseDanger) {
        float danger = Mth.clamp(baseDanger, 0.0F, 1.0F) * rollDangerMultiplier(random);
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

    public float rollDangerMultiplier(RandomSource random) {
        return minDangerMultiplier == maxDangerMultiplier
                ? minDangerMultiplier
                : Mth.lerp(random.nextFloat(), minDangerMultiplier, maxDangerMultiplier);
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

    private static float sanitizeDangerMultiplier(float value, float fallback) {
        if (!Float.isFinite(value)) {
            return fallback;
        }
        return Mth.clamp(value, 0.0F, 100.0F);
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
