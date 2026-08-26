package dev.sixik.stationarenear.quest.runtime;

import dev.sixik.stationarenear.quest.data.QuestStationState;
import dev.sixik.stationarenear.quest.event.QuestTimerExpiredEvent;
import dev.sixik.stationarenear.quest.world.QuestSavedData;
import dev.sixik.stationarenear.structures.world.StationSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;

public final class QuestTimerManager {

    private static long lastTickMillis = -1L;

    private QuestTimerManager() {
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        long nowMillis = System.nanoTime() / 1_000_000L;
        if (lastTickMillis < 0L) {
            lastTickMillis = nowMillis;
            return;
        }

        long elapsedMillis = Math.max(0L, nowMillis - lastTickMillis);
        lastTickMillis = nowMillis;
        if (elapsedMillis <= 0L) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        for (ServerLevel level : server.getAllLevels()) {
            tickLevel(level, elapsedMillis);
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        lastTickMillis = -1L;
    }

    private static void tickLevel(ServerLevel level, long elapsedMillis) {
        QuestSavedData data = QuestSavedData.get(level);
        List<QuestStationState> expiredStations = new ArrayList<>();
        boolean dirty = false;

        for (QuestStationState station : data.stations()) {
            if (station.tickTimer(elapsedMillis, isDockedQuestStation(level, station))) {
                expiredStations.add(station);
                dirty = true;
            }
        }

        if (dirty) {
            data.setDirty();
        }
        for (QuestStationState station : expiredStations) {
            MinecraftForge.EVENT_BUS.post(new QuestTimerExpiredEvent(level, station.stationId(), station));
        }
    }

    private static boolean isDockedQuestStation(ServerLevel level, QuestStationState station) {
        return StationSavedData.get(level).station(station.stationId()).isPresent();
    }
}
