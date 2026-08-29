package dev.sixik.stationarenear.structures.oxygen;

import dev.sixik.stationarenear.StationAreNear;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class StationOxygenSavedData extends SavedData {

    private static final String DATA_NAME = StationAreNear.MODID + "_station_oxygen";

    private final Map<UUID, StationOxygenState> states = new Object2ObjectLinkedOpenHashMap<>();

    public static StationOxygenSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(StationOxygenSavedData::load, StationOxygenSavedData::new, DATA_NAME);
    }

    public Optional<StationOxygenState> getStation(UUID stationId) {
        return Optional.ofNullable(states.get(stationId));
    }

    public StationOxygenState getOrCreate(UUID stationId) {
        StationOxygenState state = states.get(stationId);
        if (state == null) {
            state = new StationOxygenState(stationId);
            states.put(stationId, state);
            setDirty();
        }
        return state;
    }

    public void remove(UUID stationId) {
        if (states.remove(stationId) != null) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (StationOxygenState state : states.values()) {
            list.add(state.save());
        }
        tag.put("states", list);
        return tag;
    }

    public static StationOxygenSavedData load(CompoundTag tag) {
        StationOxygenSavedData data = new StationOxygenSavedData();
        ListTag list = tag.getList("states", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            StationOxygenState state = StationOxygenState.load(list.getCompound(i));
            data.states.put(state.stationId(), state);
        }
        return data;
    }
}