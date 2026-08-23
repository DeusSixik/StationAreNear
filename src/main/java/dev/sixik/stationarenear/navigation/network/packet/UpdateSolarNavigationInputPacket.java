package dev.sixik.stationarenear.navigation.network.packet;

import dev.sixik.stationarenear.navigation.server.SolarNavigationControlManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record UpdateSolarNavigationInputPacket(BlockPos terminalPos, int inputMask) {

    public static final int FORWARD = 1;
    public static final int BACKWARD = 1 << 1;
    public static final int LEFT = 1 << 2;
    public static final int RIGHT = 1 << 3;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(terminalPos);
        buffer.writeVarInt(inputMask);
    }

    public static UpdateSolarNavigationInputPacket decode(FriendlyByteBuf buffer) {
        return new UpdateSolarNavigationInputPacket(buffer.readBlockPos(), buffer.readVarInt());
    }

    public static void handle(UpdateSolarNavigationInputPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                SolarNavigationControlManager.updateInput(player, packet.terminalPos(), packet.inputMask());
            }
        });
        context.setPacketHandled(true);
    }
}
