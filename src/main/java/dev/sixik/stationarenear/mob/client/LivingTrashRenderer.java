package dev.sixik.stationarenear.mob.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sixik.stationarenear.mob.entity.LivingTrashEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class LivingTrashRenderer extends GeoEntityRenderer<LivingTrashEntity> {

    public LivingTrashRenderer(EntityRendererProvider.Context context) {
        super(context, new LivingTrashModel());
        shadowRadius = 0.25F;
    }

    @Override
    public void render(LivingTrashEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (entity.isHiding()) {
            return;
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
