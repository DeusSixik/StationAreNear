package dev.sixik.stationarenear.quest.config.director;

import dev.sixik.stationarenear.quest.data.QuestLocalization;
import dev.sixik.stationarenear.quest.data.QuestObjectiveKind;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Map;

public record QuestOfferConfig(
        String id,
        QuestObjectiveKind kind,
        int cost,
        int weight,
        int minCount,
        int maxCount,
        List<String> requiredQuests,
        String playerText,
        String samText,
        Map<ResourceLocation, Integer> requiredPieces,
        Map<String, Integer> requiredPieceTags,
        Map<String, Integer> questElementSpawnSkips,
        List<String> targetTags,
        String placeItem,
        String exclusiveGroup,
        int maxPerMission
) {

    public QuestOfferConfig {
        id = DirectorProfileConfig.normalizeId(id);
        if (id.isBlank()) {
            throw new IllegalArgumentException("Quest offer id cannot be blank");
        }
        kind = kind == null ? QuestObjectiveKind.CUSTOM : kind;
        cost = Math.max(0, cost);
        weight = Math.max(1, weight);
        minCount = Math.max(1, minCount);
        maxCount = Math.max(minCount, maxCount);
        requiredQuests = requiredQuests == null ? List.of() : requiredQuests.stream()
                .map(DirectorProfileConfig::normalizeId)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        playerText = playerText == null ? "" : playerText;
        samText = samText == null ? "" : samText;
        requiredPieces = requiredPieces == null ? Map.of() : Map.copyOf(requiredPieces);
        requiredPieceTags = requiredPieceTags == null ? Map.of() : Map.copyOf(requiredPieceTags);
        questElementSpawnSkips = questElementSpawnSkips == null ? Map.of() : Map.copyOf(questElementSpawnSkips);
        targetTags = targetTags == null ? List.of() : targetTags.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(java.util.Locale.ROOT))
                .distinct()
                .toList();
        placeItem = placeItem == null ? "" : placeItem.trim().toLowerCase(java.util.Locale.ROOT);
        exclusiveGroup = exclusiveGroup == null ? "" : exclusiveGroup.trim().toLowerCase(java.util.Locale.ROOT);
        maxPerMission = Math.max(1, maxPerMission);
    }

    public int rollCount(RandomSource random) {
        return minCount == maxCount ? minCount : Mth.nextInt(random, minCount, maxCount);
    }

    public QuestLocalization localization() {
        return new QuestLocalization(playerText, samText);
    }
}
