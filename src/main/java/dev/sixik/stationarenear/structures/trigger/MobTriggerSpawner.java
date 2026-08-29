package dev.sixik.stationarenear.structures.trigger;

import dev.sixik.stationarenear.mob.registry.StationMobEntities;
import dev.sixik.stationarenear.structures.data.PlacedTriggerZone;
import dev.sixik.stationarenear.structures.util.TagsConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.Optional;

public final class MobTriggerSpawner {

    private MobTriggerSpawner() {
    }

    public static void spawnFromStructureTrigger(StationStructureSpawnTriggerEvent event) {
        if (event == null || !isMobTrigger(event.getZone())) {
            return;
        }
        CompoundTag data = event.getZone().data();
        if (data.contains("place") && !data.getBoolean("place")) {
            return;
        }
        if (!hasForcedSpawn(event)) {
            return;
        }
        spawnConfigured(event.getLevel(), event.getZone(), event.getForcedMob(), event.getForcedMobCount());
    }

    public static void spawnOnActivation(StationTriggerEvent event, boolean directorControlled) {
        if (event == null || directorControlled || !event.isFirstGlobalActivation() || !isMobTrigger(event.getZone())) {
            return;
        }
        CompoundTag data = event.getZone().data();
        if (data.contains("place") && !data.getBoolean("place")) {
            return;
        }
        spawnConfigured(event.getLevel(), event.getZone(), null, -1);
    }

    private static boolean hasForcedSpawn(StationStructureSpawnTriggerEvent event) {
        return event.getForcedMobCount() >= 0 || event.getForcedMob() != null && !event.getForcedMob().isBlank();
    }

    private static boolean isMobTrigger(PlacedTriggerZone zone) {
        return zone != null && (TagsConstants.Trigger.MOB_SPAWN.equals(zone.type()) || TagsConstants.Trigger.DANGER_MOB_SPAWN.equals(zone.type()));
    }

    private static void spawnConfigured(ServerLevel level, PlacedTriggerZone zone, String forcedMob, int forcedCount) {
        CompoundTag data = zone.data();
        float danger = Mth.clamp(data.getFloat(TagsConstants.Keys.STATION_DANGER), 0.0F, 1.0F);
        int defaultCount = 1 + Mth.ceil(danger * 4.0F);
        int count = forcedCount >= 0
                ? forcedCount
                : data.contains("count") && data.getInt("count") > 0 ? data.getInt("count") : defaultCount;
        if (count <= 0) {
            return;
        }
        String mobId = forcedMob != null && !forcedMob.isBlank()
                ? forcedMob
                : data.getString("mob");
        RandomSource random = level.getRandom();
        for (int i = 0; i < count; i++) {
            spawnDangerMob(level, zone, danger, random, mobId);
        }
    }

    private static void spawnDangerMob(ServerLevel level, PlacedTriggerZone zone, float danger, RandomSource random, String mobId) {
        EntityType<?> entityType = mobType(mobId);
        Entity entity = entityType.create(level);
        if (!(entity instanceof Mob mob)) {
            return;
        }

        BlockPos spawnPos = mobSpawnPos(level, zone, random).orElseGet(() -> randomPosInside(zone, random));
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

    private static Optional<BlockPos> mobSpawnPos(ServerLevel level, PlacedTriggerZone zone, RandomSource random) {
        for (int attempt = 0; attempt < 24; attempt++) {
            Optional<BlockPos> candidate = validMobSpawnPos(level, randomPosInside(zone, random));
            if (candidate.isPresent()) {
                return candidate;
            }
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = zone.min().getY(); y <= zone.max().getY(); y++) {
            for (int x = zone.min().getX(); x <= zone.max().getX(); x++) {
                for (int z = zone.min().getZ(); z <= zone.max().getZ(); z++) {
                    Optional<BlockPos> candidate = validMobSpawnPos(level, cursor.set(x, y, z).immutable());
                    if (candidate.isPresent()) {
                        return candidate;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> validMobSpawnPos(ServerLevel level, BlockPos pos) {
        if (canMobStandAt(level, pos)) {
            return Optional.of(pos);
        }
        BlockPos above = pos.above();
        if (canMobStandAt(level, above)) {
            return Optional.of(above);
        }
        return Optional.empty();
    }

    private static boolean canMobStandAt(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).isAir()
                && !level.getBlockState(pos.below()).isAir();
    }

    private static EntityType<?> mobType(String mobId) {
        if (mobId != null && !mobId.isBlank()) {
            ResourceLocation id = ResourceLocation.tryParse(mobId);
            if (id != null && net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
                EntityType<?> entityType = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(id);
                if (entityType != null) {
                    return entityType;
                }
            }
        }
        return StationMobEntities.LIVING_TRASH.get();
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
