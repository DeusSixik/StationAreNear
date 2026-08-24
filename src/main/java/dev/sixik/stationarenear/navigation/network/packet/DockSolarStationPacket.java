package dev.sixik.stationarenear.navigation.network.packet;

import dev.sixik.stationarenear.navigation.SolarNavigationConfig;
import dev.sixik.stationarenear.navigation.registry.SolarNavigationBlocks;
import dev.sixik.stationarenear.navigation.server.SolarNavigationControlManager;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorResolver;
import dev.sixik.stationarenear.ship.runtime.ShipManager;
import dev.sixik.stationarenear.navigation.world.SolarNavigationStationCleaner;
import dev.sixik.stationarenear.structures.generation.StationGenerationResult;
import dev.sixik.stationarenear.structures.generation.StationGenerationSettings;
import dev.sixik.stationarenear.structures.generation.StationGenerator;
import dev.sixik.stationarenear.structures.util.StationStructureIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record DockSolarStationPacket(BlockPos terminalPos, String stationName, long stationSeed, boolean quest, float stationX, float stationY) {

    private static final ResourceLocation DEFAULT_POOL = StationStructureIds.pool("space_station");
    private static final int MIN_ROOMS = 10;
    private static final int MAX_FLOORS = 1;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(terminalPos);
        buffer.writeUtf(stationName, 64);
        buffer.writeLong(stationSeed);
        buffer.writeBoolean(quest);
        buffer.writeFloat(stationX);
        buffer.writeFloat(stationY);
    }

    public static DockSolarStationPacket decode(FriendlyByteBuf buffer) {
        return new DockSolarStationPacket(buffer.readBlockPos(), buffer.readUtf(64), buffer.readLong(), buffer.readBoolean(), buffer.readFloat(), buffer.readFloat());
    }

    public static void handle(DockSolarStationPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                handleDock(player, packet);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleDock(ServerPlayer player, DockSolarStationPacket packet) {
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(packet.terminalPos())) {
            player.displayClientMessage(Component.literal("Navigation terminal is not loaded."), false);
            return;
        }

        BlockState state = level.getBlockState(packet.terminalPos());
        if (!state.is(SolarNavigationBlocks.SOLAR_NAVIGATION_TERMINAL.get())) {
            player.displayClientMessage(Component.literal("Navigation terminal is missing."), false);
            return;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(packet.terminalPos())) > 96.0D) {
            player.displayClientMessage(Component.literal("Too far away from navigation terminal."), false);
            return;
        }

        if (SolarNavigationStationCleaner.hasDockedStation(level, packet.terminalPos(), packet.stationSeed())) {
            SolarNavigationControlManager.forceStop(level, packet.terminalPos());
            ShipManager.setDocking(level, packet.terminalPos(), true);
            player.displayClientMessage(Component.literal("Already docked with " + packet.stationName() + "."), false);
            return;
        }

        int clearedOldStations = SolarNavigationStationCleaner.clearByTerminal(level, packet.terminalPos());
        if (clearedOldStations > 0) {
            ShipManager.setDocking(level, packet.terminalPos(), false);
        }

        ShipDockingAnchorResolver.ResolvedDockingAnchor dockingAnchor = ShipDockingAnchorResolver.resolve(level, packet.terminalPos(), state);
        Direction stationDirection = dockingAnchor.stationDirection();
        BlockPos doorCenter = dockingAnchor.doorCenter();
        long generationSeed = packet.stationSeed() ^ level.getSeed() ^ Mth.getSeed(packet.terminalPos());
        float missionDanger = packet.quest() ? 0.55F : 0.35F;

        int maxRooms = Math.max(MIN_ROOMS, SolarNavigationConfig.DUNGEON_PREVIEW_MAX_ROOMS.get() + 8);
        StationGenerationResult result = new StationGenerator().generateDockedStation(
                level,
                doorCenter,
                stationDirection,
                new StationGenerationSettings(DEFAULT_POOL, missionDanger, true, MAX_FLOORS, MIN_ROOMS, maxRooms, generationSeed)
        );

        if (!result.success()) {
            player.displayClientMessage(Component.literal("Dock failed with " + packet.stationName() + ": " + result.message()), false);
            return;
        }

        result.station().ifPresent(station -> {
            station.customData().putLong(SolarNavigationStationCleaner.KEY_NAVIGATION_TERMINAL_POS, packet.terminalPos().asLong());
            station.customData().putLong(SolarNavigationStationCleaner.KEY_NAVIGATION_STATION_SEED, packet.stationSeed());
            station.customData().putString(SolarNavigationStationCleaner.KEY_NAVIGATION_STATION_NAME, packet.stationName());
            station.customData().putFloat(SolarNavigationStationCleaner.KEY_NAVIGATION_STATION_X, packet.stationX());
            station.customData().putFloat(SolarNavigationStationCleaner.KEY_NAVIGATION_STATION_Y, packet.stationY());
            station.customData().putBoolean("navigationShipAnchorBound", dockingAnchor.boundToShip());
            station.customData().putString("navigationShipConnection", dockingAnchor.connectionName());
            dev.sixik.stationarenear.structures.world.StationSavedData.get(level).addStation(station);
        });
        SolarNavigationControlManager.forceStop(level, packet.terminalPos());
        ShipManager.setDocking(level, packet.terminalPos(), true);

        int pieces = result.station().map(station -> station.pieces().size()).orElse(0);
        String cleanupMessage = clearedOldStations > 0 ? " cleared old=" + clearedOldStations : "";
        player.displayClientMessage(Component.literal("Docked with " + packet.stationName() + ": generated " + pieces + " station pieces." + cleanupMessage), false);
    }
}
