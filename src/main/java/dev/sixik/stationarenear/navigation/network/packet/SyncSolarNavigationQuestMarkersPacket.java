package dev.sixik.stationarenear.navigation.network.packet;

import dev.sixik.stationarenear.navigation.data.SolarNavigationQuestMarker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record SyncSolarNavigationQuestMarkersPacket(List<SolarNavigationQuestMarker> questMarkers) {

    public SyncSolarNavigationQuestMarkersPacket {
        questMarkers = List.copyOf(questMarkers);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(questMarkers.size());
        for (SolarNavigationQuestMarker marker : questMarkers) {
            marker.encode(buffer);
        }
    }

    public static SyncSolarNavigationQuestMarkersPacket decode(FriendlyByteBuf buffer) {
        int markerCount = buffer.readVarInt();
        List<SolarNavigationQuestMarker> questMarkers = new ArrayList<>(markerCount);
        for (int i = 0; i < markerCount; i++) {
            questMarkers.add(SolarNavigationQuestMarker.decode(buffer));
        }
        return new SyncSolarNavigationQuestMarkersPacket(questMarkers);
    }

    public static void handle(SyncSolarNavigationQuestMarkersPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                dev.sixik.stationarenear.navigation.SolarNavigationScreen.syncQuestMarkers(packet.questMarkers())
        ));
        context.setPacketHandled(true);
    }
}
