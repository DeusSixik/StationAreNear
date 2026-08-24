package dev.sixik.stationarenear.ship.world;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.ship.data.ShipState;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public class ShipSavedData extends SavedData {

    private static final String DATA_NAME = StationAreNear.MODID + "_ships";

    private final Long2ObjectMap<ShipState> ships = new Long2ObjectLinkedOpenHashMap<>();

    public static ShipSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ShipSavedData::load, ShipSavedData::new, DATA_NAME);
    }

    public ShipState ship(BlockPos terminalPos) {
        ShipState state = ships.get(terminalPos.asLong());
        if (state == null) {
            state = ShipState.createDefault();
            ships.put(terminalPos.asLong(), state);
            setDirty();
        }
        return state;
    }

    public Optional<ShipState> shipIfPresent(BlockPos terminalPos) {
        return Optional.ofNullable(ships.get(terminalPos.asLong()));
    }

    public void ship(BlockPos terminalPos, ShipState state) {
        ships.put(terminalPos.asLong(), state);
        setDirty();
    }

    public Collection<ShipState> ships() {
        return ships.values();
    }

    public boolean remove(BlockPos terminalPos) {
        boolean removed = ships.remove(terminalPos.asLong()) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag shipTags = new ListTag();
        for (Long2ObjectMap.Entry<ShipState> entry : ships.long2ObjectEntrySet()) {
            CompoundTag shipTag = entry.getValue().save();
            shipTag.putLong("terminal", entry.getLongKey());
            shipTags.add(shipTag);
        }
        tag.put("ships", shipTags);
        return tag;
    }

    private static ShipSavedData load(CompoundTag tag) {
        ShipSavedData data = new ShipSavedData();
        ListTag shipTags = tag.getList("ships", Tag.TAG_COMPOUND);
        for (Tag shipEntry : shipTags) {
            try {
                CompoundTag shipTag = (CompoundTag) shipEntry;
                data.ships.put(shipTag.getLong("terminal"), ShipState.load(shipTag));
            } catch (RuntimeException exception) {
                StationAreNear.LOGGER.warn("Skipped broken ship state", exception);
            }
        }
        return data;
    }
}
