package dev.sixik.stationarenear.structures.world;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.structures.data.StationInstance;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class StationSavedData extends SavedData {

    private static final String DATA_NAME = StationAreNear.MODID + "_stations";

    private final Map<UUID, StationInstance> stations = new Object2ObjectLinkedOpenHashMap<>();
    private final Map<UUID, Set<String>> activatedTriggers = new Object2ObjectLinkedOpenHashMap<>();

    public static StationSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(StationSavedData::load, StationSavedData::new, DATA_NAME);
    }

    public Optional<StationInstance> station(UUID id) {
        return Optional.ofNullable(stations.get(id));
    }

    public Collection<StationInstance> stations() {
        return stations.values();
    }

    public void addStation(StationInstance station) {
        stations.put(station.id(), station);
        setDirty();
    }

    public boolean removeStation(UUID id) {
        boolean removed = stations.remove(id) != null;
        activatedTriggers.remove(id);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public boolean isTriggerActivated(UUID stationId, String triggerId) {
        return activatedTriggers.getOrDefault(stationId, Set.of()).contains(triggerId);
    }

    public boolean markTriggerActivated(UUID stationId, String triggerId) {
        Set<String> triggers = activatedTriggers.computeIfAbsent(stationId, ignored -> new LinkedHashSet<>());
        boolean added = triggers.add(triggerId);
        if (added) {
            setDirty();
        }
        return added;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag stationTags = new ListTag();
        for (StationInstance station : stations.values()) {
            stationTags.add(station.save());
        }
        tag.put("stations", stationTags);

        ListTag activationTags = new ListTag();
        for (Map.Entry<UUID, Set<String>> entry : activatedTriggers.entrySet()) {
            CompoundTag activationTag = new CompoundTag();
            activationTag.putUUID("station", entry.getKey());
            ListTag triggerTags = new ListTag();
            for (String triggerId : entry.getValue()) {
                triggerTags.add(StringTag.valueOf(triggerId));
            }
            activationTag.put("triggers", triggerTags);
            activationTags.add(activationTag);
        }
        tag.put("activatedTriggers", activationTags);
        return tag;
    }

    private static StationSavedData load(CompoundTag tag) {
        StationSavedData data = new StationSavedData();
        ListTag stationTags = tag.getList("stations", Tag.TAG_COMPOUND);
        for (Tag stationTag : stationTags) {
            try {
                StationInstance station = StationInstance.load((CompoundTag) stationTag);
                data.stations.put(station.id(), station);
            } catch (RuntimeException exception) {
                StationAreNear.LOGGER.warn("Skipped broken generated station", exception);
            }
        }

        ListTag activationTags = tag.getList("activatedTriggers", Tag.TAG_COMPOUND);
        for (Tag activationEntry : activationTags) {
            CompoundTag activationTag = (CompoundTag) activationEntry;
            UUID stationId = activationTag.getUUID("station");
            Set<String> triggers = new LinkedHashSet<>();
            ListTag triggerTags = activationTag.getList("triggers", Tag.TAG_STRING);
            for (Tag triggerTag : triggerTags) {
                if (triggerTag instanceof StringTag stringTag) {
                    triggers.add(stringTag.getAsString());
                }
            }
            data.activatedTriggers.put(stationId, triggers);
        }
        return data;
    }
}
