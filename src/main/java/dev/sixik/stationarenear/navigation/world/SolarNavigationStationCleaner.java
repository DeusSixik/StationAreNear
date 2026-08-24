package dev.sixik.stationarenear.navigation.world;

import dev.sixik.stationarenear.structures.data.StationInstance;
import dev.sixik.stationarenear.structures.network.StationStructureNetwork;
import dev.sixik.stationarenear.structures.world.StationSavedData;
import dev.sixik.stationarenear.navigation.data.SolarNavigationDockedStation;
import dev.sixik.stationarenear.navigation.data.SolarNavigationShipState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

public final class SolarNavigationStationCleaner {

    public static final String KEY_NAVIGATION_TERMINAL_POS = "solarNavigationTerminalPos";
    public static final String KEY_NAVIGATION_STATION_SEED = "solarNavigationStationSeed";
    public static final String KEY_NAVIGATION_STATION_NAME = "solarNavigationStationName";
    public static final String KEY_NAVIGATION_STATION_X = "solarNavigationStationX";
    public static final String KEY_NAVIGATION_STATION_Y = "solarNavigationStationY";

    private SolarNavigationStationCleaner() {
    }

    public static int clearFarFromShip(ServerLevel level, BlockPos terminalPos, SolarNavigationShipState shipState, float unloadDistance) {
        StationSavedData data = StationSavedData.get(level);
        List<StationInstance> targets = new ArrayList<>();
        float unloadDistanceSq = unloadDistance * unloadDistance;
        for (StationInstance station : data.stations()) {
            if (!station.customData().contains(KEY_NAVIGATION_TERMINAL_POS)
                    || station.customData().getLong(KEY_NAVIGATION_TERMINAL_POS) != terminalPos.asLong()
                    || !station.customData().contains(KEY_NAVIGATION_STATION_X)
                    || !station.customData().contains(KEY_NAVIGATION_STATION_Y)) {
                continue;
            }
            float stationX = station.customData().getFloat(KEY_NAVIGATION_STATION_X);
            float stationY = station.customData().getFloat(KEY_NAVIGATION_STATION_Y);
            float dx = shipState.shipX() - stationX;
            float dy = shipState.shipY() - stationY;
            if (dx * dx + dy * dy > unloadDistanceSq) {
                targets.add(station);
            }
        }
        return clearStations(level, data, targets);
    }

    public static List<SolarNavigationDockedStation> dockedStations(ServerLevel level, BlockPos terminalPos) {
        StationSavedData data = StationSavedData.get(level);
        List<SolarNavigationDockedStation> stations = new ArrayList<>();
        for (StationInstance station : data.stations()) {
            if (!station.customData().contains(KEY_NAVIGATION_TERMINAL_POS)
                    || station.customData().getLong(KEY_NAVIGATION_TERMINAL_POS) != terminalPos.asLong()
                    || !station.customData().contains(KEY_NAVIGATION_STATION_SEED)
                    || !station.customData().contains(KEY_NAVIGATION_STATION_X)
                    || !station.customData().contains(KEY_NAVIGATION_STATION_Y)) {
                continue;
            }

            stations.add(new SolarNavigationDockedStation(
                    station.customData().getLong(KEY_NAVIGATION_STATION_SEED),
                    station.customData().contains(KEY_NAVIGATION_STATION_NAME) ? station.customData().getString(KEY_NAVIGATION_STATION_NAME) : "Unknown Station",
                    station.customData().getFloat(KEY_NAVIGATION_STATION_X),
                    station.customData().getFloat(KEY_NAVIGATION_STATION_Y)
            ));
        }
        return List.copyOf(stations);
    }

    public static boolean hasDockedStation(ServerLevel level, BlockPos terminalPos, long navigationStationSeed) {
        StationSavedData data = StationSavedData.get(level);
        for (StationInstance station : data.stations()) {
            if (station.customData().contains(KEY_NAVIGATION_TERMINAL_POS)
                    && station.customData().getLong(KEY_NAVIGATION_TERMINAL_POS) == terminalPos.asLong()
                    && station.customData().contains(KEY_NAVIGATION_STATION_SEED)
                    && station.customData().getLong(KEY_NAVIGATION_STATION_SEED) == navigationStationSeed) {
                return true;
            }
        }
        return false;
    }

    public static int clearByTerminal(ServerLevel level, BlockPos terminalPos) {
        StationSavedData data = StationSavedData.get(level);
        List<StationInstance> targets = new ArrayList<>();
        for (StationInstance station : data.stations()) {
            if (station.customData().contains(KEY_NAVIGATION_TERMINAL_POS)
                    && station.customData().getLong(KEY_NAVIGATION_TERMINAL_POS) == terminalPos.asLong()) {
                targets.add(station);
            }
        }
        return clearStations(level, data, targets);
    }

    public static int clearByNavigationSeed(ServerLevel level, long navigationStationSeed) {
        StationSavedData data = StationSavedData.get(level);
        List<StationInstance> targets = new ArrayList<>();
        for (StationInstance station : data.stations()) {
            if (station.customData().contains(KEY_NAVIGATION_STATION_SEED)
                    && station.customData().getLong(KEY_NAVIGATION_STATION_SEED) == navigationStationSeed) {
                targets.add(station);
            }
        }

        return clearStations(level, data, targets);
    }

    private static int clearStations(ServerLevel level, StationSavedData data, List<StationInstance> targets) {
        for (StationInstance station : targets) {
            for (var piece : station.pieces()) {
                clearBounds(level, piece.bounds());
            }
            data.removeStation(station.id());
        }
        if (!targets.isEmpty()) {
            StationStructureNetwork.syncTemplateSelections(level);
        }
        return targets.size();
    }

    private static void clearBounds(ServerLevel level, BoundingBox bounds) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    level.setBlock(mutable.set(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }
}
