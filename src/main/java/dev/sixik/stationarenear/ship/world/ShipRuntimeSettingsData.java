package dev.sixik.stationarenear.ship.world;

import dev.sixik.stationarenear.StationAreNear;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class ShipRuntimeSettingsData extends SavedData {

    private static final String DATA_NAME = StationAreNear.MODID + "_ship_runtime_settings";

    private boolean decompressionEffectsEnabled = true;

    public static ShipRuntimeSettingsData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ShipRuntimeSettingsData::load, ShipRuntimeSettingsData::new, DATA_NAME);
    }

    public boolean decompressionEffectsEnabled() {
        return decompressionEffectsEnabled;
    }

    public void decompressionEffectsEnabled(boolean enabled) {
        if (decompressionEffectsEnabled == enabled) {
            return;
        }
        decompressionEffectsEnabled = enabled;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("decompressionEffectsEnabled", decompressionEffectsEnabled);
        return tag;
    }

    private static ShipRuntimeSettingsData load(CompoundTag tag) {
        ShipRuntimeSettingsData data = new ShipRuntimeSettingsData();
        if (tag.contains("decompressionEffectsEnabled")) {
            data.decompressionEffectsEnabled = tag.getBoolean("decompressionEffectsEnabled");
        }
        return data;
    }
}
