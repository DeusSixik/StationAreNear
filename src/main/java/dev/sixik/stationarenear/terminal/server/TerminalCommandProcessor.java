package dev.sixik.stationarenear.terminal.server;

import dev.sixik.stationarenear.navigation.world.SolarNavigationStationCleaner;
import dev.sixik.stationarenear.navigation.world.SolarNavigationSavedData;
import dev.sixik.stationarenear.navigation.block.SolarNavigationTerminalBlock;
import dev.sixik.stationarenear.navigation.StationCodeGenerator;
import dev.sixik.stationarenear.navigation.SolarNavigationProceduralMap;
import dev.sixik.stationarenear.navigation.data.SolarNavigationStationInfo;
import dev.sixik.stationarenear.quest.runtime.QuestObjectiveFormatter;
import dev.sixik.stationarenear.quest.data.QuestStationState;
import dev.sixik.stationarenear.quest.world.QuestSavedData;
import dev.sixik.stationarenear.ship.data.ShipSystemModule;
import dev.sixik.stationarenear.ship.block.entity.ShipTelevisionBlockEntity.TelevisionTextPosition;
import dev.sixik.stationarenear.ship.block.entity.ShipTelevisionBlockEntity.TelevisionContentMode;
import dev.sixik.stationarenear.ship.runtime.ShipDoorController;
import dev.sixik.stationarenear.ship.runtime.ShipTelevisionManager;
import dev.sixik.stationarenear.structures.runtime.StationDoorController;
import dev.sixik.stationarenear.structures.world.StationSavedData;
import dev.sixik.stationarenear.terminal.data.ShipTerminalSnapshot;
import dev.sixik.stationarenear.terminal.data.TerminalCommandCatalog;
import dev.sixik.stationarenear.terminal.data.TerminalHistoryKind;
import dev.sixik.stationarenear.terminal.data.TerminalHistoryLine;
import dev.sixik.stationarenear.terminal.data.TerminalSnapshotFactory;
import dev.sixik.stationarenear.terminal.network.MapTerminalNetwork;
import dev.sixik.stationarenear.terminal.network.TerminalNetwork;
import dev.sixik.stationarenear.terminal.registry.TerminalBlocks;
import dev.sixik.stationarenear.terminal.shop.PlayerBalanceSavedData;
import dev.sixik.stationarenear.terminal.shop.ShopCatalog;
import dev.sixik.stationarenear.terminal.shop.ShopItemInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

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
        return dev.sixik.stationarenear.terminal.world.TerminalSavedData.get(level).history(terminalPos);
    }

    public static void submit(ServerPlayer player, BlockPos terminalPos, String rawCommand) {
        if (!canUseTerminal(player, terminalPos)) {
            return;
        }

        String command = normalize(rawCommand);
        if (command.isBlank()) {
            return;
        }

        ServerLevel level = player.serverLevel();
        dev.sixik.stationarenear.terminal.world.TerminalSavedData data =
                dev.sixik.stationarenear.terminal.world.TerminalSavedData.get(level);
        if (isClearCommand(command)) {
            data.clear(terminalPos);
            TerminalNetwork.syncHistory(level, terminalPos, data.history(terminalPos));
            return;
        }

        List<TerminalHistoryLine> output = new ArrayList<>();
        output.add(new TerminalHistoryLine(TerminalHistoryKind.COMMAND, "/" + command));
        execute(player, level, terminalPos, command, output);
        data.appendAll(terminalPos, output);
        TerminalNetwork.syncHistory(level, terminalPos, data.history(terminalPos));
    }

    private static void execute(ServerPlayer player, ServerLevel level, BlockPos terminalPos, String command, List<TerminalHistoryLine> output) {
        ShipTerminalSnapshot snapshot = TerminalSnapshotFactory.create(level, terminalPos);
        String[] parts = command.split("\\s+", 2);
        String root = parts[0].toLowerCase(Locale.ROOT);
        String argument = parts.length > 1 ? parts[1].trim() : "";
        switch (root) {
            case "help" -> appendHelp(output);
            case "status" -> appendStatus(snapshot, output);
            case "modules" -> appendModules(snapshot, output);
            case "door" -> appendDoorCommand(level, terminalPos, argument, output);
            case "tv", "television" -> appendTelevisionCommand(level, terminalPos, snapshot, argument, output);
            case "tv_clear" -> appendTelevisionCommand(level, terminalPos, snapshot, "clear", output);
            case "tv_pos" -> appendTelevisionPositionCommand(level, terminalPos, argument, output);
            case "tv_scale" -> appendTelevisionScaleCommand(level, terminalPos, argument, output);
            case "objectives", "objective", "tasks" -> appendObjectives(level, output);
            case "map" -> appendMapCommand(player, terminalPos, output);
            case "stations" -> appendStations(snapshot, output);
            case "scan" -> {
                if (argument.isBlank()) {
                    appendStations(snapshot, output);
                } else {
                    appendStationScan(level, snapshot, argument, output);
                }
            }
            case "store" -> appendStoreCommand(player, argument, output);
            case "balance" -> appendBalanceCommand(player, output);
            default -> output.add(new TerminalHistoryLine(TerminalHistoryKind.ERROR, "Unknown command: " + command + " / use help"));
        }
    }

    private static void appendStoreCommand(ServerPlayer player, String argument, List<TerminalHistoryLine> output) {
        List<ShopItemInfo> catalog = ShopCatalog.ENTRIES;

        if (argument.isBlank()) {
            if (catalog.isEmpty()) {
                output.add(new TerminalHistoryLine(TerminalHistoryKind.WARNING, "Store catalog is empty."));
                return;
            }
            output.add(new TerminalHistoryLine(TerminalHistoryKind.INFO, "=== STATION STORE ==="));
            output.add(new TerminalHistoryLine(TerminalHistoryKind.INFO,
                    String.format(Locale.ROOT, "  %-4s %-30s %s", "#", "Item", "Price (credits)")));
            for (ShopItemInfo entry : catalog) {
                output.add(new TerminalHistoryLine(TerminalHistoryKind.OUTPUT,
                        String.format(Locale.ROOT, "  %-4d %-30s %.2f",
                                entry.index(), resolveDisplayName(entry.itemId()), entry.price())));
            }
            output.add(new TerminalHistoryLine(TerminalHistoryKind.INFO,
                    "Usage: store <index> <count>"));
            return;
        }

        String[] parts = argument.split("\\s+", 2);
        int itemIndex;
        int count;
        try {
            itemIndex = Integer.parseInt(parts[0].trim());
        } catch (NumberFormatException ex) {
            output.add(new TerminalHistoryLine(TerminalHistoryKind.ERROR,
                    "Invalid index '" + parts[0] + "'. Usage: store <index> <count>"));
            return;
        }
        try {
            count = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
            if (count < 1) count = 1;
        } catch (NumberFormatException ex) {
            output.add(new TerminalHistoryLine(TerminalHistoryKind.ERROR,
                    "Invalid count '" + parts[1] + "'. Must be a positive integer."));
            return;
        }

        if (itemIndex < 0 || itemIndex >= catalog.size()) {
            output.add(new TerminalHistoryLine(TerminalHistoryKind.ERROR,
                    "Index " + itemIndex + " out of range. Use 'store' to see available items."));
            return;
        }

        ShopItemInfo entry = catalog.get(itemIndex);
        double totalCost = entry.price() * count;

        PlayerBalanceSavedData balanceData = PlayerBalanceSavedData.get(player.serverLevel());
        if (!balanceData.canAfford(player.getUUID(), totalCost)) {
            double balance = balanceData.getBalance(player.getUUID());
            output.add(new TerminalHistoryLine(TerminalHistoryKind.ERROR,
                    String.format(Locale.ROOT,
                            "Insufficient funds. Need %.2f credits, have %.2f.",
                            totalCost, balance)));
            return;
        }

        ResourceLocation loc = new ResourceLocation(entry.itemId());
        if (!ForgeRegistries.ITEMS.containsKey(loc)) {
            output.add(new TerminalHistoryLine(TerminalHistoryKind.ERROR,
                    "Item '" + entry.itemId() + "' does not exist on this server."));
            return;
        }

        net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(loc);
        String displayName = resolveDisplayName(entry.itemId());
        int maxStack = item.getMaxStackSize();
        int remaining = count;
        while (remaining > 0) {
            int batch = Math.min(remaining, maxStack);
            ItemStack stack = new ItemStack(item, batch);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            remaining -= batch;
        }

        double newBalance = balanceData.addBalance(player.getUUID(), -totalCost);
        output.add(new TerminalHistoryLine(TerminalHistoryKind.INFO,
                String.format(Locale.ROOT,
                        "Purchased %dx %s for %.2f credits. Balance: %.2f",
                        count, displayName, totalCost, newBalance)));
    }

    private static String resolveDisplayName(String itemId) {
        ResourceLocation loc = new ResourceLocation(itemId);
        if (ForgeRegistries.ITEMS.containsKey(loc)) {
            return new ItemStack(ForgeRegistries.ITEMS.getValue(loc)).getHoverName().getString();
        }
        return itemId;
    }

    private static void appendBalanceCommand(ServerPlayer player, List<TerminalHistoryLine> output) {
        double balance = PlayerBalanceSavedData.get(player.serverLevel())
                .getBalance(player.getUUID());
        output.add(new TerminalHistoryLine(TerminalHistoryKind.INFO,
                String.format(Locale.ROOT, "Your balance: %.2f credits", balance)));
    }

    private static void appendMapCommand(ServerPlayer player, BlockPos terminalPos, List<TerminalHistoryLine> output) {
        if (MapTerminalNetwork.openMap(player, terminalPos, true)) {
            output.add(new TerminalHistoryLine(TerminalHistoryKind.INFO, "Opening docked station level map."));
        } else {
            output.add(new TerminalHistoryLine(TerminalHistoryKind.WARNING, "Station map unavailable: ship is not docked with a station."));
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
        String[] parts = argument.isBlank() ? new String[0] : argument.split("\\s+", 2);
        String action = parts.length == 0 ? "status" : parts[0].toLowerCase(Locale.ROOT);
        String doorId = parts.length > 1 ? parts[1].trim() : "";
        if (!doorId.isBlank()) {
            StationDoorController.StationDoorResult result;
            switch (action) {
                case "open" -> result = StationDoorController.setOpen(level, terminalPos, doorId, true);
                case "close", "seal" -> result = StationDoorController.setOpen(level, terminalPos, doorId, false);
                case "status" -> result = StationDoorController.status(level, terminalPos, doorId);
                default -> {
                    output.add(new TerminalHistoryLine(TerminalHistoryKind.ERROR, "Usage: door open [id] | door close [id] | door status [id]"));
                    return;
                }
            }

            TerminalHistoryKind kind = result.success()
                    ? result.open() ? TerminalHistoryKind.WARNING : result.changed() ? TerminalHistoryKind.INFO : TerminalHistoryKind.OUTPUT
                    : TerminalHistoryKind.ERROR;
            output.add(new TerminalHistoryLine(kind, result.message()));
            return;
        }

        ShipDoorController.DoorControlResult result;
        switch (action) {
            case "open" -> result = ShipDoorController.setOpen(level, terminalPos, true);
            case "close", "seal" -> result = ShipDoorController.setOpen(level, terminalPos, false);
            case "status" -> result = ShipDoorController.status(level, terminalPos);
            default -> {
                output.add(new TerminalHistoryLine(TerminalHistoryKind.ERROR, "Usage: door open [id] | door close [id] | door status [id]"));
                return;
            }
        }

        TerminalHistoryKind kind = result.success()
                ? result.open() ? TerminalHistoryKind.WARNING : result.changed() ? TerminalHistoryKind.INFO : TerminalHistoryKind.OUTPUT
                : TerminalHistoryKind.ERROR;
        output.add(new TerminalHistoryLine(kind, result.message()));
    }

    private static void appendObjectives(ServerLevel level, List<TerminalHistoryLine> output) {
        for (QuestObjectiveFormatter.Entry entry : QuestObjectiveFormatter.terminalEntries(level)) {
            output.add(new TerminalHistoryLine(entry.kind(), entry.text()));
        }
    }

    private static void appendTelevisionCommand(ServerLevel level, BlockPos terminalPos, ShipTerminalSnapshot snapshot, String argument, List<TerminalHistoryLine> output) {
        String normalized = argument == null ? "" : argument.trim();
        if (normalized.isBlank()) {
            output.add(new TerminalHistoryLine(TerminalHistoryKind.INFO, "TV commands: tv text <message> / tv hp / tv clear"));
            return;
        }

        String[] parts = normalized.split("\\s+", 2);
        String subcommand = parts[0].toLowerCase(Locale.ROOT);
        String value = parts.length > 1 ? parts[1].trim() : "";
        boolean changed;
        String message;
        switch (subcommand) {
            case "clear", "reset" -> {
                changed = ShipTelevisionManager.setManualText(level, terminalPos, "");
                message = "TV manual text cleared.";
            }
            case "ship_status", "hp", "status" -> {
                changed = ShipTelevisionManager.setManualContentMode(level, terminalPos, TelevisionContentMode.SHIP_STATUS);
                message = "TV ship status mode enabled.";
            }
            case "ship_scan" -> {
                changed = ShipTelevisionManager.setManualContentMode(level, terminalPos, TelevisionContentMode.SHIP_SCAN);
                message = "TV ship scan mode enabled.";
            }
            case "text", "message", "show" -> {
                changed = ShipTelevisionManager.setManualText(level, terminalPos, value);
                message = value.isBlank() ? "TV manual text cleared." : "TV manual text updated.";
            }
            default -> {
                changed = ShipTelevisionManager.setManualText(level, terminalPos, normalized);
                message = normalized.isBlank() ? "TV manual text cleared." : "TV manual text updated.";
            }
        }

        if (changed) {
            output.add(new TerminalHistoryLine(TerminalHistoryKind.INFO, message));
        } else {
            output.add(new TerminalHistoryLine(TerminalHistoryKind.ERROR, "No ship television found for this terminal."));
        }
    }

    private static void appendTelevisionPositionCommand(ServerLevel level, BlockPos terminalPos, String argument, List<TerminalHistoryLine> output) {
        TelevisionTextPosition position = TelevisionTextPosition.fromName(argument);
        if (argument == null || argument.isBlank()) {
            output.add(new TerminalHistoryLine(TerminalHistoryKind.INFO, "Usage: tv_pos CENTER | TOP | DOWN"));
            return;
        }

        if (ShipTelevisionManager.setManualTextPosition(level, terminalPos, position)) {
            output.add(new TerminalHistoryLine(TerminalHistoryKind.INFO, "TV manual text position set to " + position.name() + "."));
        } else {
            output.add(new TerminalHistoryLine(TerminalHistoryKind.ERROR, "No ship television found for this terminal."));
        }
    }

    private static void appendTelevisionScaleCommand(ServerLevel level, BlockPos terminalPos, String argument, List<TerminalHistoryLine> output) {
        if (argument == null || argument.isBlank()) {
            output.add(new TerminalHistoryLine(TerminalHistoryKind.INFO, "Usage: tv_scale <0.35-3.0>"));
            return;
        }

        float scale;
        try {
            scale = Float.parseFloat(argument.trim());
        } catch (NumberFormatException exception) {
            output.add(new TerminalHistoryLine(TerminalHistoryKind.ERROR, "Invalid TV scale: " + argument));
            return;
        }

        if (ShipTelevisionManager.setManualTextScale(level, terminalPos, scale)) {
            output.add(new TerminalHistoryLine(TerminalHistoryKind.INFO, "TV manual text scale set to " + formatNumber(Math.max(0.35F, Math.min(3.0F, scale))) + "."));
        } else {
            output.add(new TerminalHistoryLine(TerminalHistoryKind.ERROR, "No ship television found for this terminal."));
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

    public static boolean canUseTerminal(ServerPlayer player, BlockPos terminalPos) {
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
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds / 60L) % 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static String formatNumber(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String formatPercent(float value) {
        return String.format(Locale.ROOT, "%.0f%%", Math.max(0.0F, Math.min(1.0F, value)) * 100.0F);
    }
}
