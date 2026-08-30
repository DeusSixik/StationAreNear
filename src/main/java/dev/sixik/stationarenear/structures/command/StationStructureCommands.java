package dev.sixik.stationarenear.structures.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.sixik.stationarenear.navigation.registry.SolarNavigationBlocks;
import dev.sixik.stationarenear.ship.data.ShipSystemType;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorResolver;
import dev.sixik.stationarenear.ship.runtime.ShipDoorController;
import dev.sixik.stationarenear.ship.runtime.ShipManager;
import dev.sixik.stationarenear.structures.config.StationStructureConfig;
import dev.sixik.stationarenear.structures.config.StationStructureConfigManager;
import dev.sixik.stationarenear.structures.config.StationStructureFileStorage;
import dev.sixik.stationarenear.structures.data.StationInstance;
import dev.sixik.stationarenear.structures.data.StationPieceDefinition;
import dev.sixik.stationarenear.structures.data.StationPoolDefinition;
import dev.sixik.stationarenear.structures.editor.StationStructureEditorStick;
import dev.sixik.stationarenear.structures.generation.StationGenerationResult;
import dev.sixik.stationarenear.structures.generation.StationGenerationSettings;
import dev.sixik.stationarenear.structures.generation.StationGenerator;
import dev.sixik.stationarenear.structures.generation.StationPlacementUtil;
import dev.sixik.stationarenear.structures.item.StationStructureToolItem;
import dev.sixik.stationarenear.structures.network.StationStructureNetwork;
import dev.sixik.stationarenear.structures.registry.StationStructureItems;
import dev.sixik.stationarenear.structures.util.NbtPos;
import dev.sixik.stationarenear.structures.util.StationStructureIds;
import dev.sixik.stationarenear.structures.util.TagsConstants;
import dev.sixik.stationarenear.structures.world.StationSavedData;
import dev.sixik.stationarenear.structures.world.StationStructureLibraryData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class StationStructureCommands {

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
                                                .then(Commands.argument(TagsConstants.Keys.TAGS, StringArgumentType.word())
                                                        .then(Commands.argument("accepts", StringArgumentType.word())
                                                                .executes(context -> configureConnector(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(context, "name"),
                                                                        StringArgumentType.getString(context, "direction"),
                                                                        StringArgumentType.getString(context, TagsConstants.Keys.TAGS),
                                                                        StringArgumentType.getString(context, "accepts")
                                                                )))))))
                        .then(Commands.literal("add_connector")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .then(Commands.argument("direction", StringArgumentType.word())
                                                        .then(Commands.argument(TagsConstants.Keys.TAGS, StringArgumentType.word())
                                                                .then(Commands.argument("accepts", StringArgumentType.word())
                                                                        .then(Commands.argument("priority", IntegerArgumentType.integer())
                                                                                .executes(context -> addConnector(
                                                                                        context.getSource(),
                                                                                        StringArgumentType.getString(context, "name"),
                                                                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                                                        StringArgumentType.getString(context, "direction"),
                                                                                        StringArgumentType.getString(context, TagsConstants.Keys.TAGS),
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
                                                                IntegerArgumentType.getInteger(context, "pieces"),
                                                                RequiredPieceSpec.empty()
                                                        ))
                                                        .then(Commands.literal("required")
                                                                .then(Commands.argument(TagsConstants.Keys.REQUIRED_PIECES, StringArgumentType.greedyString())
                                                                        .suggests(StationStructureCommands::suggestPieces)
                                                                        .executes(context -> generate(
                                                                                context.getSource(),
                                                                                ResourceLocationArgument.getId(context, "pool"),
                                                                                FloatArgumentType.getFloat(context, "danger"),
                                                                                IntegerArgumentType.getInteger(context, "pieces"),
                                                                                parseRequiredPieces(context.getSource(), StringArgumentType.getString(context, TagsConstants.Keys.REQUIRED_PIECES))
                                                                        ))))))))
                        .then(generateAtCommand())
                        .then(generateAtStokeCommand())
                        .then(generateAtDockCommand())
                        .then(spawnPoolCommand())
                        .then(Commands.literal("list_generated")
                                .executes(context -> listGenerated(context.getSource())))
                        .then(Commands.literal("clear_generated")
                                .then(Commands.argument("station", StringArgumentType.word())
                                        .suggests(StationStructureCommands::suggestGeneratedStations)
                                        .executes(context -> clearGenerated(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "station")
                                        ))))
                        .then(configurationCommand())));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> generateAtDockCommand() {
        return generateAtStokeCommand();
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> generateAtStokeCommand() {
        var requiredArgument = Commands.literal("required")
                .then(Commands.argument(TagsConstants.Keys.REQUIRED_PIECES, StringArgumentType.greedyString())
                        .suggests(StationStructureCommands::suggestPieces)
                        .executes(context -> generateAtStoke(
                                context.getSource(),
                                StringArgumentType.getString(context, "pool_or_config"),
                                FloatArgumentType.getFloat(context, "danger"),
                                IntegerArgumentType.getInteger(context, "pieces"),
                                parseRequiredPieces(context.getSource(), StringArgumentType.getString(context, TagsConstants.Keys.REQUIRED_PIECES))
                        )));

        var piecesArgument = Commands.argument("pieces", IntegerArgumentType.integer(1))
                .executes(context -> generateAtStoke(
                        context.getSource(),
                        StringArgumentType.getString(context, "pool_or_config"),
                        FloatArgumentType.getFloat(context, "danger"),
                        IntegerArgumentType.getInteger(context, "pieces"),
                        RequiredPieceSpec.empty()
                ))
                .then(requiredArgument);

        var dangerArgument = Commands.argument("danger", FloatArgumentType.floatArg(0.0F, 1.0F))
                .executes(context -> generateAtStoke(
                        context.getSource(),
                        StringArgumentType.getString(context, "pool_or_config"),
                        FloatArgumentType.getFloat(context, "danger"),
                        -1,
                        RequiredPieceSpec.empty()
                ))
                .then(piecesArgument);

        var poolOrConfigArgument = Commands.argument("pool_or_config", StringArgumentType.word())
                .suggests(StationStructureCommands::suggestPoolsAndConfigs)
                .executes(context -> generateAtStoke(
                        context.getSource(),
                        StringArgumentType.getString(context, "pool_or_config"),
                        0.5F,
                        -1,
                        RequiredPieceSpec.empty()
                ))
                .then(dangerArgument);

        return Commands.literal("generate_at_stoke")
                .executes(context -> generateAtStoke(
                        context.getSource(),
                        "",
                        0.5F,
                        -1,
                        RequiredPieceSpec.empty()
                ))
                .then(poolOrConfigArgument);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> configurationCommand() {
        return Commands.literal("config")
                .then(Commands.literal("paths")
                        .executes(context -> showStructureConfigPaths(context.getSource())))
                .then(Commands.literal("reload")
                        .executes(context -> reloadStructureConfigs(context.getSource())))
                .then(Commands.literal("list")
                        .executes(context -> listStructureConfigs(context.getSource())))
                .then(Commands.literal("load_structures")
                        .executes(context -> loadExternalStructures(context.getSource())))
                .then(Commands.literal("generate")
                        .then(Commands.argument("config", StringArgumentType.word())
                                .suggests(StationStructureCommands::suggestStructureConfigs)
                                .executes(context -> generateConfig(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "config")
                                ))))
                .then(Commands.literal("generate_at")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("direction", StringArgumentType.word())
                                        .suggests(StationStructureCommands::suggestDirections)
                                        .then(Commands.argument("config", StringArgumentType.word())
                                                .suggests(StationStructureCommands::suggestStructureConfigs)
                                                .executes(context -> generateAtConfig(
                                                        context.getSource(),
                                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                        StringArgumentType.getString(context, "direction"),
                                                        StringArgumentType.getString(context, "config")
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
                        FloatArgumentType.getFloat(context, "danger"),
                        RequiredPieceSpec.empty()
                ))
                .then(Commands.literal("required")
                        .then(Commands.argument(TagsConstants.Keys.REQUIRED_PIECES, StringArgumentType.greedyString())
                                .suggests(StationStructureCommands::suggestPieces)
                                .executes(context -> generateAt(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                        StringArgumentType.getString(context, "direction"),
                                        StringArgumentType.getString(context, "pool"),
                                        IntegerArgumentType.getInteger(context, "max_floors"),
                                        IntegerArgumentType.getInteger(context, "max_rooms"),
                                        IntegerArgumentType.getInteger(context, "min_rooms"),
                                        FloatArgumentType.getFloat(context, "danger"),
                                        parseRequiredPieces(context.getSource(), StringArgumentType.getString(context, TagsConstants.Keys.REQUIRED_PIECES))
                                ))));
        var minRoomsArgument = Commands.argument("min_rooms", IntegerArgumentType.integer(1))
                .executes(context -> generateAt(
                        context.getSource(),
                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                        StringArgumentType.getString(context, "direction"),
                        StringArgumentType.getString(context, "pool"),
                        IntegerArgumentType.getInteger(context, "max_floors"),
                        IntegerArgumentType.getInteger(context, "max_rooms"),
                        IntegerArgumentType.getInteger(context, "min_rooms"),
                        0.5F,
                        RequiredPieceSpec.empty()
                ))
                .then(Commands.literal("required")
                        .then(Commands.argument(TagsConstants.Keys.REQUIRED_PIECES, StringArgumentType.greedyString())
                                .suggests(StationStructureCommands::suggestPieces)
                                .executes(context -> generateAt(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                        StringArgumentType.getString(context, "direction"),
                                        StringArgumentType.getString(context, "pool"),
                                        IntegerArgumentType.getInteger(context, "max_floors"),
                                        IntegerArgumentType.getInteger(context, "max_rooms"),
                                        IntegerArgumentType.getInteger(context, "min_rooms"),
                                        0.5F,
                                        parseRequiredPieces(context.getSource(), StringArgumentType.getString(context, TagsConstants.Keys.REQUIRED_PIECES))
                                ))))
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
                        0.5F,
                        RequiredPieceSpec.empty()
                ))
                .then(Commands.literal("required")
                        .then(Commands.argument(TagsConstants.Keys.REQUIRED_PIECES, StringArgumentType.greedyString())
                                .suggests(StationStructureCommands::suggestPieces)
                                .executes(context -> generateAt(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                        StringArgumentType.getString(context, "direction"),
                                        StringArgumentType.getString(context, "pool"),
                                        IntegerArgumentType.getInteger(context, "max_floors"),
                                        IntegerArgumentType.getInteger(context, "max_rooms"),
                                        10,
                                        0.5F,
                                        parseRequiredPieces(context.getSource(), StringArgumentType.getString(context, TagsConstants.Keys.REQUIRED_PIECES))
                                ))))
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

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> spawnPoolCommand() {
        var rotationArgument = Commands.argument("rotation", StringArgumentType.word())
                .suggests(StationStructureCommands::suggestRotations)
                .executes(context -> spawnPoolAtPlayer(
                        context.getSource(),
                        StringArgumentType.getString(context, "pool"),
                        StringArgumentType.getString(context, "rotation"),
                        0.5F
                ))
                .then(Commands.argument("danger", FloatArgumentType.floatArg(0.0F, 1.0F))
                        .executes(context -> spawnPoolAtPlayer(
                                context.getSource(),
                                StringArgumentType.getString(context, "pool"),
                                StringArgumentType.getString(context, "rotation"),
                                FloatArgumentType.getFloat(context, "danger")
                        )));

        var positionArgument = Commands.argument("pos", BlockPosArgument.blockPos())
                .then(Commands.argument("rotation", StringArgumentType.word())
                        .suggests(StationStructureCommands::suggestRotations)
                        .executes(context -> spawnPool(
                                context.getSource(),
                                StringArgumentType.getString(context, "pool"),
                                BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                StringArgumentType.getString(context, "rotation"),
                                0.5F
                        ))
                        .then(Commands.argument("danger", FloatArgumentType.floatArg(0.0F, 1.0F))
                                .executes(context -> spawnPool(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "pool"),
                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                        StringArgumentType.getString(context, "rotation"),
                                        FloatArgumentType.getFloat(context, "danger")
                                ))));

        return Commands.literal("spawn_pool")
                .then(Commands.argument("pool", StringArgumentType.word())
                        .suggests(StationStructureCommands::suggestPools)
                        .then(rotationArgument)
                        .then(Commands.literal("at").then(positionArgument)));
    }

    private static CompletableFuture<Suggestions> suggestRotations(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(List.of(
                "none", "clockwise_90", "clockwise_180", "counterclockwise_90",
                "0", "90", "180", "270",
                "north", "east", "south", "west"
        ), builder);
    }

    private static int spawnPoolAtPlayer(CommandSourceStack source, String poolText, String rotationText, float danger) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return spawnPool(source, poolText, source.getPlayerOrException().blockPosition(), rotationText, danger);
    }

    private static int spawnPool(CommandSourceStack source, String poolText, BlockPos origin, String rotationText, float danger) {
        Optional<Rotation> rotation = parseRotation(rotationText);
        if (rotation.isEmpty()) {
            source.sendFailure(Component.literal("Rotation must be one of: none, 90, 180, 270, north, east, south, west"));
            return 0;
        }

        ServerLevel level = source.getLevel();
        int loadedExternalStructures = StationStructureFileStorage.loadExternalStructures(level);
        StationStructureLibraryData library = StationStructureLibraryData.get(level);
        ResourceLocation poolId = StationStructureIds.pool(poolText);
        Optional<StationPoolDefinition> pool = library.pool(poolId);
        if (pool.isEmpty()) {
            source.sendFailure(Component.literal("Pool not found: " + poolId));
            return 0;
        }

        List<StationPieceDefinition> candidates = spawnPoolCandidates(library, pool.get(), danger);
        if (candidates.isEmpty()) {
            source.sendFailure(Component.literal("Pool has no pieces for danger=" + danger + ": " + poolId));
            return 0;
        }

        LoadableSpawnPoolCandidates loadableCandidates = loadableSpawnPoolCandidates(level, candidates);
        if (loadableCandidates.pieces().isEmpty()) {
            source.sendFailure(Component.literal("Pool has no loadable templates for danger=" + danger
                    + ": " + poolId
                    + ". loaded_external_structures=" + loadedExternalStructures
                    + ", missing=" + formatMissingTemplates(loadableCandidates.missingTemplates())));
            return 0;
        }

        StationPieceDefinition piece = selectWeightedSpawnPiece(loadableCandidates.pieces(), level.getRandom());
        Optional<StructureTemplate> template = StationStructureFileStorage.getOrLoadTemplate(level, piece.template());
        if (template.isEmpty()) {
            source.sendFailure(Component.literal("Template not found after reload for piece " + piece.id() + ": " + piece.template()));
            return 0;
        }

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation.get())
                .addProcessor(BlockIgnoreProcessor.AIR);
        template.get().placeInWorld(level, origin, origin, settings, level.getRandom(), 2);
        BoundingBox bounds = StationPlacementUtil.transformBounds(origin, template.get().getSize(), rotation.get());
        source.sendSuccess(() -> Component.literal(
                "Spawned pool " + poolId
                        + " piece=" + piece.id()
                        + " template=" + piece.template()
                        + " rotation=" + rotationName(rotation.get())
                        + " danger=" + danger
                        + " bounds=" + formatBounds(bounds)
        ), false);
        return 1;
    }

    private static List<StationPieceDefinition> spawnPoolCandidates(StationStructureLibraryData library, StationPoolDefinition pool, float danger) {
        List<StationPieceDefinition> candidates = new ArrayList<>();
        Set<ResourceLocation> pieceIds = new java.util.LinkedHashSet<>(pool.startPieces());
        pieceIds.addAll(pool.roomPieces());
        for (ResourceLocation pieceId : pieceIds) {
            library.piece(pieceId).ifPresent(piece -> {
                if (piece.canSpawnAtDanger(danger)) {
                    candidates.add(piece);
                }
            });
        }
        return candidates;
    }

    private static LoadableSpawnPoolCandidates loadableSpawnPoolCandidates(ServerLevel level, List<StationPieceDefinition> pieces) {
        List<StationPieceDefinition> loadable = new ArrayList<>();
        Set<ResourceLocation> missing = new java.util.LinkedHashSet<>();
        for (var piece : pieces) {
            if (StationStructureFileStorage.getOrLoadTemplate(level, piece.template()).isPresent()) {
                loadable.add(piece);
            } else {
                missing.add(piece.template());
            }
        }
        return new LoadableSpawnPoolCandidates(loadable, missing);
    }

    private static String formatMissingTemplates(Set<ResourceLocation> templates) {
        if (templates.isEmpty()) {
            return "none";
        }
        List<String> list = new ArrayList<>();
        for (ResourceLocation template : templates) {
            list.add(template.toString());
        }
        return String.join(", ", list);
    }

    private static StationPieceDefinition selectWeightedSpawnPiece(
            List<StationPieceDefinition> pieces,
            RandomSource random
    ) {
        int totalWeight = 0;
        for (var piece : pieces) {
            totalWeight += Math.max(1, piece.weight());
        }
        int roll = random.nextInt(Math.max(1, totalWeight));
        int current = 0;
        for (var piece : pieces) {
            current += Math.max(1, piece.weight());
            if (roll < current) {
                return piece;
            }
        }
        return pieces.get(0);
    }

    private static Optional<Rotation> parseRotation(String text) {
        if (text == null) {
            return Optional.empty();
        }
        return switch (text.toLowerCase()) {
            case "none", "0", "north", "n" -> Optional.of(Rotation.NONE);
            case "90", "cw90", "clockwise_90", "east", "e" -> Optional.of(Rotation.CLOCKWISE_90);
            case "180", "cw180", "clockwise_180", "south", "s" -> Optional.of(Rotation.CLOCKWISE_180);
            case "270", "ccw90", "counterclockwise_90", "west", "w" -> Optional.of(Rotation.COUNTERCLOCKWISE_90);
            default -> Optional.empty();
        };
    }

    private static String rotationName(Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> "clockwise_90";
            case CLOCKWISE_180 -> "clockwise_180";
            case COUNTERCLOCKWISE_90 -> "counterclockwise_90";
            default -> "none";
        };
    }

    private static CompletableFuture<Suggestions> suggestStructureConfigs(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> suggestions = new ArrayList<>();
        for (StationStructureConfig config : StationStructureConfigManager.configurations()) {
            suggestions.add(config.id().toString());
            suggestions.add(config.id().getPath());
        }
        return SharedSuggestionProvider.suggest(suggestions, builder);
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

    private static CompletableFuture<Suggestions> suggestPoolsAndConfigs(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> suggestions = new ArrayList<>();
        for (StationStructureConfig config : StationStructureConfigManager.configurations()) {
            suggestions.add(config.id().toString());
            suggestions.add(config.id().getPath());
        }
        for (var pool : StationStructureLibraryData.get(context.getSource().getLevel()).pools()) {
            suggestions.add(pool.id().toString());
            suggestions.add(pool.id().getPath());
        }
        return SharedSuggestionProvider.suggest(suggestions, builder);
    }

    private static CompletableFuture<Suggestions> suggestPieces(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> suggestions = new ArrayList<>();
        String existingText = builder.getRemaining();
        String prefix = "";
        int lastComma = existingText.lastIndexOf(',');
        if (lastComma >= 0) {
            prefix = existingText.substring(0, lastComma + 1);
        }
        java.util.Set<String> tagSuggestions = new java.util.LinkedHashSet<>();
        for (var piece : StationStructureLibraryData.get(context.getSource().getLevel()).pieces()) {
            suggestions.add(prefix + piece.id() + "=1");
            suggestions.add(prefix + piece.id().getPath() + "=1");
            tagSuggestions.addAll(piece.tags());
        }
        for (String tag : tagSuggestions) {
            suggestions.add(prefix + "#" + tag + "=1");
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
        source.sendSuccess(() -> Component.literal("Configured connector parameters for tool"), false);
        return 1;
    }

    private static int addConnector(CommandSourceStack source, String name, BlockPos pos, String directionName, String tags, String accepts, int priority) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Direction direction = Direction.byName(directionName);
        if (direction == null) {
            source.sendFailure(Component.literal("Unknown direction: " + directionName));
            return 0;
        }

        ItemStack stack = heldTool(source.getPlayerOrException());
        CompoundTag root = stack.getOrCreateTag();
        ListTag list = root.getList(StationStructureToolItem.KEY_CONNECTORS, Tag.TAG_COMPOUND);

        CompoundTag connector = new CompoundTag();
        connector.putString("name", name);
        connector.put("worldPosition", NbtPos.save(pos));
        connector.putString("direction", direction.getSerializedName());
        connector.putString(TagsConstants.Keys.TAGS, tags);
        connector.putString("accepts", accepts);
        connector.putInt("priority", priority);
        list.add(connector);
        root.put(StationStructureToolItem.KEY_CONNECTORS, list);

        source.sendSuccess(() -> Component.literal("Added connector '" + name + "' to tool metadata (total: " + list.size() + ")"), false);
        return list.size();
    }

    private static int addTriggerZone(CommandSourceStack source, String id, String type, BlockPos min, BlockPos max) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ItemStack stack = heldTool(source.getPlayerOrException());
        CompoundTag root = stack.getOrCreateTag();
        ListTag list = root.getList(StationStructureToolItem.KEY_TRIGGER_ZONES, Tag.TAG_COMPOUND);

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

        CompoundTag zone = new CompoundTag();
        zone.putString("id", id);
        zone.putString("type", type);
        zone.put("worldMin", NbtPos.save(normalizedMin));
        zone.put("worldMax", NbtPos.save(normalizedMax));
        zone.put("data", new CompoundTag());
        list.add(zone);
        root.put(StationStructureToolItem.KEY_TRIGGER_ZONES, list);

        source.sendSuccess(() -> Component.literal("Added trigger zone '" + id + "' (" + type + ") to tool metadata (total: " + list.size() + ")"), false);
        return list.size();
    }

    private static int configureStartPiece(CommandSourceStack source, boolean startPiece) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ItemStack stack = heldTool(source.getPlayerOrException());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(StationStructureToolItem.KEY_START_PIECE, startPiece);
        source.sendSuccess(() -> Component.literal("Set start_piece=" + startPiece + " on tool"), false);
        return 1;
    }

    private static int configureWeight(CommandSourceStack source, int weight) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ItemStack stack = heldTool(source.getPlayerOrException());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(StationStructureToolItem.KEY_WEIGHT, Math.max(1, weight));
        source.sendSuccess(() -> Component.literal("Set weight=" + Math.max(1, weight) + " on tool"), false);
        return 1;
    }

    private static int showStructureConfigPaths(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Structure Config directory: " + StationStructureFileStorage.structureConfigurationsDirectory()), false);
        source.sendSuccess(() -> Component.literal("Structure Export directory: " + StationStructureFileStorage.structuresDirectory()), false);
        return 1;
    }

    private static int reloadStructureConfigs(CommandSourceStack source) {
        int loaded = StationStructureConfigManager.reload();
        source.sendSuccess(() -> Component.literal("Reloaded structure configs: " + loaded), false);
        return loaded;
    }

    private static int listStructureConfigs(CommandSourceStack source) {
        Collection<StationStructureConfig> configs = StationStructureConfigManager.configurations();
        if (configs.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No structure configs loaded."), false);
            return 0;
        }

        for (StationStructureConfig config : configs) {
            source.sendSuccess(() -> Component.literal(
                    "Config: " + config.id()
                            + " pool=" + config.pool()
                            + " maxFloors=" + config.maxFloors()
                            + " minRooms=" + config.minRooms()
                            + " maxRooms=" + config.maxRooms()
                            + " danger=" + config.minDangerMultiplier() + ".." + config.maxDangerMultiplier()
            ), false);
        }
        return configs.size();
    }

    private static int loadExternalStructures(CommandSourceStack source) {
        int loaded = StationStructureFileStorage.loadExternalStructures(source.getLevel());
        source.sendSuccess(() -> Component.literal("Loaded external structure templates: " + loaded), false);
        return loaded;
    }

    private static int generateConfig(CommandSourceStack source, String configText) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos doorCenter = player.blockPosition();
        Direction stationDirection = player.getDirection();
        Optional<StationStructureConfig> config = StationStructureConfigManager.get(configText);
        if (config.isEmpty()) {
            source.sendFailure(Component.literal("Structure config not found: " + configText));
            return 0;
        }

        StationGenerationSettings settings = config.get().createSettings(source.getLevel().getRandom(), source.getLevel().getRandom().nextLong());
        StationGenerationResult result = new StationGenerator().generateDockedStation(source.getLevel(), doorCenter, stationDirection, settings);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Generated station from config " + config.get().id() + " danger=" + settings.missionDanger()), false);
        return result.station().map(station -> station.pieces().size()).orElse(1);
    }

    private static int generateAtConfig(CommandSourceStack source, BlockPos doorCenter, String directionName, String configText) {
        Direction stationDirection = Direction.byName(directionName);
        if (stationDirection == null || stationDirection.getAxis().isVertical()) {
            source.sendFailure(Component.literal("Direction must be one of: north, south, east, west"));
            return 0;
        }
        Optional<StationStructureConfig> config = StationStructureConfigManager.get(configText);
        if (config.isEmpty()) {
            source.sendFailure(Component.literal("Structure config not found: " + configText));
            return 0;
        }

        StationGenerationSettings settings = config.get().createSettings(source.getLevel().getRandom(), source.getLevel().getRandom().nextLong());
        StationGenerationResult result = new StationGenerator().generateDockedStation(source.getLevel(), doorCenter, stationDirection, settings);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }

        result.station().ifPresent(station -> source.sendSuccess(() -> Component.literal(
                "Generated station " + station.id() + " config=" + config.get().id()
                        + " pool=" + station.pool()
                        + " danger=" + settings.missionDanger()
                        + " pieces=" + station.pieces().size()
                        + " bounds=" + formatBounds(aggregateBounds(station))
        ), false));
        return result.station().map(station -> station.pieces().size()).orElse(1);
    }

    private static int generate(CommandSourceStack source, ResourceLocation pool, float danger, int pieces, RequiredPieceSpec requiredPieces) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos doorCenter = player.blockPosition();
        Direction stationDirection = player.getDirection();
        StationGenerationResult result = new StationGenerator().generateDockedStation(
                source.getLevel(),
                doorCenter,
                stationDirection,
                new StationGenerationSettings(pool, danger, true, pieces, source.getLevel().getRandom().nextLong()).withRequiredPieces(requiredPieces.pieces(), requiredPieces.tags())
        );

        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(result.message()), false);
        return result.station().map(station -> station.pieces().size()).orElse(1);
    }

    private static int generateAt(CommandSourceStack source, BlockPos doorCenter, String directionName, String poolText, int maxFloors, int maxRooms, int minRooms, float danger, RequiredPieceSpec requiredPieces) {
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
                new StationGenerationSettings(pool, danger, true, maxFloors, minRooms, maxRooms, source.getLevel().getRandom().nextLong()).withRequiredPieces(requiredPieces.pieces(), requiredPieces.tags())
        );

        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }

        result.station().ifPresent(station -> source.sendSuccess(() -> Component.literal(
                "Generated station " + station.id() + " pool=" + station.pool()
                        + " pieces=" + station.pieces().size()
                        + (requiredPieces.isEmpty() ? "" : " required=" + formatRequiredPieces(requiredPieces))
                        + " bounds=" + formatBounds(aggregateBounds(station))
        ), false));
        return result.station().map(station -> station.pieces().size()).orElse(1);
    }

    private static int generateAtStoke(
            CommandSourceStack source,
            String poolOrConfigText,
            float danger,
            int pieceCount,
            RequiredPieceSpec requiredPieces
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        BlockPos playerPos = player.blockPosition();

        Optional<BlockPos> terminalOptional = findNearbyTerminal(level, playerPos, 64);
        BlockPos doorCenter;
        Direction stationDirection;
        BlockPos terminalPos = null;

        if (terminalOptional.isPresent()) {
            terminalPos = terminalOptional.get();
            BlockState terminalState = level.getBlockState(terminalPos);
            var dockingAnchor = ShipDockingAnchorResolver.resolve(level, terminalPos, terminalState);
            doorCenter = dockingAnchor.doorCenter();
            stationDirection = dockingAnchor.stationDirection();
        } else {
            doorCenter = playerPos;
            stationDirection = player.getDirection();
        }

        StationStructureFileStorage.loadExternalStructures(level);
        StationGenerationSettings settings;
        long seed = level.getRandom().nextLong();

        if (poolOrConfigText == null || poolOrConfigText.isBlank()) {
            Collection<StationStructureConfig> configs = StationStructureConfigManager.configurations();
            if (!configs.isEmpty()) {
                settings = configs.iterator().next().createSettings(level.getRandom(), seed);
            } else {
                ResourceLocation defaultPool = StationStructureIds.pool("space_station");
                settings = new StationGenerationSettings(defaultPool, danger, true, pieceCount > 0 ? pieceCount : 16, seed);
            }
        } else {
            Optional<StationStructureConfig> config = StationStructureConfigManager.get(poolOrConfigText);
            if (config.isPresent()) {
                settings = config.get().createSettings(level.getRandom(), seed);
                if (pieceCount > 0) {
                    settings = new StationGenerationSettings(
                            settings.pool(),
                            settings.missionDanger(),
                            settings.randomStation(),
                            settings.maxFloors(),
                            pieceCount,
                            pieceCount,
                            settings.seed(),
                            settings.requiredPieces(),
                            settings.requiredPieceTags(),
                            settings.questElementSpawnSkips(),
                            settings.customData()
                    );
                }
            } else {
                ResourceLocation pool = StationStructureIds.pool(poolOrConfigText);
                settings = new StationGenerationSettings(pool, danger, true, pieceCount > 0 ? pieceCount : 16, seed);
            }
        }

        if (!requiredPieces.isEmpty()) {
            settings = settings.withRequiredPieces(requiredPieces.pieces(), requiredPieces.tags());
        }

        StationGenerationResult result = new StationGenerator().generateDockedStation(
                level,
                doorCenter,
                stationDirection,
                settings
        );

        if (!result.success()) {
            source.sendFailure(Component.literal("Docked generation failed at " + doorCenter + " dir " + stationDirection + ": " + result.message()));
            return 0;
        }

        if (terminalPos != null) {
            ShipManager.setDocking(level, terminalPos, true);
            if (ShipManager.state(level, terminalPos).hasModule(ShipSystemType.AUTO_DOORS)) {
                ShipDoorController.setOpen(level, terminalPos, true);
            }
        }

        final BlockPos finalTerminalPos = terminalPos;
        final BlockPos finalDoorCenter = doorCenter;
        final Direction finalStationDirection = stationDirection;
        result.station().ifPresent(station -> {
            if (finalTerminalPos != null) {
                station.customData().putLong("navigationTerminalPos", finalTerminalPos.asLong());
            }
            source.sendSuccess(() -> Component.literal(
                    "Generated and docked station " + station.id()
                            + " pool=" + station.pool()
                            + " pieces=" + station.pieces().size()
                            + " at " + finalDoorCenter + " facing " + finalStationDirection
            ), true);
        });

        return result.station().map(station -> station.pieces().size()).orElse(1);
    }

    private static Optional<BlockPos> findNearbyTerminal(ServerLevel level, BlockPos center, int radius) {
        BlockPos min = center.offset(-radius, -radius / 2, -radius);
        BlockPos max = center.offset(radius, radius / 2, radius);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.getBlockState(pos).is(SolarNavigationBlocks.SOLAR_NAVIGATION_TERMINAL.get())) {
                continue;
            }
            double distance = pos.distSqr(center);
            if (distance < bestDistance) {
                best = pos.immutable();
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    private static RequiredPieceSpec parseRequiredPieces(CommandSourceStack source, String text) {
        Map<ResourceLocation, Integer> requiredPieces = new LinkedHashMap<>();
        Map<String, Integer> requiredTags = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return new RequiredPieceSpec(requiredPieces, requiredTags);
        }

        for (String rawEntry : text.split(",")) {
            String entry = rawEntry.trim();
            if (entry.isEmpty()) {
                continue;
            }

            String[] parts = entry.split("=", 2);
            int count = 1;
            if (parts.length > 1) {
                try {
                    count = Math.max(1, Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException exception) {
                    source.sendFailure(Component.literal("Invalid required piece count: " + entry));
                    continue;
                }
            }

            String key = parts[0].trim();
            if (key.startsWith("#")) {
                String tag = StationGenerationSettings.normalizeTag(key.substring(1));
                if (!tag.isBlank()) {
                    requiredTags.merge(tag, count, Integer::sum);
                }
            } else {
                ResourceLocation id = StationStructureIds.normalize(key, "stations/new_piece");
                requiredPieces.merge(id, count, Integer::sum);
            }
        }
        return new RequiredPieceSpec(requiredPieces, requiredTags);
    }

    private static String formatRequiredPieces(RequiredPieceSpec requiredPieces) {
        List<String> values = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Integer> entry : requiredPieces.pieces().entrySet()) {
            values.add(entry.getKey() + "x" + entry.getValue());
        }
        for (Map.Entry<String, Integer> entry : requiredPieces.tags().entrySet()) {
            values.add("#" + entry.getKey() + "x" + entry.getValue());
        }
        return String.join(",", values);
    }

    private record LoadableSpawnPoolCandidates(
            List<StationPieceDefinition> pieces,
            Set<ResourceLocation> missingTemplates
    ) {
    }
    private record RequiredPieceSpec(Map<ResourceLocation, Integer> pieces, Map<String, Integer> tags) {
        static RequiredPieceSpec empty() {
            return new RequiredPieceSpec(Map.of(), Map.of());
        }

        boolean isEmpty() {
            return pieces.isEmpty() && tags.isEmpty();
        }
    }

    private static int listGenerated(CommandSourceStack source) {
        List<StationInstance> stations = new ArrayList<>(StationSavedData.get(source.getLevel()).stations());
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
        List<StationInstance> targets = new ArrayList<>();
        if (stationText.equalsIgnoreCase("all")) {
            targets.addAll(data.stations());
        } else {
            Optional<StationInstance> station = findStation(data, stationText);
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

    private static Optional<StationInstance> findStation(StationSavedData data, String stationText) {
        try {
            UUID id = UUID.fromString(stationText);
            return data.station(id);
        } catch (IllegalArgumentException ignored) {
            return data.stations().stream()
                    .filter(station -> station.id().toString().startsWith(stationText))
                    .findFirst();
        }
    }

    private static BoundingBox aggregateBounds(StationInstance station) {
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
