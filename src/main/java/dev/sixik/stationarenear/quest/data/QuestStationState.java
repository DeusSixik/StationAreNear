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
    private static final long DOCKED_FAILURE_GRACE_MILLIS = 1000L;

    private final UUID stationId;
    private final Map<String, QuestObjectiveState> objectives = new Object2ObjectLinkedOpenHashMap<>();
    private long timerDurationMillis = NO_TIMER;
    private long timerRemainingMillis = NO_TIMER;
    private boolean timerExpired;
    private String displayStationCode = "";
    private String missionId = "";
    private double moneyReward;
    private CompoundTag directorPlan = new CompoundTag();

    public QuestStationState(UUID stationId) {
        this.stationId = stationId;
    }

    public UUID stationId() {
        return stationId;
    }

    public Collection<QuestObjectiveState> objectives() {
        return objectives.values();
    }

    public String missionId() {
        return missionId;
    }

    public void missionId(String missionId) {
        this.missionId = missionId == null ? "" : missionId.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public double moneyReward() {
        return moneyReward;
    }

    public void moneyReward(double moneyReward) {
        this.moneyReward = Double.isFinite(moneyReward) ? Math.max(0.0D, moneyReward) : 0.0D;
    }

    public CompoundTag directorPlan() {
        return directorPlan.copy();
    }

    public void directorPlan(CompoundTag directorPlan) {
        this.directorPlan = directorPlan == null ? new CompoundTag() : directorPlan.copy();
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
        return tickTimer(elapsedMillis, false);
    }

    public boolean tickTimer(long elapsedMillis, boolean deferFailureWhileDocked) {
        if (!timerRunning() || elapsedMillis <= 0L) {
            return false;
        }

        long minimumRemaining = deferFailureWhileDocked ? DOCKED_FAILURE_GRACE_MILLIS : 0L;
        timerRemainingMillis = Math.max(minimumRemaining, timerRemainingMillis - elapsedMillis);
        if (timerRemainingMillis > 0L) {
            return false;
        }

        timerExpired = true;
        return true;
    }

    public boolean expireTimer() {
        if (!hasTimer() || timerExpired || !hasActiveObjectives()) {
            return false;
        }
        timerRemainingMillis = 0L;
        timerExpired = true;
        return true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("stationId", stationId);
        if (!displayStationCode.isBlank()) {
            tag.putString("displayStationCode", displayStationCode);
        }
        if (!missionId.isBlank()) {
            tag.putString("missionId", missionId);
        }
        if (moneyReward > 0.0D) {
            tag.putDouble("moneyReward", moneyReward);
        }
        if (!directorPlan.isEmpty()) {
            tag.put("directorPlan", directorPlan.copy());
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
        copy.missionId = missionId;
        copy.moneyReward = moneyReward;
        copy.directorPlan = directorPlan.copy();
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
        state.missionId = tag.contains("missionId") ? tag.getString("missionId") : "";
        if (tag.contains("moneyReward")) {
            state.moneyReward(tag.getDouble("moneyReward"));
        } else if (tag.contains("money_reward")) {
            state.moneyReward(tag.getDouble("money_reward"));
        }
        if (tag.contains("directorPlan", Tag.TAG_COMPOUND)) {
            state.directorPlan(tag.getCompound("directorPlan"));
        }
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
