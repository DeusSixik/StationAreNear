package dev.sixik.stationarenear.terminal.network.packet;

import dev.sixik.stationarenear.terminal.server.TerminalCommandProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SubmitTerminalCommandPacket(BlockPos terminalPos, String command) {

    public SubmitTerminalCommandPacket {
        command = command == null ? "" : command;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(terminalPos);
        buffer.writeUtf(command, 512);
    }

    public static SubmitTerminalCommandPacket decode(FriendlyByteBuf buffer) {
        return new SubmitTerminalCommandPacket(buffer.readBlockPos(), buffer.readUtf(512));
    }

    public static void handle(SubmitTerminalCommandPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                TerminalCommandProcessor.submit(player, packet.terminalPos(), packet.command());
            }
        });
        context.setPacketHandled(true);
    }
}
