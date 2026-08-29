package dev.sixik.stationarenear.structures.lamps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class StationLampState {

    private final UUID stationId;
    private boolean hasElectricQuest;
    private boolean powerOn = true;
    private final Map<BlockPos, BlockState> originalLamps = new LinkedHashMap<>();
    private final Map<BlockPos, BlockState> emergencyLamps = new LinkedHashMap<>();

    public StationLampState(UUID stationId) {
        this.stationId = stationId;
    }

    public UUID stationId() {
        return stationId;
    }

    public boolean hasElectricQuest() {
        return hasElectricQuest;
    }

    public void setHasElectricQuest(boolean hasElectricQuest) {
        this.hasElectricQuest = hasElectricQuest;
    }

    public boolean isPowerOn() {
        return powerOn;
    }

    public void setPowerOn(boolean powerOn) {
        this.powerOn = powerOn;
    }

    public Map<BlockPos, BlockState> originalLamps() {
        return originalLamps;
    }

    public Map<BlockPos, BlockState> emergencyLamps() {
        return emergencyLamps;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("stationId", stationId);
        tag.putBoolean("hasElectricQuest", hasElectricQuest);
        tag.putBoolean("powerOn", powerOn);

        ListTag origList = new ListTag();
        for (Map.Entry<BlockPos, BlockState> entry : originalLamps.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLong("pos", entry.getKey().asLong());
            entryTag.put("state", NbtUtils.writeBlockState(entry.getValue()));
            origList.add(entryTag);
        }
        tag.put("originalLamps", origList);

        ListTag emergList = new ListTag();
        for (Map.Entry<BlockPos, BlockState> entry : emergencyLamps.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLong("pos", entry.getKey().asLong());
            entryTag.put("state", NbtUtils.writeBlockState(entry.getValue()));
            emergList.add(entryTag);
        }
        tag.put("emergencyLamps", emergList);

        return tag;
    }

    public static StationLampState load(CompoundTag tag) {
        UUID id = tag.hasUUID("stationId") ? tag.getUUID("stationId") : UUID.randomUUID();
        StationLampState state = new StationLampState(id);
        state.hasElectricQuest = tag.getBoolean("hasElectricQuest");
        state.powerOn = !tag.contains("powerOn") || tag.getBoolean("powerOn");

        ListTag origList = tag.getList("originalLamps", Tag.TAG_COMPOUND);
        for (int i = 0; i < origList.size(); i++) {
            CompoundTag entryTag = origList.getCompound(i);
            BlockPos pos = BlockPos.of(entryTag.getLong("pos"));
            BlockState blockState = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), entryTag.getCompound("state"));
            state.originalLamps.put(pos, blockState);
        }

        ListTag emergList = tag.getList("emergencyLamps", Tag.TAG_COMPOUND);
        for (int i = 0; i < emergList.size(); i++) {
            CompoundTag entryTag = emergList.getCompound(i);
            BlockPos pos = BlockPos.of(entryTag.getLong("pos"));
            BlockState blockState = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), entryTag.getCompound("state"));
            state.emergencyLamps.put(pos, blockState);
        }

        return state;
    }
}