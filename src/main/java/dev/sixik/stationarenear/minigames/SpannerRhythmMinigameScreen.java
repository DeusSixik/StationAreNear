package dev.sixik.stationarenear.minigames;

import com.mojang.blaze3d.platform.NativeImage;
import dev.sixik.unigui.api.core.FrameContext;
import dev.sixik.unigui.api.core.InvalidationFlags;
import dev.sixik.unigui.api.core.UIContext;
import dev.sixik.unigui.api.core.UnityLikeUIScaleProvider;
import dev.sixik.unigui.api.input.KeyCodes;
import dev.sixik.unigui.api.input.KeyboardState;
import dev.sixik.unigui.api.layout.Alignment;
import dev.sixik.unigui.api.layout.LayoutContext;
import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.render.*;
import dev.sixik.unigui.api.widget.Visibility;
import dev.sixik.unigui.api.widget.Widget;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftClipboardService;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftWidgetScreen;
import dev.sixik.unigui.backend.minecraft_impl.UniGuiTextures;
import dev.sixik.unigui.impl.core.DefaultUIContext;
import dev.sixik.unigui.impl.widget.WidgetBase;
import dev.sixik.unigui.widgets.containers.Box;
import dev.sixik.unigui.widgets.containers.StackPanel;
import dev.sixik.unigui.widgets.feedback.OverlayLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;

public final class SpannerRhythmMinigameScreen {
    private static final MutableColor BACKGROUND = MutableColor.rgba(0.006f, 0.009f, 0.014f, 0.94f);

    private SpannerRhythmMinigameScreen() {
    }

    public static void open() {
        open(null);
    }

    public static void open(Runnable completionHook) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen previous = minecraft.screen;

        DefaultUIContext context = new DefaultUIContext(new MinecraftClipboardService());
        UnityLikeUIScaleProvider scale = new UnityLikeUIScaleProvider()
                .referenceResolution(1920.0f, 1080.0f)
                .matchBalanced()
                .scaleRange(0.75f, 6.0f)
                .userScale(2.4f);
        context.scaleProvider(scale);

        Runnable[] closeAction = new Runnable[1];
        SpannerRhythmMinigameWidget minigame = new SpannerRhythmMinigameWidget();
        minigame.onCompleted(() -> {
            if (completionHook != null) completionHook.run();
            if (closeAction[0] != null) closeAction[0].run();
        });
        minigame.onFailed(() -> {
            if (closeAction[0] != null) closeAction[0].run();
        });

        MinecraftWidgetScreen screen = new MinecraftWidgetScreen(Component.literal("Spanner Rhythm Minigame"), root(minigame), context) {
            @Override
            public void onClose() {
                Minecraft.getInstance().setScreen(previous);
            }

            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {

                if(keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    onClose();
                }

                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        };
        closeAction[0] = screen::onClose;
        screen.useContextScale().scaleWithMinecraftGui(false);
        screen.useSdfDefaultFont();
        screen.renderPolicy(UiRenderPolicy.continuous());
        minecraft.setScreen(screen);
    }

    private static Widget root(SpannerRhythmMinigameWidget minigame) {
        StackPanel viewport = new StackPanel();
        viewport.layout(style -> style.sizePercent(100.0f, 100.0f));
        viewport.addChild(backgroundFrame());
        minigame.layout(style -> style.sizePercent(100.0f, 100.0f).align(Alignment.STRETCH, Alignment.STRETCH));
        viewport.addChild(minigame);
        return new OverlayLayer(viewport);
    }

    private static Box backgroundFrame() {
        Box frame = new Box();
        frame.themeEnabled(false);
        frame.backgroundVisible(true);
        frame.borderVisible(false);
        frame.radius(0.0f);
        frame.background(BACKGROUND);
        frame.layout(style -> style.sizePercent(100.0f, 100.0f).align(Alignment.STRETCH, Alignment.STRETCH));
        return frame;
    }

    public static final class SpannerRhythmMinigameWidget extends WidgetBase {
        private static final float HIT_ZONE = 0.22f;
        private static final float MARKER_SPEED = 1.12f;
        private static final int REQUIRED_HITS = 18;
        private static final float MAX_ANGLE = (float) Math.toRadians(62.0f);
        private static final MutableColor WHITE = MutableColor.rgba(1.0f, 1.0f, 1.0f, 1.0f);
        private static final MutableColor ZONE_IDLE = MutableColor.rgba(0.22f, 0.32f, 0.42f, 0.28f);
        private static final MutableColor ZONE_READY = MutableColor.rgba(0.44f, 0.84f, 1.0f, 0.38f);
        private static final MutableColor ZONE_BAD = MutableColor.rgba(1.0f, 0.20f, 0.14f, 0.42f);
        private static final MutableColor BAR_BG = MutableColor.rgba(0.05f, 0.06f, 0.08f, 0.55f);
        private static final MutableColor BAR_FILL = MutableColor.rgba(0.45f, 0.84f, 1.0f, 0.92f);
        private static final TextureHandle LINE_TEXTURE = SpannerTextures.line();
        private static final TextureHandle POINT_TEXTURE = SpannerTextures.point();
        private static final TextureHandle SPANNER_TEXTURE = SpannerTextures.spanner();
        private static final TextureHandle A_TEXTURE = SpannerTextures.a();
        private static final TextureHandle A_HOVERED_TEXTURE = SpannerTextures.aHovered();
        private static final TextureHandle D_TEXTURE = SpannerTextures.d();
        private static final TextureHandle D_HOVERED_TEXTURE = SpannerTextures.dHovered();

        private float marker = 0.5f;
        private float markerDirection = 1.0f;
        private float spannerAngle;
        private float targetAngle;
        private float hitPulse;
        private float mistakePulse;
        private int hits;
        private int misses;
        private Side expectedSide = Side.LEFT;
        private Runnable completedCallback = () -> {};
        private Runnable failedCallback = () -> {};

        public SpannerRhythmMinigameWidget() {
            focusable(true);
        }

        public SpannerRhythmMinigameWidget onCompleted(Runnable callback) {
            completedCallback = callback == null ? () -> {} : callback;
            return this;
        }

        public SpannerRhythmMinigameWidget onFailed(Runnable callback) {
            failedCallback = callback == null ? () -> {} : callback;
            return this;
        }

        public void resetGame() {
            marker = 0.5f;
            markerDirection = 1.0f;
            spannerAngle = 0.0f;
            targetAngle = 0.0f;
            hitPulse = 0.0f;
            mistakePulse = 0.0f;
            hits = 0;
            misses = 0;
            expectedSide = Side.LEFT;
            invalidate(InvalidationFlags.VISUAL);
        }

        @Override
        public void measure(LayoutContext context) {
            setDesiredSize(resolveDesiredSize(context, 760.0f, 430.0f));
        }

        @Override
        public void tick(FrameContext frame) {
            if (visibility() != Visibility.VISIBLE) return;
            super.tick(frame);

            float dt = frame == null || frame.deltaSeconds() <= 0.0f ? 1.0f / 60.0f : frame.deltaSeconds();
            marker += markerDirection * MARKER_SPEED * dt;
            if (marker >= 1.0f) {
                marker = 1.0f;
                markerDirection = -1.0f;
            } else if (marker <= 0.0f) {
                marker = 0.0f;
                markerDirection = 1.0f;
            }

            UIContext context = uiContext();
            KeyboardState keyboard = context == null ? KeyboardState.NONE : context.keyboard();
            if (keyboard.wasPressed(KeyCodes.A)) {
                press(Side.LEFT);
            }
            if (keyboard.wasPressed(KeyCodes.D)) {
                press(Side.RIGHT);
            }

            hitPulse = Math.max(0.0f, hitPulse - dt * 4.5f);
            mistakePulse = Math.max(0.0f, mistakePulse - dt * 3.5f);
            spannerAngle += (targetAngle - spannerAngle) * Math.min(1.0f, dt * 14.0f);
            invalidate(InvalidationFlags.VISUAL);
        }

        @Override
        public void render(RenderContext context) {
            if (visibility() != Visibility.VISIBLE || context == null) return;
            if (layoutBounds().width() <= 0.0f || layoutBounds().height() <= 0.0f) return;

            pushOpacity(context);
            DrawScope draw = new DrawScope(context, transform(), layoutBounds());
            try {
                renderPlayfield(draw);
            } finally {
                popOpacity(context);
            }
        }

        private void press(Side side) {
            boolean correct = side == expectedSide && isMarkerInside(side);
            if (correct) {
                hits++;
                expectedSide = expectedSide.opposite();
                targetAngle = side == Side.LEFT ? -MAX_ANGLE : MAX_ANGLE;
                hitPulse = 1.0f;
                mistakePulse = 0.0f;
                if (hits >= REQUIRED_HITS) {
                    completedCallback.run();
                }
            } else {
                hits = Math.max(0, hits - 1);
                misses++;
                targetAngle = side == Side.LEFT ? MAX_ANGLE * 0.32f : -MAX_ANGLE * 0.32f;
                mistakePulse = 1.0f;
                if (misses >= 3) {
                    failedCallback.run();
                }
            }
            invalidate(InvalidationFlags.VISUAL);
        }

        private boolean isMarkerInside(Side side) {
            return side == Side.LEFT ? marker <= HIT_ZONE : marker >= 1.0f - HIT_ZONE;
        }

        private void renderPlayfield(DrawScope draw) {
            float cx = layoutBounds().x() + layoutBounds().width() * 0.5f;
            float lineW = lineWidth();
            float lineH = lineHeight();
            float lineX = cx - lineW * 0.5f;
            float lineY = layoutBounds().y() + layoutBounds().height() * 0.58f;
            float zoneW = lineW * HIT_ZONE;

            renderSpanner(draw, cx, lineY - scale() * 13.5f);
            renderZone(draw, lineX, lineY - scale() * 3.0f, zoneW, lineH + scale() * 6.0f, Side.LEFT);
            renderZone(draw, lineX + lineW - zoneW, lineY - scale() * 3.0f, zoneW, lineH + scale() * 6.0f, Side.RIGHT);
            renderLine(draw, lineX, lineY, lineW, lineH);
            renderMarker(draw, lineX, lineY, lineW, lineH);
            renderKeys(draw, lineX, lineY, lineW);
            renderProgress(draw, cx, lineY + scale() * 31.0f, lineW * 0.62f);
        }

        private void renderZone(DrawScope draw, float x, float y, float w, float h, Side side) {
            boolean ready = side == expectedSide;
            MutableColor color = mistakePulse > 0.0f && ready
                    ? ZONE_BAD
                    : ready ? ZONE_READY : ZONE_IDLE;
            draw.addRectFilled(x, y, w, h, scale() * 1.5f, color);
        }

        private void renderLine(DrawScope draw, float x, float y, float w, float h) {
            if (LINE_TEXTURE != null) {
                draw.addImage(LINE_TEXTURE, x, y, w, h, WHITE);
            } else {
                draw.addRectFilled(x, y, w, h, scale(), MutableColor.rgba(0.36f, 0.40f, 0.48f, 1.0f));
                draw.addRect(x, y, w, h, scale(), MutableColor.rgba(0.12f, 0.14f, 0.18f, 1.0f), Math.max(1.0f, scale() * 0.25f));
            }
        }

        private void renderMarker(DrawScope draw, float lineX, float lineY, float lineW, float lineH) {
            float pointW = pointWidth();
            float pointH = pointHeight();
            float x = lineX + marker * (lineW - pointW);
            float y = lineY + lineH * 0.5f - pointH * 0.5f;
            if (POINT_TEXTURE != null) {
                draw.addImage(POINT_TEXTURE, x, y, pointW, pointH, WHITE);
            } else {
                draw.addRectFilled(x, y, pointW, pointH, scale() * 0.5f, BAR_FILL);
            }
        }

        private void renderKeys(DrawScope draw, float lineX, float lineY, float lineW) {
            float keySize = 16.0f * scale();
            float y = lineY + scale() * 14.0f;
            TextureHandle left = expectedSide == Side.LEFT ? A_HOVERED_TEXTURE : A_TEXTURE;
            TextureHandle right = expectedSide == Side.RIGHT ? D_HOVERED_TEXTURE : D_TEXTURE;
            drawKey(draw, left, lineX - keySize * 0.25f, y, keySize, "A", expectedSide == Side.LEFT);
            drawKey(draw, right, lineX + lineW - keySize * 0.75f, y, keySize, "D", expectedSide == Side.RIGHT);
        }

        private void drawKey(DrawScope draw, TextureHandle texture, float x, float y, float size, String label, boolean active) {
            if (texture != null) {
                draw.addImage(texture, x, y, size, size, WHITE);
            } else {
                draw.addRectFilled(x, y, size, size, scale() * 1.5f, active ? ZONE_READY : ZONE_IDLE);
                draw.addText(label, x, y + size * 0.18f, size, size * 0.66f, WHITE);
            }
        }

        private void renderSpanner(DrawScope draw, float centerX, float centerY) {
            float pulseOffset = hitPulse > 0.0f ? (float) Math.sin(hitPulse * Math.PI) * scale() * 1.8f : 0.0f;
            float angle = spannerAngle + (mistakePulse > 0.0f ? (float) Math.sin(mistakePulse * 42.0f) * 0.08f : 0.0f);
            if (SPANNER_TEXTURE != null) {
                drawRotatedImage(draw, SPANNER_TEXTURE, centerX, centerY - pulseOffset,
                        SPANNER_TEXTURE.width() * spannerScale(),
                        SPANNER_TEXTURE.height() * spannerScale(), angle);
            } else {
                float len = 24.0f * spannerScale();
                float thick = 5.0f * spannerScale();
                DrawPoint a = rotated(centerX, centerY, angle, -len * 0.5f, -thick * 0.5f);
                DrawPoint b = rotated(centerX, centerY, angle, len * 0.5f, -thick * 0.5f);
                DrawPoint c = rotated(centerX, centerY, angle, len * 0.5f, thick * 0.5f);
                DrawPoint d = rotated(centerX, centerY, angle, -len * 0.5f, thick * 0.5f);
                draw.addQuadFilled(a, b, c, d, MutableColor.rgba(0.68f, 0.74f, 0.82f, 1.0f));
            }
        }

        private void renderProgress(DrawScope draw, float centerX, float y, float width) {
            float height = Math.max(3.0f, scale() * 0.7f);
            float x = centerX - width * 0.5f;
            draw.addRectFilled(x, y, width, height, height * 0.5f, BAR_BG);
            draw.addRectFilled(x, y, width * hits / REQUIRED_HITS, height, height * 0.5f, BAR_FILL);
        }

        private void drawRotatedImage(DrawScope draw, TextureHandle texture, float centerX, float centerY,
                                      float width, float height, float angle) {
            float hw = width * 0.5f;
            float hh = height * 0.5f;
            draw.addImageQuad(texture,
                    rotated(centerX, centerY, angle, -hw, -hh),
                    rotated(centerX, centerY, angle, hw, -hh),
                    rotated(centerX, centerY, angle, hw, hh),
                    rotated(centerX, centerY, angle, -hw, hh),
                    new DrawPoint(0.0f, 0.0f), new DrawPoint(1.0f, 0.0f),
                    new DrawPoint(1.0f, 1.0f), new DrawPoint(0.0f, 1.0f), WHITE);
        }

        private DrawPoint rotated(float originX, float originY, float angle, float localX, float localY) {
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            return new DrawPoint(originX + localX * cos - localY * sin, originY + localX * sin + localY * cos);
        }

        private float lineWidth() {
            return (LINE_TEXTURE == null ? 80.0f : LINE_TEXTURE.width()) * scale();
        }

        private float lineHeight() {
            return (LINE_TEXTURE == null ? 5.0f : LINE_TEXTURE.height()) * scale();
        }

        private float pointWidth() {
            return (POINT_TEXTURE == null ? 4.0f : POINT_TEXTURE.width()) * scale();
        }

        private float pointHeight() {
            return (POINT_TEXTURE == null ? 9.0f : POINT_TEXTURE.height()) * scale();
        }

        private float spannerScale() {
            return scale() * 1.15f;
        }

        private float scale() {
            float maxW = layoutBounds().width() * 0.58f / 80.0f;
            float maxH = layoutBounds().height() * 0.32f / 42.0f;
            return Math.max(3.0f, Math.min(9.0f, Math.min(maxW, maxH)));
        }

        private enum Side {
            LEFT,
            RIGHT;

            private Side opposite() {
                return this == LEFT ? RIGHT : LEFT;
            }
        }
    }

    private static final class SpannerTextures {
        private static TextureHandle line;
        private static TextureHandle point;
        private static TextureHandle spanner;
        private static TextureHandle a;
        private static TextureHandle aHovered;
        private static TextureHandle d;
        private static TextureHandle dHovered;
        private static boolean loadFailed;

        private SpannerTextures() {
        }

        private static TextureHandle line() {
            ensureLoaded();
            return line;
        }

        private static TextureHandle point() {
            ensureLoaded();
            return point;
        }

        private static TextureHandle spanner() {
            ensureLoaded();
            return spanner;
        }

        private static TextureHandle a() {
            ensureLoaded();
            return a;
        }

        private static TextureHandle aHovered() {
            ensureLoaded();
            return aHovered;
        }

        private static TextureHandle d() {
            ensureLoaded();
            return d;
        }

        private static TextureHandle dHovered() {
            ensureLoaded();
            return dHovered;
        }

        private static void ensureLoaded() {
            if (line != null || loadFailed) return;
            try {
                line = load("line", "line.png");
                point = load("point", "point.png");
                spanner = load("spanner", "spanner.png");
                a = load("a", "A.png");
                aHovered = load("a_hovered", "A_hovered.png");
                d = load("d", "D.png");
                dHovered = load("d_hovered", "D_hovered.png");
            } catch (IOException | RuntimeException failure) {
                loadFailed = true;
            }
        }

        private static TextureHandle load(String id, String fileName) throws IOException {
            String resource = "assets/stationarenear/textures/gui/spanner/" + fileName;
            ClassLoader loader = SpannerRhythmMinigameScreen.class.getClassLoader();
            try (InputStream stream = loader.getResourceAsStream(resource)) {
                if (stream == null) throw new IOException("Missing spanner texture resource: " + resource);
                return UniGuiTextures.replace("stationarenear:dynamic/spanner/" + id,
                        NativeImage.read(stream),
                        TextureOptions.nearest());
            }
        }
    }
}