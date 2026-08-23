package dev.sixik.stationarenear.structures.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public final class NbtPos {

    private NbtPos() {
    }

    public static CompoundTag save(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        return tag;
    }

    public static BlockPos load(CompoundTag tag) {
        return new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }
}
