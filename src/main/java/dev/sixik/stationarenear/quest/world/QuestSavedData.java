package dev.sixik.stationarenear.quest.world;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.quest.data.QuestStationState;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class QuestSavedData extends SavedData {

    private static final String DATA_NAME = StationAreNear.MODID + "_quests";

    private final Map<UUID, QuestStationState> stations = new Object2ObjectLinkedOpenHashMap<>();
    private UUID currentStationId;

    public static QuestSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(QuestSavedData::load, QuestSavedData::new, DATA_NAME);
    }

    public QuestStationState station(UUID stationId) {
        QuestStationState state = stations.get(stationId);
        if (state == null) {
            state = new QuestStationState(stationId);
            stations.put(stationId, state);
            setDirty();
        }
        return state;
    }

    public Optional<QuestStationState> stationIfPresent(UUID stationId) {
        return Optional.ofNullable(stations.get(stationId));
    }

    public boolean hasStation(UUID stationId) {
        return stations.containsKey(stationId);
    }

    public void station(QuestStationState state) {
        stations.put(state.stationId(), state);
        setDirty();
    }

    public Optional<UUID> currentStationId() {
        return Optional.ofNullable(currentStationId);
    }

    public Optional<QuestStationState> currentStation() {
        return currentStationId == null ? Optional.empty() : stationIfPresent(currentStationId);
    }

    public void currentStationId(UUID stationId) {
        currentStationId = stationId;
        setDirty();
    }

    public void clearCurrentStation() {
        currentStationId = null;
        setDirty();
    }

    public Collection<QuestStationState> stations() {
        return stations.values();
    }

    public boolean remove(UUID stationId) {
        boolean removed = stations.remove(stationId) != null;
        if (stationId.equals(currentStationId)) {
            currentStationId = null;
        }
        if (removed) {
            setDirty();
        }
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (currentStationId != null) {
            tag.putUUID("currentStationId", currentStationId);
        }
        ListTag stationTags = new ListTag();
        for (QuestStationState station : stations.values()) {
            stationTags.add(station.save());
        }
        tag.put("stations", stationTags);
        return tag;
    }

    private static QuestSavedData load(CompoundTag tag) {
        QuestSavedData data = new QuestSavedData();
        if (tag.hasUUID("currentStationId")) {
            data.currentStationId = tag.getUUID("currentStationId");
        }
        ListTag stationTags = tag.getList("stations", Tag.TAG_COMPOUND);
        for (Tag stationTag : stationTags) {
            try {
                QuestStationState state = QuestStationState.load((CompoundTag) stationTag);
                data.stations.put(state.stationId(), state);
            } catch (RuntimeException exception) {
                StationAreNear.LOGGER.warn("Skipped broken quest station state", exception);
            }
        }
        return data;
    }
}
