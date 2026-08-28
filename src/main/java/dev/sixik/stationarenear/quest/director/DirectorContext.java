package dev.sixik.stationarenear.quest.director;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

import java.util.List;
import java.util.Set;

public record DirectorContext(
        ServerLevel level,
        long seed,
        int questBudgetOverride,
        int stationBudgetOverride,
        int playMinutes,
        int completedMissionCount,
        Set<String> completedQuestIds
) {

    public DirectorContext(ServerLevel level, long seed, int questBudgetOverride, int stationBudgetOverride, Set<String> completedQuestIds) {
        this(level, seed, questBudgetOverride, stationBudgetOverride, averageOnlinePlayMinutes(level), 0, completedQuestIds);
    }

    public DirectorContext(ServerLevel level, long seed, int questBudgetOverride, int stationBudgetOverride, int completedMissionCount, Set<String> completedQuestIds) {
        this(level, seed, questBudgetOverride, stationBudgetOverride, averageOnlinePlayMinutes(level), completedMissionCount, completedQuestIds);
    }

    public DirectorContext {
        playMinutes = Math.max(0, playMinutes);
        completedMissionCount = Math.max(0, completedMissionCount);
        completedQuestIds = completedQuestIds == null ? Set.of() : Set.copyOf(completedQuestIds);
    }

    public int questBudget(int fallback) {
        return questBudgetOverride >= 0 ? questBudgetOverride : fallback;
    }

    public int stationBudget(int fallback) {
        return stationBudgetOverride >= 0 ? stationBudgetOverride : fallback;
    }

    private static int averageOnlinePlayMinutes(ServerLevel level) {
        if (level == null || level.getServer() == null) {
            return 0;
        }
        List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return 0;
        }

        long totalTicks = 0L;
        for (ServerPlayer player : players) {
            totalTicks += Math.max(0, player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)));
        }
        return (int) Math.min(Integer.MAX_VALUE, totalTicks / players.size() / 20L / 60L);
    }
}
