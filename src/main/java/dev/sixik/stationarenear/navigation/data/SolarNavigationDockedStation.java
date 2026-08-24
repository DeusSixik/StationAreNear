package dev.sixik.stationarenear.navigation.data;

import dev.sixik.stationarenear.navigation.StationCodeGenerator;
import net.minecraft.network.FriendlyByteBuf;

public record SolarNavigationDockedStation(long seed, String name, String code, float x, float y) {

    public SolarNavigationDockedStation {
        name = name == null || name.isBlank() ? "Unknown Station" : name;
        code = code == null || code.isBlank() ? StationCodeGenerator.code(seed, x, y) : code;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeLong(seed);
        buffer.writeUtf(name, 128);
        buffer.writeUtf(code, 32);
        buffer.writeFloat(x);
        buffer.writeFloat(y);
    }

    public static SolarNavigationDockedStation decode(FriendlyByteBuf buffer) {
        return new SolarNavigationDockedStation(
                buffer.readLong(),
                buffer.readUtf(128),
                buffer.readUtf(32),
                buffer.readFloat(),
                buffer.readFloat()
        );
    }
}
