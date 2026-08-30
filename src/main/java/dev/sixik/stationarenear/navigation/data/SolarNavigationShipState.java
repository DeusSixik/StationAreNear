package dev.sixik.stationarenear.navigation.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public record SolarNavigationShipState(float shipX, float shipY, float velocityX, float velocityY, float angle) {

    public static final SolarNavigationShipState DEFAULT = new SolarNavigationShipState(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeFloat(shipX);
        buffer.writeFloat(shipY);
        buffer.writeFloat(velocityX);
        buffer.writeFloat(velocityY);
        buffer.writeFloat(angle);
    }

    public static SolarNavigationShipState decode(FriendlyByteBuf buffer) {
        return new SolarNavigationShipState(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("shipX", shipX);
        tag.putFloat("shipY", shipY);
        tag.putFloat("velocityX", velocityX);
        tag.putFloat("velocityY", velocityY);
        tag.putFloat("angle", angle);
        return tag;
    }

    public static SolarNavigationShipState load(CompoundTag tag) {
        return new SolarNavigationShipState(
                tag.getFloat("shipX"),
                tag.getFloat("shipY"),
                tag.getFloat("velocityX"),
                tag.getFloat("velocityY"),
                tag.contains("angle") ? tag.getFloat("angle") : DEFAULT.angle()
        );
    }
}
