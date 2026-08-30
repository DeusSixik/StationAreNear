package dev.sixik.stationarenear.quest.client;

import dev.sixik.stationarenear.quest.block.QuestPickupBlock;
import dev.sixik.stationarenear.quest.network.QuestNetwork;
import dev.sixik.stationarenear.quest.registry.QuestBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class QuestClientEvents {

    private static BlockHitResult activePickupHit;

    private QuestClientEvents() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(QuestClientEvents::onClientSetup);
        MinecraftForge.EVENT_BUS.addListener(QuestClientEvents::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(QuestClientEvents::onClientLogout);
        MinecraftForge.EVENT_BUS.addListener(QuestFurniturePickupOverlay::onRenderGui);
        MinecraftForge.EVENT_BUS.addListener(QuestPlacementZoneRenderer::onRenderLevelStage);
        MinecraftForge.EVENT_BUS.addListener(QuestClientEvents::onRenderInventoryEffects);
    }

    private static void onRenderInventoryEffects(net.minecraftforge.client.event.ScreenEvent.RenderInventoryMobEffects event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        boolean hasVisibleEffects = minecraft.player.getActiveEffects().stream()
                .anyMatch(net.minecraft.world.effect.MobEffectInstance::showIcon);
        if (!hasVisibleEffects) {
            event.setCanceled(true);
        }
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(QuestBlocks.OXYGEN_PANEL.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(QuestBlocks.ENERGY_PANEL.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(QuestBlocks.GRAVITATION_PANEL.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(QuestBlocks.WORKBENCH.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(QuestBlocks.FRIDGE.get(), RenderType.cutout());
        });
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
        boolean isPickup = state.getBlock() instanceof QuestPickupBlock;
        boolean isEnergyPanel = state.is(QuestBlocks.ENERGY_PANEL.get())
                && state.hasProperty(dev.sixik.stationarenear.quest.block.EnergyPanelBlock.BROKEN)
                && !state.getValue(dev.sixik.stationarenear.quest.block.EnergyPanelBlock.BROKEN);

        if (!isPickup && !isEnergyPanel) {
            stopHolding();
            return;
        }

        activePickupHit = hit;
        QuestNetwork.sendFurniturePickupHold(hit.getBlockPos(), true);
    }

    private static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        activePickupHit = null;
        QuestFurniturePickupOverlay.hide();
        QuestPlacementZoneClientState.clear();
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
