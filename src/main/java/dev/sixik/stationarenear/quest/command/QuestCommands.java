package dev.sixik.stationarenear.quest.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.sixik.stationarenear.quest.api.QuestApi;
import dev.sixik.stationarenear.quest.data.QuestStationState;
import dev.sixik.stationarenear.quest.world.QuestSavedData;
import dev.sixik.stationarenear.structures.data.StationInstance;
import dev.sixik.stationarenear.structures.world.StationSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
                                        .executes(context -> startTestQuest(context.getSource())))
                                .then(Commands.literal("stop")
                                        .executes(context -> stopTestQuest(context.getSource()))))));
    }

    private static int startTestQuest(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        registerTestDefinitions();

        Optional<UUID> currentStationId = QuestApi.currentStationId(level);
        if (currentStationId.isPresent() && isTestQuest(level, currentStationId.get())) {
            QuestApi.clear(level, currentStationId.get());
        }

        Optional<StationInstance> station = nearestGeneratedStation(level, source.getPosition());
        UUID stationId = station.map(StationInstance::id).orElse(FALLBACK_TEST_STATION_ID);

        QuestApi.startQuest(
                level,
                stationId,
                List.of(
                        QuestApi.quest(TEST_COLLECT_ID, 3),
                        QuestApi.quest(TEST_ACTIVATE_ID, 1)
                ),
                Map.of(
                        TEST_COLLECT_ID, "Recover three demo cargo crates",
                        TEST_ACTIVATE_ID, "Activate the demo station console"
                ),
                TEST_DURATION_SECONDS
        );

        String target = station
                .map(value -> "nearest generated station " + value.id())
                .orElse("virtual demo station " + stationId);
        source.sendSuccess(() -> Component.literal("Started demo quest on " + target + "."), false);
        source.sendSuccess(() -> Component.literal("Terminal command: /objectives. Stop command: /stationarenear quests test stop."), false);
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
                .map(state -> state.objective(TEST_COLLECT_ID).isPresent() || state.objective(TEST_ACTIVATE_ID).isPresent())
                .orElse(false);
    }

    private static Optional<StationInstance> nearestGeneratedStation(ServerLevel level, Vec3 position) {
        return StationSavedData.get(level)
                .stations()
                .stream()
                .min(Comparator.comparingDouble(station -> station.shuttleDoorCenter().distToCenterSqr(position.x(), position.y(), position.z())));
    }
}
