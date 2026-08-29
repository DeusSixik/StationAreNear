package dev.sixik.stationarenear.terminal.map.data;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record StationMapSnapshot(
        BlockPos terminalPos,
        UUID stationId,
        String stationCode,
        int dockY,
        int dockX,
        int dockZ,
        int minFloor,
        int maxFloor,
        List<StationMapPiece> pieces,
        List<StationMapPlayer> trackedPlayers,
        List<StationMapPanel> trackedPanels
) {

    public StationMapSnapshot(BlockPos terminalPos, UUID stationId, String stationCode, int dockY, int dockX, int dockZ, int minFloor, int maxFloor, List<StationMapPiece> pieces) {
        this(terminalPos, stationId, stationCode, dockY, dockX, dockZ, minFloor, maxFloor, pieces, List.of(), List.of());
    }

    public StationMapSnapshot {
        stationCode = stationCode == null || stationCode.isBlank() ? stationId.toString().substring(0, 8).toUpperCase() : stationCode;
        pieces = List.copyOf(pieces == null ? List.of() : pieces);
        trackedPlayers = List.copyOf(trackedPlayers == null ? List.of() : trackedPlayers);
        trackedPanels = List.copyOf(trackedPanels == null ? List.of() : trackedPanels);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(terminalPos);
        buffer.writeUUID(stationId);
        buffer.writeUtf(stationCode, 32);
        buffer.writeInt(dockY);
        buffer.writeInt(dockX);
        buffer.writeInt(dockZ);
        buffer.writeVarInt(minFloor);
        buffer.writeVarInt(maxFloor);
        buffer.writeVarInt(pieces.size());
        for (StationMapPiece piece : pieces) {
            piece.encode(buffer);
        }
        buffer.writeVarInt(trackedPlayers.size());
        for (StationMapPlayer player : trackedPlayers) {
            player.encode(buffer);
        }
        buffer.writeVarInt(trackedPanels.size());
        for (StationMapPanel panel : trackedPanels) {
            panel.encode(buffer);
        }
    }

    public static StationMapSnapshot decode(FriendlyByteBuf buffer) {
        BlockPos terminalPos = buffer.readBlockPos();
        UUID stationId = buffer.readUUID();
        String stationCode = buffer.readUtf(32);
        int dockY = buffer.readInt();
        int dockX = buffer.readInt();
        int dockZ = buffer.readInt();
        int minFloor = buffer.readVarInt();
        int maxFloor = buffer.readVarInt();
        int count = buffer.readVarInt();
        List<StationMapPiece> pieces = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            pieces.add(StationMapPiece.decode(buffer));
        }
        int playerCount = buffer.readVarInt();
        List<StationMapPlayer> trackedPlayers = new ArrayList<>(playerCount);
        for (int i = 0; i < playerCount; i++) {
            trackedPlayers.add(StationMapPlayer.decode(buffer));
        }
        int panelCount = buffer.readVarInt();
        List<StationMapPanel> trackedPanels = new ArrayList<>(panelCount);
        for (int i = 0; i < panelCount; i++) {
            trackedPanels.add(StationMapPanel.decode(buffer));
        }
        return new StationMapSnapshot(terminalPos, stationId, stationCode, dockY, dockX, dockZ, minFloor, maxFloor, pieces, trackedPlayers, trackedPanels);
    }
}
