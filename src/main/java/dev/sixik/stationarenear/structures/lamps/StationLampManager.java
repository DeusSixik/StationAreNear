package dev.sixik.stationarenear.structures.lamps;

import dev.sixik.stationarenear.quest.config.director.StationOfferType;
import dev.sixik.stationarenear.quest.registry.StationQuests;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class StationLampManager {

    private static final int DEFAULT_BROKEN_CHANCE = 30;

    private StationLampManager() {
    }

    public static void onStationGenerated(ServerLevel level, StationInstance station) {
        StationLampSavedData data = StationLampSavedData.get(level);
        StationLampState state = data.getOrCreate(station.id());

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

        boolean hasQuest = hasElectricQuest(level, station);
        state.setHasElectricQuest(hasQuest);
        if (hasQuest) {
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
        Optional<StationInstance> stationOpt = stationAt(level, panelPos);
        if (stationOpt.isEmpty()) {
            return;
        }
        StationInstance station = stationOpt.get();
        StationLampSavedData data = StationLampSavedData.get(level);
        Optional<StationLampState> stateOpt = data.getStation(station.id());
        if (stateOpt.isEmpty()) {
            return;
        }
        StationLampState state = stateOpt.get();
        if (!state.hasElectricQuest()) {
            return;
        }
        state.setPowerOn(powered);
        if (powered) {
            restoreOriginalLamps(level, state);
        } else {
            applyEmergencyLamps(level, state);
        }
        data.setDirty();
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
        if (target.hasProperty(HorizontalDirectionalBlock.FACING) && source.hasProperty(HorizontalDirectionalBlock.FACING)) {
            Direction facing = source.getValue(HorizontalDirectionalBlock.FACING);
            if (facing.getAxis().isHorizontal()) {
                return target.setValue(HorizontalDirectionalBlock.FACING, facing);
            }
        }
        if (target.hasProperty(DirectionalBlock.FACING)) {
            if (source.hasProperty(DirectionalBlock.FACING)) {
                return target.setValue(DirectionalBlock.FACING, source.getValue(DirectionalBlock.FACING));
            } else if (source.hasProperty(HorizontalDirectionalBlock.FACING)) {
                return target.setValue(DirectionalBlock.FACING, source.getValue(HorizontalDirectionalBlock.FACING));
            }
        }
        return target;
    }

    private static boolean isWallLamp(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().toLowerCase(Locale.ROOT);
        if (path.contains("wall_lamp")) {
            return true;
        }
        return state.hasProperty(HorizontalDirectionalBlock.FACING) && !state.hasProperty(DirectionalBlock.FACING);
    }

    private static boolean hasElectricQuest(ServerLevel level, StationInstance station) {
        if (QuestSavedData.get(level).stationIfPresent(station.id())
                .flatMap(s -> s.objective(StationQuests.REPAIR_ELECTRIC_PANEL)).isPresent()) {
            return true;
        }

        CompoundTag customData = station.customData();
        if (customData.contains("stationOffers", Tag.TAG_LIST)) {
            ListTag offers = customData.getList("stationOffers", Tag.TAG_COMPOUND);
            for (int i = 0; i < offers.size(); i++) {
                CompoundTag offer = offers.getCompound(i);
                if (StationOfferType.from(offer.getString("type")) == StationOfferType.ENERGY_FAILURE) {
                    return true;
                }
            }
        }

        for (PlacedStationPiece piece : station.pieces()) {
            for (PlacedTriggerZone zone : piece.triggerZones()) {
                if (zone.data().contains("broken") && zone.data().getBoolean("broken")) {
                    return true;
                }
                if (zone.data().contains("energyPanel") && zone.data().getBoolean("energyPanel")) {
                    return true;
                }
            }
        }

        return false;
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
}