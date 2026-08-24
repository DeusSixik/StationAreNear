package dev.sixik.stationarenear.ship.runtime;

import dev.sixik.stationarenear.navigation.registry.SolarNavigationBlocks;
import dev.sixik.stationarenear.navigation.world.SolarNavigationStationCleaner;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchor;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorResolver;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorSavedData;
import dev.sixik.stationarenear.structures.data.StationInstance;
import dev.sixik.stationarenear.structures.world.StationSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class ShipIntegrityScanner {

    private static final int DOCKED_SCAN_PADDING = 96;
    private static final int MAX_FLOOD_VISITS = 65_536;

    private ShipIntegrityScanner() {
    }

    public static IntegrityReport inspect(ServerLevel level, BlockPos terminalPos) {
        Optional<ShipDockingAnchor> anchor = ShipDockingAnchorSavedData.get(level)
                .anchor(terminalPos)
                .or(() -> ShipDockingAnchorResolver.bindNearbyShip(level, terminalPos));
        if (anchor.isEmpty()) {
            return new IntegrityReport(false, false, false, false, "ship_not_bound");
        }

        ShipDockingAnchor value = anchor.get();
        boolean docked = isDocked(level, terminalPos, value);
        boolean doorOpen = isDoorOpen(level, value);
        BlockPos navigationTerminal = navigationTerminal(level, terminalPos, value);
        ClosureResult closure = closedSystem(level, value, navigationTerminal, docked);
        boolean hullBreach = !closure.sealed();
        String reason = closure.sealed() ? "sealed" : closure.reason();
        if (hullBreach && doorOpen && !docked) {
            reason = "door_open_to_space";
        }
        return new IntegrityReport(hullBreach, hullBreach, doorOpen, docked, reason);
    }

    public static boolean containsShipBlock(ServerLevel level, BlockPos blockPos) {
        for (ShipDockingAnchor anchor : ShipDockingAnchorSavedData.get(level).anchors()) {
            if (contains(anchor.shipBounds(), blockPos)) {
                return true;
            }
        }
        return false;
    }

    public static Set<BlockPos> terminalsForBlock(ServerLevel level, BlockPos blockPos) {
        Set<BlockPos> terminals = new HashSet<>();
        for (ShipDockingAnchor anchor : ShipDockingAnchorSavedData.get(level).anchors()) {
            if (contains(anchor.shipBounds(), blockPos)) {
                terminals.add(anchor.terminalPos());
            }
        }
        return terminals;
    }

    private static ClosureResult closedSystem(ServerLevel level, ShipDockingAnchor anchor, BlockPos navigationTerminal, boolean docked) {
        BoundingBox limit = docked ? expand(anchor.shipBounds(), DOCKED_SCAN_PADDING) : anchor.shipBounds();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();

        for (Direction direction : Direction.values()) {
            BlockPos start = navigationTerminal.relative(direction);
            if (isOpenSpace(level, start)) {
                queue.add(start);
            }
        }

        if (queue.isEmpty()) {
            return ClosureResult.closedResult();
        }

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (!contains(limit, current)) {
                return ClosureResult.openResult("open_to_space");
            }
            if (!isOpenSpace(level, current) || !visited.add(current.immutable())) {
                continue;
            }
            if (visited.size() > MAX_FLOOD_VISITS) {
                return ClosureResult.openResult("unbounded_air_volume");
            }

            for (Direction direction : Direction.values()) {
                queue.add(current.relative(direction));
            }
        }

        return ClosureResult.closedResult();
    }

    private static boolean isOpenSpace(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir();
    }

    private static BlockPos navigationTerminal(ServerLevel level, BlockPos terminalPos, ShipDockingAnchor anchor) {
        if (level.getBlockState(terminalPos).is(SolarNavigationBlocks.SOLAR_NAVIGATION_TERMINAL.get())) {
            return terminalPos;
        }
        for (Long relatedTerminal : relatedTerminalPositions(level, terminalPos, anchor)) {
            BlockPos relatedPos = BlockPos.of(relatedTerminal);
            if (level.getBlockState(relatedPos).is(SolarNavigationBlocks.SOLAR_NAVIGATION_TERMINAL.get())) {
                return relatedPos;
            }
        }
        return terminalPos;
    }

    private static boolean isDoorOpen(ServerLevel level, ShipDockingAnchor anchor) {
        for (BlockPos pos : dockingAperture(anchor)) {
            if (level.getBlockState(pos).isAir()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDocked(ServerLevel level, BlockPos terminalPos, ShipDockingAnchor anchor) {
        Set<Long> relatedTerminals = relatedTerminalPositions(level, terminalPos, anchor);
        for (StationInstance station : StationSavedData.get(level).stations()) {
            if (!station.customData().contains(SolarNavigationStationCleaner.KEY_NAVIGATION_TERMINAL_POS)) {
                continue;
            }
            long navigationTerminal = station.customData().getLong(SolarNavigationStationCleaner.KEY_NAVIGATION_TERMINAL_POS);
            if (relatedTerminals.contains(navigationTerminal)) {
                return true;
            }
        }
        return false;
    }

    public static Set<Long> relatedTerminalPositions(ServerLevel level, BlockPos terminalPos, ShipDockingAnchor anchor) {
        Set<Long> terminals = new HashSet<>();
        terminals.add(terminalPos.asLong());
        terminals.add(anchor.terminalPos().asLong());
        for (ShipDockingAnchor other : ShipDockingAnchorSavedData.get(level).anchors()) {
            if (sameBounds(anchor.shipBounds(), other.shipBounds())) {
                terminals.add(other.terminalPos().asLong());
            }
        }
        return terminals;
    }

    private static Iterable<BlockPos> dockingAperture(ShipDockingAnchor anchor) {
        Set<BlockPos> positions = new HashSet<>();
        Direction side = anchor.direction().getClockWise();
        int minSide = -anchor.width() / 2;
        int maxSide = anchor.width() - anchor.width() / 2 - 1;
        int minY = -anchor.height() / 2;
        int maxY = anchor.height() - anchor.height() / 2 - 1;
        for (int sideOffset = minSide; sideOffset <= maxSide; sideOffset++) {
            for (int yOffset = minY; yOffset <= maxY; yOffset++) {
                positions.add(anchor.anchorPos().relative(side, sideOffset).above(yOffset));
            }
        }
        return positions;
    }

    private static BoundingBox expand(BoundingBox bounds, int padding) {
        return new BoundingBox(
                bounds.minX() - padding,
                bounds.minY() - padding,
                bounds.minZ() - padding,
                bounds.maxX() + padding,
                bounds.maxY() + padding,
                bounds.maxZ() + padding
        );
    }

    private static boolean contains(BoundingBox bounds, BlockPos pos) {
        return pos.getX() >= bounds.minX() && pos.getX() <= bounds.maxX()
                && pos.getY() >= bounds.minY() && pos.getY() <= bounds.maxY()
                && pos.getZ() >= bounds.minZ() && pos.getZ() <= bounds.maxZ();
    }

    private static boolean sameBounds(BoundingBox left, BoundingBox right) {
        return left.minX() == right.minX()
                && left.minY() == right.minY()
                && left.minZ() == right.minZ()
                && left.maxX() == right.maxX()
                && left.maxY() == right.maxY()
                && left.maxZ() == right.maxZ();
    }

    public record IntegrityReport(boolean decompressed, boolean hullBreach, boolean doorOpen, boolean docked, String reason) {
    }

    private record ClosureResult(boolean sealed, String reason) {
        private static ClosureResult closedResult() {
            return new ClosureResult(true, "sealed");
        }

        private static ClosureResult openResult(String reason) {
            return new ClosureResult(false, reason);
        }
    }
}
