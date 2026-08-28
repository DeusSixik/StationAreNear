package dev.sixik.stationarenear.structures.data;

import dev.sixik.stationarenear.structures.util.NbtPos;
import dev.sixik.stationarenear.structures.util.TagsConstants;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record StationPieceDefinition(
        ResourceLocation id,
        ResourceLocation template,
        ResourceLocation pool,
        List<StationConnector> connectors,
        List<StationTriggerZone> triggerZones,
        Set<String> tags,
        BlockPos selectionMin,
        BlockPos selectionMax,
        int floorSpan,
        int weight,
        Direction exteriorSide,
        float minDanger,
        float maxDanger
) {

    public StationPieceDefinition {
        connectors = List.copyOf(connectors);
        triggerZones = List.copyOf(triggerZones);
        tags = normalizeTags(tags);
        floorSpan = Math.max(1, floorSpan);
        weight = Math.max(1, weight);
    }

    public boolean canSpawnAtDanger(float danger) {
        return danger >= minDanger && danger <= maxDanger;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id.toString());
        tag.putString("template", template.toString());
        tag.putString("pool", pool.toString());
        tag.put(TagsConstants.Keys.TAGS, saveTags(tags));
        tag.putInt("weight", weight);
        tag.put("selectionMin", NbtPos.save(selectionMin));
        tag.put("selectionMax", NbtPos.save(selectionMax));
        tag.putInt("floorSpan", Math.max(1, floorSpan));
        if (exteriorSide != null) {
            tag.putString("exteriorSide", exteriorSide.getSerializedName());
        }
        tag.putFloat("minDanger", minDanger);
        tag.putFloat("maxDanger", maxDanger);

        ListTag connectorTags = new ListTag();
        for (StationConnector connector : connectors) {
            connectorTags.add(connector.save());
        }
        tag.put("connectors", connectorTags);

        ListTag triggerTags = new ListTag();
        for (StationTriggerZone triggerZone : triggerZones) {
            triggerTags.add(triggerZone.save());
        }
        tag.put("triggerZones", triggerTags);

        return tag;
    }

    public static StationPieceDefinition load(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
        ResourceLocation template = ResourceLocation.tryParse(tag.getString("template"));
        ResourceLocation pool = ResourceLocation.tryParse(tag.getString("pool"));
        if (id == null || template == null || pool == null) {
            throw new IllegalArgumentException("Invalid station piece resource location");
        }

        List<StationConnector> connectors = new ObjectArrayList<>();
        ListTag connectorTags = tag.getList("connectors", Tag.TAG_COMPOUND);
        for (Tag connectorTag : connectorTags) {
            connectors.add(StationConnector.load((CompoundTag) connectorTag));
        }

        List<StationTriggerZone> triggerZones = new ObjectArrayList<>();
        ListTag triggerTags = tag.getList("triggerZones", Tag.TAG_COMPOUND);
        for (Tag triggerTag : triggerTags) {
            triggerZones.add(StationTriggerZone.load((CompoundTag) triggerTag));
        }

        Set<String> tags = tag.contains(TagsConstants.Keys.TAGS, Tag.TAG_LIST) ? loadTags(tag.getList(TagsConstants.Keys.TAGS, Tag.TAG_STRING)) : Set.of();
        BlockPos selectionMin = tag.contains("selectionMin") ? NbtPos.load(tag.getCompound("selectionMin")) : BlockPos.ZERO;
        BlockPos selectionMax = tag.contains("selectionMax") ? NbtPos.load(tag.getCompound("selectionMax")) : BlockPos.ZERO;
        int detectedFloorSpan = Math.max(1, (selectionMax.getY() - selectionMin.getY() + 16) / 16);
        return new StationPieceDefinition(
                id,
                template,
                pool,
                connectors,
                triggerZones,
                tags,
                selectionMin,
                selectionMax,
                tag.contains("floorSpan") ? Math.max(1, tag.getInt("floorSpan")) : detectedFloorSpan,
                Math.max(1, tag.getInt("weight")),
                tag.contains("exteriorSide") ? Direction.byName(tag.getString("exteriorSide")) : null,
                tag.contains("minDanger") ? tag.getFloat("minDanger") : 0.0F,
                tag.contains("maxDanger") ? tag.getFloat("maxDanger") : 1.0F
        );
    }
    private static Set<String> normalizeTags(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Set.of();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            normalized.add(tag.trim().toLowerCase(java.util.Locale.ROOT));
        }
        return Set.copyOf(normalized);
    }

    private static ListTag saveTags(Set<String> tags) {
        ListTag list = new ListTag();
        for (String tag : tags) {
            list.add(StringTag.valueOf(tag));
        }
        return list;
    }

    private static Set<String> loadTags(ListTag list) {
        Set<String> tags = new LinkedHashSet<>();
        for (Tag tag : list) {
            if (tag instanceof StringTag stringTag) {
                String value = stringTag.getAsString();
                if (!value.isBlank()) {
                    tags.add(value.trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        return tags;
    }

}
