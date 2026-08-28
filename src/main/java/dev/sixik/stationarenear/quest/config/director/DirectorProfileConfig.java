package dev.sixik.stationarenear.quest.config.director;

import dev.sixik.stationarenear.StationAreNear;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Locale;

public record DirectorProfileConfig(
        String id,
        String name,
        boolean enabled,
        long durationSeconds,
        int questBudget,
        double questBudgetPerPlayMinute,
        int stationBudget,
        double stationBudgetPerPlayMinute,
        float baseDanger,
        float dangerGrowthMultiplier,
        boolean leftoverQuestCreditsToStation,
        String questOfferPool,
        String stationOfferPool,
        int minObjectives,
        int maxObjectives,
        String stationConfig,
        int minSpawnDistance,
        int maxSpawnDistance,
        float markerRadius,
        int markerColor,
        double moneyReward,
        List<String> requiredQuests
) {

    public DirectorProfileConfig {
        id = normalizeId(id);
        if (id.isBlank()) {
            throw new IllegalArgumentException("Director profile id cannot be blank");
        }
        name = name == null || name.isBlank() ? id : name;
        durationSeconds = Math.max(1L, durationSeconds);
        questBudget = Math.max(0, questBudget);
        questBudgetPerPlayMinute = sanitizeBudgetRate(questBudgetPerPlayMinute);
        stationBudget = Math.max(0, stationBudget);
        stationBudgetPerPlayMinute = sanitizeBudgetRate(stationBudgetPerPlayMinute);
        baseDanger = sanitizeBaseDanger(baseDanger);
        dangerGrowthMultiplier = sanitizeDangerGrowthMultiplier(dangerGrowthMultiplier);
        questOfferPool = normalizeId(questOfferPool == null || questOfferPool.isBlank() ? "default_quests" : questOfferPool);
        stationOfferPool = normalizeId(stationOfferPool == null || stationOfferPool.isBlank() ? "default_station_events" : stationOfferPool);
        minObjectives = Math.max(1, minObjectives);
        maxObjectives = Math.max(minObjectives, maxObjectives);
        stationConfig = cleanStationConfig(stationConfig);
        minSpawnDistance = Math.max(1, minSpawnDistance);
        maxSpawnDistance = Math.max(minSpawnDistance, maxSpawnDistance);
        markerRadius = Math.max(1.0F, markerRadius);
        moneyReward = Double.isFinite(moneyReward) ? Math.max(0.0D, moneyReward) : 0.0D;
        requiredQuests = requiredQuests == null ? List.of() : requiredQuests.stream()
                .map(DirectorProfileConfig::normalizeId)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    public int rollSpawnDistance(RandomSource random) {
        return minSpawnDistance == maxSpawnDistance ? minSpawnDistance : Mth.nextInt(random, minSpawnDistance, maxSpawnDistance);
    }

    public int rollObjectiveCount(RandomSource random) {
        return minObjectives == maxObjectives ? minObjectives : Mth.nextInt(random, minObjectives, maxObjectives);
    }

    public int questBudgetForPlayMinutes(int playMinutes) {
        return scaledBudget(questBudget, questBudgetPerPlayMinute, playMinutes);
    }

    public int stationBudgetForPlayMinutes(int playMinutes) {
        return scaledBudget(stationBudget, stationBudgetPerPlayMinute, playMinutes);
    }

    public float dangerForCompletedMissions(int completedMissionCount, float dungeonDangerMultiplier) {
        return dangerForCompletedMissions(baseDanger, dangerGrowthMultiplier, completedMissionCount, dungeonDangerMultiplier);
    }

    public static float dangerForCompletedMissions(float baseDanger, float dangerGrowthMultiplier, int completedMissionCount, float dungeonDangerMultiplier) {
        double danger = sanitizeBaseDanger(baseDanger)
                * Math.pow(sanitizeDangerGrowthMultiplier(dangerGrowthMultiplier), Math.max(0, completedMissionCount))
                * Math.max(0.0F, dungeonDangerMultiplier);
        if (!Double.isFinite(danger)) {
            return 1.0F;
        }
        return (float) Mth.clamp(danger, 0.0D, 1.0D);
    }

    private static float sanitizeBaseDanger(float value) {
        return Float.isFinite(value) ? Mth.clamp(value, 0.0F, 1.0F) : 0.35F;
    }

    private static float sanitizeDangerGrowthMultiplier(float value) {
        return Float.isFinite(value) ? Mth.clamp(value, 1.0F, 10.0F) : 1.35F;
    }

    private static double sanitizeBudgetRate(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, value) : 0.0D;
    }

    private static int scaledBudget(int baseBudget, double perMinute, int playMinutes) {
        double value = baseBudget + perMinute * Math.max(0, playMinutes);
        if (!Double.isFinite(value)) {
            return baseBudget;
        }
        return (int) Mth.clamp(Math.round(value), 0L, Integer.MAX_VALUE);
    }

    public static String normalizeId(String id) {
        String normalized = id == null ? "" : id.trim();
        if (normalized.isBlank()) {
            return "";
        }
        normalized = normalized.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        if (normalized.toLowerCase(Locale.ROOT).endsWith(".json")) {
            normalized = normalized.substring(0, normalized.length() - ".json".length());
        }
        if (!normalized.contains(":")) {
            normalized = StationAreNear.MODID + ":" + normalized;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    public static String cleanStationConfig(String stationConfig) {
        String normalized = stationConfig == null || stationConfig.isBlank() ? "default_station" : stationConfig.trim();
        normalized = normalized.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        if (normalized.toLowerCase(Locale.ROOT).endsWith(".json")) {
            normalized = normalized.substring(0, normalized.length() - ".json".length());
        }
        return normalized;
    }
}
