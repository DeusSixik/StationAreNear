package dev.sixik.stationarenear.terminal.server;

import dev.sixik.stationarenear.navigation.data.SolarNavigationStationInfo;
import dev.sixik.stationarenear.ship.data.ShipSystemModule;
import dev.sixik.stationarenear.terminal.data.ShipTerminalSnapshot;
import dev.sixik.stationarenear.terminal.data.TerminalCommandCatalog;
import dev.sixik.stationarenear.terminal.data.TerminalHistoryKind;
import dev.sixik.stationarenear.terminal.data.TerminalHistoryLine;
import dev.sixik.stationarenear.terminal.data.TerminalSnapshotFactory;
import dev.sixik.stationarenear.terminal.network.TerminalNetwork;
import dev.sixik.stationarenear.terminal.registry.TerminalBlocks;
import dev.sixik.stationarenear.terminal.world.TerminalSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TerminalCommandProcessor {

    private static final double MAX_TERMINAL_DISTANCE_SQ = 256.0D;

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
        String root = command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        switch (root) {
            case "help" -> appendHelp(output);
            case "status" -> appendStatus(snapshot, output);
            case "modules" -> appendModules(snapshot, output);
            case "stations", "scan" -> appendStations(snapshot, output);
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

    private static void appendStations(ShipTerminalSnapshot snapshot, List<TerminalHistoryLine> output) {
        if (snapshot.nearbyStations().isEmpty()) {
            output.add(new TerminalHistoryLine(TerminalHistoryKind.INFO, "No known stations near current solar position."));
            return;
        }

        output.add(new TerminalHistoryLine(TerminalHistoryKind.INFO, "Nearby stations around solar position:"));
        for (SolarNavigationStationInfo station : snapshot.nearbyStations()) {
            output.add(new TerminalHistoryLine(station.quest() ? TerminalHistoryKind.WARNING : TerminalHistoryKind.OUTPUT,
                    "  " + station.name()
                            + (station.quest() ? " | QUEST" : "")
                            + " | distance " + formatNumber(station.distance())));
        }
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

    private static String formatNumber(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String formatPercent(float value) {
        return String.format(Locale.ROOT, "%.0f%%", Math.max(0.0F, Math.min(1.0F, value)) * 100.0F);
    }
}
