package dev.sixik.stationarenear.quest.runtime;

import dev.sixik.stationarenear.navigation.StationCodeGenerator;
import dev.sixik.stationarenear.navigation.data.SolarNavigationQuestMarker;
import dev.sixik.stationarenear.navigation.server.SolarNavigationControlManager;
import dev.sixik.stationarenear.navigation.world.SolarNavigationSavedData;
import dev.sixik.stationarenear.navigation.world.SolarNavigationStationCleaner;
import dev.sixik.stationarenear.quest.api.QuestApi;
import dev.sixik.stationarenear.quest.config.QuestPhraseManager;
import dev.sixik.stationarenear.quest.data.QuestStationState;
import dev.sixik.stationarenear.quest.event.PlayerQuestMissionCompletedEvent;
import dev.sixik.stationarenear.quest.event.QuestMissionCompletedEvent;
import dev.sixik.stationarenear.quest.event.QuestTimerExpiredEvent;
import dev.sixik.stationarenear.quest.world.BalanceSavedData;
import dev.sixik.stationarenear.quest.world.QuestSavedData;
import dev.sixik.stationarenear.ship.runtime.ShipDecompressionEffects;
import dev.sixik.stationarenear.ship.runtime.ShipManager;
import dev.sixik.stationarenear.ship.world.ShipControlLockSavedData;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchor;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorResolver;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorSavedData;
import dev.sixik.stationarenear.terminal.shop.PlayerBalanceSavedData;
import dev.sixik.stationarenear.structures.data.StationInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class QuestStationDepartureHandler {

    private static final List<PendingEjection> PENDING_EJECTIONS = new ArrayList<>();

    private record PendingEjection(ResourceKey<Level> dimension, BlockPos terminalPos, long executeGameTime) {
    }

    private QuestStationDepartureHandler() {
    }

    public static void beforeStationCleared(ServerLevel level, StationInstance station) {
        QuestSavedData data = QuestSavedData.get(level);
        QuestStationState state = data.stationIfPresent(station.id()).orElse(null);
        if (state == null) {
            return;
        }

        if (state.hasActiveObjectives() && !state.timerExpired() && state.timerRemainingMillis() <= 1000L && state.expireTimer()) {
            data.station(state);
            MinecraftForge.EVENT_BUS.post(new QuestTimerExpiredEvent(level, station.id(), state));
        }

        Optional<BlockPos> terminalPos = navigationTerminalPos(station);
        if (state.timerExpired() || state.hasActiveObjectives()) {
            failMission(level, station, state, terminalPos, state.timerExpired() ? "left_station_after_timer_expired" : "left_station_with_incomplete_tasks");
            return;
        }

        completeMission(level, station, state, terminalPos);
    }

    private static void completeMission(ServerLevel level, StationInstance station, QuestStationState state, Optional<BlockPos> terminalPos) {
        QuestSavedData data = QuestSavedData.get(level);
        double reward = state.moneyReward();
        PlayerBalanceSavedData playerBalanceData = PlayerBalanceSavedData.get(level);
        BalanceSavedData.get(level).add(reward);
        if (!state.missionId().isBlank()) {
            data.markQuestCompleted(state.missionId());
        }
        data.incrementCompletedMissionCount();
        MinecraftForge.EVENT_BUS.post(new QuestMissionCompletedEvent(level, station.id(), reward));
        for (ServerPlayer player : affectedPlayers(level, station, terminalPos)) {
            double playerBalance = playerBalanceData.addBalance(player.getUUID(), reward);
            MinecraftForge.EVENT_BUS.post(new PlayerQuestMissionCompletedEvent(level, player, station.id(), state.missionId(), state));
            player.displayClientMessage(Component.literal(String.format(java.util.Locale.ROOT, "Mission completed. Reward: %.2f. Balance: %.2f.", reward, playerBalance)), false);
        }
        data.remove(station.id());
        removeNavigationMarker(level, station);
    }

    private static void failMission(ServerLevel level, StationInstance station, QuestStationState state, Optional<BlockPos> terminalPos, String reason) {
        QuestApi.fail(level, station.id(), reason);

        int delaySec = QuestPhraseManager.getEjectionDelaySeconds();
        String stationCode = state != null && !state.displayStationCode().isBlank()
                ? state.displayStationCode()
                : (station != null && station.customData().contains(SolarNavigationStationCleaner.KEY_NAVIGATION_STATION_CODE)
                ? station.customData().getString(SolarNavigationStationCleaner.KEY_NAVIGATION_STATION_CODE)
                : StationCodeGenerator.code(station.id()));

        Map<String, String> placeholders = Map.of(
                "station", stationCode,
                "delay", String.valueOf(delaySec),
                "reason", reason
        );

        QuestPhraseManager.PhraseEntry phrase = QuestPhraseManager.getFailedEjectionPhrase(level.getRandom());
        String chatText = QuestPhraseManager.format(phrase.text(), placeholders);
        String samText = QuestPhraseManager.format(phrase.sam(), placeholders);

        if (!chatText.isBlank()) {
            for (ServerPlayer player : affectedPlayers(level, station, terminalPos)) {
                player.sendSystemMessage(Component.literal(chatText));
            }
        }

        if (!samText.isBlank()) {
            QuestAnnouncementHandler.speak(level, station.id(), samText);
        }

        terminalPos.ifPresent(pos -> {
            ShipControlLockSavedData.get(level).lock(pos, "quest_failure:" + reason);
            SolarNavigationControlManager.forceStop(level, pos);
            ShipManager.setDocking(level, pos, false);
            scheduleEjection(level, pos, delaySec);
        });
        QuestSavedData.get(level).remove(station.id());
        removeNavigationMarker(level, station);
    }

    private static void scheduleEjection(ServerLevel level, BlockPos pos, int delaySec) {
        if (delaySec <= 0) {
            ShipDecompressionEffects.forceEjectPlayers(level, pos);
            return;
        }
        long targetTime = level.getGameTime() + (long) delaySec * 20L;
        PENDING_EJECTIONS.add(new PendingEjection(level.dimension(), pos, targetTime));
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING_EJECTIONS.isEmpty()) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        Iterator<PendingEjection> iterator = PENDING_EJECTIONS.iterator();
        while (iterator.hasNext()) {
            PendingEjection pending = iterator.next();
            ServerLevel targetLevel = server.getLevel(pending.dimension());
            if (targetLevel == null) {
                iterator.remove();
                continue;
            }
            if (targetLevel.getGameTime() >= pending.executeGameTime()) {
                ShipDecompressionEffects.forceEjectPlayers(targetLevel, pending.terminalPos());
                iterator.remove();
            }
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        PENDING_EJECTIONS.clear();
    }

    private static Optional<BlockPos> navigationTerminalPos(StationInstance station) {
        if (!station.customData().contains(SolarNavigationStationCleaner.KEY_NAVIGATION_TERMINAL_POS)) {
            return Optional.empty();
        }
        return Optional.of(BlockPos.of(station.customData().getLong(SolarNavigationStationCleaner.KEY_NAVIGATION_TERMINAL_POS)));
    }

    private static Set<ServerPlayer> affectedPlayers(ServerLevel level, StationInstance station, Optional<BlockPos> terminalPos) {
        Set<ServerPlayer> players = new LinkedHashSet<>();
        Optional<ShipDockingAnchor> anchor = terminalPos.flatMap(pos -> ShipDockingAnchorSavedData.get(level).anchor(pos).or(() -> ShipDockingAnchorResolver.bindNearbyShip(level, pos)));
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            BlockPos pos = player.blockPosition();
            if (anchor.map(value -> contains(value.shipBounds(), pos)).orElse(false) || contains(station, pos)) {
                players.add(player);
            }
        }
        return players;
    }

    private static boolean contains(StationInstance station, BlockPos pos) {
        for (var piece : station.pieces()) {
            if (contains(piece.bounds(), pos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(BoundingBox bounds, BlockPos pos) {
        return pos.getX() >= bounds.minX() && pos.getX() <= bounds.maxX()
                && pos.getY() >= bounds.minY() && pos.getY() <= bounds.maxY()
                && pos.getZ() >= bounds.minZ() && pos.getZ() <= bounds.maxZ();
    }

    private static void removeNavigationMarker(ServerLevel level, StationInstance station) {
        if (!station.customData().contains(SolarNavigationStationCleaner.KEY_NAVIGATION_STATION_SEED)) {
            return;
        }
        long seed = station.customData().getLong(SolarNavigationStationCleaner.KEY_NAVIGATION_STATION_SEED);
        SolarNavigationSavedData navigationData = SolarNavigationSavedData.get(level);
        for (SolarNavigationQuestMarker marker : Set.copyOf(navigationData.questMarkers())) {
            if (marker.seed() == seed) {
                navigationData.removeQuestMarker(marker.id());
            }
        }
    }
}
