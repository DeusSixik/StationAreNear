package dev.sixik.stationarenear.terminal.network;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.terminal.network.packet.OpenTerminalPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class TerminalNetwork {

    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(StationAreNear.MODID, "terminal"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private static int nextPacketId;

    private TerminalNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(OpenTerminalPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenTerminalPacket::encode)
                .decoder(OpenTerminalPacket::decode)
                .consumerMainThread(OpenTerminalPacket::handle)
                .add();
    }

    public static void openTerminal(ServerPlayer player, BlockPos terminalPos) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenTerminalPacket(terminalPos));
    }
}
