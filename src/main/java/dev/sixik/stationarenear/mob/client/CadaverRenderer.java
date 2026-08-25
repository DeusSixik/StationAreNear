package dev.sixik.stationarenear.mob.client;

import dev.sixik.stationarenear.mob.entity.CadaverEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class CadaverRenderer extends GeoEntityRenderer<CadaverEntity> {

    public CadaverRenderer(EntityRendererProvider.Context context) {
        super(context, new CadaverModel());
        shadowRadius = 0.45F;
    }
}
