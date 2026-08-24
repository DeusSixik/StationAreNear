package dev.sixik.stationarenear.navigation.data;

import net.minecraft.network.FriendlyByteBuf;

public record SolarNavigationStationInfo(String name, float x, float y, float radius, boolean quest, long seed, int color, float distance) {

    public SolarNavigationStationInfo {
        name = name == null || name.isBlank() ? "Unknown Station" : name;
        radius = Math.max(1.0F, radius);
        distance = Math.max(0.0F, distance);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(name);
        buffer.writeFloat(x);
        buffer.writeFloat(y);
        buffer.writeFloat(radius);
        buffer.writeBoolean(quest);
        buffer.writeLong(seed);
        buffer.writeInt(color);
        buffer.writeFloat(distance);
    }

    public static SolarNavigationStationInfo decode(FriendlyByteBuf buffer) {
        return new SolarNavigationStationInfo(
                buffer.readUtf(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readBoolean(),
                buffer.readLong(),
                buffer.readInt(),
                buffer.readFloat()
        );
    }
}
