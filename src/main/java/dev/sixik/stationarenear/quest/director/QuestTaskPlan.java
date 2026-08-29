package dev.sixik.stationarenear.quest.director;

import dev.sixik.stationarenear.quest.config.director.QuestOfferConfig;
import dev.sixik.stationarenear.quest.data.QuestTask;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

public record QuestTaskPlan(
        QuestOfferConfig offer,
        int count,
        int totalCost
) {

    public QuestTaskPlan {
        count = Math.max(1, count);
        totalCost = Math.max(0, totalCost);
    }

    public String id() {
        return offer.id();
    }

    public QuestTask task() {
        return new QuestTask(offer.id(), count);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", offer.id());
        tag.putString("kind", offer.kind().name());
        tag.putInt("count", count);
        tag.putInt("cost", totalCost);
        if (!offer.targetTags().isEmpty()) {
            ListTag targetTags = new ListTag();
            for (String targetTag : offer.targetTags()) {
                targetTags.add(StringTag.valueOf(targetTag));
            }
            tag.put("targetTags", targetTags);
        }
        if (!offer.placeItem().isBlank()) {
            tag.putString("placeItem", offer.placeItem());
        }
        return tag;
    }
}
