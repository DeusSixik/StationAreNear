package dev.sixik.stationarenear.structures.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sixik.stationarenear.StationAreNear;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import dev.sixik.stationarenear.structures.data.StationPieceDefinition;
import dev.sixik.stationarenear.structures.world.StationStructureLibraryData;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public final class StationStructureFileStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String STRUCTURE_CONFIGURATION_FOLDER = "Structure Configuration";
    private static final String STRUCTURE_FOLDER = "Structures";

    private static final Path ROOT = FMLPaths.CONFIGDIR.get().resolve(StationAreNear.MODID);
    private static final Path STRUCTURE_CONFIGURATIONS = ROOT.resolve(STRUCTURE_CONFIGURATION_FOLDER);
    private static final Path STRUCTURES = ROOT.resolve(STRUCTURE_FOLDER);

    private StationStructureFileStorage() {
    }

    public static Path root() {
        return ROOT;
    }

    public static Path structureConfigurationsDirectory() {
        return STRUCTURE_CONFIGURATIONS;
    }

    public static Path structuresDirectory() {
        return STRUCTURES;
    }

    public static void ensureDirectories() {
        try {
            Files.createDirectories(STRUCTURE_CONFIGURATIONS);
            Files.createDirectories(STRUCTURES);
        } catch (IOException exception) {
            StationAreNear.LOGGER.error("Failed to create StationAreNear structure config folders", exception);
        }
    }

    public static boolean templateExists(ResourceLocation templateId) {
        return Files.isRegularFile(templatePath(templateId));
    }

    public static void saveTemplate(ServerLevel level, ResourceLocation templateId, StructureTemplate template) throws IOException {
        ensureDirectories();
        Path path = templatePath(templateId);
        Files.createDirectories(path.getParent());
        NbtIo.writeCompressed(template.save(new CompoundTag()), path.toFile());
        loadTemplate(level, templateId, path);
        deleteGeneratedWorldCache(level, templateId);
    }

    public static boolean deleteTemplate(ResourceLocation templateId) {
        boolean deleted = false;
        try {
            deleted = Files.deleteIfExists(templatePath(templateId));
            deleted = Files.deleteIfExists(templateMetadataPath(templateId)) || deleted;
        } catch (IOException exception) {
            StationAreNear.LOGGER.warn("Failed to delete external station template {}", templateId, exception);
            return deleted;
        }
        return deleted;
    }

    public static void saveDefinition(StationPieceDefinition definition, boolean startPiece) throws IOException {
        ensureDirectories();
        Path path = templateMetadataPath(definition.template());
        Files.createDirectories(path.getParent());

        JsonObject root = new JsonObject();
        root.addProperty("startPiece", startPiece);
        root.addProperty("definition", definition.save().toString());
        Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
    }

    public static int loadExternalDefinitions(StationStructureLibraryData library) {
        ensureDirectories();
        if (!Files.isDirectory(STRUCTURES)) {
            return 0;
        }

        AtomicInteger loaded = new AtomicInteger();
        try (Stream<Path> paths = Files.walk(STRUCTURES)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> loadExternalDefinition(path, library, loaded));
        } catch (IOException exception) {
            StationAreNear.LOGGER.warn("Failed to scan external station definitions folder {}", STRUCTURES, exception);
        }
        return loaded.get();
    }

    public static int loadExternalStructures(ServerLevel level) {
        ensureDirectories();
        if (!Files.isDirectory(STRUCTURES)) {
            return 0;
        }

        AtomicInteger loaded = new AtomicInteger();
        try (Stream<Path> paths = Files.walk(STRUCTURES)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".nbt"))
                    .forEach(path -> externalTemplateId(path).ifPresent(templateId -> {
                        try {
                            loadTemplate(level, templateId, path);
                            loaded.incrementAndGet();
                        } catch (IOException | RuntimeException exception) {
                            StationAreNear.LOGGER.warn("Failed to load external station template {} from {}", templateId, path, exception);
                        }
                    }));
        } catch (IOException exception) {
            StationAreNear.LOGGER.warn("Failed to scan external station templates folder {}", STRUCTURES, exception);
        }
        return loaded.get();
    }

    private static void loadTemplate(ServerLevel level, ResourceLocation templateId, Path path) throws IOException {
        CompoundTag tag = NbtIo.readCompressed(path.toFile());
        StructureTemplate template = level.getStructureManager().getOrCreate(templateId);
        template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    private static void loadExternalDefinition(Path path, StationStructureLibraryData library, AtomicInteger loaded) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (!rootElement.isJsonObject()) {
                return;
            }
            JsonObject root = rootElement.getAsJsonObject();
            JsonElement definitionElement = root.get("definition");
            if (definitionElement == null || !definitionElement.isJsonPrimitive()) {
                return;
            }

            StationPieceDefinition definition = StationPieceDefinition.load(TagParser.parseTag(definitionElement.getAsString()));
            boolean startPiece = root.has("startPiece") && root.get("startPiece").getAsBoolean();
            library.loadExternalPiece(definition, startPiece);
            loaded.incrementAndGet();
        } catch (Exception exception) {
            StationAreNear.LOGGER.warn("Failed to load external station definition from {}", path, exception);
        }
    }

    private static void deleteGeneratedWorldCache(ServerLevel level, ResourceLocation templateId) {
        try {
            Files.deleteIfExists(level.getStructureManager().getPathToGeneratedStructure(templateId, ".nbt"));
        } catch (IOException | RuntimeException exception) {
            StationAreNear.LOGGER.warn("Failed to delete generated world cache for station template {}", templateId, exception);
        }
    }

    private static Path templatePath(ResourceLocation templateId) {
        return STRUCTURES.resolve(templateId.getNamespace()).resolve(templateId.getPath() + ".nbt");
    }

    private static Path templateMetadataPath(ResourceLocation templateId) {
        return STRUCTURES.resolve(templateId.getNamespace()).resolve(templateId.getPath() + ".json");
    }

    private static Optional<ResourceLocation> externalTemplateId(Path path) {
        Path relativePath = STRUCTURES.relativize(path);
        String normalized = relativePath.toString().replace('\\', '/');
        if (!normalized.endsWith(".nbt")) {
            return Optional.empty();
        }
        normalized = normalized.substring(0, normalized.length() - ".nbt".length());

        String namespace = StationAreNear.MODID;
        String templatePath = normalized;
        String namespacePrefix = StationAreNear.MODID + "/";
        if (normalized.startsWith(namespacePrefix)) {
            templatePath = normalized.substring(namespacePrefix.length());
        }

        ResourceLocation id = ResourceLocation.tryParse(namespace + ":" + templatePath);
        return Optional.ofNullable(id);
    }
}
