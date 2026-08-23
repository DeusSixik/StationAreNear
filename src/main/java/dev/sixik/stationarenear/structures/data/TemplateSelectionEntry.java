package dev.sixik.stationarenear.structures.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record TemplateSelectionEntry(
        ResourceLocation template,
        String source,
        boolean hasBounds,
        BoundingBox bounds
) {

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("template", template.toString());
        tag.putString("source", source);
        tag.putBoolean("hasBounds", hasBounds);
        if (hasBounds && bounds != null) {
            tag.put("bounds", saveBounds(bounds));
        }
        return tag;
    }

    public static TemplateSelectionEntry load(CompoundTag tag) {
        ResourceLocation template = ResourceLocation.tryParse(tag.getString("template"));
        if (template == null) {
            template = new ResourceLocation(dev.sixik.stationarenear.StationAreNear.MODID, "missing");
        }
        boolean hasBounds = tag.getBoolean("hasBounds") && tag.contains("bounds");
        return new TemplateSelectionEntry(template, tag.getString("source"), hasBounds, hasBounds ? loadBounds(tag.getCompound("bounds")) : null);
    }

    public static CompoundTag saveBounds(BoundingBox bounds) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("minX", bounds.minX());
        tag.putInt("minY", bounds.minY());
        tag.putInt("minZ", bounds.minZ());
        tag.putInt("maxX", bounds.maxX());
        tag.putInt("maxY", bounds.maxY());
        tag.putInt("maxZ", bounds.maxZ());
        return tag;
    }

    public static BoundingBox loadBounds(CompoundTag tag) {
        return new BoundingBox(tag.getInt("minX"), tag.getInt("minY"), tag.getInt("minZ"), tag.getInt("maxX"), tag.getInt("maxY"), tag.getInt("maxZ"));
    }
}
