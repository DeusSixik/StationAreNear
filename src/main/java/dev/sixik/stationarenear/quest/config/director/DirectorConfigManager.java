package dev.sixik.stationarenear.quest.config.director;

import com.google.gson.*;
import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.quest.api.QuestApi;
import dev.sixik.stationarenear.quest.data.QuestLocalization;
import dev.sixik.stationarenear.quest.data.QuestObjectiveKind;
import dev.sixik.stationarenear.quest.world.QuestSavedData;
import dev.sixik.stationarenear.structures.config.StationStructureConfig;
import dev.sixik.stationarenear.structures.config.StationStructureConfigManager;
import dev.sixik.stationarenear.structures.generation.StationGenerationSettings;
import dev.sixik.stationarenear.structures.util.StationStructureIds;
import dev.sixik.stationarenear.structures.util.TagsConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class DirectorConfigManager {
    public static final String DEFAULT_PROFILE_ID = StationAreNear.MODID + ":default_director";
    public static final String DIRECTOR_PLAN_KEY = "directorPlan";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path DIR = FMLPaths.CONFIGDIR.get().resolve(StationAreNear.MODID).resolve("Director Configuration");
    private static final Path DIRECTORS = DIR.resolve("director.json");
    private static final Path QUESTS = DIR.resolve("quest_offers.json");
    private static final Path STATION = DIR.resolve("station_offers.json");
    private static final Path TRASH = DIR.resolve("trash_blocks.json");
    private static final List<ResourceLocation> DEFAULT_TRASH = List.of(new ResourceLocation("minecraft", "dirt"), new ResourceLocation("minecraft", "coarse_dirt"));

    private static DirectorConfigSnapshot snapshot = DirectorConfigSnapshot.empty(DEFAULT_TRASH);

    private DirectorConfigManager() {
    }

    public static synchronized void init() {
        reload();
    }

    public static synchronized int reload() {
        ensureDefaults();
        Map<String, DirectorProfileConfig> profiles = readProfiles();
        Map<String, List<QuestOfferConfig>> questPools = readQuestPools();
        Map<String, List<StationOfferConfig>> stationPools = readStationPools();
        List<ResourceLocation> trash = readTrashBlocks(TRASH, new LinkedHashSet<>());
        if (profiles.isEmpty()) {
            DirectorProfileConfig profile = new DirectorProfileConfig(DEFAULT_PROFILE_ID, "Default Director", true, 600, 200, 12.0D, 150, 0.0D, 0.35F, 1.35F, true, "default_quests", "default_station_events", 1, 3, "default_station", 1000, 5000, 86.0F, 0xFFF7C45A, 0.0D, List.of());
            profiles.put(profile.id(), profile);
        }
        snapshot = new DirectorConfigSnapshot(trash.isEmpty() ? DEFAULT_TRASH : trash, profiles, questPools, stationPools);
        registerQuestOffers();
        StationAreNear.LOGGER.info("Loaded Director config: {} profiles, {} quest pools, {} station pools", snapshot.profiles().size(), snapshot.questPools().size(), snapshot.stationPools().size());
        return snapshot.profiles().size();
    }

    public static Collection<DirectorProfileConfig> profiles() {
        return snapshot.profiles().values().stream().sorted(Comparator.comparing(DirectorProfileConfig::id)).toList();
    }

    public static Optional<DirectorProfileConfig> profile(String id) {
        String normalized = DirectorProfileConfig.normalizeId(id);
        DirectorProfileConfig direct = snapshot.profiles().get(normalized);
        if (direct != null) {
            return Optional.of(direct);
        }
        String raw = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        return snapshot.profiles().values().stream().filter(profile -> shortId(profile.id()).equals(raw)).findFirst();
    }

    public static Optional<DirectorProfileConfig> defaultProfileConfig() {
        return profile(DEFAULT_PROFILE_ID).or(() -> snapshot.profiles().values().stream().findFirst());
    }

    public static List<DirectorProfileConfig> availableProfiles(ServerLevel level) {
        Set<String> completed = QuestSavedData.get(level).completedQuestIds();
        return snapshot.profiles().values().stream()
                .filter(DirectorProfileConfig::enabled)
                .filter(profile -> profile.requiredQuests().stream().allMatch(completed::contains))
                .filter(profile -> !isCompletedChainGate(profile, completed))
                .sorted(Comparator.comparing(DirectorProfileConfig::id))
                .toList();
    }

    private static boolean isCompletedChainGate(DirectorProfileConfig profile, Set<String> completed) {
        return completed.contains(profile.id()) && snapshot.profiles().values().stream()
                .anyMatch(other -> other.requiredQuests().contains(profile.id()));
    }

    public static Optional<DirectorProfileConfig> randomAvailableProfile(ServerLevel level, RandomSource random) {
        List<DirectorProfileConfig> profiles = availableProfiles(level);
        return profiles.isEmpty() ? Optional.empty() : Optional.of(profiles.get(random.nextInt(profiles.size())));
    }

    public static List<QuestOfferConfig> questOffers(String poolId) {
        return snapshot.questPools().getOrDefault(DirectorProfileConfig.normalizeId(poolId), List.of());
    }

    public static List<StationOfferConfig> stationOffers(String poolId) {
        return snapshot.stationPools().getOrDefault(DirectorProfileConfig.normalizeId(poolId), List.of());
    }

    public static Map<String, QuestLocalization> questLocalizations() {
        Map<String, QuestLocalization> result = new LinkedHashMap<>();
        snapshot.questPools().values().forEach(pool -> pool.forEach(offer -> result.put(offer.id(), offer.localization())));
        return Map.copyOf(result);
    }

    public static List<ResourceLocation> trashBlockIds() {
        return snapshot.trashBlocks();
    }

    public static List<BlockState> trashBlockStates() {
        List<BlockState> states = new ArrayList<>();
        for (ResourceLocation id : snapshot.trashBlocks()) {
            Block block = ForgeRegistries.BLOCKS.getValue(id);
            if (block != null && block != Blocks.AIR) {
                states.add(block.defaultBlockState());
            }
        }
        return states.isEmpty() ? List.of(Blocks.DIRT.defaultBlockState()) : List.copyOf(states);
    }

    public static boolean isTrashBlock(BlockState state) {
        return snapshot.trashBlocks().contains(ForgeRegistries.BLOCKS.getKey(state.getBlock()));
    }

    public static StationGenerationSettings createStationSettings(ServerLevel level, dev.sixik.stationarenear.quest.director.DirectorPlan plan, long seed, StationGenerationSettings fallback) {
        float directorDanger = plan.profile().dangerForCompletedMissions(plan.credits().completedMissionCount(), 1.0F);
        StationGenerationSettings settings = baseStationSettings(level, plan.profile().stationConfig(), seed, directorDanger, fallback);
        settings = applyQuestRequirements(settings, plan.requiredPieces(), plan.requiredPieceTags(), plan.questElementSpawnSkips());
        CompoundTag savedPlan = plan.save();
        savedPlan.putFloat("missionDanger", settings.missionDanger());
        CompoundTag customData = settings.customData();
        customData.put(DIRECTOR_PLAN_KEY, savedPlan);
        return settings.withCustomData(customData);
    }

    public static StationGenerationSettings createStationSettings(ServerLevel level, CompoundTag directorPlan, long seed, StationGenerationSettings fallback) {
        if (directorPlan == null || directorPlan.isEmpty()) {
            return fallback == null ? defaultStationSettings(level.getRandom(), seed, StationStructureConfig.DEFAULT_BASE_DANGER) : fallback;
        }
        float directorDanger = directorDanger(directorPlan);
        StationGenerationSettings settings = baseStationSettings(level, directorPlan.getString("stationConfig"), seed, directorDanger, fallback);
        settings = applyQuestRequirements(settings, readResourceCountTag(directorPlan.getCompound("requiredPieces")), readStringCountTag(directorPlan.getCompound("requiredPieceTags")), readStringCountTag(directorPlan.getCompound("questElementSpawnSkips")));
        CompoundTag savedPlan = directorPlan.copy();
        savedPlan.putFloat("missionDanger", settings.missionDanger());
        CompoundTag customData = settings.customData();
        customData.put(DIRECTOR_PLAN_KEY, savedPlan);
        return settings.withCustomData(customData);
    }

    public static StationGenerationSettings applyQuestRequirements(StationGenerationSettings settings, Map<ResourceLocation, Integer> pieces, Map<String, Integer> tags, Map<String, Integer> skips) {
        Map<ResourceLocation, Integer> mergedPieces = new LinkedHashMap<>(settings.requiredPieces());
        Map<String, Integer> mergedTags = new LinkedHashMap<>(settings.requiredPieceTags());
        Map<String, Integer> mergedSkips = new LinkedHashMap<>(settings.questElementSpawnSkips());
        merge(mergedPieces, pieces);
        merge(mergedTags, tags);
        merge(mergedSkips, skips);
        return new StationGenerationSettings(settings.pool(), settings.missionDanger(), settings.randomStation(), settings.maxFloors(), settings.minRooms(), Math.max(settings.maxRooms(), sum(mergedPieces) + sum(mergedTags)), settings.seed(), mergedPieces, mergedTags, mergedSkips, settings.customData());
    }

    public static CompoundTag saveResourceCountMap(Map<ResourceLocation, Integer> map) {
        CompoundTag tag = new CompoundTag();
        if (map != null) {
            map.forEach((key, value) -> { if (key != null && value != null && value > 0) tag.putInt(key.toString(), value); });
        }
        return tag;
    }

    public static CompoundTag saveStringCountMap(Map<String, Integer> map) {
        CompoundTag tag = new CompoundTag();
        if (map != null) {
            map.forEach((key, value) -> { if (key != null && !key.isBlank() && value != null && value > 0) tag.putInt(key, value); });
        }
        return tag;
    }

    private static Map<String, DirectorProfileConfig> readProfiles() {
        Map<String, DirectorProfileConfig> result = new LinkedHashMap<>();
        try (Reader reader = Files.newBufferedReader(DIRECTORS, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonElement entries = root.has("profiles") ? root.get("profiles") : root;
            if (entries.isJsonArray()) {
                for (JsonElement element : entries.getAsJsonArray()) addProfile(result, element.getAsJsonObject());
            } else if (entries.isJsonObject()) {
                addProfile(result, entries.getAsJsonObject());
            }
        } catch (Exception exception) {
            StationAreNear.LOGGER.warn("Failed to load Director profiles from {}", DIRECTORS, exception);
        }
        return result;
    }

    private static void addProfile(Map<String, DirectorProfileConfig> result, JsonObject object) {
        String id = string(object, "id", DEFAULT_PROFILE_ID);
        JsonObject objectiveCount = object(object, "objective_count");
        JsonObject distance = object(object, "spawn_distance");
        if (distance == null) distance = object(object, "distance");
        JsonObject budgetFormula = object(object, "budget_formula");
        double questBudgetPerPlayMinute = budgetFormula == null
                ? doubleNumber(object, "quest_budget_per_play_minute", 12.0D)
                : doubleNumber(budgetFormula, "quest_per_play_minute", doubleNumber(object, "quest_budget_per_play_minute", 12.0D));
        double stationBudgetPerPlayMinute = budgetFormula == null
                ? doubleNumber(object, "station_budget_per_play_minute", 0.0D)
                : doubleNumber(budgetFormula, "station_per_play_minute", doubleNumber(object, "station_budget_per_play_minute", 0.0D));
        JsonObject dangerFormula = object(object, "danger_formula");
        float baseDanger = dangerFormula == null
                ? number(object, "base_danger", 0.35F)
                : number(dangerFormula, "base", number(object, "base_danger", 0.35F));
        float dangerGrowthMultiplier = dangerFormula == null
                ? number(object, "danger_growth_multiplier", 1.35F)
                : number(dangerFormula, "growth_multiplier", number(object, "danger_growth_multiplier", 1.35F));
        DirectorProfileConfig profile = new DirectorProfileConfig(id, string(object, "name", id), bool(object, "enabled", true), duration(object, 600), integer(object, "quest_budget", 200), questBudgetPerPlayMinute, integer(object, "station_budget", 150), stationBudgetPerPlayMinute, baseDanger, dangerGrowthMultiplier, bool(object, "leftover_quest_credits_to_station", true), string(object, "quest_offer_pool", "default_quests"), string(object, "station_offer_pool", "default_station_events"), objectiveCount == null ? integer(object, "min_objectives", 1) : integer(objectiveCount, "min", 1), objectiveCount == null ? integer(object, "max_objectives", 3) : integer(objectiveCount, "max", 3), string(object, "station_config", "default_station"), distance == null ? integer(object, "min_distance", 1000) : integer(distance, "min", 1000), distance == null ? integer(object, "max_distance", 5000) : integer(distance, "max", 5000), number(object, "marker_radius", 86.0F), color(object, "marker_color", 0xFFF7C45A), doubleNumber(object, "money_reward", 0.0D), normalizeIds(stringList(object.get("required_quests"))));
        result.put(profile.id(), profile);
    }

    private static Map<String, List<QuestOfferConfig>> readQuestPools() {
        Map<String, List<QuestOfferConfig>> result = new LinkedHashMap<>();
        try (Reader reader = Files.newBufferedReader(QUESTS, StandardCharsets.UTF_8)) {
            JsonObject pools = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("pools");
            for (Map.Entry<String, JsonElement> entry : pools.entrySet()) {
                List<QuestOfferConfig> offers = new ArrayList<>();
                for (JsonElement element : entry.getValue().getAsJsonArray()) parseQuestOffer(element.getAsJsonObject()).ifPresent(offers::add);
                result.put(DirectorProfileConfig.normalizeId(entry.getKey()), List.copyOf(offers));
            }
        } catch (Exception exception) {
            StationAreNear.LOGGER.warn("Failed to load Director quest offers from {}", QUESTS, exception);
        }
        return result;
    }

    private static Optional<QuestOfferConfig> parseQuestOffer(JsonObject object) {
        String id = string(object, "id", "");
        if (id.isBlank()) return Optional.empty();
        int[] count = countRange(object.get("count"));
        Map<ResourceLocation, Integer> pieces = resourceCountMap(object(object, TagsConstants.Keys.REQUIRED_PIECES));
        List<String> targetTags = stringList(object.get(TagsConstants.Keys.TARGET_TAGS));
        String placeItem = string(object, "place_item", "");
        QuestObjectiveKind kind = questKind(string(object, "kind", "CUSTOM"));
        Map<String, Integer> tags = defaultRequiredPieceTags(stringCountMap(object(object, TagsConstants.Keys.REQUIRED_PIECE_TAGS)), targetTags, !placeItem.isBlank(), kind);
        return Optional.of(new QuestOfferConfig(id, kind, integer(object, "cost", 0), integer(object, "weight", 1), count[0], count[1], normalizeIds(stringList(object.get("required_quests"))), string(object, "text", id), string(object, "sam_text", ""), pieces, tags, stringCountMap(object(object, TagsConstants.Keys.QUEST_ELEMENT_SPAWN_SKIPS)), targetTags, placeItem, string(object, "exclusive_group", ""), integer(object, "max_per_mission", 1)));
    }

    private static Map<String, List<StationOfferConfig>> readStationPools() {
        Map<String, List<StationOfferConfig>> result = new LinkedHashMap<>();
        try (Reader reader = Files.newBufferedReader(STATION, StandardCharsets.UTF_8)) {
            JsonObject pools = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("pools");
            for (Map.Entry<String, JsonElement> entry : pools.entrySet()) {
                List<StationOfferConfig> offers = new ArrayList<>();
                for (JsonElement element : entry.getValue().getAsJsonArray()) parseStationOffer(element.getAsJsonObject()).ifPresent(offers::add);
                result.put(DirectorProfileConfig.normalizeId(entry.getKey()), List.copyOf(offers));
            }
        } catch (Exception exception) {
            StationAreNear.LOGGER.warn("Failed to load Director station offers from {}", STATION, exception);
        }
        return result;
    }

    private static Map<String, Integer> defaultRequiredPieceTags(Map<String, Integer> configuredTags, List<String> targetTags, boolean requiresQuestObjectPlacer, QuestObjectiveKind kind) {
        boolean useTargetTags = configuredTags.isEmpty()
                || configuredTags.size() == 1 && configuredTags.containsKey(TagsConstants.Quest.QUEST_ROOM);
        if (!useTargetTags) {
            return configuredTags;
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        if (kind == QuestObjectiveKind.CLEAR_TRASH) {
            result.merge(TagsConstants.Trigger.OBJECT_ZONE_PLACER, 1, Integer::sum);
            return Map.copyOf(result);
        }
        if (kind == QuestObjectiveKind.REPAIR_ELECTRIC_PANEL || kind == QuestObjectiveKind.REPAIR_GRAVITATION_PANEL || kind == QuestObjectiveKind.REPAIR_OXYGEN_PANEL) {
            result.merge(TagsConstants.Trigger.QUEST_OBJECT_PLACER, 1, Integer::sum);
            return Map.copyOf(result);
        }
        if (targetTags != null) {
            for (String tag : targetTags) {
                String normalized = tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT);
                if (!normalized.isBlank()) {
                    result.merge(normalized, 1, Integer::sum);
                }
            }
        }
        if (requiresQuestObjectPlacer) {
            result.merge(TagsConstants.Trigger.QUEST_OBJECT_PLACER, 1, Integer::sum);
        }
        return result.isEmpty() ? configuredTags : Map.copyOf(result);
    }

    private static Optional<StationOfferConfig> parseStationOffer(JsonObject object) {
        String id = string(object, "id", "");
        if (id.isBlank()) return Optional.empty();
        int[] count = countRange(object.get("count"));
        return Optional.of(new StationOfferConfig(id, StationOfferType.from(string(object, "type", "CUSTOM")), integer(object, "cost", 0), integer(object, "weight", 1), count[0], count[1], string(object, "entity", ""), stringList(object.get(TagsConstants.Keys.TARGET_TAGS)), integer(object, "max_per_station", 64)));
    }

    private static void registerQuestOffers() {
        Set<String> done = new HashSet<>();
        snapshot.questPools().values().forEach(pool -> pool.forEach(offer -> { if (done.add(offer.id())) QuestApi.register(offer.id(), Integer.class, offer.kind(), offer.localization()); }));
    }

    private static StationGenerationSettings baseStationSettings(ServerLevel level, String stationConfig, long seed, float baseDanger, StationGenerationSettings fallback) {
        return StationStructureConfigManager.get(DirectorProfileConfig.cleanStationConfig(stationConfig))
                .map(config -> config.createSettings(level.getRandom(), seed, baseDanger))
                .orElseGet(() -> fallback == null ? defaultStationSettings(level.getRandom(), seed, baseDanger) : fallback.withMissionDanger(baseDanger));
    }

    private static StationGenerationSettings defaultStationSettings(RandomSource random, long seed, float baseDanger) {
        return StationStructureConfigManager.random(random)
                .map(config -> config.createSettings(random, seed, baseDanger))
                .orElseGet(() -> new StationGenerationSettings(StationStructureIds.pool("space_station"), baseDanger, true, 1, 10, 20, seed));
    }

    private static float directorDanger(CompoundTag directorPlan) {
        int completedMissionCount = Math.max(0, directorPlan.getInt("completedMissionCount"));
        float baseDanger = directorPlan.contains("baseDanger") ? directorPlan.getFloat("baseDanger") : 0.35F;
        float dangerGrowthMultiplier = directorPlan.contains("dangerGrowthMultiplier") ? directorPlan.getFloat("dangerGrowthMultiplier") : 1.35F;
        return DirectorProfileConfig.dangerForCompletedMissions(baseDanger, dangerGrowthMultiplier, completedMissionCount, 1.0F);
    }

    private static List<ResourceLocation> readTrashBlocks(Path path, Set<Path> visited) {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        Path normalized = path.toAbsolutePath().normalize();
        if (!visited.add(normalized) || !Files.isRegularFile(normalized)) return List.of();
        try (Reader reader = Files.newBufferedReader(normalized, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            JsonArray array = root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().getAsJsonArray("trash_blocks");
            for (JsonElement element : array) {
                String value = element.getAsString().trim();
                if (value.endsWith(".json")) result.addAll(readTrashBlocks(normalized.getParent().resolve(value), visited));
                else Optional.ofNullable(ResourceLocation.tryParse(value)).ifPresent(result::add);
            }
        } catch (Exception exception) {
            StationAreNear.LOGGER.warn("Failed to load Director trash blocks from {}", path, exception);
        }
        return List.copyOf(result);
    }

    private static void ensureDefaults() {
        try {
            Files.createDirectories(DIR);
            if (!Files.isRegularFile(DIRECTORS)) Files.writeString(DIRECTORS, DEFAULT_DIRECTOR_JSON, StandardCharsets.UTF_8);
            if (!Files.isRegularFile(QUESTS)) Files.writeString(QUESTS, DEFAULT_QUEST_OFFERS_JSON, StandardCharsets.UTF_8);
            if (!Files.isRegularFile(STATION)) Files.writeString(STATION, DEFAULT_STATION_OFFERS_JSON, StandardCharsets.UTF_8);
            if (!Files.isRegularFile(TRASH)) Files.writeString(TRASH, "[\n  \"minecraft:dirt\",\n  \"minecraft:coarse_dirt\"\n]\n", StandardCharsets.UTF_8);
        } catch (Exception exception) {
            StationAreNear.LOGGER.warn("Failed to write default Director config files in {}", DIR, exception);
        }
    }

    private static JsonObject object(JsonObject object, String key) { JsonElement element = object.get(key); return element != null && element.isJsonObject() ? element.getAsJsonObject() : null; }
    private static String string(JsonObject object, String key, String fallback) { JsonElement element = object.get(key); return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback; }
    private static boolean bool(JsonObject object, String key, boolean fallback) { JsonElement element = object.get(key); return element != null && element.isJsonPrimitive() ? element.getAsBoolean() : fallback; }
    private static int integer(JsonObject object, String key, int fallback) { JsonElement element = object.get(key); return element != null && element.isJsonPrimitive() ? element.getAsInt() : fallback; }
    private static float number(JsonObject object, String key, float fallback) { JsonElement element = object.get(key); return element != null && element.isJsonPrimitive() ? element.getAsFloat() : fallback; }
    private static double doubleNumber(JsonObject object, String key, double fallback) { JsonElement element = object.get(key); return element != null && element.isJsonPrimitive() ? element.getAsDouble() : fallback; }
    private static long duration(JsonObject object, long fallback) { return object.has("duration_minutes") ? Math.max(1L, Math.round(object.get("duration_minutes").getAsDouble() * 60.0D)) : Math.max(1L, object.has("duration_seconds") ? object.get("duration_seconds").getAsLong() : fallback); }
    private static int color(JsonObject object, String key, int fallback) { try { String value = string(object, key, ""); if (value.isBlank()) return fallback; int color = value.startsWith("#") ? (int) Long.parseLong(value.substring(1), 16) : Integer.decode(value); return (color & 0xFF000000) == 0 ? color | 0xFF000000 : color; } catch (RuntimeException exception) { return fallback; } }
    private static String shortId(String id) { int separator = id.indexOf(':'); return separator >= 0 ? id.substring(separator + 1) : id; }
    private static List<String> normalizeIds(List<String> ids) { return ids.stream().map(DirectorProfileConfig::normalizeId).filter(id -> !id.isBlank()).toList(); }
    private static int[] countRange(JsonElement element) { if (element == null || element.isJsonNull()) return new int[]{1, 1}; if (element.isJsonPrimitive()) { int count = Math.max(1, element.getAsInt()); return new int[]{count, count}; } JsonObject object = element.getAsJsonObject(); int min = integer(object, "min", 1); int max = integer(object, "max", min); return new int[]{Math.max(1, min), Math.max(min, max)}; }
    private static List<String> stringList(JsonElement element) { if (element == null || element.isJsonNull()) return List.of(); List<String> values = new ArrayList<>(); if (element.isJsonArray()) element.getAsJsonArray().forEach(child -> addStringParts(values, child.getAsString())); else if (element.isJsonPrimitive()) addStringParts(values, element.getAsString()); return values.stream().map(value -> value.trim().toLowerCase(Locale.ROOT)).filter(value -> !value.isBlank()).distinct().toList(); }
    private static void addStringParts(List<String> values, String value) { if (value != null) for (String part : value.split("[,;]")) if (!part.isBlank()) values.add(part.trim()); }
    private static Map<ResourceLocation, Integer> resourceCountMap(JsonObject object) { Map<ResourceLocation, Integer> result = new LinkedHashMap<>(); if (object != null) object.entrySet().forEach(entry -> { int count = readCount(entry.getValue()); if (count > 0) result.merge(StationStructureIds.normalize(entry.getKey(), "stations/new_piece"), count, Integer::sum); }); return result; }
    private static Map<String, Integer> stringCountMap(JsonObject object) { Map<String, Integer> result = new LinkedHashMap<>(); if (object != null) object.entrySet().forEach(entry -> { int count = readCount(entry.getValue()); if (count > 0) result.merge(entry.getKey().toLowerCase(Locale.ROOT), count, Integer::sum); }); return result; }
    private static int readCount(JsonElement element) { if (element == null || element.isJsonNull()) return 0; if (element.isJsonPrimitive()) return Math.max(0, element.getAsInt()); return integer(element.getAsJsonObject(), "count", 1); }
    private static QuestObjectiveKind questKind(String value) { try { return QuestObjectiveKind.valueOf(value.toUpperCase(Locale.ROOT)); } catch (RuntimeException exception) { return QuestObjectiveKind.CUSTOM; } }
    private static Map<ResourceLocation, Integer> readResourceCountTag(CompoundTag tag) { Map<ResourceLocation, Integer> result = new LinkedHashMap<>(); for (String key : tag.getAllKeys()) Optional.ofNullable(ResourceLocation.tryParse(key)).ifPresent(id -> { if (tag.getInt(key) > 0) result.put(id, tag.getInt(key)); }); return result; }
    private static Map<String, Integer> readStringCountTag(CompoundTag tag) { Map<String, Integer> result = new LinkedHashMap<>(); for (String key : tag.getAllKeys()) if (tag.getInt(key) > 0) result.put(key, tag.getInt(key)); return result; }
    private static <K> void merge(Map<K, Integer> target, Map<K, Integer> source) { if (source != null) source.forEach((key, value) -> { if (key != null && value != null && value > 0) target.merge(key, value, Integer::sum); }); }
    private static int sum(Map<?, Integer> map) { int total = 0; for (int value : map.values()) total += value; return total; }

    private static final String DEFAULT_DIRECTOR_JSON = """
            {
              "profiles": [
                {
                  "id": "stationarenear:default_director",
                  "name": "Default Director",
                  "enabled": true,
                  "duration_seconds": 600,
                  "quest_budget": 200,
                  "station_budget": 150,
                  "budget_formula": {
                    "quest_per_play_minute": 12.0,
                    "station_per_play_minute": 0.0
                  },
                  "danger_formula": {
                    "base": 0.35,
                    "growth_multiplier": 1.35
                  },
                  "leftover_quest_credits_to_station": true,
                  "quest_offer_pool": "stationarenear:default_quests",
                  "station_offer_pool": "stationarenear:default_station_events",
                  "objective_count": { "min": 1, "max": 3 },
                  "station_config": "default_station",
                  "spawn_distance": { "min": 1000, "max": 5000 },
                  "marker_radius": 86.0,
                  "marker_color": "#F7C45A",
                  "money_reward": 0.0,
                  "required_quests": []
                }
              ]
            }
            """;
    private static final String DEFAULT_QUEST_OFFERS_JSON = """
            {
              "pools": {
                "stationarenear:default_quests": [
                  {
                    "id": "stationarenear:clear_trash",
                    "kind": "CLEAR_TRASH",
                    "cost": 80,
                    "weight": 10,
                    "count": { "min": 12, "max": 24 },
                    "%s": ["%s"],
                    "text": "Убрать мусор в отмеченных секциях станции",
                    "sam_text": "Clean up trash in the marked station sections."
                  },
                  {
                    "id": "stationarenear:repair_doors",
                    "kind": "REPAIR_DOOR",
                    "cost": 40,
                    "weight": 6,
                    "count": { "min": 1, "max": 2 },
                    "%s": ["%s"],
                    "text": "Починить сломанные гермодвери",
                    "sam_text": "Repair the broken pressure doors."
                  },
                  {
                    "id": "stationarenear:place_fridge",
                    "kind": "PLACE_ITEM",
                    "cost": 60,
                    "weight": 5,
                    "count": 1,
                    "%s": ["%s"],
                    "place_item": "stationarenear:fridge",
                    "text": "Установить холодильник рядом с розеткой",
                    "sam_text": "Install the fridge near the marked power socket."
                  },
                  {
                    "id": "stationarenear:place_microwave",
                    "kind": "PLACE_ITEM",
                    "cost": 50,
                    "weight": 5,
                    "count": 1,
                    "%s": ["%s"],
                    "place_item": "stationarenear:microwave",
                    "text": "Установить микроволновку рядом с розеткой",
                    "sam_text": "Install the microwave near the marked power socket."
                  },
                  {
                    "id": "stationarenear:place_kitchen_sink",
                    "kind": "PLACE_ITEM",
                    "cost": 50,
                    "weight": 5,
                    "count": 1,
                    "%s": ["%s"],
                    "place_item": "stationarenear:kitchen_sink",
                    "text": "Установить раковину рядом с трубами",
                    "sam_text": "Install the sink near the marked pipes."
                  },
                  {
                    "id": "stationarenear:repair_electric_panel",
                    "kind": "REPAIR_ELECTRIC_PANEL",
                    "cost": 70,
                    "weight": 3,
                    "count": 1,
                    "%s": ["%s"],
                    "text": "Починить электрический щиток",
                    "sam_text": "Repair the station electrical panel."
                  },
                  {
                    "id": "stationarenear:repair_gravitation_panel",
                    "kind": "REPAIR_GRAVITATION_PANEL",
                    "cost": 60,
                    "weight": 4,
                    "count": 1,
                    "%s": ["%s"],
                    "text": "Починить панель гравитации",
                    "sam_text": "Repair the station gravitation panel."
                  },
                  {
                    "id": "stationarenear:repair_oxygen_panel",
                    "kind": "REPAIR_OXYGEN_PANEL",
                    "cost": 70,
                    "weight": 4,
                    "count": 1,
                    "%s": ["%s"],
                    "text": "Починить панель кислорода",
                    "sam_text": "Repair the station oxygen panel."
                  }
                ]
              }
            }
            """.formatted(
            TagsConstants.Keys.TARGET_TAGS, TagsConstants.Quest.TRASH,
            TagsConstants.Keys.TARGET_TAGS, TagsConstants.Trigger.DOOR_TRIGGER,
            TagsConstants.Keys.TARGET_TAGS, TagsConstants.Quest.ELECTRIC,
            TagsConstants.Keys.TARGET_TAGS, TagsConstants.Quest.ELECTRIC,
            TagsConstants.Keys.TARGET_TAGS, "kitchen_sink",
            TagsConstants.Keys.TARGET_TAGS, TagsConstants.Quest.ELECTRIC_SWITCH,
            TagsConstants.Keys.TARGET_TAGS, TagsConstants.Quest.GRAVITATION_PANEL,
            TagsConstants.Keys.TARGET_TAGS, TagsConstants.Quest.OXYGEN_PANEL
    );
    private static final String DEFAULT_STATION_OFFERS_JSON = """
            {
              "pools": {
                "stationarenear:default_station_events": [
                  {
                    "id": "stationarenear:cadaver",
                    "type": "MOB",
                    "cost": 100,
                    "weight": 4,
                    "count": 1,
                    "entity": "stationarenear:cadaver",
                    "%s": ["%s"]
                  },
                  {
                    "id": "stationarenear:living_trash",
                    "type": "MOB",
                    "cost": 20,
                    "weight": 10,
                    "count": 1,
                    "entity": "stationarenear:living_trash",
                    "%s": ["%s"]
                  },
                  {
                    "id": "stationarenear:broken_door",
                    "type": "BROKEN_DOOR",
                    "cost": 20,
                    "weight": 6,
                    "count": 1,
                    "%s": ["%s"]
                  },
                  {
                    "id": "stationarenear:extra_props",
                    "type": "OBJECT",
                    "cost": 10,
                    "weight": 6,
                    "count": { "min": 1, "max": 4 },
                    "%s": ["%s"]
                  },
                  {
                    "id": "stationarenear:energy_failure",
                    "type": "ENERGY_FAILURE",
                    "cost": 40,
                    "weight": 3,
                    "count": 1,
                    "%s": ["%s"]
                  },
                  {
                    "id": "stationarenear:gravitation_failure",
                    "type": "GRAVITATION_FAILURE",
                    "cost": 40,
                    "weight": 3,
                    "count": 1,
                    "%s": ["%s"]
                  },
                  {
                    "id": "stationarenear:oxygen_failure",
                    "type": "OXYGEN_FAILURE",
                    "cost": 40,
                    "weight": 3,
                    "count": 1,
                    "%s": ["%s"]
                  }
                ]
              }
            }
            """.formatted(
            TagsConstants.Keys.TARGET_TAGS, TagsConstants.Trigger.MOB_SPAWN,
            TagsConstants.Keys.TARGET_TAGS, TagsConstants.Trigger.MOB_SPAWN,
            TagsConstants.Keys.TARGET_TAGS, TagsConstants.Trigger.DOOR_TRIGGER,
            TagsConstants.Keys.TARGET_TAGS, TagsConstants.Trigger.OBJECT_ZONE_PLACER,
            TagsConstants.Keys.TARGET_TAGS, TagsConstants.Quest.ELECTRIC_SWITCH,
            TagsConstants.Keys.TARGET_TAGS, TagsConstants.Quest.GRAVITATION_PANEL,
            TagsConstants.Keys.TARGET_TAGS, TagsConstants.Quest.OXYGEN_PANEL
    );
}
