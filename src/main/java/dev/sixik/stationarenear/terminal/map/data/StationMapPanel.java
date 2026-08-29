package dev.sixik.stationarenear.terminal.map.data;

import net.minecraft.network.FriendlyByteBuf;

public record StationMapPanel(int x, int y, int z, int floor, boolean broken) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
        buffer.writeVarInt(floor);
        buffer.writeBoolean(broken);
    }

    public static StationMapPanel decode(FriendlyByteBuf buffer) {
        return new StationMapPanel(buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readVarInt(), buffer.readBoolean());
    }
}
