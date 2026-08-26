package dev.sixik.stationarenear.quest.runtime;

import dev.sixik.stationarenear.quest.api.QuestApi;
import dev.sixik.stationarenear.quest.data.QuestStationState;
import dev.sixik.stationarenear.quest.event.QuestTimerExpiredEvent;
import dev.sixik.stationarenear.quest.world.QuestSavedData;
import dev.sixik.stationarenear.structures.data.StationInstance;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;

public final class QuestStationDepartureHandler {

    private QuestStationDepartureHandler() {
    }

    public static void beforeStationCleared(ServerLevel level, StationInstance station) {
        QuestSavedData data = QuestSavedData.get(level);
        QuestStationState state = data.stationIfPresent(station.id()).orElse(null);
        if (state == null || !state.hasActiveObjectives()) {
            return;
        }
        if (!state.timerExpired() && state.timerRemainingMillis() <= 1000L && state.expireTimer()) {
            data.station(state);
            MinecraftForge.EVENT_BUS.post(new QuestTimerExpiredEvent(level, station.id(), state));
        }
        if (state.timerExpired()) {
            QuestApi.fail(level, station.id(), "left_station_after_timer_expired");
        }
    }
}
