package dev.sixik.stationarenear.structures.editor;

import dev.sixik.stationarenear.structures.client.StationEditorClientState;
import dev.sixik.stationarenear.structures.data.PlacedStationPiece;
import dev.sixik.stationarenear.structures.data.StationConnector;
import dev.sixik.stationarenear.structures.data.StationPieceDefinition;
import dev.sixik.stationarenear.structures.data.StationPoolDefinition;
import dev.sixik.stationarenear.structures.data.StationTriggerZone;
import dev.sixik.stationarenear.structures.generation.StationPlacementUtil;
import dev.sixik.stationarenear.structures.item.StationStructureToolItem;
import dev.sixik.stationarenear.structures.network.StationStructureNetwork;
import dev.sixik.stationarenear.structures.network.packet.OpenStationZoneEditorPacket;
import dev.sixik.stationarenear.structures.util.NbtPos;
import dev.sixik.stationarenear.structures.world.StationSavedData;
import dev.sixik.stationarenear.structures.world.StationStructureLibraryData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
            case EDIT_STRUCTURE -> handleEditStructure(player, stack, clickedPos);
            case TRIGGER_MANAGER_CREATE -> handleTriggerCreate(player, stack, clickedPos, shiftDown);
            case TRIGGER_MANAGER_EDIT -> handleTriggerEdit(player, stack, clickedPos);
            case TRIGGER_SHAPE_POINTS -> handleTriggerShapePoints(player, stack, clickedPos, shiftDown);
            case CONNECTION_MANAGER -> handleConnectionManager(player, stack, clickedPos, shiftDown);
            case STRUCTURE_COPY -> handleStructureCopy(player, stack, clickedPos);
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

    private static void handleEditStructure(ServerPlayer player, ItemStack stack, BlockPos clickedPos) {
        StationStructureLibraryData library = StationStructureLibraryData.get(player.serverLevel());
        Optional<CopySource> sourceOptional = findCopySource(player, library, clickedPos);
        if (sourceOptional.isEmpty()) {
            player.displayClientMessage(Component.literal("No saved/generated structure piece at clicked block."), true);
            return;
        }

        CopySource source = sourceOptional.get();
        loadSourceIntoEditor(stack, source, source.selectionBounds(), true);
        syncClientPreview(stack);
        StationStructureNetwork.sendOpenEditor(player, OpenStationZoneEditorPacket.fromStack(stack));
        player.displayClientMessage(Component.literal("Editing structure " + source.definition().id() + " at " + clickedPos.toShortString()), true);
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

    private static void handleTriggerShapePoints(ServerPlayer player, ItemStack stack, BlockPos clickedPos, boolean shiftDown) {
        if (!StationStructureEditorStick.hasSelection(stack)) {
            player.displayClientMessage(Component.literal("Create a Structure Zone first."), true);
            return;
        }

        CompoundTag tag = stack.getOrCreateTag();
        StationStructureEditorStick.normalize(tag);
        ListTag triggers = tag.getList(StationStructureToolItem.KEY_TRIGGER_ZONES, Tag.TAG_COMPOUND);
        int triggerIndex = selectedTriggerIndex(tag);
        if (triggerIndex < 0 || triggerIndex >= triggers.size() || !triggerContains(triggers.getCompound(triggerIndex), clickedPos)) {
            triggerIndex = findTriggerAt(stack, clickedPos);
        }
        if (triggerIndex < 0 || triggerIndex >= triggers.size()) {
            player.displayClientMessage(Component.literal("Click inside trigger zone to edit its shape points."), true);
            return;
        }

        CompoundTag trigger = triggers.getCompound(triggerIndex);
        if (!triggerContains(trigger, clickedPos)) {
            player.displayClientMessage(Component.literal("Shape point must be inside selected trigger zone."), true);
            return;
        }

        CompoundTag data = trigger.getCompound("data").copy();
        if (shiftDown) {
            data.remove("shape");
            data.remove("shapePoints");
            trigger.put("data", data);
            triggers.set(triggerIndex, trigger);
            tag.put(StationStructureToolItem.KEY_TRIGGER_ZONES, triggers);
            tag.putString(StationStructureEditorStick.KEY_SELECTED_NODE, "trigger:" + triggerIndex);
            syncClientPreview(stack);
            player.displayClientMessage(Component.literal("Trigger shape points cleared."), true);
            return;
        }

        BlockPos min = NbtPos.load(trigger.getCompound("worldMin"));
        BlockPos offset = clickedPos.subtract(min);
        ToggleResult result = toggleShapePoint(data, offset);
        trigger.put("data", data);
        triggers.set(triggerIndex, trigger);
        tag.put(StationStructureToolItem.KEY_TRIGGER_ZONES, triggers);
        tag.putString(StationStructureEditorStick.KEY_SELECTED_NODE, "trigger:" + triggerIndex);
        syncClientPreview(stack);
        player.displayClientMessage(Component.literal((result.added() ? "Added" : "Removed") + " shape point " + clickedPos.toShortString() + " (" + result.count() + " total)."), true);
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


    private static void handleStructureCopy(ServerPlayer player, ItemStack stack, BlockPos clickedPos) {
        StationStructureLibraryData library = StationStructureLibraryData.get(player.serverLevel());
        Optional<CopySource> sourceOptional = findCopySource(player, library, clickedPos);
        if (sourceOptional.isEmpty()) {
            player.displayClientMessage(Component.literal("No saved/generated structure piece at clicked block."), true);
            return;
        }

        CopySource source = sourceOptional.get();
        CompoundTag tag = stack.getOrCreateTag();
        boolean hadTargetSelection = StationStructureEditorStick.hasSelection(stack);
        BoundingBox targetBounds = hadTargetSelection ? rootBounds(tag) : source.selectionBounds();
        BlockPos targetMin = minPos(targetBounds);
        BlockPos targetMax = maxPos(targetBounds);

        if (!hadTargetSelection) {
            tag.put(StationStructureToolItem.KEY_POS_1, NbtPos.save(targetMin));
            tag.put(StationStructureToolItem.KEY_POS_2, NbtPos.save(targetMax));
            tag.putString(StationStructureToolItem.KEY_TEMPLATE, source.definition().template().toString());
        }

        StationPieceDefinition definition = source.definition();
        loadSourceIntoEditor(stack, source, targetBounds, !hadTargetSelection);
        syncClientPreview(stack);

        String sizeWarning = sameSize(source.selectionBounds(), targetBounds) ? "" : " Size differs; copied zones were clamped.";
        player.displayClientMessage(Component.literal("Copied structure metadata from " + definition.id() + ": "
                + definition.connectors().size() + " connections, " + definition.triggerZones().size() + " triggers." + sizeWarning), true);
    }

    private static void loadSourceIntoEditor(ItemStack stack, CopySource source, BoundingBox targetBounds, boolean updateTemplate) {
        CompoundTag tag = stack.getOrCreateTag();
        StationPieceDefinition definition = source.definition();
        BlockPos targetMin = minPos(targetBounds);
        BlockPos targetMax = maxPos(targetBounds);
        tag.put(StationStructureToolItem.KEY_POS_1, NbtPos.save(targetMin));
        tag.put(StationStructureToolItem.KEY_POS_2, NbtPos.save(targetMax));
        if (updateTemplate) {
            tag.putString(StationStructureToolItem.KEY_TEMPLATE, definition.template().toString());
        }
        tag.putString(StationStructureToolItem.KEY_POOL, definition.pool().toString());
        tag.putString(StationStructureToolItem.KEY_TEMPLATE_TAGS, String.join(",", definition.tags()));
        tag.putBoolean(StationStructureToolItem.KEY_START_PIECE, source.startPiece());
        tag.putInt(StationStructureToolItem.KEY_WEIGHT, Math.max(1, definition.weight()));
        tag.putInt(StationStructureToolItem.KEY_FLOOR_SPAN, Math.max(1, definition.floorSpan()));
        tag.putString(StationStructureToolItem.KEY_EXTERIOR_SIDE, definition.exteriorSide() == null ? "none" : definition.exteriorSide().getSerializedName());
        tag.put(StationStructureToolItem.KEY_CONNECTORS, copyConnectors(source, targetBounds));
        tag.put(StationStructureToolItem.KEY_TRIGGER_ZONES, copyTriggers(source, targetBounds));
        tag.remove(StationStructureEditorStick.KEY_TRIGGER_DRAFT_POS_1);
        tag.remove(StationStructureEditorStick.KEY_TRIGGER_DRAFT_POS_2);
        tag.remove(StationStructureEditorStick.KEY_CONNECTION_DRAFT_POS_1);
        tag.remove(StationStructureEditorStick.KEY_CONNECTION_DRAFT_POS_2);
        tag.putString(StationStructureEditorStick.KEY_SELECTED_NODE, "root");
        StationStructureEditorStick.normalize(tag);
    }

    private static Optional<CopySource> findCopySource(ServerPlayer player, StationStructureLibraryData library, BlockPos clickedPos) {
        for (var station : StationSavedData.get(player.serverLevel()).stations()) {
            for (PlacedStationPiece piece : station.pieces()) {
                if (!contains(piece.selectionBounds(), clickedPos) && !contains(piece.bounds(), clickedPos)) {
                    continue;
                }
                Optional<StationPieceDefinition> definition = library.piece(piece.definitionId());
                if (definition.isPresent()) {
                    return Optional.of(new CopySource(
                            definition.get(),
                            piece.origin(),
                            piece.rotation(),
                            piece.selectionBounds(),
                            isStartPiece(library, definition.get())
                    ));
                }
            }
        }

        for (Map.Entry<ResourceLocation, BoundingBox> entry : library.savedTemplateSelections().entrySet()) {
            BoundingBox bounds = entry.getValue();
            if (!contains(bounds, clickedPos)) {
                continue;
            }
            Optional<StationPieceDefinition> definition = library.piece(entry.getKey());
            if (definition.isPresent()) {
                StationPieceDefinition piece = definition.get();
                return Optional.of(new CopySource(
                        piece,
                        minPos(bounds).subtract(piece.selectionMin()),
                        Rotation.NONE,
                        bounds,
                        isStartPiece(library, piece)
                ));
            }
        }
        return Optional.empty();
    }

    private static ListTag copyConnectors(CopySource source, BoundingBox targetBounds) {
        ListTag list = new ListTag();
        BlockPos sourceMin = minPos(source.selectionBounds());
        for (StationConnector connector : source.definition().connectors()) {
            BlockPos sourceWorldPosition = source.origin().offset(StructureTemplate.transform(connector.position(), Mirror.NONE, source.rotation(), BlockPos.ZERO));
            Direction sourceWorldDirection = source.rotation().rotate(connector.direction());
            BoundingBox sourceBounds = StationPlacementUtil.transformBox(source.origin(), connector.min(), connector.max(), source.rotation());
            BlockPos worldMin = rebaseToTarget(new BlockPos(sourceBounds.minX(), sourceBounds.minY(), sourceBounds.minZ()), sourceMin, targetBounds);
            BlockPos worldMax = rebaseToTarget(new BlockPos(sourceBounds.maxX(), sourceBounds.maxY(), sourceBounds.maxZ()), sourceMin, targetBounds);
            BlockPos worldPosition = rebaseToTarget(sourceWorldPosition, sourceMin, targetBounds);
            CompoundTag connectorTag = new CompoundTag();
            connectorTag.putString("nodeType", StationEditorNodeType.CONNECTION.name());
            connectorTag.putString("name", connector.name());
            connectorTag.put("worldMin", NbtPos.save(min(worldMin, worldMax)));
            connectorTag.put("worldMax", NbtPos.save(max(worldMin, worldMax)));
            connectorTag.put("worldPosition", NbtPos.save(worldPosition));
            connectorTag.putString("direction", sourceWorldDirection.getSerializedName());
            connectorTag.putString("tags", join(connector.tags()));
            connectorTag.putString("accepts", join(connector.accepts()));
            connectorTag.putInt("priority", connector.priority());
            connectorTag.putInt("width", connector.width());
            connectorTag.putInt("height", connector.height());
            connectorTag.putString("acceptedSizes", connector.acceptedSizes());
            connectorTag.putBoolean(StationConnector.KEY_REQUIRES_PASSAGE, connector.requiresPassage());
            list.add(connectorTag);
        }
        return list;
    }

    private static ListTag copyTriggers(CopySource source, BoundingBox targetBounds) {
        ListTag list = new ListTag();
        BlockPos sourceMin = minPos(source.selectionBounds());
        for (StationTriggerZone triggerZone : source.definition().triggerZones()) {
            BoundingBox sourceBounds = StationPlacementUtil.transformBox(source.origin(), triggerZone.min(), triggerZone.max(), source.rotation());
            BlockPos worldMin = rebaseToTarget(new BlockPos(sourceBounds.minX(), sourceBounds.minY(), sourceBounds.minZ()), sourceMin, targetBounds);
            BlockPos worldMax = rebaseToTarget(new BlockPos(sourceBounds.maxX(), sourceBounds.maxY(), sourceBounds.maxZ()), sourceMin, targetBounds);
            CompoundTag triggerTag = new CompoundTag();
            StationEditorNodeType nodeType = triggerNodeType(triggerZone.type());
            triggerTag.putString("nodeType", nodeType.name());
            triggerTag.putString("id", triggerZone.id());
            triggerTag.putString("type", triggerZone.type());
            BlockPos normalizedWorldMin = min(worldMin, worldMax);
            BlockPos normalizedWorldMax = max(worldMin, worldMax);
            triggerTag.put("worldMin", NbtPos.save(normalizedWorldMin));
            triggerTag.put("worldMax", NbtPos.save(normalizedWorldMax));
            CompoundTag data = triggerZone.data().copy();
            rebaseShapePoints(data, triggerZone, source, targetBounds, normalizedWorldMin);
            triggerTag.put("data", data);
            list.add(triggerTag);
        }
        return list;
    }

    private static void rebaseShapePoints(CompoundTag data, StationTriggerZone triggerZone, CopySource source, BoundingBox targetBounds, BlockPos targetMin) {
        if (!data.contains("shapePoints", Tag.TAG_LIST)) {
            return;
        }
        ListTag sourcePoints = data.getList("shapePoints", Tag.TAG_COMPOUND);
        ListTag rebasedPoints = new ListTag();
        BlockPos sourceMin = minPos(source.selectionBounds());
        for (int i = 0; i < sourcePoints.size(); i++) {
            CompoundTag point = sourcePoints.getCompound(i);
            if (!point.contains("x", Tag.TAG_INT) || !point.contains("y", Tag.TAG_INT) || !point.contains("z", Tag.TAG_INT)) {
                continue;
            }
            BlockPos offset = NbtPos.load(point);
            BlockPos localPos = triggerZone.min().offset(offset.getX(), offset.getY(), offset.getZ());
            BlockPos sourceWorldPos = source.origin().offset(StructureTemplate.transform(localPos, Mirror.NONE, source.rotation(), BlockPos.ZERO));
            BlockPos targetWorldPos = rebaseToTarget(sourceWorldPos, sourceMin, targetBounds);
            rebasedPoints.add(NbtPos.save(targetWorldPos.subtract(targetMin)));
        }
        data.put("shapePoints", rebasedPoints);
        data.putString("shape", "points");
    }

    private static StationEditorNodeType triggerNodeType(String type) {
        try {
            StationEditorNodeType nodeType = StationEditorNodeType.valueOf(type.toUpperCase(java.util.Locale.ROOT));
            return nodeType == StationEditorNodeType.STRUCTURE || nodeType == StationEditorNodeType.CONNECTION ? StationEditorNodeType.TRIGGER : nodeType;
        } catch (IllegalArgumentException exception) {
            return StationEditorNodeType.TRIGGER;
        }
    }

    private static BoundingBox rootBounds(CompoundTag tag) {
        BlockPos min = StationStructureEditorStick.structureMin(tag);
        BlockPos max = StationStructureEditorStick.structureMax(tag);
        return new BoundingBox(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
    }

    private static BlockPos rebaseToTarget(BlockPos sourceWorldPos, BlockPos sourceMin, BoundingBox targetBounds) {
        BlockPos targetMin = minPos(targetBounds);
        BlockPos targetMax = maxPos(targetBounds);
        BlockPos rebased = targetMin.offset(sourceWorldPos.getX() - sourceMin.getX(), sourceWorldPos.getY() - sourceMin.getY(), sourceWorldPos.getZ() - sourceMin.getZ());
        return new BlockPos(
                net.minecraft.util.Mth.clamp(rebased.getX(), targetMin.getX(), targetMax.getX()),
                net.minecraft.util.Mth.clamp(rebased.getY(), targetMin.getY(), targetMax.getY()),
                net.minecraft.util.Mth.clamp(rebased.getZ(), targetMin.getZ(), targetMax.getZ())
        );
    }

    private static boolean isStartPiece(StationStructureLibraryData library, StationPieceDefinition definition) {
        return library.pool(definition.pool())
                .map(StationPoolDefinition::startPieces)
                .map(starts -> starts.contains(definition.id()))
                .orElse(false);
    }

    private static boolean contains(BoundingBox bounds, BlockPos pos) {
        return pos.getX() >= bounds.minX() && pos.getX() <= bounds.maxX()
                && pos.getY() >= bounds.minY() && pos.getY() <= bounds.maxY()
                && pos.getZ() >= bounds.minZ() && pos.getZ() <= bounds.maxZ();
    }

    private static boolean sameSize(BoundingBox left, BoundingBox right) {
        return left.getXSpan() == right.getXSpan() && left.getYSpan() == right.getYSpan() && left.getZSpan() == right.getZSpan();
    }

    private static BlockPos minPos(BoundingBox bounds) {
        return new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ());
    }

    private static BlockPos maxPos(BoundingBox bounds) {
        return new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }

    private static BlockPos min(BlockPos left, BlockPos right) {
        return new BlockPos(Math.min(left.getX(), right.getX()), Math.min(left.getY(), right.getY()), Math.min(left.getZ(), right.getZ()));
    }

    private static BlockPos max(BlockPos left, BlockPos right) {
        return new BlockPos(Math.max(left.getX(), right.getX()), Math.max(left.getY(), right.getY()), Math.max(left.getZ(), right.getZ()));
    }

    private static String join(Set<String> values) {
        return String.join(",", values);
    }

    private static boolean insideRoot(ItemStack stack, BlockPos pos) {
        CompoundTag tag = StationStructureEditorStick.editorTag(stack);
        BlockPos min = StationStructureEditorStick.structureMin(tag);
        BlockPos max = StationStructureEditorStick.structureMax(tag);
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    private static int selectedTriggerIndex(CompoundTag tag) {
        String selected = tag.getString(StationStructureEditorStick.KEY_SELECTED_NODE);
        if (!selected.startsWith("trigger:")) {
            return -1;
        }
        try {
            return Integer.parseInt(selected.substring("trigger:".length()));
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static ToggleResult toggleShapePoint(CompoundTag data, BlockPos offset) {
        ListTag oldPoints = data.getList("shapePoints", Tag.TAG_COMPOUND);
        ListTag newPoints = new ListTag();
        boolean removed = false;
        for (int i = 0; i < oldPoints.size(); i++) {
            CompoundTag point = oldPoints.getCompound(i);
            if (point.getInt("x") == offset.getX() && point.getInt("y") == offset.getY() && point.getInt("z") == offset.getZ()) {
                removed = true;
                continue;
            }
            newPoints.add(point.copy());
        }
        if (!removed) {
            newPoints.add(NbtPos.save(offset));
        }
        if (newPoints.isEmpty()) {
            data.remove("shape");
            data.remove("shapePoints");
        } else {
            data.putString("shape", "points");
            data.put("shapePoints", newPoints);
        }
        return new ToggleResult(!removed, newPoints.size());
    }

    private static boolean triggerContains(CompoundTag trigger, BlockPos pos) {
        if (!trigger.contains("worldMin", Tag.TAG_COMPOUND) || !trigger.contains("worldMax", Tag.TAG_COMPOUND)) {
            return false;
        }
        BlockPos min = NbtPos.load(trigger.getCompound("worldMin"));
        BlockPos max = NbtPos.load(trigger.getCompound("worldMax"));
        return new AABB(min, max.offset(1, 1, 1)).contains(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
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
    private record ToggleResult(boolean added, int count) {
    }

    private record CopySource(StationPieceDefinition definition, BlockPos origin, Rotation rotation, BoundingBox selectionBounds, boolean startPiece) {
    }
}
