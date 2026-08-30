package dev.sixik.stationarenear.navigation.network.packet;

import dev.sixik.stationarenear.navigation.client.StationDockingOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SyncStationDockingOverlayPacket(String stationName, String stationCode, int durationSeconds) {

    public static void encode(SyncStationDockingOverlayPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.stationName);
        buf.writeUtf(packet.stationCode);
        buf.writeVarInt(packet.durationSeconds);
    }

    public static SyncStationDockingOverlayPacket decode(FriendlyByteBuf buf) {
        return new SyncStationDockingOverlayPacket(buf.readUtf(), buf.readUtf(), buf.readVarInt());
    }

    public static void handle(SyncStationDockingOverlayPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> StationDockingOverlay.show(
                packet.stationName(),
                packet.stationCode(),
                packet.durationSeconds() * 1000L
        )));
        context.setPacketHandled(true);
    }
}
