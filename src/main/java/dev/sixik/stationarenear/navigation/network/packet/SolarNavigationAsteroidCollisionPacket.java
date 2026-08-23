package dev.sixik.stationarenear.navigation.network.packet;

import dev.sixik.stationarenear.navigation.block.SolarNavigationTerminalBlock;
import dev.sixik.stationarenear.navigation.data.SolarNavigationShipState;
import dev.sixik.stationarenear.navigation.event.SolarNavigationAsteroidCollisionEvent;
import dev.sixik.stationarenear.navigation.registry.SolarNavigationBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SolarNavigationAsteroidCollisionPacket(
        BlockPos terminalPos,
        SolarNavigationShipState shipState,
        long asteroidSeed,
        float asteroidX,
        float asteroidY,
        float asteroidRadius,
        float impactSpeed
) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(terminalPos);
        shipState.encode(buffer);
        buffer.writeLong(asteroidSeed);
        buffer.writeFloat(asteroidX);
        buffer.writeFloat(asteroidY);
        buffer.writeFloat(asteroidRadius);
        buffer.writeFloat(impactSpeed);
    }

    public static SolarNavigationAsteroidCollisionPacket decode(FriendlyByteBuf buffer) {
        return new SolarNavigationAsteroidCollisionPacket(
                buffer.readBlockPos(),
                SolarNavigationShipState.decode(buffer),
                buffer.readLong(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat()
        );
    }

    public static void handle(SolarNavigationAsteroidCollisionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                postEvent(player, packet);
            }
        });
        context.setPacketHandled(true);
    }

    private static void postEvent(ServerPlayer player, SolarNavigationAsteroidCollisionPacket packet) {
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(packet.terminalPos())) {
            return;
        }
        BlockState state = level.getBlockState(packet.terminalPos());
        if (!state.is(SolarNavigationBlocks.SOLAR_NAVIGATION_TERMINAL.get()) || !state.hasProperty(SolarNavigationTerminalBlock.FACING)) {
            return;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(packet.terminalPos())) > 256.0D) {
            return;
        }

        MinecraftForge.EVENT_BUS.post(new SolarNavigationAsteroidCollisionEvent(
                player,
                packet.terminalPos(),
                packet.shipState(),
                packet.asteroidSeed(),
                packet.asteroidX(),
                packet.asteroidY(),
                packet.asteroidRadius(),
                packet.impactSpeed()
        ));
    }
}
