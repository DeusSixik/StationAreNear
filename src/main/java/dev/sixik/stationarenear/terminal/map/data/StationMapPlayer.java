package dev.sixik.stationarenear.terminal.map.data;

import net.minecraft.network.FriendlyByteBuf;

public record StationMapPlayer(String name, double x, double z, int floor) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(name, 64);
        buffer.writeDouble(x);
        buffer.writeDouble(z);
        buffer.writeVarInt(floor);
    }

    public static StationMapPlayer decode(FriendlyByteBuf buffer) {
        return new StationMapPlayer(buffer.readUtf(64), buffer.readDouble(), buffer.readDouble(), buffer.readVarInt());
    }
}
