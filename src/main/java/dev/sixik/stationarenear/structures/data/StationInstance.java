package dev.sixik.stationarenear.structures.data;

import dev.sixik.stationarenear.structures.util.NbtPos;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public record StationInstance(
        UUID id,
        ResourceLocation pool,
        BlockPos shuttleDoorCenter,
        Direction stationDirection,
        float danger,
        long seed,
        List<PlacedStationPiece> pieces,
        CompoundTag customData
) {

    public StationInstance {
        pieces = List.copyOf(pieces);
        customData = customData.copy();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("pool", pool.toString());
        tag.put("shuttleDoorCenter", NbtPos.save(shuttleDoorCenter));
        tag.putString("stationDirection", stationDirection.getSerializedName());
        tag.putFloat("danger", danger);
        tag.putLong("seed", seed);
        tag.put("customData", customData.copy());

        ListTag pieceTags = new ListTag();
        for (PlacedStationPiece piece : pieces) {
            pieceTags.add(piece.save());
        }
        tag.put("pieces", pieceTags);

        return tag;
    }

    public static StationInstance load(CompoundTag tag) {
        ResourceLocation pool = ResourceLocation.tryParse(tag.getString("pool"));
        Direction stationDirection = Direction.byName(tag.getString("stationDirection"));
        if (pool == null) {
            throw new IllegalArgumentException("Invalid station pool id");
        }
        if (stationDirection == null) {
            stationDirection = Direction.NORTH;
        }

        List<PlacedStationPiece> pieces = new ObjectArrayList<>();
        ListTag pieceTags = tag.getList("pieces", Tag.TAG_COMPOUND);
        for (Tag pieceTag : pieceTags) {
            pieces.add(PlacedStationPiece.load((CompoundTag) pieceTag));
        }

        return new StationInstance(
                tag.getUUID("id"),
                pool,
                NbtPos.load(tag.getCompound("shuttleDoorCenter")),
                stationDirection,
                tag.getFloat("danger"),
                tag.getLong("seed"),
                pieces,
                tag.getCompound("customData")
        );
    }
}
