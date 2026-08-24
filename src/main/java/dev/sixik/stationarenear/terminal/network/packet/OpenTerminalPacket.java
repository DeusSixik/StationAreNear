package dev.sixik.stationarenear.terminal.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record OpenTerminalPacket(BlockPos terminalPos) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(terminalPos);
    }

    public static OpenTerminalPacket decode(FriendlyByteBuf buffer) {
        return new OpenTerminalPacket(buffer.readBlockPos());
    }

    public static void handle(OpenTerminalPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                dev.sixik.stationarenear.terminal.client.RetroTerminalScreen.open()
        ));
        context.setPacketHandled(true);
    }
}
