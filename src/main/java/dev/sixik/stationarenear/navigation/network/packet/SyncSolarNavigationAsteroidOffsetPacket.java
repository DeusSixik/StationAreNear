package dev.sixik.stationarenear.navigation.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SyncSolarNavigationAsteroidOffsetPacket(
        BlockPos terminalPos,
        long asteroidSeed,
        float offsetX,
        float offsetY,
        float velocityX,
        float velocityY
) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(terminalPos);
        buffer.writeLong(asteroidSeed);
        buffer.writeFloat(offsetX);
        buffer.writeFloat(offsetY);
        buffer.writeFloat(velocityX);
        buffer.writeFloat(velocityY);
    }

    public static SyncSolarNavigationAsteroidOffsetPacket decode(FriendlyByteBuf buffer) {
        return new SyncSolarNavigationAsteroidOffsetPacket(
                buffer.readBlockPos(),
                buffer.readLong(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat()
        );
    }

    public static void handle(SyncSolarNavigationAsteroidOffsetPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                dev.sixik.stationarenear.navigation.SolarNavigationScreen.syncAsteroidOffset(
                        packet.terminalPos(),
                        packet.asteroidSeed(),
                        packet.offsetX(),
                        packet.offsetY(),
                        packet.velocityX(),
                        packet.velocityY()
                )
        ));
        context.setPacketHandled(true);
    }
}
