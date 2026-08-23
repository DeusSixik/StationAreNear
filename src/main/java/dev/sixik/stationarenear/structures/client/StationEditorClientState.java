package dev.sixik.stationarenear.structures.client;

import dev.sixik.stationarenear.structures.data.TemplateSelectionEntry;
import dev.sixik.stationarenear.structures.editor.StationStructureEditorStick;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class StationEditorClientState {

    private static CompoundTag editorTag;
    private static String selectedKey = "root";
    private static List<TemplateSelectionEntry> templateSelections = List.of();
    private static List<ResourceLocation> poolIds = List.of();
    private static final Set<ResourceLocation> hiddenTemplateSelections = new LinkedHashSet<>();

    private StationEditorClientState() {
    }

    public static void setEditorTag(CompoundTag tag) {
        editorTag = tag.copy();
        StationStructureEditorStick.normalize(editorTag);
    }

    public static CompoundTag editorTag() {
        return editorTag == null ? null : editorTag.copy();
    }

    public static boolean hasEditorTag() {
        return editorTag != null;
    }

    public static BlockPos structureMin() {
        return editorTag == null ? BlockPos.ZERO : StationStructureEditorStick.structureMin(editorTag);
    }

    public static BlockPos structureMax() {
        return editorTag == null ? BlockPos.ZERO : StationStructureEditorStick.structureMax(editorTag);
    }

    public static String selectedKey() {
        return selectedKey;
    }

    public static void selectedKey(String key) {
        selectedKey = key == null || key.isBlank() ? "root" : key;
    }

    public static boolean showHandles() {
        return editorTag != null && (!editorTag.contains(StationStructureEditorStick.KEY_SHOW_HANDLES)
                || editorTag.getBoolean(StationStructureEditorStick.KEY_SHOW_HANDLES));
    }

    public static boolean showRootText() {
        return editorTag != null && (!editorTag.contains(StationStructureEditorStick.KEY_SHOW_ROOT_TEXT)
                || editorTag.getBoolean(StationStructureEditorStick.KEY_SHOW_ROOT_TEXT));
    }

    public static void setTemplateSelections(List<TemplateSelectionEntry> entries) {
        templateSelections = List.copyOf(entries);
    }

    public static List<TemplateSelectionEntry> templateSelections() {
        return new ArrayList<>(templateSelections);
    }

    public static void setPoolIds(List<ResourceLocation> ids) {
        poolIds = List.copyOf(ids);
    }

    public static List<ResourceLocation> poolIds() {
        return new ArrayList<>(poolIds);
    }

    public static boolean templateSelectionVisible(ResourceLocation template) {
        return !hiddenTemplateSelections.contains(template);
    }

    public static void templateSelectionVisible(ResourceLocation template, boolean visible) {
        if (visible) {
            hiddenTemplateSelections.remove(template);
        } else {
            hiddenTemplateSelections.add(template);
        }
    }
}
