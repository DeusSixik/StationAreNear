package dev.sixik.stationarenear.ship.world;

import dev.sixik.stationarenear.StationAreNear;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Optional;

public class ShipControlLockSavedData extends SavedData {

    private static final String DATA_NAME = StationAreNear.MODID + "_ship_control_locks";

    private final Long2ObjectMap<String> lockedTerminals = new Long2ObjectLinkedOpenHashMap<>();

    public static ShipControlLockSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ShipControlLockSavedData::load, ShipControlLockSavedData::new, DATA_NAME);
    }

    public boolean isLocked(BlockPos terminalPos) {
        return lockedTerminals.containsKey(terminalPos.asLong());
    }

    public Optional<String> reason(BlockPos terminalPos) {
        return Optional.ofNullable(lockedTerminals.get(terminalPos.asLong()));
    }

    public void lock(BlockPos terminalPos, String reason) {
        lockedTerminals.put(terminalPos.asLong(), reason == null ? "" : reason);
        setDirty();
    }

    public boolean unlock(BlockPos terminalPos) {
        boolean removed = lockedTerminals.remove(terminalPos.asLong()) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (Long2ObjectMap.Entry<String> entry : lockedTerminals.long2ObjectEntrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLong("terminal", entry.getLongKey());
            entryTag.putString("reason", entry.getValue());
            entries.add(entryTag);
        }
        tag.put("lockedTerminals", entries);
        return tag;
    }

    private static ShipControlLockSavedData load(CompoundTag tag) {
        ShipControlLockSavedData data = new ShipControlLockSavedData();
        ListTag entries = tag.getList("lockedTerminals", Tag.TAG_COMPOUND);
        for (Tag entry : entries) {
            CompoundTag entryTag = (CompoundTag) entry;
            data.lockedTerminals.put(entryTag.getLong("terminal"), entryTag.getString("reason"));
        }
        return data;
    }
}
