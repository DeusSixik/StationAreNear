package dev.sixik.stationarenear.structures.generation;

import dev.sixik.stationarenear.structures.data.PlacedTriggerZone;
import dev.sixik.stationarenear.structures.data.StationConnector;
import dev.sixik.stationarenear.structures.data.StationTriggerZone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
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
                connector.acceptedSizes()
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
        BoundingBox bounds = transformBox(origin, triggerZone.min(), triggerZone.max(), rotation);
        CompoundTag data = triggerZone.data().copy();
        data.putFloat("stationDanger", danger);
        return new PlacedTriggerZone(
                triggerZone.id(),
                triggerZone.type(),
                new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()),
                new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ()),
                data
        );
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
