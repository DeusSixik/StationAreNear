package dev.sixik.stationarenear.structures.data;

import dev.sixik.stationarenear.structures.util.NbtPos;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record StationPieceDefinition(
        ResourceLocation id,
        ResourceLocation template,
        ResourceLocation pool,
        List<StationConnector> connectors,
        List<StationTriggerZone> triggerZones,
        BlockPos selectionMin,
        BlockPos selectionMax,
        int floorSpan,
        int weight,
        float minDanger,
        float maxDanger
) {

    public StationPieceDefinition {
        connectors = List.copyOf(connectors);
        triggerZones = List.copyOf(triggerZones);
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
        tag.putInt("weight", weight);
        tag.put("selectionMin", NbtPos.save(selectionMin));
        tag.put("selectionMax", NbtPos.save(selectionMax));
        tag.putInt("floorSpan", Math.max(1, floorSpan));
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

        BlockPos selectionMin = tag.contains("selectionMin") ? NbtPos.load(tag.getCompound("selectionMin")) : BlockPos.ZERO;
        BlockPos selectionMax = tag.contains("selectionMax") ? NbtPos.load(tag.getCompound("selectionMax")) : BlockPos.ZERO;
        int detectedFloorSpan = Math.max(1, (selectionMax.getY() - selectionMin.getY() + 16) / 16);
        return new StationPieceDefinition(
                id,
                template,
                pool,
                connectors,
                triggerZones,
                selectionMin,
                selectionMax,
                tag.contains("floorSpan") ? Math.max(1, tag.getInt("floorSpan")) : detectedFloorSpan,
                Math.max(1, tag.getInt("weight")),
                tag.contains("minDanger") ? tag.getFloat("minDanger") : 0.0F,
                tag.contains("maxDanger") ? tag.getFloat("maxDanger") : 1.0F
        );
    }
}
