package dev.sixik.stationarenear.structures.network.packet;

import dev.sixik.stationarenear.structures.editor.StationStructureEditorStick;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record CycleStationEditorModePacket(int direction) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(direction);
    }

    public static CycleStationEditorModePacket decode(FriendlyByteBuf buffer) {
        return new CycleStationEditorModePacket(buffer.readVarInt());
    }

    public static void handle(CycleStationEditorModePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                StationStructureEditorStick.cycleMode(player, packet.direction());
            }
        });
        context.setPacketHandled(true);
    }
}
