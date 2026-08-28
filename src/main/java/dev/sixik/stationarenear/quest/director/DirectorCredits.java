package dev.sixik.stationarenear.quest.director;

public record DirectorCredits(
        int playMinutes,
        int completedMissionCount,
        int questBudget,
        int questSpent,
        int questLeftover,
        int stationBudget,
        int stationSpent,
        int stationLeftover
) {
}
