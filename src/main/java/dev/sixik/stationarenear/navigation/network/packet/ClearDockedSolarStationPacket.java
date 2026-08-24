package dev.sixik.stationarenear.navigation.network.packet;

import dev.sixik.stationarenear.navigation.block.SolarNavigationTerminalBlock;
import dev.sixik.stationarenear.navigation.registry.SolarNavigationBlocks;
import dev.sixik.stationarenear.navigation.world.SolarNavigationStationCleaner;
import dev.sixik.stationarenear.ship.runtime.ShipManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ClearDockedSolarStationPacket(BlockPos terminalPos, String stationName, String stationCode, long stationSeed) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(terminalPos);
        buffer.writeUtf(stationName, 128);
        buffer.writeUtf(stationCode, 32);
        buffer.writeLong(stationSeed);
    }

    public static ClearDockedSolarStationPacket decode(FriendlyByteBuf buffer) {
        return new ClearDockedSolarStationPacket(buffer.readBlockPos(), buffer.readUtf(128), buffer.readUtf(32), buffer.readLong());
    }

    public static void handle(ClearDockedSolarStationPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                clearStation(player, packet);
            }
        });
        context.setPacketHandled(true);
    }

    private static void clearStation(ServerPlayer player, ClearDockedSolarStationPacket packet) {
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

        int cleared = SolarNavigationStationCleaner.clearByNavigationSeed(level, packet.stationSeed());
        if (cleared > 0) {
            ShipManager.setDocking(level, packet.terminalPos(), false);
            player.displayClientMessage(Component.literal("Undocked from " + packet.stationCode() + ": cleared generated station."), false);
        }
    }
}
