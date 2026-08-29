package dev.sixik.stationarenear.structures.lamps;

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

public class StationLampSavedData extends SavedData {

    private static final String DATA_NAME = StationAreNear.MODID + "_station_lamps";

    private final Map<UUID, StationLampState> states = new Object2ObjectLinkedOpenHashMap<>();

    public static StationLampSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(StationLampSavedData::load, StationLampSavedData::new, DATA_NAME);
    }

    public Optional<StationLampState> getStation(UUID stationId) {
        return Optional.ofNullable(states.get(stationId));
    }

    public StationLampState getOrCreate(UUID stationId) {
        StationLampState state = states.get(stationId);
        if (state == null) {
            state = new StationLampState(stationId);
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
        for (StationLampState state : states.values()) {
            list.add(state.save());
        }
        tag.put("states", list);
        return tag;
    }

    public static StationLampSavedData load(CompoundTag tag) {
        StationLampSavedData data = new StationLampSavedData();
        ListTag list = tag.getList("states", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            StationLampState state = StationLampState.load(list.getCompound(i));
            data.states.put(state.stationId(), state);
        }
        return data;
    }
}