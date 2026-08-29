package dev.sixik.stationarenear.ship.runtime;

import dev.sixik.stationarenear.quest.event.QuestCompletedEvent;
import dev.sixik.stationarenear.quest.event.QuestProgressChangedEvent;
import dev.sixik.stationarenear.quest.event.QuestStartedEvent;
import dev.sixik.stationarenear.quest.event.QuestTimerExpiredEvent;
import dev.sixik.stationarenear.quest.event.StationQuestsCompletedEvent;
import dev.sixik.stationarenear.quest.runtime.QuestObjectiveFormatter;
import dev.sixik.stationarenear.ship.block.ShipTelevisionBlock;
import dev.sixik.stationarenear.navigation.data.SolarNavigationStationInfo;
import dev.sixik.stationarenear.ship.block.entity.ShipTelevisionBlockEntity;
import dev.sixik.stationarenear.ship.block.entity.ShipTelevisionBlockEntity.TelevisionContentMode;
import dev.sixik.stationarenear.ship.block.entity.ShipTelevisionBlockEntity.TelevisionTextPosition;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchor;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorResolver;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import dev.sixik.stationarenear.terminal.data.ShipTerminalSnapshot;
import dev.sixik.stationarenear.terminal.data.TerminalSnapshotFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class ShipTelevisionManager {

    private static long lastQuestRefreshGameTime = -1L;
    private static long lastDynamicRefreshGameTime = -1L;

    private ShipTelevisionManager() {
    }

    public static void onQuestStarted(QuestStartedEvent event) {
        refresh(event.getLevel());
    }

    public static void onQuestProgressChanged(QuestProgressChangedEvent event) {
        refresh(event.getLevel());
    }

    public static void onQuestCompleted(QuestCompletedEvent event) {
        refresh(event.getLevel());
    }

    public static void onStationQuestsCompleted(StationQuestsCompletedEvent event) {
        refresh(event.getLevel());
    }

    public static void onQuestTimerExpired(QuestTimerExpiredEvent event) {
        refresh(event.getLevel());
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        long gameTime = server.overworld().getGameTime();
        boolean refreshQuest = lastQuestRefreshGameTime < 0L || gameTime - lastQuestRefreshGameTime >= 20L;
        boolean refreshDynamic = lastDynamicRefreshGameTime < 0L || gameTime - lastDynamicRefreshGameTime >= 100L;
        if (!refreshQuest && !refreshDynamic) {
            return;
        }
        if (refreshQuest) {
            lastQuestRefreshGameTime = gameTime;
        }
        if (refreshDynamic) {
            lastDynamicRefreshGameTime = gameTime;
        }

        for (ServerLevel level : server.getAllLevels()) {
            if (refreshQuest) {
                refresh(level);
            }
            if (refreshDynamic) {
                refreshDynamicManualText(level);
            }
        }
    }

    public static void refresh(ServerLevel level) {
        String questText = QuestObjectiveFormatter.televisionText(level);
        for (ShipTelevisionBlockEntity television : shipTelevisions(level)) {
            if (!television.questTextEquals(questText)) {
                television.questText(questText);
            }
        }
    }

    public static boolean setManualText(ServerLevel level, BlockPos terminalPos, String text) {
        Set<ShipTelevisionBlockEntity> televisions = boundTelevisions(level, terminalPos);
        if (televisions.isEmpty()) {
            return false;
        }

        for (ShipTelevisionBlockEntity television : televisions) {
            television.manualText(text);
        }
        refresh(level);
        return true;
    }

    public static boolean setManualContentMode(ServerLevel level, BlockPos terminalPos, TelevisionContentMode mode) {
        Set<ShipTelevisionBlockEntity> televisions = boundTelevisions(level, terminalPos);
        if (televisions.isEmpty()) {
            return false;
        }

        ShipTerminalSnapshot snapshot = TerminalSnapshotFactory.create(level, terminalPos);
        String text = switch (mode) {
            case SHIP_STATUS -> shipStatusText(snapshot);
            case SHIP_SCAN -> shipScanText(snapshot);
            case TEXT -> "";
        };
        for (ShipTelevisionBlockEntity television : televisions) {
            television.dynamicManualText(mode, terminalPos, text);
            if (mode == TelevisionContentMode.SHIP_SCAN) {
                television.scanShipPositionChanged(snapshot.navigationState().shipX(), snapshot.navigationState().shipY());
            }
        }
        refresh(level);
        return true;
    }

    public static boolean setManualTextPosition(ServerLevel level, BlockPos terminalPos, TelevisionTextPosition position) {
        Set<ShipTelevisionBlockEntity> televisions = boundTelevisions(level, terminalPos);
        if (televisions.isEmpty()) {
            return false;
        }

        for (ShipTelevisionBlockEntity television : televisions) {
            television.manualTextPosition(position);
        }
        return true;
    }

    public static boolean setManualTextScale(ServerLevel level, BlockPos terminalPos, float scale) {
        Set<ShipTelevisionBlockEntity> televisions = boundTelevisions(level, terminalPos);
        if (televisions.isEmpty()) {
            return false;
        }

        for (ShipTelevisionBlockEntity television : televisions) {
            television.manualTextScale(scale);
        }
        return true;
    }

    private static void refreshDynamicManualText(ServerLevel level) {
        for (ShipTelevisionBlockEntity television : shipTelevisions(level)) {
            TelevisionContentMode mode = television.manualContentMode();
            if (mode == TelevisionContentMode.TEXT || television.controllerTerminalPos().equals(BlockPos.ZERO)) {
                continue;
            }

            ShipTerminalSnapshot snapshot = TerminalSnapshotFactory.create(level, television.controllerTerminalPos());
            if (mode == TelevisionContentMode.SHIP_STATUS) {
                television.updateDynamicManualText(shipStatusText(snapshot));
            } else if (mode == TelevisionContentMode.SHIP_SCAN
                    && television.scanShipPositionChanged(snapshot.navigationState().shipX(), snapshot.navigationState().shipY())) {
                television.updateDynamicManualText(shipScanText(snapshot));
            }
        }
    }

    public static String shipStatusText(ShipTerminalSnapshot snapshot) {
        return "SHIP STATUS\n"
                + "HP: " + formatNumber(snapshot.shipState().hp()) + "/" + formatNumber(snapshot.shipState().maxHp()) + " (" + formatPercent(snapshot.shipState().hpPercent()) + ")\n"
                + "INTEGRITY: " + integrityText(snapshot) + "\n"
                + "DOCKED: " + (snapshot.docked() ? "YES" : "NO") + "\n"
                + "SPEED: " + formatNumber(speed(snapshot));
    }

    public static String shipScanText(ShipTerminalSnapshot snapshot) {
        if (!snapshot.shipState().hasModule(dev.sixik.stationarenear.ship.data.ShipSystemType.STATION_LOCATOR)) {
            return "SHIP SCAN\nStation locator module required.\nInstall 'station_locator' upgrade.";
        }

        if (snapshot.nearbyStations().isEmpty()) {
            return "SHIP SCAN\nNo known stations near current solar position.";
        }

        StringBuilder text = new StringBuilder("SHIP SCAN\nNEARBY STATIONS");
        for (SolarNavigationStationInfo station : snapshot.nearbyStations()) {
            text.append('\n')
                    .append(station.code());
            if (station.quest()) {
                text.append(" | QUEST");
            }
            text.append(" | distance ").append(formatNumber(station.distance()));
        }
        text.append("\nUse: scan <station_id>");
        return text.toString();
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

    private static Set<ShipTelevisionBlockEntity> boundTelevisions(ServerLevel level, BlockPos terminalPos) {
        Optional<ShipDockingAnchor> anchor = ShipDockingAnchorSavedData.get(level)
                .anchor(terminalPos)
                .or(() -> ShipDockingAnchorResolver.bindNearbyShip(level, terminalPos));
        if (anchor.isEmpty()) {
            return Set.of();
        }

        Set<ShipTelevisionBlockEntity> televisions = new HashSet<>();
        addTelevisions(level, anchor.get().shipBounds(), televisions, new HashSet<>());
        return televisions;
    }

    private static Set<ShipTelevisionBlockEntity> shipTelevisions(ServerLevel level) {
        Set<ShipTelevisionBlockEntity> televisions = new HashSet<>();
        Set<Long> visitedMasters = new HashSet<>();
        for (ShipDockingAnchor anchor : ShipDockingAnchorSavedData.get(level).anchors()) {
            addTelevisions(level, anchor.shipBounds(), televisions, visitedMasters);
        }
        return televisions;
    }

    private static void addTelevisions(ServerLevel level, BoundingBox bounds, Set<ShipTelevisionBlockEntity> televisions, Set<Long> visitedMasters) {
        for (BlockPos pos : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof ShipTelevisionBlock)) {
                continue;
            }

            BlockPos masterPos = ShipTelevisionBlock.masterPos(pos, state);
            if (!visitedMasters.add(masterPos.asLong()) || !level.isLoaded(masterPos)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(masterPos);
            if (blockEntity instanceof ShipTelevisionBlockEntity television) {
                televisions.add(television);
            }
        }
    }
}
