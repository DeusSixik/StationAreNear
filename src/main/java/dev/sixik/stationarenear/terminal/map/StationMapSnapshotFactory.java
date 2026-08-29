package dev.sixik.stationarenear.terminal.map;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.navigation.StationCodeGenerator;
import dev.sixik.stationarenear.navigation.world.SolarNavigationStationCleaner;
import dev.sixik.stationarenear.quest.data.QuestObjectiveState;
import dev.sixik.stationarenear.quest.world.QuestSavedData;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchor;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorResolver;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorSavedData;
import dev.sixik.stationarenear.ship.runtime.ShipIntegrityScanner;
import dev.sixik.stationarenear.structures.data.PlacedStationPiece;
import dev.sixik.stationarenear.structures.data.PlacedTriggerZone;
import dev.sixik.stationarenear.structures.data.StationConnector;
import dev.sixik.stationarenear.structures.data.StationPieceDefinition;
import dev.sixik.stationarenear.structures.generation.StationPlacementUtil;
import dev.sixik.stationarenear.structures.data.StationInstance;
import dev.sixik.stationarenear.structures.world.StationSavedData;
import dev.sixik.stationarenear.structures.world.StationStructureLibraryData;
import dev.sixik.stationarenear.terminal.map.data.StationMapConnection;
import dev.sixik.stationarenear.terminal.map.data.StationMapPiece;
import dev.sixik.stationarenear.terminal.map.data.StationMapSnapshot;
import dev.sixik.stationarenear.terminal.map.model.StationMapData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

public final class StationMapSnapshotFactory {

    private static final int FLOOR_HEIGHT = 16;
    private static final int PLAYER_SHIP_MAP_HALF_SIZE = 2;
    private static final ResourceLocation PLAYER_SHIP_MAP_ID = ResourceLocation.tryParse(StationAreNear.MODID + ":player_ship");
    private static final String KEY_TARGET_TRIGGER_IDS = "targetTriggerIds";
    private static final String KEY_COMPLETED_TARGET_TRIGGER_IDS = "completedTargetTriggerIds";

    private StationMapSnapshotFactory() {
    }

    public static Optional<StationMapSnapshot> create(ServerLevel level, BlockPos terminalPos) {
        Optional<ShipDockingAnchor> anchor = ShipDockingAnchorSavedData.get(level)
                .anchor(terminalPos)
                .or(() -> ShipDockingAnchorResolver.bindNearbyShip(level, terminalPos));
        if (anchor.isEmpty()) {
            return Optional.empty();
        }

        Set<Long> relatedTerminals = ShipIntegrityScanner.relatedTerminalPositions(level, terminalPos, anchor.get());
        Optional<StationInstance> dockedStation = StationSavedData.get(level).stations().stream()
                .filter(station -> station.customData().contains(SolarNavigationStationCleaner.KEY_NAVIGATION_TERMINAL_POS))
                .filter(station -> relatedTerminals.contains(station.customData().getLong(SolarNavigationStationCleaner.KEY_NAVIGATION_TERMINAL_POS)))
                .findFirst();
        if (dockedStation.isEmpty()) {
            return Optional.empty();
        }

        StationInstance station = dockedStation.get();
        StationStructureLibraryData library = StationStructureLibraryData.get(level);
        Set<String> questTargetTriggers = activeQuestTargetTriggers(level, station);
        int dockY = station.shuttleDoorCenter().getY();
        int dockX = station.shuttleDoorCenter().getX();
        int dockZ = station.shuttleDoorCenter().getZ();
        List<StationMapPiece> pieces = new ArrayList<>();
        int minFloor = 0;
        int maxFloor = 0;
        for (PlacedStationPiece piece : station.pieces()) {
            if (!hasPlacedBlocks(level, piece.bounds())) continue;
            BoundingBox bounds = piece.selectionBounds();
            int pieceMinFloor = floorIndex(bounds.minY(), dockY);
            int pieceMaxFloor = floorIndex(bounds.maxY(), dockY);
            boolean dockPiece = false;
            List<StationConnector> visibleConnectors = visibleConnectors(library, piece);
            List<StationMapConnection> connections = mapConnections(visibleConnectors, dockY);
            pieces.add(new StationMapPiece(
                    piece.definitionId(),
                    pieceMinFloor,
                    pieceMaxFloor,
                    bounds.minX(),
                    bounds.minZ(),
                    bounds.maxX(),
                    bounds.maxZ(),
                    dockPiece,
                    connections,
                    questPieceMarker(piece, questTargetTriggers)
            ));
            minFloor = Math.min(minFloor, pieceMinFloor);
            maxFloor = Math.max(maxFloor, pieceMaxFloor);
        }

        if (pieces.isEmpty()) {
            return Optional.empty();
        }

        pieces.add(playerShipPiece(station.shuttleDoorCenter(), station.stationDirection()));

        pieces.sort(Comparator.comparingInt(StationMapPiece::minFloor)
                .thenComparingInt(StationMapPiece::minX)
                .thenComparingInt(StationMapPiece::minZ));
        String code = station.customData().getString(SolarNavigationStationCleaner.KEY_NAVIGATION_STATION_CODE);
        if (code.isBlank()) {
            code = StationCodeGenerator.code(station.id());
        }
        return Optional.of(new StationMapSnapshot(terminalPos, station.id(), code, dockY, dockX, dockZ, minFloor, maxFloor, pieces));
    }


    public static Optional<StationMapData> createData(ServerLevel level, BlockPos terminalPos) {
        Optional<ShipDockingAnchor> anchor = ShipDockingAnchorSavedData.get(level)
                .anchor(terminalPos)
                .or(() -> ShipDockingAnchorResolver.bindNearbyShip(level, terminalPos));
        if (anchor.isEmpty()) {
            return Optional.empty();
        }

        Set<Long> relatedTerminals = ShipIntegrityScanner.relatedTerminalPositions(level, terminalPos, anchor.get());
        Optional<StationInstance> dockedStation = StationSavedData.get(level).stations().stream()
                .filter(station -> station.customData().contains(SolarNavigationStationCleaner.KEY_NAVIGATION_TERMINAL_POS))
                .filter(station -> relatedTerminals.contains(station.customData().getLong(SolarNavigationStationCleaner.KEY_NAVIGATION_TERMINAL_POS)))
                .findFirst();
        if (dockedStation.isEmpty()) {
            return Optional.empty();
        }

        StationInstance station = dockedStation.get();
        StationStructureLibraryData library = StationStructureLibraryData.get(level);
        int dockX = station.shuttleDoorCenter().getX();
        int dockY = station.shuttleDoorCenter().getY();
        int dockZ = station.shuttleDoorCenter().getZ();
        int minFloor = 0;
        int maxFloor = 0;
        List<StationMapData.Room> rooms = new ArrayList<>();
        for (PlacedStationPiece piece : station.pieces()) {
            if (!hasPlacedBlocks(level, piece.bounds())) continue;
            BoundingBox bounds = piece.selectionBounds();
            int pieceMinFloor = floorIndex(bounds.minY(), dockY);
            int pieceMaxFloor = floorIndex(bounds.maxY(), dockY);
            boolean dockPiece = false;
            List<StationConnector> visibleConnectors = visibleConnectors(library, piece);
            rooms.add(new StationMapData.Room(
                    piece.definitionId().toString(),
                    piece.template().toString(),
                    pieceMinFloor,
                    pieceMaxFloor,
                    dataBox(piece.bounds()),
                    dataBox(bounds),
                    localDataBox(bounds, dockX, dockY, dockZ),
                    dockPiece,
                    mapPassages(visibleConnectors, dockX, dockY, dockZ)
            ));
            minFloor = Math.min(minFloor, pieceMinFloor);
            maxFloor = Math.max(maxFloor, pieceMaxFloor);
        }

        if (rooms.isEmpty()) {
            return Optional.empty();
        }

        rooms.add(playerShipRoom(station.shuttleDoorCenter(), station.stationDirection()));

        String code = station.customData().getString(SolarNavigationStationCleaner.KEY_NAVIGATION_STATION_CODE);
        if (code.isBlank()) {
            code = StationCodeGenerator.code(station.id());
        }
        return Optional.of(new StationMapData(
                station.id().toString(),
                code,
                station.pool().toString(),
                station.danger(),
                station.seed(),
                new StationMapData.Point3i(dockX, dockY, dockZ),
                minFloor,
                maxFloor,
                rooms
        ));
    }

    private static boolean hasPlacedBlocks(ServerLevel level, BoundingBox bounds) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    if (!level.getBlockState(mutable.set(x, y, z)).isAir()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static List<StationConnector> visibleConnectors(StationStructureLibraryData library, PlacedStationPiece piece) {
        Optional<StationPieceDefinition> definition = library.piece(piece.definitionId());
        if (definition.isEmpty() || definition.get().connectors().size() <= 1) {
            return List.of();
        }

        List<StationConnector> connectors = new ArrayList<>();
        for (StationConnector connector : definition.get().connectors()) {
            StationConnector transformed = StationPlacementUtil.transformConnector(connector, piece.origin(), piece.rotation());
            if (transformed.direction().getAxis().isVertical() || piece.openConnectors().contains(transformed)) {
                continue;
            }
            connectors.add(transformed);
        }
        return connectors;
    }

    private static List<StationMapConnection> mapConnections(List<StationConnector> connectors, int dockY) {
        List<StationMapConnection> connections = new ArrayList<>(connectors.size());
        for (StationConnector connector : connectors) {
            connections.add(new StationMapConnection(
                    floorIndex(connector.position().getY(), dockY),
                    connector.position().getX(),
                    connector.position().getZ(),
                    connector.direction()
            ));
        }
        return connections;
    }

    private static List<StationMapData.Passage> mapPassages(List<StationConnector> connectors, int dockX, int dockY, int dockZ) {
        List<StationMapData.Passage> passages = new ArrayList<>(connectors.size());
        for (StationConnector connector : connectors) {
            passages.add(new StationMapData.Passage(
                    connector.name(),
                    floorIndex(connector.position().getY(), dockY),
                    dataPoint(connector.position()),
                    localDataPoint(connector.position(), dockX, dockY, dockZ),
                    connector.direction().getSerializedName(),
                    dataBox(connector.min(), connector.max()),
                    localDataBox(connector.min(), connector.max(), dockX, dockY, dockZ),
                    connector.width(),
                    connector.height(),
                    connector.acceptedSizes()
            ));
        }
        return passages;
    }

    private static StationMapPiece playerShipPiece(BlockPos dock, Direction stationDirection) {
        Direction shipDirection = horizontalOrNorth(stationDirection).getOpposite();
        BlockPos center = dock.relative(shipDirection, PLAYER_SHIP_MAP_HALF_SIZE);
        return new StationMapPiece(
                PLAYER_SHIP_MAP_ID,
                0,
                0,
                center.getX() - PLAYER_SHIP_MAP_HALF_SIZE,
                center.getZ() - PLAYER_SHIP_MAP_HALF_SIZE,
                center.getX() + PLAYER_SHIP_MAP_HALF_SIZE,
                center.getZ() + PLAYER_SHIP_MAP_HALF_SIZE,
                true,
                List.of(new StationMapConnection(0, dock.getX(), dock.getZ(), horizontalOrNorth(stationDirection))),
                ""
        );
    }

    private static Set<String> activeQuestTargetTriggers(ServerLevel level, StationInstance station) {
        return QuestSavedData.get(level)
                .stationIfPresent(station.id())
                .map(state -> {
                    Set<String> triggers = new HashSet<>();
                    for (QuestObjectiveState objective : state.objectives()) {
                        if (objective.completed()) {
                            continue;
                        }
                        Set<String> completedTargetTriggers = completedQuestTargetTriggers(objective);
                        if (!objective.targetTriggerId().isBlank() && !completedTargetTriggers.contains(objective.targetTriggerId())) {
                            triggers.add(objective.targetTriggerId());
                        }
                        if (objective.progress().contains(KEY_TARGET_TRIGGER_IDS, Tag.TAG_LIST)) {
                            ListTag targetTriggerIds = objective.progress().getList(KEY_TARGET_TRIGGER_IDS, Tag.TAG_STRING);
                            for (int i = 0; i < targetTriggerIds.size(); i++) {
                                String targetTriggerId = targetTriggerIds.getString(i);
                                if (!targetTriggerId.isBlank() && !completedTargetTriggers.contains(targetTriggerId)) {
                                    triggers.add(targetTriggerId);
                                }
                            }
                        }
                    }
                    return triggers;
                })
                .orElse(Set.of());
    }

    private static Set<String> completedQuestTargetTriggers(QuestObjectiveState objective) {
        if (!objective.progress().contains(KEY_COMPLETED_TARGET_TRIGGER_IDS, Tag.TAG_LIST)) {
            return Set.of();
        }
        Set<String> completed = new HashSet<>();
        ListTag completedTargetTriggerIds = objective.progress().getList(KEY_COMPLETED_TARGET_TRIGGER_IDS, Tag.TAG_STRING);
        for (int i = 0; i < completedTargetTriggerIds.size(); i++) {
            String targetTriggerId = completedTargetTriggerIds.getString(i);
            if (!targetTriggerId.isBlank()) {
                completed.add(targetTriggerId);
            }
        }
        return completed;
    }

    private static String questPieceMarker(PlacedStationPiece piece, Set<String> questTargetTriggers) {
        if (questTargetTriggers.isEmpty()) {
            return "";
        }
        for (PlacedTriggerZone triggerZone : piece.triggerZones()) {
            if (questTargetTriggers.contains(triggerZone.id())) {
                return "quest";
            }
        }
        return "";
    }

    private static StationMapData.Room playerShipRoom(BlockPos dock, Direction stationDirection) {
        Direction shipDirection = horizontalOrNorth(stationDirection).getOpposite();
        BlockPos center = dock.relative(shipDirection, PLAYER_SHIP_MAP_HALF_SIZE);
        BlockPos min = new BlockPos(center.getX() - PLAYER_SHIP_MAP_HALF_SIZE, dock.getY(), center.getZ() - PLAYER_SHIP_MAP_HALF_SIZE);
        BlockPos max = new BlockPos(center.getX() + PLAYER_SHIP_MAP_HALF_SIZE, dock.getY(), center.getZ() + PLAYER_SHIP_MAP_HALF_SIZE);
        return new StationMapData.Room(
                StationAreNear.MODID + ":player_ship",
                StationAreNear.MODID + ":player_ship",
                0,
                0,
                dataBox(min, max),
                dataBox(min, max),
                localDataBox(min, max, dock.getX(), dock.getY(), dock.getZ()),
                true,
                List.of(new StationMapData.Passage(
                        "ship_dock",
                        0,
                        dataPoint(dock),
                        StationMapData.Point3i.ZERO,
                        horizontalOrNorth(stationDirection).getSerializedName(),
                        dataBox(dock, dock),
                        StationMapData.Box3i.ZERO,
                        1,
                        1,
                        "1x1"
                ))
        );
    }

    private static Direction horizontalOrNorth(Direction direction) {
        return direction == null || direction.getAxis().isVertical() ? Direction.NORTH : direction;
    }

    private static StationMapData.Point3i dataPoint(BlockPos pos) {
        return new StationMapData.Point3i(pos.getX(), pos.getY(), pos.getZ());
    }

    private static StationMapData.Point3i localDataPoint(BlockPos pos, int dockX, int dockY, int dockZ) {
        return new StationMapData.Point3i(pos.getX() - dockX, pos.getY() - dockY, pos.getZ() - dockZ);
    }

    private static StationMapData.Box3i dataBox(BoundingBox bounds) {
        return new StationMapData.Box3i(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }

    private static StationMapData.Box3i dataBox(BlockPos min, BlockPos max) {
        return new StationMapData.Box3i(
                Math.min(min.getX(), max.getX()),
                Math.min(min.getY(), max.getY()),
                Math.min(min.getZ(), max.getZ()),
                Math.max(min.getX(), max.getX()),
                Math.max(min.getY(), max.getY()),
                Math.max(min.getZ(), max.getZ())
        );
    }

    private static StationMapData.Box3i localDataBox(BoundingBox bounds, int dockX, int dockY, int dockZ) {
        return new StationMapData.Box3i(
                bounds.minX() - dockX,
                bounds.minY() - dockY,
                bounds.minZ() - dockZ,
                bounds.maxX() - dockX,
                bounds.maxY() - dockY,
                bounds.maxZ() - dockZ
        );
    }

    private static StationMapData.Box3i localDataBox(BlockPos min, BlockPos max, int dockX, int dockY, int dockZ) {
        StationMapData.Box3i world = dataBox(min, max);
        return new StationMapData.Box3i(
                world.minX() - dockX,
                world.minY() - dockY,
                world.minZ() - dockZ,
                world.maxX() - dockX,
                world.maxY() - dockY,
                world.maxZ() - dockZ
        );
    }

    private static int floorIndex(int y, int dockY) {
        return Math.floorDiv(y - dockY + FLOOR_HEIGHT / 2, FLOOR_HEIGHT);
    }

    private static boolean contains(BoundingBox bounds, BlockPos pos) {
        return pos.getX() >= bounds.minX() && pos.getX() <= bounds.maxX()
                && pos.getY() >= bounds.minY() && pos.getY() <= bounds.maxY()
                && pos.getZ() >= bounds.minZ() && pos.getZ() <= bounds.maxZ();
    }
}
