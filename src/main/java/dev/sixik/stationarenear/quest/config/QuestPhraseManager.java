package dev.sixik.stationarenear.quest.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sixik.stationarenear.StationAreNear;
import net.minecraft.util.RandomSource;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class QuestPhraseManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve(StationAreNear.MODID);
    private static final Path PHRASES_FILE = CONFIG_DIR.resolve("phrases.json");

    private static final PhraseEntry DEFAULT_QUEST_START = new PhraseEntry(
            "§e[HELICORP]§r Команда, получено новое задание. Отправляйтесь на станцию §6{station}§r. Время на выполнение: §b{time}§r. Награда: §a{reward} CR§r.",
            "Команда, у вас новое задание. Отправляйтесь на станцию {station}. Время на выполнение: {time}. {objectives}"
    );

    private static final PhraseEntry DEFAULT_FAILED_EJECTION = new PhraseEntry(
            "§c[HELICORP] Внимание! Задание на станции {station} провалено. Нарушение контракта. Процедура сброса персонала в открытый космос будет активирована через {delay} сек.§r",
            "Внимание. Задание провалено. Разгерметизация и сброс персонала через несколько секунд."
    );

    private static int ejectionDelaySeconds = 5;
    private static final List<PhraseEntry> questStartPhrases = new ArrayList<>();
    private static final List<PhraseEntry> failedEjectionPhrases = new ArrayList<>();

    private QuestPhraseManager() {
    }

    public static synchronized void init() {
        reload();
    }

    public static synchronized void reload() {
        ensureDefaultFile();
        questStartPhrases.clear();
        failedEjectionPhrases.clear();
        ejectionDelaySeconds = 5;

        if (!Files.exists(PHRASES_FILE)) {
            useDefaults();
            return;
        }

        try (Reader reader = Files.newBufferedReader(PHRASES_FILE, StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (!rootElement.isJsonObject()) {
                useDefaults();
                return;
            }

            JsonObject root = rootElement.getAsJsonObject();
            if (root.has("ejection_delay_seconds") && root.get("ejection_delay_seconds").isJsonPrimitive()) {
                ejectionDelaySeconds = Math.max(0, root.get("ejection_delay_seconds").getAsInt());
            }

            parsePhrases(root, "quest_start", questStartPhrases, DEFAULT_QUEST_START);
            parsePhrases(root, "mission_failed_ejection", failedEjectionPhrases, DEFAULT_FAILED_EJECTION);
        } catch (Exception exception) {
            StationAreNear.LOGGER.warn("Failed to load phrases from {}", PHRASES_FILE, exception);
            useDefaults();
        }

        if (questStartPhrases.isEmpty()) {
            questStartPhrases.add(DEFAULT_QUEST_START);
        }
        if (failedEjectionPhrases.isEmpty()) {
            failedEjectionPhrases.add(DEFAULT_FAILED_EJECTION);
        }
    }

    private static void parsePhrases(JsonObject root, String key, List<PhraseEntry> list, PhraseEntry fallback) {
        if (!root.has(key)) {
            list.add(fallback);
            return;
        }

        JsonElement element = root.get(key);
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                if (item.isJsonObject()) {
                    PhraseEntry entry = parseEntry(item.getAsJsonObject());
                    if (entry != null) {
                        list.add(entry);
                    }
                }
            }
        } else if (element.isJsonObject()) {
            PhraseEntry entry = parseEntry(element.getAsJsonObject());
            if (entry != null) {
                list.add(entry);
            }
        }

        if (list.isEmpty()) {
            list.add(fallback);
        }
    }

    private static PhraseEntry parseEntry(JsonObject object) {
        String text = object.has("text") ? object.get("text").getAsString() : "";
        String sam = object.has("sam") ? object.get("sam").getAsString() : "";
        if (text.isBlank() && sam.isBlank()) {
            return null;
        }
        return new PhraseEntry(text, sam);
    }

    private static void useDefaults() {
        questStartPhrases.clear();
        questStartPhrases.add(DEFAULT_QUEST_START);
        failedEjectionPhrases.clear();
        failedEjectionPhrases.add(DEFAULT_FAILED_EJECTION);
        ejectionDelaySeconds = 5;
    }

    private static void ensureDefaultFile() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            if (Files.exists(PHRASES_FILE)) {
                return;
            }

            JsonObject root = new JsonObject();
            root.addProperty("ejection_delay_seconds", 5);

            JsonArray questStartArray = new JsonArray();
            JsonObject defaultQuestStartObj = new JsonObject();
            defaultQuestStartObj.addProperty("text", DEFAULT_QUEST_START.text());
            defaultQuestStartObj.addProperty("sam", DEFAULT_QUEST_START.sam());
            questStartArray.add(defaultQuestStartObj);
            root.add("quest_start", questStartArray);

            JsonArray failedEjectionArray = new JsonArray();
            JsonObject defaultFailedEjectionObj = new JsonObject();
            defaultFailedEjectionObj.addProperty("text", DEFAULT_FAILED_EJECTION.text());
            defaultFailedEjectionObj.addProperty("sam", DEFAULT_FAILED_EJECTION.sam());
            failedEjectionArray.add(defaultFailedEjectionObj);
            root.add("mission_failed_ejection", failedEjectionArray);

            try (Writer writer = Files.newBufferedWriter(PHRASES_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception exception) {
            StationAreNear.LOGGER.warn("Failed to create default phrases file {}", PHRASES_FILE, exception);
        }
    }

    public static PhraseEntry getQuestStartPhrase(RandomSource random) {
        if (questStartPhrases.isEmpty()) {
            return DEFAULT_QUEST_START;
        }
        int index = random.nextInt(questStartPhrases.size());
        return questStartPhrases.get(index);
    }

    public static PhraseEntry getFailedEjectionPhrase(RandomSource random) {
        if (failedEjectionPhrases.isEmpty()) {
            return DEFAULT_FAILED_EJECTION;
        }
        int index = random.nextInt(failedEjectionPhrases.size());
        return failedEjectionPhrases.get(index);
    }

    public static int getEjectionDelaySeconds() {
        return ejectionDelaySeconds;
    }

    public static String format(String template, Map<String, String> placeholders) {
        if (template == null || template.isBlank()) {
            return "";
        }
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    public record PhraseEntry(String text, String sam) {
    }
}
