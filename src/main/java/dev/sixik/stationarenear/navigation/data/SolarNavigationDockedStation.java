package dev.sixik.stationarenear.navigation.data;

import net.minecraft.network.FriendlyByteBuf;

public record SolarNavigationDockedStation(long seed, String name, float x, float y) {

    public SolarNavigationDockedStation {
        name = name == null || name.isBlank() ? "Unknown Station" : name;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeLong(seed);
        buffer.writeUtf(name, 128);
        buffer.writeFloat(x);
        buffer.writeFloat(y);
    }

    public static SolarNavigationDockedStation decode(FriendlyByteBuf buffer) {
        return new SolarNavigationDockedStation(
                buffer.readLong(),
                buffer.readUtf(128),
                buffer.readFloat(),
                buffer.readFloat()
        );
    }
}
