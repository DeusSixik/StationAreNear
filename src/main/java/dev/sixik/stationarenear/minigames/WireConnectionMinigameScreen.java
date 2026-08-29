package dev.sixik.stationarenear.minigames;

import com.mojang.blaze3d.platform.NativeImage;
import dev.sixik.unigui.api.core.FrameContext;
import dev.sixik.unigui.api.core.InvalidationFlags;
import dev.sixik.unigui.api.core.UIContext;
import dev.sixik.unigui.api.core.UnityLikeUIScaleProvider;
import dev.sixik.unigui.api.event.*;
import dev.sixik.unigui.api.input.MouseCursor;
import dev.sixik.unigui.api.input.PointerButton;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class WireConnectionMinigameScreen {
    private static final MutableColor BACKGROUND = MutableColor.rgba(0.006f, 0.009f, 0.014f, 0.94f);
    private static final MutableColor BORDER = MutableColor.rgba(0.22f, 0.34f, 0.52f, 0.76f);

    private WireConnectionMinigameScreen() {
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
        WireConnectionMinigameWidget minigame = new WireConnectionMinigameWidget();
        minigame.onCompleted(() -> {
            if (completionHook != null) completionHook.run();
            if (closeAction[0] != null) closeAction[0].run();
        });

        MinecraftWidgetScreen screen = new MinecraftWidgetScreen(Component.literal("Wire Connection Minigame"), root(minigame), context) {
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

    private static Widget root(WireConnectionMinigameWidget minigame) {
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

    public static final class WireConnectionMinigameWidget extends WidgetBase {
        private static final int WIRE_COUNT = 4;
        private static final MutableColor WHITE = MutableColor.rgba(1.0f, 1.0f, 1.0f, 1.0f);
        private static final MutableColor SHADOW = MutableColor.rgba(0.0f, 0.0f, 0.0f, 0.42f);
        private static final MutableColor SOCKET_SHADOW = MutableColor.rgba(0.0f, 0.0f, 0.0f, 0.36f);
        private static final MutableColor WARNING = MutableColor.rgba(1.00f, 0.34f, 0.24f, 1.0f);
        private static final TextureHandle BOX_TEXTURE = CableTextures.box();
        private static final TextureHandle INPUT_TEXTURE = CableTextures.input();
        private static final TextureHandle CONNECTOR_BG_TEXTURE = CableTextures.connectorBg();
        private static final WireSpec[] WIRES = {
                wire("red", MutableColor.rgba(1.00f, 0.22f, 0.18f, 1.0f)),
                wire("blue", MutableColor.rgba(0.22f, 0.56f, 1.00f, 1.0f)),
                wire("green", MutableColor.rgba(0.24f, 0.86f, 0.42f, 1.0f)),
                wire("white", MutableColor.rgba(0.88f, 0.92f, 0.95f, 1.0f))
        };

        private final int[] leftWireBySlot = new int[WIRE_COUNT];
        private final int[] rightWireBySlot = new int[WIRE_COUNT];
        private final int[] connectedSlotByWire = new int[WIRE_COUNT];
        private int dragPointerId = -1;
        private int activeWire = -1;
        private float dragX;
        private float dragY;
        private float mistakeFlash;
        private Runnable completedCallback = () -> {};

        public WireConnectionMinigameWidget() {
            focusable(true);
            mouseCursor(MouseCursor.POINTER);
            randomizeWires();
            resetConnections();
        }

        public WireConnectionMinigameWidget onCompleted(Runnable callback) {
            completedCallback = callback == null ? () -> {} : callback;
            return this;
        }

        public void resetGame() {
            int pointerId = dragPointerId;
            dragPointerId = -1;
            activeWire = -1;
            mistakeFlash = 0.0f;
            randomizeWires();
            resetConnections();
            UIContext context = uiContext();
            if (context != null && pointerId >= 0) {
                context.releasePointer(pointerId, this);
            }
            invalidate(InvalidationFlags.VISUAL);
        }

        @Override
        public void measure(LayoutContext context) {
            setDesiredSize(resolveDesiredSize(context, 720.0f, 720.0f));
        }

        @Override
        public void tick(FrameContext frame) {
            if (visibility() != Visibility.VISIBLE) return;
            super.tick(frame);
            float dt = frame == null || frame.deltaSeconds() <= 0.0f ? 1.0f / 60.0f : frame.deltaSeconds();
            if (mistakeFlash > 0.0f) {
                mistakeFlash = Math.max(0.0f, mistakeFlash - dt * 2.8f);
                invalidate(InvalidationFlags.VISUAL);
            }
        }

        @Override
        public void render(RenderContext context) {
            if (visibility() != Visibility.VISIBLE || context == null) return;
            if (layoutBounds().width() <= 0.0f || layoutBounds().height() <= 0.0f) return;

            pushOpacity(context);
            DrawScope draw = new DrawScope(context, transform(), layoutBounds());
            boolean clip = false;
            try {
                draw.pushClip(boardX(), boardY(), boardSize(), boardSize());
                clip = true;
                renderBoard(draw);
                renderConnectedWires(draw);
                renderActiveWire(draw);
                renderSockets(draw);
            } finally {
                if (clip) draw.popClip();
                popOpacity(context);
            }
        }

        @Override
        public void handle(Event event) {
            if (visibility() != Visibility.VISIBLE || !enabled()) return;
            super.handle(event);
            if (event.isCancelled()) return;
            if (event instanceof PointerEvent pointer && pointer.phase() == EventPhase.CAPTURE) return;

            if (event instanceof PointerPressedEvent pointer
                    && pointer.button() == PointerButton.PRIMARY) {
                int wire = hitLeftInput(boardLocalX(pointer), boardLocalY(pointer));
                if (wire >= 0) {
                    beginDrag(pointer, wire);
                    event.cancel();
                }
            } else if (event instanceof PointerMovedEvent pointer && dragPointerId == pointer.pointerId()) {
                updateDrag(pointer);
                event.cancel();
            } else if (event instanceof PointerReleasedEvent pointer
                    && pointer.button() == PointerButton.PRIMARY
                    && dragPointerId == pointer.pointerId()) {
                finishDrag(pointer);
                event.cancel();
            }
        }

        private void beginDrag(PointerPressedEvent pointer, int wire) {
            dragPointerId = pointer.pointerId();
            activeWire = wire;
            connectedSlotByWire[wire] = -1;
            dragX = boardLocalX(pointer);
            dragY = boardLocalY(pointer);
            UIContext context = uiContext();
            if (context != null) {
                context.capturePointer(pointer.pointerId(), this);
            }
            invalidate(InvalidationFlags.VISUAL);
        }

        private void updateDrag(PointerMovedEvent pointer) {
            float size = boardSize();
            dragX = clamp(boardLocalX(pointer), 0.0f, size);
            dragY = clamp(boardLocalY(pointer), 0.0f, size);
            invalidate(InvalidationFlags.VISUAL);
        }

        private void finishDrag(PointerReleasedEvent pointer) {
            UIContext context = uiContext();
            if (context != null) {
                context.releasePointer(pointer.pointerId(), this);
            }

            int targetSlot = hitRightConnector(boardLocalX(pointer), boardLocalY(pointer));
            if (activeWire >= 0 && targetSlot >= 0 && rightWireBySlot[targetSlot] == activeWire) {
                connectedSlotByWire[activeWire] = targetSlot;
                if (connectedCount() == WIRE_COUNT) {
                    completedCallback.run();
                }
            } else if (activeWire >= 0) {
                mistakeFlash = 1.0f;
            }

            dragPointerId = -1;
            activeWire = -1;
            invalidate(InvalidationFlags.VISUAL);
        }

        private void randomizeWires() {
            for (int i = 0; i < WIRE_COUNT; i++) {
                leftWireBySlot[i] = i;
                rightWireBySlot[i] = i;
            }
            shuffle(leftWireBySlot);
            shuffle(rightWireBySlot);
        }

        private void resetConnections() {
            Arrays.fill(connectedSlotByWire, -1);
        }

        private int connectedCount() {
            int count = 0;
            for (int slot : connectedSlotByWire) {
                if (slot >= 0) count++;
            }
            return count;
        }

        private void renderBoard(DrawScope draw) {
            float x = boardX();
            float y = boardY();
            float size = boardSize();
            if (BOX_TEXTURE != null) {
                draw.addImage(BOX_TEXTURE, x, y, size, size, WHITE);
            } else {
                draw.addRectFilled(x, y, size, size, 8.0f, MutableColor.rgba(0.08f, 0.09f, 0.11f, 1.0f));
                draw.addRect(x, y, size, size, 8.0f, BORDER, 2.0f);
            }
            if (mistakeFlash > 0.0f) {
                draw.addRectFilled(x + 7.0f, y + 7.0f, size - 14.0f, size - 14.0f, 3.0f,
                        MutableColor.rgba(1.0f, 0.12f, 0.08f, 0.12f * mistakeFlash));
            }
        }

        private void renderSockets(DrawScope draw) {
            for (int slot = 0; slot < WIRE_COUNT; slot++) {
                int wire = leftWireBySlot[slot];
                drawBareInput(draw, leftInputX(), connectorY(slot), WIRES[wire], activeWire == wire);
            }
            for (int slot = 0; slot < WIRE_COUNT; slot++) {
                int wire = rightWireBySlot[slot];
                drawTargetConnector(draw, rightConnectorX(), connectorY(slot), WIRES[wire], activeWire == wire);
            }
        }

        private void drawBareInput(DrawScope draw, float localX, float localY, WireSpec wire, boolean active) {
            float scale = pixelScale();
            float inputW = (INPUT_TEXTURE == null ? 5.0f : INPUT_TEXTURE.width()) * scale;
            float inputH = (INPUT_TEXTURE == null ? 8.0f : INPUT_TEXTURE.height()) * scale;
            float stubEndX = localX - inputW * 0.46f;

            if (active) {
                draw.addCircle(absX(localX), absY(localY), Math.max(10.0f, inputH * 0.84f), wire.color, 24, Math.max(1.5f, scale * 0.75f));
            }
            drawCableStub(draw, leftCableStubStartX(), stubEndX, localY, wire);
            drawConnectorBase(draw, leftConnectorBaseX(), localY);

            float x = absX(localX - inputW * 0.5f);
            float y = absY(localY - inputH * 0.5f);
            draw.addRectFilled(x - scale, y - scale, inputW + scale * 2.0f, inputH + scale * 2.0f, scale, SOCKET_SHADOW);
            if (INPUT_TEXTURE != null) {
                draw.addImage(INPUT_TEXTURE, x, y, inputW, inputH, WHITE);
            } else {
                draw.addRectFilled(x, y, inputW, inputH, scale, MutableColor.rgba(0.95f, 0.48f, 0.12f, 1.0f));
            }
        }

        private void renderConnectedWires(DrawScope draw) {
            for (int wire = 0; wire < WIRE_COUNT; wire++) {
                int rightSlot = connectedSlotByWire[wire];
                if (rightSlot < 0) continue;
                int leftSlot = leftSlotForWire(wire);
                if (leftSlot < 0) continue;
                drawWire(draw, leftCableStartX(), connectorY(leftSlot), rightCableEndX(), connectorY(rightSlot), WIRES[wire], 1.0f);
            }
        }

        private void renderActiveWire(DrawScope draw) {
            if (activeWire < 0 || dragPointerId < 0) return;
            int leftSlot = leftSlotForWire(activeWire);
            if (leftSlot < 0) return;
            drawWire(draw, leftCableStartX(), connectorY(leftSlot), dragX, dragY, WIRES[activeWire], 0.92f);
        }

        private void drawTargetConnector(DrawScope draw, float localX, float localY, WireSpec wire, boolean active) {
            float scale = pixelScale() * (active ? 1.08f : 1.0f);
            float w = (wire.connector == null ? 9.0f : wire.connector.width()) * scale;
            float h = (wire.connector == null ? 12.0f : wire.connector.height()) * scale;
            float x = absX(localX - w * 0.5f);
            float y = absY(localY - h * 0.5f);
            if (active) {
                draw.addCircle(absX(localX), absY(localY), Math.max(13.0f, h * 0.72f), wire.color, 24, Math.max(1.5f, scale * 0.75f));
            }
            if (wire.connector == null) {
                draw.addRectFilled(x, y, w, h, scale, wire.color);
                draw.addRect(x, y, w, h, scale, WHITE, Math.max(1.0f, scale * 0.45f));
            } else {
                draw.addImage(wire.connector, x, y, w, h, WHITE);
            }
        }

        private void drawConnectorBase(DrawScope draw, float localX, float localY) {
            float scale = pixelScale();
            float w = (CONNECTOR_BG_TEXTURE == null ? 4.0f : CONNECTOR_BG_TEXTURE.width()) * scale;
            float h = (CONNECTOR_BG_TEXTURE == null ? 12.0f : CONNECTOR_BG_TEXTURE.height()) * scale;
            float x = absX(localX - w * 0.5f);
            float y = absY(localY - h * 0.5f);
            if (CONNECTOR_BG_TEXTURE != null) {
                draw.addImage(CONNECTOR_BG_TEXTURE, x, y, w, h, WHITE);
            } else {
                draw.addRectFilled(x, y, w, h, scale * 0.5f, MutableColor.rgba(0.18f, 0.18f, 0.20f, 1.0f));
                draw.addRectFilled(x + w * 0.38f, y + h * 0.25f, w * 0.32f, h * 0.50f, 0.0f, MutableColor.rgba(0.43f, 0.45f, 0.50f, 1.0f));
            }
        }

        private void drawWire(DrawScope draw, float startX, float startY, float endX, float endY,
                              WireSpec wire, float alpha) {
            MutableColor tint = MutableColor.rgba(1.0f, 1.0f, 1.0f, alpha);
            List<DrawPoint> curve = bezierPoints(absX(startX), absY(startY), absX(endX), absY(endY));
            draw.addPolyline(curve, SHADOW, false, cableThickness() + 5.0f);
            if (wire.cable == null) {
                draw.addPolyline(curve, MutableColor.rgba(wire.color.r(), wire.color.g(), wire.color.b(), alpha), false, cableThickness());
                return;
            }
            for (int i = 0; i < curve.size() - 1; i++) {
                drawCableSegment(draw, wire.cable, curve.get(i), curve.get(i + 1), cableThickness(), tint);
            }
        }

        private void drawCableStub(DrawScope draw, float startX, float endX, float localY, WireSpec wire) {
            DrawPoint start = new DrawPoint(absX(startX), absY(localY));
            DrawPoint end = new DrawPoint(absX(endX), absY(localY));
            List<DrawPoint> points = List.of(start, end);
            draw.addPolyline(points, SHADOW, false, cableThickness() + 4.0f);
            if (wire.cable == null) {
                draw.addPolyline(points, wire.color, false, cableThickness());
            } else {
                drawCableSegment(draw, wire.cable, start, end, cableThickness(), WHITE);
            }
        }

        private void drawCableSegment(DrawScope draw, TextureHandle texture, DrawPoint start, DrawPoint end,
                                      float thickness, MutableColor tint) {
            float dx = end.x() - start.x();
            float dy = end.y() - start.y();
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length <= 0.01f) return;
            float nx = -dy / length * thickness * 0.5f;
            float ny = dx / length * thickness * 0.5f;
            DrawPoint p1 = new DrawPoint(start.x() + nx, start.y() + ny);
            DrawPoint p2 = new DrawPoint(end.x() + nx, end.y() + ny);
            DrawPoint p3 = new DrawPoint(end.x() - nx, end.y() - ny);
            DrawPoint p4 = new DrawPoint(start.x() - nx, start.y() - ny);
            draw.addImageQuad(texture, p1, p2, p3, p4,
                    new DrawPoint(0.0f, 0.0f), new DrawPoint(1.0f, 0.0f),
                    new DrawPoint(1.0f, 1.0f), new DrawPoint(0.0f, 1.0f), tint);
        }

        private List<DrawPoint> bezierPoints(float startX, float startY, float endX, float endY) {
            float distance = Math.abs(endX - startX);
            float c1x = startX + distance * 0.46f;
            float c1y = startY;
            float c2x = endX - distance * 0.46f;
            float c2y = endY;
            List<DrawPoint> points = new ArrayList<>(18);
            for (int i = 0; i <= 17; i++) {
                float t = i / 17.0f;
                float inv = 1.0f - t;
                float px = inv * inv * inv * startX + 3.0f * inv * inv * t * c1x + 3.0f * inv * t * t * c2x + t * t * t * endX;
                float py = inv * inv * inv * startY + 3.0f * inv * inv * t * c1y + 3.0f * inv * t * t * c2y + t * t * t * endY;
                points.add(new DrawPoint(px, py));
            }
            return points;
        }

        private int hitLeftInput(float localX, float localY) {
            if (!insideBoard(localX, localY)) return -1;
            for (int slot = 0; slot < WIRE_COUNT; slot++) {
                if (distance(localX, localY, leftInputX(), connectorY(slot)) <= connectorHitRadius()) {
                    return leftWireBySlot[slot];
                }
            }
            return -1;
        }

        private int hitRightConnector(float localX, float localY) {
            if (!insideBoard(localX, localY)) return -1;
            for (int slot = 0; slot < WIRE_COUNT; slot++) {
                if (distance(localX, localY, rightConnectorX(), connectorY(slot)) <= connectorHitRadius()) return slot;
            }
            return -1;
        }

        private int leftSlotForWire(int wire) {
            for (int slot = 0; slot < WIRE_COUNT; slot++) {
                if (leftWireBySlot[slot] == wire) return slot;
            }
            return -1;
        }

        private boolean insideBoard(float localX, float localY) {
            float size = boardSize();
            return localX >= 0.0f && localY >= 0.0f && localX <= size && localY <= size;
        }

        private float leftInputX() {
            return assetX(15.5f);
        }

        private float leftConnectorBaseX() {
            return assetX(8.5f);
        }

        private float leftCableStubStartX() {
            return assetX(8.5f);
        }

        private float leftCableStartX() {
            return assetX(18.2f);
        }

        private float rightConnectorX() {
            return assetX(163.5f);
        }


        private float rightCableEndX() {
            return assetX(157.0f);
        }

        private float connectorY(int slot) {
            return assetY(31.5f + slot * 35.0f);
        }

        private float assetX(float value) {
            return boardSize() * value / 176.0f;
        }

        private float assetY(float value) {
            return boardSize() * value / 176.0f;
        }

        private float boardSize() {
            return Math.max(260.0f, Math.min(layoutBounds().width(), layoutBounds().height()) * 0.82f);
        }

        private float boardX() {
            return layoutBounds().x() + (layoutBounds().width() - boardSize()) * 0.5f;
        }

        private float boardY() {
            return layoutBounds().y() + (layoutBounds().height() - boardSize()) * 0.5f;
        }

        private float pixelScale() {
            return Math.max(1.0f, boardSize() / 176.0f);
        }

        private float cableThickness() {
            return Math.max(10.0f, pixelScale() * 8.0f);
        }

        private float connectorHitRadius() {
            return Math.max(18.0f, pixelScale() * 11.0f);
        }

        private float absX(float localX) {
            return boardX() + localX;
        }

        private float absY(float localY) {
            return boardY() + localY;
        }

        private float boardLocalX(PointerEvent pointer) {
            return pointer.rootX() - boardX();
        }

        private float boardLocalY(PointerEvent pointer) {
            return pointer.rootY() - boardY();
        }

        private static void shuffle(int[] values) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = values.length - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int tmp = values[i];
                values[i] = values[j];
                values[j] = tmp;
            }
        }

        private static float distance(float ax, float ay, float bx, float by) {
            float dx = ax - bx;
            float dy = ay - by;
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        private static float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

        private static WireSpec wire(String id, MutableColor color) {
            return new WireSpec(color,
                    CableTextures.cable(id),
                    CableTextures.connector(id));
        }

        private record WireSpec(MutableColor color, TextureHandle cable, TextureHandle connector) {
        }

        private static final class CableTextures {
            private static TextureHandle box;
            private static TextureHandle input;
            private static TextureHandle connectorBg;
            private static TextureHandle redCable;
            private static TextureHandle blueCable;
            private static TextureHandle greenCable;
            private static TextureHandle whiteCable;
            private static TextureHandle redConnector;
            private static TextureHandle blueConnector;
            private static TextureHandle greenConnector;
            private static TextureHandle whiteConnector;
            private static boolean loadFailed;

            private CableTextures() {
            }

            private static TextureHandle box() {
                ensureLoaded();
                return box;
            }

            private static TextureHandle input() {
                ensureLoaded();
                return input;
            }

            private static TextureHandle connectorBg() {
                ensureLoaded();
                return connectorBg;
            }

            private static TextureHandle cable(String id) {
                ensureLoaded();
                return switch (id) {
                    case "red" -> redCable;
                    case "blue" -> blueCable;
                    case "green" -> greenCable;
                    case "white" -> whiteCable;
                    default -> null;
                };
            }

            private static TextureHandle connector(String id) {
                ensureLoaded();
                return switch (id) {
                    case "red" -> redConnector;
                    case "blue" -> blueConnector;
                    case "green" -> greenConnector;
                    case "white" -> whiteConnector;
                    default -> null;
                };
            }

            private static void ensureLoaded() {
                if (box != null || loadFailed) return;
                try {
                    box = load("cables_box_gui", "cables_box_gui.png");
                    input = load("cable_input", "cable_input.png");
                    connectorBg = load("cables_connector_bg", "cables_connector_bg.png");
                    redCable = load("cable_red", "cable_red.png");
                    blueCable = load("cable_blue", "cable_blue.png");
                    greenCable = load("cable_green", "cable_green.png");
                    whiteCable = load("cable_white", "cable_white.png");
                    redConnector = load("cable_connector_red", "cable_connector_red.png");
                    blueConnector = load("cable_connector_blue", "cable_connector_blue.png");
                    greenConnector = load("cable_connector_green", "cable_connector_green.png");
                    whiteConnector = load("cable_connector_white", "cable_connector_white.png");
                } catch (IOException | RuntimeException failure) {
                    loadFailed = true;
                }
            }

            private static TextureHandle load(String id, String fileName) throws IOException {
                String resource = "assets/stationarenear/textures/gui/cables/" + fileName;
                ClassLoader loader = WireConnectionMinigameScreen.class.getClassLoader();
                try (InputStream stream = loader.getResourceAsStream(resource)) {
                    if (stream == null) throw new IOException("Missing cable texture resource: " + resource);
                    return UniGuiTextures.replace("stationarenear:dynamic/cables/" + id,
                            NativeImage.read(stream),
                            TextureOptions.nearest());
                }
            }
        }
    }
}

