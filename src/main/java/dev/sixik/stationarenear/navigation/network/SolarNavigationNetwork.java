package dev.sixik.stationarenear.navigation.network;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.navigation.network.packet.ClearDockedSolarStationPacket;
import dev.sixik.stationarenear.navigation.network.packet.DockSolarStationPacket;
import dev.sixik.stationarenear.navigation.data.SolarNavigationShipState;
import dev.sixik.stationarenear.navigation.network.packet.OpenSolarNavigationPacket;
import dev.sixik.stationarenear.navigation.network.packet.SolarNavigationAsteroidCollisionPacket;
import dev.sixik.stationarenear.navigation.network.packet.SyncSolarNavigationQuestMarkersPacket;
import dev.sixik.stationarenear.navigation.network.packet.SyncSolarNavigationStatePacket;
import dev.sixik.stationarenear.navigation.network.packet.UpdateSolarNavigationInputPacket;
import dev.sixik.stationarenear.navigation.network.packet.UpdateSolarNavigationStatePacket;
import dev.sixik.stationarenear.navigation.server.SolarNavigationControlManager;
import dev.sixik.stationarenear.navigation.world.SolarNavigationSavedData;
import dev.sixik.stationarenear.navigation.world.SolarNavigationStationCleaner;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;

public final class SolarNavigationNetwork {

    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(StationAreNear.MODID, "navigation"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private static int nextPacketId;

    private SolarNavigationNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(OpenSolarNavigationPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenSolarNavigationPacket::encode)
                .decoder(OpenSolarNavigationPacket::decode)
                .consumerMainThread(OpenSolarNavigationPacket::handle)
                .add();
        CHANNEL.messageBuilder(DockSolarStationPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DockSolarStationPacket::encode)
                .decoder(DockSolarStationPacket::decode)
                .consumerMainThread(DockSolarStationPacket::handle)
                .add();
        CHANNEL.messageBuilder(UpdateSolarNavigationStatePacket.class, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UpdateSolarNavigationStatePacket::encode)
                .decoder(UpdateSolarNavigationStatePacket::decode)
                .consumerMainThread(UpdateSolarNavigationStatePacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncSolarNavigationQuestMarkersPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncSolarNavigationQuestMarkersPacket::encode)
                .decoder(SyncSolarNavigationQuestMarkersPacket::decode)
                .consumerMainThread(SyncSolarNavigationQuestMarkersPacket::handle)
                .add();
        CHANNEL.messageBuilder(SolarNavigationAsteroidCollisionPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SolarNavigationAsteroidCollisionPacket::encode)
                .decoder(SolarNavigationAsteroidCollisionPacket::decode)
                .consumerMainThread(SolarNavigationAsteroidCollisionPacket::handle)
                .add();
        CHANNEL.messageBuilder(ClearDockedSolarStationPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClearDockedSolarStationPacket::encode)
                .decoder(ClearDockedSolarStationPacket::decode)
                .consumerMainThread(ClearDockedSolarStationPacket::handle)
                .add();
        CHANNEL.messageBuilder(UpdateSolarNavigationInputPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UpdateSolarNavigationInputPacket::encode)
                .decoder(UpdateSolarNavigationInputPacket::decode)
                .consumerMainThread(UpdateSolarNavigationInputPacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncSolarNavigationStatePacket.class, nextPacketId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncSolarNavigationStatePacket::encode)
                .decoder(SyncSolarNavigationStatePacket::decode)
                .consumerMainThread(SyncSolarNavigationStatePacket::handle)
                .add();
    }

    public static void openTerminal(ServerPlayer player, BlockPos terminalPos, long seed) {
        SolarNavigationSavedData data = SolarNavigationSavedData.get(player.serverLevel());
        SolarNavigationShipState state = SolarNavigationControlManager.open(player, terminalPos, seed, data.shipState(terminalPos));
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenSolarNavigationPacket(
                seed,
                terminalPos,
                state,
                List.copyOf(data.questMarkers()),
                SolarNavigationStationCleaner.dockedStations(player.serverLevel(), terminalPos)
        ));
    }

    public static void sendDock(DockSolarStationPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendState(BlockPos terminalPos, SolarNavigationShipState state) {
        CHANNEL.sendToServer(new UpdateSolarNavigationStatePacket(terminalPos, state));
    }

    public static void sendInput(BlockPos terminalPos, int inputMask) {
        CHANNEL.sendToServer(new UpdateSolarNavigationInputPacket(terminalPos, inputMask));
    }

    public static void syncState(ServerPlayer player, BlockPos terminalPos, SolarNavigationShipState state) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncSolarNavigationStatePacket(terminalPos, state));
    }

    public static void sendAsteroidCollision(SolarNavigationAsteroidCollisionPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendClearDockedStation(ClearDockedSolarStationPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void syncQuestMarkers(ServerLevel level) {
        SolarNavigationSavedData data = SolarNavigationSavedData.get(level);
        SyncSolarNavigationQuestMarkersPacket packet = new SyncSolarNavigationQuestMarkersPacket(List.copyOf(data.questMarkers()));
        for (ServerPlayer player : level.players()) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }
}
