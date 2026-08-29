package dev.sixik.stationarenear.ship.data;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Locale;

public enum ShipSystemType {
    HULL_PLATING("Hull plating", 1, 250.0F, 100.0, "Base ship hull reinforcement"),
    ENGINE("Engine", 1, 160.0F, 100.0, "Ship propulsion system"),
    LIFE_SUPPORT("Life support", 1, 140.0F, 100.0, "Ship atmosphere and pressurization system"),
    NAVIGATION("Navigation", 1, 120.0F, 100.0, "Core solar navigation computer"),
    POWER_CORE("Power core", 1, 180.0F, 100.0, "Internal power distribution grid"),
    AUTO_DOORS("Auto Door Closer", 0, 100.0F, 100.0, "Automatically closes ship pressure door on undocking"),
    PLAYER_TRACKER("Player Tracker", 0, 100.0F, 150.0, "Displays player locations on station map"),
    CRAFT_STATION("Crafting Station", 0, 100.0F, 120.0, "Spawns workbench in ship craft_station trigger"),
    EXTRA_STORAGE("Extra Storage", 0, 100.0F, 80.0, "Spawns container in ship storage trigger"),
    MANEUVERABILITY("Maneuverability Thrusters", 0, 100.0F, 150.0, "Eliminates inertia and drift in solar navigation"),
    PANEL_DETECTOR("Panel Detector", 0, 100.0F, 120.0, "Shows electric panel positions on station map"),
    STATION_LOCATOR("Station Locator", 0, 100.0F, 100.0, "Enables radar and stations scan commands"),
    RAD_SHIELDING("Radiation Shielding", 0, 100.0F, 200.0, "Protects against station radiation hazard");

    private final String displayName;
    private final int defaultLevel;
    private final float defaultDurability;
    private final double price;
    private final String description;

    ShipSystemType(String displayName, int defaultLevel, float defaultDurability, double price, String description) {
        this.displayName = displayName;
        this.defaultLevel = defaultLevel;
        this.defaultDurability = defaultDurability;
        this.price = price;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public int defaultLevel() {
        return defaultLevel;
    }

    public float defaultDurability() {
        return defaultDurability;
    }

    public double price() {
        return price;
    }

    public String description() {
        return description;
    }

    public boolean isUpgrade() {
        return defaultLevel == 0;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this);
    }

    public static ShipSystemType decode(FriendlyByteBuf buffer) {
        return buffer.readEnum(ShipSystemType.class);
    }

    public static ShipSystemType byId(String id) {
        if (id != null) {
            for (ShipSystemType type : values()) {
                if (type.id().equals(id) || type.name().equalsIgnoreCase(id)) {
                    return type;
                }
            }
        }
        return HULL_PLATING;
    }
}
