package dev.sixik.stationarenear.ship.docking;

import dev.sixik.stationarenear.structures.util.NbtPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record ShipDockingAnchor(
        BlockPos terminalPos,
        BoundingBox shipBounds,
        BlockPos anchorPos,
        Direction direction,
        String connectionName,
        int width,
        int height,
        String tags,
        String accepts
) {

    public ShipDockingAnchor {
        direction = direction == null || direction.getAxis().isVertical() ? Direction.NORTH : direction;
        connectionName = connectionName == null || connectionName.isBlank() ? "main" : connectionName.trim();
        width = Math.max(1, width);
        height = Math.max(1, height);
        tags = tags == null ? "" : tags.trim();
        accepts = accepts == null ? "" : accepts.trim();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("terminalPos", NbtPos.save(terminalPos));
        tag.put("anchorPos", NbtPos.save(anchorPos));
        tag.putString("direction", direction.getSerializedName());
        tag.putString("connectionName", connectionName);
        tag.putInt("width", width);
        tag.putInt("height", height);
        tag.putString("tags", tags);
        tag.putString("accepts", accepts);

        CompoundTag bounds = new CompoundTag();
        bounds.putInt("minX", shipBounds.minX());
        bounds.putInt("minY", shipBounds.minY());
        bounds.putInt("minZ", shipBounds.minZ());
        bounds.putInt("maxX", shipBounds.maxX());
        bounds.putInt("maxY", shipBounds.maxY());
        bounds.putInt("maxZ", shipBounds.maxZ());
        tag.put("shipBounds", bounds);
        return tag;
    }

    public static ShipDockingAnchor load(CompoundTag tag) {
        Direction direction = Direction.byName(tag.getString("direction"));
        if (direction == null || direction.getAxis().isVertical()) {
            direction = Direction.NORTH;
        }

        BoundingBox bounds = loadBounds(tag.getCompound("shipBounds"));
        return new ShipDockingAnchor(
                NbtPos.load(tag.getCompound("terminalPos")),
                bounds,
                NbtPos.load(tag.getCompound("anchorPos")),
                direction,
                tag.getString("connectionName"),
                tag.contains("width") ? tag.getInt("width") : 1,
                tag.contains("height") ? tag.getInt("height") : 1,
                tag.getString("tags"),
                tag.getString("accepts")
        );
    }

    private static BoundingBox loadBounds(CompoundTag tag) {
        if (tag.isEmpty() || !tag.contains("minX", Tag.TAG_INT)) {
            return new BoundingBox(0, 0, 0, 0, 0, 0);
        }
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
