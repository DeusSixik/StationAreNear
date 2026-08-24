package dev.sixik.stationarenear.quest.data;

import net.minecraft.nbt.CompoundTag;

public record QuestObjectiveState(String id, boolean completed, CompoundTag progress, int targetCount, String text) {

    public QuestObjectiveState(String id, boolean completed, CompoundTag progress) {
        this(id, completed, progress, 1, id);
    }

    public QuestObjectiveState {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Quest id cannot be blank");
        }
        progress = progress == null ? new CompoundTag() : progress.copy();
        targetCount = Math.max(1, targetCount);
        text = text == null || text.isBlank() ? id : text;
    }

    public QuestObjectiveState withProgress(CompoundTag progress) {
        return new QuestObjectiveState(id, completed, progress, targetCount, text);
    }

    public QuestObjectiveState withDisplay(int targetCount, String text) {
        return new QuestObjectiveState(id, completed, progress, targetCount, text);
    }

    public QuestObjectiveState complete(CompoundTag progress) {
        return new QuestObjectiveState(id, true, progress == null ? this.progress : progress, targetCount, text);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putBoolean("completed", completed);
        tag.put("progress", progress.copy());
        tag.putInt("targetCount", targetCount);
        tag.putString("text", text);
        return tag;
    }

    public static QuestObjectiveState load(CompoundTag tag) {
        return new QuestObjectiveState(
                tag.getString("id"),
                tag.getBoolean("completed"),
                tag.getCompound("progress"),
                tag.contains("targetCount") ? tag.getInt("targetCount") : 1,
                tag.contains("text") ? tag.getString("text") : tag.getString("id")
        );
    }
}
