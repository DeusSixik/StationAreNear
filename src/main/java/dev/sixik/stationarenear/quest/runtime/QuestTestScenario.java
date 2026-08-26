package dev.sixik.stationarenear.quest.runtime;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.navigation.StationCodeGenerator;
import dev.sixik.stationarenear.navigation.api.SolarNavigationApi;
import dev.sixik.stationarenear.navigation.data.SolarNavigationQuestMarker;
import dev.sixik.stationarenear.navigation.data.SolarNavigationShipState;
import dev.sixik.stationarenear.navigation.registry.SolarNavigationBlocks;
import dev.sixik.stationarenear.navigation.world.SolarNavigationSavedData;
import dev.sixik.stationarenear.navigation.world.SolarNavigationStationCleaner;
import dev.sixik.stationarenear.quest.api.QuestApi;
import dev.sixik.stationarenear.quest.data.QuestLocalization;
import dev.sixik.stationarenear.quest.data.QuestStationState;
import dev.sixik.stationarenear.quest.data.QuestTask;
import dev.sixik.stationarenear.quest.registry.StationQuests;
import dev.sixik.stationarenear.quest.world.QuestSavedData;
import dev.sixik.stationarenear.structures.data.PlacedStationPiece;
import dev.sixik.stationarenear.structures.data.PlacedTriggerZone;
import dev.sixik.stationarenear.structures.data.StationInstance;
import dev.sixik.stationarenear.structures.generation.StationGenerationSettings;
import dev.sixik.stationarenear.structures.trigger.StationStructureTriggerType;
import dev.sixik.stationarenear.structures.util.StationStructureIds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class QuestTestScenario {

    public static final String MARKER_ID = StationAreNear.MODID + ":test_quest_station";
    public static final ResourceLocation QUEST_ROOM = StationStructureIds.normalize("quest_room", "quest_room");
    public static final UUID PENDING_STATION_ID = UUID.nameUUIDFromBytes(MARKER_ID.getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private static final int MIN_DISTANCE = 1000;
    private static final int MAX_DISTANCE = 5000;
    private static final int TERMINAL_SEARCH_RADIUS = 64;
    private static final long DURATION_SECONDS = 10L * 60L;
    private static final int TEST_TRASH_REQUIRED = 20;
    private static final int TEST_TRASH_EXTRA = 3;
    private static final String KEY_QUEST_ELEMENT_SPAWN_SKIPS = "questElementSpawnSkips";

    private QuestTestScenario() {
    }

    public static SolarNavigationQuestMarker createQuestMarker(ServerLevel level, Vec3 sourcePosition) {
        return createQuestMarker(level, sourcePosition, 0);
    }

    public static SolarNavigationQuestMarker createQuestMarker(ServerLevel level, Vec3 sourcePosition, int trashSpawnSkip) {
        Optional<BlockPos> terminal = nearestNavigationTerminal(level, BlockPos.containing(sourcePosition), TERMINAL_SEARCH_RADIUS);
        SolarNavigationShipState shipState = terminal
                .map(pos -> SolarNavigationSavedData.get(level).shipState(pos))
                .orElse(SolarNavigationShipState.DEFAULT);

        RandomSource random = level.getRandom();
        float distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE + 1);
        double angle = random.nextDouble() * Math.PI * 2.0D;
        float x = shipState.shipX() + (float) Math.cos(angle) * distance;
        float y = shipState.shipY() + (float) Math.sin(angle) * distance;
        long seed = level.getSeed() ^ MARKER_ID.hashCode() ^ Mth.getSeed((int) x, 0, (int) y) ^ random.nextLong();
        SolarNavigationQuestMarker marker = SolarNavigationApi.createQuestDungeon(level, MARKER_ID, "TEST QUEST", x, y, 86.0F, 0xFFF7C45A, seed);
        startPendingQuest(level, marker);
        storePendingTrashSpawnSkip(level, trashSpawnSkip);
        return marker;
    }

    public static void startPendingQuest(ServerLevel level, SolarNavigationQuestMarker marker) {
        QuestApi.startQuestLocalized(
                level,
                PENDING_STATION_ID,
                pendingTasks(),
                testTexts(),
                DURATION_SECONDS,
                StationCodeGenerator.code(marker.seed(), marker.x(), marker.y())
        );
    }

    public static StationGenerationSettings applyQuestRoomRequirement(StationGenerationSettings settings) {
        return settings.withRequiredPieces(Map.of(QUEST_ROOM, 1));
    }

    public static StationGenerationSettings applyQuestRoomRequirement(StationGenerationSettings settings, Map<String, Integer> questElementSpawnSkips) {
        return applyQuestRoomRequirement(settings).withQuestElementSpawnSkips(questElementSpawnSkips);
    }

    public static Map<String, Integer> pendingQuestElementSpawnSkips(ServerLevel level) {
        QuestStationState state = QuestSavedData.get(level).stationIfPresent(PENDING_STATION_ID).orElse(null);
        if (state == null) {
            return Map.of();
        }
        int skip = state.objective(StationQuests.CLEAR_TRASH)
                .map(objective -> objective.progress().getInt("spawnSkip"))
                .orElse(0);
        if (skip <= 0) {
            return Map.of();
        }
        return Map.of(StationQuests.CLEAR_TRASH, skip);
    }

    public static boolean isTestQuestMarker(ServerLevel level, long stationSeed) {
        return SolarNavigationSavedData.get(level).questMarker(MARKER_ID)
                .map(marker -> marker.seed() == stationSeed)
                .orElse(false);
    }

    public static boolean startDockedQuest(ServerLevel level, StationInstance station) {
        Optional<PlacedStationPiece> questRoom = station.pieces().stream()
                .filter(QuestTestScenario::isQuestRoom)
                .findFirst();
        if (questRoom.isEmpty()) {
            return false;
        }

        List<PlacedTriggerZone> questTriggers = questRoom.get().triggerZones().stream()
                .filter(zone -> StationStructureTriggerType.from(zone.type()) == StationStructureTriggerType.QUEST)
                .sorted(Comparator.comparing(PlacedTriggerZone::id))
                .toList();
        if (questTriggers.isEmpty()) {
            return false;
        }

        QuestSpawnPlan spawnPlan = spawnPseudoTrash(level, station, questTriggers, TEST_TRASH_REQUIRED, TEST_TRASH_EXTRA);
        movePendingQuestToStation(level, station, questTriggers, spawnPlan);
        return true;
    }

    private static void movePendingQuestToStation(ServerLevel level, StationInstance station, List<PlacedTriggerZone> questTriggers, QuestSpawnPlan spawnPlan) {
        QuestSavedData data = QuestSavedData.get(level);
        Map<String, String> targets = triggerTargets(questTriggers, spawnPlan);
        QuestStationState source = data.stationIfPresent(PENDING_STATION_ID)
                .orElseGet(() -> createPendingState(level, station));
        QuestStationState moved = source.copyFor(station.id(), targets);
        moved.objective(StationQuests.CLEAR_TRASH).ifPresent(objective -> moved.put(objective.withDisplay(
                spawnPlan.targetCount(),
                objective.text()
        )));
        String code = station.customData().getString(SolarNavigationStationCleaner.KEY_NAVIGATION_STATION_CODE);
        if (!code.isBlank()) {
            moved.displayStationCode(code);
        }
        data.remove(PENDING_STATION_ID);
        data.station(moved);
        data.currentStationId(station.id());
    }

    private static QuestStationState createPendingState(ServerLevel level, StationInstance station) {
        String code = station.customData().getString(SolarNavigationStationCleaner.KEY_NAVIGATION_STATION_CODE);
        QuestApi.startQuestLocalized(level, PENDING_STATION_ID, pendingTasks(), testTexts(), DURATION_SECONDS, code);
        return QuestSavedData.get(level).station(PENDING_STATION_ID);
    }

    private static void storePendingTrashSpawnSkip(ServerLevel level, int trashSpawnSkip) {
        if (trashSpawnSkip <= 0) {
            return;
        }
        QuestSavedData data = QuestSavedData.get(level);
        QuestStationState station = data.station(PENDING_STATION_ID);
        station.objective(StationQuests.CLEAR_TRASH).ifPresent(objective -> {
            CompoundTag progress = objective.progress();
            progress.putInt("spawnSkip", trashSpawnSkip);
            station.put(objective.withProgress(progress));
            data.station(station);
        });
    }

    public static int stop(ServerLevel level) {
        int removed = 0;
        Optional<SolarNavigationQuestMarker> marker = SolarNavigationSavedData.get(level).questMarker(MARKER_ID);
        if (marker.isPresent()) {
            removed += SolarNavigationStationCleaner.clearByNavigationSeed(level, marker.get().seed());
            SolarNavigationApi.removeQuestDungeon(level, MARKER_ID);
            removed++;
        }

        if (QuestApi.clear(level, PENDING_STATION_ID)) {
            removed++;
        }

        List<UUID> testStations = new ArrayList<>();
        for (QuestStationState state : QuestSavedData.get(level).stations()) {
            if (isTestQuest(state)) {
                testStations.add(state.stationId());
            }
        }
        for (UUID stationId : testStations) {
            if (QuestApi.clear(level, stationId)) {
                removed++;
            }
        }
        return removed;
    }

    public static boolean isTestQuest(QuestStationState state) {
        return state.objective(StationQuests.CLEAR_TRASH).isPresent()
                || state.objective(StationQuests.PLACE_ITEM).isPresent()
                || state.objective(StationQuests.REPAIR_BLOCKS).isPresent()
                || state.objective(StationQuests.BUILD_SHEATHING).isPresent()
                || state.objective(StationQuests.REPAIR_DOORS).isPresent();
    }

    private static List<QuestTask> testTasks(List<PlacedTriggerZone> questTriggers) {
        return List.of(
                QuestApi.quest(StationQuests.CLEAR_TRASH, TEST_TRASH_REQUIRED, triggerId(questTriggers, 0)),
                QuestApi.quest(StationQuests.PLACE_ITEM, 1, triggerId(questTriggers, 1)),
                QuestApi.quest(StationQuests.REPAIR_BLOCKS, 1, triggerId(questTriggers, 2)),
                QuestApi.quest(StationQuests.BUILD_SHEATHING, 1, triggerId(questTriggers, 3)),
                QuestApi.quest(StationQuests.REPAIR_DOORS, 1, triggerId(questTriggers, 4))
        );
    }

    private static List<QuestTask> pendingTasks() {
        return List.of(
                QuestApi.quest(StationQuests.CLEAR_TRASH, TEST_TRASH_REQUIRED)
        );
    }

    private static Map<String, String> triggerTargets(List<PlacedTriggerZone> questTriggers, QuestSpawnPlan spawnPlan) {
        Map<String, String> targets = new LinkedHashMap<>();
        targets.put(StationQuests.CLEAR_TRASH, spawnPlan.targetTriggerId().isBlank() ? triggerId(questTriggers, 0) : spawnPlan.targetTriggerId());
        targets.put(StationQuests.PLACE_ITEM, triggerId(questTriggers, 1));
        targets.put(StationQuests.REPAIR_BLOCKS, triggerId(questTriggers, 2));
        targets.put(StationQuests.BUILD_SHEATHING, triggerId(questTriggers, 3));
        targets.put(StationQuests.REPAIR_DOORS, triggerId(questTriggers, 4));
        return targets;
    }

    private static Map<String, QuestLocalization> testTexts() {
        Map<String, QuestLocalization> texts = new LinkedHashMap<>();
        texts.put(StationQuests.CLEAR_TRASH, new QuestLocalization("\u0423\u0431\u0435\u0440\u0438\u0442\u0435 \u043f\u0441\u0435\u0432\u0434\u043e-\u0433\u0440\u044f\u0437\u044c \u0448\u0432\u0430\u0431\u0440\u043e\u0439", "Clean up the test dirt piles with the mop."));
        texts.put(StationQuests.PLACE_ITEM, new QuestLocalization("\u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0435 \u0442\u0435\u0441\u0442\u043e\u0432\u044b\u0439 \u043f\u0440\u0435\u0434\u043c\u0435\u0442 \u0432 \u0437\u043e\u043d\u0435", "Install the test item in the marked zone."));
        texts.put(StationQuests.REPAIR_BLOCKS, new QuestLocalization("\u041f\u043e\u0447\u0438\u043d\u0438\u0442\u0435 \u0431\u043b\u043e\u043a \u0448\u043f\u0430\u043a\u043b\u0451\u0432\u043a\u043e\u0439", "Repair the marked block with putty."));
        texts.put(StationQuests.BUILD_SHEATHING, new QuestLocalization("\u041f\u043e\u0441\u0442\u0440\u043e\u0439\u0442\u0435 \u043e\u0431\u0448\u0438\u0432\u043a\u0443 \u0432 \u0437\u043e\u043d\u0435", "Build station sheathing in the marked zone."));
        texts.put(StationQuests.REPAIR_DOORS, new QuestLocalization("\u041f\u043e\u0447\u0438\u043d\u0438\u0442\u0435 \u0433\u0435\u0440\u043c\u043e\u0434\u0432\u0435\u0440\u044c \u043a\u0443\u0441\u0430\u0447\u043a\u0430\u043c\u0438", "Repair the pressure door with cutters."));
        return texts;
    }

    private static String triggerId(List<PlacedTriggerZone> questTriggers, int index) {
        return questTriggers.get(Math.min(index, questTriggers.size() - 1)).id();
    }

    private static boolean isQuestRoom(PlacedStationPiece piece) {
        return QUEST_ROOM.equals(piece.definitionId()) || QUEST_ROOM.equals(piece.template());
    }

    private static QuestSpawnPlan spawnPseudoTrash(ServerLevel level, StationInstance station, List<PlacedTriggerZone> questTriggers, int requiredCount, int extraCount) {
        int skip = questElementSpawnSkip(station, StationQuests.CLEAR_TRASH);
        int requiredToSpawn = Math.max(0, requiredCount - skip);
        int totalToSpawn = requiredToSpawn + Math.max(0, extraCount);
        if (totalToSpawn <= 0) {
            return new QuestSpawnPlan(requiredCount, Math.max(1, Math.min(requiredCount, skip)), 0, triggerId(questTriggers, 0));
        }

        List<PlacedTriggerZone> clusterZones = clusteredQuestZones(questTriggers, station.seed());
        int requiredPlaced = 0;
        int totalPlaced = 0;
        String targetTriggerId = "";

        for (PlacedTriggerZone zone : clusterZones) {
            if (totalPlaced >= totalToSpawn) {
                break;
            }
            int placed = spawnPseudoTrash(level, zone, totalToSpawn - totalPlaced, totalPlaced);
            if (placed <= 0) {
                continue;
            }
            if (targetTriggerId.isBlank()) {
                targetTriggerId = zone.id();
            }
            int requiredPart = Math.min(placed, Math.max(0, requiredToSpawn - requiredPlaced));
            requiredPlaced += requiredPart;
            totalPlaced += placed;
        }

        if (targetTriggerId.isBlank()) {
            targetTriggerId = triggerId(questTriggers, 0);
        }
        return new QuestSpawnPlan(requiredCount, Math.max(1, Math.min(requiredCount, requiredPlaced + skip)), requiredPlaced, targetTriggerId);
    }

    private static List<PlacedTriggerZone> clusteredQuestZones(List<PlacedTriggerZone> questTriggers, long seed) {
        List<PlacedTriggerZone> shuffled = new ArrayList<>(questTriggers);
        Collections.shuffle(shuffled, new java.util.Random(seed ^ 0x7157A5C0FFEE11L));
        if (shuffled.size() <= 1) {
            return shuffled;
        }

        PlacedTriggerZone anchor = shuffled.get(0);
        shuffled.sort(Comparator
                .comparingInt((PlacedTriggerZone zone) -> zoneDistance(anchor, zone))
                .thenComparing(PlacedTriggerZone::id));
        return shuffled;
    }

    private static int zoneDistance(PlacedTriggerZone left, PlacedTriggerZone right) {
        int dx = center(left.min().getX(), left.max().getX()) - center(right.min().getX(), right.max().getX());
        int dy = center(left.min().getY(), left.max().getY()) - center(right.min().getY(), right.max().getY());
        int dz = center(left.min().getZ(), left.max().getZ()) - center(right.min().getZ(), right.max().getZ());
        return dx * dx + dy * dy + dz * dz;
    }

    private static int center(int min, int max) {
        return Math.floorDiv(min + max, 2);
    }

    private static int spawnPseudoTrash(ServerLevel level, PlacedTriggerZone zone, int count, int variantOffset) {
        BoundingBox bounds = new BoundingBox(
                zone.min().getX(), zone.min().getY(), zone.min().getZ(),
                zone.max().getX(), zone.max().getY(), zone.max().getZ()
        );
        List<BlockPos> positions = candidatePositions(bounds);
        int placed = 0;
        for (BlockPos pos : positions) {
            if (placed >= count) {
                return placed;
            }
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, pseudoTrashState(variantOffset + placed), 3);
                placed++;
            }
        }
        return placed;
    }

    private static BlockState pseudoTrashState(int index) {
        return (index & 1) == 0 ? Blocks.DIRT.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
    }

    private static int questElementSpawnSkip(StationInstance station, String questId) {
        CompoundTag skips = station.customData().getCompound(KEY_QUEST_ELEMENT_SPAWN_SKIPS);
        String normalized = StationGenerationSettings.normalizeQuestId(questId);
        return skips.contains(normalized) ? Math.max(0, skips.getInt(normalized)) : 0;
    }

    private static List<BlockPos> candidatePositions(BoundingBox bounds) {
        List<BlockPos> positions = new ArrayList<>();
        int centerX = center(bounds.minX(), bounds.maxX());
        int centerY = center(bounds.minY(), bounds.maxY());
        int centerZ = center(bounds.minZ(), bounds.maxZ());
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    positions.add(mutable.set(x, y, z).immutable());
                }
            }
        }
        positions.sort(Comparator
                .comparingInt((BlockPos pos) -> manhattan(pos, centerX, centerY, centerZ))
                .thenComparingInt(pos -> squaredDistance(pos, centerX, centerY, centerZ))
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        return positions;
    }

    private static int manhattan(BlockPos pos, int x, int y, int z) {
        return Math.abs(pos.getX() - x) + Math.abs(pos.getY() - y) + Math.abs(pos.getZ() - z);
    }

    private static int squaredDistance(BlockPos pos, int x, int y, int z) {
        int dx = pos.getX() - x;
        int dy = pos.getY() - y;
        int dz = pos.getZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static Optional<BlockPos> nearestNavigationTerminal(ServerLevel level, BlockPos center, int radius) {
        BlockPos min = center.offset(-radius, -radius / 2, -radius);
        BlockPos max = center.offset(radius, radius / 2, radius);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.getBlockState(pos).is(SolarNavigationBlocks.SOLAR_NAVIGATION_TERMINAL.get())) {
                continue;
            }
            double distance = pos.distSqr(center);
            if (distance < bestDistance) {
                best = pos.immutable();
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    private record QuestSpawnPlan(int requestedRequired, int targetCount, int requiredPlaced, String targetTriggerId) {
    }
}
