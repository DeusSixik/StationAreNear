package dev.sixik.stationarenear.navigation.ship;

import dev.sixik.stationarenear.StationAreNear;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.Optional;

public class ShipDockingAnchorSavedData extends SavedData {

    private static final String DATA_NAME = StationAreNear.MODID + "_ship_docking_anchors";

    private final Long2ObjectMap<ShipDockingAnchor> anchors = new Long2ObjectLinkedOpenHashMap<>();

    public static ShipDockingAnchorSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ShipDockingAnchorSavedData::load, ShipDockingAnchorSavedData::new, DATA_NAME);
    }

    public Optional<ShipDockingAnchor> anchor(BlockPos terminalPos) {
        return Optional.ofNullable(anchors.get(terminalPos.asLong()));
    }

    public Collection<ShipDockingAnchor> anchors() {
        return anchors.values();
    }

    public void upsert(ShipDockingAnchor anchor) {
        anchors.put(anchor.terminalPos().asLong(), anchor);
        setDirty();
    }

    public boolean remove(BlockPos terminalPos) {
        boolean removed = anchors.remove(terminalPos.asLong()) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag anchorTags = new ListTag();
        for (ShipDockingAnchor anchor : anchors.values()) {
            anchorTags.add(anchor.save());
        }
        tag.put("anchors", anchorTags);
        return tag;
    }

    private static ShipDockingAnchorSavedData load(CompoundTag tag) {
        ShipDockingAnchorSavedData data = new ShipDockingAnchorSavedData();
        ListTag anchorTags = tag.getList("anchors", Tag.TAG_COMPOUND);
        for (Tag anchorTag : anchorTags) {
            try {
                ShipDockingAnchor anchor = ShipDockingAnchor.load((CompoundTag) anchorTag);
                data.anchors.put(anchor.terminalPos().asLong(), anchor);
            } catch (RuntimeException exception) {
                StationAreNear.LOGGER.warn("Skipped broken ship docking anchor", exception);
            }
        }
        return data;
    }
}
