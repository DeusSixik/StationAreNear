package dev.sixik.stationarenear.structures.editor;

import dev.sixik.stationarenear.structures.item.StationStructureToolItem;
import dev.sixik.stationarenear.structures.registry.StationStructureItems;
import dev.sixik.stationarenear.structures.util.NbtPos;
import dev.sixik.stationarenear.structures.util.StationStructureIds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

public final class StationStructureEditorStick {

    public static final String KEY_EDITOR_STICK = "stationStructureEditorStick";
    public static final String KEY_EDITOR_MODE = "stationEditorMode";
    public static final String KEY_SELECTED_NODE = "selectedNode";
    public static final String KEY_SHOW_HANDLES = "showZoneHandles";
    public static final String KEY_SHOW_ROOT_TEXT = "showRootText";
    public static final String KEY_LOCK_ROOT_ZONE = "lockRootZone";
    public static final String KEY_TRIGGER_DRAFT_POS_1 = "triggerDraftPos1";
    public static final String KEY_TRIGGER_DRAFT_POS_2 = "triggerDraftPos2";
    public static final String KEY_CONNECTION_DRAFT_POS_1 = "connectionDraftPos1";
    public static final String KEY_CONNECTION_DRAFT_POS_2 = "connectionDraftPos2";

    private StationStructureEditorStick() {
    }

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.STICK);
        stack.enchant(Enchantments.UNBREAKING, 1);
        stack.setHoverName(Component.literal("Station Structure Editor").withStyle(ChatFormatting.LIGHT_PURPLE));
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(KEY_EDITOR_STICK, true);
        tag.putString(StationStructureToolItem.KEY_TEMPLATE, "stationarenear:stations/new_piece");
        tag.putString(StationStructureToolItem.KEY_POOL, "stationarenear:space_station");
        tag.putBoolean(StationStructureToolItem.KEY_START_PIECE, false);
        tag.putInt(StationStructureToolItem.KEY_WEIGHT, 1);
        tag.putInt(StationStructureToolItem.KEY_FLOOR_SPAN, 1);
        tag.putString(KEY_EDITOR_MODE, StationEditorWandMode.ZONE_SELECTION.name());
        tag.putBoolean(KEY_SHOW_HANDLES, true);
        tag.putBoolean(KEY_SHOW_ROOT_TEXT, true);
        tag.putBoolean(KEY_LOCK_ROOT_ZONE, false);
        return stack;
    }

    public static boolean isEditorTool(ItemStack stack) {
        return stack.is(StationStructureItems.STATION_STRUCTURE_TOOL.get())
                || (stack.is(Items.STICK) && stack.getOrCreateTag().getBoolean(KEY_EDITOR_STICK));
    }

    public static boolean hasSelection(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        return tag.contains(StationStructureToolItem.KEY_POS_1) && tag.contains(StationStructureToolItem.KEY_POS_2);
    }

    public static void setPosition(ItemStack stack, String key, BlockPos pos) {
        stack.getOrCreateTag().put(key, NbtPos.save(pos));
    }

    public static boolean rootZoneLocked(ItemStack stack) {
        return stack.getOrCreateTag().getBoolean(KEY_LOCK_ROOT_ZONE);
    }

    public static StationEditorWandMode mode(ItemStack stack) {
        return parseMode(stack.getOrCreateTag().getString(KEY_EDITOR_MODE));
    }

    public static void mode(ItemStack stack, StationEditorWandMode mode) {
        stack.getOrCreateTag().putString(KEY_EDITOR_MODE, mode.name());
    }

    public static void cycleMode(ServerPlayer player, int direction) {
        ItemStack stack = player.getMainHandItem();
        if (!isEditorTool(stack)) {
            return;
        }
        StationEditorWandMode next = mode(stack).next(direction);
        mode(stack, next);
        player.displayClientMessage(Component.literal("Station editor mode: " + next.title()).withStyle(ChatFormatting.AQUA), true);
    }

    public static CompoundTag editorTag(ItemStack stack) {
        CompoundTag source = stack.getOrCreateTag();
        CompoundTag editor = new CompoundTag();
        copyIfPresent(source, editor, StationStructureToolItem.KEY_POS_1);
        copyIfPresent(source, editor, StationStructureToolItem.KEY_POS_2);
        editor.putString(StationStructureToolItem.KEY_TEMPLATE, defaultString(source.getString(StationStructureToolItem.KEY_TEMPLATE), "stationarenear:stations/new_piece"));
        editor.putString(StationStructureToolItem.KEY_POOL, defaultString(source.getString(StationStructureToolItem.KEY_POOL), "stationarenear:space_station"));
        editor.putBoolean(StationStructureToolItem.KEY_START_PIECE, source.getBoolean(StationStructureToolItem.KEY_START_PIECE));
        editor.putInt(StationStructureToolItem.KEY_WEIGHT, Math.max(1, source.contains(StationStructureToolItem.KEY_WEIGHT) ? source.getInt(StationStructureToolItem.KEY_WEIGHT) : 1));
        editor.putInt(StationStructureToolItem.KEY_FLOOR_SPAN, Math.max(1, source.contains(StationStructureToolItem.KEY_FLOOR_SPAN) ? source.getInt(StationStructureToolItem.KEY_FLOOR_SPAN) : detectFloorSpan(editor)));
        editor.putString(KEY_EDITOR_MODE, parseMode(source.getString(KEY_EDITOR_MODE)).name());
        editor.putString(KEY_SELECTED_NODE, defaultString(source.getString(KEY_SELECTED_NODE), "root"));
        editor.putBoolean(KEY_SHOW_HANDLES, !source.contains(KEY_SHOW_HANDLES) || source.getBoolean(KEY_SHOW_HANDLES));
        editor.putBoolean(KEY_SHOW_ROOT_TEXT, !source.contains(KEY_SHOW_ROOT_TEXT) || source.getBoolean(KEY_SHOW_ROOT_TEXT));
        editor.putBoolean(KEY_LOCK_ROOT_ZONE, source.getBoolean(KEY_LOCK_ROOT_ZONE));
        copyIfPresent(source, editor, KEY_TRIGGER_DRAFT_POS_1);
        copyIfPresent(source, editor, KEY_TRIGGER_DRAFT_POS_2);
        copyIfPresent(source, editor, KEY_CONNECTION_DRAFT_POS_1);
        copyIfPresent(source, editor, KEY_CONNECTION_DRAFT_POS_2);
        editor.put(StationStructureToolItem.KEY_CONNECTORS, source.getList(StationStructureToolItem.KEY_CONNECTORS, Tag.TAG_COMPOUND).copy());
        editor.put(StationStructureToolItem.KEY_TRIGGER_ZONES, source.getList(StationStructureToolItem.KEY_TRIGGER_ZONES, Tag.TAG_COMPOUND).copy());
        normalize(editor);
        return editor;
    }

    public static void clearEditorTag(ServerPlayer player) {
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(Component.literal("No permission to edit station structures"), true);
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (!isEditorTool(stack)) {
            player.displayClientMessage(Component.literal("Hold station structure editor stick in main hand"), true);
            return;
        }

        clearEditorData(stack.getOrCreateTag());
        player.displayClientMessage(Component.literal("Station editor wand data cleared"), true);
    }

    public static void clearEditorData(CompoundTag tag) {
        tag.remove(StationStructureToolItem.KEY_TEMPLATE);
        tag.remove(StationStructureToolItem.KEY_POOL);
        tag.remove(StationStructureToolItem.KEY_POS_1);
        tag.remove(StationStructureToolItem.KEY_POS_2);
        tag.remove(StationStructureToolItem.KEY_CONNECTOR_NAME);
        tag.remove(StationStructureToolItem.KEY_CONNECTOR_DIRECTION);
        tag.remove(StationStructureToolItem.KEY_CONNECTOR_TAGS);
        tag.remove(StationStructureToolItem.KEY_CONNECTOR_ACCEPTS);
        tag.remove(StationStructureToolItem.KEY_CONNECTORS);
        tag.remove(StationStructureToolItem.KEY_TRIGGER_ZONES);
        tag.remove(StationStructureToolItem.KEY_START_PIECE);
        tag.remove(StationStructureToolItem.KEY_WEIGHT);
        tag.remove(StationStructureToolItem.KEY_FLOOR_SPAN);
        tag.remove(KEY_SELECTED_NODE);
        tag.remove(KEY_TRIGGER_DRAFT_POS_1);
        tag.remove(KEY_TRIGGER_DRAFT_POS_2);
        tag.remove(KEY_CONNECTION_DRAFT_POS_1);
        tag.remove(KEY_CONNECTION_DRAFT_POS_2);
        tag.putString(KEY_EDITOR_MODE, StationEditorWandMode.ZONE_SELECTION.name());
        tag.putBoolean(KEY_SHOW_HANDLES, true);
        tag.putBoolean(KEY_SHOW_ROOT_TEXT, true);
        tag.putBoolean(KEY_LOCK_ROOT_ZONE, false);
    }

    public static void applyEditorTag(ServerPlayer player, CompoundTag incoming) {
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(Component.literal("No permission to edit station structures"), true);
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (!isEditorTool(stack)) {
            player.displayClientMessage(Component.literal("Hold station structure editor stick in main hand"), true);
            return;
        }

        CompoundTag normalized = incoming.copy();
        normalize(normalized);
        CompoundTag target = stack.getOrCreateTag();
        copyIfPresent(normalized, target, StationStructureToolItem.KEY_POS_1);
        copyIfPresent(normalized, target, StationStructureToolItem.KEY_POS_2);
        target.putString(StationStructureToolItem.KEY_TEMPLATE, normalized.getString(StationStructureToolItem.KEY_TEMPLATE));
        target.putString(StationStructureToolItem.KEY_POOL, normalized.getString(StationStructureToolItem.KEY_POOL));
        target.putBoolean(StationStructureToolItem.KEY_START_PIECE, normalized.getBoolean(StationStructureToolItem.KEY_START_PIECE));
        target.putInt(StationStructureToolItem.KEY_WEIGHT, Math.max(1, normalized.getInt(StationStructureToolItem.KEY_WEIGHT)));
        target.putInt(StationStructureToolItem.KEY_FLOOR_SPAN, Math.max(1, normalized.getInt(StationStructureToolItem.KEY_FLOOR_SPAN)));
        target.putString(KEY_EDITOR_MODE, parseMode(normalized.getString(KEY_EDITOR_MODE)).name());
        target.putString(KEY_SELECTED_NODE, defaultString(normalized.getString(KEY_SELECTED_NODE), "root"));
        target.putBoolean(KEY_SHOW_HANDLES, !normalized.contains(KEY_SHOW_HANDLES) || normalized.getBoolean(KEY_SHOW_HANDLES));
        target.putBoolean(KEY_SHOW_ROOT_TEXT, !normalized.contains(KEY_SHOW_ROOT_TEXT) || normalized.getBoolean(KEY_SHOW_ROOT_TEXT));
        target.putBoolean(KEY_LOCK_ROOT_ZONE, normalized.getBoolean(KEY_LOCK_ROOT_ZONE));
        copyOrRemove(normalized, target, KEY_TRIGGER_DRAFT_POS_1);
        copyOrRemove(normalized, target, KEY_TRIGGER_DRAFT_POS_2);
        copyOrRemove(normalized, target, KEY_CONNECTION_DRAFT_POS_1);
        copyOrRemove(normalized, target, KEY_CONNECTION_DRAFT_POS_2);
        target.put(StationStructureToolItem.KEY_CONNECTORS, normalized.getList(StationStructureToolItem.KEY_CONNECTORS, Tag.TAG_COMPOUND).copy());
        target.put(StationStructureToolItem.KEY_TRIGGER_ZONES, normalized.getList(StationStructureToolItem.KEY_TRIGGER_ZONES, Tag.TAG_COMPOUND).copy());
        player.displayClientMessage(Component.literal("Station editor saved: "
                + target.getList(StationStructureToolItem.KEY_CONNECTORS, Tag.TAG_COMPOUND).size() + " connections, "
                + target.getList(StationStructureToolItem.KEY_TRIGGER_ZONES, Tag.TAG_COMPOUND).size() + " triggers"), true);
    }

    public static void normalize(CompoundTag tag) {
        BlockPos min = structureMin(tag);
        BlockPos max = structureMax(tag);

        ResourceLocation template = StationStructureIds.template(tag.getString(StationStructureToolItem.KEY_TEMPLATE));
        ResourceLocation pool = StationStructureIds.pool(tag.getString(StationStructureToolItem.KEY_POOL));

        tag.putString(StationStructureToolItem.KEY_TEMPLATE, template.toString());
        tag.putString(StationStructureToolItem.KEY_POOL, pool.toString());
        tag.putInt(StationStructureToolItem.KEY_WEIGHT, Math.max(1, tag.contains(StationStructureToolItem.KEY_WEIGHT) ? tag.getInt(StationStructureToolItem.KEY_WEIGHT) : 1));
        tag.putInt(StationStructureToolItem.KEY_FLOOR_SPAN, Math.max(1, tag.contains(StationStructureToolItem.KEY_FLOOR_SPAN) ? tag.getInt(StationStructureToolItem.KEY_FLOOR_SPAN) : detectFloorSpan(tag)));
        tag.putString(KEY_EDITOR_MODE, parseMode(tag.getString(KEY_EDITOR_MODE)).name());
        tag.putString(KEY_SELECTED_NODE, defaultString(tag.getString(KEY_SELECTED_NODE), "root"));
        if (!tag.contains(KEY_SHOW_HANDLES)) {
            tag.putBoolean(KEY_SHOW_HANDLES, true);
        }
        if (!tag.contains(KEY_SHOW_ROOT_TEXT)) {
            tag.putBoolean(KEY_SHOW_ROOT_TEXT, true);
        }
        if (!tag.contains(KEY_LOCK_ROOT_ZONE)) {
            tag.putBoolean(KEY_LOCK_ROOT_ZONE, false);
        }

        ListTag normalizedConnectors = new ListTag();
        ListTag connectors = tag.getList(StationStructureToolItem.KEY_CONNECTORS, Tag.TAG_COMPOUND);
        for (int i = 0; i < connectors.size(); i++) {
            CompoundTag connector = connectors.getCompound(i).copy();
            connector.putString("nodeType", StationEditorNodeType.CONNECTION.name());
            connector.putString("name", defaultString(connector.getString("name"), "connection_" + i));
            BlockPos connectorMin = clampPos(posFrom(connector, "worldMin", posFrom(connector, "worldPosition", min)), min, max);
            BlockPos connectorMax = clampPos(posFrom(connector, "worldMax", connectorMin), min, max);
            BlockPos normalizedMin = new BlockPos(Math.min(connectorMin.getX(), connectorMax.getX()), Math.min(connectorMin.getY(), connectorMax.getY()), Math.min(connectorMin.getZ(), connectorMax.getZ()));
            BlockPos normalizedMax = new BlockPos(Math.max(connectorMin.getX(), connectorMax.getX()), Math.max(connectorMin.getY(), connectorMax.getY()), Math.max(connectorMin.getZ(), connectorMax.getZ()));
            connector.put("worldMin", NbtPos.save(normalizedMin));
            connector.put("worldMax", NbtPos.save(normalizedMax));
            connector.put("worldPosition", NbtPos.save(clampPos(posFrom(connector, "worldPosition", normalizedMin), normalizedMin, normalizedMax)));
            Direction direction = Direction.byName(connector.getString("direction"));
            connector.putString("direction", (direction == null ? Direction.NORTH : direction).getSerializedName());
            connector.putString("tags", defaultString(connector.getString("tags"), "corridor"));
            connector.putString("accepts", defaultString(connector.getString("accepts"), "corridor,dock"));
            connector.putInt("width", Math.max(1, connector.contains("width") ? connector.getInt("width") : 3));
            connector.putInt("height", Math.max(1, connector.contains("height") ? connector.getInt("height") : 3));
            connector.putString("acceptedSizes", defaultString(connector.getString("acceptedSizes"), "3x3"));
            normalizedConnectors.add(connector);
        }
        tag.put(StationStructureToolItem.KEY_CONNECTORS, normalizedConnectors);

        ListTag normalizedTriggers = new ListTag();
        ListTag triggers = tag.getList(StationStructureToolItem.KEY_TRIGGER_ZONES, Tag.TAG_COMPOUND);
        for (int i = 0; i < triggers.size(); i++) {
            CompoundTag trigger = triggers.getCompound(i).copy();
            StationEditorNodeType nodeType = parseNodeType(trigger.getString("nodeType"), StationEditorNodeType.TRIGGER);
            if (nodeType == StationEditorNodeType.STRUCTURE || nodeType == StationEditorNodeType.CONNECTION) {
                nodeType = StationEditorNodeType.TRIGGER;
            }
            trigger.putString("nodeType", nodeType.name());
            trigger.putString("id", defaultString(trigger.getString("id"), "trigger_" + i));
            trigger.putString("type", defaultString(trigger.getString("type"), nodeType.name().toLowerCase(java.util.Locale.ROOT)));
            BlockPos worldMin = clampPos(posFrom(trigger, "worldMin", min), min, max);
            BlockPos worldMax = clampPos(posFrom(trigger, "worldMax", worldMin), min, max);
            BlockPos normalizedMin = new BlockPos(Math.min(worldMin.getX(), worldMax.getX()), Math.min(worldMin.getY(), worldMax.getY()), Math.min(worldMin.getZ(), worldMax.getZ()));
            BlockPos normalizedMax = new BlockPos(Math.max(worldMin.getX(), worldMax.getX()), Math.max(worldMin.getY(), worldMax.getY()), Math.max(worldMin.getZ(), worldMax.getZ()));
            trigger.put("worldMin", NbtPos.save(normalizedMin));
            trigger.put("worldMax", NbtPos.save(normalizedMax));
            if (!trigger.contains("data")) {
                trigger.put("data", new CompoundTag());
            }
            normalizedTriggers.add(trigger);
        }
        tag.put(StationStructureToolItem.KEY_TRIGGER_ZONES, normalizedTriggers);
    }

    public static BlockPos structureMin(CompoundTag tag) {
        if (!tag.contains(StationStructureToolItem.KEY_POS_1) || !tag.contains(StationStructureToolItem.KEY_POS_2)) {
            return BlockPos.ZERO;
        }
        BlockPos pos1 = NbtPos.load(tag.getCompound(StationStructureToolItem.KEY_POS_1));
        BlockPos pos2 = NbtPos.load(tag.getCompound(StationStructureToolItem.KEY_POS_2));
        return new BlockPos(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()));
    }

    public static BlockPos structureMax(CompoundTag tag) {
        if (!tag.contains(StationStructureToolItem.KEY_POS_1) || !tag.contains(StationStructureToolItem.KEY_POS_2)) {
            return BlockPos.ZERO;
        }
        BlockPos pos1 = NbtPos.load(tag.getCompound(StationStructureToolItem.KEY_POS_1));
        BlockPos pos2 = NbtPos.load(tag.getCompound(StationStructureToolItem.KEY_POS_2));
        return new BlockPos(Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()), Math.max(pos1.getZ(), pos2.getZ()));
    }

    private static int detectFloorSpan(CompoundTag tag) {
        if (!tag.contains(StationStructureToolItem.KEY_POS_1) || !tag.contains(StationStructureToolItem.KEY_POS_2)) {
            return 1;
        }
        BlockPos min = structureMin(tag);
        BlockPos max = structureMax(tag);
        return Math.max(1, (max.getY() - min.getY() + 16) / 16);
    }

    private static StationEditorNodeType parseNodeType(String value, StationEditorNodeType fallback) {
        try {
            return StationEditorNodeType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static StationEditorWandMode parseMode(String value) {
        try {
            return StationEditorWandMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return StationEditorWandMode.ZONE_SELECTION;
        }
    }

    private static BlockPos posFrom(CompoundTag tag, String key, BlockPos fallback) {
        return tag.contains(key) ? NbtPos.load(tag.getCompound(key)) : fallback;
    }

    private static BlockPos clampPos(BlockPos pos, BlockPos min, BlockPos max) {
        return new BlockPos(
                net.minecraft.util.Mth.clamp(pos.getX(), min.getX(), max.getX()),
                net.minecraft.util.Mth.clamp(pos.getY(), min.getY(), max.getY()),
                net.minecraft.util.Mth.clamp(pos.getZ(), min.getZ(), max.getZ())
        );
    }

    private static void copyIfPresent(CompoundTag source, CompoundTag target, String key) {
        if (source.contains(key)) {
            target.put(key, source.get(key).copy());
        }
    }

    private static void copyOrRemove(CompoundTag source, CompoundTag target, String key) {
        if (source.contains(key)) {
            target.put(key, source.get(key).copy());
        } else {
            target.remove(key);
        }
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
