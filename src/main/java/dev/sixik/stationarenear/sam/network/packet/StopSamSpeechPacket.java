package dev.sixik.stationarenear.sam.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record StopSamSpeechPacket() {

    public void encode(FriendlyByteBuf buffer) {
    }

    public static StopSamSpeechPacket decode(FriendlyByteBuf buffer) {
        return new StopSamSpeechPacket();
    }

    public static void handle(StopSamSpeechPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                dev.sixik.stationarenear.sam.client.SamClientAudio.stop()
        ));
        context.setPacketHandled(true);
    }
}
