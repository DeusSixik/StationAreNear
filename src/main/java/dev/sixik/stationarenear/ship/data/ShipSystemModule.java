package dev.sixik.stationarenear.ship.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public record ShipSystemModule(ShipSystemType type, int level, float durability, float maxDurability) {

    public ShipSystemModule {
        type = type == null ? ShipSystemType.HULL_PLATING : type;
        level = Math.max(1, level);
        maxDurability = Math.max(1.0F, maxDurability);
        durability = clamp(durability, 0.0F, maxDurability);
    }

    public static ShipSystemModule defaultModule(ShipSystemType type) {
        return new ShipSystemModule(type, type.defaultLevel(), type.defaultDurability(), type.defaultDurability());
    }

    public ShipSystemModule withDurability(float durability) {
        return new ShipSystemModule(type, level, durability, maxDurability);
    }

    public ShipSystemModule withLevel(int level) {
        float newMaxDurability = type.defaultDurability() * Math.max(1, level);
        return new ShipSystemModule(type, level, newMaxDurability, newMaxDurability);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.id());
        tag.putInt("level", level);
        tag.putFloat("durability", durability);
        tag.putFloat("maxDurability", maxDurability);
        return tag;
    }

    public static ShipSystemModule load(CompoundTag tag) {
        ShipSystemType type = ShipSystemType.byId(tag.getString("type"));
        int level = tag.contains("level") ? tag.getInt("level") : type.defaultLevel();
        float maxDurability = tag.contains("maxDurability") ? tag.getFloat("maxDurability") : type.defaultDurability() * level;
        float durability = tag.contains("durability") ? tag.getFloat("durability") : maxDurability;
        return new ShipSystemModule(type, level, durability, maxDurability);
    }

    public void encode(FriendlyByteBuf buffer) {
        type.encode(buffer);
        buffer.writeVarInt(level);
        buffer.writeFloat(durability);
        buffer.writeFloat(maxDurability);
    }

    public static ShipSystemModule decode(FriendlyByteBuf buffer) {
        return new ShipSystemModule(ShipSystemType.decode(buffer), buffer.readVarInt(), buffer.readFloat(), buffer.readFloat());
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
