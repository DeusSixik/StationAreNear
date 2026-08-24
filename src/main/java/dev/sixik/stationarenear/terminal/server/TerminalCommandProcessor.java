package dev.sixik.stationarenear.terminal.server;

import dev.sixik.stationarenear.navigation.world.SolarNavigationStationCleaner;
import dev.sixik.stationarenear.navigation.world.SolarNavigationSavedData;
import dev.sixik.stationarenear.navigation.block.SolarNavigationTerminalBlock;
import dev.sixik.stationarenear.navigation.StationCodeGenerator;
import dev.sixik.stationarenear.navigation.SolarNavigationProceduralMap;
import dev.sixik.stationarenear.navigation.data.SolarNavigationStationInfo;
import dev.sixik.stationarenear.quest.data.QuestObjectiveState;
import dev.sixik.stationarenear.quest.data.QuestStationState;
import dev.sixik.stationarenear.quest.world.QuestSavedData;
import dev.sixik.stationarenear.ship.data.ShipSystemModule;
import dev.sixik.stationarenear.ship.runtime.ShipDoorController;
import dev.sixik.stationarenear.structures.world.StationSavedData;
import dev.sixik.stationarenear.terminal.data.ShipTerminalSnapshot;
import dev.sixik.stationarenear.terminal.data.TerminalCommandCatalog;
import dev.sixik.stationarenear.terminal.data.TerminalHistoryKind;
import dev.sixik.stationarenear.terminal.data.TerminalHistoryLine;
import dev.sixik.stationarenear.terminal.data.TerminalSnapshotFactory;
import dev.sixik.stationarenear.terminal.network.TerminalNetwork;
import dev.sixik.stationarenear.terminal.registry.TerminalBlocks;
import dev.sixik.stationarenear.terminal.world.TerminalSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class TerminalCommandProcessor {

    private static final double MAX_TERMINAL_DISTANCE_SQ = 256.0D;
    private static final float TARGET_SCAN_RADIUS = 12000.0F;

    private TerminalCommandProcessor() {
    }

    public static List<TerminalHistoryLine> historyForOpen(ServerLevel level, BlockPos terminalPos) {
        return TerminalSavedData.get(level).history(terminalPos);
    }

    public static void submit(ServerPlayer player, BlockPos terminalPos, String rawCommand) {
        if (!isValidTerminal(player, terminalPos)) {
            return;
        }

        String command = normalize(rawCommand);
        if (command.isBlank()) {
            return;
        }

        ServerLevel level = player.serverLevel();
        TerminalSavedData data = TerminalSavedData.get(level);
        if (isClearCommand(command)) {
            data.clear(terminalPos);
            TerminalNetwork.syncHistory(level, terminalPos, data.history(terminalPos));
            return;
        }

        List<TerminalHistoryLine> output = new ArrayList<>();
        output.add(new TerminalHistoryLine(TerminalHistoryKind.COMMAND, "/" + command));
        execute(level, terminalPos, command, output);
        data.appendAll(terminalPos, output);
        TerminalNetwork.syncHistory(level, terminalPos, data.history(terminalPos));
    }

    private static void execute(ServerLevel level, BlockPos terminalPos, String command, List<TerminalHistoryLine> output) {
        ShipTerminalSnapshot snapshot = TerminalSnapshotFactory.create(level, terminalPos);
        String[] parts = command.split("\\s+", 2);
        String root = parts[0].toLowerCase(Locale.ROOT);
        String argument = parts.length > 1 ? parts[1].trim() : "";
        switch (root) {
            case "help" -> appendHelp(output);
            case "status" -> appendStatus(snapshot, output);
            case "modules" -> appendModules(snapshot, output);
            case "door" -> appendDoorCommand(level, terminalPos, argument, output);
            case "objectives", "objective", "tasks" -> appendObjectives(level, output);
            case "stations" -> appendStations(snapshot, output);
            case "scan" -> {
                if (argument.isBlank()) {
                    appendStations(snapshot, output);
                } else {
                    appendStationScan(level, snapshot, argument, output);
                }
            }
            default -> output.add(new TerminalHistoryLine(TerminalHistoryKind.ERROR, "Unknown command: " + command + " / use help"));
        }
    }

    private static void appendHelp(List<TerminalHistoryLine> output) {
        output.add(new TerminalHistoryLine(TerminalHistoryKind.INFO, "Available terminal commands:"));
        for (var command : TerminalCommandCatalog.COMMANDS) {
            output.add(new TerminalHistoryLine(TerminalHistoryKind.OUTPUT,
                    String.format(Locale.ROOT, "  %-8s - %s", command.command(), command.description())));
        }
    }

    private static void appendStatus(ShipTerminalSnapshot snapshot, List<TerminalHistoryLine> output) {
        float hp = snapshot.shipState().hp();
        float maxHp = snapshot.shipState().maxHp();
        output.add(new TerminalHistoryLine(hp <= maxHp * 0.25F ? TerminalHistoryKind.ERROR : hp <= maxHp * 0.55F ? TerminalHistoryKind.WARNING : TerminalHistoryKind.OUTPUT,
                "Hull HP: " + formatNumber(hp) + " / " + formatNumber(maxHp) + " (" + formatPercent(snapshot.shipState().hpPercent()) + ")"));
        output.add(new TerminalHistoryLine(snapshot.shipState().decompressed() ? TerminalHistoryKind.ERROR : TerminalHistoryKind.OUTPUT,
                "Integrity: " + integrityText(snapshot)));
        output.add(new TerminalHistoryLine(snapshot.shipState().isDocking() ? TerminalHistoryKind.INFO : TerminalHistoryKind.OUTPUT,
                "Ship Mode: " + (snapshot.shipState().isDocking() ? "DOCKED" : "FLIGHT")));
        output.add(new TerminalHistoryLine(snapshot.hullBreach() || snapshot.doorOpen() && !snapshot.docked() ? TerminalHistoryKind.WARNING : TerminalHistoryKind.OUTPUT,
                "Docking: " + (snapshot.docked() ? "DOCKED" : "IN SPACE")
                        + " / Door: " + (snapshot.doorOpen() ? "OPEN" : "SEALED")
                        + " / Hull breach: " + (snapshot.hullBreach() ? "YES" : "NO")));
        output.add(new TerminalHistoryLine(TerminalHistoryKind.OUTPUT,
                "Solar speed: " + formatNumber(speed(snapshot))));
    }

    private static void appendModules(ShipTerminalSnapshot snapshot, List<TerminalHistoryLine> output) {
        output.add(new TerminalHistoryLine(TerminalHistoryKind.INFO, "Ship modules:"));
        for (ShipSystemModule module : snapshot.shipState().modules()) {
            float durabilityRatio = module.maxDurability() <= 0.0F ? 0.0F : module.durability() / module.maxDurability();
            TerminalHistoryKind kind = durabilityRatio <= 0.25F ? TerminalHistoryKind.ERROR : durabilityRatio <= 0.55F ? TerminalHistoryKind.WARNING : TerminalHistoryKind.OUTPUT;
            output.add(new TerminalHistoryLine(kind, "  " + module.type().displayName()
                    + " | Lv." + module.level()
                    + " | DUR " + formatNumber(module.durability()) + "/" + formatNumber(module.maxDurability())
                    + " (" + formatPercent(durabilityRatio) + ")"));
        }
    }

    private static void appendDoorCommand(ServerLevel level, BlockPos terminalPos, String argument, List<TerminalHistoryLine> output) {
        String action = argument.isBlank() ? "status" : argument.split("\\s+")[0].toLowerCase(Locale.ROOT);
        ShipDoorController.DoorControlResult result;
        switch (action) {
            case "open" -> result = ShipDoorController.setOpen(level, terminalPos, true);
            case "close", "seal" -> result = ShipDoorController.setOpen(level, terminalPos, false);
            case "status" -> result = ShipDoorController.status(level, terminalPos);
            default -> {
                output.add(new TerminalHistoryLine(TerminalHistoryKind.ERROR, "Usage: door open | door close | door status"));
                return;
            }
        }

        TerminalHistoryKind kind = result.success()
                ? result.open() ? TerminalHistoryKind.WARNING : result.changed() ? TerminalHistoryKind.INFO : TerminalHistoryKind.OUTPUT
                : TerminalHistoryKind.ERROR;
        output.add(new TerminalHistoryLine(kind, result.message()));
    }

    private static void appendObjectives(ServerLevel level, List<TerminalHistoryLine> output) {
        QuestSavedData questData = QuestSavedData.get(level);
        if (questData.currentStationId().isEmpty() || questData.currentStation().isEmpty()) {
            output.add(new TerminalHistoryLine(TerminalHistoryKind.INFO, "No active mission."));
            return;
        }

        QuestStationState station = questData.currentStation().get();
        output.add(new TerminalHistoryLine(TerminalHistoryKind.INFO, "Current mission station: " + stationDisplayId(level, station.stationId())));
        if (station.hasTimer()) {
            output.add(new TerminalHistoryLine(station.timerExpired() ? TerminalHistoryKind.ERROR : TerminalHistoryKind.OUTPUT,
                    station.timerExpired() ? "Time left: EXPIRED" : "Time left: " + formatDuration(station.timerRemainingMillis())));
        }
        if (station.objectives().isEmpty()) {
            output.add(new TerminalHistoryLine(TerminalHistoryKind.INFO, "No objectives assigned."));
            return;
        }

        int index = 1;
        for (QuestObjectiveState objective : station.objectives()) {
            TerminalHistoryKind kind = objective.completed() ? TerminalHistoryKind.INFO : TerminalHistoryKind.OUTPUT;
            output.add(new TerminalHistoryLine(kind,
                    "  " + index + ". " + objective.text() + " [" + objectiveProgress(objective) + "]"));
            index++;
        }
    }

    private static void appendStations(ShipTerminalSnapshot snapshot, List<TerminalHistoryLine> output) {
        if (snapshot.nearbyStations().isEmpty()) {
            output.add(new TerminalHistoryLine(TerminalHistoryKind.INFO, "No known stations near current solar position."));
            return;
        }

        output.add(new TerminalHistoryLine(TerminalHistoryKind.INFO, "Nearby station IDs around solar position:"));
        for (SolarNavigationStationInfo station : snapshot.nearbyStations()) {
            output.add(new TerminalHistoryLine(station.quest() ? TerminalHistoryKind.WARNING : TerminalHistoryKind.OUTPUT,
                    "  " + station.code()
                            + (station.quest() ? " | QUEST" : "")
                            + " | distance " + formatNumber(station.distance())));
        }
        output.add(new TerminalHistoryLine(TerminalHistoryKind.INFO, "Use: scan <station_id>"));
    }

    private static void appendStationScan(ServerLevel level, ShipTerminalSnapshot snapshot, String stationId, List<TerminalHistoryLine> output) {
        List<SolarNavigationStationInfo> matches = SolarNavigationProceduralMap.nearbyStations(
                        SolarNavigationTerminalBlock.terminalSeed(level, snapshot.navigationTerminalPos()),
                        snapshot.navigationState(),
                        SolarNavigationSavedData.get(level).questMarkers(),
                        TARGET_SCAN_RADIUS,
                        0
                )
                .stream()
                .filter(station -> StationCodeGenerator.matches(stationId, station.code()))
                .sorted(Comparator.comparingDouble(SolarNavigationStationInfo::distance))
                .toList();

        if (matches.isEmpty()) {
            output.add(new TerminalHistoryLine(TerminalHistoryKind.WARNING,
                    "Scan failed: station " + stationId.toUpperCase(Locale.ROOT) + " is outside scanner range or unknown."));
            output.add(new TerminalHistoryLine(TerminalHistoryKind.INFO,
                    "Scanner range: " + formatNumber(TARGET_SCAN_RADIUS) + ". Move closer or check station ID."));
            return;
        }

        for (SolarNavigationStationInfo station : matches) {
            output.add(new TerminalHistoryLine(station.quest() ? TerminalHistoryKind.WARNING : TerminalHistoryKind.OUTPUT,
                    "Scan " + station.code()
                            + (station.quest() ? " | QUEST" : "")
                            + " | distance " + formatNumber(station.distance())
                            + " | signal " + signalText(station.distance())));
        }
        output.add(new TerminalHistoryLine(TerminalHistoryKind.INFO, "Direction guidance requires a future navigation module."));
    }

    private static boolean isValidTerminal(ServerPlayer player, BlockPos terminalPos) {
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(terminalPos);
        return level.isLoaded(terminalPos)
                && state.is(TerminalBlocks.TERMINAL.get())
                && player.distanceToSqr(Vec3.atCenterOf(terminalPos)) <= MAX_TERMINAL_DISTANCE_SQ;
    }

    private static boolean isClearCommand(String command) {
        String normalized = command.toLowerCase(Locale.ROOT);
        return normalized.equals("clear") || normalized.equals("cls");
    }

    private static String normalize(String rawCommand) {
        String command = rawCommand == null ? "" : rawCommand.trim();
        while (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        return command;
    }

    private static String integrityText(ShipTerminalSnapshot snapshot) {
        if (!snapshot.boundToShip()) {
            return "SHIP NOT BOUND";
        }
        if (snapshot.shipState().decompressed()) {
            return "DECOMPRESSED / " + snapshot.shipState().decompressionReason();
        }
        return "SEALED";
    }

    private static float speed(ShipTerminalSnapshot snapshot) {
        float x = snapshot.navigationState().velocityX();
        float y = snapshot.navigationState().velocityY();
        return (float) Math.sqrt(x * x + y * y);
    }

    private static String stationDisplayId(ServerLevel level, UUID stationId) {
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

    private static String signalText(float distance) {
        if (distance <= 500.0F) {
            return "CLOSE";
        }
        if (distance <= 1800.0F) {
            return "MEDIUM";
        }
        if (distance <= 4200.0F) {
            return "FAR";
        }
        return "FAINT";
    }

    private static String formatDuration(long millis) {
        long totalSeconds = millis <= 0L ? 0L : (millis + 999L) / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return minutes + "m " + seconds + "s";
    }

    private static String formatNumber(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String formatPercent(float value) {
        return String.format(Locale.ROOT, "%.0f%%", Math.max(0.0F, Math.min(1.0F, value)) * 100.0F);
    }
}
