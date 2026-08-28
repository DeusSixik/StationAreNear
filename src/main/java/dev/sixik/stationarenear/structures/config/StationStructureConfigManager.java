package dev.sixik.stationarenear.structures.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.structures.util.StationStructureIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class StationStructureConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String DEFAULT_CONFIG_FILE = "default_station.json";

    private static Map<ResourceLocation, StationStructureConfig> configurations = Map.of();

    private StationStructureConfigManager() {
    }

    public static synchronized void init() {
        StationStructureFileStorage.ensureDirectories();
        reload();
    }

    public static synchronized int reload() {
        StationStructureFileStorage.ensureDirectories();
        ensureDefaultConfig();

        Map<ResourceLocation, StationStructureConfig> loadedConfigurations = new LinkedHashMap<>();
        Path directory = StationStructureFileStorage.structureConfigurationsDirectory();
        if (Files.isDirectory(directory)) {
            try (Stream<Path> paths = Files.walk(directory)) {
                List<Path> jsonFiles = paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        .sorted(Comparator.comparing(Path::toString))
                        .toList();
                for (Path path : jsonFiles) {
                    parse(path).ifPresent(config -> loadedConfigurations.put(config.id(), config));
                }
            } catch (IOException exception) {
                StationAreNear.LOGGER.warn("Failed to scan station structure configuration folder {}", directory, exception);
            }
        }

        configurations = Map.copyOf(loadedConfigurations);
        StationAreNear.LOGGER.info("Loaded {} StationAreNear structure generation configs from {}", configurations.size(), directory);
        return configurations.size();
    }

    public static Collection<StationStructureConfig> configurations() {
        return configurations.values();
    }

    public static Optional<StationStructureConfig> get(String idText) {
        ResourceLocation id = StationStructureIds.normalize(idText, StationStructureConfig.DEFAULT_ID.getPath());
        StationStructureConfig config = configurations.get(id);
        if (config != null) {
            return Optional.of(config);
        }
        for (StationStructureConfig candidate : configurations.values()) {
            if (candidate.id().getPath().equals(idText)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public static Optional<StationStructureConfig> random(RandomSource random) {
        if (configurations.isEmpty()) {
            reload();
        }
        if (configurations.isEmpty()) {
            return Optional.empty();
        }
        List<StationStructureConfig> values = new ArrayList<>(configurations.values());
        return Optional.of(values.get(random.nextInt(values.size())));
    }

    private static Optional<StationStructureConfig> parse(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                StationAreNear.LOGGER.warn("Skipped station structure config {}: root must be object", path);
                return Optional.empty();
            }
            return Optional.of(parseObject(root.getAsJsonObject(), path));
        } catch (IOException | RuntimeException exception) {
            StationAreNear.LOGGER.warn("Skipped broken station structure config {}", path, exception);
            return Optional.empty();
        }
    }

    private static StationStructureConfig parseObject(JsonObject object, Path path) {
        ResourceLocation fallbackId = StationStructureIds.normalize(fileNameWithoutExtension(path), StationStructureConfig.DEFAULT_ID.getPath());
        ResourceLocation id = readResourceLocation(object, fallbackId, "id", "name");
        ResourceLocation pool = readResourceLocation(object, StationStructureIds.pool("space_station"), "pool", "structurePool");

        int maxFloors = readInt(object, 1, "maxFloors", "floors", "floorCount");
        int minRooms = readInt(object, 10, "minRooms", "minRoomCount");
        int maxRooms = readInt(object, 18, "maxRooms", "maxRoomCount", "rooms");
        JsonObject rooms = readObject(object, "rooms", "roomCount");
        if (rooms != null) {
            minRooms = readInt(rooms, minRooms, "min", "minRooms");
            maxRooms = readInt(rooms, maxRooms, "max", "maxRooms");
        }

        float minDanger = readFloat(object, 0.25F, "minDanger", "dangerMin");
        float maxDanger = readFloat(object, 0.55F, "maxDanger", "dangerMax");
        JsonObject danger = readObject(object, "danger", "dangerLevel", "dangerRange");
        if (danger != null) {
            minDanger = readFloat(danger, minDanger, "min", "minDanger");
            maxDanger = readFloat(danger, maxDanger, "max", "maxDanger");
        }

        Map<ResourceLocation, Integer> requiredPieces = parseResourceCountMap(readObject(object, "requiredPieces", "requiredTemplates"));
        Map<String, Integer> requiredPieceTags = parseStringCountMap(readObject(object, "requiredPieceTags", "requiredTags"));
        mergeRequiredRooms(readObject(object, "requiredRooms", "mandatoryRooms"), requiredPieces, requiredPieceTags);
        Map<String, Integer> questElementSpawnSkips = parseStringCountMap(readObject(object, "questElementSpawnSkips", "spawnSkips"));

        return new StationStructureConfig(
                id,
                pool,
                maxFloors,
                minRooms,
                maxRooms,
                minDanger,
                maxDanger,
                requiredPieces,
                requiredPieceTags,
                questElementSpawnSkips
        );
    }

    private static void mergeRequiredRooms(JsonObject object, Map<ResourceLocation, Integer> requiredPieces, Map<String, Integer> requiredPieceTags) {
        if (object == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            int count = readCount(entry.getValue());
            if (count <= 0) {
                continue;
            }
            String key = entry.getKey().trim();
            if (key.startsWith("#") || (!key.contains(":") && !key.contains("/"))) {
                requiredPieceTags.merge(normalizeTag(key.startsWith("#") ? key.substring(1) : key), count, Integer::sum);
            } else {
                requiredPieces.merge(StationStructureIds.normalize(key, "stations/new_piece"), count, Integer::sum);
            }
        }
    }

    private static Map<ResourceLocation, Integer> parseResourceCountMap(JsonObject object) {
        Map<ResourceLocation, Integer> map = new LinkedHashMap<>();
        if (object == null) {
            return map;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            int count = readCount(entry.getValue());
            if (count > 0) {
                map.merge(StationStructureIds.normalize(entry.getKey(), "stations/new_piece"), count, Integer::sum);
            }
        }
        return map;
    }

    private static Map<String, Integer> parseStringCountMap(JsonObject object) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (object == null) {
            return map;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            int count = readCount(entry.getValue());
            String key = normalizeTag(entry.getKey());
            if (!key.isBlank() && count > 0) {
                map.merge(key, count, Integer::sum);
            }
        }
        return map;
    }

    private static int readCount(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return 0;
        }
        if (element.isJsonPrimitive()) {
            return Math.max(0, element.getAsInt());
        }
        if (element.isJsonObject()) {
            return readInt(element.getAsJsonObject(), 1, "count", "amount");
        }
        return 0;
    }

    private static ResourceLocation readResourceLocation(JsonObject object, ResourceLocation fallback, String... keys) {
        String value = readString(object, null, keys);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return StationStructureIds.normalize(value, fallback.getPath());
    }

    private static JsonObject readObject(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element != null && element.isJsonObject()) {
                return element.getAsJsonObject();
            }
        }
        return null;
    }

    private static String readString(JsonObject object, String fallback, String... keys) {
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element != null && element.isJsonPrimitive()) {
                return element.getAsString();
            }
        }
        return fallback;
    }

    private static int readInt(JsonObject object, int fallback, String... keys) {
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element != null && element.isJsonPrimitive()) {
                return element.getAsInt();
            }
        }
        return fallback;
    }

    private static float readFloat(JsonObject object, float fallback, String... keys) {
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element != null && element.isJsonPrimitive()) {
                return element.getAsFloat();
            }
        }
        return fallback;
    }

    private static String normalizeTag(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String fileNameWithoutExtension(Path path) {
        String fileName = path.getFileName().toString();
        int extensionIndex = fileName.lastIndexOf('.');
        return extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
    }

    private static void ensureDefaultConfig() {
        Path path = StationStructureFileStorage.structureConfigurationsDirectory().resolve(DEFAULT_CONFIG_FILE);
        if (Files.isRegularFile(path)) {
            return;
        }

        JsonObject root = new JsonObject();
        root.addProperty("id", StationStructureConfig.DEFAULT_ID.toString());
        root.addProperty("pool", "stationarenear:space_station");
        root.addProperty("maxFloors", 2);

        JsonObject rooms = new JsonObject();
        rooms.addProperty("min", 10);
        rooms.addProperty("max", 18);
        root.add("rooms", rooms);

        JsonObject danger = new JsonObject();
        danger.addProperty("min", 0.25F);
        danger.addProperty("max", 0.55F);
        root.add("danger", danger);

        JsonObject requiredRooms = new JsonObject();
        root.add("requiredRooms", requiredRooms);
        root.add("requiredPieces", new JsonObject());
        root.add("requiredPieceTags", new JsonObject());
        root.add("questElementSpawnSkips", new JsonObject());

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            StationAreNear.LOGGER.warn("Failed to write default station structure config {}", path, exception);
        }
    }
}
