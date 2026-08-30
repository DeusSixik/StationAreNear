package dev.sixik.stationarenear.ship.runtime;

import dev.sixik.stationarenear.navigation.block.SolarNavigationTerminalBlock;
import dev.sixik.stationarenear.navigation.registry.SolarNavigationBlocks;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorResolver;
import dev.sixik.stationarenear.ship.world.ShipWorldSpawnSavedData;
import dev.sixik.stationarenear.structures.config.StationStructureFileStorage;
import dev.sixik.stationarenear.structures.data.StationPieceDefinition;
import dev.sixik.stationarenear.structures.data.StationTriggerZone;
import dev.sixik.stationarenear.structures.generation.StationPlacementUtil;
import dev.sixik.stationarenear.structures.network.StationStructureNetwork;
import dev.sixik.stationarenear.structures.util.StationStructureIds;
import dev.sixik.stationarenear.structures.util.TagsConstants;
import dev.sixik.stationarenear.structures.world.StationStructureLibraryData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.Optional;

public final class ShipWorldSpawnManager {

    private static final ResourceLocation SHIP_POOL = StationStructureIds.pool("space_ship");
    private static final BlockPos DEFAULT_ORIGIN = new BlockPos(0, 100, 0);

    private ShipWorldSpawnManager() {
    }

    public static void ensureShipSpawned(ServerLevel level) {
        ShipWorldSpawnSavedData data = ShipWorldSpawnSavedData.get(level);
        if (data.isShipSpawned()) {
            return;
        }

        StationStructureLibraryData library = StationStructureLibraryData.get(level);
        StationStructureFileStorage.loadExternalDefinitions(library);
        Optional<StationPieceDefinition> pieceOpt = library.pieces().stream()
                .filter(p -> p.pool().equals(SHIP_POOL))
                .findFirst();

        if (pieceOpt.isEmpty()) {
            return;
        }

        StationPieceDefinition definition = pieceOpt.get();
        Optional<StructureTemplate> templateOpt = StationStructureFileStorage.getOrLoadTemplate(level, definition.template());
        if (templateOpt.isEmpty()) {
            return;
        }

        BlockPos origin = DEFAULT_ORIGIN;
        templateOpt.get().placeInWorld(level, origin, origin, new StructurePlaceSettings().setRotation(Rotation.NONE), level.getRandom(), 2);
        BoundingBox selectionBounds = StationPlacementUtil.transformBox(origin, definition.selectionMin(), definition.selectionMax(), Rotation.NONE);
        library.upsertTemplateSelection(definition.template(), selectionBounds);
        StationStructureNetwork.syncTemplateSelections(level);

        BoundingBox spawnZone = null;
        for (StationTriggerZone zone : definition.triggerZones()) {
            if (hasSpawnTag(zone)) {
                spawnZone = StationPlacementUtil.transformBox(origin, zone.min(), zone.max(), Rotation.NONE);
                break;
            }
        }

        BlockPos defaultSpawn;
        if (spawnZone != null) {
            defaultSpawn = new BlockPos((spawnZone.minX() + spawnZone.maxX()) / 2, spawnZone.minY(), (spawnZone.minZ() + spawnZone.maxZ()) / 2);
        } else {
            defaultSpawn = new BlockPos(selectionBounds.getCenter().getX(), selectionBounds.minY() + 1, selectionBounds.getCenter().getZ());
            spawnZone = new BoundingBox(defaultSpawn.getX() - 1, defaultSpawn.getY(), defaultSpawn.getZ() - 1, defaultSpawn.getX() + 1, defaultSpawn.getY() + 1, defaultSpawn.getZ() + 1);
        }

        level.setDefaultSpawnPos(defaultSpawn, 0.0F);

        for (BlockPos pos : BlockPos.betweenClosed(selectionBounds.minX(), selectionBounds.minY(), selectionBounds.minZ(), selectionBounds.maxX(), selectionBounds.maxY(), selectionBounds.maxZ())) {
            BlockState state = level.getBlockState(pos);
            if (state.is(SolarNavigationBlocks.SOLAR_NAVIGATION_TERMINAL.get()) && state.hasProperty(SolarNavigationTerminalBlock.FACING)) {
                ShipDockingAnchorResolver.bindNearbyShip(level, pos.immutable());
                break;
            }
        }

        data.setShipSpawned(true);
        data.setShipBounds(selectionBounds);
        data.setSpawnZoneBounds(spawnZone);
        data.setDefaultSpawnPos(defaultSpawn);
    }

    public static BlockPos randomSpawnPos(ServerLevel level, BoundingBox zone) {
        if (zone == null) {
            return level.getSharedSpawnPos();
        }

        int minX = Math.min(zone.minX(), zone.maxX());
        int maxX = Math.max(zone.minX(), zone.maxX());
        int minZ = Math.min(zone.minZ(), zone.maxZ());
        int maxZ = Math.max(zone.minZ(), zone.maxZ());
        int y = zone.minY();

        int rx = minX == maxX ? minX : minX + level.getRandom().nextInt(maxX - minX + 1);
        int rz = minZ == maxZ ? minZ : minZ + level.getRandom().nextInt(maxZ - minZ + 1);
        return new BlockPos(rx, y, rz);
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ServerLevel level = player.serverLevel();
        ensureShipSpawned(level);

        ShipWorldSpawnSavedData data = ShipWorldSpawnSavedData.get(level);
        if (!data.isShipSpawned()) {
            return;
        }

        if (!data.hasSpawned(player.getUUID())) {
            BlockPos spawnPos = randomSpawnPos(level, data.getSpawnZoneBounds());
            player.teleportTo(level, spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, player.getYRot(), player.getXRot());
            player.setRespawnPosition(level.dimension(), spawnPos, player.getYRot(), true, false);
            data.markSpawned(player.getUUID());
        }
    }

    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isEndConquered()) {
            return;
        }

        ServerLevel level = player.serverLevel();
        ShipWorldSpawnSavedData data = ShipWorldSpawnSavedData.get(level);
        if (data.isShipSpawned()) {
            BlockPos spawnPos = randomSpawnPos(level, data.getSpawnZoneBounds());
            player.setRespawnPosition(level.dimension(), spawnPos, player.getYRot(), true, false);
        }
    }

    private static boolean hasSpawnTag(StationTriggerZone zone) {
        if (zone.id() != null) {
            String id = zone.id().toLowerCase();
            if (id.contains(TagsConstants.Ship.SPAWN_POSITION) || id.contains(TagsConstants.Ship.SPAWN_POS) || id.contains(TagsConstants.Ship.PLAYER_SPAWN) || id.equals(TagsConstants.Ship.SPAWN)) {
                return true;
            }
        }
        if (zone.data() != null) {
            String tags = zone.data().contains(TagsConstants.Keys.TAGS)
                    ? zone.data().getString(TagsConstants.Keys.TAGS).toLowerCase()
                    : (zone.data().contains(TagsConstants.Keys.TAG) ? zone.data().getString(TagsConstants.Keys.TAG).toLowerCase() : "");
            for (String tag : tags.split("[,; ]+")) {
                String clean = tag.trim();
                if (clean.equals(TagsConstants.Ship.SPAWN_POSITION) || clean.equals(TagsConstants.Ship.SPAWN_POS) || clean.equals(TagsConstants.Ship.PLAYER_SPAWN) || clean.equals(TagsConstants.Ship.SPAWN)) {
                    return true;
                }
            }
        }
        return false;
    }
}
