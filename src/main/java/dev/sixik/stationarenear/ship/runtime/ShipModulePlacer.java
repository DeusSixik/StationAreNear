package dev.sixik.stationarenear.ship.runtime;

import dev.sixik.stationarenear.quest.registry.QuestBlocks;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchor;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorResolver;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorSavedData;
import dev.sixik.stationarenear.structures.data.StationPieceDefinition;
import dev.sixik.stationarenear.structures.data.StationTriggerZone;
import dev.sixik.stationarenear.structures.generation.StationPlacementUtil;
import dev.sixik.stationarenear.structures.util.StationStructureIds;
import dev.sixik.stationarenear.structures.util.TagsConstants;
import dev.sixik.stationarenear.structures.world.StationStructureLibraryData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ShipModulePlacer {

    private static final ResourceLocation SHIP_POOL = StationStructureIds.pool("space_ship");

    private ShipModulePlacer() {
    }

    public enum PlacementResult {
        SUCCESS,
        ALREADY_EXISTS,
        NO_TRIGGERS_FOUND,
        LIMIT_REACHED
    }

    public static PlacementResult installCraftStation(ServerLevel level, BlockPos terminalPos) {
        List<BlockPos> positions = findShipTriggerPositions(level, terminalPos, "craft_station");
        if (positions.isEmpty()) {
            return PlacementResult.NO_TRIGGERS_FOUND;
        }

        for (BlockPos pos : positions) {
            if (level.getBlockState(pos).is(QuestBlocks.WORKBENCH.get())) {
                return PlacementResult.ALREADY_EXISTS;
            }
            if (level.getBlockState(pos).isAir() || level.getBlockState(pos).canBeReplaced()) {
                level.setBlockAndUpdate(pos, QuestBlocks.WORKBENCH.get().defaultBlockState());
                return PlacementResult.SUCCESS;
            }
        }

        level.setBlockAndUpdate(positions.get(0), QuestBlocks.WORKBENCH.get().defaultBlockState());
        return PlacementResult.SUCCESS;
    }

    public static PlacementResult installExtraStorage(ServerLevel level, BlockPos terminalPos) {
        List<BlockPos> positions = findShipTriggerPositions(level, terminalPos, "storage", "extra_storage");
        if (positions.isEmpty()) {
            return PlacementResult.NO_TRIGGERS_FOUND;
        }

        for (BlockPos pos : positions) {
            if (level.getBlockState(pos).isAir() || level.getBlockState(pos).canBeReplaced()) {
                level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
                return PlacementResult.SUCCESS;
            }
        }

        return PlacementResult.LIMIT_REACHED;
    }

    public static int availableStorageSlots(ServerLevel level, BlockPos terminalPos) {
        return findShipTriggerPositions(level, terminalPos, "storage", "extra_storage").size();
    }

    public static List<BlockPos> findShipTriggerPositions(ServerLevel level, BlockPos terminalPos, String... tags) {
        Optional<ShipDockingAnchor> anchorOpt = ShipDockingAnchorSavedData.get(level)
                .anchor(terminalPos)
                .or(() -> ShipDockingAnchorResolver.bindNearbyShip(level, terminalPos));
        if (anchorOpt.isEmpty()) {
            return List.of();
        }

        ShipDockingAnchor anchor = anchorOpt.get();
        BoundingBox shipBounds = anchor.shipBounds();
        StationStructureLibraryData library = StationStructureLibraryData.get(level);
        List<BlockPos> results = new ArrayList<>();

        for (Map.Entry<ResourceLocation, BoundingBox> entry : library.savedTemplateSelections().entrySet()) {
            if (!sameBounds(shipBounds, entry.getValue())) {
                continue;
            }
            for (StationPieceDefinition piece : library.pieces()) {
                if (!isShipPieceSelection(piece, entry.getKey())) {
                    continue;
                }
                BlockPos origin = new BlockPos(shipBounds.minX(), shipBounds.minY(), shipBounds.minZ()).subtract(piece.selectionMin());
                for (StationTriggerZone zone : piece.triggerZones()) {
                    if (hasMatchingTag(zone, tags)) {
                        BoundingBox triggerBounds = StationPlacementUtil.transformBox(origin, zone.min(), zone.max(), Rotation.NONE);
                        results.add(new BlockPos(triggerBounds.minX(), triggerBounds.minY(), triggerBounds.minZ()));
                    }
                }
            }
        }

        if (results.isEmpty()) {
            for (StationPieceDefinition piece : library.pieces()) {
                if (!piece.pool().equals(SHIP_POOL)) {
                    continue;
                }
                BlockPos origin = new BlockPos(shipBounds.minX(), shipBounds.minY(), shipBounds.minZ()).subtract(piece.selectionMin());
                for (StationTriggerZone zone : piece.triggerZones()) {
                    if (hasMatchingTag(zone, tags)) {
                        BoundingBox triggerBounds = StationPlacementUtil.transformBox(origin, zone.min(), zone.max(), Rotation.NONE);
                        results.add(new BlockPos(triggerBounds.minX(), triggerBounds.minY(), triggerBounds.minZ()));
                    }
                }
            }
        }

        return results;
    }

    private static boolean hasMatchingTag(StationTriggerZone zone, String... tags) {
        if (zone.id() != null) {
            for (String tag : tags) {
                if (zone.id().equalsIgnoreCase(tag)) {
                    return true;
                }
            }
        }
        if (zone.data() != null) {
            String tagsStr = zone.data().contains(TagsConstants.Keys.TAGS)
                    ? zone.data().getString(TagsConstants.Keys.TAGS)
                    : (zone.data().contains("tag") ? zone.data().getString("tag") : "");
            for (String part : tagsStr.split("[,; ]+")) {
                for (String tag : tags) {
                    if (part.trim().equalsIgnoreCase(tag)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isShipPieceSelection(StationPieceDefinition piece, ResourceLocation selectionId) {
        return piece.pool().equals(SHIP_POOL) && (piece.template().equals(selectionId) || piece.id().equals(selectionId));
    }

    private static boolean sameBounds(BoundingBox a, BoundingBox b) {
        return a.minX() == b.minX() && a.minY() == b.minY() && a.minZ() == b.minZ()
                && a.maxX() == b.maxX() && a.maxY() == b.maxY() && a.maxZ() == b.maxZ();
    }
}
