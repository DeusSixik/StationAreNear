package dev.sixik.stationarenear.ship.data;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Locale;

public enum ShipSystemType {
    HULL_PLATING("Hull plating", 1, 250.0F),
    ENGINE("Engine", 1, 160.0F),
    LIFE_SUPPORT("Life support", 1, 140.0F),
    NAVIGATION("Navigation", 1, 120.0F),
    POWER_CORE("Power core", 1, 180.0F);

    private final String displayName;
    private final int defaultLevel;
    private final float defaultDurability;

    ShipSystemType(String displayName, int defaultLevel, float defaultDurability) {
        this.displayName = displayName;
        this.defaultLevel = defaultLevel;
        this.defaultDurability = defaultDurability;
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
