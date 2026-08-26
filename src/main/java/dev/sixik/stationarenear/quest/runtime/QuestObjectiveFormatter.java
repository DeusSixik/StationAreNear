package dev.sixik.stationarenear.quest.runtime;

import dev.sixik.stationarenear.navigation.StationCodeGenerator;
import dev.sixik.stationarenear.navigation.world.SolarNavigationStationCleaner;
import dev.sixik.stationarenear.quest.data.QuestObjectiveState;
import dev.sixik.stationarenear.quest.data.QuestStationState;
import dev.sixik.stationarenear.quest.world.QuestSavedData;
import dev.sixik.stationarenear.structures.world.StationSavedData;
import dev.sixik.stationarenear.terminal.data.TerminalHistoryKind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class QuestObjectiveFormatter {

    public static final String IDLE_TEXT = "\u041e\u0436\u0438\u0434\u0430\u0439\u0442\u0435 \u0437\u0430\u0434\u0430\u043d\u0438\u044f";

    private QuestObjectiveFormatter() {
    }

    public static boolean hasActiveMission(ServerLevel level) {
        QuestSavedData questData = QuestSavedData.get(level);
        return questData.currentStationId().isPresent() && questData.currentStation().isPresent();
    }

    public static List<Entry> terminalEntries(ServerLevel level) {
        QuestSavedData questData = QuestSavedData.get(level);
        if (questData.currentStationId().isEmpty() || questData.currentStation().isEmpty()) {
            return List.of(new Entry(TerminalHistoryKind.INFO, "No active mission."));
        }

        QuestStationState station = questData.currentStation().get();
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(TerminalHistoryKind.INFO, "Current mission station: " + stationDisplayId(level, station)));
        addTimerLine(station, entries);
        addObjectiveLines(station, entries);
        return entries;
    }

    public static String televisionText(ServerLevel level) {
        QuestSavedData questData = QuestSavedData.get(level);
        if (questData.currentStationId().isEmpty() || questData.currentStation().isEmpty()) {
            return "";
        }

        QuestStationState station = questData.currentStation().get();
        List<String> lines = new ArrayList<>();
        lines.add("\u0417\u0410\u0414\u0410\u041d\u0418\u0415");
        lines.add("\u0421\u0422\u0410\u041d\u0426\u0418\u042f: " + stationDisplayId(level, station));
        if (station.hasTimer()) {
            lines.add(station.timerExpired() ? "\u0412\u0420\u0415\u041c\u042f: \u0418\u0421\u0422\u0415\u041a\u041b\u041e" : "\u0412\u0420\u0415\u041c\u042f: " + formatDuration(station.timerRemainingMillis()));
        }
        if (station.objectives().isEmpty()) {
            lines.add("\u0417\u0430\u0434\u0430\u0447\u0438 \u043d\u0435 \u043d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u044b");
        } else {
            int index = 1;
            for (QuestObjectiveState objective : station.objectives()) {
                lines.add(index + ". " + objective.text() + " [" + objectiveProgress(objective) + "]");
                index++;
            }
        }
        return String.join("\n", lines);
    }

    private static void addTimerLine(QuestStationState station, List<Entry> entries) {
        if (!station.hasTimer()) {
            return;
        }
        entries.add(new Entry(station.timerExpired() ? TerminalHistoryKind.ERROR : TerminalHistoryKind.OUTPUT,
                station.timerExpired() ? "Time left: EXPIRED" : "Time left: " + formatDuration(station.timerRemainingMillis())));
    }

    private static void addObjectiveLines(QuestStationState station, List<Entry> entries) {
        if (station.objectives().isEmpty()) {
            entries.add(new Entry(TerminalHistoryKind.INFO, "No objectives assigned."));
            return;
        }

        int index = 1;
        for (QuestObjectiveState objective : station.objectives()) {
            TerminalHistoryKind kind = objective.completed() ? TerminalHistoryKind.INFO : TerminalHistoryKind.OUTPUT;
            entries.add(new Entry(kind, "  " + index + ". " + objective.text() + " [" + objectiveProgress(objective) + "]"));
            index++;
        }
    }

    private static String stationDisplayId(ServerLevel level, QuestStationState questStation) {
        if (questStation != null && !questStation.displayStationCode().isBlank()) {
            return questStation.displayStationCode();
        }
        UUID stationId = questStation == null ? null : questStation.stationId();
        return StationSavedData.get(level)
                .station(stationId)
                .map(station -> station.customData().getString(SolarNavigationStationCleaner.KEY_NAVIGATION_STATION_CODE))
                .filter(code -> code != null && !code.isBlank())
                .orElseGet(() -> StationCodeGenerator.code(stationId));
    }

    private static String objectiveProgress(QuestObjectiveState objective) {
        if (objective.completed()) {
            return "DONE";
        }
        if (objective.targetCount() <= 1) {
            return "PENDING";
        }
        return Math.min(progressCount(objective.progress()), objective.targetCount()) + "/" + objective.targetCount();
    }

    private static int progressCount(CompoundTag progress) {
        if (progress.contains("value", Tag.TAG_INT)) {
            return progress.getInt("value");
        }
        if (progress.contains("value", Tag.TAG_LONG)) {
            return (int) Math.min(Integer.MAX_VALUE, progress.getLong("value"));
        }
        if (progress.contains("value", Tag.TAG_FLOAT)) {
            return Math.round(progress.getFloat("value"));
        }
        if (progress.contains("value", Tag.TAG_DOUBLE)) {
            return (int) Math.round(progress.getDouble("value"));
        }
        return 0;
    }

    private static String formatDuration(long millis) {
        long totalSeconds = millis <= 0L ? 0L : (millis + 999L) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds / 60L) % 60L;
        long seconds = totalSeconds % 60L;
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    public record Entry(TerminalHistoryKind kind, String text) {
    }
}
