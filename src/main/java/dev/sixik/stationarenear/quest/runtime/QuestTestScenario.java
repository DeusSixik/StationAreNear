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
import dev.sixik.stationarenear.quest.config.director.DirectorConfigManager;
import dev.sixik.stationarenear.quest.config.director.DirectorProfileConfig;
import dev.sixik.stationarenear.quest.director.DirectorContext;
import dev.sixik.stationarenear.quest.director.DirectorPlan;
import dev.sixik.stationarenear.quest.director.QuestDirector;
import dev.sixik.stationarenear.quest.data.QuestLocalization;
import dev.sixik.stationarenear.quest.data.QuestObjectiveState;
import dev.sixik.stationarenear.quest.data.QuestStationState;
import dev.sixik.stationarenear.quest.data.QuestTask;
import dev.sixik.stationarenear.quest.registry.StationQuests;
import dev.sixik.stationarenear.quest.world.QuestSavedData;
import dev.sixik.stationarenear.ship.block.PressureTightDoorBlock;
import dev.sixik.stationarenear.ship.registry.ShipBlocks;
import dev.sixik.stationarenear.structures.data.PlacedStationPiece;
import dev.sixik.stationarenear.structures.data.PlacedTriggerZone;
import dev.sixik.stationarenear.structures.data.StationInstance;
import dev.sixik.stationarenear.structures.generation.StationGenerationSettings;
import dev.sixik.stationarenear.structures.trigger.StationStructureTriggerType;
import dev.sixik.stationarenear.structures.trigger.StationTriggerHandlers;
import dev.sixik.stationarenear.structures.util.StationStructureIds;
import dev.sixik.stationarenear.structures.util.TagsConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
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
import java.util.Set;
import java.util.UUID;

public final class QuestTestScenario {

    public static final String MARKER_ID = StationAreNear.MODID + ":test_quest_station";
    public static final ResourceLocation QUEST_ROOM = StationStructureIds.normalize(TagsConstants.Quest.QUEST_ROOM, TagsConstants.Quest.QUEST_ROOM);
    public static final UUID PENDING_STATION_ID = UUID.nameUUIDFromBytes(MARKER_ID.getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private static final String DEFAULT_DIRECTOR_ID = DirectorConfigManager.DEFAULT_PROFILE_ID;
    private static final int TERMINAL_SEARCH_RADIUS = 64;
    private static final int TEST_TRASH_EXTRA = 3;
    private static final String TEST_FRIDGE_TAG = TagsConstants.Quest.SOCKET;
    private static final String TEST_SINK_TAG = TagsConstants.Quest.PIPES;
    private static final String TEST_TRASH_ZONE_TAG = TagsConstants.Quest.TRASH;
    private static final String KEY_QUEST_ELEMENT_SPAWN_SKIPS = "questElementSpawnSkips";
    private static final String KEY_TARGET_TRIGGER_IDS = "targetTriggerIds";

    private QuestTestScenario() {
    }

    public static SolarNavigationQuestMarker createQuestMarker(ServerLevel level, Vec3 sourcePosition) {
        return createQuestMarker(level, sourcePosition, 0);
    }

    public static SolarNavigationQuestMarker createQuestMarker(ServerLevel level, Vec3 sourcePosition, int trashSpawnSkip) {
        return createQuestMarker(level, sourcePosition, trashSpawnSkip, "");
    }

    public static SolarNavigationQuestMarker createQuestMarker(ServerLevel level, Vec3 sourcePosition, int trashSpawnSkip, String directorId) {
        Optional<BlockPos> terminal = nearestNavigationTerminal(level, BlockPos.containing(sourcePosition), TERMINAL_SEARCH_RADIUS);
        SolarNavigationShipState shipState = terminal
                .map(pos -> SolarNavigationSavedData.get(level).shipState(pos))
                .orElse(SolarNavigationShipState.DEFAULT);

        RandomSource random = level.getRandom();
        DirectorProfileConfig profile = selectProfile(level, random, directorId);
        float distance = profile.rollSpawnDistance(random);
        double angle = random.nextDouble() * Math.PI * 2.0D;
        float x = shipState.shipX() + (float) Math.cos(angle) * distance;
        float y = shipState.shipY() + (float) Math.sin(angle) * distance;
        long seed = level.getSeed() ^ profile.id().hashCode() ^ Mth.getSeed((int) x, 0, (int) y) ^ random.nextLong();
        DirectorPlan plan = createDirectorPlan(level, profile, seed);
        if (plan.tasks().isEmpty()) {
            throw new IllegalStateException("Director profile has no affordable quest offers: " + profile.id());
        }
        SolarNavigationQuestMarker marker = SolarNavigationApi.createQuestDungeon(level, MARKER_ID, plan.displayName(), x, y, profile.markerRadius(), profile.markerColor(), seed);
        startPendingQuest(level, marker, plan);
        storePendingTrashSpawnSkip(level, trashSpawnSkip);
        return marker;
    }

    public static void startPendingQuest(ServerLevel level, SolarNavigationQuestMarker marker) {
        DirectorProfileConfig profile = selectProfile(level, level.getRandom(), "");
        startPendingQuest(level, marker, createDirectorPlan(level, profile, marker.seed()));
    }

    public static void startPendingQuest(ServerLevel level, SolarNavigationQuestMarker marker, DirectorPlan plan) {
        QuestApi.startQuestLocalized(
                level,
                PENDING_STATION_ID,
                plan.tasks(),
                plan.localizations(),
                plan.profile().durationSeconds(),
                StationCodeGenerator.code(marker.seed(), marker.x(), marker.y()),
                plan.profile().moneyReward()
        );
        QuestStationState state = QuestSavedData.get(level).station(PENDING_STATION_ID);
        state.missionId(plan.missionId());
        state.directorPlan(plan.save());
        QuestSavedData.get(level).station(state);
    }

    public static StationGenerationSettings createGenerationSettings(ServerLevel level, long generationSeed, StationGenerationSettings fallback) {
        QuestStationState state = QuestSavedData.get(level).stationIfPresent(PENDING_STATION_ID).orElse(null);
        if (state == null || state.directorPlan().isEmpty()) {
            return DirectorConfigManager.applyQuestRequirements(fallback, Map.of(QUEST_ROOM, 1), Map.of(), pendingQuestElementSpawnSkips(level));
        }
        StationGenerationSettings settings = DirectorConfigManager.createStationSettings(level, state.directorPlan(), generationSeed, fallback);
        Map<String, Integer> skips = new LinkedHashMap<>(settings.questElementSpawnSkips());
        pendingQuestElementSpawnSkips(level).forEach((key, value) -> skips.merge(key, value, Integer::sum));
        return settings.withQuestElementSpawnSkips(skips);
    }

    public static StationGenerationSettings applyQuestRoomRequirement(StationGenerationSettings settings) {
        return DirectorConfigManager.applyQuestRequirements(settings, Map.of(QUEST_ROOM, 1), Map.of(), Map.of());
    }

    public static StationGenerationSettings applyQuestRoomRequirement(StationGenerationSettings settings, Map<String, Integer> questElementSpawnSkips) {
        return DirectorConfigManager.applyQuestRequirements(settings, Map.of(QUEST_ROOM, 1), Map.of(), questElementSpawnSkips);
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

        List<PlacedTriggerZone> placeTriggers = station.pieces().stream()
                .flatMap(piece -> piece.triggerZones().stream())
                .filter(QuestTestScenario::isQuestPlaceTrigger)
                .sorted(Comparator.comparing(PlacedTriggerZone::id))
                .toList();
        List<PlacedTriggerZone> doorTriggers = station.pieces().stream()
                .flatMap(piece -> piece.triggerZones().stream())
                .filter(QuestTestScenario::isDoorTrigger)
                .sorted(Comparator.comparing(PlacedTriggerZone::id))
                .toList();
        List<QuestObjectZoneTarget> objectZoneTriggers = station.pieces().stream()
                .flatMap(piece -> piece.triggerZones().stream()
                        .filter(QuestTestScenario::isQuestObjectZoneTrigger)
                        .map(zone -> new QuestObjectZoneTarget(piece, zone)))
                .sorted(Comparator.comparing((QuestObjectZoneTarget target) -> target.zone().id()))
                .toList();

        QuestStationState pendingState = QuestSavedData.get(level).stationIfPresent(PENDING_STATION_ID)
                .orElseGet(() -> createPendingState(level, station));
        int trashRequired = activeTargetCount(pendingState, StationQuests.CLEAR_TRASH);

        PlaceTargetPlan fridgePlan = hasActiveObjective(pendingState, StationQuests.PLACE_FRIDGE)
                ? selectPlaceTarget(placeTriggers, station.seed(), TEST_FRIDGE_TAG)
                : new PlaceTargetPlan(false, "");
        PlaceTargetPlan microwavePlan = hasActiveObjective(pendingState, StationQuests.PLACE_MICROWAVE)
                ? selectPlaceTarget(placeTriggers, station.seed() ^ 0xA110C0DEL, TEST_FRIDGE_TAG, Set.of(fridgePlan.targetTriggerId()))
                : new PlaceTargetPlan(false, "");
        PlaceTargetPlan sinkPlan = hasActiveObjective(pendingState, StationQuests.PLACE_KITCHEN_SINK)
                ? selectPlaceTarget(placeTriggers, station.seed() ^ 0x51A4C0DEL, TEST_SINK_TAG)
                : new PlaceTargetPlan(false, "");
        DoorSpawnPlan doorPlan = hasActiveObjective(pendingState, StationQuests.REPAIR_DOORS)
                ? spawnBrokenRepairDoor(level, station, doorTriggers)
                : new DoorSpawnPlan(false, "");
        EnergyPanelPlan energyPanelPlan = hasActiveObjective(pendingState, StationQuests.REPAIR_ELECTRIC_PANEL)
                ? spawnRepairEnergyPanel(level, station)
                : new EnergyPanelPlan(false, "");
        QuestSpawnPlan spawnPlan = trashRequired > 0
                ? spawnPseudoTrash(level, station, questTriggers, objectZoneTriggers, trashRequired, TEST_TRASH_EXTRA)
                : new QuestSpawnPlan(0, 0, 0, "", List.of());
        movePendingQuestToStation(level, station, questTriggers, fridgePlan, microwavePlan, sinkPlan, spawnPlan, doorPlan, energyPanelPlan);
        return true;
    }

    private static void movePendingQuestToStation(ServerLevel level, StationInstance station, List<PlacedTriggerZone> questTriggers, PlaceTargetPlan fridgePlan, PlaceTargetPlan microwavePlan, PlaceTargetPlan sinkPlan, QuestSpawnPlan spawnPlan, DoorSpawnPlan doorPlan, EnergyPanelPlan energyPanelPlan) {
        QuestSavedData data = QuestSavedData.get(level);
        Map<String, String> targets = triggerTargets(questTriggers, fridgePlan, microwavePlan, sinkPlan, spawnPlan, doorPlan, energyPanelPlan);
        QuestStationState source = data.stationIfPresent(PENDING_STATION_ID)
                .orElseGet(() -> createPendingState(level, station));
        QuestStationState moved = source.copyFor(station.id(), targets);
        moved.objective(StationQuests.CLEAR_TRASH).ifPresent(objective -> {
            CompoundTag progress = objective.progress();
            putTargetTriggerIds(progress, spawnPlan.targetTriggerIds());
            moved.put(objective.withDisplay(
                    spawnPlan.targetCount(),
                    objective.text()
            ).withProgress(progress));
        });
        if (!fridgePlan.present()) {
            moved.objective(StationQuests.PLACE_FRIDGE).ifPresent(objective -> moved.put(objective.complete(null)));
        }
        if (!microwavePlan.present()) {
            moved.objective(StationQuests.PLACE_MICROWAVE).ifPresent(objective -> moved.put(objective.complete(null)));
        }
        if (!sinkPlan.present()) {
            moved.objective(StationQuests.PLACE_KITCHEN_SINK).ifPresent(objective -> moved.put(objective.complete(null)));
        }
        if (!doorPlan.placed()) {
            moved.objective(StationQuests.REPAIR_DOORS).ifPresent(objective -> moved.put(objective.complete(null)));
        }
        if (!energyPanelPlan.placed()) {
            moved.objective(StationQuests.REPAIR_ELECTRIC_PANEL).ifPresent(objective -> moved.put(objective.complete(null)));
        }
        String code = station.customData().getString(SolarNavigationStationCleaner.KEY_NAVIGATION_STATION_CODE);
        if (!code.isBlank()) {
            moved.displayStationCode(code);
        }
        if (!moved.hasActiveObjectives() && !moved.missionId().isBlank()) {
            data.markQuestCompleted(moved.missionId());
        }
        data.remove(PENDING_STATION_ID);
        data.station(moved);
        data.currentStationId(station.id());
    }

    private static QuestStationState createPendingState(ServerLevel level, StationInstance station) {
        String code = station.customData().getString(SolarNavigationStationCleaner.KEY_NAVIGATION_STATION_CODE);
        DirectorProfileConfig profile = selectProfile(level, level.getRandom(), "");
        DirectorPlan plan = createDirectorPlan(level, profile, station.seed());
        QuestApi.startQuestLocalized(level, PENDING_STATION_ID, plan.tasks(), plan.localizations(), profile.durationSeconds(), code, profile.moneyReward());
        QuestStationState state = QuestSavedData.get(level).station(PENDING_STATION_ID);
        state.missionId(plan.missionId());
        state.directorPlan(plan.save());
        QuestSavedData.get(level).station(state);
        return state;
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
        return !state.missionId().isBlank()
                || state.objective(StationQuests.CLEAR_TRASH).isPresent()
                || state.objective(StationQuests.PLACE_ITEM).isPresent()
                || state.objective(StationQuests.PLACE_FRIDGE).isPresent()
                || state.objective(StationQuests.PLACE_MICROWAVE).isPresent()
                || state.objective(StationQuests.PLACE_KITCHEN_SINK).isPresent()
                || state.objective(StationQuests.REPAIR_BLOCKS).isPresent()
                || state.objective(StationQuests.BUILD_SHEATHING).isPresent()
                || state.objective(StationQuests.REPAIR_DOORS).isPresent()
                || state.objective(StationQuests.REPAIR_ELECTRIC_PANEL).isPresent();
    }

    public static boolean hasAvailableQuest(ServerLevel level) {
        return !DirectorConfigManager.availableProfiles(level).isEmpty();
    }

    public static Optional<String> pendingDirectorName(ServerLevel level) {
        return QuestSavedData.get(level).stationIfPresent(PENDING_STATION_ID)
                .map(QuestStationState::directorPlan)
                .filter(plan -> !plan.isEmpty())
                .map(plan -> plan.getString("displayName"))
                .filter(name -> !name.isBlank());
    }

    public static Optional<DirectorProfileConfig> pendingDirectorProfile(ServerLevel level) {
        return QuestSavedData.get(level).stationIfPresent(PENDING_STATION_ID)
                .map(QuestStationState::missionId)
                .flatMap(DirectorConfigManager::profile);
    }

    private static DirectorProfileConfig selectProfile(ServerLevel level, RandomSource random, String directorId) {
        if (directorId != null && !directorId.isBlank()) {
            return DirectorConfigManager.profile(directorId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown StationAreNear Director profile: " + directorId));
        }
        return DirectorConfigManager.randomAvailableProfile(level, random)
                .or(() -> DirectorConfigManager.profile(DEFAULT_DIRECTOR_ID))
                .or(() -> DirectorConfigManager.defaultProfileConfig())
                .orElseThrow(() -> new IllegalStateException("No configured StationAreNear Director profiles are available"));
    }

    private static DirectorContext directorContext(ServerLevel level, long seed) {
        QuestSavedData data = QuestSavedData.get(level);
        return new DirectorContext(level, seed, -1, -1, data.completedMissionCount(), data.completedQuestIds());
    }

    private static DirectorPlan createDirectorPlan(ServerLevel level, DirectorProfileConfig profile, long seed) {
        return QuestDirector.createPlan(profile, directorContext(level, seed));
    }

    private static boolean hasActiveObjective(QuestStationState state, String questId) {
        return state.objective(questId)
                .map(objective -> !objective.completed())
                .orElse(false);
    }

    private static int activeTargetCount(QuestStationState state, String questId) {
        return state.objective(questId)
                .filter(objective -> !objective.completed())
                .map(QuestObjectiveState::targetCount)
                .orElse(0);
    }

    private static Map<String, String> triggerTargets(List<PlacedTriggerZone> questTriggers, PlaceTargetPlan fridgePlan, PlaceTargetPlan microwavePlan, PlaceTargetPlan sinkPlan, QuestSpawnPlan spawnPlan, DoorSpawnPlan doorPlan, EnergyPanelPlan energyPanelPlan) {
        Map<String, String> targets = new LinkedHashMap<>();
        targets.put(StationQuests.CLEAR_TRASH, spawnPlan.targetTriggerId().isBlank() ? triggerId(questTriggers, 0) : spawnPlan.targetTriggerId());
        targets.put(StationQuests.PLACE_FRIDGE, fridgePlan.targetTriggerId());
        targets.put(StationQuests.PLACE_MICROWAVE, microwavePlan.targetTriggerId());
        targets.put(StationQuests.PLACE_KITCHEN_SINK, sinkPlan.targetTriggerId());
        targets.put(StationQuests.REPAIR_BLOCKS, triggerId(questTriggers, 2));
        targets.put(StationQuests.REPAIR_DOORS, doorPlan.targetTriggerId());
        targets.put(StationQuests.REPAIR_ELECTRIC_PANEL, energyPanelPlan.targetTriggerId());
        return targets;
    }

    private static Map<String, QuestLocalization> testTexts() {
        Map<String, QuestLocalization> texts = new LinkedHashMap<>();
        texts.put(StationQuests.CLEAR_TRASH, new QuestLocalization("\u0423\u0431\u0435\u0440\u0438\u0442\u0435 \u043c\u0443\u0441\u043e\u0440 \u043d\u0430 \u0441\u0442\u0430\u043d\u0446\u0438\u0438", "Clean up the station trash."));
        texts.put(StationQuests.PLACE_FRIDGE, new QuestLocalization("\u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0435 \u0445\u043e\u043b\u043e\u0434\u0438\u043b\u044c\u043d\u0438\u043a \u0440\u044f\u0434\u043e\u043c \u0441 \u0440\u043e\u0437\u0435\u0442\u043a\u043e\u0439", "Install the fridge near the marked power socket."));
        texts.put(StationQuests.PLACE_MICROWAVE, new QuestLocalization("\u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0435 \u043c\u0438\u043a\u0440\u043e\u0432\u043e\u043b\u043d\u043e\u0432\u043a\u0443 \u0440\u044f\u0434\u043e\u043c \u0441 \u0440\u043e\u0437\u0435\u0442\u043a\u043e\u0439", "Install the microwave near the marked power socket."));
        texts.put(StationQuests.PLACE_KITCHEN_SINK, new QuestLocalization("\u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0435 \u0440\u0430\u043a\u043e\u0432\u0438\u043d\u0443 \u0440\u044f\u0434\u043e\u043c \u0441 \u0442\u0440\u0443\u0431\u0430\u043c\u0438", "Install the kitchen sink near the marked pipes."));
        texts.put(StationQuests.REPAIR_BLOCKS, new QuestLocalization("\u041f\u043e\u0447\u0438\u043d\u0438\u0442\u0435 \u0431\u043b\u043e\u043a \u0448\u043f\u0430\u0442\u043b\u0451\u0432\u043a\u043e\u0439", "Repair the marked block with putty."));
        texts.put(StationQuests.REPAIR_DOORS, new QuestLocalization("\u041f\u043e\u0447\u0438\u043d\u0438\u0442\u0435 \u0441\u043b\u043e\u043c\u0430\u043d\u043d\u0443\u044e \u0433\u0435\u0440\u043c\u043e\u0434\u0432\u0435\u0440\u044c \u0438\u043d\u0436\u0435\u043d\u0435\u0440\u043d\u043e\u0439 \u0448\u0435\u0441\u0442\u0435\u0440\u043d\u0451\u0439", "Repair the pressure door with engineering gear."));
        texts.put(StationQuests.REPAIR_ELECTRIC_PANEL, new QuestLocalization("\u041f\u043e\u0447\u0438\u043d\u0438\u0442\u0435 \u044d\u043b\u0435\u043a\u0442\u0440\u0438\u0447\u0435\u0441\u043a\u0438\u0439 \u0449\u0438\u0442\u043e\u043a", "Repair the electrical panel with engineering gear."));
        texts.putAll(DirectorConfigManager.questLocalizations());
        return texts;
    }

    private static String triggerId(List<PlacedTriggerZone> questTriggers, int index) {
        return questTriggers.get(Math.min(index, questTriggers.size() - 1)).id();
    }

    private static boolean isDoorTrigger(PlacedTriggerZone zone) {
        return StationStructureTriggerType.from(zone.type()) == StationStructureTriggerType.DOOR_TRIGGER;
    }

    private static boolean isQuestPlaceTrigger(PlacedTriggerZone zone) {
        return StationStructureTriggerType.from(zone.type()) == StationStructureTriggerType.QUEST_PLACE;
    }

    private static boolean isQuestObjectZoneTrigger(PlacedTriggerZone zone) {
        return StationStructureTriggerType.from(zone.type()) == StationStructureTriggerType.OBJECT_ZONE_PLACER
                && StationTriggerHandlers.isObjectZoneQuestOnly(zone);
    }

    private static PlaceTargetPlan selectPlaceTarget(List<PlacedTriggerZone> placeTriggers, long seed, String requiredTag) {
        return selectPlaceTarget(placeTriggers, seed, requiredTag, Set.of());
    }

    private static PlaceTargetPlan selectPlaceTarget(List<PlacedTriggerZone> placeTriggers, long seed, String requiredTag, Set<String> excludedIds) {
        Set<String> excluded = excludedIds == null ? Set.of() : excludedIds;
        List<PlacedTriggerZone> candidates = placeTriggers.stream()
                .filter(zone -> hasTriggerTag(zone, requiredTag))
                .filter(zone -> !excluded.contains(zone.id()))
                .toList();
        if (candidates.isEmpty()) {
            candidates = placeTriggers.stream()
                    .filter(zone -> hasTriggerTag(zone, requiredTag))
                    .toList();
        }
        if (candidates.isEmpty()) {
            candidates = placeTriggers;
        }
        if (candidates.isEmpty()) {
            return new PlaceTargetPlan(false, "");
        }

        List<PlacedTriggerZone> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, new java.util.Random(seed ^ 0x91ACE51A11L));
        return new PlaceTargetPlan(true, shuffled.get(0).id());
    }

    private static boolean hasTriggerTag(PlacedTriggerZone zone, String requiredTag) {
        if (requiredTag == null || requiredTag.isBlank()) {
            return true;
        }
        String tags = zone.data().contains(TagsConstants.Keys.TAGS) ? zone.data().getString(TagsConstants.Keys.TAGS) : zone.data().getString(TagsConstants.Keys.TAG);
        if (tags == null || tags.isBlank()) {
            return false;
        }
        for (String tag : tags.split(",")) {
            if (requiredTag.equalsIgnoreCase(tag.trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isQuestRoom(PlacedStationPiece piece) {
        return QUEST_ROOM.equals(piece.definitionId()) || QUEST_ROOM.equals(piece.template());
    }

    private static QuestSpawnPlan spawnPseudoTrash(ServerLevel level, StationInstance station, List<PlacedTriggerZone> questTriggers, List<QuestObjectZoneTarget> objectZoneTargets, int requiredCount, int extraCount) {
        List<QuestObjectZoneTarget> trashZoneTargets = objectZoneTargets.stream()
                .filter(target -> isTrashObjectZoneTarget(target.zone()))
                .toList();
        if (!trashZoneTargets.isEmpty()) {
            QuestSpawnPlan plan = spawnObjectZoneTrash(level, station, trashZoneTargets, requiredCount, extraCount);
            if (plan.requiredPlaced() > 0 || questTriggers.isEmpty() || requiredCount <= questElementSpawnSkip(station, StationQuests.CLEAR_TRASH)) {
                return plan;
            }
        }
        return spawnPseudoTrash(level, station, questTriggers, requiredCount, extraCount);
    }

    private static QuestSpawnPlan spawnObjectZoneTrash(ServerLevel level, StationInstance station, List<QuestObjectZoneTarget> objectZoneTargets, int requiredCount, int extraCount) {
        int skip = questElementSpawnSkip(station, StationQuests.CLEAR_TRASH);
        int requiredToSpawn = Math.max(0, requiredCount - skip);
        int totalToSpawn = requiredToSpawn + Math.max(0, extraCount);
        List<QuestObjectZoneTarget> clusterZones = clusteredObjectZoneTargets(objectZoneTargets, station.seed());
        if (totalToSpawn <= 0) {
            List<String> targets = clusterZones.isEmpty() ? List.of() : List.of(clusterZones.get(0).zone().id());
            return new QuestSpawnPlan(requiredCount, Math.max(1, Math.min(requiredCount, skip)), 0, primaryTarget(targets), targets);
        }

        int requiredPlaced = 0;
        int totalPlaced = 0;
        List<String> targetTriggerIds = new ArrayList<>();

        for (int i = 0; i < clusterZones.size() && totalPlaced < totalToSpawn; i++) {
            QuestObjectZoneTarget target = clusterZones.get(i);
            int remaining = totalToSpawn - totalPlaced;
            int zonesRemaining = clusterZones.size() - i;
            int countForZone = Math.max(1, (remaining + zonesRemaining - 1) / zonesRemaining);
            int placed = StationTriggerHandlers.placeBlocksInObjectZoneForQuest(level, target.zone(), DirectorConfigManager.trashBlockStates(), countForZone);
            if (placed <= 0) {
                continue;
            }
            targetTriggerIds.add(target.zone().id());
            int requiredPart = Math.min(placed, Math.max(0, requiredToSpawn - requiredPlaced));
            requiredPlaced += requiredPart;
            totalPlaced += placed;
        }

        return new QuestSpawnPlan(requiredCount, Math.max(1, Math.min(requiredCount, requiredPlaced + skip)), requiredPlaced, primaryTarget(targetTriggerIds), targetTriggerIds);
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
        List<String> targetTriggerIds = new ArrayList<>();

        for (PlacedTriggerZone zone : clusterZones) {
            if (totalPlaced >= totalToSpawn) {
                break;
            }
            int placed = spawnPseudoTrash(level, zone, totalToSpawn - totalPlaced, totalPlaced);
            if (placed <= 0) {
                continue;
            }
            targetTriggerIds.add(zone.id());
            int requiredPart = Math.min(placed, Math.max(0, requiredToSpawn - requiredPlaced));
            requiredPlaced += requiredPart;
            totalPlaced += placed;
        }

        if (targetTriggerIds.isEmpty()) {
            targetTriggerIds.add(triggerId(questTriggers, 0));
        }
        return new QuestSpawnPlan(requiredCount, Math.max(1, Math.min(requiredCount, requiredPlaced + skip)), requiredPlaced, primaryTarget(targetTriggerIds), targetTriggerIds);
    }

    private static boolean isTrashObjectZoneTarget(PlacedTriggerZone zone) {
        return hasTriggerTag(zone, TEST_TRASH_ZONE_TAG)
                || hasTriggerTag(zone, "clear_trash")
                || hasTriggerTag(zone, StationQuests.CLEAR_TRASH);
    }

    private static String primaryTarget(List<String> targetTriggerIds) {
        return targetTriggerIds == null || targetTriggerIds.isEmpty() ? "" : targetTriggerIds.get(0);
    }

    private static void putTargetTriggerIds(CompoundTag progress, List<String> targetTriggerIds) {
        progress.remove(KEY_TARGET_TRIGGER_IDS);
        if (targetTriggerIds == null || targetTriggerIds.isEmpty()) {
            return;
        }
        ListTag list = new ListTag();
        java.util.LinkedHashSet<String> unique = new java.util.LinkedHashSet<>(targetTriggerIds);
        for (String targetTriggerId : unique) {
            if (targetTriggerId != null && !targetTriggerId.isBlank()) {
                list.add(StringTag.valueOf(targetTriggerId));
            }
        }
        if (!list.isEmpty()) {
            progress.put(KEY_TARGET_TRIGGER_IDS, list);
        }
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

    private static List<QuestObjectZoneTarget> clusteredObjectZoneTargets(List<QuestObjectZoneTarget> objectZoneTargets, long seed) {
        List<QuestObjectZoneTarget> shuffled = new ArrayList<>(objectZoneTargets);
        Collections.shuffle(shuffled, new java.util.Random(seed ^ 0x0B1EC75A11L));
        if (shuffled.size() <= 1) {
            return shuffled;
        }

        QuestObjectZoneTarget anchor = shuffled.get(0);
        shuffled.sort(Comparator
                .comparingInt((QuestObjectZoneTarget target) -> zoneDistance(anchor.zone(), target.zone()))
                .thenComparing(target -> target.zone().id()));
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

    private static EnergyPanelPlan spawnRepairEnergyPanel(ServerLevel level, StationInstance station) {
        Optional<PlacedTriggerZone> target = StationTriggerHandlers.selectEnergyPanelTrigger(station);
        if (target.isEmpty()) {
            return new EnergyPanelPlan(false, "");
        }
        boolean placed = StationTriggerHandlers.placeEnergyPanel(level, station, target.get(), false);
        return new EnergyPanelPlan(placed, placed ? target.get().id() : "");
    }

    private static DoorSpawnPlan spawnBrokenRepairDoor(ServerLevel level, StationInstance station, List<PlacedTriggerZone> doorTriggers) {
        List<PlacedTriggerZone> zones = preferredDoorZones(doorTriggers, station.seed());
        for (PlacedTriggerZone zone : zones) {
            BlockPos masterPos = new BlockPos(
                    center(zone.min().getX(), zone.max().getX()),
                    zone.min().getY(),
                    center(zone.min().getZ(), zone.max().getZ())
            );
            if (breakExistingDoor(level, masterPos)) {
                return new DoorSpawnPlan(true, zone.id());
            }

            for (net.minecraft.core.Direction direction : doorDirections(doorDirection(zone, station.stationDirection()))) {
                if (!canPlaceDoor(level, masterPos, direction)) {
                    continue;
                }
                level.setBlock(masterPos, ShipBlocks.STATION_PRESSURE_TIGHT_DOOR.get().defaultBlockState(), 3);
                if (PressureTightDoorBlock.placeDoor(level, masterPos, direction, true, false, doorId(station.seed(), masterPos))) {
                    return new DoorSpawnPlan(true, zone.id());
                }
                level.removeBlock(masterPos, false);
            }
        }
        return new DoorSpawnPlan(false, "");
    }

    private static boolean breakExistingDoor(ServerLevel level, BlockPos masterPos) {
        BlockState state = level.getBlockState(masterPos);
        if (!(state.getBlock() instanceof PressureTightDoorBlock)) {
            return false;
        }
        return PressureTightDoorBlock.setBroken(level, masterPos, true);
    }

    private static net.minecraft.core.Direction doorDirection(PlacedTriggerZone zone, net.minecraft.core.Direction fallback) {
        net.minecraft.core.Direction direction = net.minecraft.core.Direction.byName(zone.data().getString("direction"));
        if (direction != null && direction.getAxis().isHorizontal()) {
            return direction;
        }
        return fallback != null && fallback.getAxis().isHorizontal() ? fallback : net.minecraft.core.Direction.NORTH;
    }

    private static String doorId(long stationSeed, BlockPos pos) {
        long value = stationSeed ^ Mth.getSeed(pos) ^ 0xD00A51DL;
        String code = StationCodeGenerator.code(value).substring(3);
        return "DR-" + code;
    }

    private static List<PlacedTriggerZone> preferredDoorZones(List<PlacedTriggerZone> questTriggers, long seed) {
        List<PlacedTriggerZone> zones = new ArrayList<>(questTriggers);
        if (zones.size() > 4) {
            PlacedTriggerZone preferred = zones.remove(4);
            Collections.shuffle(zones, new java.util.Random(seed ^ 0xD00F5EEDL));
            zones.add(0, preferred);
        } else {
            Collections.shuffle(zones, new java.util.Random(seed ^ 0xD00F5EEDL));
        }
        return zones;
    }

    private static List<net.minecraft.core.Direction> doorDirections(net.minecraft.core.Direction preferred) {
        List<net.minecraft.core.Direction> directions = new ArrayList<>();
        if (preferred != null && preferred.getAxis().isHorizontal()) {
            directions.add(preferred);
        }
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            if (!directions.contains(direction)) {
                directions.add(direction);
            }
        }
        return directions;
    }

    private static boolean canPlaceDoor(ServerLevel level, BlockPos masterPos, net.minecraft.core.Direction facing) {
        for (BlockPos partPos : PressureTightDoorBlock.partPositions(masterPos, facing)) {
            if (!level.getBlockState(partPos).isAir()) {
                return false;
            }
        }
        return true;
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
        List<BlockState> trashStates = DirectorConfigManager.trashBlockStates();
        return trashStates.get(Math.floorMod(index, trashStates.size()));
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

    private record QuestSpawnPlan(int requestedRequired, int targetCount, int requiredPlaced, String targetTriggerId, List<String> targetTriggerIds) {

        private QuestSpawnPlan(int requestedRequired, int targetCount, int requiredPlaced, String targetTriggerId) {
            this(requestedRequired, targetCount, requiredPlaced, targetTriggerId, targetTriggerId == null || targetTriggerId.isBlank() ? List.of() : List.of(targetTriggerId));
        }

        private QuestSpawnPlan {
            targetTriggerIds = targetTriggerIds == null ? List.of() : List.copyOf(targetTriggerIds);
        }
    }

    private record QuestObjectZoneTarget(PlacedStationPiece piece, PlacedTriggerZone zone) {
    }

    private record PlaceTargetPlan(boolean present, String targetTriggerId) {
    }

    private record DoorSpawnPlan(boolean placed, String targetTriggerId) {
    }

    private record EnergyPanelPlan(boolean placed, String targetTriggerId) {
    }
}
