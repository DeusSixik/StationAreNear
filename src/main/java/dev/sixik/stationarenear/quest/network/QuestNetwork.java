package dev.sixik.stationarenear.quest.network;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.quest.network.packet.QuestFurniturePickupHoldPacket;
import dev.sixik.stationarenear.quest.network.packet.QuestFurniturePickupProgressPacket;
import dev.sixik.stationarenear.quest.network.packet.RepairDoorCompletePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class QuestNetwork {

    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(StationAreNear.MODID, "quest"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private static int nextPacketId;

    private QuestNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(QuestFurniturePickupHoldPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(QuestFurniturePickupHoldPacket::encode)
                .decoder(QuestFurniturePickupHoldPacket::decode)
                .consumerMainThread(QuestFurniturePickupHoldPacket::handle)
                .add();
        CHANNEL.messageBuilder(QuestFurniturePickupProgressPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(QuestFurniturePickupProgressPacket::encode)
                .decoder(QuestFurniturePickupProgressPacket::decode)
                .consumerMainThread(QuestFurniturePickupProgressPacket::handle)
                .add();
        CHANNEL.messageBuilder(dev.sixik.stationarenear.quest.network.packet.RepairDoorCompletePacket.class, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RepairDoorCompletePacket::encode)
                .decoder(RepairDoorCompletePacket::decode)
                .consumerMainThread(RepairDoorCompletePacket::handle)
                .add();
        CHANNEL.messageBuilder(dev.sixik.stationarenear.quest.network.packet.RepairEnergyPanelCompletePacket.class, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(dev.sixik.stationarenear.quest.network.packet.RepairEnergyPanelCompletePacket::encode)
                .decoder(dev.sixik.stationarenear.quest.network.packet.RepairEnergyPanelCompletePacket::decode)
                .consumerMainThread(dev.sixik.stationarenear.quest.network.packet.RepairEnergyPanelCompletePacket::handle)
                .add();
        CHANNEL.messageBuilder(dev.sixik.stationarenear.quest.network.packet.RepairWallPanelCompletePacket.class, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(dev.sixik.stationarenear.quest.network.packet.RepairWallPanelCompletePacket::encode)
                .decoder(dev.sixik.stationarenear.quest.network.packet.RepairWallPanelCompletePacket::decode)
                .consumerMainThread(dev.sixik.stationarenear.quest.network.packet.RepairWallPanelCompletePacket::handle)
                .add();
    }

    public static void sendRepairDoor(BlockPos pos) {
        CHANNEL.sendToServer(new dev.sixik.stationarenear.quest.network.packet.RepairDoorCompletePacket(pos));
    }

    public static void sendRepairEnergyPanel(BlockPos pos) {
        CHANNEL.sendToServer(new dev.sixik.stationarenear.quest.network.packet.RepairEnergyPanelCompletePacket(pos));
    }

    public static void sendRepairWallPanel(BlockPos pos) {
        CHANNEL.sendToServer(new dev.sixik.stationarenear.quest.network.packet.RepairWallPanelCompletePacket(pos));
    }

    public static void sendFurniturePickupHold(BlockPos pos, boolean holding) {
        CHANNEL.sendToServer(new QuestFurniturePickupHoldPacket(pos, holding));
    }

    public static void syncFurniturePickupProgress(ServerPlayer player, float progress, boolean visible) {
        syncFurniturePickupProgress(player, progress, visible, "");
    }

    public static void syncFurniturePickupProgress(ServerPlayer player, float progress, boolean visible, String title) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new QuestFurniturePickupProgressPacket(progress, visible, title));
    }
}
