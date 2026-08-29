package dev.sixik.stationarenear.quest.network.packet;

import dev.sixik.stationarenear.ship.block.PressureTightDoorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record RepairDoorCompletePacket(BlockPos pos) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
    }

    public static RepairDoorCompletePacket decode(FriendlyByteBuf buffer) {
        return new RepairDoorCompletePacket(buffer.readBlockPos());
    }

    public static void handle(RepairDoorCompletePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.level() instanceof ServerLevel serverLevel)) {
                return;
            }
            if (player.blockPosition().distSqr(packet.pos()) > 100) {
                return;
            }
            PressureTightDoorBlock.performRepair(serverLevel, packet.pos(), player, InteractionHand.MAIN_HAND);
        });
        context.setPacketHandled(true);
    }
}
