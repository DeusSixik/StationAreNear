package dev.sixik.stationarenear.structures.runtime;

import dev.sixik.stationarenear.navigation.registry.SolarNavigationBlocks;
import dev.sixik.stationarenear.navigation.world.SolarNavigationStationCleaner;
import dev.sixik.stationarenear.ship.block.PressureTightDoorBlock;
import dev.sixik.stationarenear.ship.block.entity.PressureTightDoorBlockEntity;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchor;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorResolver;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorSavedData;
import dev.sixik.stationarenear.ship.runtime.ShipIntegrityScanner;
import dev.sixik.stationarenear.structures.data.PlacedStationPiece;
import dev.sixik.stationarenear.structures.data.PlacedTriggerZone;
import dev.sixik.stationarenear.structures.data.StationInstance;
import dev.sixik.stationarenear.structures.world.StationSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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
        if (doorId == null || doorId.isBlank()) {
            return Optional.empty();
        }

        Set<Long> relatedTerminals = relatedTerminalPositions(level, terminalPos);
        StationSavedData stationData = StationSavedData.get(level);

        for (StationInstance station : stationData.stations()) {
            if (station.customData().contains(SolarNavigationStationCleaner.KEY_NAVIGATION_TERMINAL_POS)
                    && relatedTerminals.contains(station.customData().getLong(SolarNavigationStationCleaner.KEY_NAVIGATION_TERMINAL_POS))) {
                Optional<DoorLookup> lookup = findDoorInStation(level, station, doorId);
                if (lookup.isPresent()) {
                    return lookup;
                }
            }
        }

        List<StationInstance> allStations = new ArrayList<>(stationData.stations());
        allStations.sort(Comparator.comparingDouble(s -> s.shuttleDoorCenter().distSqr(terminalPos)));
        for (StationInstance station : allStations) {
            Optional<DoorLookup> lookup = findDoorInStation(level, station, doorId);
            if (lookup.isPresent()) {
                return lookup;
            }
        }

        return findNearbyDoor(level, terminalPos, doorId);
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

    private static Optional<DoorLookup> findDoorInStation(ServerLevel level, StationInstance station, String queryDoorId) {
        for (PlacedStationPiece piece : station.pieces()) {
            for (PlacedTriggerZone zone : piece.triggerZones()) {
                BlockPos masterPos = doorMasterPos(zone);
                Optional<DoorLookup> lookup = checkDoorAt(level, masterPos, queryDoorId);
                if (lookup.isPresent()) {
                    return lookup;
                }
            }
        }

        Set<Long> checkedChunks = new HashSet<>();
        for (PlacedStationPiece piece : station.pieces()) {
            BoundingBox bounds = piece.bounds();
            int minChunkX = bounds.minX() >> 4;
            int maxChunkX = bounds.maxX() >> 4;
            int minChunkZ = bounds.minZ() >> 4;
            int maxChunkZ = bounds.maxZ() >> 4;

            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    long chunkKey = ChunkPos.asLong(cx, cz);
                    if (!checkedChunks.add(chunkKey)) {
                        continue;
                    }
                    if (!level.hasChunk(cx, cz)) {
                        continue;
                    }
                    LevelChunk chunk = level.getChunk(cx, cz);
                    for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                        BlockEntity be = chunk.getBlockEntity(pos);
                        if (be instanceof PressureTightDoorBlockEntity door) {
                            String actualId = door.doorId();
                            if (actualId.isBlank()) {
                                actualId = PressureTightDoorBlock.generateDoorId(station.seed(), pos);
                                door.setDoorId(actualId);
                            }
                            if (matchesDoorId(actualId, queryDoorId)) {
                                BlockState state = chunk.getBlockState(pos);
                                if (state.getBlock() instanceof PressureTightDoorBlock && PressureTightDoorBlock.isMaster(state)) {
                                    return Optional.of(new DoorLookup(pos, actualId, PressureTightDoorBlock.isOpen(state), PressureTightDoorBlock.isBroken(state)));
                                }
                            }
                        }
                    }
                }
            }
        }

        for (PlacedStationPiece piece : station.pieces()) {
            BoundingBox bounds = piece.bounds();
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                        mutable.set(x, y, z);
                        Optional<DoorLookup> lookup = checkDoorAt(level, mutable, queryDoorId);
                        if (lookup.isPresent()) {
                            return lookup;
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static Optional<DoorLookup> findNearbyDoor(ServerLevel level, BlockPos center, String queryDoorId) {
        int chunkRadius = 8;
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;

        for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
            for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                    BlockEntity be = chunk.getBlockEntity(pos);
                    if (be instanceof PressureTightDoorBlockEntity door) {
                        String actualId = door.doorId();
                        if (actualId.isBlank()) {
                            actualId = PressureTightDoorBlock.generateDoorId(level.getSeed(), pos);
                            door.setDoorId(actualId);
                        }
                        if (matchesDoorId(actualId, queryDoorId)) {
                            BlockState state = chunk.getBlockState(pos);
                            if (state.getBlock() instanceof PressureTightDoorBlock && PressureTightDoorBlock.isMaster(state)) {
                                return Optional.of(new DoorLookup(pos, actualId, PressureTightDoorBlock.isOpen(state), PressureTightDoorBlock.isBroken(state)));
                            }
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<DoorLookup> checkDoorAt(ServerLevel level, BlockPos pos, String queryDoorId) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PressureTightDoorBlock) || !PressureTightDoorBlock.isMaster(state)) {
            return Optional.empty();
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof PressureTightDoorBlockEntity door)) {
            return Optional.empty();
        }

        String actualId = door.doorId();
        if (actualId.isBlank()) {
            actualId = PressureTightDoorBlock.generateDoorId(level.getSeed(), pos);
            door.setDoorId(actualId);
        }

        if (!matchesDoorId(actualId, queryDoorId)) {
            return Optional.empty();
        }

        BlockPos masterPos = pos.immutable();
        return Optional.of(new DoorLookup(masterPos, actualId, PressureTightDoorBlock.isOpen(state), PressureTightDoorBlock.isBroken(state)));
    }

    private static BlockPos doorMasterPos(PlacedTriggerZone zone) {
        int x = Math.floorDiv(zone.min().getX() + zone.max().getX(), 2);
        int y = zone.min().getY();
        int z = Math.floorDiv(zone.min().getZ() + zone.max().getZ(), 2);
        return new BlockPos(x, y, z);
    }

    private static boolean matchesDoorId(String actualDoorId, String queryDoorId) {
        if (actualDoorId == null || queryDoorId == null) {
            return false;
        }
        if (actualDoorId.equalsIgnoreCase(queryDoorId)) {
            return true;
        }
        String normActual = normalizeDoorId(actualDoorId);
        String normQuery = normalizeDoorId(queryDoorId);
        return !normActual.isBlank() && normActual.equals(normQuery);
    }

    private static String normalizeDoorId(String doorId) {
        if (doorId == null) {
            return "";
        }
        String normalized = doorId.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("DR-")) {
            return normalized;
        }
        if (normalized.startsWith("DR_")) {
            return "DR-" + normalized.substring(3);
        }
        if (normalized.startsWith("DR")) {
            return "DR-" + normalized.substring(2);
        }
        return "DR-" + normalized;
    }

    private static String stateText(boolean open) {
        return open ? "OPEN" : "SEALED";
    }

    public record StationDoorResult(boolean success, boolean changed, boolean open, String message) {
    }

    private record DoorLookup(BlockPos masterPos, String doorId, boolean open, boolean broken) {
    }
}
