package dev.sixik.stationarenear.structures.generation;

import dev.sixik.stationarenear.structures.data.PlacedTriggerZone;
import dev.sixik.stationarenear.structures.data.StationConnector;
import dev.sixik.stationarenear.structures.data.StationTriggerZone;
import dev.sixik.stationarenear.structures.trigger.StationStructureTriggerType;
import dev.sixik.stationarenear.structures.util.NbtPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class StationPlacementUtil {

    private StationPlacementUtil() {
    }

    static Rotation rotationBetween(Direction from, Direction to) {
        if (from.getAxis().isVertical() || to.getAxis().isVertical()) {
            return from == to ? Rotation.NONE : null;
        }

        Direction current = from;
        for (Rotation rotation : List.of(Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90)) {
            if (rotation.rotate(current) == to) {
                return rotation;
            }
        }
        return null;
    }

    static BlockPos transform(BlockPos local, Rotation rotation) {
        return StructureTemplate.transform(local, Mirror.NONE, rotation, BlockPos.ZERO);
    }

    public static StationConnector transformConnector(StationConnector connector, BlockPos origin, Rotation rotation) {
        BlockPos worldPosition = origin.offset(transform(connector.position(), rotation));
        Direction worldDirection = rotation.rotate(connector.direction());
        BoundingBox bounds = transformBox(origin, connector.min(), connector.max(), rotation);
        return new StationConnector(
                connector.name(),
                worldPosition,
                worldDirection,
                connector.tags(),
                connector.accepts(),
                connector.priority(),
                new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()),
                new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ()),
                connector.width(),
                connector.height(),
                connector.acceptedSizes(),
                connector.requiresPassage()
        );
    }

    public static BoundingBox transformBounds(BlockPos origin, Vec3i size, Rotation rotation) {
        List<BlockPos> corners = new ArrayList<>(8);
        int maxX = Math.max(0, size.getX() - 1);
        int maxY = Math.max(0, size.getY() - 1);
        int maxZ = Math.max(0, size.getZ() - 1);

        for (int x : new int[]{0, maxX}) {
            for (int y : new int[]{0, maxY}) {
                for (int z : new int[]{0, maxZ}) {
                    corners.add(origin.offset(transform(new BlockPos(x, y, z), rotation)));
                }
            }
        }

        int minWorldX = corners.stream().mapToInt(BlockPos::getX).min().orElse(origin.getX());
        int minWorldY = corners.stream().mapToInt(BlockPos::getY).min().orElse(origin.getY());
        int minWorldZ = corners.stream().mapToInt(BlockPos::getZ).min().orElse(origin.getZ());
        int maxWorldX = corners.stream().mapToInt(BlockPos::getX).max().orElse(origin.getX());
        int maxWorldY = corners.stream().mapToInt(BlockPos::getY).max().orElse(origin.getY());
        int maxWorldZ = corners.stream().mapToInt(BlockPos::getZ).max().orElse(origin.getZ());

        return new BoundingBox(minWorldX, minWorldY, minWorldZ, maxWorldX, maxWorldY, maxWorldZ);
    }

    static PlacedTriggerZone transformTrigger(StationTriggerZone triggerZone, BlockPos origin, Rotation rotation, float danger) {
        return transformTrigger(triggerZone, origin, rotation, danger, null, null);
    }

    static PlacedTriggerZone transformTrigger(StationTriggerZone triggerZone, BlockPos origin, Rotation rotation, float danger, BlockPos selectionMin, BlockPos selectionMax) {
        BoundingBox bounds = transformBox(origin, triggerZone.min(), triggerZone.max(), rotation);
        CompoundTag data = triggerZone.data().copy();
        rotateTriggerDirection(triggerZone, data, rotation, selectionMin, selectionMax);
        rotateShapePoints(triggerZone, origin, data, bounds, rotation);
        data.putFloat("stationDanger", danger);
        return new PlacedTriggerZone(
                triggerZone.id(),
                triggerZone.type(),
                new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()),
                new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ()),
                data
        );
    }

    private static void rotateShapePoints(StationTriggerZone triggerZone, BlockPos origin, CompoundTag data, BoundingBox transformedBounds, Rotation rotation) {
        if (!data.contains("shapePoints", Tag.TAG_LIST)) {
            return;
        }
        ListTag sourcePoints = data.getList("shapePoints", Tag.TAG_COMPOUND);
        ListTag transformedPoints = new ListTag();
        BlockPos transformedMin = new BlockPos(transformedBounds.minX(), transformedBounds.minY(), transformedBounds.minZ());
        for (int i = 0; i < sourcePoints.size(); i++) {
            BlockPos offset = NbtPos.load(sourcePoints.getCompound(i));
            BlockPos localPos = triggerZone.min().offset(offset.getX(), offset.getY(), offset.getZ());
            BlockPos worldPos = origin.offset(transform(localPos, rotation));
            transformedPoints.add(NbtPos.save(worldPos.subtract(transformedMin)));
        }
        data.put("shapePoints", transformedPoints);
        data.putString("shape", "points");
    }

    private static void rotateTriggerDirection(StationTriggerZone triggerZone, CompoundTag data, Rotation rotation, BlockPos selectionMin, BlockPos selectionMax) {
        Direction direction = configuredHorizontalDirection(data);
        if (direction == null && StationStructureTriggerType.from(triggerZone.type()) == StationStructureTriggerType.DOOR_TRIGGER) {
            direction = inferHorizontalFaceDirection(triggerZone.min(), triggerZone.max(), selectionMin, selectionMax);
        }
        if (direction != null && direction.getAxis().isHorizontal()) {
            data.putString("direction", rotation.rotate(direction).getSerializedName());
        }
        rotateHorizontalDirection(data, "shapeDirection", rotation);
    }

    private static Direction configuredHorizontalDirection(CompoundTag data) {
        Direction configured = Direction.byName(data.getString("direction").toLowerCase(java.util.Locale.ROOT));
        return configured != null && configured.getAxis().isHorizontal() ? configured : null;
    }

    private static void rotateHorizontalDirection(CompoundTag data, String key, Rotation rotation) {
        Direction configured = Direction.byName(data.getString(key).toLowerCase(java.util.Locale.ROOT));
        if (configured != null && configured.getAxis().isHorizontal()) {
            data.putString(key, rotation.rotate(configured).getSerializedName());
        }
    }

    private static Direction inferHorizontalFaceDirection(BlockPos min, BlockPos max, BlockPos selectionMin, BlockPos selectionMax) {
        if (selectionMin == null || selectionMax == null) {
            return Direction.NORTH;
        }

        Direction bestDirection = Direction.NORTH;
        int bestDistance = Integer.MAX_VALUE;
        int west = Math.abs(min.getX() - selectionMin.getX());
        if (west < bestDistance) { bestDistance = west; bestDirection = Direction.WEST; }
        int east = Math.abs(selectionMax.getX() - max.getX());
        if (east < bestDistance) { bestDistance = east; bestDirection = Direction.EAST; }
        int north = Math.abs(min.getZ() - selectionMin.getZ());
        if (north < bestDistance) { bestDistance = north; bestDirection = Direction.NORTH; }
        int south = Math.abs(selectionMax.getZ() - max.getZ());
        if (south < bestDistance) { bestDirection = Direction.SOUTH; }
        return bestDirection;
    }

    public static BoundingBox transformBox(BlockPos origin, BlockPos min, BlockPos max, Rotation rotation) {
        BlockPos size = new BlockPos(max.getX() - min.getX() + 1, max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1);
        BoundingBox relative = transformBounds(origin.offset(transform(min, rotation)), size, rotation);
        return new BoundingBox(relative.minX(), relative.minY(), relative.minZ(), relative.maxX(), relative.maxY(), relative.maxZ());
    }

    static boolean intersectsAny(BoundingBox candidate, List<BoundingBox> occupied) {
        for (BoundingBox bounds : occupied) {
            if (candidate.intersects(bounds)) {
                return true;
            }
        }
        return false;
    }

    public static Set<String> parseTags(String tags) {
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>();
        for (String tag : tags.split(",")) {
            String value = tag.trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }
}
