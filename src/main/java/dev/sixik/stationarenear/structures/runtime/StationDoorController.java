package dev.sixik.stationarenear.structures.runtime;

import dev.sixik.stationarenear.navigation.world.SolarNavigationStationCleaner;
import dev.sixik.stationarenear.navigation.registry.SolarNavigationBlocks;
import dev.sixik.stationarenear.ship.block.PressureTightDoorBlock;
import dev.sixik.stationarenear.ship.block.entity.PressureTightDoorBlockEntity;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchor;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorResolver;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorSavedData;
import dev.sixik.stationarenear.ship.runtime.ShipIntegrityScanner;
import dev.sixik.stationarenear.structures.data.StationInstance;
import dev.sixik.stationarenear.structures.world.StationSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class StationDoorController {

    private StationDoorController() {
    }

    public static StationDoorResult setOpen(ServerLevel level, BlockPos terminalPos, String doorId, boolean open) {
        Optional<DoorLookup> door = findDoor(level, terminalPos, doorId);
        if (door.isEmpty()) {
            return new StationDoorResult(false, false, false, "Station door command failed: door " + doorId + " was not found on docked station.");
        }

        DoorLookup lookup = door.get();
        if (lookup.broken() && open) {
            return new StationDoorResult(false, false, lookup.open(), "Station door " + lookup.doorId() + " is broken and cannot be opened.");
        }
        if (lookup.open() == open) {
            return new StationDoorResult(true, false, open, "Station door " + lookup.doorId() + " already " + stateText(open) + ".");
        }

        boolean changed = PressureTightDoorBlock.setOpen(level, lookup.masterPos(), open);
        if (!changed) {
            return new StationDoorResult(false, false, lookup.open(), "Station door command failed: door " + lookup.doorId() + " is no longer valid.");
        }

        return new StationDoorResult(true, true, open, open ? "Station door " + lookup.doorId() + " opened." : "Station door " + lookup.doorId() + " sealed.");
    }

    public static StationDoorResult status(ServerLevel level, BlockPos terminalPos, String doorId) {
        Optional<DoorLookup> door = findDoor(level, terminalPos, doorId);
        if (door.isEmpty()) {
            return new StationDoorResult(false, false, false, "Station door command failed: door " + doorId + " was not found on docked station.");
        }

        DoorLookup lookup = door.get();
        String status = lookup.broken() ? "BROKEN" : stateText(lookup.open());
        return new StationDoorResult(true, false, lookup.open(), "Station door " + lookup.doorId() + ": " + status + ".");
    }

    private static Optional<DoorLookup> findDoor(ServerLevel level, BlockPos terminalPos, String doorId) {
        String normalizedId = normalizeDoorId(doorId);
        if (normalizedId.isBlank()) {
            return Optional.empty();
        }

        Set<Long> relatedTerminals = relatedTerminalPositions(level, terminalPos);
        StationSavedData stationData = StationSavedData.get(level);
        for (StationInstance station : stationData.stations()) {
            if (!station.customData().contains(SolarNavigationStationCleaner.KEY_NAVIGATION_TERMINAL_POS)
                    || !relatedTerminals.contains(station.customData().getLong(SolarNavigationStationCleaner.KEY_NAVIGATION_TERMINAL_POS))) {
                continue;
            }

            Optional<DoorLookup> lookup = findDoorInStation(level, station, normalizedId);
            if (lookup.isPresent()) {
                return lookup;
            }
        }
        return Optional.empty();
    }

    private static Set<Long> relatedTerminalPositions(ServerLevel level, BlockPos terminalPos) {
        Set<Long> positions = new HashSet<>();
        positions.add(terminalPos.asLong());
        Optional<ShipDockingAnchor> anchor = ShipDockingAnchorSavedData.get(level)
                .anchor(terminalPos)
                .or(() -> ShipDockingAnchorResolver.bindNearbyShip(level, terminalPos));
        if (anchor.isEmpty()) {
            return positions;
        }

        positions.addAll(ShipIntegrityScanner.relatedTerminalPositions(level, terminalPos, anchor.get()));
        for (long relatedTerminal : Set.copyOf(positions)) {
            BlockPos relatedPos = BlockPos.of(relatedTerminal);
            if (level.getBlockState(relatedPos).is(SolarNavigationBlocks.SOLAR_NAVIGATION_TERMINAL.get())) {
                positions.add(relatedPos.asLong());
            }
        }
        return positions;
    }

    private static Optional<DoorLookup> findDoorInStation(ServerLevel level, StationInstance station, String normalizedId) {
        for (var piece : station.pieces()) {
            BoundingBox bounds = piece.bounds();
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                        mutable.set(x, y, z);
                        BlockState state = level.getBlockState(mutable);
                        if (!(state.getBlock() instanceof PressureTightDoorBlock) || !PressureTightDoorBlock.isMaster(state)) {
                            continue;
                        }

                        BlockEntity blockEntity = level.getBlockEntity(mutable);
                        if (!(blockEntity instanceof PressureTightDoorBlockEntity door)) {
                            continue;
                        }
                        if (!normalizeDoorId(door.doorId()).equals(normalizedId)) {
                            continue;
                        }

                        BlockPos masterPos = mutable.immutable();
                        return Optional.of(new DoorLookup(masterPos, door.doorId(), PressureTightDoorBlock.isOpen(state), PressureTightDoorBlock.isBroken(state)));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static String normalizeDoorId(String doorId) {
        return doorId == null ? "" : doorId.trim().toUpperCase(Locale.ROOT);
    }

    private static String stateText(boolean open) {
        return open ? "OPEN" : "SEALED";
    }

    public record StationDoorResult(boolean success, boolean changed, boolean open, String message) {
    }

    private record DoorLookup(BlockPos masterPos, String doorId, boolean open, boolean broken) {
    }
}
