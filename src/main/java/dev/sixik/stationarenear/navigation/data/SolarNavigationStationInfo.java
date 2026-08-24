package dev.sixik.stationarenear.navigation.data;

import dev.sixik.stationarenear.navigation.StationCodeGenerator;
import net.minecraft.network.FriendlyByteBuf;

public record SolarNavigationStationInfo(String name, String code, float x, float y, float radius, boolean quest, long seed, int color, float distance) {

    public SolarNavigationStationInfo {
        name = name == null || name.isBlank() ? "Unknown Station" : name;
        code = code == null || code.isBlank() ? StationCodeGenerator.code(seed, x, y) : code;
        radius = Math.max(1.0F, radius);
        distance = Math.max(0.0F, distance);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(name, 128);
        buffer.writeUtf(code, 32);
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
                buffer.readUtf(128),
                buffer.readUtf(32),
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
