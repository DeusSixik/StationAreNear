package dev.sixik.stationarenear.sam.network;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.sam.SamVoice;
import dev.sixik.stationarenear.sam.network.packet.PlaySamSpeechPacket;
import dev.sixik.stationarenear.sam.network.packet.StopSamSpeechPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class SamNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final double SPEECH_RANGE = 64.0D;
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(StationAreNear.MODID, "sam"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private static int nextPacketId;

    private SamNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(PlaySamSpeechPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlaySamSpeechPacket::encode)
                .decoder(PlaySamSpeechPacket::decode)
                .consumerMainThread(PlaySamSpeechPacket::handle)
                .add();
        CHANNEL.messageBuilder(StopSamSpeechPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StopSamSpeechPacket::encode)
                .decoder(StopSamSpeechPacket::decode)
                .consumerMainThread(StopSamSpeechPacket::handle)
                .add();
    }

    public static void play(ServerLevel level, Vec3 position, String text, SamVoice voice) {
        PlaySamSpeechPacket packet = new PlaySamSpeechPacket(position.x(), position.y(), position.z(), text, voice);
        CHANNEL.send(PacketDistributor.NEAR.with(PacketDistributor.TargetPoint.p(
                position.x(),
                position.y(),
                position.z(),
                SPEECH_RANGE,
                level.dimension()
        )), packet);
    }

    public static void stop(ServerLevel level, Vec3 position) {
        StopSamSpeechPacket packet = new StopSamSpeechPacket();
        CHANNEL.send(PacketDistributor.NEAR.with(PacketDistributor.TargetPoint.p(
                position.x(),
                position.y(),
                position.z(),
                SPEECH_RANGE,
                level.dimension()
        )), packet);
    }

    public static void stop(ServerLevel level) {
        StopSamSpeechPacket packet = new StopSamSpeechPacket();
        CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), packet);
    }
}
