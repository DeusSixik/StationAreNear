package dev.sixik.stationarenear.quest.director;

import java.util.List;

public record DirectorDebugReport(List<String> lines) {

    public DirectorDebugReport {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public static DirectorDebugReport from(DirectorPlan plan) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        DirectorCredits credits = plan.credits();
        lines.add("profile=" + plan.profile().id() + " name=" + plan.profile().name());
        lines.add("play_minutes=" + credits.playMinutes() + " budget_formula=base+play_minutes*rate");
        lines.add("completed_missions=" + credits.completedMissionCount()
                + " danger=" + plan.profile().dangerForCompletedMissions(credits.completedMissionCount(), 1.0F)
                + " formula=base_danger*growth^completed_missions*danger_multiply");
        lines.add("quest=" + credits.questSpent() + "/" + credits.questBudget() + " left=" + credits.questLeftover());
        lines.add("station=" + credits.stationSpent() + "/" + credits.stationBudget() + " left=" + credits.stationLeftover());
        lines.add("objectives=" + plan.questTasks().stream().map(task -> task.id() + "x" + task.count()).toList());
        lines.add("station_offers=" + plan.stationSpawns().stream().map(spawn -> spawn.offer().id() + "x" + spawn.count()).toList());
        return new DirectorDebugReport(lines);
    }
}
