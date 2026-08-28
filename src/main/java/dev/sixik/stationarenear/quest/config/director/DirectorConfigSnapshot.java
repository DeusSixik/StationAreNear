package dev.sixik.stationarenear.quest.config.director;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

public record DirectorConfigSnapshot(
        List<ResourceLocation> trashBlocks,
        Map<String, DirectorProfileConfig> profiles,
        Map<String, List<QuestOfferConfig>> questPools,
        Map<String, List<StationOfferConfig>> stationPools
) {

    public DirectorConfigSnapshot {
        trashBlocks = trashBlocks == null ? List.of() : List.copyOf(trashBlocks);
        profiles = profiles == null ? Map.of() : Map.copyOf(profiles);
        questPools = questPools == null ? Map.of() : copyQuestPools(questPools);
        stationPools = stationPools == null ? Map.of() : copyStationPools(stationPools);
    }

    public static DirectorConfigSnapshot empty(List<ResourceLocation> trashBlocks) {
        return new DirectorConfigSnapshot(trashBlocks, Map.of(), Map.of(), Map.of());
    }

    private static Map<String, List<QuestOfferConfig>> copyQuestPools(Map<String, List<QuestOfferConfig>> pools) {
        java.util.Map<String, List<QuestOfferConfig>> copy = new java.util.LinkedHashMap<>();
        pools.forEach((key, value) -> copy.put(DirectorProfileConfig.normalizeId(key), value == null ? List.of() : List.copyOf(value)));
        return Map.copyOf(copy);
    }

    private static Map<String, List<StationOfferConfig>> copyStationPools(Map<String, List<StationOfferConfig>> pools) {
        java.util.Map<String, List<StationOfferConfig>> copy = new java.util.LinkedHashMap<>();
        pools.forEach((key, value) -> copy.put(DirectorProfileConfig.normalizeId(key), value == null ? List.of() : List.copyOf(value)));
        return Map.copyOf(copy);
    }
}
