package dev.sixik.stationarenear.structures.trigger;

import dev.sixik.stationarenear.quest.director.DirectorStationSpawnHandler;
import dev.sixik.stationarenear.quest.block.EnergyPanelBlock;
import dev.sixik.stationarenear.quest.registry.QuestBlocks;
import dev.sixik.stationarenear.ship.block.PressureTightDoorBlock;
import dev.sixik.stationarenear.ship.registry.ShipBlocks;
import dev.sixik.stationarenear.structures.config.StationStructureFileStorage;
import dev.sixik.stationarenear.structures.data.PlacedStationPiece;
import dev.sixik.stationarenear.structures.data.PlacedTriggerZone;
import dev.sixik.stationarenear.structures.data.StationInstance;
import dev.sixik.stationarenear.structures.data.StationPieceDefinition;
import dev.sixik.stationarenear.structures.data.StationPoolDefinition;
import dev.sixik.stationarenear.structures.generation.StationPlacementUtil;
import dev.sixik.stationarenear.structures.util.StationStructureIds;
import dev.sixik.stationarenear.structures.util.TagsConstants;
import dev.sixik.stationarenear.structures.world.StationStructureLibraryData;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class StationTriggerHandlers {

    private static final String ENERGY_SWITCH_TAG = TagsConstants.Quest.ELECTRIC_SWITCH;
    private static final int ENERGY_PANEL_DEFAULT_CHANCE = 80;

    private StationTriggerHandlers() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(StationTriggerHandlers::onStationTrigger);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, StationTriggerHandlers::onStructureSpawnTrigger);
    }

    private static void onStationTrigger(StationTriggerEvent event) {
        MobTriggerSpawner.spawnOnActivation(event, DirectorStationSpawnHandler.hasDirectorPlan(event.getStation()));
        SoundTriggerHandler.handleTrigger(event);
    }

    private static void onStructureSpawnTrigger(StationStructureSpawnTriggerEvent event) {
        if (event.isPlacementCanceled()) {
            return;
        }
        switch (event.getTriggerType()) {
            case OBJECT_PLACER, QUEST_OBJECT_PLACER -> {
                if (isGravitationPanelTrigger(event.getZone())) {
                    placeGravitationPanel(event);
                } else if (isOxygenPanelTrigger(event.getZone())) {
                    placeOxygenPanel(event);
                } else if (isEnergySwitchTrigger(event.getZone())) {
                    placeEnergyPanel(event);
                } else if (event.getTriggerType() == StationStructureTriggerType.OBJECT_PLACER) {
                    placeObject(event);
                }
            }
            case OBJECT_ZONE_PLACER -> placeObjectZone(event, event.isForcePlaceObjectZone(), event.getForcedObjectZoneCount());
            case MOB_SPAWN -> MobTriggerSpawner.spawnFromStructureTrigger(event);
            case DOOR_TRIGGER -> placeDoor(event);
            case QUEST_PLACE -> {
                if (isGravitationPanelTrigger(event.getZone())) {
                    placeGravitationPanel(event);
                } else if (isOxygenPanelTrigger(event.getZone())) {
                    placeOxygenPanel(event);
                } else {
                    placeEnergyPanel(event);
                }
            }
            case SOUND_TRIGGER -> SoundTriggerHandler.handleSpawnTrigger(event);
            case QUEST -> {
            }
            default -> {
            }
        }
    }

    public static int placeObjectZoneForQuest(ServerLevel level, StationInstance station, PlacedStationPiece piece, PlacedTriggerZone zone, int count) {
        if (count <= 0 || StationStructureTriggerType.from(zone.type()) != StationStructureTriggerType.OBJECT_ZONE_PLACER) {
            return 0;
        }
        StationStructureSpawnTriggerEvent event = new StationStructureSpawnTriggerEvent(level, station, piece, zone, StationStructureTriggerType.OBJECT_ZONE_PLACER);
        return placeObjectZone(event, true, count);
    }

    public static int placeQuestObjectForQuest(ServerLevel level, StationInstance station, PlacedStationPiece piece, PlacedTriggerZone zone, String requiredObject, int count) {
        if (count <= 0 || StationStructureTriggerType.from(zone.type()) != StationStructureTriggerType.QUEST_OBJECT_PLACER) {
            return 0;
        }
        StationStructureSpawnTriggerEvent event = new StationStructureSpawnTriggerEvent(level, station, piece, zone, StationStructureTriggerType.QUEST_OBJECT_PLACER);
        int placed = 0;
        int attempts = Math.max(8, count * 8);
        while (placed < count && attempts-- > 0) {
            if (placeObject(event, true, "")) {
                placed++;
            }
        }
        return placed;
    }

    public static boolean isObjectZoneQuestOnly(PlacedTriggerZone zone) {
        return isObjectZoneQuestOnly(zone.data());
    }


    public static int placeBlocksInObjectZoneForQuest(ServerLevel level, PlacedTriggerZone zone, List<BlockState> states, int count) {
        if (count <= 0 || StationStructureTriggerType.from(zone.type()) != StationStructureTriggerType.OBJECT_ZONE_PLACER) {
            return 0;
        }
        List<BlockState> validStates = states == null
                ? List.of()
                : states.stream().filter(state -> state != null && !state.isAir()).toList();
        if (validStates.isEmpty()) {
            return 0;
        }

        List<BlockPos> candidates = objectZoneBlockCandidates(level, zone);
        if (candidates.isEmpty()) {
            return 0;
        }
        java.util.Collections.shuffle(candidates, new java.util.Random(level.getRandom().nextLong() ^ zone.id().hashCode()));

        RandomSource random = level.getRandom();
        int placed = 0;
        for (BlockPos pos : candidates) {
            if (placed >= count) {
                break;
            }
            BlockState state = validStates.get(random.nextInt(validStates.size()));
            level.setBlock(pos, state, 3);
            placed++;
        }
        return placed;
    }


    private static void placeEnergyPanel(StationStructureSpawnTriggerEvent event) {
        CompoundTag data = event.getZone().data();
        if (data.contains("energyPanel") && !data.getBoolean("energyPanel")) {
            return;
        }

        boolean isBroken = dev.sixik.stationarenear.structures.lamps.StationLampManager.hasEnergyFailureOffer(event.getStation())
                || (data.contains("broken") && data.getBoolean("broken"));

        Optional<EnergyPanelTarget> selected = selectEnergyPanelTarget(event.getStation(), isBroken);
        if (selected.isEmpty() || !selected.get().matches(event.getPiece(), event.getZone())) {
            return;
        }

        if (data.contains("pool") && !data.getString("pool").isBlank()) {
            String requiredTag = isBroken ? "electrick_broken" : "electrick_normal";
            if (placeObject(event, true, requiredTag)) {
                return;
            }
        }

        placeEnergyPanel(event.getLevel(), event.getStation(), event.getZone(), isBroken);
    }

    public static Optional<PlacedTriggerZone> selectEnergyPanelTrigger(dev.sixik.stationarenear.structures.data.StationInstance station) {
        boolean isBroken = dev.sixik.stationarenear.structures.lamps.StationLampManager.hasEnergyFailureOffer(station);
        return selectEnergyPanelTarget(station, isBroken).map(EnergyPanelTarget::zone);
    }

    public static boolean placeEnergyPanel(net.minecraft.server.level.ServerLevel level, dev.sixik.stationarenear.structures.data.StationInstance station, PlacedTriggerZone zone, boolean broken) {
        BlockPos pos = centerPos(zone);
        Direction facing = panelFacing(zone.data(), station.stationDirection());
        level.setBlock(pos, QuestBlocks.ENERGY_PANEL.get().defaultBlockState()
                .setValue(EnergyPanelBlock.FACING, facing)
                .setValue(EnergyPanelBlock.POWERED, !broken)
                .setValue(EnergyPanelBlock.BROKEN, broken), 3);
        return true;
    }

    private static Optional<EnergyPanelTarget> selectEnergyPanelTarget(dev.sixik.stationarenear.structures.data.StationInstance station, boolean isBroken) {
        List<EnergyPanelTarget> topPriority = new ObjectArrayList<>();
        List<EnergyPanelTarget> highPriority = new ObjectArrayList<>();
        List<EnergyPanelTarget> mediumPriority = new ObjectArrayList<>();
        List<EnergyPanelTarget> fallback = new ObjectArrayList<>();

        String targetSpecificTag = isBroken ? "electrick_broken" : "electrick_normal";
        String altSpecificTag = isBroken ? "electric_broken" : "electric_normal";

        for (PlacedStationPiece piece : station.pieces()) {
            for (PlacedTriggerZone zone : piece.triggerZones()) {
                StationStructureTriggerType type = StationStructureTriggerType.from(zone.type());
                if (type != StationStructureTriggerType.QUEST_PLACE
                        && type != StationStructureTriggerType.QUEST_OBJECT_PLACER
                        && type != StationStructureTriggerType.OBJECT_PLACER) {
                    continue;
                }
                EnergyPanelTarget target = new EnergyPanelTarget(piece, zone);
                if (hasTriggerTag(zone, targetSpecificTag) || hasTriggerTag(zone, altSpecificTag)) {
                    topPriority.add(target);
                } else if (hasTriggerTag(zone, ENERGY_SWITCH_TAG) || hasTriggerTag(zone, "energy_panel") || hasTriggerTag(zone, "electrick") || hasTriggerTag(zone, "electric")) {
                    highPriority.add(target);
                } else if (isEnergySwitchTrigger(zone)) {
                    mediumPriority.add(target);
                } else {
                    fallback.add(target);
                }
            }
        }
        List<EnergyPanelTarget> candidates = !topPriority.isEmpty() ? topPriority
                : (!highPriority.isEmpty() ? highPriority
                : (!mediumPriority.isEmpty() ? mediumPriority : fallback));
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        candidates.sort(java.util.Comparator
                .comparing((EnergyPanelTarget target) -> target.zone().id())
                .thenComparingInt(target -> target.zone().min().getX())
                .thenComparingInt(target -> target.zone().min().getY())
                .thenComparingInt(target -> target.zone().min().getZ()));
        RandomSource random = RandomSource.create(station.seed() ^ 0xE13C7A11B0A2L);
        return Optional.of(candidates.get(random.nextInt(candidates.size())));
    }

    public static boolean isGravitationPanelTrigger(PlacedTriggerZone zone) {
        return hasTriggerTag(zone, "gravitation_panel")
                || hasTriggerTag(zone, "gravity_panel")
                || hasTriggerTag(zone, "gravitation")
                || hasTriggerTag(zone, "gravity")
                || hasTriggerTag(zone, "broken_gravitation")
                || hasTriggerTag(zone, "broken_gravity");
    }

    public static boolean isOxygenPanelTrigger(PlacedTriggerZone zone) {
        return hasTriggerTag(zone, "oxygen_panel")
                || hasTriggerTag(zone, "oxygen")
                || hasTriggerTag(zone, "broken_oxygen")
                || hasTriggerTag(zone, "oxygen_system");
    }

    public static Optional<PlacedTriggerZone> selectGravitationPanelTrigger(StationInstance station) {
        List<PlacedTriggerZone> zones = selectGravitationPanelTriggers(station, 1);
        return zones.isEmpty() ? Optional.empty() : Optional.of(zones.get(0));
    }

    public static List<PlacedTriggerZone> selectGravitationPanelTriggers(StationInstance station, int count) {
        if (count <= 0) {
            return List.of();
        }
        List<PlacedTriggerZone> zones = new ArrayList<>();
        for (PlacedStationPiece piece : station.pieces()) {
            for (PlacedTriggerZone zone : piece.triggerZones()) {
                if (isGravitationPanelTrigger(zone)) {
                    zones.add(zone);
                }
            }
        }
        if (zones.isEmpty()) {
            return List.of();
        }
        zones.sort(java.util.Comparator.comparing(PlacedTriggerZone::id));
        java.util.Collections.shuffle(zones, new java.util.Random(station.seed() ^ 0x68417101L));
        return zones.subList(0, Math.min(count, zones.size()));
    }

    public static Optional<PlacedTriggerZone> selectOxygenPanelTrigger(StationInstance station) {
        List<PlacedTriggerZone> zones = selectOxygenPanelTriggers(station, 1);
        return zones.isEmpty() ? Optional.empty() : Optional.of(zones.get(0));
    }

    public static List<PlacedTriggerZone> selectOxygenPanelTriggers(StationInstance station, int count) {
        if (count <= 0) {
            return List.of();
        }
        List<PlacedTriggerZone> zones = new ArrayList<>();
        for (PlacedStationPiece piece : station.pieces()) {
            for (PlacedTriggerZone zone : piece.triggerZones()) {
                if (isOxygenPanelTrigger(zone)) {
                    zones.add(zone);
                }
            }
        }
        if (zones.isEmpty()) {
            return List.of();
        }
        zones.sort(java.util.Comparator.comparing(PlacedTriggerZone::id));
        java.util.Collections.shuffle(zones, new java.util.Random(station.seed() ^ 0x0276E101L));
        return zones.subList(0, Math.min(count, zones.size()));
    }

    public static boolean placeGravitationPanel(ServerLevel level, StationInstance station, PlacedTriggerZone zone, boolean broken) {
        BlockPos pos = centerPos(zone);
        Direction facing = panelFacing(zone.data(), station.stationDirection());
        level.setBlock(pos, QuestBlocks.GRAVITATION_PANEL.get().defaultBlockState()
                .setValue(dev.sixik.stationarenear.quest.block.WallMountedPanelBlock.FACING, facing)
                .setValue(dev.sixik.stationarenear.quest.block.WallMountedPanelBlock.BROKEN, broken), 3);
        dev.sixik.stationarenear.structures.gravity.StationGravitationManager.onPanelPlaced(level, pos, broken);
        return true;
    }

    public static boolean placeOxygenPanel(ServerLevel level, StationInstance station, PlacedTriggerZone zone, boolean broken) {
        BlockPos pos = centerPos(zone);
        Direction facing = panelFacing(zone.data(), station.stationDirection());
        level.setBlock(pos, QuestBlocks.OXYGEN_PANEL.get().defaultBlockState()
                .setValue(dev.sixik.stationarenear.quest.block.WallMountedPanelBlock.FACING, facing)
                .setValue(dev.sixik.stationarenear.quest.block.WallMountedPanelBlock.BROKEN, broken), 3);
        dev.sixik.stationarenear.structures.oxygen.StationOxygenManager.onPanelPlaced(level, pos, broken);
        return true;
    }

    public static void placeGravitationPanel(StationStructureSpawnTriggerEvent event) {
        CompoundTag data = event.getZone().data();
        if (!event.isForcePlaceObjectZone() && !data.getBoolean("placeGravitationPanel")) {
            return;
        }
        boolean isBroken = dev.sixik.stationarenear.structures.gravity.StationGravitationManager.hasGravitationFailureOffer(event.getStation())
                || (data.contains("broken") && data.getBoolean("broken"))
                || (data.contains("brokenGravitation") && data.getBoolean("brokenGravitation"))
                || hasTriggerTag(event.getZone(), "broken_gravity")
                || hasTriggerTag(event.getZone(), "broken_gravitation");

        if (data.contains("pool") && !data.getString("pool").isBlank()) {
            String requiredTag = isBroken ? "broken_gravity" : "gravitation_panel";
            if (placeObject(event, true, requiredTag)) {
                return;
            }
        }
        placeGravitationPanel(event.getLevel(), event.getStation(), event.getZone(), isBroken);
    }

    public static void placeOxygenPanel(StationStructureSpawnTriggerEvent event) {
        CompoundTag data = event.getZone().data();
        if (!event.isForcePlaceObjectZone() && !data.getBoolean("placeOxygenPanel")) {
            return;
        }
        boolean isBroken = dev.sixik.stationarenear.structures.oxygen.StationOxygenManager.hasOxygenFailureOffer(event.getStation())
                || (data.contains("broken") && data.getBoolean("broken"))
                || (data.contains("brokenOxygen") && data.getBoolean("brokenOxygen"))
                || hasTriggerTag(event.getZone(), "broken_oxygen");

        if (data.contains("pool") && !data.getString("pool").isBlank()) {
            String requiredTag = isBroken ? "broken_oxygen" : "oxygen_panel";
            if (placeObject(event, true, requiredTag)) {
                return;
            }
        }
        placeOxygenPanel(event.getLevel(), event.getStation(), event.getZone(), isBroken);
    }

    private static boolean isEnergySwitchTrigger(PlacedTriggerZone zone) {
        return hasTriggerTag(zone, "electrick_broken")
                || hasTriggerTag(zone, "electrick_normal")
                || hasTriggerTag(zone, "electric_broken")
                || hasTriggerTag(zone, "electric_normal")
                || hasTriggerTag(zone, ENERGY_SWITCH_TAG)
                || hasTriggerTag(zone, "energy_panel")
                || hasTriggerTag(zone, "electrick")
                || hasTriggerTag(zone, "electric")
                || hasTriggerTag(zone, "electricity");
    }

    private static boolean hasTriggerTag(PlacedTriggerZone zone, String requiredTag) {
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

    private static Direction panelFacing(CompoundTag data, Direction fallback) {
        Direction parsed = Direction.byName(data.getString("direction").toLowerCase(Locale.ROOT));
        if (parsed != null && parsed.getAxis().isHorizontal()) {
            return parsed;
        }
        Direction objectDirection = Direction.byName(data.getString("objectDirection").toLowerCase(Locale.ROOT));
        if (objectDirection != null && objectDirection.getAxis().isHorizontal()) {
            return objectDirection;
        }
        return fallback.getAxis().isHorizontal() ? fallback : Direction.NORTH;
    }

    private static BlockPos centerPos(PlacedTriggerZone zone) {
        return new BlockPos(
                Math.floorDiv(zone.min().getX() + zone.max().getX(), 2),
                Math.floorDiv(zone.min().getY() + zone.max().getY(), 2),
                Math.floorDiv(zone.min().getZ() + zone.max().getZ(), 2)
        );
    }

    private static void placeDoor(StationStructureSpawnTriggerEvent event) {
        CompoundTag data = event.getZone().data();
        if (data.contains("place") && !data.getBoolean("place")) {
            return;
        }

        RandomSource random = event.getLevel().getRandom();
        int chance = data.contains("chance") ? data.getInt("chance") : 80;
        if (random.nextInt(100) >= Mth.clamp(chance, 0, 100)) {
            return;
        }

        Direction facing = doorFacing(data, event.getStation().stationDirection());
        boolean broken = data.contains("broken") && data.getBoolean("broken");
        boolean open = !broken && random.nextInt(100) < Mth.clamp(data.contains("openChance") ? data.getInt("openChance") : 15, 0, 100);
        BlockPos masterPos = doorMasterPos(event.getZone());
        String doorId = doorId(event.getStation().seed(), masterPos);

        event.getLevel().setBlock(masterPos, ShipBlocks.STATION_PRESSURE_TIGHT_DOOR.get().defaultBlockState(), 3);
        if (!PressureTightDoorBlock.placeDoor(event.getLevel(), masterPos, facing, broken, open, doorId)) {
            event.getLevel().removeBlock(masterPos, false);
        }
    }

    private static int doorBrokenChance(CompoundTag data, float stationDanger) {
        if (data.contains("brokenChance") && data.getInt("brokenChance") != 25) {
            return Mth.clamp(data.getInt("brokenChance"), 0, 100);
        }
        return Mth.clamp(Math.round(5.0F + Mth.clamp(stationDanger, 0.0F, 1.0F) * 45.0F), 0, 100);
    }

    private static Direction doorFacing(CompoundTag data, Direction fallback) {
        String direction = data.getString("direction");
        Direction parsed = Direction.byName(direction.toLowerCase(Locale.ROOT));
        if (parsed != null && parsed.getAxis().isHorizontal()) {
            return parsed;
        }
        return fallback.getAxis().isHorizontal() ? fallback : Direction.NORTH;
    }

    private static BlockPos doorMasterPos(PlacedTriggerZone zone) {
        int x = Math.floorDiv(zone.min().getX() + zone.max().getX(), 2);
        int y = zone.min().getY();
        int z = Math.floorDiv(zone.min().getZ() + zone.max().getZ(), 2);
        return new BlockPos(x, y, z);
    }

    private static String doorId(long stationSeed, BlockPos pos) {
        return dev.sixik.stationarenear.ship.block.PressureTightDoorBlock.generateDoorId(stationSeed, pos);
    }

    private static boolean placeObject(StationStructureSpawnTriggerEvent event) {
        return placeObject(event, false, "");
    }

    private static boolean placeObject(StationStructureSpawnTriggerEvent event, boolean questInvocation, String requiredObject) {
        CompoundTag data = event.getZone().data();
        if (data.contains("place") && !data.getBoolean("place")) {
            return false;
        }

        RandomSource random = event.getLevel().getRandom();
        if (!questInvocation) {
            int chance = Mth.clamp((data.contains("placeChance") ? data.getInt("placeChance") : data.getInt("chance")) + event.getAdditionalPlaceObjectChance(), 0, 100);
            boolean ignoreChance = data.getBoolean("ignoreChancePlace");
            if (!ignoreChance && random.nextInt(100) >= chance) {
                return false;
            }
        }

        StationStructureLibraryData library = StationStructureLibraryData.get(event.getLevel());
        List<StationPieceDefinition> candidates;
        if (questInvocation) {
            candidates = questObjectCandidates(library, data, event.getZone().data().getFloat(TagsConstants.Keys.STATION_DANGER), requiredObject);
        } else {
            ResourceLocation poolId = StationStructureIds.pool(data.getString("pool"));
            Optional<StationPoolDefinition> poolOptional = library.pool(poolId);
            if (poolOptional.isEmpty()) {
                return false;
            }
            candidates = objectCandidates(library, poolOptional.get(), event.getZone().data().getFloat(TagsConstants.Keys.STATION_DANGER));
        }
        if (candidates.isEmpty()) {
            return false;
        }

        Optional<ObjectPlacement> placement = selectObjectPlacement(event, candidates, random, questInvocation);
        if (placement.isEmpty()) {
            return false;
        }

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(placement.get().rotation())
                .addProcessor(BlockIgnoreProcessor.AIR);
        boolean placed = placement.get().template().placeInWorld(event.getLevel(), placement.get().origin(), placement.get().origin(), settings, random, 2);
        return placed;
    }

    private static int placeObjectZone(StationStructureSpawnTriggerEvent event, boolean questInvocation, int forcedCount) {
        CompoundTag data = event.getZone().data();
        if (data.contains("place") && !data.getBoolean("place")) {
            return 0;
        }
        if (!questInvocation && isObjectZoneQuestOnly(data)) {
            return 0;
        }

        RandomSource random = event.getLevel().getRandom();
        if (!questInvocation) {
            int chance = Mth.clamp((data.contains("placeChance") ? data.getInt("placeChance") : data.contains("chance") ? data.getInt("chance") : 100) + event.getAdditionalPlaceObjectChance(), 0, 100);
            boolean ignoreChance = data.getBoolean("ignoreChancePlace");
            if (!ignoreChance && random.nextInt(100) >= chance) {
                return 0;
            }
        }

        StationStructureLibraryData library = StationStructureLibraryData.get(event.getLevel());
        List<StationPieceDefinition> candidates = objectZoneCandidates(library, data, data.getFloat(TagsConstants.Keys.STATION_DANGER));
        if (candidates.isEmpty()) {
            return 0;
        }

        int targetCount;
        if (forcedCount >= 0) {
            targetCount = Mth.clamp(forcedCount, 0, 256);
        } else {
            int minCount = Mth.clamp(readObjectZoneCount(data, "minCount", "min", 1), 0, 256);
            int maxFallback = data.contains("count") ? data.getInt("count") : Math.max(minCount, 8);
            int maxCount = Mth.clamp(readObjectZoneCount(data, "maxCount", "max", maxFallback), minCount, 256);
            targetCount = maxCount == minCount ? minCount : Mth.nextInt(random, minCount, maxCount);
        }

        int placed = 0;
        int attempts = Math.max(32, targetCount * 32);
        while (placed < targetCount && attempts-- > 0) {
            Optional<ObjectPlacement> placement = selectObjectZonePlacement(event, candidates, random);
            if (placement.isEmpty()) {
                continue;
            }
            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setRotation(placement.get().rotation())
                    .addProcessor(BlockIgnoreProcessor.AIR);
            placement.get().template().placeInWorld(event.getLevel(), placement.get().origin(), placement.get().origin(), settings, random, 2);
            placed++;
        }
        return placed;
    }

    private static Optional<ObjectPlacement> selectObjectPlacement(StationStructureSpawnTriggerEvent event, List<StationPieceDefinition> candidates, RandomSource random, boolean allowUpwardOverflow) {
        List<StationPieceDefinition> remaining = new java.util.ArrayList<>(candidates);
        while (!remaining.isEmpty()) {
            StationPieceDefinition definition = selectWeighted(remaining, random);
            remaining.remove(definition);
            Optional<StructureTemplate> template = StationStructureFileStorage.getOrLoadTemplate(event.getLevel(), definition.template());
            if (template.isEmpty()) {
                continue;
            }

            for (Rotation rotation : objectRotations(event.getZone().data(), template.get(), random)) {
                Optional<BlockPos> origin = randomOriginInside(event.getZone(), template.get(), rotation, random, allowUpwardOverflow);
                if (origin.isPresent()) {
                    BlockPos adjustedOrigin = adjustSurfaceObjectOrigin(event, template.get(), origin.get());
                    return Optional.of(new ObjectPlacement(template.get(), adjustedOrigin, rotation));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<ObjectPlacement> selectObjectZonePlacement(StationStructureSpawnTriggerEvent event, List<StationPieceDefinition> candidates, RandomSource random) {
        StationPieceDefinition definition = selectWeighted(candidates, random);
        Optional<StructureTemplate> template = StationStructureFileStorage.getOrLoadTemplate(event.getLevel(), definition.template());
        if (template.isEmpty()) {
            return Optional.empty();
        }

        for (Rotation rotation : objectRotations(event.getZone().data(), template.get(), random)) {
            Optional<BlockPos> origin = randomOriginInside(event.getZone(), template.get(), rotation, random);
            if (origin.isPresent() && canPlaceObjectZoneAt(event, template.get(), origin.get(), rotation)) {
                return Optional.of(new ObjectPlacement(template.get(), origin.get(), rotation));
            }
        }
        return Optional.empty();
    }

    private static List<Rotation> objectRotations(CompoundTag data, StructureTemplate template, RandomSource random) {
        if (useObjectDirection(data)) {
            return List.of(rotationBetween(objectBaseDirection(data, template), objectDirection(data)));
        }

        boolean randomRotation = data.getBoolean("randomRotation");
        if (!objectRotationEnabled(data)) {
            return List.of(randomRotation ? randomHorizontalRotation(random) : Rotation.NONE);
        }

        List<Rotation> rotations = new java.util.ArrayList<>(List.of(Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90));
        if (randomRotation) {
            for (int i = rotations.size() - 1; i > 0; i--) {
                java.util.Collections.swap(rotations, i, random.nextInt(i + 1));
            }
        }
        return rotations;
    }

    private static boolean useObjectDirection(CompoundTag data) {
        if (data.contains("useObjectDirection")) {
            return data.getBoolean("useObjectDirection");
        }
        if (data.contains("USE_OBJECT_DIRECTION")) {
            return data.getBoolean("USE_OBJECT_DIRECTION");
        }
        return data.contains("objectDirection") || data.contains("OBJECT_DIRECTION");
    }

    private static Direction objectDirection(CompoundTag data) {
        Direction direction = Direction.byName(data.getString("objectDirection").toLowerCase(Locale.ROOT));
        if (direction == null) {
            direction = Direction.byName(data.getString("OBJECT_DIRECTION").toLowerCase(Locale.ROOT));
        }
        return direction != null && direction.getAxis().isHorizontal() ? direction : Direction.NORTH;
    }

    private static Direction objectBaseDirection(CompoundTag data, StructureTemplate template) {
        Direction direction = objectTemplateDirection(data, template);
        return direction != null ? direction : Direction.NORTH;
    }

    private static Direction objectTemplateDirection(CompoundTag data, StructureTemplate template) {
        Direction configured = Direction.byName(data.getString("objectBaseDirection").toLowerCase(Locale.ROOT));
        if (configured != null && configured.getAxis().isHorizontal()) {
            return configured;
        }

        CompoundTag savedTemplate = template.save(new CompoundTag());
        ListTag palette = savedTemplate.contains("palette", Tag.TAG_LIST)
                ? savedTemplate.getList("palette", Tag.TAG_COMPOUND)
                : firstPalette(savedTemplate);
        return inferTemplateFacing(palette);
    }

    private static ListTag firstPalette(CompoundTag savedTemplate) {
        ListTag palettes = savedTemplate.getList("palettes", Tag.TAG_LIST);
        if (palettes.isEmpty()) {
            return new ListTag();
        }
        return palettes.getList(0);
    }

    private static Direction inferTemplateFacing(ListTag palette) {
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag state = palette.getCompound(i);
            if (!state.contains("Properties", Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag properties = state.getCompound("Properties");
            Direction direction = propertyDirection(properties, "facing");
            if (direction == null) {
                direction = propertyDirection(properties, "horizontal_facing");
            }
            if (direction != null) {
                return direction;
            }
        }
        return null;
    }

    private static Direction propertyDirection(CompoundTag properties, String key) {
        if (!properties.contains(key, Tag.TAG_STRING)) {
            return null;
        }
        Direction direction = Direction.byName(properties.getString(key).toLowerCase(Locale.ROOT));
        return direction != null && direction.getAxis().isHorizontal() ? direction : null;
    }

    private static Rotation rotationBetween(Direction from, Direction to) {
        Direction current = from;
        for (Rotation rotation : List.of(Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90)) {
            if (rotation.rotate(current) == to) {
                return rotation;
            }
        }
        return Rotation.NONE;
    }

    private static BlockPos adjustSurfaceObjectOrigin(StationStructureSpawnTriggerEvent event, StructureTemplate template, BlockPos origin) {
        CompoundTag data = event.getZone().data();
        if (!useObjectDirection(data) || !isSingleBlockTemplate(template) || objectTemplateDirection(data, template) == null) {
            return origin;
        }
        if (event.getLevel().getBlockState(origin).isAir()) {
            return origin;
        }

        Direction direction = objectDirection(data);
        BlockPos surfacePos = origin.relative(direction);
        if (event.getLevel().getBlockState(surfacePos).isAir()) {
            return surfacePos;
        }
        return origin;
    }

    private static boolean isSingleBlockTemplate(StructureTemplate template) {
        Vec3i size = template.getSize();
        return size.getX() == 1 && size.getY() == 1 && size.getZ() == 1;
    }

    private static boolean objectRotationEnabled(CompoundTag data) {
        if (data.contains("objectRotation")) {
            return data.getBoolean("objectRotation");
        }
        if (data.contains("OBJECT_ROTATION")) {
            return data.getBoolean("OBJECT_ROTATION");
        }
        return true;
    }

    private static List<StationPieceDefinition> objectCandidates(StationStructureLibraryData library, StationPoolDefinition pool, float danger) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        ids.addAll(pool.roomPieces());
        ids.addAll(pool.startPieces());

        List<StationPieceDefinition> all = new ObjectArrayList<>();
        List<StationPieceDefinition> withoutConnectors = new ObjectArrayList<>();
        for (ResourceLocation id : ids) {
            Optional<StationPieceDefinition> definition = library.piece(id).filter(piece -> piece.canSpawnAtDanger(danger));
            if (definition.isEmpty()) {
                continue;
            }
            all.add(definition.get());
            if (definition.get().connectors().isEmpty()) {
                withoutConnectors.add(definition.get());
            }
        }
        return withoutConnectors.isEmpty() ? all : withoutConnectors;
    }

    private static List<StationPieceDefinition> objectZoneCandidates(StationStructureLibraryData library, CompoundTag data, float danger) {
        Set<ResourceLocation> seen = new LinkedHashSet<>();
        List<StationPieceDefinition> candidates = new ObjectArrayList<>();
        for (ResourceLocation poolId : objectPoolIds(data)) {
            Optional<StationPoolDefinition> pool = library.pool(poolId);
            if (pool.isEmpty()) {
                continue;
            }
            for (StationPieceDefinition definition : objectCandidates(library, pool.get(), danger)) {
                if (seen.add(definition.id())) {
                    candidates.add(definition);
                }
            }
        }
        return candidates;
    }

    private static List<StationPieceDefinition> questObjectCandidates(StationStructureLibraryData library, CompoundTag data, float danger, String requiredObject) {
        List<StationPieceDefinition> candidates = objectZoneCandidates(library, data, danger);
        String normalizedObject = normalizeObjectId(requiredObject);
        if (normalizedObject.isBlank()) {
            return candidates;
        }
        List<StationPieceDefinition> matched = new ObjectArrayList<>();
        for (StationPieceDefinition definition : candidates) {
            if (objectMatchesRequired(definition, normalizedObject)) {
                matched.add(definition);
            }
        }
        if (matched.isEmpty() && objectDataMatchesRequired(data, normalizedObject)) {
            return candidates;
        }
        return matched;
    }

    private static boolean objectDataMatchesRequired(CompoundTag data, String requiredObject) {
        String shortName = shortObjectName(requiredObject);
        for (String key : List.of("object", "objectId", "piece", "template", "item", "requiredItem", TagsConstants.Keys.TAG, TagsConstants.Keys.TAGS)) {
            String value = data.getString(key);
            if (value == null || value.isBlank()) {
                continue;
            }
            for (String part : value.split("[,;]")) {
                String normalized = normalizeObjectId(part);
                if (normalized.equals(requiredObject) || normalized.equals(shortName) || shortObjectName(normalized).equals(shortName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean objectMatchesRequired(StationPieceDefinition definition, String requiredObject) {
        if (matchesResourceName(definition.id(), requiredObject) || matchesResourceName(definition.template(), requiredObject)) {
            return true;
        }
        String shortName = shortObjectName(requiredObject);
        for (String tag : definition.tags()) {
            String normalizedTag = normalizeObjectId(tag);
            if (normalizedTag.equals(requiredObject) || normalizedTag.equals(shortName) || shortObjectName(normalizedTag).equals(shortName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesResourceName(ResourceLocation id, String requiredObject) {
        if (id == null) {
            return false;
        }
        String full = normalizeObjectId(id.toString());
        String path = normalizeObjectId(id.getPath());
        String shortName = shortObjectName(requiredObject);
        return full.equals(requiredObject)
                || path.equals(requiredObject)
                || path.equals(shortName)
                || path.endsWith("/" + shortName);
    }

    private static String shortObjectName(String objectId) {
        String normalized = normalizeObjectId(objectId);
        int slash = normalized.lastIndexOf('/');
        String tail = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        int colon = tail.lastIndexOf(':');
        return colon >= 0 ? tail.substring(colon + 1) : tail;
    }

    private static String normalizeObjectId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static List<ResourceLocation> objectPoolIds(CompoundTag data) {
        Set<ResourceLocation> poolIds = new LinkedHashSet<>();
        if (data.contains("pools", Tag.TAG_LIST)) {
            ListTag pools = data.getList("pools", Tag.TAG_STRING);
            for (int i = 0; i < pools.size(); i++) {
                addObjectPoolId(poolIds, pools.getString(i));
            }
        } else {
            addObjectPoolIds(poolIds, data.getString("pools"));
        }
        addObjectPoolIds(poolIds, data.getString("pool"));
        if (poolIds.isEmpty()) {
            poolIds.add(StationStructureIds.pool("objects/default"));
        }
        return List.copyOf(poolIds);
    }

    private static void addObjectPoolIds(Set<ResourceLocation> poolIds, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String part : value.split("[,;]")) {
            addObjectPoolId(poolIds, part);
        }
    }

    private static void addObjectPoolId(Set<ResourceLocation> poolIds, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        poolIds.add(StationStructureIds.pool(value.trim()));
    }

    private static StationPieceDefinition selectWeighted(List<StationPieceDefinition> definitions, RandomSource random) {
        int totalWeight = 0;
        for (StationPieceDefinition definition : definitions) {
            totalWeight += Math.max(1, definition.weight());
        }
        int roll = random.nextInt(Math.max(1, totalWeight));
        for (StationPieceDefinition definition : definitions) {
            roll -= Math.max(1, definition.weight());
            if (roll < 0) {
                return definition;
            }
        }
        return definitions.get(0);
    }

    private static Optional<BlockPos> randomOriginInside(PlacedTriggerZone zone, StructureTemplate template, Rotation rotation, RandomSource random) {
        return randomOriginInside(zone, template, rotation, random, false);
    }

    private static Optional<BlockPos> randomOriginInside(PlacedTriggerZone zone, StructureTemplate template, Rotation rotation, RandomSource random, boolean allowUpwardOverflow) {
        BoundingBox localBounds = StationPlacementUtil.transformBounds(BlockPos.ZERO, template.getSize(), rotation);
        Vec3i size = new Vec3i(
                localBounds.maxX() - localBounds.minX() + 1,
                localBounds.maxY() - localBounds.minY() + 1,
                localBounds.maxZ() - localBounds.minZ() + 1
        );
        int availableX = zone.max().getX() - zone.min().getX() - size.getX() + 1;
        int availableY = zone.max().getY() - zone.min().getY() - size.getY() + 1;
        int availableZ = zone.max().getZ() - zone.min().getZ() - size.getZ() + 1;
        if (availableX < 0 || availableZ < 0 || (!allowUpwardOverflow && availableY < 0)) {
            return Optional.empty();
        }

        int targetY = zone.min().getY();
        if (!StationTriggerZoneShape.hasShape(zone.data())) {
            BlockPos targetMin = new BlockPos(
                    zone.min().getX() + Mth.nextInt(random, 0, availableX),
                    targetY,
                    zone.min().getZ() + Mth.nextInt(random, 0, availableZ)
            );
            return Optional.of(targetMin.offset(-localBounds.minX(), -localBounds.minY(), -localBounds.minZ()));
        }

        BlockPos selected = null;
        int matches = 0;
        for (int x = 0; x <= availableX; x++) {
            for (int z = 0; z <= availableZ; z++) {
                BlockPos targetMin = new BlockPos(zone.min().getX() + x, targetY, zone.min().getZ() + z);
                BlockPos targetMax = allowUpwardOverflow
                        ? targetMin.offset(size.getX() - 1, 0, size.getZ() - 1)
                        : targetMin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
                if (StationTriggerZoneShape.containsBox(zone.data(), zone.min(), zone.max(), targetMin, targetMax) && random.nextInt(++matches) == 0) {
                    selected = targetMin.offset(-localBounds.minX(), -localBounds.minY(), -localBounds.minZ());
                }
            }
        }
        return Optional.ofNullable(selected);
    }


    private static List<BlockPos> objectZoneBlockCandidates(ServerLevel level, PlacedTriggerZone zone) {
        List<BlockPos> candidates = new ObjectArrayList<>();
        int y = zone.min().getY();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = zone.min().getX(); x <= zone.max().getX(); x++) {
            for (int z = zone.min().getZ(); z <= zone.max().getZ(); z++) {
                BlockPos pos = cursor.set(x, y, z).immutable();
                if (!StationTriggerZoneShape.contains(zone.data(), zone.min(), zone.max(), pos)) {
                    continue;
                }
                if (!level.getBlockState(pos).isAir()) {
                    continue;
                }
                if (level.getBlockState(pos.below()).isAir()) {
                    continue;
                }
                candidates.add(pos);
            }
        }
        return candidates;
    }

    private static boolean isObjectZoneQuestOnly(CompoundTag data) {
        return data.getBoolean(TagsConstants.Keys.ONLY_QUESTS) || data.getBoolean(TagsConstants.Keys.ONLY_QUEST) || data.getBoolean(TagsConstants.Keys.QUEST_ONLY);
    }

    private static int readObjectZoneCount(CompoundTag data, String primaryKey, String fallbackKey, int fallback) {
        if (data.contains(primaryKey)) {
            return data.getInt(primaryKey);
        }
        if (data.contains(fallbackKey)) {
            return data.getInt(fallbackKey);
        }
        return fallback;
    }

    private static boolean canPlaceObjectZoneAt(StationStructureSpawnTriggerEvent event, StructureTemplate template, BlockPos origin, Rotation rotation) {
        return hasFloorBelowBottomCenter(event, template, origin, rotation) && isObjectZoneBoundsClear(event, template, origin, rotation);
    }

    private static boolean hasFloorBelowBottomCenter(StationStructureSpawnTriggerEvent event, StructureTemplate template, BlockPos origin, Rotation rotation) {
        BoundingBox bounds = StationPlacementUtil.transformBounds(origin, template.getSize(), rotation);
        BlockPos floorPos = new BlockPos(
                Math.floorDiv(bounds.minX() + bounds.maxX(), 2),
                bounds.minY() - 1,
                Math.floorDiv(bounds.minZ() + bounds.maxZ(), 2)
        );
        return !event.getLevel().getBlockState(floorPos).isAir();
    }

    private static boolean isObjectZoneBoundsClear(StationStructureSpawnTriggerEvent event, StructureTemplate template, BlockPos origin, Rotation rotation) {
        BoundingBox bounds = StationPlacementUtil.transformBounds(origin, template.getSize(), rotation);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    if (!event.getLevel().getBlockState(cursor.set(x, y, z)).isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private record ObjectPlacement(StructureTemplate template, BlockPos origin, Rotation rotation) {
    }

    private static Rotation randomHorizontalRotation(RandomSource random) {
        return switch (random.nextInt(4)) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private record EnergyPanelTarget(PlacedStationPiece piece, PlacedTriggerZone zone) {

        private boolean matches(PlacedStationPiece piece, PlacedTriggerZone zone) {
            return this.piece.equals(piece) && this.zone.equals(zone);
        }
    }
}
