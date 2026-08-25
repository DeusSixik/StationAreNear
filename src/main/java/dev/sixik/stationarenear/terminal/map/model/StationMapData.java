package dev.sixik.stationarenear.terminal.map.model;

import java.util.List;

/**
 * UI-agnostic station map model with no Minecraft, UniGUI, or packet dependencies.
 * This DTO can be copied into another project and rendered by any UI.
 */
public record StationMapData(
        String stationId,
        String stationCode,
        String poolId,
        float danger,
        long seed,
        Point3i dockWorld,
        int minFloor,
        int maxFloor,
        List<Room> rooms
) {

    public StationMapData {
        stationId = blankFallback(stationId, "unknown");
        stationCode = blankFallback(stationCode, stationId);
        poolId = blankFallback(poolId, "unknown");
        dockWorld = dockWorld == null ? Point3i.ZERO : dockWorld;
        rooms = List.copyOf(rooms == null ? List.of() : rooms);
    }

    /**
     * Station room/piece. local* coordinates are relative to dockWorld.
     */
    public record Room(
            String id,
            String templateId,
            int minFloor,
            int maxFloor,
            Box3i worldBounds,
            Box3i worldSelectionBounds,
            Box3i localSelectionBounds,
            boolean dockRoom,
            List<Passage> passages
    ) {

        public Room {
            id = blankFallback(id, "unknown");
            templateId = blankFallback(templateId, "unknown");
            worldBounds = worldBounds == null ? Box3i.ZERO : worldBounds;
            worldSelectionBounds = worldSelectionBounds == null ? Box3i.ZERO : worldSelectionBounds;
            localSelectionBounds = localSelectionBounds == null ? Box3i.ZERO : localSelectionBounds;
            if (maxFloor < minFloor) {
                int swap = maxFloor;
                maxFloor = minFloor;
                minFloor = swap;
            }
            passages = List.copyOf(passages == null ? List.of() : passages);
        }
    }

    /**
     * Connected passage between rooms. Open/dead-end connections are not included.
     */
    public record Passage(
            String id,
            int floor,
            Point3i worldPosition,
            Point3i localPosition,
            String direction,
            Box3i worldBounds,
            Box3i localBounds,
            int width,
            int height,
            String acceptedSizes
    ) {

        public Passage {
            id = blankFallback(id, "connection");
            worldPosition = worldPosition == null ? Point3i.ZERO : worldPosition;
            localPosition = localPosition == null ? Point3i.ZERO : localPosition;
            direction = blankFallback(direction, "north");
            worldBounds = worldBounds == null ? Box3i.ZERO : worldBounds;
            localBounds = localBounds == null ? Box3i.ZERO : localBounds;
            width = Math.max(1, width);
            height = Math.max(1, height);
            acceptedSizes = blankFallback(acceptedSizes, width + "x" + height);
        }
    }

    public record Box3i(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public static final Box3i ZERO = new Box3i(0, 0, 0, 0, 0, 0);

        public int widthX() {
            return maxX - minX + 1;
        }

        public int heightY() {
            return maxY - minY + 1;
        }

        public int depthZ() {
            return maxZ - minZ + 1;
        }

        public int centerX() {
            return Math.floorDiv(minX + maxX, 2);
        }

        public int centerY() {
            return Math.floorDiv(minY + maxY, 2);
        }

        public int centerZ() {
            return Math.floorDiv(minZ + maxZ, 2);
        }
    }

    public record Point3i(int x, int y, int z) {
        public static final Point3i ZERO = new Point3i(0, 0, 0);
    }

    private static String blankFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
