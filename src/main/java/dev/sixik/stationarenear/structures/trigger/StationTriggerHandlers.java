package dev.sixik.stationarenear.structures.trigger;

import dev.sixik.stationarenear.structures.data.PlacedTriggerZone;
import dev.sixik.stationarenear.structures.data.StationPieceDefinition;
import dev.sixik.stationarenear.structures.data.StationPoolDefinition;
import dev.sixik.stationarenear.structures.generation.StationPlacementUtil;
import dev.sixik.stationarenear.structures.util.StationStructureIds;
import dev.sixik.stationarenear.structures.world.StationStructureLibraryData;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class StationTriggerHandlers {

    private StationTriggerHandlers() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(StationTriggerHandlers::onStationTrigger);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, StationTriggerHandlers::onStructureSpawnTrigger);
    }

    private static void onStationTrigger(StationTriggerEvent event) {
        if (!event.isFirstGlobalActivation()) {
            return;
        }
        if (!event.getZone().type().equals("mob_spawn") && !event.getZone().type().equals("danger_mob_spawn")) {
            return;
        }

        float danger = Mth.clamp(event.getZone().data().getFloat("stationDanger"), 0.0F, 1.0F);
        int count = 1 + Mth.ceil(danger * 4.0F);
        RandomSource random = event.getLevel().getRandom();
        for (int i = 0; i < count; i++) {
            spawnDangerMob(event.getLevel(), event.getZone(), danger, random, null);
        }
    }

    private static void onStructureSpawnTrigger(StationStructureSpawnTriggerEvent event) {
        if (event.isPlacementCanceled()) {
            return;
        }
        switch (event.getTriggerType()) {
            case OBJECT_PLACER -> placeObject(event);
            case MOB_SPAWN -> placeMobs(event);
            case QUEST -> {
                // Quest triggers intentionally only publish StationStructureSpawnTriggerEvent for quest code.
            }
            default -> {
            }
        }
    }

    private static void placeObject(StationStructureSpawnTriggerEvent event) {
        CompoundTag data = event.getZone().data();
        if (data.contains("place") && !data.getBoolean("place")) {
            return;
        }

        int chance = Mth.clamp((data.contains("placeChance") ? data.getInt("placeChance") : data.getInt("chance")) + event.getAdditionalPlaceObjectChance(), 0, 100);
        boolean ignoreChance = data.getBoolean("ignoreChancePlace");
        RandomSource random = event.getLevel().getRandom();
        if (!ignoreChance && random.nextInt(100) >= chance) {
            return;
        }

        ResourceLocation poolId = StationStructureIds.pool(data.getString("pool"));
        StationStructureLibraryData library = StationStructureLibraryData.get(event.getLevel());
        Optional<StationPoolDefinition> poolOptional = library.pool(poolId);
        if (poolOptional.isEmpty()) {
            return;
        }

        List<StationPieceDefinition> candidates = objectCandidates(library, poolOptional.get(), event.getZone().data().getFloat("stationDanger"));
        if (candidates.isEmpty()) {
            return;
        }

        StationPieceDefinition definition = selectWeighted(candidates, random);
        Optional<StructureTemplate> template = event.getLevel().getStructureManager().get(definition.template());
        if (template.isEmpty()) {
            return;
        }

        Rotation rotation = data.getBoolean("randomRotation") ? randomHorizontalRotation(random) : Rotation.NONE;
        Optional<BlockPos> origin = randomOriginInside(event.getZone(), template.get(), rotation, random);
        if (origin.isEmpty()) {
            return;
        }

        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
        template.get().placeInWorld(event.getLevel(), origin.get(), origin.get(), settings, random, 2);
    }

    private static void placeMobs(StationStructureSpawnTriggerEvent event) {
        CompoundTag data = event.getZone().data();
        if (data.contains("place") && !data.getBoolean("place")) {
            return;
        }

        float danger = Mth.clamp(data.getFloat("stationDanger"), 0.0F, 1.0F);
        int count = event.getForcedMobCount() >= 0
                ? event.getForcedMobCount()
                : Math.max(0, data.contains("count") ? data.getInt("count") : 1 + Mth.ceil(danger * 4.0F));
        String mobId = event.getForcedMob() != null && !event.getForcedMob().isBlank()
                ? event.getForcedMob()
                : data.getString("mob");
        RandomSource random = event.getLevel().getRandom();
        for (int i = 0; i < count; i++) {
            spawnDangerMob(event.getLevel(), event.getZone(), danger, random, mobId);
        }
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
        BoundingBox localBounds = StationPlacementUtil.transformBounds(BlockPos.ZERO, template.getSize(), rotation);
        Vec3i size = new Vec3i(
                localBounds.maxX() - localBounds.minX() + 1,
                localBounds.maxY() - localBounds.minY() + 1,
                localBounds.maxZ() - localBounds.minZ() + 1
        );
        int availableX = zone.max().getX() - zone.min().getX() - size.getX() + 1;
        int availableY = zone.max().getY() - zone.min().getY() - size.getY() + 1;
        int availableZ = zone.max().getZ() - zone.min().getZ() - size.getZ() + 1;
        if (availableX < 0 || availableY < 0 || availableZ < 0) {
            return Optional.empty();
        }

        BlockPos targetMin = new BlockPos(
                zone.min().getX() + Mth.nextInt(random, 0, availableX),
                zone.min().getY() + Mth.nextInt(random, 0, availableY),
                zone.min().getZ() + Mth.nextInt(random, 0, availableZ)
        );
        return Optional.of(targetMin.offset(-localBounds.minX(), -localBounds.minY(), -localBounds.minZ()));
    }

    private static Rotation randomHorizontalRotation(RandomSource random) {
        return switch (random.nextInt(4)) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static void spawnDangerMob(net.minecraft.server.level.ServerLevel level, PlacedTriggerZone zone, float danger, RandomSource random, String mobId) {
        EntityType<?> entityType = mobType(mobId, danger);
        Entity entity = entityType.create(level);
        if (!(entity instanceof Mob mob)) {
            return;
        }

        BlockPos spawnPos = randomPosInside(zone, random);
        mob.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        mob.finalizeSpawn(
                (ServerLevelAccessor) level,
                level.getCurrentDifficultyAt(spawnPos),
                MobSpawnType.STRUCTURE,
                null,
                null
        );
        scaleDanger(mob, danger);
        level.addFreshEntity(mob);
    }

    private static EntityType<?> mobType(String mobId, float danger) {
        if (mobId != null && !mobId.isBlank()) {
            ResourceLocation id = ResourceLocation.tryParse(mobId);
            if (id != null && net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
                EntityType<?> entityType = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(id);
                if (entityType != null) {
                    return entityType;
                }
            }
        }
        return danger >= 0.65F ? EntityType.SKELETON : EntityType.ZOMBIE;
    }

    private static BlockPos randomPosInside(PlacedTriggerZone zone, RandomSource random) {
        int x = Mth.nextInt(random, zone.min().getX(), zone.max().getX());
        int y = Mth.nextInt(random, zone.min().getY(), zone.max().getY());
        int z = Mth.nextInt(random, zone.min().getZ(), zone.max().getZ());
        return new BlockPos(x, y, z);
    }

    private static void scaleDanger(Mob mob, float danger) {
        double multiplier = 1.0D + danger;
        if (mob.getAttribute(Attributes.MAX_HEALTH) != null) {
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(mob.getAttribute(Attributes.MAX_HEALTH).getBaseValue() * multiplier);
            mob.setHealth(mob.getMaxHealth());
        }
        if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(mob.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue() * multiplier);
        }
    }
}
