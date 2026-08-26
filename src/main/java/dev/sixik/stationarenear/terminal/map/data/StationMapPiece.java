package dev.sixik.stationarenear.terminal.map.data;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record StationMapPiece(
        ResourceLocation definitionId,
        int minFloor,
        int maxFloor,
        int minX,
        int minZ,
        int maxX,
        int maxZ,
        boolean dockPiece,
        List<StationMapConnection> connections,
        String markerId
) {

    public StationMapPiece(ResourceLocation definitionId, int minFloor, int maxFloor, int minX, int minZ, int maxX, int maxZ, boolean dockPiece, List<StationMapConnection> connections) {
        this(definitionId, minFloor, maxFloor, minX, minZ, maxX, maxZ, dockPiece, connections, "");
    }

    public StationMapPiece {
        if (definitionId == null) {
            definitionId = new ResourceLocation("minecraft", "empty");
        }
        if (maxFloor < minFloor) {
            int swap = maxFloor;
            maxFloor = minFloor;
            minFloor = swap;
        }
        connections = List.copyOf(connections == null ? List.of() : connections);
        markerId = markerId == null ? "" : markerId.trim();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(definitionId);
        buffer.writeVarInt(minFloor);
        buffer.writeVarInt(maxFloor);
        buffer.writeInt(minX);
        buffer.writeInt(minZ);
        buffer.writeInt(maxX);
        buffer.writeInt(maxZ);
        buffer.writeBoolean(dockPiece);
        buffer.writeUtf(markerId, 64);
        buffer.writeVarInt(connections.size());
        for (StationMapConnection connection : connections) {
            connection.encode(buffer);
        }
    }

    public static StationMapPiece decode(FriendlyByteBuf buffer) {
        ResourceLocation definitionId = buffer.readResourceLocation();
        int minFloor = buffer.readVarInt();
        int maxFloor = buffer.readVarInt();
        int minX = buffer.readInt();
        int minZ = buffer.readInt();
        int maxX = buffer.readInt();
        int maxZ = buffer.readInt();
        boolean dockPiece = buffer.readBoolean();
        String markerId = buffer.readUtf(64);
        int connectionCount = buffer.readVarInt();
        List<StationMapConnection> connections = new ArrayList<>(connectionCount);
        for (int i = 0; i < connectionCount; i++) {
            connections.add(StationMapConnection.decode(buffer));
        }
        return new StationMapPiece(definitionId, minFloor, maxFloor, minX, minZ, maxX, maxZ, dockPiece, connections, markerId);
    }
}
