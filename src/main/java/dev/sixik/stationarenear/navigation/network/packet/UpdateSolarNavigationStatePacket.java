package dev.sixik.stationarenear.navigation.network.packet;

import dev.sixik.stationarenear.navigation.SolarNavigationConfig;
import dev.sixik.stationarenear.navigation.block.SolarNavigationTerminalBlock;
import dev.sixik.stationarenear.navigation.data.SolarNavigationShipState;
import dev.sixik.stationarenear.navigation.registry.SolarNavigationBlocks;
import dev.sixik.stationarenear.navigation.world.SolarNavigationSavedData;
import dev.sixik.stationarenear.navigation.world.SolarNavigationStationCleaner;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record UpdateSolarNavigationStatePacket(BlockPos terminalPos, SolarNavigationShipState state) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(terminalPos);
        state.encode(buffer);
    }

    public static UpdateSolarNavigationStatePacket decode(FriendlyByteBuf buffer) {
        return new UpdateSolarNavigationStatePacket(buffer.readBlockPos(), SolarNavigationShipState.decode(buffer));
    }

    public static void handle(UpdateSolarNavigationStatePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                saveState(player, packet);
            }
        });
        context.setPacketHandled(true);
    }

    private static void saveState(ServerPlayer player, UpdateSolarNavigationStatePacket packet) {
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
        SolarNavigationSavedData.get(level).shipState(packet.terminalPos(), packet.state());
        SolarNavigationStationCleaner.clearFarFromShip(level, packet.terminalPos(), packet.state(), SolarNavigationConfig.STATION_UNLOAD_DISTANCE.get().floatValue());
    }
}
