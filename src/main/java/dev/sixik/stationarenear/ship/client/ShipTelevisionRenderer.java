package dev.sixik.stationarenear.ship.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.sixik.stationarenear.ship.block.ShipTelevisionBlock;
import dev.sixik.stationarenear.ship.block.entity.ShipTelevisionBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class ShipTelevisionRenderer implements BlockEntityRenderer<ShipTelevisionBlockEntity> {

    private static final float SCREEN_Z = 13.74F / 16.0F;
    private static final float TEXT_Z_OFFSET = -0.01F;
    private static final float SCREEN_LEFT = -14.0F / 16.0F;
    private static final float SCREEN_RIGHT = 30.0F / 16.0F;
    private static final float SCREEN_TOP = 30.0F / 16.0F;
    private static final float SCREEN_BOTTOM = 4.0F / 16.0F;
    private static final float SCREEN_CENTER_X = (SCREEN_LEFT + SCREEN_RIGHT) * 0.5F;
    private static final float SCREEN_CANVAS_WIDTH = 260.0F;
    private static final float TEXT_SCALE = (SCREEN_RIGHT - SCREEN_LEFT) / SCREEN_CANVAS_WIDTH;
    private static final int SCREEN_PADDING_X = 10;
    private static final int SCREEN_PADDING_TOP = 10;
    private static final int SCREEN_PADDING_BOTTOM = 10;
    private static final int LINE_HEIGHT = 11;
    private static final int BACKGROUND_COLOR = 0xEE000000;
    private static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;
    private static final List<Component> DEFAULT_LINES = List.of(
            Component.literal("STATION ARE NEAR"),
            Component.literal("SHIP TV ONLINE"),
            Component.literal("OBJECTIVES: NONE"),
            Component.literal("PRESSURE: STABLE"),
            Component.literal("SIGNAL: TEST MODE")
    );

    private final Font font;

    public ShipTelevisionRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(ShipTelevisionBlockEntity television, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState state = television.getBlockState();
        if (!ShipTelevisionBlock.isMaster(state) || !state.hasProperty(ShipTelevisionBlock.FACING)) {
            return;
        }

        poseStack.pushPose();
        rotateToFacing(poseStack, state.getValue(ShipTelevisionBlock.FACING));
        renderBackground(poseStack, bufferSource);
        renderScreenText(television, poseStack, bufferSource);
        poseStack.popPose();
    }

    private void renderScreenText(ShipTelevisionBlockEntity television, PoseStack poseStack, MultiBufferSource bufferSource) {
        float textScale = television.textScale();
        float effectiveScale = TEXT_SCALE * textScale;
        float canvasWidth = SCREEN_CANVAS_WIDTH / textScale;
        poseStack.translate(SCREEN_CENTER_X, SCREEN_TOP, SCREEN_Z + TEXT_Z_OFFSET);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(effectiveScale, -effectiveScale, effectiveScale);

        Matrix4f matrix = poseStack.last().pose();
        int maxWidth = Math.round(canvasWidth - SCREEN_PADDING_X * 2.0F);
        int screenHeight = screenCanvasHeight(textScale);
        int viewportTop = SCREEN_PADDING_TOP;
        int viewportBottom = screenHeight - SCREEN_PADDING_BOTTOM;
        List<TelevisionLine> lines = wrappedLines(television, maxWidth);
        int totalHeight = lines.size() * LINE_HEIGHT;
        int y = textStartY(television.textPosition(), viewportTop, viewportBottom, totalHeight);

        for (TelevisionLine line : lines) {
            if (y + LINE_HEIGHT >= viewportTop && y <= viewportBottom - LINE_HEIGHT) {
                drawCentered(bufferSource, matrix, line.text(), y, line.color());
            }
            y += LINE_HEIGHT;
        }
    }

    private static int textStartY(ShipTelevisionBlockEntity.TelevisionTextPosition position, int viewportTop, int viewportBottom, int totalHeight) {
        int viewportHeight = viewportBottom - viewportTop;
        if (totalHeight > viewportHeight) {
            return viewportBottom - totalHeight;
        }
        return switch (position) {
            case TOP -> viewportTop;
            case DOWN -> viewportBottom - totalHeight;
            case CENTER -> viewportTop + (viewportHeight - totalHeight) / 2;
        };
    }

    private List<TelevisionLine> wrappedLines(ShipTelevisionBlockEntity television, int maxWidth) {
        List<TelevisionLine> lines = new ArrayList<>();
        String customText = television.text();
        if (customText == null || customText.isBlank()) {
            for (int i = 0; i < DEFAULT_LINES.size(); i++) {
                int color = i == 0 ? 0xFF66FF99 : 0xFF24E875;
                addWrappedLine(lines, DEFAULT_LINES.get(i), maxWidth, color);
            }
            return lines;
        }

        String[] rawLines = customText.replace("\r", "").split("\\n|\n", -1);
        for (String rawLine : rawLines) {
            addWrappedLine(lines, Component.literal(rawLine), maxWidth, 0xFF24E875);
        }
        return lines;
    }

    private void addWrappedLine(List<TelevisionLine> lines, Component text, int maxWidth, int color) {
        if (text.getString().isEmpty()) {
            lines.add(new TelevisionLine(FormattedCharSequence.EMPTY, color));
            return;
        }
        for (FormattedCharSequence splitLine : font.split(text, maxWidth)) {
            lines.add(new TelevisionLine(splitLine, color));
        }
    }

    private void drawCentered(MultiBufferSource bufferSource, Matrix4f matrix, FormattedCharSequence text, int y, int color) {
        float x = -font.width(text) / 2.0F;
        font.drawInBatch(text, x, y, color, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
    }

    private static int screenCanvasHeight(float textScale) {
        return Math.round((SCREEN_TOP - SCREEN_BOTTOM) / (TEXT_SCALE * textScale));
    }

    private static void renderBackground(PoseStack poseStack, MultiBufferSource bufferSource) {
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugQuads());
        int alpha = BACKGROUND_COLOR >>> 24 & 0xFF;
        int red = BACKGROUND_COLOR >>> 16 & 0xFF;
        int green = BACKGROUND_COLOR >>> 8 & 0xFF;
        int blue = BACKGROUND_COLOR & 0xFF;
        consumer.vertex(matrix, SCREEN_LEFT, SCREEN_BOTTOM, SCREEN_Z).color(red, green, blue, alpha).endVertex();
        consumer.vertex(matrix, SCREEN_RIGHT, SCREEN_BOTTOM, SCREEN_Z).color(red, green, blue, alpha).endVertex();
        consumer.vertex(matrix, SCREEN_RIGHT, SCREEN_TOP, SCREEN_Z).color(red, green, blue, alpha).endVertex();
        consumer.vertex(matrix, SCREEN_LEFT, SCREEN_TOP, SCREEN_Z).color(red, green, blue, alpha).endVertex();
    }

    private static void rotateToFacing(PoseStack poseStack, Direction facing) {
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        }));
        poseStack.translate(-0.5D, 0.0D, -0.5D);
    }

    private record TelevisionLine(FormattedCharSequence text, int color) {
    }
}
