package dev.sixik.stationarenear.navigation.network.packet;

import dev.sixik.stationarenear.navigation.data.SolarNavigationShipState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SyncSolarNavigationStatePacket(BlockPos terminalPos, SolarNavigationShipState state) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(terminalPos);
        state.encode(buffer);
    }

    public static SyncSolarNavigationStatePacket decode(FriendlyByteBuf buffer) {
        return new SyncSolarNavigationStatePacket(buffer.readBlockPos(), SolarNavigationShipState.decode(buffer));
    }

    public static void handle(SyncSolarNavigationStatePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                dev.sixik.stationarenear.navigation.SolarNavigationScreen.syncShipState(packet.terminalPos(), packet.state())
        ));
        context.setPacketHandled(true);
    }
}
