package dev.sixik.stationarenear.quest.registry;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.quest.api.QuestApi;
import dev.sixik.stationarenear.quest.data.QuestDefinition;
import dev.sixik.stationarenear.quest.data.QuestObjectiveKind;

public final class StationQuests {

    public static final String CLEAR_TRASH = id("clear_trash");
    public static final String PLACE_ITEM = id("place_item");
    public static final String PLACE_FRIDGE = id("place_fridge");
    public static final String PLACE_MICROWAVE = id("place_microwave");
    public static final String PLACE_KITCHEN_SINK = id("place_kitchen_sink");
    public static final String REPAIR_BLOCKS = id("repair_blocks");
    public static final String BUILD_SHEATHING = id("build_sheathing");
    public static final String REPAIR_DOORS = id("repair_doors");
    public static final String REPAIR_ELECTRIC_PANEL = id("repair_electric_panel");

    private StationQuests() {
    }

    public static void register() {
        register(CLEAR_TRASH, QuestObjectiveKind.CLEAR_TRASH, "\u0423\u0431\u0435\u0440\u0438\u0442\u0435 \u043c\u0443\u0441\u043e\u0440", "Clean up the station trash");
        register(PLACE_ITEM, QuestObjectiveKind.PLACE_ITEM, "\u0420\u0430\u0437\u043c\u0435\u0441\u0442\u0438\u0442\u0435 \u043d\u0443\u0436\u043d\u044b\u0435 \u043f\u0440\u0435\u0434\u043c\u0435\u0442\u044b", "Place the required items at the marked station zone");
        register(PLACE_FRIDGE, QuestObjectiveKind.PLACE_ITEM, "\u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0435 \u0445\u043e\u043b\u043e\u0434\u0438\u043b\u044c\u043d\u0438\u043a \u0440\u044f\u0434\u043e\u043c \u0441 \u0440\u043e\u0437\u0435\u0442\u043a\u043e\u0439", "Install the fridge near the power socket");
        register(PLACE_MICROWAVE, QuestObjectiveKind.PLACE_ITEM, "\u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0435 \u043c\u0438\u043a\u0440\u043e\u0432\u043e\u043b\u043d\u043e\u0432\u043a\u0443 \u0440\u044f\u0434\u043e\u043c \u0441 \u0440\u043e\u0437\u0435\u0442\u043a\u043e\u0439", "Install the microwave near the power socket");
        register(PLACE_KITCHEN_SINK, QuestObjectiveKind.PLACE_ITEM, "\u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0435 \u0440\u0430\u043a\u043e\u0432\u0438\u043d\u0443 \u0440\u044f\u0434\u043e\u043c \u0441 \u0442\u0440\u0443\u0431\u0430\u043c\u0438", "Install the kitchen sink near the pipes");
        register(REPAIR_BLOCKS, QuestObjectiveKind.REPAIR_BLOCK, "\u041f\u043e\u0447\u0438\u043d\u0438\u0442\u0435 \u043f\u043e\u0432\u0440\u0435\u0436\u0434\u0451\u043d\u043d\u044b\u0435 \u0431\u043b\u043e\u043a\u0438", "Repair the damaged blocks at the marked station zone");
        register(BUILD_SHEATHING, QuestObjectiveKind.BUILD_BLOCK, "\u041f\u043e\u0441\u0442\u0440\u043e\u0439\u0442\u0435 \u043e\u0431\u0448\u0438\u0432\u043a\u0443 \u0441\u0442\u0430\u043d\u0446\u0438\u0438", "Build the requested station sheathing at the marked station zone");
        register(REPAIR_DOORS, QuestObjectiveKind.REPAIR_DOOR, "\u041f\u043e\u0447\u0438\u043d\u0438\u0442\u0435 \u0433\u0435\u0440\u043c\u043e\u0434\u0432\u0435\u0440\u0438", "Repair the pressure doors at the marked station zone");
        register(REPAIR_ELECTRIC_PANEL, QuestObjectiveKind.REPAIR_ELECTRIC_PANEL, "\u041f\u043e\u0447\u0438\u043d\u0438\u0442\u0435 \u044d\u043b\u0435\u043a\u0442\u0440\u0438\u0447\u0435\u0441\u043a\u0438\u0439 \u0449\u0438\u0442\u043e\u043a", "Repair the station electrical panel");
    }

    public static QuestDefinition register(String id, QuestObjectiveKind kind, String playerText, String samText) {
        return QuestApi.register(id, Integer.class, kind, playerText, samText);
    }

    private static String id(String path) {
        return StationAreNear.MODID + ":" + path;
    }
}
