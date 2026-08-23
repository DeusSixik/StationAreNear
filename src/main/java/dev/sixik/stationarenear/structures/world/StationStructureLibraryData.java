package dev.sixik.stationarenear.structures.world;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.structures.data.StationPieceDefinition;
import dev.sixik.stationarenear.structures.data.StationPoolDefinition;
import dev.sixik.stationarenear.structures.data.TemplateSelectionEntry;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class StationStructureLibraryData extends SavedData {

    private static final String DATA_NAME = StationAreNear.MODID + "_structure_library";

    private final Map<ResourceLocation, StationPieceDefinition> pieces = new Object2ObjectLinkedOpenHashMap<>();
    private final Map<ResourceLocation, StationPoolDefinition> pools = new Object2ObjectLinkedOpenHashMap<>();
    private final Map<ResourceLocation, BoundingBox> savedTemplateSelections = new Object2ObjectLinkedOpenHashMap<>();

    public static StationStructureLibraryData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(StationStructureLibraryData::load, StationStructureLibraryData::new, DATA_NAME);
    }

    public Optional<StationPieceDefinition> piece(ResourceLocation id) {
        return Optional.ofNullable(pieces.get(id));
    }

    public Optional<StationPoolDefinition> pool(ResourceLocation id) {
        return Optional.ofNullable(pools.get(id));
    }

    public Collection<StationPieceDefinition> pieces() {
        return pieces.values();
    }

    public Collection<StationPoolDefinition> pools() {
        return pools.values();
    }

    public Map<ResourceLocation, BoundingBox> savedTemplateSelections() {
        return java.util.Collections.unmodifiableMap(savedTemplateSelections);
    }

    public void upsertTemplateSelection(ResourceLocation template, BoundingBox bounds) {
        savedTemplateSelections.put(template, bounds);
        setDirty();
    }

    public boolean removeTemplate(ResourceLocation template) {
        boolean changed = savedTemplateSelections.remove(template) != null;
        java.util.List<ResourceLocation> removedPieces = new ObjectArrayList<>();
        pieces.entrySet().removeIf(entry -> {
            boolean remove = entry.getKey().equals(template) || entry.getValue().template().equals(template);
            if (remove) {
                removedPieces.add(entry.getKey());
            }
            return remove;
        });
        changed |= !removedPieces.isEmpty();
        if (!removedPieces.isEmpty()) {
            for (Map.Entry<ResourceLocation, StationPoolDefinition> entry : new ObjectArrayList<>(pools.entrySet())) {
                StationPoolDefinition pool = entry.getValue();
                for (ResourceLocation piece : removedPieces) {
                    pool = pool.withoutPiece(piece);
                }
                pools.put(entry.getKey(), pool);
            }
        }
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public void upsertPiece(StationPieceDefinition definition, boolean startPiece) {
        pieces.put(definition.id(), definition);
        StationPoolDefinition pool = pools.getOrDefault(
                definition.pool(),
                new StationPoolDefinition(definition.pool(), ObjectLists.emptyList(), ObjectLists.emptyList(), 4, 10)
        );
        pools.put(definition.pool(), pool.withPiece(definition.id(), startPiece));
        setDirty();
    }

    public void upsertPool(StationPoolDefinition pool) {
        pools.put(pool.id(), pool);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag pieceTags = new ListTag();
        for (StationPieceDefinition piece : pieces.values()) {
            pieceTags.add(piece.save());
        }
        tag.put("pieces", pieceTags);

        ListTag poolTags = new ListTag();
        for (StationPoolDefinition pool : pools.values()) {
            poolTags.add(pool.save());
        }
        tag.put("pools", poolTags);

        ListTag selectionTags = new ListTag();
        for (Map.Entry<ResourceLocation, BoundingBox> entry : savedTemplateSelections.entrySet()) {
            CompoundTag selectionTag = new CompoundTag();
            selectionTag.putString("template", entry.getKey().toString());
            selectionTag.put("bounds", TemplateSelectionEntry.saveBounds(entry.getValue()));
            selectionTags.add(selectionTag);
        }
        tag.put("templateSelections", selectionTags);

        return tag;
    }

    private static StationStructureLibraryData load(CompoundTag tag) {
        StationStructureLibraryData data = new StationStructureLibraryData();

        ListTag pieceTags = tag.getList("pieces", Tag.TAG_COMPOUND);
        for (Tag pieceTag : pieceTags) {
            try {
                StationPieceDefinition piece = StationPieceDefinition.load((CompoundTag) pieceTag);
                data.pieces.put(piece.id(), piece);
            } catch (RuntimeException exception) {
                StationAreNear.LOGGER.warn("Skipped broken station structure piece", exception);
            }
        }

        ListTag poolTags = tag.getList("pools", Tag.TAG_COMPOUND);
        for (Tag poolTag : poolTags) {
            try {
                StationPoolDefinition pool = StationPoolDefinition.load((CompoundTag) poolTag);
                data.pools.put(pool.id(), pool);
            } catch (RuntimeException exception) {
                StationAreNear.LOGGER.warn("Skipped broken station structure pool", exception);
            }
        }

        ListTag selectionTags = tag.getList("templateSelections", Tag.TAG_COMPOUND);
        for (Tag selectionTag : selectionTags) {
            try {
                CompoundTag selectionCompound = (CompoundTag) selectionTag;
                ResourceLocation template = ResourceLocation.tryParse(selectionCompound.getString("template"));
                if (template != null && selectionCompound.contains("bounds", Tag.TAG_COMPOUND)) {
                    data.savedTemplateSelections.put(template, TemplateSelectionEntry.loadBounds(selectionCompound.getCompound("bounds")));
                }
            } catch (RuntimeException exception) {
                StationAreNear.LOGGER.warn("Skipped broken station template selection", exception);
            }
        }

        return data;
    }
}
