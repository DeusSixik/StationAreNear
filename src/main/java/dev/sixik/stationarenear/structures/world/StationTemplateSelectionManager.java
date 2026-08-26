package dev.sixik.stationarenear.structures.world;

import dev.sixik.stationarenear.structures.data.PlacedStationPiece;
import dev.sixik.stationarenear.structures.data.StationConnector;
import dev.sixik.stationarenear.structures.data.StationPieceDefinition;
import dev.sixik.stationarenear.structures.data.StationTriggerZone;
import dev.sixik.stationarenear.structures.data.TemplateSelectionEntry;
import dev.sixik.stationarenear.structures.editor.StationEditorNodeType;
import dev.sixik.stationarenear.structures.editor.StationStructureEditorStick;
import dev.sixik.stationarenear.structures.generation.StationPlacementUtil;
import dev.sixik.stationarenear.structures.item.StationStructureToolItem;
import dev.sixik.stationarenear.structures.network.StationStructureNetwork;
import dev.sixik.stationarenear.structures.network.packet.OpenStationZoneEditorPacket;
import dev.sixik.stationarenear.structures.registry.StationStructureItems;
import dev.sixik.stationarenear.structures.util.NbtPos;
import dev.sixik.stationarenear.structures.util.StationStructureIds;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class StationTemplateSelectionManager {

    private StationTemplateSelectionManager() {
    }

    public static List<TemplateSelectionEntry> collect(ServerLevel level) {
        StationStructureLibraryData library = StationStructureLibraryData.get(level);
        Map<ResourceLocation, TemplateSelectionEntry> templateDefinitions = new Object2ObjectLinkedOpenHashMap<>();
        for (StationPieceDefinition piece : library.pieces()) {
            templateDefinitions.put(piece.template(), new TemplateSelectionEntry(piece.template(), "library", false, null));
        }
        for (Map.Entry<ResourceLocation, BoundingBox> entry : library.savedTemplateSelections().entrySet()) {
            templateDefinitions.put(entry.getKey(), new TemplateSelectionEntry(entry.getKey(), "saved", true, entry.getValue()));
        }

        List<TemplateSelectionEntry> entries = new ObjectArrayList<>(templateDefinitions.values());
        StationSavedData.get(level).stations().forEach(station -> {
            for (PlacedStationPiece piece : station.pieces()) {
                if (hasPlacedBlocks(level, piece.bounds())) {
                    entries.add(new TemplateSelectionEntry(piece.template(), "generated", true, piece.selectionBounds()));
                }
            }
        });
        return entries;
    }

    private static boolean hasPlacedBlocks(ServerLevel level, BoundingBox bounds) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    if (!level.getBlockState(mutable.set(x, y, z)).isAir()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static Optional<TemplateSelectionEntry> find(ServerLevel level, ResourceLocation template) {
        return collect(level).stream().filter(entry -> entry.template().equals(template) && entry.hasBounds()).findFirst();
    }

    public static void edit(ServerPlayer player, String templateIdText) {
        ServerLevel level = player.serverLevel();
        ResourceLocation templateId = StationStructureIds.template(templateIdText);
        Optional<TemplateSelectionEntry> entry = find(level, templateId);
        if (entry.isEmpty()) {
            player.displayClientMessage(Component.literal("Template " + templateId + " is not spawned in this world. Spawn it first."), false);
            return;
        }

        BoundingBox bounds = entry.get().bounds();
        BlockPos center = bounds.getCenter();
        player.teleportTo(level, center.getX() + 0.5D, center.getY() + 1.0D, center.getZ() + 0.5D, player.getYRot(), player.getXRot());

        ItemStack stack = player.getMainHandItem();
        if (!StationStructureEditorStick.isEditorTool(stack)) {
            stack = StationStructureEditorStick.create();
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        }
        CompoundTag tag = stack.getOrCreateTag();
        tag.put(StationStructureToolItem.KEY_POS_1, NbtPos.save(new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ())));
        tag.put(StationStructureToolItem.KEY_POS_2, NbtPos.save(new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ())));
        tag.putString(StationStructureToolItem.KEY_TEMPLATE, templateId.toString());
        bindTemplateMetadata(level, templateId, bounds, tag);
        tag.putString(StationStructureEditorStick.KEY_SELECTED_NODE, "root");
        StationStructureEditorStick.normalize(tag);

        StationStructureNetwork.sendOpenEditor(player, OpenStationZoneEditorPacket.fromStack(stack));
        player.displayClientMessage(Component.literal("Editing template " + templateId + " at " + center.toShortString()), false);
    }

    public static void delete(ServerPlayer player, String templateIdText) {
        ServerLevel level = player.serverLevel();
        ResourceLocation templateId = StationStructureIds.template(templateIdText);
        StationStructureLibraryData library = StationStructureLibraryData.get(level);
        boolean removedLibrary = library.removeTemplate(templateId);
        boolean removedFile = deleteGeneratedTemplateFile(level, templateId);
        level.getStructureManager().remove(templateId);
        StationStructureNetwork.syncTemplateSelections(level);
        StationStructureNetwork.openTemplateMenu(player);

        if (removedLibrary || removedFile) {
            player.displayClientMessage(Component.literal("Deleted station template " + templateId), false);
        } else {
            player.displayClientMessage(Component.literal("Template " + templateId + " was not found in saved templates."), false);
        }
    }

    private static boolean deleteGeneratedTemplateFile(ServerLevel level, ResourceLocation templateId) {
        try {
            Path templatePath = level.getStructureManager().getPathToGeneratedStructure(templateId, ".nbt");
            return Files.deleteIfExists(templatePath);
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private static void bindTemplateMetadata(ServerLevel level, ResourceLocation templateId, BoundingBox bounds, CompoundTag tag) {
        StationStructureLibraryData library = StationStructureLibraryData.get(level);
        Optional<StationPieceDefinition> definition = library.piece(templateId);
        if (definition.isEmpty()) {
            tag.put(StationStructureToolItem.KEY_CONNECTORS, new ListTag());
            tag.put(StationStructureToolItem.KEY_TRIGGER_ZONES, new ListTag());
            return;
        }

        StationPieceDefinition piece = definition.get();
        Optional<PlacedStationPiece> placedPiece = findPlacedPiece(level, templateId);
        BlockPos origin = placedPiece.map(PlacedStationPiece::origin)
                .orElse(new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()).subtract(piece.selectionMin()));
        Rotation rotation = placedPiece.map(PlacedStationPiece::rotation).orElse(Rotation.NONE);

        tag.putString(StationStructureToolItem.KEY_POOL, piece.pool().toString());
        tag.putString(StationStructureToolItem.KEY_TEMPLATE_TAGS, String.join(",", piece.tags()));
        tag.putInt(StationStructureToolItem.KEY_FLOOR_SPAN, Math.max(1, piece.floorSpan()));
        tag.put(StationStructureToolItem.KEY_CONNECTORS, saveEditorConnectors(piece.connectors(), origin, rotation));
        tag.put(StationStructureToolItem.KEY_TRIGGER_ZONES, saveEditorTriggers(piece.triggerZones(), origin, rotation));
    }

    private static Optional<PlacedStationPiece> findPlacedPiece(ServerLevel level, ResourceLocation templateId) {
        return StationSavedData.get(level).stations().stream()
                .flatMap(station -> station.pieces().stream())
                .filter(piece -> piece.template().equals(templateId))
                .findFirst();
    }

    private static ListTag saveEditorConnectors(List<StationConnector> connectors, BlockPos origin, Rotation rotation) {
        ListTag list = new ListTag();
        for (StationConnector connector : connectors) {
            BlockPos worldPosition = origin.offset(StructureTemplate.transform(connector.position(), Mirror.NONE, rotation, BlockPos.ZERO));
            Direction worldDirection = rotation.rotate(connector.direction());
            BoundingBox bounds = StationPlacementUtil.transformBox(origin, connector.min(), connector.max(), rotation);
            CompoundTag connectorTag = new CompoundTag();
            connectorTag.putString("nodeType", StationEditorNodeType.CONNECTION.name());
            connectorTag.putString("name", connector.name());
            connectorTag.put("worldMin", NbtPos.save(new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ())));
            connectorTag.put("worldMax", NbtPos.save(new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ())));
            connectorTag.put("worldPosition", NbtPos.save(worldPosition));
            connectorTag.putString("direction", worldDirection.getSerializedName());
            connectorTag.putString("tags", join(connector.tags()));
            connectorTag.putString("accepts", join(connector.accepts()));
            connectorTag.putInt("priority", connector.priority());
            connectorTag.putInt("width", connector.width());
            connectorTag.putInt("height", connector.height());
            connectorTag.putString("acceptedSizes", connector.acceptedSizes());
            list.add(connectorTag);
        }
        return list;
    }

    private static ListTag saveEditorTriggers(List<StationTriggerZone> triggerZones, BlockPos origin, Rotation rotation) {
        ListTag list = new ListTag();
        for (StationTriggerZone triggerZone : triggerZones) {
            BoundingBox triggerBounds = StationPlacementUtil.transformBox(origin, triggerZone.min(), triggerZone.max(), rotation);
            CompoundTag triggerTag = new CompoundTag();
            StationEditorNodeType nodeType = triggerNodeType(triggerZone.type());
            triggerTag.putString("nodeType", nodeType.name());
            triggerTag.putString("id", triggerZone.id());
            triggerTag.putString("type", triggerZone.type());
            triggerTag.put("worldMin", NbtPos.save(new BlockPos(triggerBounds.minX(), triggerBounds.minY(), triggerBounds.minZ())));
            triggerTag.put("worldMax", NbtPos.save(new BlockPos(triggerBounds.maxX(), triggerBounds.maxY(), triggerBounds.maxZ())));
            triggerTag.put("data", triggerZone.data().copy());
            list.add(triggerTag);
        }
        return list;
    }

    private static StationEditorNodeType triggerNodeType(String type) {
        try {
            StationEditorNodeType nodeType = StationEditorNodeType.valueOf(type.toUpperCase(java.util.Locale.ROOT));
            return nodeType == StationEditorNodeType.STRUCTURE || nodeType == StationEditorNodeType.CONNECTION ? StationEditorNodeType.TRIGGER : nodeType;
        } catch (IllegalArgumentException exception) {
            return StationEditorNodeType.TRIGGER;
        }
    }

    private static String join(Set<String> values) {
        return String.join(",", values);
    }

}
