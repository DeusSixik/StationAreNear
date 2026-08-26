package dev.sixik.stationarenear.structures.generation;

import dev.sixik.stationarenear.structures.data.*;
import dev.sixik.stationarenear.structures.trigger.StationStructureSpawnTriggerEvent;
import dev.sixik.stationarenear.structures.trigger.StationStructureTriggerType;
import dev.sixik.stationarenear.structures.world.StationSavedData;
import dev.sixik.stationarenear.structures.world.StationStructureLibraryData;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class StationGenerator {

    private static final int FLOOR_HEIGHT_BLOCKS = 16;
    private static final int START_BOUNDARY_DEAD_END_DISTANCE = 4;
    private static final int START_BOUNDARY_DEAD_END_SCORE_BONUS = 8_000;
    private static final int SECONDARY_CONNECTION_SCORE_BONUS = 650;
    private static final int EXTERIOR_CLEARANCE_BLOCKS = 10;

    public StationGenerationResult generateDockedStation(
            ServerLevel level,
            BlockPos shuttleDoorCenter,
            Direction stationDirection,
            StationGenerationSettings settings
    ) {
        if (stationDirection.getAxis().isVertical()) {
            return StationGenerationResult.failure("Station direction must be horizontal");
        }

        StationStructureLibraryData library = StationStructureLibraryData.get(level);
        Optional<StationPoolDefinition> poolOptional = library.pool(settings.pool());
        if (poolOptional.isEmpty()) {
            return StationGenerationResult.failure("Unknown station pool: " + settings.pool());
        }

        StationPoolDefinition pool = poolOptional.get();
        RandomSource random = RandomSource.create(settings.seed());
        float danger = settings.rollDanger(random);
        StationBoundary boundary = new StationBoundary(shuttleDoorCenter, stationDirection);
        StationLayout layout = planStationLayout(level, library, pool, shuttleDoorCenter, stationDirection, settings, danger, boundary);
        if (layout == null) {
            int requiredMinRooms = settings.minRooms() > 0 ? settings.minRooms() : pool.minRooms();
            return StationGenerationResult.failure("Could not plan station layout with at least " + requiredMinRooms + " pieces for pool: " + settings.pool());
        }

        List<PlacedStationPiece> placedPieces = new ObjectArrayList<>();
        for (PlacedStationPiece piece : layout.pieces()) {
            if (!placePiece(level, piece)) {
                clearPieces(level, placedPieces);
                return StationGenerationResult.failure("Failed to place station template: " + piece.template());
            }
            placedPieces.add(piece);
        }

        StationInstance station = new StationInstance(
                UUID.randomUUID(),
                settings.pool(),
                shuttleDoorCenter,
                stationDirection,
                danger,
                settings.seed(),
                placedPieces,
                new CompoundTag()
        );
        StationSavedData.get(level).addStation(station);
        postStructureSpawnTriggers(level, station);
        dev.sixik.stationarenear.structures.network.StationStructureNetwork.syncTemplateSelections(level);
        return StationGenerationResult.success(station);
    }

    private void postStructureSpawnTriggers(ServerLevel level, StationInstance station) {
        List<SpawnTriggerContext> objectPlacers = new ArrayList<>();
        for (PlacedStationPiece piece : station.pieces()) {
            for (PlacedTriggerZone zone : piece.triggerZones()) {
                StationStructureTriggerType triggerType = StationStructureTriggerType.from(zone.type());
                if (triggerType == StationStructureTriggerType.OTHER) {
                    continue;
                }
                SpawnTriggerContext trigger = new SpawnTriggerContext(piece, zone, triggerType);
                if (triggerType == StationStructureTriggerType.OBJECT_PLACER) {
                    objectPlacers.add(trigger);
                } else {
                    postStructureSpawnTrigger(level, station, trigger);
                }
            }
        }
        postObjectPlacerTriggers(level, station, objectPlacers);
    }

    private void postObjectPlacerTriggers(ServerLevel level, StationInstance station, List<SpawnTriggerContext> objectPlacers) {
        boolean[] grouped = new boolean[objectPlacers.size()];
        RandomSource random = RandomSource.create(station.seed() ^ 0x5DEECE66DL);
        for (int i = 0; i < objectPlacers.size(); i++) {
            if (grouped[i]) {
                continue;
            }

            List<SpawnTriggerContext> group = nestedObjectPlacerGroup(objectPlacers, grouped, i);
            SpawnTriggerContext selected = group.size() == 1 ? group.get(0) : selectObjectPlacerFromNestedGroup(group, random);
            postStructureSpawnTrigger(level, station, selected);
        }
    }

    private List<SpawnTriggerContext> nestedObjectPlacerGroup(List<SpawnTriggerContext> objectPlacers, boolean[] grouped, int rootIndex) {
        List<Integer> indexes = new ArrayList<>();
        indexes.add(rootIndex);
        grouped[rootIndex] = true;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < objectPlacers.size(); i++) {
                if (grouped[i]) {
                    continue;
                }
                for (int groupedIndex : indexes) {
                    if (isNestedObjectPlacer(objectPlacers.get(i).zone(), objectPlacers.get(groupedIndex).zone())) {
                        grouped[i] = true;
                        indexes.add(i);
                        changed = true;
                        break;
                    }
                }
            }
        }

        List<SpawnTriggerContext> group = new ObjectArrayList<>();
        for (int index : indexes) {
            group.add(objectPlacers.get(index));
        }
        return group;
    }

    private SpawnTriggerContext selectObjectPlacerFromNestedGroup(List<SpawnTriggerContext> group, RandomSource random) {
        List<SpawnTriggerContext> forced = new ObjectArrayList<>();
        for (SpawnTriggerContext trigger : group) {
            if (trigger.zone().data().getBoolean("ignoreChancePlace")) {
                forced.add(trigger);
            }
        }
        List<SpawnTriggerContext> candidates = forced.isEmpty() ? group : forced;
        int totalWeight = 0;
        for (SpawnTriggerContext trigger : candidates) {
            int chance = trigger.zone().data().contains("placeChance") ? trigger.zone().data().getInt("placeChance") : trigger.zone().data().getInt("chance");
            totalWeight += Math.max(1, chance <= 0 ? 50 : chance);
        }
        int roll = random.nextInt(Math.max(1, totalWeight));
        for (SpawnTriggerContext trigger : candidates) {
            int chance = trigger.zone().data().contains("placeChance") ? trigger.zone().data().getInt("placeChance") : trigger.zone().data().getInt("chance");
            roll -= Math.max(1, chance <= 0 ? 50 : chance);
            if (roll < 0) {
                return trigger;
            }
        }
        return candidates.get(0);
    }

    private boolean isNestedObjectPlacer(PlacedTriggerZone left, PlacedTriggerZone right) {
        return containsTriggerZone(left, right) || containsTriggerZone(right, left);
    }

    private boolean containsTriggerZone(PlacedTriggerZone outer, PlacedTriggerZone inner) {
        return outer.min().getX() <= inner.min().getX()
                && outer.min().getY() <= inner.min().getY()
                && outer.min().getZ() <= inner.min().getZ()
                && outer.max().getX() >= inner.max().getX()
                && outer.max().getY() >= inner.max().getY()
                && outer.max().getZ() >= inner.max().getZ();
    }

    private void postStructureSpawnTrigger(ServerLevel level, StationInstance station, SpawnTriggerContext trigger) {
        MinecraftForge.EVENT_BUS.post(new StationStructureSpawnTriggerEvent(level, station, trigger.piece(), trigger.zone(), trigger.triggerType()));
    }

    private StationLayout planStationLayout(
            ServerLevel level,
            StationStructureLibraryData library,
            StationPoolDefinition pool,
            BlockPos shuttleDoorCenter,
            Direction stationDirection,
            StationGenerationSettings settings,
            float danger,
            StationBoundary boundary
    ) {
        StationLayout bestLayout = null;
        int attempts = layoutAttemptCount(settings.maxRooms());
        for (int attempt = 0; attempt < attempts; attempt++) {
            long attemptSeed = settings.seed() ^ (0x9E3779B97F4A7C15L * (attempt + 1L));
            StationLayout layout = buildLayoutAttempt(
                    level,
                    library,
                    pool,
                    shuttleDoorCenter,
                    stationDirection,
                    settings,
                    danger,
                    boundary,
                    RandomSource.create(attemptSeed)
            );
            if (layout == null) {
                continue;
            }
            if (bestLayout == null || layout.score() > bestLayout.score()) {
                bestLayout = layout;
            }
            if (bestLayout.openConnectors().isEmpty() && bestLayout.pieces().size() >= settings.maxRooms()) {
                break;
            }
        }
        return bestLayout;
    }

    private int layoutAttemptCount(int maxRooms) {
        return Math.max(12, Math.min(48, maxRooms * 4));
    }

    private StationLayout buildLayoutAttempt(
            ServerLevel level,
            StationStructureLibraryData library,
            StationPoolDefinition pool,
            BlockPos shuttleDoorCenter,
            Direction stationDirection,
            StationGenerationSettings settings,
            float danger,
            StationBoundary boundary,
            RandomSource random
    ) {
        List<PlacedStationPiece> pieces = new ObjectArrayList<>();
        List<BoundingBox> occupied = new ObjectArrayList<>();
        List<BoundingBox> reservedClearances = new ObjectArrayList<>();
        List<StationConnector> openConnectors = new ObjectArrayList<>();
        IntArrayList parentIndexes = new IntArrayList();
        List<StationConnector> sourceConnectors = new ObjectArrayList<>();
        Object2IntMap<ResourceLocation> pieceUsage = new Object2IntOpenHashMap<>();
        boolean allowVerticalConnections = allowVerticalConnections(settings.maxFloors());

        PlacedStationPiece startPiece = chooseStartPiece(level, library, pool, shuttleDoorCenter, stationDirection, danger, random, boundary, settings.maxFloors());
        if (startPiece == null) {
            return null;
        }

        pieces.add(startPiece);
        parentIndexes.add(-1);
        sourceConnectors.add(null);
        occupied.add(startPiece.bounds());
        reserveExteriorClearance(library, startPiece, reservedClearances);
        incrementPieceUsage(pieceUsage, startPiece);
        openConnectors.addAll(usableOpenConnectors(startPiece.openConnectors(), collisionBounds(occupied, reservedClearances), boundary, allowVerticalConnections));

        int targetPieces = settings.maxRooms();
        int requiredMinRooms = settings.minRooms() > 0 ? settings.minRooms() : pool.minRooms();
        int minAllowedY = startPiece.bounds().minY() - (settings.maxFloors() - 1) * FLOOR_HEIGHT_BLOCKS;
        int maxAllowedY = startPiece.bounds().maxY() + (settings.maxFloors() - 1) * FLOOR_HEIGHT_BLOCKS;
        while (!openConnectors.isEmpty() && pieces.size() < targetPieces) {
            boolean needsExpandablePiece = pieces.size() + 1 < requiredMinRooms;
            PlacementCandidate candidate = chooseNextPiece(level, library, pool, openConnectors, danger, random, occupied, reservedClearances, boundary, minAllowedY, maxAllowedY, settings.maxFloors(), needsExpandablePiece, pieceUsage);
            if (candidate == null) {
                break;
            }

            int parentIndex = indexOfConnectorOwner(pieces, candidate.sourceConnector());
            openConnectors.remove(candidate.sourceConnector());
            markConnectorConsumed(pieces, candidate.sourceConnector());
            PlacedStationPiece placedPiece = consumeSecondaryConnectorClosures(pieces, openConnectors, candidate.piece(), candidate.sourceConnector());
            pieces.add(placedPiece);
            parentIndexes.add(parentIndex);
            sourceConnectors.add(candidate.sourceConnector());
            occupied.add(placedPiece.bounds());
            reserveExteriorClearance(library, placedPiece, reservedClearances);
            incrementPieceUsage(pieceUsage, placedPiece);
            openConnectors.addAll(usableOpenConnectors(placedPiece.openConnectors(), collisionBounds(occupied, reservedClearances), boundary, allowVerticalConnections));
        }

        if (pieces.size() < requiredMinRooms) {
            return null;
        }

        capRemainingOpenConnectors(level, library, pool, openConnectors, danger, random, occupied, reservedClearances, boundary, minAllowedY, maxAllowedY, settings.maxFloors(), pieces, parentIndexes, sourceConnectors, pieceUsage);
        openConnectors.removeIf(connector -> !isUsableConnector(connector, collisionBounds(occupied, reservedClearances), boundary, allowVerticalConnections));
        repairDanglingSections(level, library, pool, openConnectors, danger, random, occupied, reservedClearances, boundary, minAllowedY, maxAllowedY, settings.maxFloors(), pieces, parentIndexes, sourceConnectors);
        openConnectors.removeIf(connector -> !isUsableConnector(connector, collisionBounds(occupied, reservedClearances), boundary, allowVerticalConnections));
        syncPieceOpenConnectors(pieces, openConnectors);
        return new StationLayout(new ObjectArrayList<>(pieces), new ObjectArrayList<>(openConnectors), scoreLayout(pieces, openConnectors, boundary, requiredMinRooms, targetPieces));
    }

    private int scoreLayout(List<PlacedStationPiece> pieces, List<StationConnector> openConnectors, StationBoundary boundary, int requiredMinRooms, int targetPieces) {
        int score = pieces.size() * 250 - openConnectors.size() * 2500;
        if (pieces.size() >= requiredMinRooms) {
            score += 10_000;
        }
        if (openConnectors.isEmpty()) {
            score += 100_000;
        }
        if (pieces.size() >= targetPieces) {
            score += 5_000;
        }
        for (PlacedStationPiece piece : pieces) {
            score += Math.min(80, Math.max(0, boundary.progress(piece.bounds())));
        }
        return score;
    }

    private void syncPieceOpenConnectors(List<PlacedStationPiece> pieces, List<StationConnector> openConnectors) {
        for (int i = 0; i < pieces.size(); i++) {
            PlacedStationPiece piece = pieces.get(i);
            List<StationConnector> remainingConnectors = new ObjectArrayList<>();
            for (StationConnector connector : piece.openConnectors()) {
                if (openConnectors.contains(connector)) {
                    remainingConnectors.add(connector);
                }
            }
            if (remainingConnectors.size() == piece.openConnectors().size()) {
                continue;
            }
            pieces.set(i, new PlacedStationPiece(
                    piece.definitionId(),
                    piece.template(),
                    piece.origin(),
                    piece.rotation(),
                    piece.bounds(),
                    piece.selectionBounds(),
                    remainingConnectors,
                    piece.triggerZones()
            ));
        }
    }

    private boolean insideFloorLimit(BoundingBox bounds, int minAllowedY, int maxAllowedY) {
        return bounds.minY() >= minAllowedY && bounds.maxY() <= maxAllowedY;
    }

    private void clearPieces(ServerLevel level, List<PlacedStationPiece> pieces) {
        for (PlacedStationPiece piece : pieces) {
            clearBounds(level, piece.bounds());
        }
    }

    private void clearBounds(ServerLevel level, BoundingBox bounds) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    level.setBlock(mutable.set(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private PlacedStationPiece chooseStartPiece(
            ServerLevel level,
            StationStructureLibraryData library,
            StationPoolDefinition pool,
            BlockPos shuttleDoorCenter,
            Direction stationDirection,
            float danger,
            RandomSource random,
            StationBoundary boundary,
            int maxFloors
    ) {
        List<StationPieceDefinition> candidates = definitions(library, pool.startPieces(), danger);
        shuffleWeighted(candidates, random);

        Direction requiredConnectorDirection = stationDirection.getOpposite();
        BlockPos connectorTarget = shuttleDoorCenter.relative(stationDirection);
        PlacementCandidate best = null;
        for (StationPieceDefinition definition : candidates) {
            if (!pieceAllowedForFloors(definition, maxFloors)) {
                continue;
            }
            Optional<StructureTemplate> template = level.getStructureManager().get(definition.template());
            if (template.isEmpty()) {
                continue;
            }

            for (StationConnector connector : definition.connectors()) {
                Rotation rotation = StationPlacementUtil.rotationBetween(connector.direction(), requiredConnectorDirection);
                if (rotation == null) {
                    continue;
                }

                BlockPos origin = connectorTarget.subtract(StationPlacementUtil.transform(connector.position(), rotation));
                PlacedStationPiece piece = buildPlacedPiece(definition, template.get(), origin, rotation, danger, connector);
                if (!boundary.allowsBounds(piece.bounds()) || !exteriorSideAllowed(definition, piece, rotation, List.of(), List.of())) {
                    continue;
                }
                int score = scorePiece(piece, definition, boundary, random, usableOpenConnectors(piece.openConnectors(), List.of(piece.bounds()), boundary, allowVerticalConnections(maxFloors)).size(), 0, stationDirection)
                        + exteriorSideScore(definition, piece, rotation, List.of());
                if (best == null || score > best.score()) {
                    best = new PlacementCandidate(connector, piece, score);
                }
            }
        }
        return best == null ? null : best.piece();
    }

    private PlacementCandidate chooseNextPiece(
            ServerLevel level,
            StationStructureLibraryData library,
            StationPoolDefinition pool,
            List<StationConnector> openConnectors,
            float danger,
            RandomSource random,
            List<BoundingBox> occupied,
            List<BoundingBox> reservedClearances,
            StationBoundary boundary,
            int minAllowedY,
            int maxAllowedY,
            int maxFloors,
            boolean needsExpandablePiece,
            Map<ResourceLocation, Integer> pieceUsage
    ) {
        List<StationPieceDefinition> candidates = definitions(library, pool.roomPieces(), danger);
        List<StationPieceDefinition> capCandidates = singleConnectorDefinitions(candidates, maxFloors);
        shuffleWeighted(candidates, random);

        List<PlacementCandidate> validPlacements = new ObjectArrayList<>();
        for (StationConnector openConnector : new ObjectArrayList<>(openConnectors)) {
            if (!isUsableConnector(openConnector, collisionBounds(occupied, reservedClearances), boundary, allowVerticalConnections(maxFloors))) {
                continue;
            }
            boolean shouldUseDeadEndNearBoundary = false;
            BlockPos target = openConnector.position().relative(openConnector.direction());
            Direction requiredDirection = openConnector.direction().getOpposite();
            for (StationPieceDefinition definition : candidates) {
                if (!pieceAllowedForFloors(definition, maxFloors)) {
                    continue;
                }
                Optional<StructureTemplate> template = level.getStructureManager().get(definition.template());
                if (template.isEmpty()) {
                    continue;
                }

                for (StationConnector candidateConnector : definition.connectors()) {
                    if (!openConnector.isCompatibleWith(candidateConnector)) {
                        continue;
                    }

                    Rotation rotation = StationPlacementUtil.rotationBetween(candidateConnector.direction(), requiredDirection);
                    if (rotation == null) {
                        continue;
                    }

                    BlockPos origin = target.subtract(StationPlacementUtil.transform(candidateConnector.position(), rotation));
                    PlacedStationPiece piece = buildPlacedPiece(definition, template.get(), origin, rotation, danger, candidateConnector);
                    if (!insideFloorLimit(piece.bounds(), minAllowedY, maxAllowedY)
                            || StationPlacementUtil.intersectsAny(piece.bounds(), collisionBounds(occupied, reservedClearances))
                            || !exteriorSideAllowed(definition, piece, rotation, occupied, reservedClearances)) {
                        continue;
                    }
                    if (!boundary.allowsBounds(piece.bounds())) {
                        shouldUseDeadEndNearBoundary |= shouldReplaceWithDeadEndNearBoundary(openConnector, piece, boundary);
                        continue;
                    }

                    List<BoundingBox> occupiedWithCandidate = new ObjectArrayList<>(occupied);
                    occupiedWithCandidate.add(piece.bounds());
                    List<BoundingBox> reservedWithCandidate = new ObjectArrayList<>(reservedClearances);
                    reserveExteriorClearance(definition, piece, reservedWithCandidate);
                    List<StationConnector> usable = usableOpenConnectors(piece.openConnectors(), collisionBounds(occupiedWithCandidate, reservedWithCandidate), boundary, allowVerticalConnections(maxFloors));
                    int usableConnectors = usable.size();
                    if (shouldReplaceWithDeadEndNearBoundary(openConnector, piece, usable, boundary)) {
                        shouldUseDeadEndNearBoundary = true;
                        continue;
                    }
                    if (needsExpandablePiece && usableConnectors == 0) {
                        continue;
                    }

                    int usageCount = pieceUsage.getOrDefault(definition.id(), 0);
                    int secondaryConnections = secondaryConnectorClosureCount(piece, openConnectors, openConnector);
                    int score = scorePiece(piece, definition, boundary, random, usableConnectors, usageCount, openConnector.direction())
                            + connectorDirectionScore(openConnector, boundary.direction())
                            + secondaryConnections * SECONDARY_CONNECTION_SCORE_BONUS
                            + exteriorSideScore(definition, piece, rotation, occupied);
                    validPlacements.add(new PlacementCandidate(openConnector, piece, score));
                }
            }

            if (shouldUseDeadEndNearBoundary && !needsExpandablePiece) {
                PlacementCandidate cap = chooseCapPiece(level, capCandidates, openConnector, danger, random, occupied, reservedClearances, boundary, minAllowedY, maxAllowedY);
                if (cap != null) {
                    validPlacements.add(new PlacementCandidate(cap.sourceConnector(), cap.piece(), cap.score() + START_BOUNDARY_DEAD_END_SCORE_BONUS));
                }
            }
        }
        return selectWeightedCandidate(validPlacements, random);
    }

    private List<StationConnector> usableOpenConnectors(List<StationConnector> connectors, List<BoundingBox> occupied, StationBoundary boundary, boolean allowVerticalConnections) {
        List<StationConnector> usable = new ObjectArrayList<>();
        for (StationConnector connector : connectors) {
            if (isUsableConnector(connector, occupied, boundary, allowVerticalConnections)) {
                usable.add(connector);
            }
        }
        return usable;
    }

    private boolean isUsableConnector(StationConnector connector, List<BoundingBox> occupied, StationBoundary boundary, boolean allowVerticalConnections) {
        if (!isRoomLinkConnector(connector) || (!allowVerticalConnections && connector.direction().getAxis().isVertical())) {
            return false;
        }
        return boundary.allowsConnector(connector) && !connectorTargetIntersectsOccupied(connector, occupied);
    }

    private PlacedStationPiece consumeSecondaryConnectorClosures(
            List<PlacedStationPiece> pieces,
            List<StationConnector> openConnectors,
            PlacedStationPiece piece,
            StationConnector sourceConnector
    ) {
        List<StationConnector> remainingConnectors = new ObjectArrayList<>();
        boolean consumedAny = false;
        for (StationConnector connector : piece.openConnectors()) {
            StationConnector matchingConnector = findMatchingOpenConnector(openConnectors, connector, sourceConnector);
            if (matchingConnector == null) {
                remainingConnectors.add(connector);
                continue;
            }

            openConnectors.remove(matchingConnector);
            markConnectorConsumed(pieces, matchingConnector);
            consumedAny = true;
        }

        if (!consumedAny) {
            return piece;
        }

        return new PlacedStationPiece(
                piece.definitionId(),
                piece.template(),
                piece.origin(),
                piece.rotation(),
                piece.bounds(),
                piece.selectionBounds(),
                remainingConnectors,
                piece.triggerZones()
        );
    }

    private int secondaryConnectorClosureCount(PlacedStationPiece piece, List<StationConnector> openConnectors, StationConnector sourceConnector) {
        int count = 0;
        for (StationConnector connector : piece.openConnectors()) {
            if (findMatchingOpenConnector(openConnectors, connector, sourceConnector) != null) {
                count++;
            }
        }
        return count;
    }

    private StationConnector findMatchingOpenConnector(List<StationConnector> openConnectors, StationConnector connector, StationConnector sourceConnector) {
        for (StationConnector openConnector : openConnectors) {
            if (openConnector.equals(sourceConnector)) {
                continue;
            }
            if (connectorsCanCloseLoop(openConnector, connector)) {
                return openConnector;
            }
        }
        return null;
    }

    private boolean connectorsCanCloseLoop(StationConnector left, StationConnector right) {
        return isRoomLinkConnector(left)
                && isRoomLinkConnector(right)
                && left.direction() == right.direction().getOpposite()
                && left.position().relative(left.direction()).equals(right.position())
                && right.position().relative(right.direction()).equals(left.position())
                && left.isCompatibleWith(right);
    }

    private boolean shouldReplaceWithDeadEndNearBoundary(StationConnector sourceConnector, PlacedStationPiece piece, StationBoundary boundary) {
        return shouldReplaceWithDeadEndNearBoundary(sourceConnector, piece, List.of(), boundary);
    }

    private boolean shouldReplaceWithDeadEndNearBoundary(StationConnector sourceConnector, PlacedStationPiece piece, List<StationConnector> usableConnectors, StationBoundary boundary) {
        List<StationConnector> roomLinkConnectors = roomLinkConnectors(piece.openConnectors());
        if (!boundary.isNearStartLine(sourceConnector, START_BOUNDARY_DEAD_END_DISTANCE) || roomLinkConnectors.size() <= 1) {
            return false;
        }
        if (!boundary.allowsBounds(piece.bounds())) {
            return true;
        }
        for (StationConnector connector : roomLinkConnectors) {
            if (!boundary.allowsConnector(connector)) {
                return true;
            }
        }
        return !usableConnectors.isEmpty() && usableConnectors.size() < roomLinkConnectors.size()
                && roomLinkConnectors.stream().anyMatch(connector -> !usableConnectors.contains(connector) && !boundary.allowsConnector(connector));
    }

    private List<StationPieceDefinition> singleConnectorDefinitions(List<StationPieceDefinition> definitions, int maxFloors) {
        List<StationPieceDefinition> caps = new ObjectArrayList<>();
        for (StationPieceDefinition definition : definitions) {
            if (roomLinkConnectorCount(definition.connectors()) == 1 && pieceAllowedForFloors(definition, maxFloors)) {
                caps.add(definition);
            }
        }
        return caps;
    }

    private boolean allowVerticalConnections(int maxFloors) {
        return false;
    }

    private boolean pieceAllowedForFloors(StationPieceDefinition definition, int maxFloors) {
        if (definition.floorSpan() > maxFloors) {
            return false;
        }
        if (maxFloors <= 1) {
            for (StationConnector connector : definition.connectors()) {
                if (connector.direction().getAxis().isVertical()) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isRoomLinkConnector(StationConnector connector) {
        return connector.direction().getAxis().isHorizontal();
    }

    private int roomLinkConnectorCount(List<StationConnector> connectors) {
        int count = 0;
        for (StationConnector connector : connectors) {
            if (isRoomLinkConnector(connector)) {
                count++;
            }
        }
        return count;
    }

    private List<StationConnector> roomLinkConnectors(List<StationConnector> connectors) {
        List<StationConnector> roomLinks = new ObjectArrayList<>();
        for (StationConnector connector : connectors) {
            if (isRoomLinkConnector(connector)) {
                roomLinks.add(connector);
            }
        }
        return roomLinks;
    }

    private void capRemainingOpenConnectors(
            ServerLevel level,
            StationStructureLibraryData library,
            StationPoolDefinition pool,
            List<StationConnector> openConnectors,
            float danger,
            RandomSource random,
            List<BoundingBox> occupied,
            List<BoundingBox> reservedClearances,
            StationBoundary boundary,
            int minAllowedY,
            int maxAllowedY,
            int maxFloors,
            List<PlacedStationPiece> pieces,
            List<Integer> parentIndexes,
            List<StationConnector> sourceConnectors,
            Object2IntMap<ResourceLocation> pieceUsage
    ) {
        if (openConnectors.isEmpty()) {
            return;
        }

        List<StationPieceDefinition> capDefinitions = singleConnectorDefinitions(definitions(library, pool.roomPieces(), danger), maxFloors);
        if (capDefinitions.isEmpty()) {
            return;
        }

        boolean allowVerticalConnections = allowVerticalConnections(maxFloors);
        for (StationConnector openConnector : new ObjectArrayList<>(openConnectors)) {
            if (!isUsableConnector(openConnector, collisionBounds(occupied, reservedClearances), boundary, allowVerticalConnections)) {
                continue;
            }

            PlacementCandidate cap = chooseCapPiece(level, capDefinitions, openConnector, danger, random, occupied, reservedClearances, boundary, minAllowedY, maxAllowedY);
            if (cap == null) {
                continue;
            }

            int parentIndex = indexOfConnectorOwner(pieces, openConnector);
            openConnectors.remove(openConnector);
            markConnectorConsumed(pieces, openConnector);
            pieces.add(cap.piece());
            parentIndexes.add(parentIndex);
            sourceConnectors.add(openConnector);
            occupied.add(cap.piece().bounds());
            reserveExteriorClearance(library, cap.piece(), reservedClearances);
            incrementPieceUsage(pieceUsage, cap.piece());
        }
    }

    private void repairDanglingSections(
            ServerLevel level,
            StationStructureLibraryData library,
            StationPoolDefinition pool,
            List<StationConnector> openConnectors,
            float danger,
            RandomSource random,
            List<BoundingBox> occupied,
            List<BoundingBox> reservedClearances,
            StationBoundary boundary,
            int minAllowedY,
            int maxAllowedY,
            int maxFloors,
            List<PlacedStationPiece> pieces,
            IntArrayList parentIndexes,
            List<StationConnector> sourceConnectors
    ) {
        List<StationPieceDefinition> capDefinitions = singleConnectorDefinitions(definitions(library, pool.roomPieces(), danger), maxFloors);
        if (capDefinitions.isEmpty()) {
            return;
        }

        boolean allowVerticalConnections = allowVerticalConnections(maxFloors);
        int maxRepairPasses = Math.max(4, pieces.size());
        for (int pass = 0; pass < maxRepairPasses && !openConnectors.isEmpty(); pass++) {
            boolean repaired = false;
            for (StationConnector openConnector : new ObjectArrayList<>(openConnectors)) {
                int ownerIndex = indexOfConnectorOwner(pieces, openConnector);
                if (ownerIndex <= 0 || ownerIndex >= sourceConnectors.size()) {
                    continue;
                }

                StationConnector sourceConnector = sourceConnectors.get(ownerIndex);
                int parentIndex = parentIndexes.get(ownerIndex);
                if (sourceConnector == null || parentIndex < 0) {
                    continue;
                }

                Optional<StationPieceDefinition> ownerDefinition = library.piece(pieces.get(ownerIndex).definitionId());
                if (ownerDefinition.isEmpty() || ownerDefinition.get().connectors().size() <= 1) {
                    continue;
                }

                IntArrayList removedIndexes = subtreeIndexes(parentIndexes, ownerIndex);
                List<BoundingBox> occupiedWithoutSection = occupiedWithout(occupied, removedIndexes);
                List<BoundingBox> reservedWithoutSection = reservedClearancesWithout(library, pieces, removedIndexes);
                PlacementCandidate cap = chooseCapPiece(level, capDefinitions, sourceConnector, danger, random, occupiedWithoutSection, reservedWithoutSection, boundary, minAllowedY, maxAllowedY);
                if (cap == null) {
                    continue;
                }

                replaceSubtreeWithPiece(library, pieces, occupied, reservedClearances, parentIndexes, sourceConnectors, openConnectors, removedIndexes, ownerIndex, cap.piece(), sourceConnector, boundary, allowVerticalConnections);
                repaired = true;
                break;
            }

            if (!repaired) {
                break;
            }
        }
    }

    private void replaceSubtreeWithPiece(
            StationStructureLibraryData library,
            List<PlacedStationPiece> pieces,
            List<BoundingBox> occupied,
            List<BoundingBox> reservedClearances,
            List<Integer> parentIndexes,
            List<StationConnector> sourceConnectors,
            List<StationConnector> openConnectors,
            List<Integer> removedIndexes,
            int rootIndex,
            PlacedStationPiece replacement,
            StationConnector replacementSourceConnector,
            StationBoundary boundary,
            boolean allowVerticalConnections
    ) {
        boolean[] removed = new boolean[pieces.size()];
        for (int index : removedIndexes) {
            if (index >= 0 && index < removed.length) {
                removed[index] = true;
            }
        }

        int[] oldToNew = new int[pieces.size()];
        java.util.Arrays.fill(oldToNew, -1);
        List<PlacedStationPiece> newPieces = new ObjectArrayList<>();
        List<Integer> newParentIndexes = new ObjectArrayList<>();
        List<StationConnector> newSourceConnectors = new ObjectArrayList<>();
        for (int i = 0; i < pieces.size(); i++) {
            if (removed[i]) {
                continue;
            }
            oldToNew[i] = newPieces.size();
            newPieces.add(pieces.get(i));
            newSourceConnectors.add(sourceConnectors.get(i));
        }

        for (int i = 0; i < pieces.size(); i++) {
            if (removed[i]) {
                continue;
            }
            int oldParent = parentIndexes.get(i);
            newParentIndexes.add(oldParent < 0 ? -1 : oldToNew[oldParent]);
        }

        int oldParent = parentIndexes.get(rootIndex);
        newPieces.add(replacement);
        newParentIndexes.add(oldParent < 0 ? -1 : oldToNew[oldParent]);
        newSourceConnectors.add(replacementSourceConnector);

        pieces.clear();
        pieces.addAll(newPieces);
        parentIndexes.clear();
        parentIndexes.addAll(newParentIndexes);
        sourceConnectors.clear();
        sourceConnectors.addAll(newSourceConnectors);
        rebuildOccupied(occupied, pieces);
        rebuildReservedClearances(library, pieces, reservedClearances);
        refreshOpenConnectors(openConnectors, pieces, collisionBounds(occupied, reservedClearances), boundary, allowVerticalConnections);
    }

    private IntArrayList subtreeIndexes(IntArrayList parentIndexes, int rootIndex) {
        boolean[] removed = new boolean[parentIndexes.size()];
        removed[rootIndex] = true;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < parentIndexes.size(); i++) {
                int parentIndex = parentIndexes.get(i);
                if (!removed[i] && parentIndex >= 0 && removed[parentIndex]) {
                    removed[i] = true;
                    changed = true;
                }
            }
        }

        IntArrayList indexes = new IntArrayList();
        for (int i = 0; i < removed.length; i++) {
            if (removed[i]) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    private List<BoundingBox> occupiedWithout(List<BoundingBox> occupied, List<Integer> removedIndexes) {
        boolean[] removed = new boolean[occupied.size()];
        for (int index : removedIndexes) {
            if (index >= 0 && index < removed.length) {
                removed[index] = true;
            }
        }

        List<BoundingBox> remaining = new ObjectArrayList<>();
        for (int i = 0; i < occupied.size(); i++) {
            if (!removed[i]) {
                remaining.add(occupied.get(i));
            }
        }
        return remaining;
    }

    private void rebuildOccupied(List<BoundingBox> occupied, List<PlacedStationPiece> pieces) {
        occupied.clear();
        for (PlacedStationPiece piece : pieces) {
            occupied.add(piece.bounds());
        }
    }

    private void refreshOpenConnectors(List<StationConnector> openConnectors, List<PlacedStationPiece> pieces, List<BoundingBox> occupied, StationBoundary boundary, boolean allowVerticalConnections) {
        openConnectors.clear();
        for (PlacedStationPiece piece : pieces) {
            openConnectors.addAll(usableOpenConnectors(piece.openConnectors(), occupied, boundary, allowVerticalConnections));
        }
    }

    private PlacementCandidate chooseCapPiece(
            ServerLevel level,
            List<StationPieceDefinition> capDefinitions,
            StationConnector openConnector,
            float danger,
            RandomSource random,
            List<BoundingBox> occupied,
            List<BoundingBox> reservedClearances,
            StationBoundary boundary,
            int minAllowedY,
            int maxAllowedY
    ) {
        BlockPos target = openConnector.position().relative(openConnector.direction());
        Direction requiredDirection = openConnector.direction().getOpposite();
        List<PlacementCandidate> validPlacements = new ObjectArrayList<>();
        List<StationPieceDefinition> candidates = new ObjectArrayList<>(capDefinitions);
        shuffleWeighted(candidates, random);

        for (StationPieceDefinition definition : candidates) {
            Optional<StructureTemplate> template = level.getStructureManager().get(definition.template());
            if (template.isEmpty()) {
                continue;
            }

            for (StationConnector candidateConnector : definition.connectors()) {
                if (!isRoomLinkConnector(candidateConnector) || !openConnector.isCompatibleWith(candidateConnector)) {
                    continue;
                }

                Rotation rotation = StationPlacementUtil.rotationBetween(candidateConnector.direction(), requiredDirection);
                if (rotation == null) {
                    continue;
                }

                BlockPos origin = target.subtract(StationPlacementUtil.transform(candidateConnector.position(), rotation));
                PlacedStationPiece piece = buildPlacedPiece(definition, template.get(), origin, rotation, danger, candidateConnector);
                if (!insideFloorLimit(piece.bounds(), minAllowedY, maxAllowedY)
                        || !boundary.allowsBounds(piece.bounds())
                        || StationPlacementUtil.intersectsAny(piece.bounds(), collisionBounds(occupied, reservedClearances))
                        || !exteriorSideAllowed(definition, piece, rotation, occupied, reservedClearances)) {
                    continue;
                }

                int score = definition.weight() * 12 + exteriorSideScore(definition, piece, rotation, occupied) + random.nextInt(16);
                validPlacements.add(new PlacementCandidate(openConnector, piece, score));
            }
        }

        return selectWeightedCandidate(validPlacements, random);
    }

    private void markConnectorConsumed(List<PlacedStationPiece> pieces, StationConnector consumedConnector) {
        for (int i = 0; i < pieces.size(); i++) {
            PlacedStationPiece piece = pieces.get(i);
            if (!piece.openConnectors().contains(consumedConnector)) {
                continue;
            }

            List<StationConnector> remainingConnectors = new ObjectArrayList<>(piece.openConnectors());
            remainingConnectors.remove(consumedConnector);
            pieces.set(i, new PlacedStationPiece(
                    piece.definitionId(),
                    piece.template(),
                    piece.origin(),
                    piece.rotation(),
                    piece.bounds(),
                    piece.selectionBounds(),
                    remainingConnectors,
                    piece.triggerZones()
            ));
            return;
        }
    }

    private int indexOfConnectorOwner(List<PlacedStationPiece> pieces, StationConnector connector) {
        for (int i = 0; i < pieces.size(); i++) {
            if (pieces.get(i).openConnectors().contains(connector)) {
                return i;
            }
        }
        return -1;
    }

    private List<BoundingBox> collisionBounds(List<BoundingBox> occupied, List<BoundingBox> reservedClearances) {
        if (reservedClearances.isEmpty()) {
            return occupied;
        }
        List<BoundingBox> collisionBounds = new ObjectArrayList<>(occupied);
        collisionBounds.addAll(reservedClearances);
        return collisionBounds;
    }

    private void reserveExteriorClearance(StationStructureLibraryData library, PlacedStationPiece piece, List<BoundingBox> reservedClearances) {
        library.piece(piece.definitionId()).ifPresent(definition -> reserveExteriorClearance(definition, piece, reservedClearances));
    }

    private void reserveExteriorClearance(StationPieceDefinition definition, PlacedStationPiece piece, List<BoundingBox> reservedClearances) {
        if (definition.exteriorSide() == null) {
            return;
        }
        reservedClearances.add(exteriorClearanceBounds(piece.bounds(), piece.rotation().rotate(definition.exteriorSide())));
    }

    private void rebuildReservedClearances(StationStructureLibraryData library, List<PlacedStationPiece> pieces, List<BoundingBox> reservedClearances) {
        reservedClearances.clear();
        for (PlacedStationPiece piece : pieces) {
            reserveExteriorClearance(library, piece, reservedClearances);
        }
    }

    private List<BoundingBox> reservedClearancesWithout(StationStructureLibraryData library, List<PlacedStationPiece> pieces, List<Integer> removedIndexes) {
        boolean[] removed = new boolean[pieces.size()];
        for (int index : removedIndexes) {
            if (index >= 0 && index < removed.length) {
                removed[index] = true;
            }
        }

        List<BoundingBox> remaining = new ObjectArrayList<>();
        for (int i = 0; i < pieces.size(); i++) {
            if (!removed[i]) {
                reserveExteriorClearance(library, pieces.get(i), remaining);
            }
        }
        return remaining;
    }

    private boolean exteriorSideAllowed(StationPieceDefinition definition, PlacedStationPiece piece, Rotation rotation, List<BoundingBox> occupied, List<BoundingBox> reservedClearances) {
        if (definition.exteriorSide() == null) {
            return true;
        }
        Direction exteriorDirection = rotation.rotate(definition.exteriorSide());
        BoundingBox clearance = exteriorClearanceBounds(piece.bounds(), exteriorDirection);
        return exteriorSideAtLayoutBoundary(piece.bounds(), occupied, exteriorDirection)
                && !StationPlacementUtil.intersectsAny(clearance, occupied)
                && !StationPlacementUtil.intersectsAny(clearance, reservedClearances);
    }

    private int exteriorSideScore(StationPieceDefinition definition, PlacedStationPiece piece, Rotation rotation, List<BoundingBox> occupied) {
        if (definition.exteriorSide() == null) {
            return 0;
        }
        Direction exteriorDirection = rotation.rotate(definition.exteriorSide());
        return exteriorSideAtLayoutBoundary(piece.bounds(), occupied, exteriorDirection) ? 180 : -600;
    }

    private boolean exteriorSideAtLayoutBoundary(BoundingBox bounds, List<BoundingBox> occupied, Direction direction) {
        if (occupied.isEmpty()) {
            return true;
        }
        return switch (direction) {
            case EAST -> bounds.maxX() >= occupied.stream().mapToInt(BoundingBox::maxX).max().orElse(bounds.maxX());
            case WEST -> bounds.minX() <= occupied.stream().mapToInt(BoundingBox::minX).min().orElse(bounds.minX());
            case SOUTH -> bounds.maxZ() >= occupied.stream().mapToInt(BoundingBox::maxZ).max().orElse(bounds.maxZ());
            case NORTH -> bounds.minZ() <= occupied.stream().mapToInt(BoundingBox::minZ).min().orElse(bounds.minZ());
            case UP -> bounds.maxY() >= occupied.stream().mapToInt(BoundingBox::maxY).max().orElse(bounds.maxY());
            case DOWN -> bounds.minY() <= occupied.stream().mapToInt(BoundingBox::minY).min().orElse(bounds.minY());
        };
    }

    private BoundingBox exteriorClearanceBounds(BoundingBox bounds, Direction direction) {
        return switch (direction) {
            case EAST -> new BoundingBox(bounds.maxX() + 1, bounds.minY(), bounds.minZ(), bounds.maxX() + EXTERIOR_CLEARANCE_BLOCKS, bounds.maxY(), bounds.maxZ());
            case WEST -> new BoundingBox(bounds.minX() - EXTERIOR_CLEARANCE_BLOCKS, bounds.minY(), bounds.minZ(), bounds.minX() - 1, bounds.maxY(), bounds.maxZ());
            case SOUTH -> new BoundingBox(bounds.minX(), bounds.minY(), bounds.maxZ() + 1, bounds.maxX(), bounds.maxY(), bounds.maxZ() + EXTERIOR_CLEARANCE_BLOCKS);
            case NORTH -> new BoundingBox(bounds.minX(), bounds.minY(), bounds.minZ() - EXTERIOR_CLEARANCE_BLOCKS, bounds.maxX(), bounds.maxY(), bounds.minZ() - 1);
            case UP -> new BoundingBox(bounds.minX(), bounds.maxY() + 1, bounds.minZ(), bounds.maxX(), bounds.maxY() + EXTERIOR_CLEARANCE_BLOCKS, bounds.maxZ());
            case DOWN -> new BoundingBox(bounds.minX(), bounds.minY() - EXTERIOR_CLEARANCE_BLOCKS, bounds.minZ(), bounds.maxX(), bounds.minY() - 1, bounds.maxZ());
        };
    }

    private boolean connectorTargetIntersectsOccupied(StationConnector connector, List<BoundingBox> occupied) {
        BlockPos target = connector.position().relative(connector.direction());
        for (BoundingBox bounds : occupied) {
            if (target.getX() >= bounds.minX() && target.getX() <= bounds.maxX()
                    && target.getY() >= bounds.minY() && target.getY() <= bounds.maxY()
                    && target.getZ() >= bounds.minZ() && target.getZ() <= bounds.maxZ()) {
                return true;
            }
        }
        return false;
    }

    private int scorePiece(PlacedStationPiece piece, StationPieceDefinition definition, StationBoundary boundary, RandomSource random, int usableConnectors, int usageCount, Direction sourceDirection) {
        return Math.min(30, Math.max(0, boundary.progress(piece.bounds())))
                + usableConnectors * 35
                + turnPotentialScore(piece.openConnectors(), sourceDirection, boundary.direction())
                + Math.max(0, definition.floorSpan() - 1) * 45
                + definition.weight() * 8
                - usageCount * 55
                + random.nextInt(45);
    }

    private int turnPotentialScore(List<StationConnector> connectors, Direction sourceDirection, Direction stationDirection) {
        int score = 0;
        for (StationConnector connector : connectors) {
            if (connector.direction() == sourceDirection) {
                score += 4;
            } else if (connector.direction() == sourceDirection.getOpposite()) {
                score -= 18;
            } else if (connector.direction().getAxis().isHorizontal()) {
                score += connector.direction() == stationDirection ? 10 : 32;
            } else {
                score += 0;
            }
        }
        return score;
    }

    private int connectorDirectionScore(StationConnector connector, Direction stationDirection) {
        if (connector.direction() == stationDirection) {
            return -18;
        }
        if (connector.direction() == stationDirection.getOpposite()) {
            return -80;
        }
        return 22;
    }

    private PlacementCandidate selectWeightedCandidate(List<PlacementCandidate> candidates, RandomSource random) {
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort((left, right) -> Integer.compare(right.score(), left.score()));
        int topLimit = Math.min(6, candidates.size());
        int minScore = candidates.get(topLimit - 1).score();
        int totalWeight = 0;
        for (int i = 0; i < topLimit; i++) {
            totalWeight += Math.max(1, candidates.get(i).score() - minScore + 1);
        }
        int roll = random.nextInt(Math.max(1, totalWeight));
        for (int i = 0; i < topLimit; i++) {
            roll -= Math.max(1, candidates.get(i).score() - minScore + 1);
            if (roll < 0) {
                return candidates.get(i);
            }
        }
        return candidates.get(0);
    }

    private void incrementPieceUsage(Object2IntMap<ResourceLocation> pieceUsage, PlacedStationPiece piece) {
        pieceUsage.merge(piece.definitionId(), 1, Integer::sum);
    }

    private List<StationPieceDefinition> definitions(StationStructureLibraryData library, List<ResourceLocation> ids, float danger) {
        List<StationPieceDefinition> definitions = new ObjectArrayList<>();
        for (ResourceLocation id : ids) {
            library.piece(id).filter(piece -> piece.canSpawnAtDanger(danger)).ifPresent(definitions::add);
        }
        return definitions;
    }

    private void shuffleWeighted(List<StationPieceDefinition> definitions, RandomSource random) {
        definitions.sort((left, right) -> Integer.compare(
                random.nextInt(Math.max(1, right.weight())),
                random.nextInt(Math.max(1, left.weight()))
        ));
    }

    private PlacedStationPiece buildPlacedPiece(
            StationPieceDefinition definition,
            StructureTemplate template,
            BlockPos origin,
            Rotation rotation,
            float danger,
            StationConnector consumedConnector
    ) {
        List<StationConnector> openConnectors = new ObjectArrayList<>();
        for (StationConnector connector : definition.connectors()) {
            if (connector == consumedConnector) {
                continue;
            }
            openConnectors.add(StationPlacementUtil.transformConnector(connector, origin, rotation));
        }

        List<PlacedTriggerZone> triggerZones = new ObjectArrayList<>();
        for (StationTriggerZone triggerZone : definition.triggerZones()) {
            triggerZones.add(StationPlacementUtil.transformTrigger(triggerZone, origin, rotation, danger));
        }

        return new PlacedStationPiece(
                definition.id(),
                definition.template(),
                origin,
                rotation,
                StationPlacementUtil.transformBounds(origin, template.getSize(), rotation),
                StationPlacementUtil.transformBox(origin, definition.selectionMin(), definition.selectionMax(), rotation),
                openConnectors,
                triggerZones
        );
    }

    private boolean placePiece(ServerLevel level, PlacedStationPiece piece) {
        StructureTemplate template = level.getStructureManager().getOrCreate(piece.template());
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(piece.rotation());
        return template.placeInWorld(level, piece.origin(), piece.origin(), settings, level.getRandom(), 2)
                && hasPlacedBlocks(level, piece.bounds());
    }

    private boolean hasPlacedBlocks(ServerLevel level, BoundingBox bounds) {
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


    private record PlacementCandidate(StationConnector sourceConnector, PlacedStationPiece piece, int score) {
    }

    private record StationLayout(List<PlacedStationPiece> pieces, List<StationConnector> openConnectors, int score) {
    }

    private record SpawnTriggerContext(PlacedStationPiece piece, PlacedTriggerZone zone, StationStructureTriggerType triggerType) {
    }

    private record StationBoundary(BlockPos origin, Direction direction) {
        boolean allowsBounds(BoundingBox bounds) {
            return switch (direction) {
                case EAST -> bounds.minX() > origin.getX();
                case WEST -> bounds.maxX() < origin.getX();
                case SOUTH -> bounds.minZ() > origin.getZ();
                case NORTH -> bounds.maxZ() < origin.getZ();
                default -> true;
            };
        }

        boolean allowsConnector(StationConnector connector) {
            return allowsPos(connector.position().relative(connector.direction()));
        }

        boolean allowsPos(BlockPos pos) {
            return switch (direction) {
                case EAST -> pos.getX() > origin.getX();
                case WEST -> pos.getX() < origin.getX();
                case SOUTH -> pos.getZ() > origin.getZ();
                case NORTH -> pos.getZ() < origin.getZ();
                default -> true;
            };
        }

        boolean isNearStartLine(StationConnector connector, int distance) {
            return distanceFromStart(connector.position()) <= distance
                    || distanceFromStart(connector.position().relative(connector.direction())) <= distance;
        }

        int distanceFromStart(BlockPos pos) {
            return switch (direction) {
                case EAST -> pos.getX() - origin.getX();
                case WEST -> origin.getX() - pos.getX();
                case SOUTH -> pos.getZ() - origin.getZ();
                case NORTH -> origin.getZ() - pos.getZ();
                default -> Integer.MAX_VALUE;
            };
        }

        int progress(BoundingBox bounds) {
            return switch (direction) {
                case EAST -> bounds.maxX() - origin.getX();
                case WEST -> origin.getX() - bounds.minX();
                case SOUTH -> bounds.maxZ() - origin.getZ();
                case NORTH -> origin.getZ() - bounds.minZ();
                default -> 0;
            };
        }
    }
}
