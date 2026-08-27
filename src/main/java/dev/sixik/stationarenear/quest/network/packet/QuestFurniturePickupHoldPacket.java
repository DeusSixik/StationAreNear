package dev.sixik.stationarenear.quest.network.packet;

import dev.sixik.stationarenear.quest.runtime.QuestFurniturePickupManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record QuestFurniturePickupHoldPacket(BlockPos pos, boolean holding) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeBoolean(holding);
    }

    public static QuestFurniturePickupHoldPacket decode(FriendlyByteBuf buffer) {
        return new QuestFurniturePickupHoldPacket(buffer.readBlockPos(), buffer.readBoolean());
    }

    public static void handle(QuestFurniturePickupHoldPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (packet.holding()) {
                QuestFurniturePickupManager.hold(player, packet.pos());
            } else {
                QuestFurniturePickupManager.stop(player);
            }
        });
        context.setPacketHandled(true);
    }
}
