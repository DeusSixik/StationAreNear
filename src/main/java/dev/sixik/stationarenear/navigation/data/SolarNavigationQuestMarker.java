package dev.sixik.stationarenear.navigation.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public record SolarNavigationQuestMarker(String id, String name, float x, float y, float radius, int color, long seed) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(id, 128);
        buffer.writeUtf(name, 128);
        buffer.writeFloat(x);
        buffer.writeFloat(y);
        buffer.writeFloat(radius);
        buffer.writeInt(color);
        buffer.writeLong(seed);
    }

    public static SolarNavigationQuestMarker decode(FriendlyByteBuf buffer) {
        return new SolarNavigationQuestMarker(
                buffer.readUtf(128),
                buffer.readUtf(128),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readInt(),
                buffer.readLong()
        );
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("name", name);
        tag.putFloat("x", x);
        tag.putFloat("y", y);
        tag.putFloat("radius", radius);
        tag.putInt("color", color);
        tag.putLong("seed", seed);
        return tag;
    }

    public static SolarNavigationQuestMarker load(CompoundTag tag) {
        return new SolarNavigationQuestMarker(
                tag.getString("id"),
                tag.getString("name"),
                tag.getFloat("x"),
                tag.getFloat("y"),
                tag.contains("radius") ? tag.getFloat("radius") : 72.0F,
                tag.contains("color") ? tag.getInt("color") : 0xFFF7C45A,
                tag.contains("seed") ? tag.getLong("seed") : tag.getString("id").hashCode()
        );
    }
}
