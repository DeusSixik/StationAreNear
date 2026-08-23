package dev.sixik.stationarenear.structures.data;

import dev.sixik.stationarenear.structures.util.NbtPos;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public record StationTriggerZone(
        String id,
        String type,
        BlockPos min,
        BlockPos max,
        CompoundTag data
) {

    public StationTriggerZone {
        data = data.copy();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("type", type);
        tag.put("min", NbtPos.save(min));
        tag.put("max", NbtPos.save(max));
        tag.put("data", data.copy());
        return tag;
    }

    public static StationTriggerZone load(CompoundTag tag) {
        return new StationTriggerZone(
                tag.getString("id"),
                tag.getString("type"),
                NbtPos.load(tag.getCompound("min")),
                NbtPos.load(tag.getCompound("max")),
                tag.getCompound("data")
        );
    }
}
