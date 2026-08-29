package dev.sixik.stationarenear.ship.world;

import dev.sixik.stationarenear.StationAreNear;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Set;
import java.util.UUID;

public class ShipWorldSpawnSavedData extends SavedData {

    private static final String DATA_NAME = StationAreNear.MODID + "_ship_world_spawn";

    private boolean shipSpawned;
    private BoundingBox shipBounds;
    private BoundingBox spawnZoneBounds;
    private BlockPos defaultSpawnPos = BlockPos.ZERO;
    private final Set<UUID> spawnedPlayers = new ObjectOpenHashSet<>();

    public static ShipWorldSpawnSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ShipWorldSpawnSavedData::load, ShipWorldSpawnSavedData::new, DATA_NAME);
    }

    public boolean isShipSpawned() {
        return shipSpawned;
    }

    public void setShipSpawned(boolean shipSpawned) {
        this.shipSpawned = shipSpawned;
        setDirty();
    }

    public BoundingBox getShipBounds() {
        return shipBounds;
    }

    public void setShipBounds(BoundingBox shipBounds) {
        this.shipBounds = shipBounds;
        setDirty();
    }

    public BoundingBox getSpawnZoneBounds() {
        return spawnZoneBounds != null ? spawnZoneBounds : (shipBounds != null ? shipBounds : new BoundingBox(defaultSpawnPos));
    }

    public void setSpawnZoneBounds(BoundingBox spawnZoneBounds) {
        this.spawnZoneBounds = spawnZoneBounds;
        setDirty();
    }

    public BlockPos getDefaultSpawnPos() {
        return defaultSpawnPos;
    }

    public void setDefaultSpawnPos(BlockPos defaultSpawnPos) {
        this.defaultSpawnPos = defaultSpawnPos;
        setDirty();
    }

    public boolean hasSpawned(UUID playerUuid) {
        return spawnedPlayers.contains(playerUuid);
    }

    public void markSpawned(UUID playerUuid) {
        if (spawnedPlayers.add(playerUuid)) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("shipSpawned", shipSpawned);
        if (shipBounds != null) {
            tag.putIntArray("shipBounds", new int[]{
                    shipBounds.minX(), shipBounds.minY(), shipBounds.minZ(),
                    shipBounds.maxX(), shipBounds.maxY(), shipBounds.maxZ()
            });
        }
        if (spawnZoneBounds != null) {
            tag.putIntArray("spawnZoneBounds", new int[]{
                    spawnZoneBounds.minX(), spawnZoneBounds.minY(), spawnZoneBounds.minZ(),
                    spawnZoneBounds.maxX(), spawnZoneBounds.maxY(), spawnZoneBounds.maxZ()
            });
        }
        tag.putLong("defaultSpawnPos", defaultSpawnPos.asLong());

        ListTag list = new ListTag();
        for (UUID uuid : spawnedPlayers) {
            list.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("spawnedPlayers", list);
        return tag;
    }

    private static ShipWorldSpawnSavedData load(CompoundTag tag) {
        ShipWorldSpawnSavedData data = new ShipWorldSpawnSavedData();
        data.shipSpawned = tag.getBoolean("shipSpawned");
        if (tag.contains("shipBounds")) {
            int[] arr = tag.getIntArray("shipBounds");
            if (arr.length >= 6) {
                data.shipBounds = new BoundingBox(arr[0], arr[1], arr[2], arr[3], arr[4], arr[5]);
            }
        }
        if (tag.contains("spawnZoneBounds")) {
            int[] arr = tag.getIntArray("spawnZoneBounds");
            if (arr.length >= 6) {
                data.spawnZoneBounds = new BoundingBox(arr[0], arr[1], arr[2], arr[3], arr[4], arr[5]);
            }
        }
        if (tag.contains("defaultSpawnPos")) {
            data.defaultSpawnPos = BlockPos.of(tag.getLong("defaultSpawnPos"));
        }
        ListTag list = tag.getList("spawnedPlayers", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                data.spawnedPlayers.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }
}
