package dev.sixik.stationarenear.navigation.network.packet;

import dev.sixik.stationarenear.navigation.data.SolarNavigationQuestMarker;
import dev.sixik.stationarenear.navigation.data.SolarNavigationShipState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record OpenSolarNavigationPacket(long seed, BlockPos terminalPos, SolarNavigationShipState shipState, List<SolarNavigationQuestMarker> questMarkers) {

    public OpenSolarNavigationPacket {
        questMarkers = List.copyOf(questMarkers);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeLong(seed);
        buffer.writeBlockPos(terminalPos);
        shipState.encode(buffer);
        buffer.writeVarInt(questMarkers.size());
        for (SolarNavigationQuestMarker marker : questMarkers) {
            marker.encode(buffer);
        }
    }

    public static OpenSolarNavigationPacket decode(FriendlyByteBuf buffer) {
        long seed = buffer.readLong();
        BlockPos terminalPos = buffer.readBlockPos();
        SolarNavigationShipState shipState = SolarNavigationShipState.decode(buffer);
        int markerCount = buffer.readVarInt();
        List<SolarNavigationQuestMarker> questMarkers = new ArrayList<>(markerCount);
        for (int i = 0; i < markerCount; i++) {
            questMarkers.add(SolarNavigationQuestMarker.decode(buffer));
        }
        return new OpenSolarNavigationPacket(seed, terminalPos, shipState, questMarkers);
    }

    public static void handle(OpenSolarNavigationPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                dev.sixik.stationarenear.navigation.SolarNavigationScreen.openGui(packet.seed(), packet.terminalPos(), packet.shipState(), packet.questMarkers())
        ));
        context.setPacketHandled(true);
    }
}
