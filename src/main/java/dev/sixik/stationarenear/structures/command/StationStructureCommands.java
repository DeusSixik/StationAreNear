package dev.sixik.stationarenear.structures.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.sixik.stationarenear.structures.editor.StationStructureEditorStick;
import dev.sixik.stationarenear.navigation.registry.SolarNavigationBlocks;
import dev.sixik.stationarenear.navigation.ship.ShipDockingAnchor;
import dev.sixik.stationarenear.navigation.ship.ShipDockingAnchorResolver;
import dev.sixik.stationarenear.navigation.ship.ShipDockingAnchorSavedData;
import dev.sixik.stationarenear.structures.data.StationPieceDefinition;
import dev.sixik.stationarenear.structures.data.StationPoolDefinition;
import dev.sixik.stationarenear.structures.generation.StationGenerationResult;
import dev.sixik.stationarenear.structures.generation.StationGenerationSettings;
import dev.sixik.stationarenear.structures.generation.StationGenerator;
import dev.sixik.stationarenear.structures.generation.StationPlacementUtil;
import dev.sixik.stationarenear.structures.item.StationStructureToolItem;
import dev.sixik.stationarenear.structures.network.StationStructureNetwork;
import dev.sixik.stationarenear.structures.registry.StationStructureItems;
import dev.sixik.stationarenear.structures.util.StationStructureIds;
import dev.sixik.stationarenear.structures.world.StationSavedData;
import dev.sixik.stationarenear.structures.world.StationStructureLibraryData;
import dev.sixik.stationarenear.structures.util.NbtPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class StationStructureCommands {

    private static final ResourceLocation SPACE_SHIP_POOL = StationStructureIds.pool("space_ship");

    private StationStructureCommands() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(StationStructureCommands::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("stationarenear")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("structures")
                        .then(Commands.literal("tool")
                                .then(Commands.argument("template", ResourceLocationArgument.id())
                                        .then(Commands.argument("pool", ResourceLocationArgument.id())
                                                .executes(context -> configureTool(
                                                        context.getSource(),
                                                        ResourceLocationArgument.getId(context, "template"),
                                                        ResourceLocationArgument.getId(context, "pool")
                                                )))))
                        .then(Commands.literal("editor_stick")
                                .executes(context -> giveEditorStick(context.getSource())))
                        .then(Commands.literal("connector")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("direction", StringArgumentType.word())
                                                .then(Commands.argument("tags", StringArgumentType.word())
                                                        .then(Commands.argument("accepts", StringArgumentType.word())
                                                                .executes(context -> configureConnector(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(context, "name"),
                                                                        StringArgumentType.getString(context, "direction"),
                                                                        StringArgumentType.getString(context, "tags"),
                                                                        StringArgumentType.getString(context, "accepts")
                                                                )))))))
                        .then(Commands.literal("add_connector")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .then(Commands.argument("direction", StringArgumentType.word())
                                                        .then(Commands.argument("tags", StringArgumentType.word())
                                                                .then(Commands.argument("accepts", StringArgumentType.word())
                                                                        .then(Commands.argument("priority", IntegerArgumentType.integer())
                                                                                .executes(context -> addConnector(
                                                                                        context.getSource(),
                                                                                        StringArgumentType.getString(context, "name"),
                                                                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                                                        StringArgumentType.getString(context, "direction"),
                                                                                        StringArgumentType.getString(context, "tags"),
                                                                                        StringArgumentType.getString(context, "accepts"),
                                                                                        IntegerArgumentType.getInteger(context, "priority")
                                                                                )))))))))
                        .then(Commands.literal("add_trigger_zone")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .then(Commands.argument("min", BlockPosArgument.blockPos())
                                                        .then(Commands.argument("max", BlockPosArgument.blockPos())
                                                                .executes(context -> addTriggerZone(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(context, "id"),
                                                                        StringArgumentType.getString(context, "type"),
                                                                        BlockPosArgument.getLoadedBlockPos(context, "min"),
                                                                        BlockPosArgument.getLoadedBlockPos(context, "max")
                                                                )))))))
                        .then(Commands.literal("start_piece")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(context -> configureStartPiece(context.getSource(), BoolArgumentType.getBool(context, "value")))))
                        .then(shipAnchorCommand())
                        .then(Commands.literal("weight")
                                .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                        .executes(context -> configureWeight(context.getSource(), IntegerArgumentType.getInteger(context, "value")))))
                        .then(Commands.literal("generate")
                                .then(Commands.argument("pool", ResourceLocationArgument.id())
                                        .then(Commands.argument("danger", FloatArgumentType.floatArg(0.0F, 1.0F))
                                                .then(Commands.argument("pieces", IntegerArgumentType.integer(1))
                                                        .executes(context -> generate(
                                                                context.getSource(),
                                                                ResourceLocationArgument.getId(context, "pool"),
                                                                FloatArgumentType.getFloat(context, "danger"),
                                                                IntegerArgumentType.getInteger(context, "pieces")
                                                        ))))))
                        .then(generateAtCommand())
                        .then(spawnSpaceShipCommand())
                        .then(Commands.literal("list_generated")
                                .executes(context -> listGenerated(context.getSource())))
                        .then(Commands.literal("clear_generated")
                                .then(Commands.argument("station", StringArgumentType.word())
                                        .suggests(StationStructureCommands::suggestGeneratedStations)
                                        .executes(context -> clearGenerated(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "station")
                                        ))))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> generateAtCommand() {
        var dangerArgument = Commands.argument("danger", FloatArgumentType.floatArg(0.0F, 1.0F))
                .executes(context -> generateAt(
                        context.getSource(),
                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                        StringArgumentType.getString(context, "direction"),
                        StringArgumentType.getString(context, "pool"),
                        IntegerArgumentType.getInteger(context, "max_floors"),
                        IntegerArgumentType.getInteger(context, "max_rooms"),
                        IntegerArgumentType.getInteger(context, "min_rooms"),
                        FloatArgumentType.getFloat(context, "danger")
                ));
        var minRoomsArgument = Commands.argument("min_rooms", IntegerArgumentType.integer(1))
                .executes(context -> generateAt(
                        context.getSource(),
                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                        StringArgumentType.getString(context, "direction"),
                        StringArgumentType.getString(context, "pool"),
                        IntegerArgumentType.getInteger(context, "max_floors"),
                        IntegerArgumentType.getInteger(context, "max_rooms"),
                        IntegerArgumentType.getInteger(context, "min_rooms"),
                        0.5F
                ))
                .then(dangerArgument);
        var maxRoomsArgument = Commands.argument("max_rooms", IntegerArgumentType.integer(1))
                .executes(context -> generateAt(
                        context.getSource(),
                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                        StringArgumentType.getString(context, "direction"),
                        StringArgumentType.getString(context, "pool"),
                        IntegerArgumentType.getInteger(context, "max_floors"),
                        IntegerArgumentType.getInteger(context, "max_rooms"),
                        10,
                        0.5F
                ))
                .then(minRoomsArgument);
        return Commands.literal("generate_at")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("direction", StringArgumentType.word())
                                .suggests(StationStructureCommands::suggestDirections)
                                .then(Commands.argument("pool", StringArgumentType.word())
                                        .suggests(StationStructureCommands::suggestPools)
                                        .then(Commands.argument("max_floors", IntegerArgumentType.integer(1))
                                                .then(maxRoomsArgument)))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> spawnSpaceShipCommand() {
        return Commands.literal("spawn_space_ship")
                .executes(context -> spawnSpaceShip(context.getSource(), context.getSource().getPlayerOrException().blockPosition(), null))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> spawnSpaceShip(
                                context.getSource(),
                                BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                null
                        ))
                        .then(Commands.argument("template", StringArgumentType.word())
                                .suggests(StationStructureCommands::suggestSpaceShipPieces)
                                .executes(context -> spawnSpaceShip(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                        StringArgumentType.getString(context, "template")
                                ))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> shipAnchorCommand() {
        return Commands.literal("ship_anchor")
                .then(Commands.literal("bind")
                        .then(Commands.argument("terminal_pos", BlockPosArgument.blockPos())
                                .executes(context -> autoBindShipAnchor(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "terminal_pos")
                                ))))
                .then(Commands.literal("bind_from_wand")
                        .then(Commands.argument("terminal_pos", BlockPosArgument.blockPos())
                                .executes(context -> bindShipAnchorFromWand(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "terminal_pos"),
                                        null
                                ))
                                .then(Commands.argument("connection", StringArgumentType.word())
                                        .suggests(StationStructureCommands::suggestHeldConnections)
                                        .executes(context -> bindShipAnchorFromWand(
                                                context.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(context, "terminal_pos"),
                                                StringArgumentType.getString(context, "connection")
                                        )))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("terminal_pos", BlockPosArgument.blockPos())
                                .executes(context -> clearShipAnchor(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "terminal_pos")
                                ))))
                .then(Commands.literal("info")
                        .then(Commands.argument("terminal_pos", BlockPosArgument.blockPos())
                                .executes(context -> showShipAnchor(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "terminal_pos")
                                ))))
                .then(Commands.literal("list")
                        .executes(context -> listShipAnchors(context.getSource())));
    }

    private static CompletableFuture<Suggestions> suggestDirections(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(List.of("north", "south", "east", "west"), builder);
    }

    private static CompletableFuture<Suggestions> suggestPools(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> suggestions = new ArrayList<>();
        for (var pool : StationStructureLibraryData.get(context.getSource().getLevel()).pools()) {
            suggestions.add(pool.id().toString());
            suggestions.add(pool.id().getPath());
        }
        return SharedSuggestionProvider.suggest(suggestions, builder);
    }

    private static CompletableFuture<Suggestions> suggestGeneratedStations(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("all");
        for (var station : StationSavedData.get(context.getSource().getLevel()).stations()) {
            String id = station.id().toString();
            suggestions.add(id);
            suggestions.add(id.substring(0, 8));
        }
        return SharedSuggestionProvider.suggest(suggestions, builder);
    }

    private static CompletableFuture<Suggestions> suggestSpaceShipPieces(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> suggestions = new ArrayList<>();
        StationStructureLibraryData library = StationStructureLibraryData.get(context.getSource().getLevel());
        library.pool(SPACE_SHIP_POOL).ifPresent(pool -> {
            for (ResourceLocation id : allPoolPieces(pool)) {
                suggestions.add(id.toString());
                suggestions.add(id.getPath());
            }
        });
        return SharedSuggestionProvider.suggest(suggestions, builder);
    }

    private static CompletableFuture<Suggestions> suggestHeldConnections(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> suggestions = new ArrayList<>();
        try {
            ItemStack stack = context.getSource().getPlayerOrException().getMainHandItem();
            if (StationStructureEditorStick.isEditorTool(stack)) {
                CompoundTag editorTag = StationStructureEditorStick.editorTag(stack);
                ListTag connectors = editorTag.getList(StationStructureToolItem.KEY_CONNECTORS, Tag.TAG_COMPOUND);
                for (int i = 0; i < connectors.size(); i++) {
                    suggestions.add(connectors.getCompound(i).getString("name"));
                }
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ignored) {
            return Suggestions.empty();
        }
        return SharedSuggestionProvider.suggest(suggestions, builder);
    }

    private static int configureTool(CommandSourceStack source, ResourceLocation template, ResourceLocation pool) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ItemStack stack = heldTool(source.getPlayerOrException());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(StationStructureToolItem.KEY_TEMPLATE, template.toString());
        tag.putString(StationStructureToolItem.KEY_POOL, pool.toString());
        tag.putBoolean(StationStructureToolItem.KEY_START_PIECE, false);
        source.sendSuccess(() -> Component.literal("Configured station structure tool: " + template + " -> " + pool), false);
        return 1;
    }

    private static int giveEditorStick(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = StationStructureEditorStick.create();
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        source.sendSuccess(() -> Component.literal("Issued station structure editor stick"), false);
        return 1;
    }

    private static int configureConnector(CommandSourceStack source, String name, String directionName, String tags, String accepts) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Direction direction = Direction.byName(directionName);
        if (direction == null) {
            source.sendFailure(Component.literal("Unknown direction: " + directionName));
            return 0;
        }

        ItemStack stack = heldTool(source.getPlayerOrException());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(StationStructureToolItem.KEY_CONNECTOR_NAME, name);
        tag.putString(StationStructureToolItem.KEY_CONNECTOR_DIRECTION, direction.getSerializedName());
        tag.putString(StationStructureToolItem.KEY_CONNECTOR_TAGS, tags);
        tag.putString(StationStructureToolItem.KEY_CONNECTOR_ACCEPTS, accepts);
        source.sendSuccess(() -> Component.literal("Configured station connector " + name), false);
        return 1;
    }

    private static int addConnector(CommandSourceStack source, String name, BlockPos position, String directionName, String tags, String accepts, int priority) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Direction direction = Direction.byName(directionName);
        if (direction == null) {
            source.sendFailure(Component.literal("Unknown direction: " + directionName));
            return 0;
        }

        ItemStack stack = heldTool(source.getPlayerOrException());
        CompoundTag tag = stack.getOrCreateTag();
        ListTag connectors = tag.getList(StationStructureToolItem.KEY_CONNECTORS, Tag.TAG_COMPOUND);
        CompoundTag connector = new CompoundTag();
        connector.putString("name", name);
        connector.put("worldPosition", NbtPos.save(position));
        connector.putString("direction", direction.getSerializedName());
        connector.putString("tags", tags);
        connector.putString("accepts", accepts);
        connector.putInt("priority", priority);
        connectors.add(connector);
        tag.put(StationStructureToolItem.KEY_CONNECTORS, connectors);
        source.sendSuccess(() -> Component.literal("Added station connector " + name + " at " + position.toShortString()), false);
        return connectors.size();
    }

    private static int addTriggerZone(CommandSourceStack source, String id, String type, BlockPos min, BlockPos max) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ItemStack stack = heldTool(source.getPlayerOrException());
        CompoundTag tag = stack.getOrCreateTag();
        ListTag triggerZones = tag.getList(StationStructureToolItem.KEY_TRIGGER_ZONES, Tag.TAG_COMPOUND);

        BlockPos normalizedMin = new BlockPos(
                Math.min(min.getX(), max.getX()),
                Math.min(min.getY(), max.getY()),
                Math.min(min.getZ(), max.getZ())
        );
        BlockPos normalizedMax = new BlockPos(
                Math.max(min.getX(), max.getX()),
                Math.max(min.getY(), max.getY()),
                Math.max(min.getZ(), max.getZ())
        );

        CompoundTag triggerZone = new CompoundTag();
        triggerZone.putString("id", id);
        triggerZone.putString("type", type);
        triggerZone.put("worldMin", NbtPos.save(normalizedMin));
        triggerZone.put("worldMax", NbtPos.save(normalizedMax));
        triggerZone.put("data", new CompoundTag());
        triggerZones.add(triggerZone);
        tag.put(StationStructureToolItem.KEY_TRIGGER_ZONES, triggerZones);
        source.sendSuccess(() -> Component.literal("Added station trigger zone " + id), false);
        return triggerZones.size();
    }

    private static int configureStartPiece(CommandSourceStack source, boolean value) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ItemStack stack = heldTool(source.getPlayerOrException());
        stack.getOrCreateTag().putBoolean(StationStructureToolItem.KEY_START_PIECE, value);
        source.sendSuccess(() -> Component.literal("Station start piece: " + value), false);
        return 1;
    }

    private static int configureWeight(CommandSourceStack source, int value) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ItemStack stack = heldTool(source.getPlayerOrException());
        stack.getOrCreateTag().putInt(StationStructureToolItem.KEY_WEIGHT, value);
        source.sendSuccess(() -> Component.literal("Station piece weight: " + value), false);
        return 1;
    }

    private static int autoBindShipAnchor(CommandSourceStack source, BlockPos terminalPos) {
        ServerLevel level = source.getLevel();
        if (!level.getBlockState(terminalPos).is(SolarNavigationBlocks.SOLAR_NAVIGATION_TERMINAL.get())) {
            source.sendFailure(Component.literal("Solar Navigation Terminal not found at " + terminalPos.toShortString()));
            return 0;
        }

        Optional<ShipDockingAnchor> anchor = ShipDockingAnchorResolver.bindNearbyShip(level, terminalPos);
        if (anchor.isEmpty()) {
            source.sendFailure(Component.literal("No saved stationarenear:space_ship template zone found near terminal " + terminalPos.toShortString()));
            return 0;
        }

        ShipDockingAnchor value = anchor.get();
        source.sendSuccess(() -> Component.literal("Auto-bound ship docking anchor " + value.connectionName()
                + " at " + value.anchorPos().toShortString()
                + " facing " + value.direction().getSerializedName()
                + " to terminal " + terminalPos.toShortString()), false);
        return 1;
    }

    private static int bindShipAnchorFromWand(CommandSourceStack source, BlockPos terminalPos, String connectionName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = source.getLevel();
        if (!level.getBlockState(terminalPos).is(SolarNavigationBlocks.SOLAR_NAVIGATION_TERMINAL.get())) {
            source.sendFailure(Component.literal("Solar Navigation Terminal not found at " + terminalPos.toShortString()));
            return 0;
        }

        ItemStack stack = source.getPlayerOrException().getMainHandItem();
        if (!StationStructureEditorStick.isEditorTool(stack)) {
            source.sendFailure(Component.literal("Hold Station Structure Editor wand with ship zone and connection."));
            return 0;
        }

        CompoundTag editorTag = StationStructureEditorStick.editorTag(stack);
        if (!editorTag.contains(StationStructureToolItem.KEY_POS_1) || !editorTag.contains(StationStructureToolItem.KEY_POS_2)) {
            source.sendFailure(Component.literal("Select ship Root zone POS_1/POS_2 before binding anchor."));
            return 0;
        }

        CompoundTag connector = findShipAnchorConnector(editorTag, connectionName);
        if (connector == null) {
            source.sendFailure(Component.literal("Ship connection not found. Create/select a Connection in the editor first."));
            return 0;
        }

        Direction direction = Direction.byName(connector.getString("direction"));
        if (direction == null || direction.getAxis().isVertical()) {
            source.sendFailure(Component.literal("Ship docking connection must face north/south/east/west."));
            return 0;
        }

        BlockPos rootMin = StationStructureEditorStick.structureMin(editorTag);
        BlockPos rootMax = StationStructureEditorStick.structureMax(editorTag);
        BlockPos anchorPos = NbtPos.load(connector.getCompound("worldPosition"));
        ShipDockingAnchor anchor = new ShipDockingAnchor(
                terminalPos,
                new BoundingBox(rootMin.getX(), rootMin.getY(), rootMin.getZ(), rootMax.getX(), rootMax.getY(), rootMax.getZ()),
                anchorPos,
                direction,
                connector.getString("name"),
                Math.max(1, connector.contains("width") ? connector.getInt("width") : 1),
                Math.max(1, connector.contains("height") ? connector.getInt("height") : 1),
                connector.getString("tags"),
                connector.getString("accepts")
        );
        ShipDockingAnchorSavedData.get(level).upsert(anchor);
        source.sendSuccess(() -> Component.literal("Bound ship docking anchor " + anchor.connectionName()
                + " at " + anchor.anchorPos().toShortString()
                + " facing " + anchor.direction().getSerializedName()
                + " to terminal " + terminalPos.toShortString()), false);
        return 1;
    }

    private static CompoundTag findShipAnchorConnector(CompoundTag editorTag, String connectionName) {
        ListTag connectors = editorTag.getList(StationStructureToolItem.KEY_CONNECTORS, Tag.TAG_COMPOUND);
        if (connectors.isEmpty()) {
            return null;
        }

        if (connectionName != null && !connectionName.isBlank()) {
            for (int i = 0; i < connectors.size(); i++) {
                CompoundTag connector = connectors.getCompound(i);
                if (connector.getString("name").equalsIgnoreCase(connectionName)) {
                    return connector;
                }
            }
            return null;
        }

        String selectedNode = editorTag.getString(StationStructureEditorStick.KEY_SELECTED_NODE);
        if (selectedNode.startsWith("connection:")) {
            try {
                int index = Integer.parseInt(selectedNode.substring("connection:".length()));
                if (index >= 0 && index < connectors.size()) {
                    return connectors.getCompound(index);
                }
            } catch (NumberFormatException ignored) {
                return connectors.getCompound(0);
            }
        }
        return connectors.getCompound(0);
    }

    private static int clearShipAnchor(CommandSourceStack source, BlockPos terminalPos) {
        boolean removed = ShipDockingAnchorSavedData.get(source.getLevel()).remove(terminalPos);
        if (!removed) {
            source.sendFailure(Component.literal("No ship docking anchor bound to terminal " + terminalPos.toShortString()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Cleared ship docking anchor for terminal " + terminalPos.toShortString()), false);
        return 1;
    }

    private static int showShipAnchor(CommandSourceStack source, BlockPos terminalPos) {
        Optional<ShipDockingAnchor> anchor = ShipDockingAnchorSavedData.get(source.getLevel()).anchor(terminalPos);
        if (anchor.isEmpty()) {
            source.sendFailure(Component.literal("No ship docking anchor bound to terminal " + terminalPos.toShortString()));
            return 0;
        }
        ShipDockingAnchor value = anchor.get();
        source.sendSuccess(() -> Component.literal("Ship anchor terminal=" + value.terminalPos().toShortString()
                + " connection=" + value.connectionName()
                + " anchor=" + value.anchorPos().toShortString()
                + " direction=" + value.direction().getSerializedName()
                + " shipBounds=" + formatBounds(value.shipBounds())), false);
        return 1;
    }

    private static int listShipAnchors(CommandSourceStack source) {
        Collection<ShipDockingAnchor> anchors = ShipDockingAnchorSavedData.get(source.getLevel()).anchors();
        if (anchors.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No ship docking anchors saved in this dimension."), false);
            return 0;
        }
        for (ShipDockingAnchor anchor : anchors) {
            source.sendSuccess(() -> Component.literal("terminal=" + anchor.terminalPos().toShortString()
                    + " connection=" + anchor.connectionName()
                    + " anchor=" + anchor.anchorPos().toShortString()
                    + " direction=" + anchor.direction().getSerializedName()), false);
        }
        return anchors.size();
    }

    private static int spawnSpaceShip(CommandSourceStack source, BlockPos origin, String templateText) {
        ServerLevel level = source.getLevel();
        StationStructureLibraryData library = StationStructureLibraryData.get(level);
        Optional<StationPieceDefinition> piece = resolveSpaceShipPiece(library, templateText);
        if (piece.isEmpty()) {
            source.sendFailure(Component.literal("No stationarenear:space_ship template found. Save a ship piece into pool space_ship first."));
            return 0;
        }

        StationPieceDefinition definition = piece.get();
        Optional<StructureTemplate> template = level.getStructureManager().get(definition.template());
        if (template.isEmpty()) {
            source.sendFailure(Component.literal("Missing structure template file: " + definition.template()));
            return 0;
        }

        template.get().placeInWorld(level, origin, origin, new StructurePlaceSettings().setRotation(Rotation.NONE), level.getRandom(), 2);
        BoundingBox selectionBounds = StationPlacementUtil.transformBox(origin, definition.selectionMin(), definition.selectionMax(), Rotation.NONE);
        library.upsertTemplateSelection(definition.template(), selectionBounds);
        StationStructureNetwork.syncTemplateSelections(level);
        source.sendSuccess(() -> Component.literal("Spawned space ship " + definition.id()
                + " at " + origin.toShortString()
                + " bounds=" + formatBounds(selectionBounds)), false);
        return 1;
    }

    private static Optional<StationPieceDefinition> resolveSpaceShipPiece(StationStructureLibraryData library, String templateText) {
        Optional<StationPoolDefinition> pool = library.pool(SPACE_SHIP_POOL);
        if (pool.isEmpty()) {
            return Optional.empty();
        }

        List<ResourceLocation> pieceIds = allPoolPieces(pool.get());
        if (pieceIds.isEmpty()) {
            return Optional.empty();
        }

        if (templateText == null || templateText.isBlank()) {
            return library.piece(pieceIds.get(0));
        }

        ResourceLocation requested = StationStructureIds.template(templateText);
        for (ResourceLocation pieceId : pieceIds) {
            Optional<StationPieceDefinition> piece = library.piece(pieceId);
            if (piece.isPresent() && (piece.get().id().equals(requested) || piece.get().template().equals(requested))) {
                return piece;
            }
        }
        return Optional.empty();
    }

    private static List<ResourceLocation> allPoolPieces(StationPoolDefinition pool) {
        List<ResourceLocation> pieces = new ArrayList<>();
        pieces.addAll(pool.startPieces());
        for (ResourceLocation roomPiece : pool.roomPieces()) {
            if (!pieces.contains(roomPiece)) {
                pieces.add(roomPiece);
            }
        }
        return pieces;
    }

    private static int generate(CommandSourceStack source, ResourceLocation pool, float danger, int pieces) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos doorCenter = player.blockPosition();
        Direction stationDirection = player.getDirection();
        StationGenerationResult result = new StationGenerator().generateDockedStation(
                source.getLevel(),
                doorCenter,
                stationDirection,
                new StationGenerationSettings(pool, danger, true, pieces, source.getLevel().getRandom().nextLong())
        );

        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(result.message()), false);
        return result.station().map(station -> station.pieces().size()).orElse(1);
    }

    private static int generateAt(CommandSourceStack source, BlockPos doorCenter, String directionName, String poolText, int maxFloors, int maxRooms, int minRooms, float danger) {
        Direction stationDirection = Direction.byName(directionName);
        if (stationDirection == null || stationDirection.getAxis().isVertical()) {
            source.sendFailure(Component.literal("Direction must be one of: north, south, east, west"));
            return 0;
        }

        ResourceLocation pool = StationStructureIds.pool(poolText);
        StationGenerationResult result = new StationGenerator().generateDockedStation(
                source.getLevel(),
                doorCenter,
                stationDirection,
                new StationGenerationSettings(pool, danger, true, maxFloors, minRooms, maxRooms, source.getLevel().getRandom().nextLong())
        );

        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }

        result.station().ifPresent(station -> source.sendSuccess(() -> Component.literal(
                "Generated station " + station.id() + " pool=" + station.pool()
                        + " pieces=" + station.pieces().size()
                        + " bounds=" + formatBounds(aggregateBounds(station))
        ), false));
        return result.station().map(station -> station.pieces().size()).orElse(1);
    }

    private static int listGenerated(CommandSourceStack source) {
        List<dev.sixik.stationarenear.structures.data.StationInstance> stations = new ArrayList<>(StationSavedData.get(source.getLevel()).stations());
        if (stations.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No generated stations saved in this dimension."), false);
            return 0;
        }

        for (int i = 0; i < stations.size(); i++) {
            var station = stations.get(i);
            int displayStationIndex = i + 1;
            source.sendSuccess(() -> Component.literal(
                    "#" + displayStationIndex + " " + station.id() + " pool=" + station.pool()
                            + " pieces=" + station.pieces().size()
                            + " stationBounds=" + formatBounds(aggregateBounds(station))
            ), false);
            for (int pieceIndex = 0; pieceIndex < station.pieces().size(); pieceIndex++) {
                var piece = station.pieces().get(pieceIndex);
                int displayPieceIndex = pieceIndex + 1;
                source.sendSuccess(() -> Component.literal(
                        "  piece #" + displayPieceIndex + " template=" + piece.template()
                                + " pos_a=" + formatPos(piece.bounds().minX(), piece.bounds().minY(), piece.bounds().minZ())
                                + " pos_b=" + formatPos(piece.bounds().maxX(), piece.bounds().maxY(), piece.bounds().maxZ())
                ), false);
            }
        }
        return stations.size();
    }

    private static int clearGenerated(CommandSourceStack source, String stationText) {
        ServerLevel level = source.getLevel();
        StationSavedData data = StationSavedData.get(level);
        List<dev.sixik.stationarenear.structures.data.StationInstance> targets = new ArrayList<>();
        if (stationText.equalsIgnoreCase("all")) {
            targets.addAll(data.stations());
        } else {
            Optional<dev.sixik.stationarenear.structures.data.StationInstance> station = findStation(data, stationText);
            if (station.isEmpty()) {
                source.sendFailure(Component.literal("Generated station not found: " + stationText));
                return 0;
            }
            targets.add(station.get());
        }

        for (var station : targets) {
            for (var piece : station.pieces()) {
                clearBounds(level, piece.bounds());
            }
            data.removeStation(station.id());
        }
        StationStructureNetwork.syncTemplateSelections(level);
        source.sendSuccess(() -> Component.literal("Cleared generated stations: " + targets.size()), false);
        return targets.size();
    }

    private static Optional<dev.sixik.stationarenear.structures.data.StationInstance> findStation(StationSavedData data, String stationText) {
        try {
            UUID id = UUID.fromString(stationText);
            return data.station(id);
        } catch (IllegalArgumentException ignored) {
            return data.stations().stream()
                    .filter(station -> station.id().toString().startsWith(stationText))
                    .findFirst();
        }
    }

    private static BoundingBox aggregateBounds(dev.sixik.stationarenear.structures.data.StationInstance station) {
        BoundingBox result = null;
        for (var piece : station.pieces()) {
            BoundingBox bounds = piece.bounds();
            if (result == null) {
                result = new BoundingBox(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
            } else {
                result = new BoundingBox(
                        Math.min(result.minX(), bounds.minX()),
                        Math.min(result.minY(), bounds.minY()),
                        Math.min(result.minZ(), bounds.minZ()),
                        Math.max(result.maxX(), bounds.maxX()),
                        Math.max(result.maxY(), bounds.maxY()),
                        Math.max(result.maxZ(), bounds.maxZ())
                );
            }
        }
        return result == null ? new BoundingBox(0, 0, 0, 0, 0, 0) : result;
    }

    private static void clearBounds(ServerLevel level, BoundingBox bounds) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    level.setBlock(mutable.set(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static String formatBounds(BoundingBox bounds) {
        return "pos_a=" + formatPos(bounds.minX(), bounds.minY(), bounds.minZ())
                + " pos_b=" + formatPos(bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }

    private static String formatPos(int x, int y, int z) {
        return x + " " + y + " " + z;
    }

    private static ItemStack heldTool(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(StationStructureItems.STATION_STRUCTURE_TOOL.get())) {
            stack = new ItemStack(StationStructureItems.STATION_STRUCTURE_TOOL.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        }
        return stack;
    }
}
