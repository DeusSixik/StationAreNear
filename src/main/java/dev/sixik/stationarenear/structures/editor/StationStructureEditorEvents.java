package dev.sixik.stationarenear.structures.editor;

import dev.sixik.stationarenear.structures.client.StationEditorClientState;
import dev.sixik.stationarenear.structures.item.StationStructureToolItem;
import dev.sixik.stationarenear.structures.network.StationStructureNetwork;
import dev.sixik.stationarenear.structures.network.packet.OpenStationZoneEditorPacket;
import dev.sixik.stationarenear.structures.util.NbtPos;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

public final class StationStructureEditorEvents {

    private StationStructureEditorEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(StationStructureEditorEvents::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(StationStructureEditorEvents::onRightClickItem);
        MinecraftForge.EVENT_BUS.addListener(StationStructureEditorEvents::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(StationStructureEditorEvents::onPlayerChangedDimension);
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StationStructureNetwork.syncTemplateSelections(player);
        }
    }

    private static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StationStructureNetwork.syncTemplateSelections(player);
        }
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!StationStructureEditorStick.isEditorTool(stack)) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        handleModeClick(player, stack, event.getPos(), player.isShiftKeyDown());
    }

    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (!StationStructureEditorStick.isEditorTool(stack)) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (player.isShiftKeyDown()) {
            StationStructureNetwork.openTemplateMenu(player);
            return;
        }

        if (!StationStructureEditorStick.hasSelection(stack)) {
            player.displayClientMessage(Component.literal("Select pos1 with Right Click and pos2 with Shift+Right Click"), true);
            return;
        }

        StationStructureNetwork.sendOpenEditor(player, OpenStationZoneEditorPacket.fromStack(stack));
    }

    private static void handleModeClick(ServerPlayer player, ItemStack stack, BlockPos clickedPos, boolean shiftDown) {
        switch (StationStructureEditorStick.mode(stack)) {
            case ZONE_SELECTION -> handleZoneSelection(player, stack, clickedPos, shiftDown);
            case TRIGGER_MANAGER_CREATE -> handleTriggerCreate(player, stack, clickedPos, shiftDown);
            case TRIGGER_MANAGER_EDIT -> handleTriggerEdit(player, stack, clickedPos);
            case CONNECTION_MANAGER -> handleConnectionManager(player, stack, clickedPos, shiftDown);
        }
    }

    private static void handleZoneSelection(ServerPlayer player, ItemStack stack, BlockPos clickedPos, boolean shiftDown) {
        if (StationStructureEditorStick.rootZoneLocked(stack)) {
            player.displayClientMessage(Component.literal("Root Structure Zone is locked. Disable lock in Structure inspector first."), true);
            return;
        }
        String key = shiftDown ? StationStructureToolItem.KEY_POS_2 : StationStructureToolItem.KEY_POS_1;
        StationStructureEditorStick.setPosition(stack, key, clickedPos);
        player.displayClientMessage(Component.literal("Structure " + (shiftDown ? "POS_2" : "POS_1") + ": " + clickedPos.toShortString()), true);
        syncClientPreview(stack);
    }

    private static void handleTriggerCreate(ServerPlayer player, ItemStack stack, BlockPos clickedPos, boolean shiftDown) {
        if (!StationStructureEditorStick.hasSelection(stack)) {
            player.displayClientMessage(Component.literal("Create a Structure Zone first."), true);
            return;
        }
        if (!insideRoot(stack, clickedPos)) {
            player.displayClientMessage(Component.literal("Trigger point must be inside Structure Zone."), true);
            return;
        }

        String key = shiftDown ? StationStructureEditorStick.KEY_TRIGGER_DRAFT_POS_2 : StationStructureEditorStick.KEY_TRIGGER_DRAFT_POS_1;
        StationStructureEditorStick.setPosition(stack, key, clickedPos);
        player.displayClientMessage(Component.literal("Trigger draft " + (shiftDown ? "POS_2" : "POS_1") + ": " + clickedPos.toShortString()), true);
        syncClientPreview(stack);
        if (stack.getOrCreateTag().contains(StationStructureEditorStick.KEY_TRIGGER_DRAFT_POS_1)
                && stack.getOrCreateTag().contains(StationStructureEditorStick.KEY_TRIGGER_DRAFT_POS_2)) {
            stack.getOrCreateTag().putString(StationStructureEditorStick.KEY_SELECTED_NODE, "trigger_draft");
            StationStructureNetwork.sendOpenEditor(player, OpenStationZoneEditorPacket.fromStack(stack));
        }
    }

    private static void handleTriggerEdit(ServerPlayer player, ItemStack stack, BlockPos clickedPos) {
        int triggerIndex = findTriggerAt(stack, clickedPos);
        if (triggerIndex < 0) {
            player.displayClientMessage(Component.literal("No trigger zone at clicked block."), true);
            return;
        }
        stack.getOrCreateTag().putString(StationStructureEditorStick.KEY_SELECTED_NODE, "trigger:" + triggerIndex);
        syncClientPreview(stack);
        StationStructureNetwork.sendOpenEditor(player, OpenStationZoneEditorPacket.fromStack(stack));
    }

    private static void handleConnectionManager(ServerPlayer player, ItemStack stack, BlockPos clickedPos, boolean shiftDown) {
        if (!StationStructureEditorStick.hasSelection(stack)) {
            player.displayClientMessage(Component.literal("Create a Structure Zone first."), true);
            return;
        }
        if (!insideRoot(stack, clickedPos)) {
            player.displayClientMessage(Component.literal("Connection point must be inside Structure Zone."), true);
            return;
        }

        String key = shiftDown ? StationStructureEditorStick.KEY_CONNECTION_DRAFT_POS_2 : StationStructureEditorStick.KEY_CONNECTION_DRAFT_POS_1;
        StationStructureEditorStick.setPosition(stack, key, clickedPos);
        stack.getOrCreateTag().putString(StationStructureEditorStick.KEY_SELECTED_NODE, "connection_draft");
        player.displayClientMessage(Component.literal("Connection draft " + (shiftDown ? "POS_2" : "POS_1") + ": " + clickedPos.toShortString()), true);
        syncClientPreview(stack);
        if (shiftDown || stack.getOrCreateTag().contains(StationStructureEditorStick.KEY_CONNECTION_DRAFT_POS_2)) {
            StationStructureNetwork.sendOpenEditor(player, OpenStationZoneEditorPacket.fromStack(stack));
        }
    }

    private static boolean insideRoot(ItemStack stack, BlockPos pos) {
        CompoundTag tag = StationStructureEditorStick.editorTag(stack);
        BlockPos min = StationStructureEditorStick.structureMin(tag);
        BlockPos max = StationStructureEditorStick.structureMax(tag);
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    private static int findTriggerAt(ItemStack stack, BlockPos pos) {
        ListTag triggers = StationStructureEditorStick.editorTag(stack).getList(StationStructureToolItem.KEY_TRIGGER_ZONES, Tag.TAG_COMPOUND);
        for (int i = 0; i < triggers.size(); i++) {
            CompoundTag trigger = triggers.getCompound(i);
            BlockPos min = NbtPos.load(trigger.getCompound("worldMin"));
            BlockPos max = NbtPos.load(trigger.getCompound("worldMax"));
            if (new AABB(min, max.offset(1, 1, 1)).contains(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)) {
                return i;
            }
        }
        return -1;
    }

    private static int findConnectionAt(ItemStack stack, BlockPos pos) {
        ListTag connectors = StationStructureEditorStick.editorTag(stack).getList(StationStructureToolItem.KEY_CONNECTORS, Tag.TAG_COMPOUND);
        for (int i = 0; i < connectors.size(); i++) {
            CompoundTag connector = connectors.getCompound(i);
            BlockPos min = NbtPos.load(connector.getCompound("worldMin"));
            BlockPos max = NbtPos.load(connector.getCompound("worldMax"));
            if (new AABB(min, max.offset(1, 1, 1)).contains(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)) {
                return i;
            }
        }
        return -1;
    }

    private static void syncClientPreview(ItemStack stack) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> StationEditorClientState.setEditorTag(StationStructureEditorStick.editorTag(stack)));
    }
}
