package dev.sixik.stationarenear.mob.network.packet;

import dev.sixik.stationarenear.mob.entity.LivingTrashEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record LivingTrashContainerHoverPacket(BlockPos containerPos, int slotIndex) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(containerPos);
        buffer.writeVarInt(slotIndex);
    }

    public static LivingTrashContainerHoverPacket decode(FriendlyByteBuf buffer) {
        return new LivingTrashContainerHoverPacket(buffer.readBlockPos(), buffer.readVarInt());
    }

    public static void handle(LivingTrashContainerHoverPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || player.containerMenu == player.inventoryMenu) {
                return;
            }
            LivingTrashEntity.releaseHiddenNear(player, packet.containerPos(), packet.slotIndex());
        });
        context.setPacketHandled(true);
    }
}
