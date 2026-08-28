package dev.sixik.stationarenear.structures.generation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.LinkedHashMap;
import java.util.Map;

public record StationGenerationSettings(
        ResourceLocation pool,
        float missionDanger,
        boolean randomStation,
        int maxFloors,
        int minRooms,
        int maxRooms,
        long seed,
        Map<ResourceLocation, Integer> requiredPieces,
        Map<String, Integer> requiredPieceTags,
        Map<String, Integer> questElementSpawnSkips,
        CompoundTag customData
) {

    public StationGenerationSettings(ResourceLocation pool, float missionDanger, boolean randomStation, int maxPieces, long seed) {
        this(pool, missionDanger, randomStation, 1, 0, maxPieces, seed, Map.of(), Map.of(), Map.of(), new CompoundTag());
    }

    public StationGenerationSettings(ResourceLocation pool, float missionDanger, boolean randomStation, int maxFloors, int minRooms, int maxRooms, long seed) {
        this(pool, missionDanger, randomStation, maxFloors, minRooms, maxRooms, seed, Map.of(), Map.of(), Map.of(), new CompoundTag());
    }

    public StationGenerationSettings(
            ResourceLocation pool,
            float missionDanger,
            boolean randomStation,
            int maxFloors,
            int minRooms,
            int maxRooms,
            long seed,
            Map<ResourceLocation, Integer> requiredPieces,
            Map<String, Integer> requiredPieceTags,
            Map<String, Integer> questElementSpawnSkips
    ) {
        this(pool, missionDanger, randomStation, maxFloors, minRooms, maxRooms, seed, requiredPieces, requiredPieceTags, questElementSpawnSkips, new CompoundTag());
    }

    public StationGenerationSettings {
        missionDanger = Mth.clamp(missionDanger, 0.0F, 1.0F);
        maxFloors = Math.max(1, maxFloors);
        minRooms = Math.max(0, minRooms);
        maxRooms = Math.max(1, maxRooms);
        if (minRooms > 0) {
            maxRooms = Math.max(minRooms, maxRooms);
        }
        requiredPieces = normalizeRequiredPieces(requiredPieces);
        requiredPieceTags = normalizeRequiredTags(requiredPieceTags);
        questElementSpawnSkips = normalizeQuestElementSpawnSkips(questElementSpawnSkips);
        customData = customData == null ? new CompoundTag() : customData.copy();
    }

    public StationGenerationSettings withMissionDanger(float missionDanger) {
        return new StationGenerationSettings(pool, missionDanger, randomStation, maxFloors, minRooms, maxRooms, seed, requiredPieces, requiredPieceTags, questElementSpawnSkips, customData);
    }

    public StationGenerationSettings withRequiredPieces(Map<ResourceLocation, Integer> requiredPieces) {
        return new StationGenerationSettings(pool, missionDanger, randomStation, maxFloors, minRooms, maxRooms, seed, requiredPieces, requiredPieceTags, questElementSpawnSkips, customData);
    }

    public StationGenerationSettings withRequiredPieceTags(Map<String, Integer> requiredPieceTags) {
        return new StationGenerationSettings(pool, missionDanger, randomStation, maxFloors, minRooms, maxRooms, seed, requiredPieces, requiredPieceTags, questElementSpawnSkips, customData);
    }

    public StationGenerationSettings withRequiredPieces(Map<ResourceLocation, Integer> requiredPieces, Map<String, Integer> requiredPieceTags) {
        return new StationGenerationSettings(pool, missionDanger, randomStation, maxFloors, minRooms, maxRooms, seed, requiredPieces, requiredPieceTags, questElementSpawnSkips, customData);
    }

    public StationGenerationSettings withQuestElementSpawnSkips(Map<String, Integer> questElementSpawnSkips) {
        return new StationGenerationSettings(pool, missionDanger, randomStation, maxFloors, minRooms, maxRooms, seed, requiredPieces, requiredPieceTags, questElementSpawnSkips, customData);
    }

    public StationGenerationSettings withCustomData(CompoundTag customData) {
        return new StationGenerationSettings(pool, missionDanger, randomStation, maxFloors, minRooms, maxRooms, seed, requiredPieces, requiredPieceTags, questElementSpawnSkips, customData);
    }

    public int requiredPieceCount(ResourceLocation pieceId) {
        return requiredPieces.getOrDefault(pieceId, 0);
    }

    public int requiredPiecesTotal() {
        int total = 0;
        for (int count : requiredPieces.values()) {
            total += count;
        }
        return total;
    }

    public int requiredPieceTagCount(String tag) {
        return requiredPieceTags.getOrDefault(normalizeTag(tag), 0);
    }

    public int questElementSpawnSkip(String questId) {
        return questElementSpawnSkips.getOrDefault(normalizeQuestId(questId), 0);
    }

    private static Map<ResourceLocation, Integer> normalizeRequiredPieces(Map<ResourceLocation, Integer> requiredPieces) {
        if (requiredPieces == null || requiredPieces.isEmpty()) {
            return Map.of();
        }

        Map<ResourceLocation, Integer> normalized = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Integer> entry : requiredPieces.entrySet()) {
            ResourceLocation id = entry.getKey();
            if (id == null) {
                continue;
            }
            int count = Math.max(0, entry.getValue() == null ? 0 : entry.getValue());
            if (count > 0) {
                normalized.merge(id, count, Integer::sum);
            }
        }
        return Map.copyOf(normalized);
    }

    private static Map<String, Integer> normalizeRequiredTags(Map<String, Integer> requiredTags) {
        if (requiredTags == null || requiredTags.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : requiredTags.entrySet()) {
            String tag = normalizeTag(entry.getKey());
            int count = Math.max(0, entry.getValue() == null ? 0 : entry.getValue());
            if (!tag.isBlank() && count > 0) {
                normalized.merge(tag, count, Integer::sum);
            }
        }
        return Map.copyOf(normalized);
    }

    private static Map<String, Integer> normalizeQuestElementSpawnSkips(Map<String, Integer> skips) {
        if (skips == null || skips.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : skips.entrySet()) {
            String questId = normalizeQuestId(entry.getKey());
            int count = Math.max(0, entry.getValue() == null ? 0 : entry.getValue());
            if (!questId.isBlank() && count > 0) {
                normalized.merge(questId, count, Integer::sum);
            }
        }
        return Map.copyOf(normalized);
    }

    public static String normalizeTag(String tag) {
        return tag == null ? "" : tag.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public static String normalizeQuestId(String questId) {
        return questId == null ? "" : questId.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public float rollDanger(RandomSource random) {
        return missionDanger;
    }
}
