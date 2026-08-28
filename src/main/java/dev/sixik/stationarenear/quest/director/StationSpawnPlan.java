package dev.sixik.stationarenear.quest.director;

import dev.sixik.stationarenear.quest.config.director.StationOfferConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

public record StationSpawnPlan(
        StationOfferConfig offer,
        int count,
        int totalCost
) {

    public StationSpawnPlan {
        count = Math.max(1, count);
        totalCost = Math.max(0, totalCost);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", offer.id());
        tag.putString("type", offer.type().name());
        tag.putInt("count", count);
        tag.putInt("remaining", count);
        tag.putInt("cost", totalCost);
        if (!offer.entity().isBlank()) {
            tag.putString("entity", offer.entity());
        }
        ListTag targetTags = new ListTag();
        for (String targetTag : offer.targetTags()) {
            targetTags.add(StringTag.valueOf(targetTag));
        }
        tag.put("targetTags", targetTags);
        return tag;
    }
}
