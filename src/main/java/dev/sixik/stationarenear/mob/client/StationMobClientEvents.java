package dev.sixik.stationarenear.mob.client;

import dev.sixik.stationarenear.mob.network.LivingTrashNetwork;
import dev.sixik.stationarenear.mob.entity.LivingTrashEntity;
import dev.sixik.stationarenear.mob.registry.StationMobEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.Slot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class StationMobClientEvents {

    private static int hoverCooldown;
    private static LivingTrashEntity slotPreviewEntity;
    private static Screen trackedContainerScreen;
    private static BlockPos trackedContainerPos;

    private StationMobClientEvents() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(StationMobClientEvents::onClientSetup);
        MinecraftForge.EVENT_BUS.addListener(StationMobClientEvents::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(StationMobClientEvents::onScreenRenderPost);
        MinecraftForge.EVENT_BUS.addListener(StationMobClientEvents::onClientLogout);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            EntityRenderers.register(StationMobEntities.LIVING_TRASH.get(), LivingTrashRenderer::new);
            EntityRenderers.register(StationMobEntities.CADAVER.get(), CadaverRenderer::new);
        });
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (hoverCooldown > 0) {
            hoverCooldown--;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof AbstractContainerScreen<?> screen) || minecraft.player == null) {
            trackedContainerScreen = null;
            trackedContainerPos = null;
            return;
        }
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || hoverCooldown > 0 || slot.container == minecraft.player.getInventory()) {
            return;
        }
        Optional<BlockPos> containerPos = activeContainerPos(minecraft, screen);
        if (containerPos.isEmpty()) {
            return;
        }

        hoverCooldown = 8;
        LivingTrashNetwork.sendContainerSlotHover(containerPos.get(), slot.getSlotIndex());
    }

    private static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen) || minecraft.player == null || minecraft.level == null) {
            return;
        }

        LivingTrashEntity preview = slotPreviewEntity(minecraft);
        if (preview == null) {
            return;
        }
        Optional<BlockPos> containerPos = activeContainerPos(minecraft, screen);
        if (containerPos.isEmpty()) {
            return;
        }

        List<LivingTrashEntity> hiddenTrash = minecraft.level.getEntitiesOfClass(
                LivingTrashEntity.class,
                minecraft.player.getBoundingBox().inflate(16.0D),
                trash -> trash.isHiding() && trash.containerPos().filter(containerPos.get()::equals).isPresent()
        );
        if (hiddenTrash.isEmpty()) {
            return;
        }

        for (LivingTrashEntity trash : hiddenTrash) {
            int hiddenSlot = trash.hiddenSlot();
            if (hiddenSlot < 0) {
                continue;
            }
            for (Slot slot : screen.getMenu().slots) {
                if (slot.container == minecraft.player.getInventory() || slot.getSlotIndex() != hiddenSlot) {
                    continue;
                }
                int x = screen.getGuiLeft() + slot.x + 8;
                int y = screen.getGuiTop() + slot.y + 14;
                preview.tickCount = trash.tickCount;
                InventoryScreen.renderEntityInInventoryFollowsAngle(event.getGuiGraphics(), x, y, 13, -15.0F, 0.0F, preview);
                break;
            }
        }
    }

    private static Optional<BlockPos> activeContainerPos(Minecraft minecraft, AbstractContainerScreen<?> screen) {
        if (minecraft.level == null || minecraft.player == null) {
            trackedContainerScreen = null;
            trackedContainerPos = null;
            return Optional.empty();
        }
        if (trackedContainerScreen != screen) {
            trackedContainerScreen = screen;
            trackedContainerPos = lookedAtContainerPos(minecraft).orElse(null);
        }
        if (isValidActiveContainer(minecraft, trackedContainerPos)) {
            return Optional.of(trackedContainerPos);
        }

        Optional<BlockPos> fallback = singleHiddenContainerNearPlayer(minecraft);
        trackedContainerPos = fallback.orElse(null);
        return fallback;
    }

    private static Optional<BlockPos> lookedAtContainerPos(Minecraft minecraft) {
        if (!(minecraft.hitResult instanceof BlockHitResult hitResult) || hitResult.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        BlockPos pos = hitResult.getBlockPos();
        if (!isContainer(minecraft, pos)) {
            return Optional.empty();
        }
        return Optional.of(pos.immutable());
    }

    private static Optional<BlockPos> singleHiddenContainerNearPlayer(Minecraft minecraft) {
        List<BlockPos> containers = new ArrayList<>();
        List<LivingTrashEntity> hiddenTrash = minecraft.level.getEntitiesOfClass(
                LivingTrashEntity.class,
                minecraft.player.getBoundingBox().inflate(8.0D),
                LivingTrashEntity::isHiding
        );
        for (LivingTrashEntity trash : hiddenTrash) {
            Optional<BlockPos> containerPos = trash.containerPos();
            if (containerPos.isEmpty() || !isValidActiveContainer(minecraft, containerPos.get())) {
                continue;
            }
            if (!containers.contains(containerPos.get())) {
                containers.add(containerPos.get().immutable());
            }
        }
        return containers.size() == 1 ? Optional.of(containers.get(0)) : Optional.empty();
    }

    private static boolean isValidActiveContainer(Minecraft minecraft, BlockPos pos) {
        return pos != null
                && minecraft.level != null
                && minecraft.player != null
                && minecraft.player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D
                && isContainer(minecraft, pos);
    }

    private static boolean isContainer(Minecraft minecraft, BlockPos pos) {
        BlockEntity blockEntity = minecraft.level.getBlockEntity(pos);
        return blockEntity instanceof Container;
    }

    private static LivingTrashEntity slotPreviewEntity(Minecraft minecraft) {
        if (minecraft.level == null) {
            slotPreviewEntity = null;
            return null;
        }
        if (slotPreviewEntity == null || slotPreviewEntity.level() != minecraft.level) {
            slotPreviewEntity = new LivingTrashEntity(StationMobEntities.LIVING_TRASH.get(), minecraft.level);
        }
        return slotPreviewEntity;
    }

    private static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        hoverCooldown = 0;
        slotPreviewEntity = null;
        trackedContainerScreen = null;
        trackedContainerPos = null;
    }
}
