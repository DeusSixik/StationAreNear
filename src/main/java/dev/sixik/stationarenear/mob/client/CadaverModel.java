package dev.sixik.stationarenear.mob.client;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.mob.entity.CadaverEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class CadaverModel extends GeoModel<CadaverEntity> {

    @Override
    public ResourceLocation getModelResource(CadaverEntity animatable) {
        return new ResourceLocation(StationAreNear.MODID, "geo/cadaver.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(CadaverEntity animatable) {
        return new ResourceLocation(StationAreNear.MODID, "textures/entity/cadaver.png");
    }

    @Override
    public ResourceLocation getAnimationResource(CadaverEntity animatable) {
        return new ResourceLocation(StationAreNear.MODID, "animations/cadaver.animation.json");
    }
}
