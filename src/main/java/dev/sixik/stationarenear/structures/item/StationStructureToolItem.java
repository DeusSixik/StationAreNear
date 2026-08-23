package dev.sixik.stationarenear.structures.item;

import dev.sixik.stationarenear.structures.data.StationConnector;
import dev.sixik.stationarenear.structures.data.StationPieceDefinition;
import dev.sixik.stationarenear.structures.data.StationTriggerZone;
import dev.sixik.stationarenear.structures.editor.StationEditorNodeType;
import dev.sixik.stationarenear.structures.editor.StationStructureEditorStick;
import dev.sixik.stationarenear.structures.generation.StationPlacementUtil;
import dev.sixik.stationarenear.structures.util.NbtPos;
import dev.sixik.stationarenear.structures.util.StationStructureIds;
import dev.sixik.stationarenear.structures.world.StationStructureLibraryData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class StationStructureToolItem extends Item {

    public static final String KEY_TEMPLATE = "stationTemplate";
    public static final String KEY_POOL = "stationPool";
    public static final String KEY_POS_1 = "pos1";
    public static final String KEY_POS_2 = "pos2";
    public static final String KEY_CONNECTOR_NAME = "connectorName";
    public static final String KEY_CONNECTOR_DIRECTION = "connectorDirection";
    public static final String KEY_CONNECTOR_TAGS = "connectorTags";
    public static final String KEY_CONNECTOR_ACCEPTS = "connectorAccepts";
    public static final String KEY_CONNECTORS = "connectors";
    public static final String KEY_TRIGGER_ZONES = "triggerZones";
    public static final String KEY_START_PIECE = "startPiece";
    public static final String KEY_WEIGHT = "weight";
    public static final String KEY_FLOOR_SPAN = "floorSpan";
    public static final String KEY_EXTERIOR_SIDE = "exteriorSide";

    public StationStructureToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        ItemStack stack = context.getItemInHand();
        CompoundTag tag = stack.getOrCreateTag();
        BlockPos clickedPos = context.getClickedPos();

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            tag.put(KEY_POS_1, NbtPos.save(clickedPos));
            player.displayClientMessage(Component.literal("Station structure pos1: " + clickedPos.toShortString()), true);
            return InteractionResult.SUCCESS;
        }

        tag.put(KEY_POS_2, NbtPos.save(clickedPos));
        if (!tag.contains(KEY_TEMPLATE) || !tag.contains(KEY_POOL) || !tag.contains(KEY_POS_1)) {
            player.displayClientMessage(Component.literal("Station structure pos2: " + clickedPos.toShortString()), true);
            return InteractionResult.SUCCESS;
        }

        saveStructure((ServerLevel) level, player, stack, clickedPos, context.getClickedFace(), false);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            tooltip.add(Component.literal("Use commands to bind template and pool").withStyle(ChatFormatting.GRAY));
            return;
        }

        if (tag.contains(KEY_TEMPLATE)) {
            tooltip.add(Component.literal("Template: " + tag.getString(KEY_TEMPLATE)).withStyle(ChatFormatting.AQUA));
        }
        if (tag.contains(KEY_POOL)) {
            tooltip.add(Component.literal("Pool: " + tag.getString(KEY_POOL)).withStyle(ChatFormatting.AQUA));
        }
        if (tag.getBoolean(KEY_START_PIECE)) {
            tooltip.add(Component.literal("Docking/start piece").withStyle(ChatFormatting.GREEN));
        }
        if (tag.contains(KEY_CONNECTORS)) {
            tooltip.add(Component.literal("Connectors: " + tag.getList(KEY_CONNECTORS, CompoundTag.TAG_COMPOUND).size()).withStyle(ChatFormatting.GRAY));
        }
        if (tag.contains(KEY_TRIGGER_ZONES)) {
            tooltip.add(Component.literal("Trigger zones: " + tag.getList(KEY_TRIGGER_ZONES, CompoundTag.TAG_COMPOUND).size()).withStyle(ChatFormatting.GRAY));
        }
    }

    public static boolean saveStructure(ServerLevel level, Player player, ItemStack stack) {
        return saveStructure(level, player, stack, false);
    }

    public static boolean saveStructure(ServerLevel level, Player player, ItemStack stack, boolean allowOverwrite) {
        return saveStructure(level, player, stack, null, null, allowOverwrite);
    }

    private static boolean saveStructure(ServerLevel level, Player player, ItemStack stack, @Nullable BlockPos connectorWorldPos, @Nullable Direction clickedFace, boolean allowOverwrite) {
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(Component.literal("No permission to save station structures"), true);
            return false;
        }

        CompoundTag tag = stack.getOrCreateTag();
        ResourceLocation templateId = StationStructureIds.template(tag.getString(KEY_TEMPLATE));
        ResourceLocation poolId = StationStructureIds.pool(tag.getString(KEY_POOL));
        tag.putString(KEY_TEMPLATE, templateId.toString());
        tag.putString(KEY_POOL, poolId.toString());
        if (!tag.contains(KEY_POS_1) || !tag.contains(KEY_POS_2)) {
            player.displayClientMessage(Component.literal("Select Structure POS_1/POS_2 before saving."), true);
            return false;
        }

        BlockPos pos1 = NbtPos.load(tag.getCompound(KEY_POS_1));
        BlockPos pos2 = NbtPos.load(tag.getCompound(KEY_POS_2));
        BlockPos min = new BlockPos(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ())
        );
        BlockPos max = new BlockPos(
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ())
        );
        BlockPos size = max.subtract(min).offset(1, 1, 1);

        StationStructureLibraryData library = StationStructureLibraryData.get(level);
        if (!allowOverwrite && templateExists(level, library, templateId)) {
            player.displayClientMessage(Component.literal("Template " + templateId + " already exists. Confirm overwrite in editor UI."), false);
            return false;
        }

        StructureTemplate template = level.getStructureManager().getOrCreate(templateId);
        template.fillFromWorld(level, min, size, true, Blocks.STRUCTURE_VOID);
        if (!level.getStructureManager().save(templateId)) {
            player.displayClientMessage(Component.literal("Failed to save station structure template " + templateId), false);
            return false;
        }

        StationConnector fallbackConnector = null;
        if (connectorWorldPos != null) {
            BlockPos connectorLocalPos = connectorWorldPos.subtract(min);
            Direction connectorDirection = Direction.byName(tag.getString(KEY_CONNECTOR_DIRECTION));
            if (connectorDirection == null) {
                connectorDirection = clickedFace == null ? Direction.NORTH : clickedFace;
            }
            fallbackConnector = defaultConnector(tag, connectorLocalPos, connectorDirection);
        }
        StationPieceDefinition definition = new StationPieceDefinition(
                templateId,
                templateId,
                poolId,
                loadConnectors(tag, min, fallbackConnector),
                loadTriggerZones(tag, min),
                BlockPos.ZERO,
                max.subtract(min),
                Math.max(1, tag.contains(KEY_FLOOR_SPAN) ? tag.getInt(KEY_FLOOR_SPAN) : detectFloorSpan(max.subtract(min))),
                Math.max(1, tag.contains(KEY_WEIGHT) ? tag.getInt(KEY_WEIGHT) : 1),
                exteriorSide(tag),
                0.0F,
                1.0F
        );
        library.upsertPiece(definition, tag.getBoolean(KEY_START_PIECE));
        library.upsertTemplateSelection(templateId, new BoundingBox(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ()));
        dev.sixik.stationarenear.structures.network.StationStructureNetwork.syncTemplateSelections(level);
        player.displayClientMessage(Component.literal("Saved station piece " + templateId + " into pool " + poolId), false);
        return true;
    }

    private static Direction exteriorSide(CompoundTag tag) {
        if (!tag.contains(KEY_EXTERIOR_SIDE) || tag.getString(KEY_EXTERIOR_SIDE).isBlank() || tag.getString(KEY_EXTERIOR_SIDE).equalsIgnoreCase("none")) {
            return null;
        }
        return Direction.byName(tag.getString(KEY_EXTERIOR_SIDE));
    }

    private static int detectFloorSpan(BlockPos localMax) {
        return Math.max(1, (localMax.getY() + 16) / 16);
    }

    private static boolean templateExists(ServerLevel level, StationStructureLibraryData library, ResourceLocation templateId) {
        return library.piece(templateId).isPresent()
                || library.savedTemplateSelections().containsKey(templateId)
                || level.getStructureManager().get(templateId).isPresent();
    }

    private static StationConnector defaultConnector(CompoundTag tag, BlockPos connectorLocalPos, Direction connectorDirection) {
        Set<String> connectorTags = StationPlacementUtil.parseTags(tag.getString(KEY_CONNECTOR_TAGS));
        Set<String> connectorAccepts = StationPlacementUtil.parseTags(tag.getString(KEY_CONNECTOR_ACCEPTS));
        if (connectorTags.isEmpty()) {
            connectorTags = Set.of("corridor");
        }
        if (connectorAccepts.isEmpty()) {
            connectorAccepts = Set.of("corridor", "dock");
        }

        return new StationConnector(
                tag.getString(KEY_CONNECTOR_NAME).isBlank() ? "main" : tag.getString(KEY_CONNECTOR_NAME),
                connectorLocalPos,
                connectorDirection,
                connectorTags,
                connectorAccepts,
                0,
                connectorLocalPos,
                connectorLocalPos,
                1,
                1,
                "1x1"
        );
    }

    private static List<StationConnector> loadConnectors(CompoundTag tag, BlockPos structureMin, @Nullable StationConnector fallback) {
        if (!tag.contains(KEY_CONNECTORS)) {
            return fallback == null ? List.of() : List.of(fallback);
        }

        List<StationConnector> connectors = new ArrayList<>();
        ListTag connectorTags = tag.getList(KEY_CONNECTORS, Tag.TAG_COMPOUND);
        for (int i = 0; i < connectorTags.size(); i++) {
            CompoundTag connectorTag = connectorTags.getCompound(i);
            Direction direction = Direction.byName(connectorTag.getString("direction"));
            if (direction == null) {
                continue;
            }

            BlockPos worldPosition = connectorTag.contains("worldPosition", Tag.TAG_COMPOUND)
                    ? NbtPos.load(connectorTag.getCompound("worldPosition"))
                    : NbtPos.load(connectorTag.getCompound("worldMin"));
            BlockPos worldMin = connectorTag.contains("worldMin", Tag.TAG_COMPOUND) ? NbtPos.load(connectorTag.getCompound("worldMin")) : worldPosition;
            BlockPos worldMax = connectorTag.contains("worldMax", Tag.TAG_COMPOUND) ? NbtPos.load(connectorTag.getCompound("worldMax")) : worldPosition;
            BlockPos localPosition = worldPosition.subtract(structureMin);
            BlockPos localMin = worldMin.subtract(structureMin);
            BlockPos localMax = worldMax.subtract(structureMin);
            int width = Math.max(1, connectorTag.contains("width") ? connectorTag.getInt("width") : localMax.getX() - localMin.getX() + 1);
            int height = Math.max(1, connectorTag.contains("height") ? connectorTag.getInt("height") : localMax.getY() - localMin.getY() + 1);
            String acceptedSizes = connectorTag.contains("acceptedSizes") && !connectorTag.getString("acceptedSizes").isBlank()
                    ? connectorTag.getString("acceptedSizes")
                    : width + "x" + height;

            connectors.add(new StationConnector(
                    connectorTag.getString("name"),
                    localPosition,
                    direction,
                    StationPlacementUtil.parseTags(connectorTag.getString("tags")),
                    StationPlacementUtil.parseTags(connectorTag.getString("accepts")),
                    connectorTag.getInt("priority"),
                    localMin,
                    localMax,
                    width,
                    height,
                    acceptedSizes
            ));
        }

        return connectors.isEmpty() ? (fallback == null ? List.of() : List.of(fallback)) : connectors;
    }

    private static List<StationTriggerZone> loadTriggerZones(CompoundTag tag, BlockPos structureMin) {
        if (!tag.contains(KEY_TRIGGER_ZONES)) {
            return List.of();
        }

        List<StationTriggerZone> triggerZones = new ArrayList<>();
        ListTag triggerTags = tag.getList(KEY_TRIGGER_ZONES, Tag.TAG_COMPOUND);
        for (int i = 0; i < triggerTags.size(); i++) {
            CompoundTag triggerTag = triggerTags.getCompound(i);
            if (!triggerTag.contains("worldMin") || !triggerTag.contains("worldMax")) {
                triggerZones.add(StationTriggerZone.load(triggerTag));
                continue;
            }

            BlockPos localMin = NbtPos.load(triggerTag.getCompound("worldMin")).subtract(structureMin);
            BlockPos localMax = NbtPos.load(triggerTag.getCompound("worldMax")).subtract(structureMin);
            triggerZones.add(new StationTriggerZone(
                    triggerTag.getString("id"),
                    triggerType(triggerTag),
                    localMin,
                    localMax,
                    triggerTag.getCompound("data")
            ));
        }
        return triggerZones;
    }

    private static String triggerType(CompoundTag triggerTag) {
        if (triggerTag.contains("type") && !triggerTag.getString("type").isBlank()) {
            return triggerTag.getString("type");
        }
        if (triggerTag.contains("nodeType")) {
            try {
                return StationEditorNodeType.valueOf(triggerTag.getString("nodeType")).name().toLowerCase(java.util.Locale.ROOT);
            } catch (IllegalArgumentException ignored) {
                return "trigger";
            }
        }
        return "trigger";
    }
}
