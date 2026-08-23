package dev.sixik.stationarenear.structures.util;

import dev.sixik.stationarenear.StationAreNear;
import net.minecraft.resources.ResourceLocation;

public final class StationStructureIds {

    private StationStructureIds() {
    }

    public static ResourceLocation template(String value) {
        return normalize(value, "stations/new_piece");
    }

    public static ResourceLocation pool(String value) {
        return normalize(value, "space_station");
    }

    public static ResourceLocation normalize(String value, String fallbackPath) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            normalized = fallbackPath;
        }
        if (!normalized.contains(":")) {
            normalized = StationAreNear.MODID + ":" + normalized;
        }
        ResourceLocation id = ResourceLocation.tryParse(normalized);
        if (id == null) {
            id = new ResourceLocation(StationAreNear.MODID, fallbackPath);
        }
        return id;
    }
}
