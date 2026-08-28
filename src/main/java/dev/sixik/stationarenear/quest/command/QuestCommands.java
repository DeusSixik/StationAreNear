package dev.sixik.stationarenear.quest.command;

import com.mojang.brigadier.CommandDispatcher;
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
import dev.sixik.stationarenear.quest.data.QuestStationState;
import dev.sixik.stationarenear.quest.runtime.QuestTestScenario;
import dev.sixik.stationarenear.quest.world.QuestSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
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
