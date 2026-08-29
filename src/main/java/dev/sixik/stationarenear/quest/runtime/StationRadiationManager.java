package dev.sixik.stationarenear.quest.runtime;

import dev.sixik.stationarenear.quest.world.QuestSavedData;
import dev.sixik.stationarenear.ship.data.ShipSystemType;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchor;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorSavedData;
import dev.sixik.stationarenear.ship.runtime.ShipManager;
import dev.sixik.stationarenear.structures.data.StationInstance;
import dev.sixik.stationarenear.structures.world.StationSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.TickEvent;

public final class StationRadiationManager {

    private StationRadiationManager() {
    }

    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        ServerLevel level = player.serverLevel();
        if (level.getGameTime() % 20L != 0L) {
            return;
        }

        if (player.isCreative() || player.isSpectator()) {
            return;
        }

        QuestSavedData questData = QuestSavedData.get(level);
        if (questData.completedQuestIds().size() < 5) {
            return;
        }

        BlockPos playerPos = player.blockPosition();
        StationSavedData stationData = StationSavedData.get(level);
        StationInstance nearbyStation = null;
        for (StationInstance station : stationData.stations()) {
            if (player.distanceToSqr(station.shuttleDoorCenter().getX(), station.shuttleDoorCenter().getY(), station.shuttleDoorCenter().getZ()) <= 160000.0D) {
                nearbyStation = station;
                break;
            }
            for (dev.sixik.stationarenear.structures.data.PlacedStationPiece piece : station.pieces()) {
                if (piece.bounds().isInside(playerPos)) {
                    nearbyStation = station;
                    break;
                }
            }
            if (nearbyStation != null) {
                break;
            }
        }

        if (nearbyStation == null) {
            return;
        }

        ShipDockingAnchorSavedData anchorData = ShipDockingAnchorSavedData.get(level);
        boolean protectedByShielding = false;
        for (ShipDockingAnchor anchor : anchorData.anchors()) {
            if (anchor.shipBounds().isInside(playerPos)) {
                if (ShipManager.state(level, anchor.terminalPos()).hasModule(ShipSystemType.RAD_SHIELDING)) {
                    protectedByShielding = true;
                    break;
                }
            }
        }

        if (protectedByShielding) {
            return;
        }

        for (ShipDockingAnchor anchor : anchorData.anchors()) {
            if (ShipManager.state(level, anchor.terminalPos()).hasModule(ShipSystemType.RAD_SHIELDING)) {
                if (player.distanceToSqr(anchor.terminalPos().getX(), anchor.terminalPos().getY(), anchor.terminalPos().getZ()) <= 160000.0D) {
                    protectedByShielding = true;
                    break;
                }
            }
        }

        if (protectedByShielding) {
            return;
        }

        player.hurt(level.damageSources().magic(), 1.5F);
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, false, false, true));
        player.displayClientMessage(Component.literal("§c[RADIATION HAZARD] High radiation detected! Radiation shielding module required!"), true);
    }
}
