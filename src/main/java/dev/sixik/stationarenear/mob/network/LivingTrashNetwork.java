package dev.sixik.stationarenear.mob.network;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.mob.network.packet.LivingTrashContainerHoverPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class LivingTrashNetwork {

    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(StationAreNear.MODID, "living_trash"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private static int nextPacketId;

    private LivingTrashNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(LivingTrashContainerHoverPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LivingTrashContainerHoverPacket::encode)
                .decoder(LivingTrashContainerHoverPacket::decode)
                .consumerMainThread(LivingTrashContainerHoverPacket::handle)
                .add();
    }

    public static void sendContainerSlotHover(BlockPos containerPos, int slotIndex) {
        CHANNEL.sendToServer(new LivingTrashContainerHoverPacket(containerPos, slotIndex));
    }
}
