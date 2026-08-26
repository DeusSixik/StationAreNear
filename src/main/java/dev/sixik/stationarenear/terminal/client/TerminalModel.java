package dev.sixik.stationarenear.terminal.client;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.terminal.block.entity.TerminalBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class TerminalModel extends GeoModel<TerminalBlockEntity> {

    @Override
    public ResourceLocation getModelResource(TerminalBlockEntity animatable) {
        return new ResourceLocation(StationAreNear.MODID, "geo/console.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(TerminalBlockEntity animatable) {
        return new ResourceLocation(StationAreNear.MODID, "textures/block/console.png");
    }

    @Override
    public ResourceLocation getAnimationResource(TerminalBlockEntity animatable) {
        return new ResourceLocation(StationAreNear.MODID, "animations/console.animation.json");
    }
}
