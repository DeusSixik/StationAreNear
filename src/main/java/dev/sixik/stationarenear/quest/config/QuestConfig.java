package dev.sixik.stationarenear.quest.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class QuestConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue AUTO_QUEST_ENABLED;
    public static final ForgeConfigSpec.IntValue SERVER_START_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.IntValue QUEST_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_ONLINE_PLAYERS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("quests");
        AUTO_QUEST_ENABLED = builder.comment("Whether Director quests are automatically assigned to players.")
                .define("auto_quest_enabled", true);
        SERVER_START_COOLDOWN_SECONDS = builder.comment("Cooldown in seconds after server starts before the first automatic quest is assigned, allowing players to load and join.")
                .defineInRange("server_start_cooldown_seconds", 60, 0, 86400);
        QUEST_INTERVAL_SECONDS = builder.comment("Interval in seconds between quests (after previous quest completes or expires) before next automatic quest is assigned.")
                .defineInRange("quest_interval_seconds", 120, 5, 86400);
        REQUIRE_ONLINE_PLAYERS = builder.comment("Whether at least one player must be online to trigger automatic quest assignment.")
                .define("require_online_players", true);
        builder.pop();
        SPEC = builder.build();
    }

    private QuestConfig() {
    }
}
