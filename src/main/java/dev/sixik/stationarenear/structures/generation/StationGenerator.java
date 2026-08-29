package dev.sixik.stationarenear.structures.generation;

import dev.sixik.stationarenear.structures.config.StationStructureFileStorage;
import dev.sixik.stationarenear.structures.data.*;
import dev.sixik.stationarenear.structures.trigger.StationStructureSpawnTriggerEvent;
import dev.sixik.stationarenear.structures.trigger.StationStructureTriggerType;
import dev.sixik.stationarenear.structures.util.TagsConstants;
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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;

public class StationGenerator {

    private final Map<ResourceLocation, Optional<StructureTemplate>> templateCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static final int FLOOR_HEIGHT_BLOCKS = 16;
    private static final int START_BOUNDARY_DEAD_END_DISTANCE = 4;
    private static final int START_BOUNDARY_DEAD_END_SCORE_BONUS = 8_000;
    private static final int SECONDARY_CONNECTION_SCORE_BONUS = 650;
    private static final int REQUIRED_PIECE_SCORE_BONUS = 12_000;
    private static final int REQUIRED_GROUP_CLUSTER_SCORE_BONUS = 24_000;
    private static final int REQUIRED_GROUP_NEARBY_SCORE_BONUS = 6_000;
    private static final int REQUIRED_GROUP_FOREIGN_OWNER_PENALTY = 4_000;
    private static final int SIDE_PASSAGE_SCORE_BONUS = 10_000;
    private static final int SIDE_PASSAGE_LOOP_SCORE_BONUS = 4_000;
    private static final int SIDE_PASSAGE_DISTANCE_SCORE = 8_000;
    private static final int SIDE_PASSAGE_ROUTE_ATTEMPTS = 32;
    private static final int SIDE_PASSAGE_MIN_PIECE_LIMIT = 48;
    private static final int SIDE_PASSAGE_MAX_PIECE_LIMIT = 256;
    private static final int EXTERIOR_CLEARANCE_BLOCKS = 10;
    private static final int CANDIDATE_SCORE_WINDOW = 260;
    private static final int CANDIDATE_SCORE_WEIGHT_CAP = 96;
    private static final int FLOOR_TRANSITION_NEEDED_SCORE_BONUS = 9_000;
    private static final int FLOOR_TRANSITION_SCORE_BONUS = 140;
    private static final int LAYOUT_THREAD_COUNT = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
    private static final java.util.concurrent.ExecutorService LAYOUT_EXECUTOR = java.util.concurrent.Executors.newFixedThreadPool(
            LAYOUT_THREAD_COUNT,
            task -> {
                Thread thread = new Thread(task, "StationAreNear layout planner");
                thread.setDaemon(true);
                return thread;
            }
    );
    private static final Set<String> FLOOR_TRANSITION_TAGS = Set.of(
            "stair",
            "stairs",
            "staircase",
            "ladder",
            "floor_link",
            "vertical_link"
    );

    public StationGenerationResult generateDockedStation(
            ServerLevel level,
            BlockPos shuttleDoorCenter,
            Direction stationDirection,
            StationGenerationSettings settings
    ) {
        StationStructureFileStorage.loadExternalStructures(level);
        if (stationDirection.getAxis().isVertical()) {
            return StationGenerationResult.failure("Station direction must be horizontal");
        }

        StationStructureLibraryData library = StationStructureLibraryData.get(level);
        Optional<StationPoolDefinition> poolOptional = library.pool(settings.pool());
        if (poolOptional.isEmpty()) {
            return StationGenerationResult.failure("Unknown station pool: " + settings.pool()
                    + ". Available pools=" + library.pools().stream().map(pool -> pool.id().toString()).sorted().limit(12).toList());
        }

        StationPoolDefinition pool = poolOptional.get();
        RandomSource random = RandomSource.create(settings.seed());
        float danger = settings.rollDanger(random);
        preloadTemplates(level, library, danger);
        StationBoundary boundary = new StationBoundary(shuttleDoorCenter, stationDirection);
        LayoutDiagnostics diagnostics = LayoutDiagnostics.create(library, pool, settings, danger, boundary, this);
        StationLayout layout = planStationLayout(level, library, pool, shuttleDoorCenter, stationDirection, settings, danger, boundary, diagnostics);
        if (layout == null) {
            int requiredMinRooms = settings.minRooms() > 0 ? settings.minRooms() : pool.minRooms();
            return StationGenerationResult.failure("Could not plan station layout with at least " + requiredMinRooms + " pieces for pool: " + settings.pool() + ". " + diagnostics.summary());
        }

        List<PlacedStationPiece> placedPieces = new ObjectArrayList<>();
        for (PlacedStationPiece piece : layout.pieces()) {
            if (!placePiece(level, piece)) {
                clearPieces(level, placedPieces);
                return StationGenerationResult.failure("Failed to place station template: " + piece.template()
                        + " piece=" + piece.definitionId()
                        + " origin=" + piece.origin()
                        + " rotation=" + piece.rotation()
                        + " bounds=" + piece.bounds());
            }
            placedPieces.add(piece);
        }

        CompoundTag customData = settings.customData();
        if (!settings.questElementSpawnSkips().isEmpty()) {
            CompoundTag questSpawnSkips = new CompoundTag();
            for (Map.Entry<String, Integer> entry : settings.questElementSpawnSkips().entrySet()) {
                questSpawnSkips.putInt(entry.getKey(), entry.getValue());
            }
            customData.put("questElementSpawnSkips", questSpawnSkips);
        }

        StationInstance station = new StationInstance(
                UUID.randomUUID(),
                settings.pool(),
                shuttleDoorCenter,
                stationDirection,
                danger,
                settings.seed(),
                placedPieces,
                customData
        );
        StationSavedData.get(level).addStation(station);
        postStructureSpawnTriggers(level, station);
        dev.sixik.stationarenear.structures.lamps.StationLampManager.onStationGenerated(level, station);
        dev.sixik.stationarenear.structures.network.StationStructureNetwork.syncTemplateSelections(level);
        return StationGenerationResult.success(station);
    }

    private void postStructureSpawnTriggers(ServerLevel level, StationInstance station) {
        List<SpawnTriggerContext> objectPlacers = new ArrayList<>();
        List<SpawnTriggerContext> objectZonePlacers = new ArrayList<>();
        for (PlacedStationPiece piece : station.pieces()) {
            for (PlacedTriggerZone zone : piece.triggerZones()) {
                StationStructureTriggerType triggerType = StationStructureTriggerType.from(zone.type());
                if (triggerType == StationStructureTriggerType.OTHER) {
                    continue;
                }
                SpawnTriggerContext trigger = new SpawnTriggerContext(piece, zone, triggerType);
                if (triggerType == StationStructureTriggerType.OBJECT_PLACER) {
                    objectPlacers.add(trigger);
                } else if (triggerType == StationStructureTriggerType.OBJECT_ZONE_PLACER) {
                    objectZonePlacers.add(trigger);
                } else {
                    postStructureSpawnTrigger(level, station, trigger);
                }
            }
        }
        postObjectPlacerTriggers(level, station, objectPlacers);
        postObjectZonePlacerTriggers(level, station, objectZonePlacers);
    }

    private void postObjectZonePlacerTriggers(ServerLevel level, StationInstance station, List<SpawnTriggerContext> objectZonePlacers) {
        for (SpawnTriggerContext trigger : objectZonePlacers) {
            postStructureSpawnTrigger(level, station, trigger);
        }
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
            StationBoundary boundary,
            LayoutDiagnostics diagnostics
    ) {
        int attempts = layoutAttemptCount(settings.maxRooms());
        if (LAYOUT_THREAD_COUNT <= 1 || attempts <= 1) {
            return planStationLayoutSync(level, library, pool, shuttleDoorCenter, stationDirection, settings, danger, boundary, attempts, diagnostics);
        }

        List<java.util.concurrent.CompletableFuture<StationLayout>> futures = new ArrayList<>(attempts);
        for (int attempt = 0; attempt < attempts; attempt++) {
            final long attemptSeed = settings.seed() ^ (0x9E3779B97F4A7C15L * (attempt + 1L));
            futures.add(java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> buildLayoutAttempt(
                            level,
                            library,
                            pool,
                            shuttleDoorCenter,
                            stationDirection,
                            settings,
                            danger,
                            boundary,
                            RandomSource.create(attemptSeed),
                            diagnostics
                    ),
                    LAYOUT_EXECUTOR
            ).exceptionally(exception -> {
                diagnostics.recordFailure("layout attempt crashed: " + rootMessage(exception));
                return null;
            }));
        }

        StationLayout bestLayout = null;
        for (java.util.concurrent.CompletableFuture<StationLayout> future : futures) {
            StationLayout layout = future.join();
            if (layout != null && (bestLayout == null || layout.score() > bestLayout.score())) {
                bestLayout = layout;
            }
        }
        return bestLayout;
    }

    private StationLayout planStationLayoutSync(
            ServerLevel level,
            StationStructureLibraryData library,
            StationPoolDefinition pool,
            BlockPos shuttleDoorCenter,
            Direction stationDirection,
            StationGenerationSettings settings,
            float danger,
            StationBoundary boundary,
            int attempts,
            LayoutDiagnostics diagnostics
    ) {
        StationLayout bestLayout = null;
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
                    RandomSource.create(attemptSeed),
                    diagnostics
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

    private void preloadTemplates(ServerLevel level, StationStructureLibraryData library, float danger) {
        for (StationPieceDefinition definition : library.pieces()) {
            if (definition.canSpawnAtDanger(danger)) {
                templateCache.computeIfAbsent(definition.template(), id -> level.getStructureManager().get(id));
            }
        }
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
            RandomSource random,
            LayoutDiagnostics diagnostics
    ) {
        List<PlacedStationPiece> pieces = new ObjectArrayList<>();
        List<BoundingBox> occupied = new ObjectArrayList<>();
        List<BoundingBox> reservedClearances = new ObjectArrayList<>();
        List<StationConnector> openConnectors = new ObjectArrayList<>();
        IntArrayList parentIndexes = new IntArrayList();
        List<StationConnector> sourceConnectors = new ObjectArrayList<>();
        Object2IntMap<ResourceLocation> pieceUsage = new Object2IntOpenHashMap<>();
        Object2IntMap<String> tagUsage = new Object2IntOpenHashMap<>();
        boolean allowVerticalConnections = allowVerticalConnections(settings.maxFloors());
        diagnostics.recordAttemptStarted();

        PlacedStationPiece startPiece = chooseStartPiece(level, library, pool, shuttleDoorCenter, stationDirection, danger, random, boundary, settings.maxFloors());
        if (startPiece == null) {
            diagnostics.recordFailure("no start piece can dock to ship: need connector facing " + stationDirection.getOpposite() + ", startPieces=" + pool.startPieces().size());
            return null;
        }

        pieces.add(startPiece);
        parentIndexes.add(-1);
        sourceConnectors.add(null);
        occupied.add(startPiece.bounds());
        reserveExteriorClearance(library, startPiece, reservedClearances);
        incrementPieceUsage(pieceUsage, startPiece);
        incrementTagUsage(library, tagUsage, startPiece);
        openConnectors.addAll(usableOpenConnectors(startPiece.openConnectors(), collisionBounds(occupied, reservedClearances), boundary, allowVerticalConnections));

        int targetPieces = settings.maxRooms();
        int requiredMinRooms = settings.minRooms() > 0 ? settings.minRooms() : pool.minRooms();
        int minAllowedY = startPiece.bounds().minY() - (settings.maxFloors() - 1) * FLOOR_HEIGHT_BLOCKS;
        int maxAllowedY = startPiece.bounds().maxY() + (settings.maxFloors() - 1) * FLOOR_HEIGHT_BLOCKS;
        while (!openConnectors.isEmpty() && (pieces.size() < targetPieces || !requiredPiecesSatisfied(settings, pieceUsage, tagUsage))) {
            boolean needsExpandablePiece = pieces.size() + 1 < requiredMinRooms;
            boolean forceRequiredPiece = pieces.size() >= targetPieces && !requiredPiecesSatisfied(settings, pieceUsage, tagUsage);
            PlacementCandidate candidate = chooseNextPiece(level, library, pool, openConnectors, danger, random, pieces, occupied, reservedClearances, boundary, minAllowedY, maxAllowedY, settings.maxFloors(), needsExpandablePiece, pieceUsage, tagUsage, settings, forceRequiredPiece);
            if (candidate == null) {
                diagnostics.recordFailure("main branch stalled: pieces=" + pieces.size()
                        + "/" + targetPieces
                        + ", openConnectors=" + openConnectors.size()
                        + ", missing=" + missingRequirements(settings, pieceUsage, tagUsage));
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
            incrementTagUsage(library, tagUsage, placedPiece);
            openConnectors.addAll(usableOpenConnectors(placedPiece.openConnectors(), collisionBounds(occupied, reservedClearances), boundary, allowVerticalConnections));
        }

        if (pieces.size() < requiredMinRooms || !requiredPiecesSatisfied(settings, pieceUsage, tagUsage)) {
            diagnostics.recordFailure("required layout not satisfied: pieces=" + pieces.size()
                    + "/min" + requiredMinRooms
                    + ", missing=" + missingRequirements(settings, pieceUsage, tagUsage)
                    + ", openConnectors=" + openConnectors.size());
            return null;
        }

        if (!extendSidePassages(level, library, pool, openConnectors, danger, random, occupied, reservedClearances, boundary, minAllowedY, maxAllowedY, settings.maxFloors(), settings, pieces, parentIndexes, sourceConnectors, pieceUsage, tagUsage)) {
            diagnostics.recordFailure("could not route required passage connections: pieces=" + pieces.size()
                    + ", openConnectors=" + openConnectors.size()
                    + ", requiredPassageOpen=" + requiredPassageConnectorCount(openConnectors));
            return null;
        }

        capRemainingOpenConnectors(level, library, pool, openConnectors, danger, random, occupied, reservedClearances, boundary, minAllowedY, maxAllowedY, settings.maxFloors(), pieces, parentIndexes, sourceConnectors, pieceUsage);
        repairDanglingSections(level, library, pool, openConnectors, danger, random, occupied, reservedClearances, boundary, minAllowedY, maxAllowedY, settings.maxFloors(), pieces, parentIndexes, sourceConnectors);
        if (!openConnectors.isEmpty()) {
            diagnostics.recordFailure("unclosed connectors left after caps/repair: openConnectors=" + openConnectors.size()
                    + ", examples=" + connectorExamples(openConnectors, 4));
            return null;
        }
        syncPieceOpenConnectors(pieces, openConnectors);
        diagnostics.recordSuccess(pieces.size());
        return new StationLayout(new ObjectArrayList<>(pieces), new ObjectArrayList<>(openConnectors), scoreLayout(pieces, openConnectors, boundary, requiredMinRooms, targetPieces));
    }

    private void pruneUnusableOpenConnectors(List<StationConnector> openConnectors, List<BoundingBox> occupied, StationBoundary boundary, boolean allowVerticalConnections) {
        openConnectors.removeIf(connector -> !connector.requiresPassage() && !isUsableConnector(connector, occupied, boundary, allowVerticalConnections));
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
        boolean hasFloorTransitionPiece = false;
        for (PlacedStationPiece piece : pieces) {
            score += Math.min(80, Math.max(0, boundary.progress(piece.bounds())));
            hasFloorTransitionPiece |= piece.bounds().maxY() - piece.bounds().minY() + 1 > FLOOR_HEIGHT_BLOCKS;
        }
        if (hasFloorTransitionPiece) {
            score += 30_000;
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
            Optional<StructureTemplate> template = template(definition);
            if (template.isEmpty()) {
                continue;
            }

            for (StationConnector connector : definition.connectors()) {
                if (connector.requiresPassage()) {
                    continue;
                }
                Rotation rotation = StationPlacementUtil.rotationBetween(connector.direction(), requiredConnectorDirection);
                if (rotation == null) {
                    continue;
                }

                BlockPos origin = connectorTarget.subtract(StationPlacementUtil.transform(connector.position(), rotation));
                PlacedStationPiece piece = buildPlacedPiece(definition, template.get(), origin, rotation, danger, connector);
                if (!boundary.allowsBounds(piece.bounds()) || !exteriorSideAllowed(definition, piece, rotation, List.of(), List.of())) {
                    continue;
                }
                List<BoundingBox> startCollisionBounds = List.of(piece.bounds());
                if (!requiredPassageConnectorsUsable(piece.openConnectors(), startCollisionBounds, boundary, allowVerticalConnections(maxFloors))
                        || hasUnfillableOpenConnector(piece.openConnectors(), List.of(), null, List.of(), boundary, allowVerticalConnections(maxFloors))) {
                    continue;
                }
                int normalContinuations = normalUsableOpenConnectors(piece.openConnectors(), startCollisionBounds, boundary, allowVerticalConnections(maxFloors)).size();
                if (normalContinuations == 0) {
                    continue;
                }
                int score = scorePiece(piece, definition, boundary, random, normalContinuations, 0, stationDirection)
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
            List<PlacedStationPiece> pieces,
            List<BoundingBox> occupied,
            List<BoundingBox> reservedClearances,
            StationBoundary boundary,
            int minAllowedY,
            int maxAllowedY,
            int maxFloors,
            boolean needsExpandablePiece,
            Map<ResourceLocation, Integer> pieceUsage,
            Map<String, Integer> tagUsage,
            StationGenerationSettings settings,
            boolean forceRequiredPiece
    ) {
        List<StationPieceDefinition> candidates = candidateRoomDefinitions(library, pool, settings, danger);
        List<StationPieceDefinition> capCandidates = singleConnectorDefinitions(candidates, maxFloors);
        shuffleWeighted(candidates, random);
        boolean needsFloorTransitionPiece = maxFloors > 1 && !hasFloorTransitionPiece(library, pieces);

        List<BoundingBox> currentCollisionBounds = collisionBounds(occupied, reservedClearances);
        List<PlacementCandidate> validPlacements = new ObjectArrayList<>();
        for (StationConnector openConnector : new ObjectArrayList<>(openConnectors)) {
            if (openConnector.requiresPassage() || !isUsableConnector(openConnector, currentCollisionBounds, boundary, allowVerticalConnections(maxFloors))) {
                continue;
            }
            boolean shouldUseDeadEndNearBoundary = false;
            BlockPos target = openConnector.position().relative(openConnector.direction());
            Direction requiredDirection = openConnector.direction().getOpposite();
            for (StationPieceDefinition definition : candidates) {
                if (!pieceAllowedForFloors(definition, maxFloors) || (forceRequiredPiece && requiredPieceRemaining(settings, pieceUsage, tagUsage, definition) <= 0)) {
                    continue;
                }
                Optional<StructureTemplate> template = template(definition);
                if (template.isEmpty()) {
                    continue;
                }

                for (StationConnector candidateConnector : definition.connectors()) {
                    if (candidateConnector.requiresPassage() || !openConnector.isCompatibleWith(candidateConnector)) {
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
                    List<BoundingBox> candidateCollisionBounds = collisionBounds(occupiedWithCandidate, reservedWithCandidate);
                    if (!requiredPassageConnectorsUsable(piece.openConnectors(), candidateCollisionBounds, boundary, allowVerticalConnections(maxFloors))
                            || hasUnfillableOpenConnector(piece.openConnectors(), openConnectors, openConnector, currentCollisionBounds, boundary, allowVerticalConnections(maxFloors))) {
                        continue;
                    }
                    List<StationConnector> usable = normalUsableOpenConnectors(piece.openConnectors(), candidateCollisionBounds, boundary, allowVerticalConnections(maxFloors));
                    int usableConnectors = usable.size();
                    int secondaryConnections = secondaryConnectorClosureCount(piece, openConnectors, openConnector);
                    if (shouldReplaceWithDeadEndNearBoundary(openConnector, piece, usable, boundary)) {
                        shouldUseDeadEndNearBoundary = true;
                        continue;
                    }
                    if (needsExpandablePiece && usableConnectors == 0 && secondaryConnections == 0) {
                        continue;
                    }

                    int usageCount = pieceUsage.getOrDefault(definition.id(), 0);
                    int requiredRemaining = requiredPieceRemaining(settings, pieceUsage, tagUsage, definition);
                    int score = scorePiece(piece, definition, boundary, random, usableConnectors, usageCount, openConnector.direction())
                            + connectorDirectionScore(openConnector, boundary.direction())
                            + secondaryConnections * SECONDARY_CONNECTION_SCORE_BONUS
                            + floorTransitionScore(definition, needsFloorTransitionPiece)
                            + requiredPieceScore(requiredRemaining)
                            + requiredGroupClusterScore(library, settings, pieceUsage, tagUsage, pieces, openConnector, definition, piece)
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

    private List<StationConnector> normalUsableOpenConnectors(List<StationConnector> connectors, List<BoundingBox> occupied, StationBoundary boundary, boolean allowVerticalConnections) {
        List<StationConnector> usable = new ObjectArrayList<>();
        for (StationConnector connector : connectors) {
            if (!connector.requiresPassage() && isUsableConnector(connector, occupied, boundary, allowVerticalConnections)) {
                usable.add(connector);
            }
        }
        return usable;
    }

    private boolean requiredPassageConnectorsUsable(List<StationConnector> connectors, List<BoundingBox> occupied, StationBoundary boundary, boolean allowVerticalConnections) {
        for (StationConnector connector : connectors) {
            if (connector.requiresPassage() && !isUsableConnector(connector, occupied, boundary, allowVerticalConnections)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasUnfillableOpenConnector(
            List<StationConnector> candidateConnectors,
            List<StationConnector> openConnectors,
            StationConnector sourceConnector,
            List<BoundingBox> currentCollisionBounds,
            StationBoundary boundary,
            boolean allowVerticalConnections
    ) {
        for (StationConnector connector : candidateConnectors) {
            if (connector.requiresPassage() || !isRoomLinkConnector(connector, allowVerticalConnections)) {
                continue;
            }
            if (isUsableConnector(connector, currentCollisionBounds, boundary, allowVerticalConnections)) {
                continue;
            }
            if (findMatchingOpenConnector(openConnectors, connector, sourceConnector) != null) {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean isUsableConnector(StationConnector connector, List<BoundingBox> occupied, StationBoundary boundary, boolean allowVerticalConnections) {
        if (!isRoomLinkConnector(connector, allowVerticalConnections)) {
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
            if (openConnector.equals(sourceConnector) || openConnector.requiresPassage() || connector.requiresPassage()) {
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
        return maxFloors > 1;
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
        return isRoomLinkConnector(connector, true);
    }

    private boolean isRoomLinkConnector(StationConnector connector, boolean allowVerticalConnections) {
        return connector.direction().getAxis().isHorizontal()
                || (allowVerticalConnections && connector.direction().getAxis().isVertical());
    }

    private boolean hasRequiredPassageConnector(List<StationConnector> connectors) {
        for (StationConnector connector : connectors) {
            if (connector.requiresPassage()) {
                return true;
            }
        }
        return false;
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

    private boolean extendSidePassages(
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
            StationGenerationSettings settings,
            List<PlacedStationPiece> pieces,
            IntArrayList parentIndexes,
            List<StationConnector> sourceConnectors,
            Object2IntMap<ResourceLocation> pieceUsage,
            Object2IntMap<String> tagUsage
    ) {
        boolean allowVerticalConnections = allowVerticalConnections(maxFloors);
        int routePieceLimit = sidePassagePieceLimit(settings, pool, pieces);
        while (true) {
            pruneUnusableOpenConnectors(openConnectors, collisionBounds(occupied, reservedClearances), boundary, allowVerticalConnections);
            StationConnector sideConnector = firstSideConnector(openConnectors);
            if (sideConnector == null) {
                return true;
            }

            List<StationConnector> mainTargets = mainRouteTargets(openConnectors, sideConnector);
            if (mainTargets.isEmpty() || !tryConnectSidePassage(level, library, pool, sideConnector, mainTargets, danger, random, openConnectors, occupied, reservedClearances, boundary, minAllowedY, maxAllowedY, maxFloors, routePieceLimit, pieces, parentIndexes, sourceConnectors, pieceUsage, tagUsage)) {
                return false;
            }
        }
    }

    private int sidePassagePieceLimit(StationGenerationSettings settings, StationPoolDefinition pool, List<PlacedStationPiece> pieces) {
        int requestedSize = Math.max(settings.maxRooms(), Math.max(settings.minRooms(), pool.minRooms()));
        int dynamicLimit = Math.max(SIDE_PASSAGE_MIN_PIECE_LIMIT, Math.max(requestedSize * 8, pieces.size() * 4));
        return Math.min(SIDE_PASSAGE_MAX_PIECE_LIMIT, dynamicLimit);
    }

    private boolean tryConnectSidePassage(
            ServerLevel level,
            StationStructureLibraryData library,
            StationPoolDefinition pool,
            StationConnector sideConnector,
            List<StationConnector> mainTargets,
            float danger,
            RandomSource random,
            List<StationConnector> openConnectors,
            List<BoundingBox> occupied,
            List<BoundingBox> reservedClearances,
            StationBoundary boundary,
            int minAllowedY,
            int maxAllowedY,
            int maxFloors,
            int routePieceLimit,
            List<PlacedStationPiece> pieces,
            IntArrayList parentIndexes,
            List<StationConnector> sourceConnectors,
            Object2IntMap<ResourceLocation> pieceUsage,
            Object2IntMap<String> tagUsage
    ) {
        List<PlacedStationPiece> piecesSnapshot = new ObjectArrayList<>(pieces);
        List<StationConnector> openSnapshot = new ObjectArrayList<>(openConnectors);
        List<BoundingBox> occupiedSnapshot = new ObjectArrayList<>(occupied);
        List<BoundingBox> reservedSnapshot = new ObjectArrayList<>(reservedClearances);
        IntArrayList parentSnapshot = new IntArrayList(parentIndexes);
        List<StationConnector> sourceSnapshot = new ObjectArrayList<>(sourceConnectors);
        Object2IntOpenHashMap<ResourceLocation> pieceUsageSnapshot = new Object2IntOpenHashMap<>(pieceUsage);
        Object2IntOpenHashMap<String> tagUsageSnapshot = new Object2IntOpenHashMap<>(tagUsage);

        for (int attempt = 0; attempt < SIDE_PASSAGE_ROUTE_ATTEMPTS; attempt++) {
            restoreLayoutState(pieces, openConnectors, occupied, reservedClearances, parentIndexes, sourceConnectors, pieceUsage, tagUsage, piecesSnapshot, openSnapshot, occupiedSnapshot, reservedSnapshot, parentSnapshot, sourceSnapshot, pieceUsageSnapshot, tagUsageSnapshot);
            StationConnector currentConnector = sideConnector;
            boolean routeFailed = false;

            for (int depth = 0; depth < routePieceLimit; depth++) {
                PlacementCandidate candidate = chooseSidePassagePiece(level, library, pool, currentConnector, mainTargets, danger, random, openConnectors, occupied, reservedClearances, boundary, minAllowedY, maxAllowedY, maxFloors);
                if (candidate == null) {
                    routeFailed = true;
                    break;
                }

                int parentIndex = indexOfConnectorOwner(pieces, candidate.sourceConnector());
                int mainClosures = secondaryConnectorClosureCountToTargets(candidate.piece(), mainTargets, candidate.sourceConnector());
                openConnectors.remove(candidate.sourceConnector());
                markConnectorConsumed(pieces, candidate.sourceConnector());
                PlacedStationPiece placedPiece = consumeSecondaryConnectorClosures(pieces, openConnectors, candidate.piece(), candidate.sourceConnector());
                pieces.add(placedPiece);
                parentIndexes.add(parentIndex);
                sourceConnectors.add(candidate.sourceConnector());
                occupied.add(placedPiece.bounds());
                reserveExteriorClearance(library, placedPiece, reservedClearances);
                incrementPieceUsage(pieceUsage, placedPiece);
                incrementTagUsage(library, tagUsage, placedPiece);

                List<StationConnector> usableNewConnectors = normalUsableOpenConnectors(placedPiece.openConnectors(), collisionBounds(occupied, reservedClearances), boundary, allowVerticalConnections(maxFloors));
                openConnectors.addAll(usableNewConnectors);
                if (mainClosures > 0) {
                    return true;
                }

                currentConnector = closestConnectorToTargets(usableNewConnectors, mainTargets);
                if (currentConnector == null) {
                    routeFailed = true;
                    break;
                }
            }

            if (!routeFailed) {
                break;
            }
        }

        restoreLayoutState(pieces, openConnectors, occupied, reservedClearances, parentIndexes, sourceConnectors, pieceUsage, tagUsage, piecesSnapshot, openSnapshot, occupiedSnapshot, reservedSnapshot, parentSnapshot, sourceSnapshot, pieceUsageSnapshot, tagUsageSnapshot);
        return false;
    }

    private void restoreLayoutState(
            List<PlacedStationPiece> pieces,
            List<StationConnector> openConnectors,
            List<BoundingBox> occupied,
            List<BoundingBox> reservedClearances,
            IntArrayList parentIndexes,
            List<StationConnector> sourceConnectors,
            Object2IntMap<ResourceLocation> pieceUsage,
            Object2IntMap<String> tagUsage,
            List<PlacedStationPiece> piecesSnapshot,
            List<StationConnector> openSnapshot,
            List<BoundingBox> occupiedSnapshot,
            List<BoundingBox> reservedSnapshot,
            IntArrayList parentSnapshot,
            List<StationConnector> sourceSnapshot,
            Object2IntMap<ResourceLocation> pieceUsageSnapshot,
            Object2IntMap<String> tagUsageSnapshot
    ) {
        pieces.clear();
        pieces.addAll(piecesSnapshot);
        openConnectors.clear();
        openConnectors.addAll(openSnapshot);
        occupied.clear();
        occupied.addAll(occupiedSnapshot);
        reservedClearances.clear();
        reservedClearances.addAll(reservedSnapshot);
        parentIndexes.clear();
        parentIndexes.addAll(parentSnapshot);
        sourceConnectors.clear();
        sourceConnectors.addAll(sourceSnapshot);
        pieceUsage.clear();
        pieceUsage.putAll(pieceUsageSnapshot);
        tagUsage.clear();
        tagUsage.putAll(tagUsageSnapshot);
    }

    private StationConnector firstSideConnector(List<StationConnector> openConnectors) {
        for (StationConnector connector : openConnectors) {
            if (connector.requiresPassage()) {
                return connector;
            }
        }
        return null;
    }

    private List<StationConnector> mainRouteTargets(List<StationConnector> openConnectors, StationConnector sideConnector) {
        List<StationConnector> targets = new ObjectArrayList<>();
        for (StationConnector connector : openConnectors) {
            if (!connector.equals(sideConnector) && !connector.requiresPassage()) {
                targets.add(connector);
            }
        }
        return targets;
    }

    private PlacementCandidate chooseSidePassagePiece(
            ServerLevel level,
            StationStructureLibraryData library,
            StationPoolDefinition pool,
            StationConnector openConnector,
            List<StationConnector> mainTargets,
            float danger,
            RandomSource random,
            List<StationConnector> openConnectors,
            List<BoundingBox> occupied,
            List<BoundingBox> reservedClearances,
            StationBoundary boundary,
            int minAllowedY,
            int maxAllowedY,
            int maxFloors
    ) {
        BlockPos target = openConnector.position().relative(openConnector.direction());
        Direction requiredDirection = openConnector.direction().getOpposite();
        int currentDistance = nearestConnectorDistance(openConnector, mainTargets);
        List<PlacementCandidate> validPlacements = new ObjectArrayList<>();
        List<StationPieceDefinition> candidates = definitions(library, pool.roomPieces(), danger);
        shuffleWeighted(candidates, random);

        for (StationPieceDefinition definition : candidates) {
            if (!pieceAllowedForFloors(definition, maxFloors) || roomLinkConnectorCount(definition.connectors()) <= 1 || hasRequiredPassageConnector(definition.connectors())) {
                continue;
            }
            Optional<StructureTemplate> template = template(definition);
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

                List<BoundingBox> occupiedWithCandidate = new ObjectArrayList<>(occupied);
                occupiedWithCandidate.add(piece.bounds());
                List<BoundingBox> reservedWithCandidate = new ObjectArrayList<>(reservedClearances);
                reserveExteriorClearance(definition, piece, reservedWithCandidate);
                if (hasUnfillableOpenConnector(piece.openConnectors(), openConnectors, openConnector, collisionBounds(occupied, reservedClearances), boundary, allowVerticalConnections(maxFloors))) {
                    continue;
                }
                List<StationConnector> usable = normalUsableOpenConnectors(piece.openConnectors(), collisionBounds(occupiedWithCandidate, reservedWithCandidate), boundary, allowVerticalConnections(maxFloors));
                int mainClosures = secondaryConnectorClosureCountToTargets(piece, mainTargets, openConnector);
                if (usable.isEmpty() && mainClosures == 0) {
                    continue;
                }

                int bestNextDistance = nearestConnectorDistance(usable, mainTargets);
                int distanceImprovement = currentDistance == Integer.MAX_VALUE || bestNextDistance == Integer.MAX_VALUE ? 0 : currentDistance - bestNextDistance;
                int score = definition.weight() * 12
                        + SIDE_PASSAGE_SCORE_BONUS
                        + connectorTargetScore(usable, mainTargets)
                        + distanceImprovement * 260
                        + mainClosures * SIDE_PASSAGE_LOOP_SCORE_BONUS
                        + Math.max(0, roomLinkConnectorCount(definition.connectors()) - 2) * 1_200
                        + (mainClosures > 0 ? SIDE_PASSAGE_SCORE_BONUS * 4 : 0)
                        + exteriorSideScore(definition, piece, rotation, occupied)
                        + random.nextInt(16);
                validPlacements.add(new PlacementCandidate(openConnector, piece, score));
            }
        }

        return selectWeightedCandidate(validPlacements, random);
    }

    private int secondaryConnectorClosureCountToTargets(PlacedStationPiece piece, List<StationConnector> targets, StationConnector sourceConnector) {
        int count = 0;
        for (StationConnector connector : piece.openConnectors()) {
            if (connector.requiresPassage()) {
                continue;
            }
            for (StationConnector target : targets) {
                if (!target.equals(sourceConnector) && !target.requiresPassage() && connectorsCanCloseLoop(target, connector)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private StationConnector closestConnectorToTargets(List<StationConnector> connectors, List<StationConnector> targets) {
        StationConnector best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (StationConnector connector : connectors) {
            if (connector.requiresPassage()) {
                continue;
            }
            int distance = nearestConnectorDistance(connector, targets);
            if (distance < bestDistance) {
                best = connector;
                bestDistance = distance;
            }
        }
        return best;
    }

    private int connectorTargetScore(List<StationConnector> connectors, List<StationConnector> targets) {
        int nearest = Integer.MAX_VALUE;
        for (StationConnector connector : connectors) {
            nearest = Math.min(nearest, nearestConnectorDistance(connector, targets));
        }
        if (nearest == Integer.MAX_VALUE) {
            return 0;
        }
        return Math.max(0, SIDE_PASSAGE_DISTANCE_SCORE - nearest * 120);
    }

    private int nearestConnectorDistance(StationConnector connector, List<StationConnector> targets) {
        int nearest = Integer.MAX_VALUE;
        for (StationConnector target : targets) {
            nearest = Math.min(nearest, connectorDistance(connector, target));
        }
        return nearest;
    }

    private int nearestConnectorDistance(List<StationConnector> connectors, List<StationConnector> targets) {
        int nearest = Integer.MAX_VALUE;
        for (StationConnector connector : connectors) {
            nearest = Math.min(nearest, nearestConnectorDistance(connector, targets));
        }
        return nearest;
    }

    private int connectorDistance(StationConnector left, StationConnector right) {
        return Math.abs(left.position().getX() - right.position().getX())
                + Math.abs(left.position().getY() - right.position().getY())
                + Math.abs(left.position().getZ() - right.position().getZ());
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
            Optional<StructureTemplate> template = template(definition);
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

    private int floorTransitionScore(StationPieceDefinition definition, boolean needed) {
        if (!isFloorTransitionPiece(definition)) {
            return 0;
        }
        return needed ? FLOOR_TRANSITION_NEEDED_SCORE_BONUS : FLOOR_TRANSITION_SCORE_BONUS;
    }

    private boolean hasFloorTransitionPiece(StationStructureLibraryData library, List<PlacedStationPiece> pieces) {
        for (PlacedStationPiece piece : pieces) {
            if (piece.bounds().maxY() - piece.bounds().minY() + 1 > FLOOR_HEIGHT_BLOCKS) {
                return true;
            }
            Optional<StationPieceDefinition> definition = library.piece(piece.definitionId());
            if (definition.isPresent() && isFloorTransitionPiece(definition.get())) {
                return true;
            }
        }
        return false;
    }

    private boolean isFloorTransitionPiece(StationPieceDefinition definition) {
        if (definition.floorSpan() > 1) {
            return true;
        }
        for (String tag : definition.tags()) {
            if (FLOOR_TRANSITION_TAGS.contains(tag)) {
                return true;
            }
        }
        return false;
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
        int bestScore = candidates.get(0).score();
        int candidateLimit = 0;
        while (candidateLimit < candidates.size()
                && candidates.get(candidateLimit).score() >= bestScore - CANDIDATE_SCORE_WINDOW) {
            candidateLimit++;
        }

        int minScore = candidates.get(candidateLimit - 1).score();
        int totalWeight = 0;
        for (int i = 0; i < candidateLimit; i++) {
            totalWeight += candidateSelectionWeight(candidates.get(i).score(), minScore);
        }
        int roll = random.nextInt(Math.max(1, totalWeight));
        for (int i = 0; i < candidateLimit; i++) {
            roll -= candidateSelectionWeight(candidates.get(i).score(), minScore);
            if (roll < 0) {
                return candidates.get(i);
            }
        }
        return candidates.get(0);
    }

    private int candidateSelectionWeight(int score, int minScore) {
        return Math.max(1, Math.min(CANDIDATE_SCORE_WEIGHT_CAP, score - minScore + 1));
    }

    private Set<String> pieceCapabilityTags(StationPieceDefinition definition) {
        return pieceCapabilityTagCounts(definition).keySet();
    }

    private Object2IntMap<String> pieceCapabilityTagCounts(StationPieceDefinition definition) {
        Object2IntOpenHashMap<String> tags = new Object2IntOpenHashMap<>();
        for (String tag : definition.tags()) {
            addNormalizedTag(tags, tag, 1);
        }
        for (StationTriggerZone zone : definition.triggerZones()) {
            addNormalizedTag(tags, zone.type(), 1);
            if (zone.data().contains(TagsConstants.Keys.TAG)) {
                addNormalizedTag(tags, zone.data().getString(TagsConstants.Keys.TAG), 1);
            }
            if (zone.data().contains(TagsConstants.Keys.TAGS)) {
                addNormalizedTags(tags, zone.data().getString(TagsConstants.Keys.TAGS), 1);
            }
        }
        return tags;
    }

    private void addNormalizedTags(Object2IntMap<String> tags, String value, int count) {
        if (value == null) {
            return;
        }
        for (String part : value.split("[,;]")) {
            addNormalizedTag(tags, part, count);
        }
    }

    private void addNormalizedTag(Object2IntMap<String> tags, String value, int count) {
        if (value == null) {
            return;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.isBlank()) {
            tags.mergeInt(normalized, Math.max(1, count), Integer::sum);
        }
    }

    private void incrementPieceUsage(Object2IntMap<ResourceLocation> pieceUsage, PlacedStationPiece piece) {
        pieceUsage.merge(piece.definitionId(), 1, Integer::sum);
    }

    private void incrementTagUsage(StationStructureLibraryData library, Object2IntMap<String> tagUsage, PlacedStationPiece piece) {
        library.piece(piece.definitionId()).ifPresent(definition -> {
            pieceCapabilityTagCounts(definition).forEach((tag, count) -> tagUsage.merge(tag, Math.max(1, count), Integer::sum));
        });
    }

    private List<StationPieceDefinition> definitions(StationStructureLibraryData library, List<ResourceLocation> ids, float danger) {
        List<StationPieceDefinition> definitions = new ObjectArrayList<>();
        for (ResourceLocation id : ids) {
            library.piece(id).filter(piece -> piece.canSpawnAtDanger(danger)).ifPresent(definitions::add);
        }
        return definitions;
    }

    private List<StationPieceDefinition> candidateRoomDefinitions(StationStructureLibraryData library, StationPoolDefinition pool, StationGenerationSettings settings, float danger) {
        Set<ResourceLocation> ids = new LinkedHashSet<>(pool.roomPieces());
        ids.addAll(settings.requiredPieces().keySet());
        for (StationPieceDefinition definition : library.pieces()) {
            if (matchesRequiredTag(settings, definition)) {
                ids.add(definition.id());
            }
        }
        return definitions(library, List.copyOf(ids), danger);
    }

    private String requirementSummary(StationGenerationSettings settings) {
        List<String> requirements = new ArrayList<>();
        settings.requiredPieces().forEach((id, count) -> requirements.add("piece " + id + " x" + count));
        settings.requiredPieceTags().forEach((tag, count) -> requirements.add("tag " + tag + " x" + count));
        return requirements.isEmpty() ? "none" : String.join(", ", requirements);
    }

    private String missingRequirements(StationGenerationSettings settings, Map<ResourceLocation, Integer> pieceUsage, Map<String, Integer> tagUsage) {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Integer> entry : settings.requiredPieces().entrySet()) {
            int have = pieceUsage.getOrDefault(entry.getKey(), 0);
            if (have < entry.getValue()) {
                missing.add("piece " + entry.getKey() + " " + have + "/" + entry.getValue());
            }
        }
        for (Map.Entry<String, Integer> entry : settings.requiredPieceTags().entrySet()) {
            int have = tagUsage.getOrDefault(entry.getKey(), 0);
            if (have < entry.getValue()) {
                missing.add("tag " + entry.getKey() + " " + have + "/" + entry.getValue());
            }
        }
        return missing.isEmpty() ? "none" : String.join(", ", missing);
    }

    private int requiredPassageConnectorCount(List<StationConnector> connectors) {
        int count = 0;
        for (StationConnector connector : connectors) {
            if (connector.requiresPassage()) {
                count++;
            }
        }
        return count;
    }

    private String connectorExamples(List<StationConnector> connectors, int limit) {
        if (connectors.isEmpty()) {
            return "none";
        }
        List<String> examples = new ArrayList<>();
        for (StationConnector connector : connectors) {
            if (examples.size() >= limit) {
                break;
            }
            examples.add(connector.position() + " -> " + connector.direction() + (connector.requiresPassage() ? " requiresPassage" : ""));
        }
        return String.join("; ", examples);
    }

    private boolean requiredPiecesSatisfied(StationGenerationSettings settings, Map<ResourceLocation, Integer> pieceUsage, Map<String, Integer> tagUsage) {
        for (Map.Entry<ResourceLocation, Integer> entry : settings.requiredPieces().entrySet()) {
            if (pieceUsage.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        for (Map.Entry<String, Integer> entry : settings.requiredPieceTags().entrySet()) {
            if (tagUsage.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private int requiredPieceRemaining(StationGenerationSettings settings, Map<ResourceLocation, Integer> pieceUsage, Map<String, Integer> tagUsage, StationPieceDefinition definition) {
        int remaining = Math.max(0, settings.requiredPieceCount(definition.id()) - pieceUsage.getOrDefault(definition.id(), 0));
        for (String tag : pieceCapabilityTags(definition)) {
            remaining = Math.max(remaining, Math.max(0, settings.requiredPieceTagCount(tag) - tagUsage.getOrDefault(tag, 0)));
        }
        return remaining;
    }

    private boolean matchesRequiredTag(StationGenerationSettings settings, StationPieceDefinition definition) {
        for (String tag : pieceCapabilityTags(definition)) {
            if (settings.requiredPieceTagCount(tag) > 0) {
                return true;
            }
        }
        return false;
    }

    private int requiredPieceScore(int requiredRemaining) {
        return requiredRemaining <= 0 ? 0 : REQUIRED_PIECE_SCORE_BONUS + requiredRemaining * 500;
    }

    private int requiredGroupClusterScore(
            StationStructureLibraryData library,
            StationGenerationSettings settings,
            Map<ResourceLocation, Integer> pieceUsage,
            Map<String, Integer> tagUsage,
            List<PlacedStationPiece> pieces,
            StationConnector openConnector,
            StationPieceDefinition definition,
            PlacedStationPiece candidate
    ) {
        String group = requiredGroup(settings, pieceUsage, tagUsage, definition);
        if (group.isBlank()) {
            return 0;
        }

        PlacedStationPiece owner = connectorOwner(pieces, openConnector);
        String ownerGroup = owner == null
                ? ""
                : library.piece(owner.definitionId()).map(ownerDefinition -> placedRequiredGroup(settings, ownerDefinition)).orElse("");
        int score = ownerGroup.equals(group) ? REQUIRED_GROUP_CLUSTER_SCORE_BONUS : -REQUIRED_GROUP_FOREIGN_OWNER_PENALTY;

        int nearestSameGroupDistance = nearestGroupDistance(library, settings, pieces, candidate, group);
        if (nearestSameGroupDistance == 0) {
            score += REQUIRED_GROUP_CLUSTER_SCORE_BONUS;
        } else if (nearestSameGroupDistance < Integer.MAX_VALUE) {
            score += Math.max(0, REQUIRED_GROUP_NEARBY_SCORE_BONUS - nearestSameGroupDistance / 4);
        }
        return score;
    }

    private String requiredGroup(
            StationGenerationSettings settings,
            Map<ResourceLocation, Integer> pieceUsage,
            Map<String, Integer> tagUsage,
            StationPieceDefinition definition
    ) {
        int remainingById = Math.max(0, settings.requiredPieceCount(definition.id()) - pieceUsage.getOrDefault(definition.id(), 0));
        if (remainingById > 0) {
            return "piece:" + definition.id();
        }
        for (String tag : pieceCapabilityTags(definition)) {
            int remainingByTag = Math.max(0, settings.requiredPieceTagCount(tag) - tagUsage.getOrDefault(tag, 0));
            if (remainingByTag > 0) {
                return "tag:" + tag;
            }
        }
        return "";
    }

    private String placedRequiredGroup(StationGenerationSettings settings, StationPieceDefinition definition) {
        if (settings.requiredPieceCount(definition.id()) > 0) {
            return "piece:" + definition.id();
        }
        for (String tag : pieceCapabilityTags(definition)) {
            if (settings.requiredPieceTagCount(tag) > 0) {
                return "tag:" + tag;
            }
        }
        return "";
    }

    private PlacedStationPiece connectorOwner(List<PlacedStationPiece> pieces, StationConnector connector) {
        if (connector == null) {
            return null;
        }
        for (PlacedStationPiece piece : pieces) {
            if (piece.openConnectors().contains(connector)) {
                return piece;
            }
        }
        return null;
    }

    private int nearestGroupDistance(
            StationStructureLibraryData library,
            StationGenerationSettings settings,
            List<PlacedStationPiece> pieces,
            PlacedStationPiece candidate,
            String group
    ) {
        int nearest = Integer.MAX_VALUE;
        for (PlacedStationPiece piece : pieces) {
            String placedGroup = library.piece(piece.definitionId()).map(definition -> placedRequiredGroup(settings, definition)).orElse("");
            if (!group.equals(placedGroup)) {
                continue;
            }
            nearest = Math.min(nearest, boundsDistance(candidate.bounds(), piece.bounds()));
        }
        return nearest;
    }

    private int boundsDistance(BoundingBox left, BoundingBox right) {
        int dx = axisGap(left.minX(), left.maxX(), right.minX(), right.maxX());
        int dy = axisGap(left.minY(), left.maxY(), right.minY(), right.maxY());
        int dz = axisGap(left.minZ(), left.maxZ(), right.minZ(), right.maxZ());
        return dx * dx + dy * dy + dz * dz;
    }

    private int axisGap(int leftMin, int leftMax, int rightMin, int rightMax) {
        if (leftMax < rightMin) {
            return rightMin - leftMax - 1;
        }
        if (rightMax < leftMin) {
            return leftMin - rightMax - 1;
        }
        return 0;
    }

    private void shuffleWeighted(List<StationPieceDefinition> definitions, RandomSource random) {
        definitions.sort((left, right) -> Integer.compare(
                random.nextInt(Math.max(1, right.weight())),
                random.nextInt(Math.max(1, left.weight()))
        ));
    }

    private Optional<StructureTemplate> template(StationPieceDefinition definition) {
        return templateCache.getOrDefault(definition.template(), Optional.empty());
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
            triggerZones.add(StationPlacementUtil.transformTrigger(triggerZone, origin, rotation, danger, definition.selectionMin(), definition.selectionMax()));
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


    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return current.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static final class LayoutDiagnostics {
        private final String preflight;
        private final AtomicInteger attemptsStarted = new AtomicInteger();
        private final AtomicInteger successfulAttempts = new AtomicInteger();
        private final AtomicInteger bestPieceCount = new AtomicInteger();
        private final ConcurrentHashMap<String, AtomicInteger> failures = new ConcurrentHashMap<>();

        private LayoutDiagnostics(String preflight) {
            this.preflight = preflight;
        }

        static LayoutDiagnostics create(
                StationStructureLibraryData library,
                StationPoolDefinition pool,
                StationGenerationSettings settings,
                float danger,
                StationBoundary boundary,
                StationGenerator generator
        ) {
            int maxFloors = settings.maxFloors();
            List<StationPieceDefinition> startByDanger = generator.definitions(library, pool.startPieces(), danger);
            List<StationPieceDefinition> roomByDanger = generator.definitions(library, pool.roomPieces(), danger);
            long startAllowedFloors = startByDanger.stream().filter(definition -> generator.pieceAllowedForFloors(definition, maxFloors)).count();
            long roomAllowedFloors = roomByDanger.stream().filter(definition -> generator.pieceAllowedForFloors(definition, maxFloors)).count();
            long expandableRooms = roomByDanger.stream()
                    .filter(definition -> generator.pieceAllowedForFloors(definition, maxFloors))
                    .filter(definition -> generator.roomLinkConnectorCount(definition.connectors()) > 1)
                    .count();
            long capRooms = roomByDanger.stream()
                    .filter(definition -> generator.pieceAllowedForFloors(definition, maxFloors))
                    .filter(definition -> generator.roomLinkConnectorCount(definition.connectors()) == 1)
                    .count();

            List<String> missing = new ArrayList<>();
            for (Map.Entry<ResourceLocation, Integer> entry : settings.requiredPieces().entrySet()) {
                Optional<StationPieceDefinition> piece = library.piece(entry.getKey());
                if (piece.isEmpty()) {
                    missing.add("required piece " + entry.getKey() + " not found in library");
                } else if (!piece.get().canSpawnAtDanger(danger)) {
                    missing.add("required piece " + entry.getKey() + " blocked by danger " + danger + " allowed=" + piece.get().minDanger() + "-" + piece.get().maxDanger());
                } else if (!generator.pieceAllowedForFloors(piece.get(), maxFloors)) {
                    missing.add("required piece " + entry.getKey() + " floorSpan=" + piece.get().floorSpan() + " > maxFloors=" + maxFloors);
                }
            }
            for (Map.Entry<String, Integer> entry : settings.requiredPieceTags().entrySet()) {
                long available = roomByDanger.stream()
                        .filter(definition -> generator.pieceCapabilityTags(definition).contains(entry.getKey()))
                        .filter(definition -> generator.pieceAllowedForFloors(definition, maxFloors))
                        .count();
                if (available <= 0) {
                    missing.add("required tag " + entry.getKey() + " has no room pieces at danger=" + danger + " maxFloors=" + maxFloors);
                }
            }

            String preflight = "diagnostics{"
                    + "seed=" + settings.seed()
                    + ", danger=" + danger
                    + ", rooms=" + settings.minRooms() + "-" + settings.maxRooms()
                    + ", poolLimits=" + pool.minRooms() + "-" + pool.maxRooms()
                    + ", maxFloors=" + maxFloors
                    + ", startPieces=" + pool.startPieces().size() + "/danger=" + startByDanger.size() + "/floors=" + startAllowedFloors
                    + ", roomPieces=" + pool.roomPieces().size() + "/danger=" + roomByDanger.size() + "/floors=" + roomAllowedFloors
                    + ", expandableRooms=" + expandableRooms
                    + ", capRooms=" + capRooms
                    + ", required=" + generator.requirementSummary(settings)
                    + ", preflightIssues=" + (missing.isEmpty() ? "none" : String.join(" | ", missing))
                    + "}";
            return new LayoutDiagnostics(preflight);
        }

        void recordAttemptStarted() {
            attemptsStarted.incrementAndGet();
        }

        void recordSuccess(int pieceCount) {
            successfulAttempts.incrementAndGet();
            bestPieceCount.accumulateAndGet(pieceCount, Math::max);
        }

        void recordFailure(String reason) {
            failures.computeIfAbsent(reason, key -> new AtomicInteger()).incrementAndGet();
        }

        String summary() {
            String topFailures = failures.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<String, AtomicInteger>>comparingInt(entry -> entry.getValue().get()).reversed())
                    .limit(5)
                    .map(entry -> entry.getKey() + " x" + entry.getValue().get())
                    .reduce((left, right) -> left + " | " + right)
                    .orElse("none");
            return preflight
                    + " attempts=" + attemptsStarted.get()
                    + ", successfulAttempts=" + successfulAttempts.get()
                    + ", bestPieceCount=" + bestPieceCount.get()
                    + ", failures=" + topFailures;
        }
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
