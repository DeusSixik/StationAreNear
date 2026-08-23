package dev.sixik.stationarenear.structures.network;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.structures.data.TemplateSelectionEntry;
import dev.sixik.stationarenear.structures.network.packet.CycleStationEditorModePacket;
import dev.sixik.stationarenear.structures.network.packet.OpenStationTemplateMenuPacket;
import dev.sixik.stationarenear.structures.network.packet.OpenStationZoneEditorPacket;
import dev.sixik.stationarenear.structures.network.packet.SaveStationZoneEditorPacket;
import dev.sixik.stationarenear.structures.network.packet.SyncStationTemplateSelectionsPacket;
import dev.sixik.stationarenear.structures.network.packet.TemplateSelectionActionPacket;
import dev.sixik.stationarenear.structures.world.StationStructureLibraryData;
import dev.sixik.stationarenear.structures.world.StationTemplateSelectionManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;

public final class StationStructureNetwork {

    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(StationAreNear.MODID, "structures"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private static int nextPacketId;

    private StationStructureNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(OpenStationZoneEditorPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenStationZoneEditorPacket::encode)
                .decoder(OpenStationZoneEditorPacket::decode)
                .consumerMainThread(OpenStationZoneEditorPacket::handle)
                .add();
        CHANNEL.messageBuilder(SaveStationZoneEditorPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SaveStationZoneEditorPacket::encode)
                .decoder(SaveStationZoneEditorPacket::decode)
                .consumerMainThread(SaveStationZoneEditorPacket::handle)
                .add();
        CHANNEL.messageBuilder(CycleStationEditorModePacket.class, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CycleStationEditorModePacket::encode)
                .decoder(CycleStationEditorModePacket::decode)
                .consumerMainThread(CycleStationEditorModePacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncStationTemplateSelectionsPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncStationTemplateSelectionsPacket::encode)
                .decoder(SyncStationTemplateSelectionsPacket::decode)
                .consumerMainThread(SyncStationTemplateSelectionsPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenStationTemplateMenuPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenStationTemplateMenuPacket::encode)
                .decoder(OpenStationTemplateMenuPacket::decode)
                .consumerMainThread(OpenStationTemplateMenuPacket::handle)
                .add();
        CHANNEL.messageBuilder(TemplateSelectionActionPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TemplateSelectionActionPacket::encode)
                .decoder(TemplateSelectionActionPacket::decode)
                .consumerMainThread(TemplateSelectionActionPacket::handle)
                .add();
    }

    public static void sendOpenEditor(ServerPlayer player, OpenStationZoneEditorPacket packet) {
        List<ResourceLocation> poolIds = StationStructureLibraryData.get(player.serverLevel()).pools().stream()
                .map(dev.sixik.stationarenear.structures.data.StationPoolDefinition::id)
                .sorted(java.util.Comparator.comparing(ResourceLocation::toString))
                .toList();
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet.withPoolIds(poolIds));
    }

    public static void sendEditorSave(SaveStationZoneEditorPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendModeCycle(CycleStationEditorModePacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendTemplateAction(TemplateSelectionActionPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void openTemplateMenu(ServerPlayer player) {
        List<TemplateSelectionEntry> entries = StationTemplateSelectionManager.collect(player.serverLevel());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenStationTemplateMenuPacket(entries));
    }

    public static void syncTemplateSelections(ServerPlayer player) {
        List<TemplateSelectionEntry> entries = StationTemplateSelectionManager.collect(player.serverLevel());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncStationTemplateSelectionsPacket(entries));
    }

    public static void syncTemplateSelections(ServerLevel level) {
        List<TemplateSelectionEntry> entries = StationTemplateSelectionManager.collect(level);
        SyncStationTemplateSelectionsPacket packet = new SyncStationTemplateSelectionsPacket(entries);
        for (ServerPlayer player : level.players()) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }
}
