package dev.sixik.stationarenear.structures.trigger;

import dev.sixik.stationarenear.structures.data.PlacedStationPiece;
import dev.sixik.stationarenear.structures.data.PlacedTriggerZone;
import dev.sixik.stationarenear.structures.data.StationInstance;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

public class StationStructureSpawnTriggerEvent extends Event {

    private final ServerLevel level;
    private final StationInstance station;
    private final PlacedStationPiece piece;
    private final PlacedTriggerZone zone;
    private final StationStructureTriggerType triggerType;
    private int additionalPlaceObjectChance;
    private boolean placementCanceled;
    private String forcedMob;
    private int forcedMobCount = -1;

    public StationStructureSpawnTriggerEvent(
            ServerLevel level,
            StationInstance station,
            PlacedStationPiece piece,
            PlacedTriggerZone zone,
            StationStructureTriggerType triggerType
    ) {
        this.level = level;
        this.station = station;
        this.piece = piece;
        this.zone = zone;
        this.triggerType = triggerType;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public StationInstance getStation() {
        return station;
    }

    public PlacedStationPiece getPiece() {
        return piece;
    }

    public PlacedTriggerZone getZone() {
        return zone;
    }

    public StationStructureTriggerType getTriggerType() {
        return triggerType;
    }

    public int getAdditionalPlaceObjectChance() {
        return additionalPlaceObjectChance;
    }

    public void addPlaceObjectChance(int delta) {
        additionalPlaceObjectChance += delta;
    }

    public void setAdditionalPlaceObjectChance(int additionalPlaceObjectChance) {
        this.additionalPlaceObjectChance = additionalPlaceObjectChance;
    }

    public boolean isPlacementCanceled() {
        return placementCanceled;
    }

    public void setPlacementCanceled(boolean placementCanceled) {
        this.placementCanceled = placementCanceled;
    }

    public String getForcedMob() {
        return forcedMob;
    }

    public void setForcedMob(String forcedMob) {
        this.forcedMob = forcedMob;
    }

    public int getForcedMobCount() {
        return forcedMobCount;
    }

    public void setForcedMobCount(int forcedMobCount) {
        this.forcedMobCount = forcedMobCount;
    }
}
