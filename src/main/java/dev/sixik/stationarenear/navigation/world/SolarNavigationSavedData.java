package dev.sixik.stationarenear.navigation.world;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.navigation.data.SolarNavigationQuestMarker;
import dev.sixik.stationarenear.navigation.data.SolarNavigationShipState;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public class SolarNavigationSavedData extends SavedData {

    private static final String DATA_NAME = StationAreNear.MODID + "_solar_navigation";

    private final Map<Long, SolarNavigationShipState> shipStates = new Object2ObjectLinkedOpenHashMap<>();
    private final Map<String, SolarNavigationQuestMarker> questMarkers = new Object2ObjectLinkedOpenHashMap<>();

    public static SolarNavigationSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SolarNavigationSavedData::load, SolarNavigationSavedData::new, DATA_NAME);
    }

    public SolarNavigationShipState shipState(BlockPos terminalPos) {
        return shipStates.getOrDefault(terminalPos.asLong(), SolarNavigationShipState.DEFAULT);
    }

    public void shipState(BlockPos terminalPos, SolarNavigationShipState state) {
        shipStates.put(terminalPos.asLong(), state);
        setDirty();
    }

    public Collection<SolarNavigationQuestMarker> questMarkers() {
        return questMarkers.values();
    }

    public Optional<SolarNavigationQuestMarker> questMarker(String id) {
        return Optional.ofNullable(questMarkers.get(id));
    }

    public void upsertQuestMarker(SolarNavigationQuestMarker marker) {
        questMarkers.put(marker.id(), marker);
        setDirty();
    }

    public boolean removeQuestMarker(String id) {
        boolean removed = questMarkers.remove(id) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag stateTags = new ListTag();
        for (Map.Entry<Long, SolarNavigationShipState> entry : shipStates.entrySet()) {
            CompoundTag stateTag = entry.getValue().save();
            stateTag.putLong("terminal", entry.getKey());
            stateTags.add(stateTag);
        }
        tag.put("shipStates", stateTags);

        ListTag markerTags = new ListTag();
        for (SolarNavigationQuestMarker marker : questMarkers.values()) {
            markerTags.add(marker.save());
        }
        tag.put("questMarkers", markerTags);
        return tag;
    }

    private static SolarNavigationSavedData load(CompoundTag tag) {
        SolarNavigationSavedData data = new SolarNavigationSavedData();
        ListTag stateTags = tag.getList("shipStates", Tag.TAG_COMPOUND);
        for (Tag stateEntry : stateTags) {
            CompoundTag stateTag = (CompoundTag) stateEntry;
            data.shipStates.put(stateTag.getLong("terminal"), SolarNavigationShipState.load(stateTag));
        }

        ListTag markerTags = tag.getList("questMarkers", Tag.TAG_COMPOUND);
        for (Tag markerEntry : markerTags) {
            try {
                SolarNavigationQuestMarker marker = SolarNavigationQuestMarker.load((CompoundTag) markerEntry);
                data.questMarkers.put(marker.id(), marker);
            } catch (RuntimeException exception) {
                StationAreNear.LOGGER.warn("Skipped broken solar navigation quest marker", exception);
            }
        }
        return data;
    }
}
