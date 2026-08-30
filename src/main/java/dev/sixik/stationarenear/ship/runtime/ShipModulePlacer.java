package dev.sixik.stationarenear.ship.runtime;

import dev.sixik.stationarenear.quest.block.WorkbenchBlock;
import dev.sixik.stationarenear.quest.registry.QuestBlocks;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchor;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorResolver;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorSavedData;
import dev.sixik.stationarenear.structures.config.StationStructureFileStorage;
import dev.sixik.stationarenear.structures.data.StationPieceDefinition;
import dev.sixik.stationarenear.structures.data.StationPoolDefinition;
import dev.sixik.stationarenear.structures.data.StationTriggerZone;
import dev.sixik.stationarenear.structures.generation.StationPlacementUtil;
import dev.sixik.stationarenear.structures.util.StationStructureIds;
import dev.sixik.stationarenear.structures.util.TagsConstants;
import dev.sixik.stationarenear.structures.world.StationStructureLibraryData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    public record ShipTriggerTarget(BoundingBox bounds, CompoundTag data, Direction direction, String pool) {
    }

    public static PlacementResult installCraftStation(ServerLevel level, BlockPos terminalPos) {
        return placePoolOrFallback(
                level,
                terminalPos,
                "craft_station",
                QuestBlocks.WORKBENCH.get().defaultBlockState(),
                TagsConstants.Ship.CRAFT_STATION,
                TagsConstants.Ship.CRAFTING_STATION,
                TagsConstants.Ship.WORKBENCH
        );
    }

    public static PlacementResult installExtraStorage(ServerLevel level, BlockPos terminalPos) {
        return placePoolOrFallback(
                level,
                terminalPos,
                "storage",
                getStorageBlockState(),
                TagsConstants.Ship.STORAGE,
                TagsConstants.Ship.EXTRA_STORAGE
        );
    }

    private static BlockState getStorageBlockState() {
        net.minecraft.world.level.block.Block cabinet = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new ResourceLocation("station_blocks", "cabinet_big"));
        if (cabinet != null && cabinet != Blocks.AIR) {
            return cabinet.defaultBlockState();
        }
        return Blocks.CHEST.defaultBlockState();
    }

    public static PlacementResult placePoolOrFallback(ServerLevel level, BlockPos terminalPos, String defaultPool, BlockState fallbackBlockState, String... tags) {
        List<ShipTriggerTarget> targets = findShipTriggerTargets(level, terminalPos, tags);
        if (targets.isEmpty()) {
            return PlacementResult.NO_TRIGGERS_FOUND;
        }

        StationStructureLibraryData library = StationStructureLibraryData.get(level);
        StationStructureFileStorage.loadExternalDefinitions(library);

        for (ShipTriggerTarget target : targets) {
            BoundingBox box = target.bounds();
            if (isTargetOccupied(level, box, fallbackBlockState)) {
                continue;
            }

            BlockPos checkPos = new BlockPos(box.minX(), box.minY(), box.minZ());
            String poolName = target.pool() != null && !target.pool().isBlank() ? target.pool() : defaultPool;
            ResourceLocation poolId = StationStructureIds.pool(poolName);
            Optional<StationPoolDefinition> poolOpt = library.pool(poolId);

            List<StationPieceDefinition> candidates = new ArrayList<>();
            if (poolOpt.isPresent()) {
                for (ResourceLocation id : poolOpt.get().roomPieces()) {
                    library.piece(id).ifPresent(candidates::add);
                }
                for (ResourceLocation id : poolOpt.get().startPieces()) {
                    library.piece(id).ifPresent(candidates::add);
                }
            }
            if (candidates.isEmpty()) {
                for (StationPieceDefinition piece : library.pieces()) {
                    if (piece.pool().equals(poolId)
                            || piece.pool().getPath().equalsIgnoreCase(poolName)
                            || (piece.id() != null && piece.id().getPath().equalsIgnoreCase(poolName))) {
                        candidates.add(piece);
                    }
                }
            }

            Direction direction = target.direction();

            if (!candidates.isEmpty()) {
                StationPieceDefinition pieceDef = candidates.get(level.getRandom().nextInt(candidates.size()));
                Optional<StructureTemplate> templateOpt = StationStructureFileStorage.getOrLoadTemplate(level, pieceDef.template());
                if (templateOpt.isPresent()) {
                    StructureTemplate template = templateOpt.get();
                    Rotation rotation = rotationForTemplate(template, direction, target.data());
                    BoundingBox localBounds = StationPlacementUtil.transformBounds(BlockPos.ZERO, template.getSize(), rotation);
                    BlockPos origin = checkPos.offset(-localBounds.minX(), -localBounds.minY(), -localBounds.minZ());
                    if (poolName.contains("craft_station") || defaultPool.contains("craft_station")) {
                        origin = origin.relative(direction.getCounterClockWise(), 1);
                    }

                    BoundingBox placeBox = StationPlacementUtil.transformBounds(origin, template.getSize(), rotation);
                    for (BlockPos p : BlockPos.betweenClosed(placeBox.minX(), placeBox.minY(), placeBox.minZ(), placeBox.maxX(), placeBox.maxY(), placeBox.maxZ())) {
                        level.removeBlock(p, false);
                    }

                    StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
                    template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2);
                    ensureMultipartPlaced(level, origin, template, rotation);
                    return PlacementResult.SUCCESS;
                }
            }

            BlockState stateToPlace = fallbackBlockState;
            if (stateToPlace.getBlock() instanceof WorkbenchBlock) {
                int x = switch (direction) {
                    case NORTH -> box.maxX();
                    case SOUTH -> box.minX();
                    case EAST -> box.minX();
                    case WEST -> box.minX();
                    default -> box.minX();
                };
                int z = switch (direction) {
                    case NORTH -> box.minZ();
                    case SOUTH -> box.minZ();
                    case EAST -> box.maxZ();
                    case WEST -> box.minZ();
                    default -> box.minZ();
                };
                BlockPos masterPos = new BlockPos(x, box.minY(), z).relative(direction.getCounterClockWise(), 1);
                for (BlockPos partPos : WorkbenchBlock.partPositions(masterPos, direction)) {
                    level.removeBlock(partPos, false);
                }
                WorkbenchBlock.placeWorkbenchParts(level, masterPos, direction);
                return PlacementResult.SUCCESS;
            }

            if (stateToPlace.hasProperty(HorizontalDirectionalBlock.FACING)) {
                stateToPlace = stateToPlace.setValue(HorizontalDirectionalBlock.FACING, direction);
            }
            for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                level.setBlock(pos, stateToPlace, 3);
            }
            return PlacementResult.SUCCESS;
        }

        return PlacementResult.LIMIT_REACHED;
    }

    private static boolean isTargetOccupied(ServerLevel level, BoundingBox box, BlockState fallbackBlockState) {
        Block cabinetBig = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("station_blocks", "cabinet_big"));
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState state = level.getBlockState(pos);
            if (cabinetBig != null && cabinetBig != Blocks.AIR && state.is(cabinetBig)) {
                return true;
            }
            if (state.getBlock() instanceof WorkbenchBlock) {
                return true;
            }
            if (fallbackBlockState != null && !fallbackBlockState.isAir() && state.is(fallbackBlockState.getBlock()) && !state.isAir()) {
                return true;
            }
        }
        return false;
    }

    private static void ensureMultipartPlaced(ServerLevel level, BlockPos origin, StructureTemplate template, Rotation rotation) {
        BoundingBox box = StationPlacementUtil.transformBounds(origin, template.getSize(), rotation);
        BlockPos.betweenClosedStream(box).forEach(pos -> {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof WorkbenchBlock && WorkbenchBlock.isMaster(state)) {
                WorkbenchBlock.placeWorkbenchParts(level, pos.immutable(), state.getValue(WorkbenchBlock.FACING));
            }
        });
    }

    public static int availableStorageSlots(ServerLevel level, BlockPos terminalPos) {
        return findShipTriggerTargets(level, terminalPos, TagsConstants.Ship.STORAGE, TagsConstants.Ship.EXTRA_STORAGE).size();
    }

    public static List<ShipTriggerTarget> findShipTriggerTargets(ServerLevel level, BlockPos terminalPos, String... tags) {
        Optional<ShipDockingAnchor> anchorOpt = ShipDockingAnchorSavedData.get(level)
                .anchor(terminalPos)
                .or(() -> ShipDockingAnchorResolver.bindNearbyShip(level, terminalPos));
        if (anchorOpt.isEmpty()) {
            return List.of();
        }

        ShipDockingAnchor anchor = anchorOpt.get();
        BoundingBox shipBounds = anchor.shipBounds();
        StationStructureLibraryData library = StationStructureLibraryData.get(level);
        StationStructureFileStorage.loadExternalDefinitions(library);
        List<ShipTriggerTarget> results = new ArrayList<>();

        for (Map.Entry<ResourceLocation, BoundingBox> entry : library.savedTemplateSelections().entrySet()) {
            if (!sameBounds(shipBounds, entry.getValue())) {
                continue;
            }
            for (StationPieceDefinition piece : library.pieces()) {
                if (!isShipPieceSelection(piece, entry.getKey())) {
                    continue;
                }
                StationPlacementUtil.PlacedPieceContext context = StationPlacementUtil.resolvePlacedPiece(piece, shipBounds)
                        .orElseGet(() -> new StationPlacementUtil.PlacedPieceContext(piece, new BlockPos(shipBounds.minX(), shipBounds.minY(), shipBounds.minZ()).subtract(piece.selectionMin()), Rotation.NONE, shipBounds));
                for (StationTriggerZone zone : piece.triggerZones()) {
                    if (hasMatchingTag(zone, tags)) {
                        dev.sixik.stationarenear.structures.data.PlacedTriggerZone placedZone = context.transformTrigger(zone);
                        BoundingBox triggerBounds = new BoundingBox(placedZone.min().getX(), placedZone.min().getY(), placedZone.min().getZ(), placedZone.max().getX(), placedZone.max().getY(), placedZone.max().getZ());
                        Direction dir = triggerDirection(placedZone.data(), context.rotation());
                        String pool = placedZone.data() != null && placedZone.data().contains("pool") ? placedZone.data().getString("pool") : "";
                        results.add(new ShipTriggerTarget(triggerBounds, placedZone.data(), dir, pool));
                    }
                }
            }
        }

        if (results.isEmpty()) {
            for (StationPieceDefinition piece : library.pieces()) {
                if (!piece.pool().equals(SHIP_POOL)) {
                    continue;
                }
                StationPlacementUtil.PlacedPieceContext context = StationPlacementUtil.resolvePlacedPiece(piece, shipBounds)
                        .orElseGet(() -> new StationPlacementUtil.PlacedPieceContext(piece, new BlockPos(shipBounds.minX(), shipBounds.minY(), shipBounds.minZ()).subtract(piece.selectionMin()), Rotation.NONE, shipBounds));
                for (StationTriggerZone zone : piece.triggerZones()) {
                    if (hasMatchingTag(zone, tags)) {
                        dev.sixik.stationarenear.structures.data.PlacedTriggerZone placedZone = context.transformTrigger(zone);
                        BoundingBox triggerBounds = new BoundingBox(placedZone.min().getX(), placedZone.min().getY(), placedZone.min().getZ(), placedZone.max().getX(), placedZone.max().getY(), placedZone.max().getZ());
                        Direction dir = triggerDirection(placedZone.data(), context.rotation());
                        String pool = placedZone.data() != null && placedZone.data().contains("pool") ? placedZone.data().getString("pool") : "";
                        results.add(new ShipTriggerTarget(triggerBounds, placedZone.data(), dir, pool));
                    }
                }
            }
        }

        return results;
    }

    private static Direction triggerDirection(CompoundTag data, Rotation fallbackRotation) {
        if (data != null) {
            String dirStr = data.contains("objectDirection") ? data.getString("objectDirection")
                    : (data.contains("direction") ? data.getString("direction")
                    : (data.contains("facing") ? data.getString("facing") : ""));
            Direction dir = Direction.byName(dirStr.toLowerCase(Locale.ROOT));
            if (dir != null && dir.getAxis().isHorizontal()) {
                return dir;
            }
        }
        return fallbackRotation != null ? fallbackRotation.rotate(Direction.NORTH) : Direction.NORTH;
    }

    private static Rotation rotationForTemplate(StructureTemplate template, Direction targetDirection, CompoundTag data) {
        Direction baseFacing = inferTemplateFacing(template, data);
        if (baseFacing == null) {
            baseFacing = Direction.NORTH;
        }
        for (Rotation rot : List.of(Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90)) {
            if (rot.rotate(baseFacing) == targetDirection) {
                return rot;
            }
        }
        return Rotation.NONE;
    }

    private static Direction inferTemplateFacing(StructureTemplate template, CompoundTag data) {
        if (data != null && data.contains("objectBaseDirection")) {
            Direction dir = Direction.byName(data.getString("objectBaseDirection").toLowerCase(Locale.ROOT));
            if (dir != null && dir.getAxis().isHorizontal()) {
                return dir;
            }
        }
        CompoundTag savedTemplate = template.save(new CompoundTag());
        ListTag palettes = savedTemplate.getList("palettes", Tag.TAG_LIST);
        ListTag palette = !palettes.isEmpty() ? palettes.getList(0) : savedTemplate.getList("palette", Tag.TAG_COMPOUND);
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag state = palette.getCompound(i);
            if (!state.contains("Properties", Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag properties = state.getCompound("Properties");
            for (String key : List.of("facing", "horizontal_facing")) {
                if (properties.contains(key, Tag.TAG_STRING)) {
                    Direction dir = Direction.byName(properties.getString(key).toLowerCase(Locale.ROOT));
                    if (dir != null && dir.getAxis().isHorizontal()) {
                        return dir;
                    }
                }
            }
        }
        return Direction.NORTH;
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
