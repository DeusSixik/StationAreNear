package dev.sixik.stationarenear.ship.docking;

import dev.sixik.stationarenear.navigation.block.SolarNavigationTerminalBlock;
import dev.sixik.stationarenear.structures.data.StationConnector;
import dev.sixik.stationarenear.structures.data.StationPieceDefinition;
import dev.sixik.stationarenear.structures.util.StationStructureIds;
import dev.sixik.stationarenear.structures.world.StationStructureLibraryData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

public final class ShipDockingAnchorResolver {

    private static final ResourceLocation SHIP_POOL = StationStructureIds.pool("space_ship");
    private static final int FALLBACK_DOOR_OFFSET_BLOCKS = 3;
    private static final int SHIP_SEARCH_RADIUS_BLOCKS = 24;

    private ShipDockingAnchorResolver() {
    }

    public static ResolvedDockingAnchor resolve(ServerLevel level, BlockPos terminalPos, BlockState terminalState) {
        return ShipDockingAnchorSavedData.get(level)
                .anchor(terminalPos)
                .map(anchor -> new ResolvedDockingAnchor(
                        anchor.anchorPos(),
                        anchor.direction(),
                        true,
                        anchor.connectionName()
                ))
                .or(() -> bindNearbyShip(level, terminalPos).map(anchor -> new ResolvedDockingAnchor(
                        anchor.anchorPos(),
                        anchor.direction(),
                        true,
                        anchor.connectionName()
                )))
                .orElseGet(() -> fallback(terminalPos, terminalState));
    }

    public static Optional<ShipDockingAnchor> bindNearbyShip(ServerLevel level, BlockPos terminalPos) {
        Optional<ShipDockingAnchor> anchor = findNearbyShipAnchor(level, terminalPos);
        anchor.ifPresent(value -> ShipDockingAnchorSavedData.get(level).upsert(value));
        return anchor;
    }

    private static Optional<ShipDockingAnchor> findNearbyShipAnchor(ServerLevel level, BlockPos terminalPos) {
        StationStructureLibraryData library = StationStructureLibraryData.get(level);
        return library.pieces().stream()
                .filter(piece -> piece.pool().equals(SHIP_POOL))
                .flatMap(piece -> library.savedTemplateSelections().entrySet().stream()
                        .filter(entry -> entry.getKey().equals(piece.template()) || entry.getKey().equals(piece.id()))
                        .map(entry -> candidate(piece, terminalPos, entry)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .min(Comparator.comparingLong(Candidate::distanceSqr).thenComparingInt(candidate -> -candidate.connector().priority()))
                .map(candidate -> toAnchor(terminalPos, candidate));
    }

    private static Optional<Candidate> candidate(StationPieceDefinition piece, BlockPos terminalPos, Map.Entry<ResourceLocation, BoundingBox> entry) {
        BoundingBox bounds = entry.getValue();
        long distanceSqr = distanceSqrToBounds(terminalPos, bounds);
        if (!contains(bounds, terminalPos) && distanceSqr > (long) SHIP_SEARCH_RADIUS_BLOCKS * SHIP_SEARCH_RADIUS_BLOCKS) {
            return Optional.empty();
        }

        return piece.connectors().stream()
                .filter(connector -> connector.direction().getAxis().isHorizontal())
                .min(Comparator
                        .comparingLong((StationConnector connector) -> distanceSqr(terminalPos, worldConnectorPos(piece, bounds, connector)))
                        .thenComparingInt(connector -> -connector.priority()))
                .map(connector -> new Candidate(piece, bounds, connector, distanceSqr));
    }

    private static ShipDockingAnchor toAnchor(BlockPos terminalPos, Candidate candidate) {
        StationConnector connector = candidate.connector();
        return new ShipDockingAnchor(
                terminalPos,
                candidate.bounds(),
                worldConnectorPos(candidate.piece(), candidate.bounds(), connector),
                connector.direction(),
                connector.name(),
                connector.width(),
                connector.height(),
                String.join(",", connector.tags()),
                String.join(",", connector.accepts())
        );
    }

    private static BlockPos worldConnectorPos(StationPieceDefinition piece, BoundingBox bounds, StationConnector connector) {
        BlockPos localFromSelection = connector.position().subtract(piece.selectionMin());
        return new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()).offset(localFromSelection);
    }

    private static boolean contains(BoundingBox bounds, BlockPos pos) {
        return pos.getX() >= bounds.minX() && pos.getX() <= bounds.maxX()
                && pos.getY() >= bounds.minY() && pos.getY() <= bounds.maxY()
                && pos.getZ() >= bounds.minZ() && pos.getZ() <= bounds.maxZ();
    }

    private static long distanceSqrToBounds(BlockPos pos, BoundingBox bounds) {
        long dx = axisDistance(pos.getX(), bounds.minX(), bounds.maxX());
        long dy = axisDistance(pos.getY(), bounds.minY(), bounds.maxY());
        long dz = axisDistance(pos.getZ(), bounds.minZ(), bounds.maxZ());
        return dx * dx + dy * dy + dz * dz;
    }

    private static long axisDistance(int value, int min, int max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0;
    }

    private static long distanceSqr(BlockPos left, BlockPos right) {
        long dx = left.getX() - right.getX();
        long dy = left.getY() - right.getY();
        long dz = left.getZ() - right.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static ResolvedDockingAnchor fallback(BlockPos terminalPos, BlockState terminalState) {
        Direction direction = terminalState.getValue(SolarNavigationTerminalBlock.FACING);
        return new ResolvedDockingAnchor(
                terminalPos.relative(direction, FALLBACK_DOOR_OFFSET_BLOCKS),
                direction,
                false,
                "terminal_fallback"
        );
    }

    public record ResolvedDockingAnchor(BlockPos doorCenter, Direction stationDirection, boolean boundToShip, String connectionName) {
    }

    private record Candidate(StationPieceDefinition piece, BoundingBox bounds, StationConnector connector, long distanceSqr) {
    }
}
