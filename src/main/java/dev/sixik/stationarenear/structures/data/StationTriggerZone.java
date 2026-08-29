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
        CompoundTag data = tag.getCompound("data").copy();
        copyStringIfMissing(tag, data, "direction");
        copyStringIfMissing(tag, data, "shapeDirection");
        copyStringIfMissing(tag, data, "objectDirection");
        return new StationTriggerZone(
                tag.getString("id"),
                tag.getString("type"),
                NbtPos.load(tag.getCompound("min")),
                NbtPos.load(tag.getCompound("max")),
                data
        );
    }

    private static void copyStringIfMissing(CompoundTag source, CompoundTag target, String key) {
        if (!target.contains(key) && source.contains(key) && !source.getString(key).isBlank()) {
            target.putString(key, source.getString(key));
        }
    }
}
