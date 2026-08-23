package dev.sixik.stationarenear.structures.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sixik.stationarenear.structures.data.TemplateSelectionEntry;
import dev.sixik.stationarenear.structures.editor.StationEditorNodeType;
import dev.sixik.stationarenear.structures.editor.StationStructureEditorStick;
import dev.sixik.stationarenear.structures.item.StationStructureToolItem;
import dev.sixik.stationarenear.structures.util.NbtPos;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class StationStructureWorldRenderer {

    private StationStructureWorldRenderer() {
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        CompoundTag editorTag = StationEditorClientState.editorTag();
        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        double camX = camera.getPosition().x;
        double camY = camera.getPosition().y;
        double camZ = camera.getPosition().z;

        poseStack.pushPose();
        poseStack.translate(-camX, -camY, -camZ);

        if (canRenderTemplateSelections(minecraft)) {
            for (TemplateSelectionEntry entry : StationEditorClientState.templateSelections()) {
                if (entry.hasBounds() && StationEditorClientState.templateSelectionVisible(entry.template())) {
                    AABB templateBox = boxFromBounds(entry.bounds()).inflate(0.018D);
                    if (!isTemplateSelectionInLoadedView(minecraft, camera, templateBox)) {
                        continue;
                    }
                    renderBox(poseStack, buffers, templateBox, 0.65F, 0.15F, 1.0F, 0.10F, 0.9F, false);
                    renderBoxText(poseStack, camera, minecraft.font, buffers, templateBox, "Template: " + entry.template().getPath(), 0xFFCC88FF);
                }
            }
        }

        if (editorTag == null || !editorTag.contains(StationStructureToolItem.KEY_POS_1) || !editorTag.contains(StationStructureToolItem.KEY_POS_2)) {
            buffers.endBatch();
            poseStack.popPose();
            return;
        }

        BlockPos structureMin = StationEditorClientState.structureMin();
        BlockPos structureMax = StationEditorClientState.structureMax();
        AABB structureBox = boxFromInclusive(structureMin, structureMax).inflate(0.005D);
        renderBox(poseStack, buffers, structureBox, 1.0F, 0.15F, 0.10F, 0.18F, 1.0F, StationEditorClientState.selectedKey().equals("root"));
        if (StationEditorClientState.showRootText()) {
            renderBoxText(poseStack, camera, minecraft.font, buffers, structureBox, "Root Structure Zone", 0xFFFF5555);
        }
        if (StationEditorClientState.showHandles()) {
            renderHandle(poseStack, buffers, camera, minecraft.font, structureMin, "POS_1", 0.0F, 1.0F, 0.0F, 0xFF55FF55);
            renderHandle(poseStack, buffers, camera, minecraft.font, structureMax, "POS_2", 0.0F, 0.45F, 1.0F, 0xFF55AAFF);
        }

        if (editorTag.contains(StationStructureEditorStick.KEY_TRIGGER_DRAFT_POS_1)) {
            renderHandle(poseStack, buffers, camera, minecraft.font, NbtPos.load(editorTag.getCompound(StationStructureEditorStick.KEY_TRIGGER_DRAFT_POS_1)), "TRIGGER POS_1", 1.0F, 0.85F, 0.0F, 0xFFFFFF55);
        }
        if (editorTag.contains(StationStructureEditorStick.KEY_TRIGGER_DRAFT_POS_2)) {
            renderHandle(poseStack, buffers, camera, minecraft.font, NbtPos.load(editorTag.getCompound(StationStructureEditorStick.KEY_TRIGGER_DRAFT_POS_2)), "TRIGGER POS_2", 1.0F, 0.55F, 0.0F, 0xFFFFAA33);
        }
        if (editorTag.contains(StationStructureEditorStick.KEY_CONNECTION_DRAFT_POS_1)) {
            renderHandle(poseStack, buffers, camera, minecraft.font, NbtPos.load(editorTag.getCompound(StationStructureEditorStick.KEY_CONNECTION_DRAFT_POS_1)), "CONNECTION POS_1", 0.20F, 0.85F, 1.0F, 0xFF55DDFF);
        }
        if (editorTag.contains(StationStructureEditorStick.KEY_CONNECTION_DRAFT_POS_2)) {
            renderHandle(poseStack, buffers, camera, minecraft.font, NbtPos.load(editorTag.getCompound(StationStructureEditorStick.KEY_CONNECTION_DRAFT_POS_2)), "CONNECTION POS_2", 0.0F, 0.55F, 1.0F, 0xFF33AAFF);
        }
        if (editorTag.contains(StationStructureEditorStick.KEY_CONNECTION_DRAFT_POS_1) && editorTag.contains(StationStructureEditorStick.KEY_CONNECTION_DRAFT_POS_2)) {
            BlockPos draftA = NbtPos.load(editorTag.getCompound(StationStructureEditorStick.KEY_CONNECTION_DRAFT_POS_1));
            BlockPos draftB = NbtPos.load(editorTag.getCompound(StationStructureEditorStick.KEY_CONNECTION_DRAFT_POS_2));
            BlockPos draftMin = new BlockPos(Math.min(draftA.getX(), draftB.getX()), Math.min(draftA.getY(), draftB.getY()), Math.min(draftA.getZ(), draftB.getZ()));
            BlockPos draftMax = new BlockPos(Math.max(draftA.getX(), draftB.getX()), Math.max(draftA.getY(), draftB.getY()), Math.max(draftA.getZ(), draftB.getZ()));
            AABB draftBox = boxFromInclusive(draftMin, draftMax).inflate(0.012D);
            renderBox(poseStack, buffers, draftBox, 0.10F, 0.70F, 1.0F, 0.18F, 1.0F, StationEditorClientState.selectedKey().equals("connection_draft"));
            renderBoxText(poseStack, camera, minecraft.font, buffers, draftBox, "Connection Draft", 0xFF55DDFF);
        }

        ListTag connectors = editorTag.getList(StationStructureToolItem.KEY_CONNECTORS, Tag.TAG_COMPOUND);
        for (int i = 0; i < connectors.size(); i++) {
            CompoundTag connector = connectors.getCompound(i);
            BlockPos min = NbtPos.load(connector.getCompound("worldMin"));
            BlockPos max = NbtPos.load(connector.getCompound("worldMax"));
            BlockPos anchor = NbtPos.load(connector.getCompound("worldPosition"));
            String key = "connection:" + i;
            boolean selected = StationEditorClientState.selectedKey().equals(key);
            Direction direction = Direction.byName(connector.getString("direction"));
            if (direction == null) {
                direction = Direction.NORTH;
            }
            AABB connectionBox = boxFromInclusive(min, max).inflate(0.012D);
            renderBox(poseStack, buffers, connectionBox, 0.15F, 0.55F, 1.0F, 0.22F, 1.0F, selected);
            renderBox(poseStack, buffers, new AABB(anchor).inflate(0.035D), 0.15F, 0.85F, 1.0F, 0.22F, 1.0F, false);
            renderDirectionArrow(poseStack, buffers, connectionBox, direction, selected);
            renderBoxText(poseStack, camera, minecraft.font, buffers, connectionBox, "Connection: " + connector.getString("name"), 0xFF55AAFF);
        }

        ListTag triggers = editorTag.getList(StationStructureToolItem.KEY_TRIGGER_ZONES, Tag.TAG_COMPOUND);
        for (int i = 0; i < triggers.size(); i++) {
            CompoundTag trigger = triggers.getCompound(i);
            BlockPos min = NbtPos.load(trigger.getCompound("worldMin"));
            BlockPos max = NbtPos.load(trigger.getCompound("worldMax"));
            String key = "trigger:" + i;
            TriggerRenderColor color = triggerColor(trigger.getString("nodeType"));
            AABB triggerBox = boxFromInclusive(min, max).inflate(0.01D);
            renderBox(poseStack, buffers, triggerBox, color.red(), color.green(), color.blue(), 0.18F, 1.0F, StationEditorClientState.selectedKey().equals(key));
            renderBoxText(poseStack, camera, minecraft.font, buffers, triggerBox, trigger.getString("nodeType") + ": " + trigger.getString("id"), color.textColor());
        }

        buffers.endBatch();
        poseStack.popPose();
    }

    private static AABB boxFromInclusive(BlockPos min, BlockPos max) {
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1.0D, max.getY() + 1.0D, max.getZ() + 1.0D);
    }

    private static AABB boxFromBounds(net.minecraft.world.level.levelgen.structure.BoundingBox bounds) {
        return new AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX() + 1.0D, bounds.maxY() + 1.0D, bounds.maxZ() + 1.0D);
    }

    private static boolean canRenderTemplateSelections(Minecraft minecraft) {
        return minecraft.player != null && (minecraft.player.isCreative() || minecraft.player.isSpectator());
    }

    private static boolean isTemplateSelectionInLoadedView(Minecraft minecraft, Camera camera, AABB box) {
        if (minecraft.level == null) {
            return false;
        }

        BlockPos center = BlockPos.containing((box.minX + box.maxX) * 0.5D, (box.minY + box.maxY) * 0.5D, (box.minZ + box.maxZ) * 0.5D);
        if (!minecraft.level.isLoaded(center)) {
            return false;
        }

        double renderDistance = Math.max(16.0D, minecraft.options.getEffectiveRenderDistance() * 16.0D);
        double closestX = clamp(camera.getPosition().x, box.minX, box.maxX);
        double closestZ = clamp(camera.getPosition().z, box.minZ, box.maxZ);
        double deltaX = camera.getPosition().x - closestX;
        double deltaZ = camera.getPosition().z - closestZ;
        return deltaX * deltaX + deltaZ * deltaZ <= renderDistance * renderDistance;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static TriggerRenderColor triggerColor(String nodeTypeName) {
        StationEditorNodeType nodeType;
        try {
            nodeType = StationEditorNodeType.valueOf(nodeTypeName);
        } catch (IllegalArgumentException exception) {
            nodeType = StationEditorNodeType.TRIGGER;
        }
        return colorFromEnumIndex(nodeType.ordinal());
    }

    private static TriggerRenderColor colorFromEnumIndex(int index) {
        float hue = (index * 0.61803398875F) % 1.0F;
        float saturation = 0.72F;
        float value = 1.0F;
        int sector = (int) (hue * 6.0F);
        float fraction = hue * 6.0F - sector;
        float p = value * (1.0F - saturation);
        float q = value * (1.0F - fraction * saturation);
        float t = value * (1.0F - (1.0F - fraction) * saturation);
        float red;
        float green;
        float blue;
        switch (sector % 6) {
            case 0 -> { red = value; green = t; blue = p; }
            case 1 -> { red = q; green = value; blue = p; }
            case 2 -> { red = p; green = value; blue = t; }
            case 3 -> { red = p; green = q; blue = value; }
            case 4 -> { red = t; green = p; blue = value; }
            default -> { red = value; green = p; blue = q; }
        }
        int textColor = 0xFF000000
                | (((int) (red * 255.0F)) << 16)
                | (((int) (green * 255.0F)) << 8)
                | ((int) (blue * 255.0F));
        return new TriggerRenderColor(red, green, blue, textColor);
    }

    private record TriggerRenderColor(float red, float green, float blue, int textColor) {
    }

    private static void renderBox(PoseStack poseStack, MultiBufferSource buffers, AABB box, float red, float green, float blue, float alpha, float lineAlpha, boolean selected) {
        VertexConsumer fillConsumer = buffers.getBuffer(RenderType.debugQuads());
        renderFilledBox(poseStack, fillConsumer, box, red, green, blue, selected ? Math.min(0.36F, alpha * 1.8F) : alpha);
        VertexConsumer lineConsumer = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, lineConsumer, box, red, green, blue, selected ? 1.0F : lineAlpha);
    }

    private static void renderHandle(PoseStack poseStack, MultiBufferSource buffers, Camera camera, Font font, BlockPos pos, String label, float red, float green, float blue, int textColor) {
        renderBox(poseStack, buffers, new AABB(pos).inflate(0.035D), red, green, blue, 0.28F, 1.0F, true);
        renderFloatingText(poseStack, camera, font, buffers, pos, label, textColor);
    }

    private static void renderDirectionArrow(PoseStack poseStack, MultiBufferSource buffers, AABB box, Direction direction, boolean selected) {
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerY = (box.minY + box.maxY) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        double dirX = direction.getStepX();
        double dirY = direction.getStepY();
        double dirZ = direction.getStepZ();
        double size = Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize()));
        double length = Math.max(1.0D, size * 0.65D);
        double endX = centerX + dirX * length;
        double endY = centerY + dirY * length;
        double endZ = centerZ + dirZ * length;
        double baseX = endX - dirX * 0.32D;
        double baseY = endY - dirY * 0.32D;
        double baseZ = endZ - dirZ * 0.32D;
        float red = selected ? 1.0F : 0.25F;
        float green = selected ? 0.95F : 0.85F;
        float blue = selected ? 0.20F : 1.0F;
        VertexConsumer lineConsumer = buffers.getBuffer(RenderType.lines());
        renderLine(poseStack, lineConsumer, centerX, centerY, centerZ, endX, endY, endZ, red, green, blue, 1.0F);
        double fin = 0.24D;
        if (dirX != 0.0D) {
            renderLine(poseStack, lineConsumer, endX, endY, endZ, baseX, baseY + fin, baseZ, red, green, blue, 1.0F);
            renderLine(poseStack, lineConsumer, endX, endY, endZ, baseX, baseY - fin, baseZ, red, green, blue, 1.0F);
            renderLine(poseStack, lineConsumer, endX, endY, endZ, baseX, baseY, baseZ + fin, red, green, blue, 1.0F);
            renderLine(poseStack, lineConsumer, endX, endY, endZ, baseX, baseY, baseZ - fin, red, green, blue, 1.0F);
        } else if (dirY != 0.0D) {
            renderLine(poseStack, lineConsumer, endX, endY, endZ, baseX + fin, baseY, baseZ, red, green, blue, 1.0F);
            renderLine(poseStack, lineConsumer, endX, endY, endZ, baseX - fin, baseY, baseZ, red, green, blue, 1.0F);
            renderLine(poseStack, lineConsumer, endX, endY, endZ, baseX, baseY, baseZ + fin, red, green, blue, 1.0F);
            renderLine(poseStack, lineConsumer, endX, endY, endZ, baseX, baseY, baseZ - fin, red, green, blue, 1.0F);
        } else {
            renderLine(poseStack, lineConsumer, endX, endY, endZ, baseX + fin, baseY, baseZ, red, green, blue, 1.0F);
            renderLine(poseStack, lineConsumer, endX, endY, endZ, baseX - fin, baseY, baseZ, red, green, blue, 1.0F);
            renderLine(poseStack, lineConsumer, endX, endY, endZ, baseX, baseY + fin, baseZ, red, green, blue, 1.0F);
            renderLine(poseStack, lineConsumer, endX, endY, endZ, baseX, baseY - fin, baseZ, red, green, blue, 1.0F);
        }
    }

    private static void renderLine(PoseStack poseStack, VertexConsumer consumer, double x1, double y1, double z1, double x2, double y2, double z2, float red, float green, float blue, float alpha) {
        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 0.0001D) {
            return;
        }
        float normalX = (float) (dx / length);
        float normalY = (float) (dy / length);
        float normalZ = (float) (dz / length);
        consumer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(red, green, blue, alpha).normal(normal, normalX, normalY, normalZ).endVertex();
        consumer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(red, green, blue, alpha).normal(normal, normalX, normalY, normalZ).endVertex();
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
        renderFloatingTextAt(poseStack, camera, font, buffers, (box.minX + box.maxX) * 0.5D, (box.minY + box.maxY) * 0.5D, (box.minZ + box.maxZ) * 0.5D, text, color);
    }

    private static void renderFloatingText(PoseStack poseStack, Camera camera, Font font, MultiBufferSource buffers, BlockPos pos, String text, int color) {
        renderFloatingTextAt(poseStack, camera, font, buffers, pos.getX() + 0.5D, pos.getY() + 1.2D, pos.getZ() + 0.5D, text, color);
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
