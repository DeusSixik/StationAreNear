package dev.sixik.stationarenear.quest.world;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.quest.data.QuestStationState;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class QuestSavedData extends SavedData {

    private static final String DATA_NAME = StationAreNear.MODID + "_quests";

    private final Map<UUID, QuestStationState> stations = new Object2ObjectLinkedOpenHashMap<>();
    private final Set<String> completedQuestIds = new LinkedHashSet<>();
    private int completedMissionCount;
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

    public Set<String> completedQuestIds() {
        return Set.copyOf(completedQuestIds);
    }

    public int completedMissionCount() {
        return completedMissionCount;
    }

    public void incrementCompletedMissionCount() {
        completedMissionCount++;
        setDirty();
    }

    public void setCompletedMissionCount(int count) {
        completedMissionCount = Math.max(0, count);
        setDirty();
    }

    public void resetDifficulty() {
        completedMissionCount = 0;
        setDirty();
    }

    public void resetQuests() {
        stations.clear();
        completedQuestIds.clear();
        currentStationId = null;
        setDirty();
    }

    public void resetAll() {
        stations.clear();
        completedQuestIds.clear();
        completedMissionCount = 0;
        currentStationId = null;
        setDirty();
    }

    public boolean isQuestCompleted(String questId) {
        return completedQuestIds.contains(normalizeQuestId(questId));
    }

    public boolean markQuestCompleted(String questId) {
        String normalized = normalizeQuestId(questId);
        if (normalized.isBlank() || !completedQuestIds.add(normalized)) {
            return false;
        }
        setDirty();
        return true;
    }

    public boolean clearCompletedQuest(String questId) {
        boolean removed = completedQuestIds.remove(normalizeQuestId(questId));
        if (removed) {
            setDirty();
        }
        return removed;
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
        tag.putInt("completedMissionCount", completedMissionCount);
        ListTag completedQuestTags = new ListTag();
        for (String questId : completedQuestIds) {
            completedQuestTags.add(StringTag.valueOf(questId));
        }
        tag.put("completedQuestIds", completedQuestTags);
        ListTag stationTags = new ListTag();
        for (QuestStationState station : stations.values()) {
            stationTags.add(station.save());
        }
        tag.put("stations", stationTags);
        return tag;
    }

    private static String normalizeQuestId(String questId) {
        String normalized = questId == null ? "" : questId.trim();
        return normalized.isBlank() ? "" : normalized.toLowerCase(java.util.Locale.ROOT);
    }

    private static QuestSavedData load(CompoundTag tag) {
        QuestSavedData data = new QuestSavedData();
        if (tag.hasUUID("currentStationId")) {
            data.currentStationId = tag.getUUID("currentStationId");
        }
        data.completedMissionCount = Math.max(0, tag.getInt("completedMissionCount"));
        ListTag completedQuestTags = tag.getList("completedQuestIds", Tag.TAG_STRING);
        for (Tag questTag : completedQuestTags) {
            if (questTag instanceof StringTag stringTag) {
                String questId = normalizeQuestId(stringTag.getAsString());
                if (!questId.isBlank()) {
                    data.completedQuestIds.add(questId);
                }
            }
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
