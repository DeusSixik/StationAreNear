package dev.sixik.stationarenear.terminal.network.packet;

import dev.sixik.stationarenear.terminal.map.data.StationMapSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record OpenStationMapPacket(StationMapSnapshot snapshot, boolean returnToTerminal) {

    public void encode(FriendlyByteBuf buffer) {
        snapshot.encode(buffer);
        buffer.writeBoolean(returnToTerminal);
    }

    public static OpenStationMapPacket decode(FriendlyByteBuf buffer) {
        return new OpenStationMapPacket(StationMapSnapshot.decode(buffer), buffer.readBoolean());
    }

    public static void handle(OpenStationMapPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                dev.sixik.stationarenear.terminal.client.StationMapScreen.open(dev.sixik.stationarenear.terminal.client.StationMapScreen.OpenParams.snapshot(packet.snapshot()).returnToTerminal(packet.returnToTerminal()))
        ));
        context.setPacketHandled(true);
    }
}
