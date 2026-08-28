package dev.sixik.stationarenear.quest.config.director;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Locale;

public record StationOfferConfig(
        String id,
        StationOfferType type,
        int cost,
        int weight,
        int minCount,
        int maxCount,
        String entity,
        List<String> targetTags,
        int maxPerStation
) {

    public StationOfferConfig {
        id = DirectorProfileConfig.normalizeId(id);
        if (id.isBlank()) {
            throw new IllegalArgumentException("Station offer id cannot be blank");
        }
        type = type == null ? StationOfferType.CUSTOM : type;
        cost = Math.max(0, cost);
        weight = Math.max(1, weight);
        minCount = Math.max(1, minCount);
        maxCount = Math.max(minCount, maxCount);
        entity = entity == null ? "" : entity.trim().toLowerCase(Locale.ROOT);
        targetTags = targetTags == null ? List.of() : targetTags.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        maxPerStation = Math.max(1, maxPerStation);
    }

    public int rollCount(RandomSource random) {
        return minCount == maxCount ? minCount : Mth.nextInt(random, minCount, maxCount);
    }
}
