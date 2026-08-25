package dev.sixik.stationarenear.terminal.network.packet;

import dev.sixik.stationarenear.terminal.network.TerminalNetwork;
import dev.sixik.stationarenear.terminal.server.TerminalCommandProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record RequestOpenTerminalPacket(BlockPos terminalPos) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(terminalPos);
    }

    public static RequestOpenTerminalPacket decode(FriendlyByteBuf buffer) {
        return new RequestOpenTerminalPacket(buffer.readBlockPos());
    }

    public static void handle(RequestOpenTerminalPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && TerminalCommandProcessor.canUseTerminal(player, packet.terminalPos())) {
                TerminalNetwork.openTerminal(player, packet.terminalPos());
            }
        });
        context.setPacketHandled(true);
    }
}
