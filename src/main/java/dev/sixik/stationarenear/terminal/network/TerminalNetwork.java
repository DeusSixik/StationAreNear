package dev.sixik.stationarenear.terminal.network;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.terminal.data.TerminalHistoryLine;
import dev.sixik.stationarenear.terminal.data.TerminalSnapshotFactory;
import dev.sixik.stationarenear.terminal.network.packet.OpenTerminalPacket;
import dev.sixik.stationarenear.terminal.network.packet.SubmitTerminalCommandPacket;
import dev.sixik.stationarenear.terminal.network.packet.SyncTerminalHistoryPacket;
import dev.sixik.stationarenear.terminal.server.TerminalCommandProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;

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
        CHANNEL.messageBuilder(SubmitTerminalCommandPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SubmitTerminalCommandPacket::encode)
                .decoder(SubmitTerminalCommandPacket::decode)
                .consumerMainThread(SubmitTerminalCommandPacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncTerminalHistoryPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncTerminalHistoryPacket::encode)
                .decoder(SyncTerminalHistoryPacket::decode)
                .consumerMainThread(SyncTerminalHistoryPacket::handle)
                .add();
    }

    public static void openTerminal(ServerPlayer player, BlockPos terminalPos) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenTerminalPacket(
                terminalPos,
                TerminalSnapshotFactory.create(player.serverLevel(), terminalPos),
                TerminalCommandProcessor.historyForOpen(player.serverLevel(), terminalPos)
        ));
    }

    public static void sendCommand(BlockPos terminalPos, String command) {
        CHANNEL.sendToServer(new SubmitTerminalCommandPacket(terminalPos, command));
    }

    public static void syncHistory(ServerLevel level, BlockPos terminalPos, List<TerminalHistoryLine> history) {
        SyncTerminalHistoryPacket packet = new SyncTerminalHistoryPacket(terminalPos, history);
        for (ServerPlayer player : level.players()) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }
}
