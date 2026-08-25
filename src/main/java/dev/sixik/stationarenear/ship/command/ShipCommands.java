package dev.sixik.stationarenear.ship.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.sixik.stationarenear.navigation.registry.SolarNavigationBlocks;
import dev.sixik.stationarenear.ship.block.ShipTelevisionBlock;
import dev.sixik.stationarenear.ship.block.entity.ShipTelevisionBlockEntity;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchor;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorResolver;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorSavedData;
import dev.sixik.stationarenear.ship.runtime.ShipDecompressionEffects;
import dev.sixik.stationarenear.structures.data.StationPieceDefinition;
import dev.sixik.stationarenear.structures.data.StationPoolDefinition;
import dev.sixik.stationarenear.structures.editor.StationStructureEditorStick;
import dev.sixik.stationarenear.structures.generation.StationPlacementUtil;
import dev.sixik.stationarenear.structures.item.StationStructureToolItem;
import dev.sixik.stationarenear.structures.network.StationStructureNetwork;
import dev.sixik.stationarenear.structures.util.NbtPos;
import dev.sixik.stationarenear.structures.util.StationStructureIds;
import dev.sixik.stationarenear.structures.world.StationStructureLibraryData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
import java.util.concurrent.CompletableFuture;

public final class ShipCommands {

    private static final ResourceLocation SPACE_SHIP_POOL = StationStructureIds.pool("space_ship");

    private ShipCommands() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ShipCommands::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("stationarenear")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("ship")
                        .then(spawnSpaceShipCommand())
                        .then(decompressionCommand())
                        .then(shipAnchorCommand())
                        .then(televisionCommand())));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> televisionCommand() {
        return Commands.literal("television")
                .then(Commands.literal("set")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(context -> setTelevisionText(
                                                context.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                StringArgumentType.getString(context, "text")
                                        )))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> setTelevisionText(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                        ""
                                ))));
    }

    private static int setTelevisionText(CommandSourceStack source, BlockPos pos, String text) {
        ServerLevel level = source.getLevel();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ShipTelevisionBlock)) {
            source.sendFailure(Component.literal("No ship television at " + pos.toShortString()));
            return 0;
        }

        BlockPos masterPos = ShipTelevisionBlock.masterPos(pos, state);
        BlockEntity blockEntity = level.getBlockEntity(masterPos);
        if (!(blockEntity instanceof ShipTelevisionBlockEntity television)) {
            source.sendFailure(Component.literal("Ship television has no block entity at " + masterPos.toShortString()));
            return 0;
        }

        television.text(text);
        if (text == null || text.isBlank()) {
            source.sendSuccess(() -> Component.literal("Cleared ship television text at " + masterPos.toShortString()), true);
        } else {
            source.sendSuccess(() -> Component.literal("Updated ship television text at " + masterPos.toShortString()), true);
        }
        return 1;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> decompressionCommand() {
        return Commands.literal("decompression")
                .then(Commands.literal("status")
                        .executes(context -> showDecompressionEffects(context.getSource())))
                .then(Commands.literal("enable")
                        .executes(context -> setDecompressionEffects(context.getSource(), true)))
                .then(Commands.literal("disable")
                        .executes(context -> setDecompressionEffects(context.getSource(), false)));
    }

    private static int showDecompressionEffects(CommandSourceStack source) {
        boolean enabled = ShipDecompressionEffects.enabled(source.getLevel());
        source.sendSuccess(() -> Component.literal("Ship decompression effects are " + (enabled ? "enabled" : "disabled") + "."), false);
        return enabled ? 1 : 0;
    }

    private static int setDecompressionEffects(CommandSourceStack source, boolean enabled) {
        ShipDecompressionEffects.enabled(source.getLevel(), enabled);
        source.sendSuccess(() -> Component.literal("Ship decompression effects " + (enabled ? "enabled" : "disabled") + "."), true);
        return 1;
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
                                .suggests(ShipCommands::suggestSpaceShipPieces)
                                .executes(context -> spawnSpaceShip(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                        StringArgumentType.getString(context, "template")
                                ))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> shipAnchorCommand() {
        return Commands.literal("anchor")
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
                                        .suggests(ShipCommands::suggestHeldConnections)
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

    private static String formatBounds(BoundingBox bounds) {
        return "pos_a=" + bounds.minX() + " " + bounds.minY() + " " + bounds.minZ()
                + " pos_b=" + bounds.maxX() + " " + bounds.maxY() + " " + bounds.maxZ();
    }
}
