package dev.sixik.stationarenear.quest.data;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class QuestStationState {

    private static final long NO_TIMER = -1L;

    private final UUID stationId;
    private final Map<String, QuestObjectiveState> objectives = new Object2ObjectLinkedOpenHashMap<>();
    private long timerDurationMillis = NO_TIMER;
    private long timerRemainingMillis = NO_TIMER;
    private boolean timerExpired;
    private String displayStationCode = "";

    public QuestStationState(UUID stationId) {
        this.stationId = stationId;
    }

    public UUID stationId() {
        return stationId;
    }

    public Collection<QuestObjectiveState> objectives() {
        return objectives.values();
    }

    public String displayStationCode() {
        return displayStationCode;
    }

    public void displayStationCode(String displayStationCode) {
        this.displayStationCode = displayStationCode == null ? "" : displayStationCode.trim();
    }

    public Optional<QuestObjectiveState> objective(String id) {
        return Optional.ofNullable(objectives.get(id));
    }

    public boolean putIfAbsent(QuestObjectiveState objective) {
        if (objectives.containsKey(objective.id())) {
            return false;
        }
        objectives.put(objective.id(), objective);
        return true;
    }

    public void put(QuestObjectiveState objective) {
        objectives.put(objective.id(), objective);
    }

    public boolean hasActiveObjectives() {
        for (QuestObjectiveState objective : objectives.values()) {
            if (!objective.completed()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasTimer() {
        return timerDurationMillis >= 0L || timerRemainingMillis >= 0L || timerExpired;
    }

    public boolean timerRunning() {
        return hasTimer() && !timerExpired && timerRemainingMillis > 0L && hasActiveObjectives();
    }

    public boolean timerExpired() {
        return timerExpired;
    }

    public long timerDurationMillis() {
        return Math.max(0L, timerDurationMillis);
    }

    public long timerRemainingMillis() {
        return Math.max(0L, timerRemainingMillis);
    }

    public void startTimer(long durationMillis) {
        if (durationMillis <= 0L) {
            throw new IllegalArgumentException("Quest timer duration must be positive");
        }
        timerDurationMillis = durationMillis;
        timerRemainingMillis = durationMillis;
        timerExpired = false;
    }

    public void clearTimer() {
        timerDurationMillis = NO_TIMER;
        timerRemainingMillis = NO_TIMER;
        timerExpired = false;
    }

    public boolean tickTimer(long elapsedMillis) {
        if (!timerRunning() || elapsedMillis <= 0L) {
            return false;
        }

        timerRemainingMillis = Math.max(0L, timerRemainingMillis - elapsedMillis);
        if (timerRemainingMillis > 0L) {
            return false;
        }

        timerExpired = true;
        return true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("stationId", stationId);
        if (!displayStationCode.isBlank()) {
            tag.putString("displayStationCode", displayStationCode);
        }
        ListTag objectiveTags = new ListTag();
        for (QuestObjectiveState objective : objectives.values()) {
            objectiveTags.add(objective.save());
        }
        tag.put("objectives", objectiveTags);
        if (hasTimer()) {
            tag.putLong("timerDurationMillis", timerDurationMillis);
            tag.putLong("timerRemainingMillis", timerRemainingMillis);
            tag.putBoolean("timerExpired", timerExpired);
        }
        return tag;
    }

    public QuestStationState copyFor(UUID newStationId, Map<String, String> targetTriggerIds) {
        QuestStationState copy = new QuestStationState(newStationId);
        copy.timerDurationMillis = timerDurationMillis;
        copy.timerRemainingMillis = timerRemainingMillis;
        copy.timerExpired = timerExpired;
        copy.displayStationCode = displayStationCode;
        Map<String, String> targets = targetTriggerIds == null ? Map.of() : new LinkedHashMap<>(targetTriggerIds);
        for (QuestObjectiveState objective : objectives.values()) {
            String target = targets.getOrDefault(objective.id(), objective.targetTriggerId());
            copy.put(objective.withTargetTriggerId(target));
        }
        return copy;
    }

    public static QuestStationState load(CompoundTag tag) {
        QuestStationState state = new QuestStationState(tag.getUUID("stationId"));
        state.displayStationCode = tag.contains("displayStationCode") ? tag.getString("displayStationCode") : "";
        ListTag objectiveTags = tag.getList("objectives", Tag.TAG_COMPOUND);
        for (Tag objectiveTag : objectiveTags) {
            QuestObjectiveState objective = QuestObjectiveState.load((CompoundTag) objectiveTag);
            state.objectives.put(objective.id(), objective);
        }
        if (tag.contains("timerDurationMillis") || tag.contains("timerRemainingMillis") || tag.contains("timerExpired")) {
            state.timerDurationMillis = tag.contains("timerDurationMillis") ? tag.getLong("timerDurationMillis") : NO_TIMER;
            state.timerRemainingMillis = tag.contains("timerRemainingMillis") ? tag.getLong("timerRemainingMillis") : state.timerDurationMillis;
            state.timerExpired = tag.getBoolean("timerExpired");
        }
        return state;
    }
}
