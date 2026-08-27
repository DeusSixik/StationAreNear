package dev.sixik.stationarenear.structures.trigger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

import java.util.Locale;

public final class StationTriggerZoneShape {

    private StationTriggerZoneShape() {
    }

    public static boolean hasShape(CompoundTag data) {
        if (hasPointShape(data)) {
            return true;
        }
        String shape = shape(data);
        return !shape.equals("box") && !shape.equals("rect") && !shape.equals("rectangle");
    }

    public static boolean contains(CompoundTag data, BlockPos min, BlockPos max, BlockPos pos) {
        if (!inside(min, max, pos)) {
            return false;
        }
        String shape = shape(data);
        if (shape.equals("points") || shape.equals("mask") || hasPointShape(data)) {
            return containsPoint(data, min, pos);
        }
        if (shape.equals("box") || shape.equals("rect") || shape.equals("rectangle")) {
            return true;
        }

        int width = max.getX() - min.getX() + 1;
        int depth = max.getZ() - min.getZ() + 1;
        int localX = pos.getX() - min.getX();
        int localZ = pos.getZ() - min.getZ();
        int thickness = Mth.clamp(data.contains("shapeThickness", Tag.TAG_INT) ? data.getInt("shapeThickness") : 1, 1, Math.max(width, depth));
        Direction direction = shapeDirection(data);

        return switch (shape) {
            case "t", "tee" -> containsT(localX, localZ, width, depth, thickness, direction);
            case "l", "corner" -> containsL(localX, localZ, width, depth, thickness, direction, data.getBoolean("shapeMirror"));
            default -> true;
        };
    }

    public static boolean containsBox(CompoundTag data, BlockPos shapeMin, BlockPos shapeMax, BlockPos boxMin, BlockPos boxMax) {
        if (!hasShape(data)) {
            return boxMin.getX() >= shapeMin.getX()
                    && boxMin.getY() >= shapeMin.getY()
                    && boxMin.getZ() >= shapeMin.getZ()
                    && boxMax.getX() <= shapeMax.getX()
                    && boxMax.getY() <= shapeMax.getY()
                    && boxMax.getZ() <= shapeMax.getZ();
        }

        for (int x = boxMin.getX(); x <= boxMax.getX(); x++) {
            for (int y = boxMin.getY(); y <= boxMax.getY(); y++) {
                for (int z = boxMin.getZ(); z <= boxMax.getZ(); z++) {
                    if (!contains(data, shapeMin, shapeMax, new BlockPos(x, y, z))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean containsPoint(CompoundTag data, BlockPos min, BlockPos pos) {
        int localX = pos.getX() - min.getX();
        int localY = pos.getY() - min.getY();
        int localZ = pos.getZ() - min.getZ();
        ListTag points = data.getList("shapePoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < points.size(); i++) {
            CompoundTag point = points.getCompound(i);
            if (point.getInt("x") == localX && point.getInt("y") == localY && point.getInt("z") == localZ) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPointShape(CompoundTag data) {
        return data.contains("shapePoints", Tag.TAG_LIST) && !data.getList("shapePoints", Tag.TAG_COMPOUND).isEmpty();
    }

    private static boolean containsT(int x, int z, int width, int depth, int thickness, Direction direction) {
        int stemStartX = Math.max(0, (width - thickness) / 2);
        int stemEndX = Math.min(width - 1, stemStartX + thickness - 1);
        int stemStartZ = Math.max(0, (depth - thickness) / 2);
        int stemEndZ = Math.min(depth - 1, stemStartZ + thickness - 1);
        return switch (direction) {
            case SOUTH -> z >= depth - thickness || (x >= stemStartX && x <= stemEndX);
            case EAST -> x >= width - thickness || (z >= stemStartZ && z <= stemEndZ);
            case WEST -> x < thickness || (z >= stemStartZ && z <= stemEndZ);
            default -> z < thickness || (x >= stemStartX && x <= stemEndX);
        };
    }

    private static boolean containsL(int x, int z, int width, int depth, int thickness, Direction direction, boolean mirror) {
        return switch (direction) {
            case SOUTH -> z >= depth - thickness || (mirror ? x < thickness : x >= width - thickness);
            case EAST -> x >= width - thickness || (mirror ? z >= depth - thickness : z < thickness);
            case WEST -> x < thickness || (mirror ? z < thickness : z >= depth - thickness);
            default -> z < thickness || (mirror ? x >= width - thickness : x < thickness);
        };
    }

    private static boolean inside(BlockPos min, BlockPos max, BlockPos pos) {
        return pos.getX() >= min.getX()
                && pos.getY() >= min.getY()
                && pos.getZ() >= min.getZ()
                && pos.getX() <= max.getX()
                && pos.getY() <= max.getY()
                && pos.getZ() <= max.getZ();
    }

    private static String shape(CompoundTag data) {
        if (!data.contains("shape", Tag.TAG_STRING)) {
            return "box";
        }
        return data.getString("shape").trim().toLowerCase(Locale.ROOT);
    }

    private static Direction shapeDirection(CompoundTag data) {
        Direction direction = Direction.byName(data.getString("shapeDirection").toLowerCase(Locale.ROOT));
        return direction != null && direction.getAxis().isHorizontal() ? direction : Direction.NORTH;
    }
}
