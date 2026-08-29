package dev.sixik.stationarenear.structures.lamps;

import dev.sixik.stationarenear.quest.config.director.StationOfferType;
import dev.sixik.stationarenear.quest.registry.StationQuests;
import dev.sixik.stationarenear.quest.registry.StationSounds;
import dev.sixik.stationarenear.quest.world.QuestSavedData;
import dev.sixik.stationarenear.structures.data.PlacedStationPiece;
import dev.sixik.stationarenear.structures.data.PlacedTriggerZone;
import dev.sixik.stationarenear.structures.data.StationInstance;
import dev.sixik.stationarenear.structures.trigger.StationStructureTriggerType;
import dev.sixik.stationarenear.structures.world.StationSavedData;
import net.mcreator.stationblocks.init.StationBlocksModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class StationLampManager {

    private static final int DEFAULT_BROKEN_CHANCE = 30;
    private static final List<PendingLampUpdate> PENDING_UPDATES = new ArrayList<>();

    private StationLampManager() {
    }

    public static void onStationGenerated(ServerLevel level, StationInstance station) {
        StationLampSavedData data = StationLampSavedData.get(level);
        StationLampState state = data.getOrCreate(station.id());
        scanLampsForStation(level, station, state);

        boolean hasOffer = hasEnergyFailureOffer(station) || hasElectricQuest(level, station);
        if (!hasOffer) {
            for (PlacedStationPiece piece : station.pieces()) {
                for (PlacedTriggerZone zone : piece.triggerZones()) {
                    BlockPos center = centerPos(zone);
                    BlockState bs = level.getBlockState(center);
                    if (bs.is(dev.sixik.stationarenear.quest.registry.QuestBlocks.ENERGY_PANEL.get()) && bs.getValue(dev.sixik.stationarenear.quest.block.EnergyPanelBlock.BROKEN)) {
                        hasOffer = true;
                        break;
                    }
                }
                if (hasOffer) {
                    break;
                }
            }
        }
        state.setHasElectricQuest(hasOffer);
        if (hasOffer) {
            state.setPowerOn(false);
            applyEmergencyLamps(level, state);
        } else {
            state.setPowerOn(true);
        }
        data.setDirty();
    }

    public static void onQuestStarted(ServerLevel level, StationInstance station) {
        StationLampSavedData data = StationLampSavedData.get(level);
        StationLampState state = data.getOrCreate(station.id());
        if (!state.hasElectricQuest() && hasElectricQuest(level, station)) {
            state.setHasElectricQuest(true);
            state.setPowerOn(false);
            applyEmergencyLamps(level, state);
            data.setDirty();
        }
    }

    public static void onPanelToggled(ServerLevel level, BlockPos panelPos, boolean powered) {
        Optional<StationInstance> stationOpt = stationAt(level, panelPos).or(() -> findNearbyStation(level, panelPos));
        StationLampSavedData data = StationLampSavedData.get(level);
        StationLampState state;
        if (stationOpt.isPresent()) {
            StationInstance station = stationOpt.get();
            state = data.getOrCreate(station.id());
            if (state.originalLamps().isEmpty()) {
                scanLampsForStation(level, station, state);
            }
        } else {
            state = data.getOrCreate(UUID.nameUUIDFromBytes(("panel_" + panelPos.asLong()).getBytes()));
            if (state.originalLamps().isEmpty()) {
                scanNearbyLamps(level, panelPos, state);
            }
        }

        state.setPowerOn(powered);

        PENDING_UPDATES.removeIf(update -> update.dimension().equals(level.dimension()) && (state.originalLamps().containsKey(update.pos()) || state.emergencyLamps().containsKey(update.pos())));

        Map<BlockPos, BlockState> targetLamps = powered ? state.originalLamps() : state.emergencyLamps();
        List<Map.Entry<BlockPos, BlockState>> sortedLamps = new ArrayList<>(targetLamps.entrySet());
        sortedLamps.sort(Comparator.comparingDouble(entry -> entry.getKey().distSqr(panelPos)));

        long baseTime = level.getGameTime();
        for (int i = 0; i < sortedLamps.size(); i++) {
            Map.Entry<BlockPos, BlockState> entry = sortedLamps.get(i);
            long delay = Math.min(30L, (i / 2) * 2L);
            PENDING_UPDATES.add(new PendingLampUpdate(level.dimension(), entry.getKey(), entry.getValue(), powered, baseTime + delay));
        }

        data.setDirty();
    }

    private static void scanLampsForStation(ServerLevel level, StationInstance station, StationLampState state) {
        for (PlacedStationPiece piece : station.pieces()) {
            for (PlacedTriggerZone zone : piece.triggerZones()) {
                if (StationStructureTriggerType.from(zone.type()) == StationStructureTriggerType.LAMP_SWITCH) {
                    BlockPos pos = centerPos(zone);
                    BlockState original = level.getBlockState(pos);
                    long seed = station.seed() ^ pos.asLong();
                    RandomSource random = RandomSource.create(seed);

                    int brokenChance = zone.data().contains("brokenChance")
                            ? Mth.clamp(zone.data().getInt("brokenChance"), 0, 100)
                            : DEFAULT_BROKEN_CHANCE;

                    boolean isBroken = random.nextInt(100) < brokenChance;
                    if (isBroken) {
                        BlockState brokenState = createBrokenLampState(original);
                        level.setBlock(pos, brokenState, 3);
                    } else {
                        BlockState emergency = createEmergencyLampState(original, seed ^ 0x9E3779B97F4A7C15L);
                        state.originalLamps().put(pos, original);
                        state.emergencyLamps().put(pos, emergency);
                    }
                }
            }
        }
    }

    private static Optional<StationInstance> findNearbyStation(ServerLevel level, BlockPos pos) {
        for (StationInstance station : StationSavedData.get(level).stations()) {
            for (PlacedStationPiece piece : station.pieces()) {
                if (piece.bounds().inflatedBy(32).isInside(pos)) {
                    return Optional.of(station);
                }
            }
        }
        return Optional.empty();
    }

    private static void scanNearbyLamps(ServerLevel level, BlockPos panelPos, StationLampState state) {
        for (StationInstance station : StationSavedData.get(level).stations()) {
            for (PlacedStationPiece piece : station.pieces()) {
                for (PlacedTriggerZone zone : piece.triggerZones()) {
                    if (StationStructureTriggerType.from(zone.type()) == StationStructureTriggerType.LAMP_SWITCH) {
                        BlockPos pos = centerPos(zone);
                        if (pos.closerThan(panelPos, 64)) {
                            BlockState current = level.getBlockState(pos);
                            long seed = station.seed() ^ pos.asLong();
                            BlockState emergency = createEmergencyLampState(current, seed ^ 0x9E3779B97F4A7C15L);
                            state.originalLamps().put(pos, current);
                            state.emergencyLamps().put(pos, emergency);
                        }
                    }
                }
            }
        }
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING_UPDATES.isEmpty()) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            PENDING_UPDATES.clear();
            return;
        }

        Iterator<PendingLampUpdate> iterator = PENDING_UPDATES.iterator();
        while (iterator.hasNext()) {
            PendingLampUpdate update = iterator.next();
            ServerLevel level = server.getLevel(update.dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }
            if (level.getGameTime() >= update.executeTick()) {
                level.setBlock(update.pos(), update.state(), 3);
                float pitch = 0.9F + level.random.nextFloat() * 0.2F;
                if (update.turnOn()) {
                    level.playSound(null, update.pos(), StationSounds.LIGHT_TURN_ON.get(), SoundSource.BLOCKS, 0.4F, pitch);
                } else {
                    level.playSound(null, update.pos(), StationSounds.LIGHT_TURN_OFF.get(), SoundSource.BLOCKS, 0.4F, pitch);
                }
                iterator.remove();
            }
        }
    }

    public static void applyEmergencyLamps(ServerLevel level, StationLampState state) {
        for (Map.Entry<BlockPos, BlockState> entry : state.emergencyLamps().entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
    }

    public static void restoreOriginalLamps(ServerLevel level, StationLampState state) {
        for (Map.Entry<BlockPos, BlockState> entry : state.originalLamps().entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
    }

    private static BlockState createBrokenLampState(BlockState originalState) {
        boolean isWall = isWallLamp(originalState);
        BlockState target = isWall
                ? StationBlocksModBlocks.WALL_LAMP_BROKEN.get().defaultBlockState()
                : StationBlocksModBlocks.LAMP_BROKEN.get().defaultBlockState();
        return copyFacing(target, originalState);
    }

    private static BlockState createEmergencyLampState(BlockState originalState, long seed) {
        RandomSource random = RandomSource.create(seed);
        boolean isWall = isWallLamp(originalState);
        boolean isRed = random.nextBoolean();

        BlockState target;
        if (isWall) {
            target = isRed
                    ? StationBlocksModBlocks.WALL_LAMP_RED.get().defaultBlockState()
                    : StationBlocksModBlocks.WALL_LAMP_DISABLED.get().defaultBlockState();
        } else {
            target = isRed
                    ? StationBlocksModBlocks.LAMP_RED.get().defaultBlockState()
                    : StationBlocksModBlocks.LAMP_DISABLED.get().defaultBlockState();
        }
        return copyFacing(target, originalState);
    }

    private static BlockState copyFacing(BlockState target, BlockState source) {
        if (source.hasProperty(HorizontalDirectionalBlock.FACING) && target.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return target.setValue(HorizontalDirectionalBlock.FACING, source.getValue(HorizontalDirectionalBlock.FACING));
        }
        if (source.hasProperty(DirectionalBlock.FACING) && target.hasProperty(DirectionalBlock.FACING)) {
            return target.setValue(DirectionalBlock.FACING, source.getValue(DirectionalBlock.FACING));
        }
        if (source.hasProperty(HorizontalDirectionalBlock.FACING) && target.hasProperty(DirectionalBlock.FACING)) {
            Direction dir = source.getValue(HorizontalDirectionalBlock.FACING);
            return target.setValue(DirectionalBlock.FACING, dir);
        }
        if (source.hasProperty(DirectionalBlock.FACING) && target.hasProperty(HorizontalDirectionalBlock.FACING)) {
            Direction dir = source.getValue(DirectionalBlock.FACING);
            if (dir.getAxis().isHorizontal()) {
                return target.setValue(HorizontalDirectionalBlock.FACING, dir);
            }
        }
        return target;
    }

    private static boolean isWallLamp(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.startsWith("wall_lamp");
    }

    public static boolean hasEnergyFailureOffer(StationInstance station) {
        CompoundTag customData = station.customData();
        if (customData.contains(dev.sixik.stationarenear.quest.config.director.DirectorConfigManager.DIRECTOR_PLAN_KEY, Tag.TAG_COMPOUND)) {
            CompoundTag plan = customData.getCompound(dev.sixik.stationarenear.quest.config.director.DirectorConfigManager.DIRECTOR_PLAN_KEY);
            ListTag offers = plan.getList("stationOffers", Tag.TAG_COMPOUND);
            for (int i = 0; i < offers.size(); i++) {
                CompoundTag offer = offers.getCompound(i);
                if (StationOfferType.from(offer.getString("type")) == StationOfferType.ENERGY_FAILURE) {
                    return true;
                }
            }
        }
        if (customData.contains("stationOffers", Tag.TAG_LIST)) {
            ListTag offers = customData.getList("stationOffers", Tag.TAG_COMPOUND);
            for (int i = 0; i < offers.size(); i++) {
                CompoundTag offer = offers.getCompound(i);
                if (StationOfferType.from(offer.getString("type")) == StationOfferType.ENERGY_FAILURE) {
                    return true;
                }
            }
        }
        return customData.contains("energyFailure") && customData.getBoolean("energyFailure");
    }

    private static boolean hasElectricQuest(ServerLevel level, StationInstance station) {
        return QuestSavedData.get(level).stationIfPresent(station.id())
                .map(questState -> questState.objectives().stream().anyMatch(obj ->
                        obj.id().equals(StationQuests.REPAIR_ELECTRIC_PANEL)
                                || obj.id().toLowerCase(Locale.ROOT).contains("electric")
                                || obj.id().toLowerCase(Locale.ROOT).contains("energy")
                ))
                .orElse(false);
    }

    private static Optional<StationInstance> stationAt(ServerLevel level, BlockPos pos) {
        for (StationInstance station : StationSavedData.get(level).stations()) {
            for (PlacedStationPiece piece : station.pieces()) {
                if (piece.bounds().isInside(pos)) {
                    return Optional.of(station);
                }
            }
        }
        return Optional.empty();
    }

    private static BlockPos centerPos(PlacedTriggerZone zone) {
        return new BlockPos(
                (zone.min().getX() + zone.max().getX()) / 2,
                (zone.min().getY() + zone.max().getY()) / 2,
                (zone.min().getZ() + zone.max().getZ()) / 2
        );
    }

    public static boolean isPowerOn(ServerLevel level, BlockPos pos) {
        Optional<StationInstance> stationOpt = stationAt(level, pos);
        if (stationOpt.isEmpty()) {
            return true;
        }
        StationLampSavedData data = StationLampSavedData.get(level);
        return data.getStation(stationOpt.get().id())
                .map(StationLampState::isPowerOn)
                .orElse(true);
    }

    private record PendingLampUpdate(ResourceKey<Level> dimension, BlockPos pos, BlockState state, boolean turnOn, long executeTick) {
    }
}