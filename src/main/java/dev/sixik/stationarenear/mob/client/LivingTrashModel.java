package dev.sixik.stationarenear.mob.client;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.mob.entity.LivingTrashEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class LivingTrashModel extends GeoModel<LivingTrashEntity> {

    @Override
    public ResourceLocation getModelResource(LivingTrashEntity animatable) {
        return new ResourceLocation(StationAreNear.MODID, "geo/living_trash.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(LivingTrashEntity animatable) {
        return new ResourceLocation(StationAreNear.MODID, "textures/entity/living_trash.png");
    }

    @Override
    public ResourceLocation getAnimationResource(LivingTrashEntity animatable) {
        return new ResourceLocation(StationAreNear.MODID, "animations/living_trash.animation.json");
    }
}
