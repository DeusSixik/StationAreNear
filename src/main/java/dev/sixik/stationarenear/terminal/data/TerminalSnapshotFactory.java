package dev.sixik.stationarenear.terminal.data;

import dev.sixik.stationarenear.navigation.SolarNavigationProceduralMap;
import dev.sixik.stationarenear.navigation.block.SolarNavigationTerminalBlock;
import dev.sixik.stationarenear.navigation.data.SolarNavigationShipState;
import dev.sixik.stationarenear.navigation.data.SolarNavigationStationInfo;
import dev.sixik.stationarenear.navigation.registry.SolarNavigationBlocks;
import dev.sixik.stationarenear.navigation.world.SolarNavigationSavedData;
import dev.sixik.stationarenear.ship.data.ShipState;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchor;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorResolver;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorSavedData;
import dev.sixik.stationarenear.ship.runtime.ShipIntegrityScanner;
import dev.sixik.stationarenear.ship.runtime.ShipManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

public final class TerminalSnapshotFactory {

    private static final float STATION_SCAN_RADIUS = 2400.0F;
    private static final int STATION_SCAN_LIMIT = 12;

    private TerminalSnapshotFactory() {
    }

    public static ShipTerminalSnapshot create(ServerLevel level, BlockPos terminalPos) {
        Optional<ShipDockingAnchor> anchor = ShipDockingAnchorSavedData.get(level)
                .anchor(terminalPos)
                .or(() -> ShipDockingAnchorResolver.bindNearbyShip(level, terminalPos));
        BlockPos shipStateTerminal = ShipManager.stateTerminal(level, terminalPos);
        BlockPos navigationTerminal = resolveNavigationTerminal(level, terminalPos, anchor.orElse(null));
        SolarNavigationSavedData navigationData = SolarNavigationSavedData.get(level);
        SolarNavigationShipState navigationState = navigationData.shipState(navigationTerminal);
        ShipIntegrityScanner.IntegrityReport integrity = ShipManager.updateDecompression(level, terminalPos);
        ShipState shipState = ShipManager.state(level, terminalPos);
        List<SolarNavigationStationInfo> stations = SolarNavigationProceduralMap.nearbyStations(
                SolarNavigationTerminalBlock.terminalSeed(level, navigationTerminal),
                navigationState,
                navigationData.questMarkers(),
                STATION_SCAN_RADIUS,
                STATION_SCAN_LIMIT
        );

        return new ShipTerminalSnapshot(
                terminalPos,
                shipStateTerminal,
                navigationTerminal,
                anchor.isPresent(),
                integrity.docked(),
                integrity.doorOpen(),
                integrity.hullBreach(),
                shipState,
                navigationState,
                stations
        );
    }

    private static BlockPos resolveNavigationTerminal(ServerLevel level, BlockPos terminalPos, ShipDockingAnchor anchor) {
        if (isSolarNavigationTerminal(level, terminalPos)) {
            return terminalPos;
        }
        if (anchor == null) {
            return terminalPos;
        }
        for (Long relatedTerminal : ShipIntegrityScanner.relatedTerminalPositions(level, terminalPos, anchor)) {
            BlockPos relatedPos = BlockPos.of(relatedTerminal);
            if (isSolarNavigationTerminal(level, relatedPos)) {
                return relatedPos;
            }
        }
        return terminalPos;
    }

    private static boolean isSolarNavigationTerminal(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(SolarNavigationBlocks.SOLAR_NAVIGATION_TERMINAL.get());
    }
}
