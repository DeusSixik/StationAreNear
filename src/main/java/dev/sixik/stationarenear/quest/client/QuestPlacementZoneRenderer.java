package dev.sixik.stationarenear.quest.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sixik.stationarenear.quest.data.QuestPlacementZoneHint;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

public final class QuestPlacementZoneRenderer {

    private QuestPlacementZoneRenderer() {
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        List<QuestPlacementZoneHint> hints = QuestPlacementZoneClientState.hints();
        if (hints.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        double camX = camera.getPosition().x;
        double camY = camera.getPosition().y;
        double camZ = camera.getPosition().z;

        poseStack.pushPose();
        poseStack.translate(-camX, -camY, -camZ);

        ItemStack mainHand = minecraft.player.getMainHandItem();
        ItemStack offHand = minecraft.player.getOffhandItem();

        for (QuestPlacementZoneHint hint : hints) {
            double centerX = (hint.min().getX() + hint.max().getX() + 1) * 0.5D;
            double centerY = (hint.min().getY() + hint.max().getY() + 1) * 0.5D;
            double centerZ = (hint.min().getZ() + hint.max().getZ() + 1) * 0.5D;

            double distSq = (camX - centerX) * (camX - centerX) + (camY - centerY) * (camY - centerY) + (camZ - centerZ) * (camZ - centerZ);
            if (distSq > 4096.0D) {
                continue;
            }

            boolean holdingRequired = isHoldingRequiredItem(mainHand, hint.requiredItemId()) || isHoldingRequiredItem(offHand, hint.requiredItemId());

            AABB box = new AABB(
                    hint.min().getX(), hint.min().getY(), hint.min().getZ(),
                    hint.max().getX() + 1, hint.max().getY() + 1, hint.max().getZ() + 1
            ).inflate(0.012D);

            float faceAlpha = holdingRequired ? 0.32F : 0.16F;
            float lineAlpha = holdingRequired ? 1.0F : 0.85F;

            renderBox(poseStack, buffers, box, hint.red(), hint.green(), hint.blue(), faceAlpha, lineAlpha, holdingRequired);

            String text = holdingRequired ? "[+] " + hint.label() : hint.label();
            int textColor = holdingRequired ? 0xFFFFFFFF : hint.textColor();
            renderBoxText(poseStack, camera, minecraft.font, buffers, box, text, textColor);
        }

        buffers.endBatch();
        poseStack.popPose();
    }

    private static boolean isHoldingRequiredItem(ItemStack stack, String requiredItemId) {
        if (stack.isEmpty() || requiredItemId == null || requiredItemId.isBlank()) {
            return false;
        }
        ResourceLocation stackId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (stackId == null) {
            return false;
        }
        String required = requiredItemId.contains(":") ? requiredItemId : "stationarenear:" + requiredItemId;
        return stackId.toString().equalsIgnoreCase(required) || stackId.getPath().equalsIgnoreCase(requiredItemId);
    }

    private static void renderBox(PoseStack poseStack, MultiBufferSource buffers, AABB box, float red, float green, float blue, float alpha, float lineAlpha, boolean selected) {
        VertexConsumer fillConsumer = buffers.getBuffer(RenderType.debugQuads());
        renderFilledBox(poseStack, fillConsumer, box, red, green, blue, alpha);
        VertexConsumer lineConsumer = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, lineConsumer, box, red, green, blue, lineAlpha);
    }

    private static void renderFilledBox(PoseStack poseStack, VertexConsumer consumer, AABB box, float red, float green, float blue, float alpha) {
        Matrix4f matrix = poseStack.last().pose();
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;
        vertex(consumer, matrix, minX, minY, minZ, red, green, blue, alpha); vertex(consumer, matrix, maxX, minY, minZ, red, green, blue, alpha); vertex(consumer, matrix, maxX, minY, maxZ, red, green, blue, alpha); vertex(consumer, matrix, minX, minY, maxZ, red, green, blue, alpha);
        vertex(consumer, matrix, minX, maxY, minZ, red, green, blue, alpha); vertex(consumer, matrix, minX, maxY, maxZ, red, green, blue, alpha); vertex(consumer, matrix, maxX, maxY, maxZ, red, green, blue, alpha); vertex(consumer, matrix, maxX, maxY, minZ, red, green, blue, alpha);
        vertex(consumer, matrix, minX, minY, minZ, red, green, blue, alpha); vertex(consumer, matrix, minX, maxY, minZ, red, green, blue, alpha); vertex(consumer, matrix, maxX, maxY, minZ, red, green, blue, alpha); vertex(consumer, matrix, maxX, minY, minZ, red, green, blue, alpha);
        vertex(consumer, matrix, minX, minY, maxZ, red, green, blue, alpha); vertex(consumer, matrix, maxX, minY, maxZ, red, green, blue, alpha); vertex(consumer, matrix, maxX, maxY, maxZ, red, green, blue, alpha); vertex(consumer, matrix, minX, maxY, maxZ, red, green, blue, alpha);
        vertex(consumer, matrix, minX, minY, minZ, red, green, blue, alpha); vertex(consumer, matrix, minX, minY, maxZ, red, green, blue, alpha); vertex(consumer, matrix, minX, maxY, maxZ, red, green, blue, alpha); vertex(consumer, matrix, minX, maxY, minZ, red, green, blue, alpha);
        vertex(consumer, matrix, maxX, minY, minZ, red, green, blue, alpha); vertex(consumer, matrix, maxX, maxY, minZ, red, green, blue, alpha); vertex(consumer, matrix, maxX, maxY, maxZ, red, green, blue, alpha); vertex(consumer, matrix, maxX, minY, maxZ, red, green, blue, alpha);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, float red, float green, float blue, float alpha) {
        consumer.vertex(matrix, x, y, z).color(red, green, blue, alpha).endVertex();
    }

    private static void renderBoxText(PoseStack poseStack, Camera camera, Font font, MultiBufferSource buffers, AABB box, String text, int color) {
        renderFloatingTextAt(poseStack, camera, font, buffers, (box.minX + box.maxX) * 0.5D, box.maxY + 0.35D, (box.minZ + box.maxZ) * 0.5D, text, color);
    }

    private static void renderFloatingTextAt(PoseStack poseStack, Camera camera, Font font, MultiBufferSource buffers, double x, double y, double z, String text, int color) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(camera.rotation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f matrix = poseStack.last().pose();
        float width = -font.width(text) / 2.0F;
        font.drawInBatch(text, width, 0.0F, color, true, matrix, buffers, Font.DisplayMode.SEE_THROUGH, 0, 15728880);
        poseStack.popPose();
    }
}
