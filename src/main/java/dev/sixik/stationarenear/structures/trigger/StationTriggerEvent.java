package dev.sixik.stationarenear.structures.trigger;

import dev.sixik.stationarenear.structures.data.PlacedTriggerZone;
import dev.sixik.stationarenear.structures.data.StationInstance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

public class StationTriggerEvent extends Event {

    private final ServerLevel level;
    private final ServerPlayer player;
    private final StationInstance station;
    private final PlacedTriggerZone zone;
    private final boolean firstPlayerEnter;
    private final boolean firstGlobalActivation;

    public StationTriggerEvent(
            ServerLevel level,
            ServerPlayer player,
            StationInstance station,
            PlacedTriggerZone zone,
            boolean firstPlayerEnter,
            boolean firstGlobalActivation
    ) {
        this.level = level;
        this.player = player;
        this.station = station;
        this.zone = zone;
        this.firstPlayerEnter = firstPlayerEnter;
        this.firstGlobalActivation = firstGlobalActivation;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public StationInstance getStation() {
        return station;
    }

    public PlacedTriggerZone getZone() {
        return zone;
    }

    public boolean isFirstPlayerEnter() {
        return firstPlayerEnter;
    }

    public boolean isFirstGlobalActivation() {
        return firstGlobalActivation;
    }
}
