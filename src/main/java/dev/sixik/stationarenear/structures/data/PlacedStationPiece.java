package dev.sixik.stationarenear.structures.data;

import dev.sixik.stationarenear.structures.util.NbtPos;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;

public record PlacedStationPiece(
        ResourceLocation definitionId,
        ResourceLocation template,
        BlockPos origin,
        Rotation rotation,
        BoundingBox bounds,
        BoundingBox selectionBounds,
        List<StationConnector> openConnectors,
        List<PlacedTriggerZone> triggerZones
) {

    public PlacedStationPiece {
        openConnectors = List.copyOf(openConnectors);
        triggerZones = List.copyOf(triggerZones);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("definitionId", definitionId.toString());
        tag.putString("template", template.toString());
        tag.put("origin", NbtPos.save(origin));
        tag.putString("rotation", rotation.name());
        tag.put("bounds", saveBounds(bounds));
        tag.put("selectionBounds", saveBounds(selectionBounds));

        ListTag connectorTags = new ListTag();
        for (StationConnector connector : openConnectors) {
            connectorTags.add(connector.save());
        }
        tag.put("openConnectors", connectorTags);

        ListTag triggerTags = new ListTag();
        for (PlacedTriggerZone triggerZone : triggerZones) {
            triggerTags.add(triggerZone.save());
        }
        tag.put("triggerZones", triggerTags);

        return tag;
    }

    public static PlacedStationPiece load(CompoundTag tag) {
        ResourceLocation definitionId = ResourceLocation.tryParse(tag.getString("definitionId"));
        ResourceLocation template = ResourceLocation.tryParse(tag.getString("template"));
        if (definitionId == null || template == null) {
            throw new IllegalArgumentException("Invalid placed station piece resource location");
        }

        List<StationConnector> connectors = new ObjectArrayList<>();
        ListTag connectorTags = tag.getList("openConnectors", Tag.TAG_COMPOUND);
        for (Tag connectorTag : connectorTags) {
            connectors.add(StationConnector.load((CompoundTag) connectorTag));
        }

        List<PlacedTriggerZone> triggerZones = new ObjectArrayList<>();
        ListTag triggerTags = tag.getList("triggerZones", Tag.TAG_COMPOUND);
        for (Tag triggerTag : triggerTags) {
            triggerZones.add(PlacedTriggerZone.load((CompoundTag) triggerTag));
        }

        return new PlacedStationPiece(
                definitionId,
                template,
                NbtPos.load(tag.getCompound("origin")),
                Rotation.valueOf(tag.getString("rotation")),
                loadBounds(tag.getCompound("bounds")),
                tag.contains("selectionBounds") ? loadBounds(tag.getCompound("selectionBounds")) : loadBounds(tag.getCompound("bounds")),
                connectors,
                triggerZones
        );
    }

    private static CompoundTag saveBounds(BoundingBox bounds) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("minX", bounds.minX());
        tag.putInt("minY", bounds.minY());
        tag.putInt("minZ", bounds.minZ());
        tag.putInt("maxX", bounds.maxX());
        tag.putInt("maxY", bounds.maxY());
        tag.putInt("maxZ", bounds.maxZ());
        return tag;
    }

    private static BoundingBox loadBounds(CompoundTag tag) {
        return new BoundingBox(
                tag.getInt("minX"),
                tag.getInt("minY"),
                tag.getInt("minZ"),
                tag.getInt("maxX"),
                tag.getInt("maxY"),
                tag.getInt("maxZ")
        );
    }
}
