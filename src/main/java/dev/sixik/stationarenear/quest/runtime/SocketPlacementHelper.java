package dev.sixik.stationarenear.quest.runtime;

import dev.sixik.stationarenear.structures.data.PlacedStationPiece;
import dev.sixik.stationarenear.structures.data.PlacedTriggerZone;
import dev.sixik.stationarenear.structures.data.StationInstance;
import dev.sixik.stationarenear.structures.world.StationSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;
import java.util.Map;

public final class SocketPlacementHelper {

    private SocketPlacementHelper() {
    }

    public static boolean isNearSocket(Level level, BlockPos pos) {
        if (pos == null || level == null) {
            return false;
        }

        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos checkPos = pos.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(checkPos);
                    if (isSocketBlock(state)) {
                        return true;
                    }
                }
            }
        }

        if (level instanceof ServerLevel serverLevel) {
            for (StationInstance station : StationSavedData.get(serverLevel).stations()) {
                for (PlacedStationPiece piece : station.pieces()) {
                    if (piece.bounds().isInside(pos) || piece.selectionBounds().isInside(pos)) {
                        for (PlacedTriggerZone zone : piece.triggerZones()) {
                            if (isSocketTrigger(zone) && (isInsideZone(zone, pos) || zone.min().distManhattan(pos) <= 4)) {
                                return true;
                            }
                        }
                    }
                }
            }

            dev.sixik.stationarenear.structures.world.StationStructureLibraryData library = dev.sixik.stationarenear.structures.world.StationStructureLibraryData.get(serverLevel);
            dev.sixik.stationarenear.structures.config.StationStructureFileStorage.loadExternalDefinitions(library);
            for (Map.Entry<ResourceLocation, net.minecraft.world.level.levelgen.structure.BoundingBox> entry : library.savedTemplateSelections().entrySet()) {
                if (entry.getValue().isInside(pos) || entry.getValue().inflatedBy(4).isInside(pos)) {
                    for (dev.sixik.stationarenear.structures.data.StationPieceDefinition piece : library.pieces()) {
                        if (piece.pool().equals(dev.sixik.stationarenear.structures.util.StationStructureIds.pool("space_ship")) && (piece.template().equals(entry.getKey()) || piece.id().equals(entry.getKey()))) {
                            dev.sixik.stationarenear.structures.generation.StationPlacementUtil.PlacedPieceContext context = dev.sixik.stationarenear.structures.generation.StationPlacementUtil.resolvePlacedPiece(piece, entry.getValue()).orElse(null);
                            if (context != null) {
                                for (dev.sixik.stationarenear.structures.data.StationTriggerZone zone : piece.triggerZones()) {
                                    PlacedTriggerZone placed = context.transformTrigger(zone);
                                    if (isSocketTrigger(placed) && (isInsideZone(placed, pos) || placed.min().distManhattan(pos) <= 4)) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    public static boolean isInsideZone(PlacedTriggerZone zone, BlockPos pos) {
        return pos.getX() >= zone.min().getX() && pos.getX() <= zone.max().getX()
                && pos.getY() >= zone.min().getY() && pos.getY() <= zone.max().getY()
                && pos.getZ() >= zone.min().getZ() && pos.getZ() <= zone.max().getZ();
    }

    public static boolean isSocketBlock(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) {
            return false;
        }
        String path = id.getPath().toLowerCase(Locale.ROOT);
        return path.contains("socket") || path.contains("outlet") || path.contains("rozetka");
    }

    public static boolean isSocketTrigger(PlacedTriggerZone zone) {
        if (zone == null) {
            return false;
        }
        String id = zone.id().toLowerCase(Locale.ROOT);
        if (id.contains("socket")) {
            return true;
        }
        if (zone.data().contains("socket")) {
            return true;
        }
        if (zone.data().contains("tag") && zone.data().getString("tag").toLowerCase(Locale.ROOT).contains("socket")) {
            return true;
        }
        return false;
    }
}
