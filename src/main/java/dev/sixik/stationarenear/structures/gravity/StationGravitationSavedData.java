package dev.sixik.stationarenear.structures.gravity;

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

public class StationGravitationSavedData extends SavedData {

    private static final String DATA_NAME = StationAreNear.MODID + "_station_gravitation";

    private final Map<UUID, StationGravitationState> states = new Object2ObjectLinkedOpenHashMap<>();

    public static StationGravitationSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(StationGravitationSavedData::load, StationGravitationSavedData::new, DATA_NAME);
    }

    public Optional<StationGravitationState> getStation(UUID stationId) {
        return Optional.ofNullable(states.get(stationId));
    }

    public StationGravitationState getOrCreate(UUID stationId) {
        StationGravitationState state = states.get(stationId);
        if (state == null) {
            state = new StationGravitationState(stationId);
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
        for (StationGravitationState state : states.values()) {
            list.add(state.save());
        }
        tag.put("states", list);
        return tag;
    }

    public static StationGravitationSavedData load(CompoundTag tag) {
        StationGravitationSavedData data = new StationGravitationSavedData();
        ListTag list = tag.getList("states", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            StationGravitationState state = StationGravitationState.load(list.getCompound(i));
            data.states.put(state.stationId(), state);
        }
        return data;
    }
}