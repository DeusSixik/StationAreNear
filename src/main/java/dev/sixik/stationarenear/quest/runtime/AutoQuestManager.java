package dev.sixik.stationarenear.quest.runtime;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.navigation.world.SolarNavigationSavedData;
import dev.sixik.stationarenear.quest.config.QuestConfig;
import dev.sixik.stationarenear.quest.data.QuestStationState;
import dev.sixik.stationarenear.quest.world.QuestSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Optional;

public final class AutoQuestManager {

    private static long nextQuestAllowedTimeMillis = -1L;
    private static boolean wasQuestActive = false;
    private static int tickCounter = 0;

    private AutoQuestManager() {
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (++tickCounter % 20 != 0) {
            return;
        }

        if (!QuestConfig.AUTO_QUEST_ENABLED.get()) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        if (QuestConfig.REQUIRE_ONLINE_PLAYERS.get() && server.getPlayerCount() == 0) {
            return;
        }

        long now = System.currentTimeMillis();
        if (nextQuestAllowedTimeMillis < 0L) {
            int startupCooldownSec = QuestConfig.SERVER_START_COOLDOWN_SECONDS.get();
            nextQuestAllowedTimeMillis = now + (long) startupCooldownSec * 1000L;
            return;
        }

        ServerLevel level = server.overworld();
        if (level == null) {
            return;
        }

        boolean active = isQuestActive(level);
        if (active) {
            wasQuestActive = true;
            return;
        }

        if (wasQuestActive) {
            wasQuestActive = false;
            int intervalSec = QuestConfig.QUEST_INTERVAL_SECONDS.get();
            nextQuestAllowedTimeMillis = now + (long) intervalSec * 1000L;
            return;
        }

        if (now < nextQuestAllowedTimeMillis) {
            return;
        }

        if (!QuestTestScenario.hasAvailableQuest(level)) {
            nextQuestAllowedTimeMillis = now + 30_000L;
            return;
        }

        Vec3 spawnPos = findSpawnPosition(server, level);
        try {
            QuestTestScenario.createQuestMarker(level, spawnPos);
            wasQuestActive = true;
        } catch (Exception exception) {
            StationAreNear.LOGGER.warn("Failed to auto-assign director quest", exception);
            nextQuestAllowedTimeMillis = now + 30_000L;
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        nextQuestAllowedTimeMillis = -1L;
        wasQuestActive = false;
        tickCounter = 0;
    }

    public static boolean isQuestActive(ServerLevel level) {
        QuestSavedData questData = QuestSavedData.get(level);
        Optional<QuestStationState> current = questData.currentStation();
        if (current.isEmpty()) {
            return false;
        }
        QuestStationState state = current.get();
        if (!state.hasActiveObjectives() || state.timerExpired()) {
            return false;
        }
        return SolarNavigationSavedData.get(level).questMarker(QuestTestScenario.MARKER_ID).isPresent();
    }

    public static void triggerNextQuestImmediately() {
        nextQuestAllowedTimeMillis = 0L;
        wasQuestActive = false;
    }

    public static void scheduleNextQuest(int delaySeconds) {
        nextQuestAllowedTimeMillis = System.currentTimeMillis() + (long) Math.max(0, delaySeconds) * 1000L;
        wasQuestActive = false;
    }

    private static Vec3 findSpawnPosition(MinecraftServer server, ServerLevel level) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (!players.isEmpty()) {
            return players.get(0).position();
        }
        return Vec3.atCenterOf(level.getSharedSpawnPos());
    }
}
