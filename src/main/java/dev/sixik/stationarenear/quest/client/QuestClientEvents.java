package dev.sixik.stationarenear.quest.client;

import dev.sixik.stationarenear.quest.block.QuestPickupBlock;
import dev.sixik.stationarenear.quest.network.QuestNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

public final class QuestClientEvents {

    private static BlockHitResult activePickupHit;

    private QuestClientEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(QuestClientEvents::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(QuestClientEvents::onClientLogout);
        MinecraftForge.EVENT_BUS.addListener(QuestFurniturePickupOverlay::onRenderGui);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            stopHolding();
            return;
        }
        if (minecraft.screen != null || !minecraft.options.keyUse.isDown() || minecraft.hitResult == null || minecraft.hitResult.getType() != HitResult.Type.BLOCK) {
            stopHolding();
            return;
        }

        BlockHitResult hit = (BlockHitResult) minecraft.hitResult;
        BlockState state = minecraft.level.getBlockState(hit.getBlockPos());
        if (!(state.getBlock() instanceof QuestPickupBlock)) {
            stopHolding();
            return;
        }

        activePickupHit = hit;
        QuestNetwork.sendFurniturePickupHold(hit.getBlockPos(), true);
    }

    private static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        activePickupHit = null;
        QuestFurniturePickupOverlay.hide();
    }

    private static void stopHolding() {
        if (activePickupHit != null) {
            if (Minecraft.getInstance().getConnection() != null) {
                QuestNetwork.sendFurniturePickupHold(activePickupHit.getBlockPos(), false);
            }
            activePickupHit = null;
        }
        QuestFurniturePickupOverlay.hide();
    }
}
