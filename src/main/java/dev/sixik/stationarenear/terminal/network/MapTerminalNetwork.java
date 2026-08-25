package dev.sixik.stationarenear.terminal.network;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.terminal.map.StationMapSnapshotFactory;
import dev.sixik.stationarenear.terminal.network.packet.OpenStationMapPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class MapTerminalNetwork {

    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(StationAreNear.MODID, "station_map_terminal"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private static int nextPacketId;

    private MapTerminalNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(OpenStationMapPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenStationMapPacket::encode)
                .decoder(OpenStationMapPacket::decode)
                .consumerMainThread(OpenStationMapPacket::handle)
                .add();
    }

    public static boolean openMap(ServerPlayer player, BlockPos terminalPos) {
        return openMap(player, terminalPos, false);
    }

    public static boolean openMap(ServerPlayer player, BlockPos terminalPos, boolean returnToTerminal) {
        return StationMapSnapshotFactory.create(player.serverLevel(), terminalPos)
                .map(snapshot -> {
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenStationMapPacket(snapshot, returnToTerminal));
                    return true;
                })
                .orElseGet(() -> {
                    player.displayClientMessage(Component.literal("Station map unavailable: ship is not docked with a station."), false);
                    return false;
                });
    }
}
