package dev.sixik.stationarenear.structures.editor;

public enum StationEditorWandMode {
    ZONE_SELECTION("Zone Selection"),
    TRIGGER_MANAGER_CREATE("Trigger Manager Create"),
    TRIGGER_MANAGER_EDIT("Trigger Manager Edit"),
    TRIGGER_SHAPE_POINTS("Trigger Shape Points"),
    CONNECTION_MANAGER("Connection Manager"),
    STRUCTURE_COPY("Structure Copy");

    private final String title;

    StationEditorWandMode(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }

    public StationEditorWandMode next(int direction) {
        StationEditorWandMode[] values = values();
        int step = direction >= 0 ? 1 : -1;
        return values[Math.floorMod(ordinal() + step, values.length)];
    }
}
