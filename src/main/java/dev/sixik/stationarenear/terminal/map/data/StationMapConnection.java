package dev.sixik.stationarenear.terminal.map.data;

import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;

public record StationMapConnection(
        int floor,
        int x,
        int z,
        Direction direction
) {

    public StationMapConnection {
        direction = direction == null ? Direction.NORTH : direction;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(floor);
        buffer.writeInt(x);
        buffer.writeInt(z);
        buffer.writeUtf(direction.getSerializedName(), 16);
    }

    public static StationMapConnection decode(FriendlyByteBuf buffer) {
        int floor = buffer.readVarInt();
        int x = buffer.readInt();
        int z = buffer.readInt();
        Direction direction = Direction.byName(buffer.readUtf(16));
        return new StationMapConnection(floor, x, z, direction == null ? Direction.NORTH : direction);
    }
}
