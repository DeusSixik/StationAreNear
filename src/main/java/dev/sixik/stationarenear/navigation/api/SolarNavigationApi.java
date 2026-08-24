package dev.sixik.stationarenear.navigation.api;

import dev.sixik.stationarenear.navigation.SolarNavigationProceduralMap;
import dev.sixik.stationarenear.navigation.block.SolarNavigationTerminalBlock;
import dev.sixik.stationarenear.navigation.data.SolarNavigationQuestMarker;
import dev.sixik.stationarenear.navigation.data.SolarNavigationShipState;
import dev.sixik.stationarenear.navigation.data.SolarNavigationStationInfo;
import dev.sixik.stationarenear.navigation.network.SolarNavigationNetwork;
import dev.sixik.stationarenear.navigation.world.SolarNavigationSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class SolarNavigationApi {

    private SolarNavigationApi() {
    }

    public static SolarNavigationQuestMarker createQuestDungeon(ServerLevel level, String id, String name, float x, float y, int arrowColor) {
        long seed = level.getSeed() ^ id.hashCode() ^ Mth.getSeed((int) x, 0, (int) y);
        return createQuestDungeon(level, id, name, x, y, 72.0F, arrowColor, seed);
    }

    public static SolarNavigationQuestMarker createQuestDungeon(ServerLevel level, String id, String name, float x, float y, float radius, int arrowColor, long seed) {
        SolarNavigationQuestMarker marker = new SolarNavigationQuestMarker(id, name, x, y, radius, arrowColor, seed);
        SolarNavigationSavedData.get(level).upsertQuestMarker(marker);
        SolarNavigationNetwork.syncQuestMarkers(level);
        return marker;
    }

    public static boolean removeQuestDungeon(ServerLevel level, String id) {
        boolean removed = SolarNavigationSavedData.get(level).removeQuestMarker(id);
        if (removed) {
            SolarNavigationNetwork.syncQuestMarkers(level);
        }
        return removed;
    }

    public static Optional<SolarNavigationQuestMarker> questDungeon(ServerLevel level, String id) {
        return SolarNavigationSavedData.get(level).questMarker(id);
    }


    public static SolarNavigationShipState shipState(ServerLevel level, BlockPos terminalPos) {
        return SolarNavigationSavedData.get(level).shipState(terminalPos);
    }

    public static List<SolarNavigationStationInfo> nearbyStations(ServerLevel level, BlockPos terminalPos, float radius, int limit) {
        SolarNavigationSavedData data = SolarNavigationSavedData.get(level);
        return nearbyStations(level, terminalPos, data.shipState(terminalPos), radius, limit);
    }

    public static List<SolarNavigationStationInfo> nearbyStations(ServerLevel level, BlockPos terminalPos, SolarNavigationShipState shipState, float radius, int limit) {
        return SolarNavigationProceduralMap.nearbyStations(
                SolarNavigationTerminalBlock.terminalSeed(level, terminalPos),
                shipState,
                SolarNavigationSavedData.get(level).questMarkers(),
                radius,
                limit
        );
    }

    public static Collection<SolarNavigationQuestMarker> questDungeons(ServerLevel level) {
        return SolarNavigationSavedData.get(level).questMarkers();
    }
}
