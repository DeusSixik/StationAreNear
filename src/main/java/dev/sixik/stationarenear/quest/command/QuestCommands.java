package dev.sixik.stationarenear.quest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.sixik.stationarenear.navigation.data.SolarNavigationQuestMarker;
import dev.sixik.stationarenear.quest.api.QuestApi;
import dev.sixik.stationarenear.quest.data.QuestStationState;
import dev.sixik.stationarenear.quest.runtime.QuestTestScenario;
import dev.sixik.stationarenear.quest.world.QuestSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class QuestCommands {

    private static final String TEST_COLLECT_ID = "stationarenear:test_collect_cargo";
    private static final String TEST_ACTIVATE_ID = "stationarenear:test_activate_console";
    private static final long TEST_DURATION_SECONDS = 5L * 60L;
    private static final UUID FALLBACK_TEST_STATION_ID = UUID.nameUUIDFromBytes("stationarenear:test_station".getBytes(StandardCharsets.UTF_8));

    private QuestCommands() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(QuestCommands::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("stationarenear")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("quests")
                        .then(Commands.literal("test")
                                .then(Commands.literal("start")
                                        .executes(context -> startTestQuest(context.getSource(), 0))
                                        .then(Commands.literal("skip_trash")
                                                .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                                        .executes(context -> startTestQuest(
                                                                context.getSource(),
                                                                IntegerArgumentType.getInteger(context, "count")
                                                        )))))
                                .then(Commands.literal("stop")
                                        .executes(context -> stopTestQuest(context.getSource()))))));
    }

    private static int startTestQuest(CommandSourceStack source, int trashSpawnSkip) {
        ServerLevel level = source.getLevel();
        registerTestDefinitions();

        Optional<UUID> currentStationId = QuestApi.currentStationId(level);
        if (currentStationId.isPresent() && isTestQuest(level, currentStationId.get())) {
            QuestApi.clear(level, currentStationId.get());
        }
        QuestTestScenario.stop(level);

        SolarNavigationQuestMarker marker = QuestTestScenario.createQuestMarker(level, source.getPosition(), trashSpawnSkip);

        source.sendSuccess(() -> Component.literal("Created test quest station marker " + marker.id()
                + " code=" + dev.sixik.stationarenear.navigation.StationCodeGenerator.code(marker.seed(), marker.x(), marker.y())
                + " solar_pos=" + Math.round(marker.x()) + " " + Math.round(marker.y()) + "."), false);
        if (trashSpawnSkip > 0) {
            source.sendSuccess(() -> Component.literal("Quest trash spawn skip=" + trashSpawnSkip + "."), false);
        }
        source.sendSuccess(() -> Component.literal("Fly to it in Solar Navigation and dock. The generated station will require quest_room."), false);
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

    private static void registerTestDefinitions() {
        QuestApi.register(TEST_COLLECT_ID, Integer.class);
        QuestApi.register(TEST_ACTIVATE_ID, Boolean.class);
    }

    private static boolean isTestQuest(ServerLevel level, UUID stationId) {
        return QuestSavedData.get(level)
                .stationIfPresent(stationId)
                .map(state -> state.objective(TEST_COLLECT_ID).isPresent()
                        || state.objective(TEST_ACTIVATE_ID).isPresent()
                        || QuestTestScenario.isTestQuest(state))
                .orElse(false);
    }
}
