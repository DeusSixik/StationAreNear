package dev.sixik.stationarenear.terminal.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.sixik.stationarenear.StationAreNear;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ShopCatalog {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path DIR = FMLPaths.CONFIGDIR.get().resolve(StationAreNear.MODID).resolve("Shop");
    private static final Path CONFIG_FILE = DIR.resolve("shop_catalog.json");

    private static final String DEFAULT_JSON = """
            {
              "items": [
                { "id": "minecraft:iron_ingot",   "price": 5.0  },
                { "id": "minecraft:gold_ingot",   "price": 15.0 },
                { "id": "minecraft:diamond",       "price": 50.0 },
                { "id": "minecraft:emerald",       "price": 30.0 },
                { "id": "minecraft:coal",          "price": 2.0  },
                { "id": "minecraft:redstone",      "price": 3.0  },
                { "id": "minecraft:lapis_lazuli",  "price": 4.0  }
              ]
            }
            """;

    public static volatile List<ShopItemInfo> ENTRIES = List.of();

    private ShopCatalog() {
    }

    public static synchronized void init() {
        reload();
    }

    public static synchronized int reload() {
        ensureDefaults();
        List<ShopItemInfo> parsed = readEntries();
        ENTRIES = Collections.unmodifiableList(parsed);
        StationAreNear.LOGGER.info("[ShopCatalog] Loaded {} shop items from config.", ENTRIES.size());
        return ENTRIES.size();
    }

    private static List<ShopItemInfo> readEntries() {
        List<ShopItemInfo> result = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            JsonArray items = root.getAsJsonArray("items");
            int index = 0;
            for (JsonElement el : items) {
                JsonObject obj = el.getAsJsonObject();
                String id = obj.get("id").getAsString().trim();
                double price = obj.get("price").getAsDouble();
                if (!id.isBlank() && price >= 0) {
                    result.add(new ShopItemInfo(index, id, price));
                    index++;
                }
            }
        } catch (Exception ex) {
            StationAreNear.LOGGER.warn("[ShopCatalog] Failed to load shop_catalog.json from config folder.", ex);
        }
        return result;
    }

    private static void ensureDefaults() {
        try {
            Files.createDirectories(DIR);
            if (!Files.isRegularFile(CONFIG_FILE)) {
                Files.writeString(CONFIG_FILE, DEFAULT_JSON, StandardCharsets.UTF_8);
            }
        } catch (Exception ex) {
            StationAreNear.LOGGER.warn("[ShopCatalog] Failed to write default shop_catalog.json.", ex);
        }
    }
}
