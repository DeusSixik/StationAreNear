package dev.sixik.stationarenear.ship.client;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.ship.block.entity.PressureTightDoorBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class PressureTightDoorModel extends GeoModel<PressureTightDoorBlockEntity> {

    @Override
    public ResourceLocation getModelResource(PressureTightDoorBlockEntity animatable) {
        return new ResourceLocation(StationAreNear.MODID, "geo/pressure_tight_door.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(PressureTightDoorBlockEntity animatable) {
        return new ResourceLocation(StationAreNear.MODID, "textures/block/pressure_tight_door.png");
    }

    @Override
    public ResourceLocation getAnimationResource(PressureTightDoorBlockEntity animatable) {
        return new ResourceLocation(StationAreNear.MODID, "animations/pressure_tight_door.animation.json");
    }
}
