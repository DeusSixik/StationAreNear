package dev.sixik.stationarenear.sam.network.packet;

import dev.sixik.stationarenear.sam.SamTextSanitizer;
import dev.sixik.stationarenear.sam.SamVoice;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PlaySamSpeechPacket(double x, double y, double z, String text, SamVoice voice) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeUtf(text, SamTextSanitizer.MAX_NETWORK_TEXT_LENGTH);
        voice.encode(buffer);
    }

    public static PlaySamSpeechPacket decode(FriendlyByteBuf buffer) {
        double x = buffer.readDouble();
        double y = buffer.readDouble();
        double z = buffer.readDouble();
        String text = buffer.readUtf(SamTextSanitizer.MAX_NETWORK_TEXT_LENGTH);
        SamVoice voice = SamVoice.decode(buffer);
        return new PlaySamSpeechPacket(x, y, z, text, voice);
    }

    public static void handle(PlaySamSpeechPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                dev.sixik.stationarenear.sam.client.SamClientAudio.play(packet.text(), packet.voice(), new Vec3(packet.x(), packet.y(), packet.z()))
        ));
        context.setPacketHandled(true);
    }
}
