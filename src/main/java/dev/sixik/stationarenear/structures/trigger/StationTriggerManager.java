package dev.sixik.stationarenear.structures.trigger;

import dev.sixik.stationarenear.structures.data.PlacedStationPiece;
import dev.sixik.stationarenear.structures.data.PlacedTriggerZone;
import dev.sixik.stationarenear.structures.data.StationInstance;
import dev.sixik.stationarenear.structures.world.StationSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class StationTriggerManager {

    private static final Map<UUID, Set<String>> ACTIVE_ZONES_BY_PLAYER = new HashMap<>();

    private StationTriggerManager() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(StationTriggerManager::onPlayerTick);
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        StationSavedData stationData = StationSavedData.get(level);
        Set<String> previousActiveZones = ACTIVE_ZONES_BY_PLAYER.getOrDefault(player.getUUID(), Set.of());
        Set<String> currentActiveZones = new HashSet<>();
        BlockPos playerPos = player.blockPosition();

        for (StationInstance station : stationData.stations()) {
            for (PlacedStationPiece piece : station.pieces()) {
                for (PlacedTriggerZone zone : piece.triggerZones()) {
                    if (!contains(zone, playerPos)) {
                        continue;
                    }

                    String activeKey = station.id() + ":" + piece.definitionId() + ":" + zone.id();
                    currentActiveZones.add(activeKey);
                    boolean firstPlayerEnter = !previousActiveZones.contains(activeKey);
                    if (!firstPlayerEnter) {
                        continue;
                    }

                    boolean firstGlobalActivation = stationData.markTriggerActivated(station.id(), piece.definitionId() + ":" + zone.id());
                    MinecraftForge.EVENT_BUS.post(new StationTriggerEvent(level, player, station, zone, true, firstGlobalActivation));
                }
            }
        }

        if (currentActiveZones.isEmpty()) {
            ACTIVE_ZONES_BY_PLAYER.remove(player.getUUID());
        } else {
            ACTIVE_ZONES_BY_PLAYER.put(player.getUUID(), currentActiveZones);
        }
    }

    private static boolean contains(PlacedTriggerZone zone, BlockPos pos) {
        return pos.getX() >= zone.min().getX()
                && pos.getY() >= zone.min().getY()
                && pos.getZ() >= zone.min().getZ()
                && pos.getX() <= zone.max().getX()
                && pos.getY() <= zone.max().getY()
                && pos.getZ() <= zone.max().getZ();
    }
}
