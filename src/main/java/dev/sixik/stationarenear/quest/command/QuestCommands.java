package dev.sixik.stationarenear.quest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.navigation.StationCodeGenerator;
import dev.sixik.stationarenear.navigation.data.SolarNavigationQuestMarker;
import dev.sixik.stationarenear.quest.api.QuestApi;
import dev.sixik.stationarenear.quest.config.director.DirectorConfigManager;
import dev.sixik.stationarenear.quest.config.director.DirectorProfileConfig;
import dev.sixik.stationarenear.quest.director.DirectorContext;
import dev.sixik.stationarenear.quest.director.DirectorDebugReport;
import dev.sixik.stationarenear.quest.director.DirectorPlan;
import dev.sixik.stationarenear.quest.director.QuestDirector;
import dev.sixik.stationarenear.quest.data.QuestObjectiveState;
import dev.sixik.stationarenear.quest.data.QuestStationState;
import dev.sixik.stationarenear.quest.runtime.QuestTestScenario;
import dev.sixik.stationarenear.quest.world.BalanceSavedData;
import dev.sixik.stationarenear.quest.world.QuestSavedData;
import dev.sixik.stationarenear.structures.data.PlacedStationPiece;
import dev.sixik.stationarenear.structures.data.PlacedTriggerZone;
import dev.sixik.stationarenear.structures.data.StationInstance;
import dev.sixik.stationarenear.structures.world.StationSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class QuestCommands {

    private QuestCommands() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(QuestCommands::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("stationarenear")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("director")
                        .then(Commands.literal("reload").executes(context -> reloadDirector(context.getSource())))
                        .then(Commands.literal("list").executes(context -> listDirectors(context.getSource())))
                        .then(Commands.literal("simulate")
                                .executes(context -> simulateDirector(context.getSource(), ""))
                                .then(Commands.argument("director", ResourceLocationArgument.id())
                                        .suggests(QuestCommands::suggestDirectorProfiles)
                                        .executes(context -> simulateDirector(context.getSource(), getIdArgument(context, "director"))))))
                .then(Commands.literal("balance")
                        .executes(context -> showBalance(context.getSource()))
                        .then(Commands.literal("add")
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                        .executes(context -> addBalance(context.getSource(), DoubleArgumentType.getDouble(context, "amount")))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                        .executes(context -> setBalance(context.getSource(), DoubleArgumentType.getDouble(context, "amount"))))))
                .then(Commands.literal("quests")
                        .then(Commands.literal("reload").executes(context -> reloadDirector(context.getSource())))
                        .then(Commands.literal("start")
                                .executes(context -> startTestQuest(context.getSource(), "", 0))
                                .then(Commands.argument("director", ResourceLocationArgument.id())
                                        .suggests(QuestCommands::suggestDirectorProfiles)
                                        .executes(context -> startTestQuest(context.getSource(), getIdArgument(context, "director"), 0))))
                        .then(Commands.literal("complete")
                                .then(Commands.argument("quest", ResourceLocationArgument.id())
                                        .suggests(QuestCommands::suggestCurrentObjectiveIds)
                                        .executes(context -> completeCurrentQuest(context.getSource(), getIdArgument(context, "quest")))))
                        .then(Commands.literal("complete_at")
                                .then(Commands.argument("station", StringArgumentType.word())
                                        .suggests(QuestCommands::suggestStationIds)
                                        .then(Commands.argument("quest", ResourceLocationArgument.id())
                                                .suggests(QuestCommands::suggestStationObjectiveIds)
                                                .executes(context -> completeQuest(context.getSource(), StringArgumentType.getString(context, "station"), getIdArgument(context, "quest"))))))
                        .then(Commands.literal("complete_all")
                                .executes(context -> completeAllCurrent(context.getSource()))
                                .then(Commands.argument("station", StringArgumentType.word())
                                        .suggests(QuestCommands::suggestStationIds)
                                        .executes(context -> completeAll(context.getSource(), StringArgumentType.getString(context, "station")))))
                        .then(Commands.literal("tp_objective")
                                .executes(context -> teleportToObjective(context.getSource(), "", 1))
                                .then(Commands.argument("quest", ResourceLocationArgument.id())
                                        .suggests(QuestCommands::suggestCurrentObjectiveIds)
                                        .executes(context -> teleportToObjective(context.getSource(), getIdArgument(context, "quest"), 1))
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                .executes(context -> teleportToObjective(context.getSource(), getIdArgument(context, "quest"), IntegerArgumentType.getInteger(context, "index"))))))
                        .then(Commands.literal("test")
                                .then(Commands.literal("start")
                                        .executes(context -> startTestQuest(context.getSource(), "", 0))
                                        .then(Commands.literal("skip_trash")
                                                .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                                        .executes(context -> startTestQuest(context.getSource(), "", IntegerArgumentType.getInteger(context, "count")))))
                                        .then(Commands.argument("director", ResourceLocationArgument.id())
                                                .suggests(QuestCommands::suggestDirectorProfiles)
                                                .executes(context -> startTestQuest(context.getSource(), getIdArgument(context, "director"), 0))
                                                .then(Commands.literal("skip_trash")
                                                        .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                                                .executes(context -> startTestQuest(context.getSource(), getIdArgument(context, "director"), IntegerArgumentType.getInteger(context, "count")))))))
                                .then(Commands.literal("stop").executes(context -> stopTestQuest(context.getSource()))))));
    }

    private static int showBalance(CommandSourceStack source) {
        double balance = BalanceSavedData.get(source.getLevel()).balance();
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Balance: %.2f", balance)), false);
        return 1;
    }

    private static int addBalance(CommandSourceStack source, double amount) {
        double balance = BalanceSavedData.get(source.getLevel()).add(amount);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Balance changed by %.2f. Balance: %.2f", amount, balance)), false);
        return 1;
    }

    private static int setBalance(CommandSourceStack source, double amount) {
        double balance = BalanceSavedData.get(source.getLevel()).set(amount);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Balance set to %.2f", balance)), false);
        return 1;
    }

    private static int reloadDirector(CommandSourceStack source) {
        int count = DirectorConfigManager.reload();
        source.sendSuccess(() -> Component.literal("Reloaded Director config: " + count + " profiles, " + DirectorConfigManager.trashBlockIds().size() + " trash block ids."), false);
        return count;
    }

    private static int listDirectors(CommandSourceStack source) {
        List<DirectorProfileConfig> profiles = DirectorConfigManager.profiles().stream().toList();
        if (profiles.isEmpty()) {
            source.sendFailure(Component.literal("No Director profiles loaded."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Director profiles: " + profiles.stream().map(DirectorProfileConfig::id).toList()), false);
        return profiles.size();
    }

    private static int simulateDirector(CommandSourceStack source, String directorId) {
        ServerLevel level = source.getLevel();
        DirectorProfileConfig profile = selectDirector(level, directorId);
        long seed = level.getSeed() ^ source.getPosition().hashCode() ^ level.getRandom().nextLong();
        DirectorPlan plan = QuestDirector.createPlan(profile, directorContext(level, seed));
        for (String line : DirectorDebugReport.from(plan).lines()) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return plan.questTasks().size();
    }

    private static DirectorContext directorContext(ServerLevel level, long seed) {
        QuestSavedData data = QuestSavedData.get(level);
        return new DirectorContext(level, seed, -1, -1, data.completedMissionCount(), data.completedQuestIds());
    }

    private static int startTestQuest(CommandSourceStack source, String directorId, int trashSpawnSkip) {
        ServerLevel level = source.getLevel();
        if (directorId != null && !directorId.isBlank() && DirectorConfigManager.profile(directorId).isEmpty()) {
            source.sendFailure(Component.literal("Unknown Director profile: " + directorId));
            return 0;
        }
        if ((directorId == null || directorId.isBlank()) && !QuestTestScenario.hasAvailableQuest(level)) {
            source.sendFailure(Component.literal("No available Director profiles. Check required_quests in Director config."));
            return 0;
        }

        Optional<UUID> currentStationId = QuestApi.currentStationId(level);
        if (currentStationId.isPresent() && isTestQuest(level, currentStationId.get())) {
            QuestApi.clear(level, currentStationId.get());
        }
        QuestTestScenario.stop(level);

        SolarNavigationQuestMarker marker;
        try {
            marker = QuestTestScenario.createQuestMarker(level, source.getPosition(), trashSpawnSkip, directorId);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
        String missionName = QuestTestScenario.pendingDirectorName(level).orElse(marker.name());
        source.sendSuccess(() -> Component.literal("Created Director quest " + missionName + " marker " + marker.id()
                + " code=" + StationCodeGenerator.code(marker.seed(), marker.x(), marker.y())
                + " solar_pos=" + Math.round(marker.x()) + " " + Math.round(marker.y()) + "."), false);
        if (trashSpawnSkip > 0) {
            source.sendSuccess(() -> Component.literal("Quest trash spawn skip=" + trashSpawnSkip + "."), false);
        }
        source.sendSuccess(() -> Component.literal("Fly to it in Solar Navigation and dock. Station generation will use Director budget plan."), false);
        source.sendSuccess(() -> Component.literal("Terminal command after docking: /objectives. Stop command: /stationarenear quests test stop."), false);
        return 1;
    }

    private static int stopTestQuest(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Optional<UUID> currentStationId = QuestApi.currentStationId(level);
        if (currentStationId.isPresent() && isTestQuest(level, currentStationId.get())) {
            UUID stationId = currentStationId.get();
            QuestApi.stopCurrentQuest(level);
            source.sendSuccess(() -> Component.literal("Stopped demo quest on station " + stationId + "."), false);
            return 1;
        }

        int removedScenarioData = QuestTestScenario.stop(level);
        if (removedScenarioData > 0) {
            source.sendSuccess(() -> Component.literal("Stopped test quest scenario data: " + removedScenarioData + " item(s)."), false);
            return removedScenarioData;
        }

        List<UUID> staleTestStations = new ArrayList<>();
        for (QuestStationState state : QuestSavedData.get(level).stations()) {
            if (isTestQuest(level, state.stationId())) {
                staleTestStations.add(state.stationId());
            }
        }

        if (staleTestStations.isEmpty()) {
            source.sendFailure(Component.literal("No active demo quest found."));
            return 0;
        }

        for (UUID stationId : staleTestStations) {
            QuestApi.clear(level, stationId);
        }
        source.sendSuccess(() -> Component.literal("Stopped stale demo quest data: " + staleTestStations.size() + " station(s)."), false);
        return staleTestStations.size();
    }

    private static int completeCurrentQuest(CommandSourceStack source, String questId) {
        Optional<UUID> stationId = QuestApi.currentStationId(source.getLevel());
        if (stationId.isEmpty()) {
            source.sendFailure(Component.literal("No current quest station."));
            return 0;
        }
        return completeQuest(source, stationId.get(), questId);
    }

    private static int completeQuest(CommandSourceStack source, String stationIdText, String questId) {
        Optional<UUID> stationId = parseUuid(stationIdText);
        if (stationId.isEmpty()) {
            source.sendFailure(Component.literal("Invalid station UUID: " + stationIdText));
            return 0;
        }
        return completeQuest(source, stationId.get(), questId);
    }

    private static int completeQuest(CommandSourceStack source, UUID stationId, String questId) {
        ServerLevel level = source.getLevel();
        if (QuestApi.complete(level, stationId, questId, sourcePlayer(source))) {
            source.sendSuccess(() -> Component.literal("Completed quest " + questId + " on station " + stationId + "."), false);
            return 1;
        }
        source.sendFailure(Component.literal("Quest " + questId + " is not active on station " + stationId + ", or it is already completed."));
        return 0;
    }

    private static int completeAllCurrent(CommandSourceStack source) {
        Optional<UUID> stationId = QuestApi.currentStationId(source.getLevel());
        if (stationId.isEmpty()) {
            source.sendFailure(Component.literal("No current quest station."));
            return 0;
        }
        return completeAll(source, stationId.get());
    }

    private static int completeAll(CommandSourceStack source, String stationIdText) {
        Optional<UUID> stationId = parseUuid(stationIdText);
        if (stationId.isEmpty()) {
            source.sendFailure(Component.literal("Invalid station UUID: " + stationIdText));
            return 0;
        }
        return completeAll(source, stationId.get());
    }

    private static int completeAll(CommandSourceStack source, UUID stationId) {
        ServerLevel level = source.getLevel();
        Optional<QuestStationState> state = QuestSavedData.get(level).stationIfPresent(stationId);
        if (state.isEmpty()) {
            source.sendFailure(Component.literal("No quest data for station " + stationId + "."));
            return 0;
        }

        List<String> activeQuestIds = activeObjectiveIds(level, stationId);
        if (activeQuestIds.isEmpty()) {
            source.sendFailure(Component.literal("Station " + stationId + " has no active quests."));
            return 0;
        }

        ServerPlayer player = sourcePlayer(source);
        int completed = 0;
        for (String questId : activeQuestIds) {
            if (QuestApi.complete(level, stationId, questId, player)) {
                completed++;
            }
        }
        int result = completed;
        source.sendSuccess(() -> Component.literal("Completed " + result + " quest(s) on station " + stationId + "."), false);
        return completed;
    }

    private static int teleportToObjective(CommandSourceStack source, String questId, int index) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();
        Optional<UUID> stationId = QuestApi.currentStationId(level);
        if (stationId.isEmpty()) {
            source.sendFailure(Component.literal("No current quest station. Dock with a quest station first."));
            return 0;
        }

        Optional<QuestStationState> questState = QuestSavedData.get(level).stationIfPresent(stationId.get());
        if (questState.isEmpty()) {
            source.sendFailure(Component.literal("No quest data for current station " + stationId.get() + "."));
            return 0;
        }

        Optional<StationInstance> station = StationSavedData.get(level).station(stationId.get());
        if (station.isEmpty()) {
            source.sendFailure(Component.literal("Generated station not found for current quest station " + stationId.get() + "."));
            return 0;
        }

        Optional<TargetTriggerRef> target = targetTriggerRef(questState.get(), questId, index);
        if (target.isEmpty()) {
            source.sendFailure(Component.literal("No active objective target found. Try /stationarenear quests tp_objective <quest> <index>."));
            return 0;
        }

        Optional<TargetZoneRef> zoneRef = findTargetZone(station.get(), target.get().triggerId());
        if (zoneRef.isEmpty()) {
            source.sendFailure(Component.literal("Target trigger not found on generated station: " + target.get().triggerId()));
            return 0;
        }

        BlockPos teleportPos = safeTeleportPos(level, zoneRef.get().piece(), zoneRef.get().zone());
        player.teleportTo(level, teleportPos.getX() + 0.5D, teleportPos.getY(), teleportPos.getZ() + 0.5D, player.getYRot(), player.getXRot());
        source.sendSuccess(() -> Component.literal("Teleported to objective " + target.get().questId()
                + " target=" + target.get().triggerId()
                + " pos=" + teleportPos.toShortString()), false);
        return 1;
    }

    private static Optional<TargetTriggerRef> targetTriggerRef(QuestStationState state, String questId, int index) {
        String normalizedQuestId = questId == null ? "" : questId.trim().toLowerCase(java.util.Locale.ROOT);
        for (QuestObjectiveState objective : state.objectives()) {
            if (objective.completed()) {
                continue;
            }
            if (!normalizedQuestId.isBlank() && !objective.id().equalsIgnoreCase(normalizedQuestId)) {
                continue;
            }
            List<String> targetIds = targetTriggerIds(objective);
            if (targetIds.isEmpty()) {
                continue;
            }
            int selectedIndex = Math.max(1, index) - 1;
            if (selectedIndex >= targetIds.size()) {
                continue;
            }
            return Optional.of(new TargetTriggerRef(objective.id(), targetIds.get(selectedIndex)));
        }
        return Optional.empty();
    }

    private static List<String> targetTriggerIds(QuestObjectiveState objective) {
        List<String> ids = new ArrayList<>();
        ListTag list = objective.progress().getList("targetTriggerIds", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            String id = list.getString(i);
            if (!id.isBlank() && !ids.contains(id)) {
                ids.add(id);
            }
        }
        if (ids.isEmpty() && !objective.targetTriggerId().isBlank()) {
            ids.add(objective.targetTriggerId());
        }
        return ids;
    }

    private static Optional<TargetZoneRef> findTargetZone(StationInstance station, String triggerId) {
        for (PlacedStationPiece piece : station.pieces()) {
            for (PlacedTriggerZone zone : piece.triggerZones()) {
                if (zone.id().equals(triggerId)) {
                    return Optional.of(new TargetZoneRef(piece, zone));
                }
            }
        }
        return Optional.empty();
    }

    private static BlockPos safeTeleportPos(ServerLevel level, PlacedStationPiece piece, PlacedTriggerZone zone) {
        BoundingBox bounds = piece.selectionBounds();
        BlockPos desired = center(zone.min(), zone.max());
        BlockPos fallback = new BlockPos(
                Mth.clamp(desired.getX(), bounds.minX(), bounds.maxX()),
                Math.max(bounds.minY() + 1, desired.getY() + 1),
                Mth.clamp(desired.getZ(), bounds.minZ(), bounds.maxZ())
        );
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = bounds.minY() + 1; y <= bounds.maxY() + 2; y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    cursor.set(x, y, z);
                    if (!isSafeTeleportBlock(level, cursor)) {
                        continue;
                    }
                    double distance = cursor.distSqr(desired);
                    if (distance < bestDistance) {
                        best = cursor.immutable();
                        bestDistance = distance;
                    }
                }
            }
        }
        return best == null ? fallback : best;
    }

    private static BlockPos center(BlockPos min, BlockPos max) {
        return new BlockPos(
                Math.floorDiv(min.getX() + max.getX(), 2),
                Math.floorDiv(min.getY() + max.getY(), 2),
                Math.floorDiv(min.getZ() + max.getZ(), 2)
        );
    }

    private static boolean isSafeTeleportBlock(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).isAir()
                && !level.getBlockState(pos.below()).isAir();
    }

    private static CompletableFuture<Suggestions> suggestDirectorProfiles(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> suggestions = new ArrayList<>();
        for (DirectorProfileConfig profile : DirectorConfigManager.profiles()) {
            suggestions.add(profile.id());
            int separator = profile.id().indexOf(':');
            if (separator >= 0 && separator + 1 < profile.id().length()) {
                suggestions.add(profile.id().substring(separator + 1));
            }
        }
        return SharedSuggestionProvider.suggest(suggestions, builder);
    }

    private static CompletableFuture<Suggestions> suggestCurrentObjectiveIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        Optional<UUID> stationId = QuestApi.currentStationId(context.getSource().getLevel());
        return SharedSuggestionProvider.suggest(stationId.map(id -> activeObjectiveIds(context.getSource().getLevel(), id)).orElse(List.of()), builder);
    }

    private static CompletableFuture<Suggestions> suggestStationObjectiveIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        Optional<UUID> stationId = parseUuid(StringArgumentType.getString(context, "station"));
        return SharedSuggestionProvider.suggest(stationId.map(id -> activeObjectiveIds(context.getSource().getLevel(), id)).orElse(List.of()), builder);
    }

    private static List<String> activeObjectiveIds(ServerLevel level, UUID stationId) {
        return QuestSavedData.get(level).stationIfPresent(stationId)
                .map(state -> state.objectives().stream().filter(objective -> !objective.completed()).map(objective -> objective.id()).toList())
                .orElse(List.of());
    }

    private static CompletableFuture<Suggestions> suggestStationIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(QuestSavedData.get(context.getSource().getLevel()).stations().stream().map(state -> state.stationId().toString()).toList(), builder);
    }

    private static DirectorProfileConfig selectDirector(ServerLevel level, String directorId) {
        if (directorId != null && !directorId.isBlank()) {
            return DirectorConfigManager.profile(directorId).orElseThrow(() -> new IllegalArgumentException("Unknown Director profile: " + directorId));
        }
        return DirectorConfigManager.randomAvailableProfile(level, level.getRandom()).or(() -> DirectorConfigManager.defaultProfileConfig()).orElseThrow(() -> new IllegalStateException("No Director profiles loaded"));
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private record TargetTriggerRef(String questId, String triggerId) {
    }

    private record TargetZoneRef(PlacedStationPiece piece, PlacedTriggerZone zone) {
    }

    private static ServerPlayer sourcePlayer(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? player : null;
    }

    private static String getIdArgument(CommandContext<CommandSourceStack> context, String name) {
        ResourceLocation id = ResourceLocationArgument.getId(context, name);
        return id.getNamespace().equals("minecraft") ? StationAreNear.MODID + ":" + id.getPath() : id.toString();
    }

    private static boolean isTestQuest(ServerLevel level, UUID stationId) {
        return QuestSavedData.get(level)
                .stationIfPresent(stationId)
                .map(QuestTestScenario::isTestQuest)
                .orElse(false);
    }
}
