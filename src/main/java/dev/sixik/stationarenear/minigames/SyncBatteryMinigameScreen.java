package dev.sixik.stationarenear.minigames;

import com.mojang.blaze3d.platform.NativeImage;
import dev.sixik.unigui.api.core.FrameContext;
import dev.sixik.unigui.api.core.InvalidationFlags;
import dev.sixik.unigui.api.core.UIContext;
import dev.sixik.unigui.api.core.UnityLikeUIScaleProvider;
import dev.sixik.unigui.api.event.*;
import dev.sixik.unigui.api.input.KeyCodes;
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
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class SyncBatteryMinigameScreen {
    private static final MutableColor BACKGROUND = MutableColor.rgba(0.006f, 0.009f, 0.014f, 0.94f);
    private static final MutableColor WHITE = MutableColor.rgba(1.0f, 1.0f, 1.0f, 1.0f);

    private SyncBatteryMinigameScreen() {
    }

    public static void open() {
        open(ColumnPuzzleConfig.defaultPreset(), null);
    }

    public static void open(Runnable completionHook) {
        open(ColumnPuzzleConfig.defaultPreset(), completionHook);
    }

    public static void open(ColumnPuzzleConfig config) {
        open(config, null);
    }

    public static void open(ColumnPuzzleConfig config, Runnable completionHook) {
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
        SyncBatteryMinigameWidget minigame = new SyncBatteryMinigameWidget(config);
        minigame.onCompleted(() -> {
            if (completionHook != null) completionHook.run();
            if (closeAction[0] != null) closeAction[0].run();
        });

        MinecraftWidgetScreen screen = new MinecraftWidgetScreen(Component.literal("Sync Battery Minigame"), root(minigame), context) {
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

    private static Widget root(SyncBatteryMinigameWidget minigame) {
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

    public static final class SyncBatteryMinigameWidget extends WidgetBase {
        private static final int PANEL_W = 176;
        private static final int PANEL_H = 80;
        private static final int VISUAL_SLOTS = 5;
        private static final int SECTION_COUNT = 14;
        private static final float BATTERY_X = 42.0f;
        private static final float BATTERY_STEP = 19.0f;
        private static final float BATTERY_TOP = 14.0f;
        private static final float SECTION_H = 3.0f;
        private static final float BUTTON_Y = 59.0f;
        private static final float LEFT_ARROW_X = 30.0f;
        private static final float RIGHT_ARROW_X = 139.0f;
        private static final MutableColor FALLBACK_PANEL = MutableColor.rgba(0.29f, 0.30f, 0.36f, 1.0f);
        private static final MutableColor FALLBACK_FRAME = MutableColor.rgba(0.12f, 0.13f, 0.17f, 1.0f);
        private static final MutableColor FALLBACK_FILL = MutableColor.rgba(0.68f, 0.76f, 0.82f, 1.0f);
        private static final MutableColor FALLBACK_HOVER = MutableColor.rgba(0.80f, 0.86f, 0.92f, 1.0f);
        private static final MutableColor FALLBACK_PRESSED = MutableColor.rgba(0.28f, 0.30f, 0.34f, 1.0f);
        private static final MutableColor TARGET_MARKER = MutableColor.rgba(0.95f, 0.82f, 0.42f, 0.95f);
        private static final TextureHandle PANEL_TEXTURE = SyncTextures.panel();
        private static final TextureHandle ARROW_LEFT_TEXTURE = SyncTextures.arrowLeft();
        private static final TextureHandle ARROW_RIGHT_TEXTURE = SyncTextures.arrowRight();
        private static final TextureHandle BATTERY_TEXTURE = SyncTextures.battery();
        private static final TextureHandle BUTTON_TEXTURE = SyncTextures.button();
        private static final TextureHandle BUTTON_HOVER_TEXTURE = SyncTextures.buttonHighlighted();
        private static final TextureHandle BUTTON_PRESSED_TEXTURE = SyncTextures.buttonPressed();

        private final ColumnPuzzleConfig config;
        private final int[] state;
        private int hoveredButton = -1;
        private int pressedButton = -1;
        private int capturedPointerId = -1;
        private Runnable completedCallback = () -> {};

        public SyncBatteryMinigameWidget() {
            this(ColumnPuzzleConfig.defaultPreset());
        }

        public SyncBatteryMinigameWidget(ColumnPuzzleConfig config) {
            this.config = config == null ? ColumnPuzzleConfig.defaultPreset() : config;
            this.state = new int[this.config.columns()];
            focusable(true);
            mouseCursor(MouseCursor.POINTER);
            resetGame();
        }

        public SyncBatteryMinigameWidget onCompleted(Runnable callback) {
            completedCallback = callback == null ? () -> {} : callback;
            return this;
        }

        public void resetGame() {
            int[] generated = config.generateInitialState();
            System.arraycopy(generated, 0, state, 0, state.length);
            hoveredButton = -1;
            pressedButton = -1;
            capturedPointerId = -1;
            invalidate(InvalidationFlags.VISUAL);
        }

        public int[] state() {
            return state.clone();
        }

        public ColumnPuzzleConfig config() {
            return config;
        }

        @Override
        public void measure(LayoutContext context) {
            setDesiredSize(resolveDesiredSize(context, 760.0f, 360.0f));
        }

        @Override
        public void tick(FrameContext frame) {
            if (visibility() != Visibility.VISIBLE) return;
            super.tick(frame);
        }

        @Override
        public void render(RenderContext context) {
            if (visibility() != Visibility.VISIBLE || context == null) return;
            if (layoutBounds().width() <= 0.0f || layoutBounds().height() <= 0.0f) return;

            pushOpacity(context);
            DrawScope draw = new DrawScope(context, transform(), layoutBounds());
            boolean clipped = false;
            try {
                draw.pushClip(panelX(), panelY(), panelWidth(), panelHeight());
                clipped = true;
                renderPanel(draw);
                renderBatteryFill(draw);
                renderTargetMarkers(draw);
                renderButtons(draw);
            } finally {
                if (clipped) draw.popClip();
                popOpacity(context);
            }
        }

        @Override
        public void handle(Event event) {
            if (visibility() != Visibility.VISIBLE || !enabled()) return;
            super.handle(event);
            if (event.isCancelled()) return;
            if (event instanceof PointerEvent pointer && pointer.phase() == EventPhase.CAPTURE) return;

            if (event instanceof PointerMovedEvent pointer) {
                updateHover(pointer);
                if (capturedPointerId == pointer.pointerId()) event.cancel();
            } else if (event instanceof PointerPressedEvent pointer && pointer.button() == PointerButton.PRIMARY) {
                int button = hitButton(panelLocalX(pointer), panelLocalY(pointer));
                if (button >= 0) {
                    pressedButton = button;
                    capturedPointerId = pointer.pointerId();
                    UIContext context = uiContext();
                    if (context != null) context.capturePointer(pointer.pointerId(), this);
                    invalidate(InvalidationFlags.VISUAL);
                    event.cancel();
                }
            } else if (event instanceof PointerReleasedEvent pointer
                    && pointer.button() == PointerButton.PRIMARY
                    && capturedPointerId == pointer.pointerId()) {
                releaseButton(pointer);
                event.cancel();
            }
        }

        private void updateHover(PointerEvent pointer) {
            int button = hitButton(panelLocalX(pointer), panelLocalY(pointer));
            if (hoveredButton != button) {
                hoveredButton = button;
                invalidate(InvalidationFlags.VISUAL);
            }
        }

        private void releaseButton(PointerReleasedEvent pointer) {
            UIContext context = uiContext();
            if (context != null) context.releasePointer(pointer.pointerId(), this);

            int button = hitButton(panelLocalX(pointer), panelLocalY(pointer));
            if (button >= 0 && button == pressedButton) {
                config.apply(state, button);
                if (isCompleted()) completedCallback.run();
            }
            pressedButton = -1;
            capturedPointerId = -1;
            hoveredButton = button;
            invalidate(InvalidationFlags.VISUAL);
        }

        private boolean isCompleted() {
            return Arrays.equals(state, config.target());
        }

        private void renderPanel(DrawScope draw) {
            if (PANEL_TEXTURE != null) {
                draw.addImage(PANEL_TEXTURE, panelX(), panelY(), panelWidth(), panelHeight(), WHITE);
                return;
            }
            draw.addRectFilled(panelX(), panelY(), panelWidth(), panelHeight(), 8.0f, FALLBACK_PANEL);
            draw.addRect(panelX(), panelY(), panelWidth(), panelHeight(), 8.0f, FALLBACK_FRAME, 2.0f);
        }

        private void renderBatteryFill(DrawScope draw) {
            for (int column = 0; column < config.columns(); column++) {
                int filledSections = filledSections(state[column]);
                for (int section = 0; section < filledSections; section++) {
                    float x = assetX(columnX(column));
                    float y = assetY(BATTERY_TOP + (SECTION_COUNT - 1 - section) * SECTION_H);
                    if (BATTERY_TEXTURE != null) {
                        draw.addImage(BATTERY_TEXTURE, x, y,
                                BATTERY_TEXTURE.width() * panelScale(),
                                BATTERY_TEXTURE.height() * panelScale(), WHITE);
                    } else {
                        draw.addRectFilled(x + panelScale(), y,
                                14.0f * panelScale(), 2.0f * panelScale(), 0.0f, FALLBACK_FILL);
                    }
                }
            }
        }

        private void renderTargetMarkers(DrawScope draw) {
            int commonTarget = commonTargetLevel();
            if (commonTarget >= 0) {
                renderTargetArrows(draw, commonTarget);
                return;
            }

            for (int column = 0; column < config.columns(); column++) {
                float y = targetY(config.target(column));
                draw.addRectFilled(assetX(columnX(column) - 1.0f), y - panelScale() * 0.5f,
                        18.0f * panelScale(), Math.max(1.0f, panelScale()), 0.0f, TARGET_MARKER);
            }
        }

        private void renderTargetArrows(DrawScope draw, int targetLevel) {
            float y = targetY(targetLevel) - arrowHeight() * 0.5f;
            if (ARROW_LEFT_TEXTURE != null) {
                draw.addImage(ARROW_LEFT_TEXTURE, assetX(LEFT_ARROW_X), y,
                        ARROW_LEFT_TEXTURE.width() * panelScale(),
                        ARROW_LEFT_TEXTURE.height() * panelScale(), WHITE);
            } else {
                draw.addTriangleFilled(
                        new DrawPoint(assetX(LEFT_ARROW_X), y + arrowHeight() * 0.5f),
                        new DrawPoint(assetX(LEFT_ARROW_X + 7.0f), y),
                        new DrawPoint(assetX(LEFT_ARROW_X + 7.0f), y + arrowHeight()),
                        FALLBACK_FILL);
            }
            if (ARROW_RIGHT_TEXTURE != null) {
                draw.addImage(ARROW_RIGHT_TEXTURE, assetX(RIGHT_ARROW_X), y,
                        ARROW_RIGHT_TEXTURE.width() * panelScale(),
                        ARROW_RIGHT_TEXTURE.height() * panelScale(), WHITE);
            } else {
                draw.addTriangleFilled(
                        new DrawPoint(assetX(RIGHT_ARROW_X + 7.0f), y + arrowHeight() * 0.5f),
                        new DrawPoint(assetX(RIGHT_ARROW_X), y),
                        new DrawPoint(assetX(RIGHT_ARROW_X), y + arrowHeight()),
                        FALLBACK_FILL);
            }
        }

        private void renderButtons(DrawScope draw) {
            for (int button = 0; button < config.buttonCount(); button++) {
                TextureHandle texture = buttonTexture(button);
                float x = assetX(buttonX(button));
                float y = assetY(BUTTON_Y);
                if (texture != null) {
                    draw.addImage(texture, x, y, texture.width() * panelScale(), texture.height() * panelScale(), WHITE);
                } else {
                    MutableColor color = pressedButton == button ? FALLBACK_PRESSED : hoveredButton == button ? FALLBACK_HOVER : FALLBACK_FILL;
                    draw.addRectFilled(x, y, 16.0f * panelScale(), 9.0f * panelScale(), panelScale(), color);
                    draw.addRect(x, y, 16.0f * panelScale(), 9.0f * panelScale(), panelScale(), FALLBACK_FRAME, Math.max(1.0f, panelScale() * 0.35f));
                }
            }
        }

        private TextureHandle buttonTexture(int button) {
            if (pressedButton == button && BUTTON_PRESSED_TEXTURE != null) return BUTTON_PRESSED_TEXTURE;
            if (hoveredButton == button && BUTTON_HOVER_TEXTURE != null) return BUTTON_HOVER_TEXTURE;
            return BUTTON_TEXTURE;
        }

        private int hitButton(float localX, float localY) {
            if (localX < 0.0f || localY < 0.0f || localX > PANEL_W || localY > PANEL_H) return -1;
            for (int button = 0; button < config.buttonCount(); button++) {
                float x = buttonX(button);
                if (localX >= x && localX <= x + 16.0f && localY >= BUTTON_Y && localY <= BUTTON_Y + 9.0f) {
                    return button;
                }
            }
            return -1;
        }

        private int commonTargetLevel() {
            int value = config.target(0);
            for (int i = 1; i < config.columns(); i++) {
                if (config.target(i) != value) return -1;
            }
            return value;
        }

        private int filledSections(int level) {
            if (config.levelsPerColumn() <= 1) return 0;
            return Math.round((float) level / (config.levelsPerColumn() - 1) * SECTION_COUNT);
        }

        private float targetY(int targetLevel) {
            int filled = filledSections(targetLevel);
            if (filled <= 0) return assetY(BATTERY_TOP + SECTION_COUNT * SECTION_H - SECTION_H * 0.5f);
            return assetY(BATTERY_TOP + (SECTION_COUNT - filled) * SECTION_H + SECTION_H * 0.5f);
        }

        private float arrowHeight() {
            return (ARROW_LEFT_TEXTURE == null ? 9.0f : ARROW_LEFT_TEXTURE.height()) * panelScale();
        }

        private float columnX(int column) {
            return BATTERY_X + column * BATTERY_STEP;
        }

        private float buttonX(int button) {
            return BATTERY_X + button * BATTERY_STEP;
        }

        private float panelWidth() {
            return PANEL_W * panelScale();
        }

        private float panelHeight() {
            return PANEL_H * panelScale();
        }

        private float panelScale() {
            float maxW = layoutBounds().width() * 0.88f / PANEL_W;
            float maxH = layoutBounds().height() * 0.70f / PANEL_H;
            return Math.max(2.0f, Math.min(8.0f, Math.min(maxW, maxH)));
        }

        private float panelX() {
            return layoutBounds().x() + (layoutBounds().width() - panelWidth()) * 0.5f;
        }

        private float panelY() {
            return layoutBounds().y() + (layoutBounds().height() - panelHeight()) * 0.5f;
        }

        private float assetX(float x) {
            return panelX() + x * panelScale();
        }

        private float assetY(float y) {
            return panelY() + y * panelScale();
        }

        private float panelLocalX(PointerEvent pointer) {
            return (pointer.rootX() - panelX()) / panelScale();
        }

        private float panelLocalY(PointerEvent pointer) {
            return (pointer.rootY() - panelY()) / panelScale();
        }

        @Override
        public String toString() {
            return "SyncBatteryMinigameWidget{" +
                    "state=" + Arrays.toString(state) +
                    ", target=" + Arrays.toString(config.target()) +
                    '}';
        }
    }

    public static final class ColumnPuzzleConfig {
        private static final int MAX_VISUAL_SLOTS = 5;
        private final int columns;
        private final int levelsPerColumn;
        private final int[][] buttonEffects;
        private final int[] target;
        private final int shuffleSteps;

        public ColumnPuzzleConfig(int columns, int levelsPerColumn, int[][] buttonEffects, int[] target, int shuffleSteps) {
            if (columns <= 0 || columns > MAX_VISUAL_SLOTS) {
                throw new IllegalArgumentException("columns must be in range 1.." + MAX_VISUAL_SLOTS);
            }
            if (levelsPerColumn < 2) {
                throw new IllegalArgumentException("levelsPerColumn must be >= 2");
            }
            if (buttonEffects == null || buttonEffects.length == 0 || buttonEffects.length > MAX_VISUAL_SLOTS) {
                throw new IllegalArgumentException("buttonEffects must contain 1.." + MAX_VISUAL_SLOTS + " buttons");
            }
            if (target == null || target.length != columns) {
                throw new IllegalArgumentException("target length must match columns");
            }

            this.columns = columns;
            this.levelsPerColumn = levelsPerColumn;
            this.buttonEffects = copyEffects(buttonEffects, columns);
            this.target = normalizeTarget(target, levelsPerColumn);
            this.shuffleSteps = Math.max(1, shuffleSteps);
        }

        public static ColumnPuzzleConfig defaultPreset() {
            int columns = 5;
            int levels = 15;
            int[] target = {14, 14, 14, 14, 14};
            int[][] buttons = {
                    {1, 1, 0, 0, 0},
                    {0, 1, 1, 0, 0},
                    {0, 0, 1, 1, 0},
                    {0, 0, 0, 1, 1},
                    {1, 0, 0, 0, 1}
            };
            return new ColumnPuzzleConfig(columns, levels, buttons, target, 12);
        }

        public int columns() {
            return columns;
        }

        public int levelsPerColumn() {
            return levelsPerColumn;
        }

        public int buttonCount() {
            return buttonEffects.length;
        }

        public int effect(int button, int column) {
            return buttonEffects[button][column];
        }

        public int target(int column) {
            return target[column];
        }

        public int[] target() {
            return target.clone();
        }

        public int[][] buttonEffects() {
            return copyEffects(buttonEffects, columns);
        }

        public int shuffleSteps() {
            return shuffleSteps;
        }

        public int[] generateInitialState() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int[] generated = target.clone();
            for (int attempt = 0; attempt < 8; attempt++) {
                System.arraycopy(target, 0, generated, 0, columns);
                for (int step = 0; step < shuffleSteps; step++) {
                    applyInverse(generated, random.nextInt(buttonEffects.length));
                }
                if (!Arrays.equals(generated, target) || !hasAnyNonZeroEffect()) {
                    return generated;
                }
            }
            apply(generated, firstNonZeroButton());
            return generated;
        }

        public void apply(int[] state, int button) {
            applyDelta(state, button, 1);
        }

        private void applyInverse(int[] state, int button) {
            applyDelta(state, button, -1);
        }

        private void applyDelta(int[] state, int button, int direction) {
            if (state == null || state.length != columns) {
                throw new IllegalArgumentException("state length must match columns");
            }
            if (button < 0 || button >= buttonEffects.length) {
                throw new IndexOutOfBoundsException("button index out of range: " + button);
            }
            for (int column = 0; column < columns; column++) {
                state[column] = mod(state[column] + buttonEffects[button][column] * direction, levelsPerColumn);
            }
        }

        public ColumnPuzzleSolution solve(int[] initialState) {
            return solveByBfs(initialState, 1_000_000);
        }

        public ColumnPuzzleSolution solveByBfs(int[] initialState, int maxStates) {
            if (initialState == null || initialState.length != columns) {
                throw new IllegalArgumentException("initialState length must match columns");
            }
            int[] normalizedInitial = normalizeTarget(initialState, levelsPerColumn);
            long totalStates = totalStateCount(maxStates);
            if (totalStates < 0L) {
                return ColumnPuzzleSolution.notAttempted(buttonEffects.length);
            }

            int total = (int) totalStates;
            int start = encode(normalizedInitial);
            int goal = encode(target);
            int[] previous = new int[total];
            int[] previousButton = new int[total];
            Arrays.fill(previous, -1);
            Arrays.fill(previousButton, -1);

            Queue<Integer> queue = new ArrayDeque<>();
            previous[start] = start;
            queue.add(start);

            int[] current = new int[columns];
            while (!queue.isEmpty()) {
                int code = queue.remove();
                if (code == goal) {
                    return reconstructSolution(start, goal, previous, previousButton);
                }
                decode(code, current);
                for (int button = 0; button < buttonEffects.length; button++) {
                    int[] next = current.clone();
                    apply(next, button);
                    int nextCode = encode(next);
                    if (previous[nextCode] >= 0) continue;
                    previous[nextCode] = code;
                    previousButton[nextCode] = button;
                    queue.add(nextCode);
                }
            }
            return ColumnPuzzleSolution.unsolvable(buttonEffects.length);
        }

        private ColumnPuzzleSolution reconstructSolution(int start, int goal, int[] previous, int[] previousButton) {
            int[] presses = new int[buttonEffects.length];
            List<Integer> reversed = new ArrayList<>();
            int cursor = goal;
            while (cursor != start) {
                int button = previousButton[cursor];
                if (button < 0) return ColumnPuzzleSolution.unsolvable(buttonEffects.length);
                presses[button]++;
                reversed.add(button);
                cursor = previous[cursor];
            }
            Collections.reverse(reversed);
            int[] sequence = new int[reversed.size()];
            for (int i = 0; i < reversed.size(); i++) {
                sequence[i] = reversed.get(i);
            }
            return new ColumnPuzzleSolution(true, true, presses, sequence);
        }

        private int firstNonZeroButton() {
            for (int button = 0; button < buttonEffects.length; button++) {
                for (int value : buttonEffects[button]) {
                    if (mod(value, levelsPerColumn) != 0) return button;
                }
            }
            return 0;
        }

        private boolean hasAnyNonZeroEffect() {
            for (int[] effect : buttonEffects) {
                for (int value : effect) {
                    if (mod(value, levelsPerColumn) != 0) return true;
                }
            }
            return false;
        }

        private int encode(int[] value) {
            int code = 0;
            int factor = 1;
            for (int column = 0; column < columns; column++) {
                code += mod(value[column], levelsPerColumn) * factor;
                factor *= levelsPerColumn;
            }
            return code;
        }

        private void decode(int code, int[] out) {
            for (int column = 0; column < columns; column++) {
                out[column] = code % levelsPerColumn;
                code /= levelsPerColumn;
            }
        }

        private long totalStateCount(int maxStates) {
            long total = 1L;
            for (int i = 0; i < columns; i++) {
                total *= levelsPerColumn;
                if (total > maxStates || total > Integer.MAX_VALUE) return -1L;
            }
            return total;
        }

        private static int[][] copyEffects(int[][] source, int columns) {
            int[][] copy = new int[source.length][columns];
            for (int button = 0; button < source.length; button++) {
                if (source[button] == null || source[button].length != columns) {
                    throw new IllegalArgumentException("button effect length must match columns");
                }
                System.arraycopy(source[button], 0, copy[button], 0, columns);
            }
            return copy;
        }

        private static int[] normalizeTarget(int[] source, int levelsPerColumn) {
            int[] copy = source.clone();
            for (int i = 0; i < copy.length; i++) {
                copy[i] = mod(copy[i], levelsPerColumn);
            }
            return copy;
        }

        private static int mod(int value, int base) {
            int result = value % base;
            return result < 0 ? result + base : result;
        }
    }

    public record ColumnPuzzleSolution(boolean attempted, boolean solvable, int[] presses, int[] sequence) {
        private static ColumnPuzzleSolution notAttempted(int buttonCount) {
            return new ColumnPuzzleSolution(false, false, new int[buttonCount], new int[0]);
        }

        private static ColumnPuzzleSolution unsolvable(int buttonCount) {
            return new ColumnPuzzleSolution(true, false, new int[buttonCount], new int[0]);
        }

        public ColumnPuzzleSolution {
            presses = presses == null ? new int[0] : presses.clone();
            sequence = sequence == null ? new int[0] : sequence.clone();
        }

        @Override
        public int[] presses() {
            return presses.clone();
        }

        @Override
        public int[] sequence() {
            return sequence.clone();
        }
    }

    private static final class SyncTextures {
        private static TextureHandle panel;
        private static TextureHandle arrowLeft;
        private static TextureHandle arrowRight;
        private static TextureHandle battery;
        private static TextureHandle button;
        private static TextureHandle buttonHighlighted;
        private static TextureHandle buttonPressed;
        private static boolean loadFailed;

        private SyncTextures() {
        }

        private static TextureHandle panel() {
            ensureLoaded();
            return panel;
        }

        private static TextureHandle arrowLeft() {
            ensureLoaded();
            return arrowLeft;
        }

        private static TextureHandle arrowRight() {
            ensureLoaded();
            return arrowRight;
        }

        private static TextureHandle battery() {
            ensureLoaded();
            return battery;
        }

        private static TextureHandle button() {
            ensureLoaded();
            return button;
        }

        private static TextureHandle buttonHighlighted() {
            ensureLoaded();
            return buttonHighlighted;
        }

        private static TextureHandle buttonPressed() {
            ensureLoaded();
            return buttonPressed;
        }

        private static void ensureLoaded() {
            if (panel != null || loadFailed) return;
            try {
                panel = load("sync_panel", "sync_panel.png");
                arrowLeft = load("sync_panel_arrow_left", "sync_panel_arrow_left.png");
                arrowRight = load("sync_panel_arrow_right", "sync_panel_arrow_right.png");
                battery = load("sync_panel_battery", "sync_panel_battery.png");
                button = load("sync_panel_button", "sync_panel_button.png");
                buttonHighlighted = load("sync_panel_button_highlighted", "sync_panel_button_highlighted.png");
                buttonPressed = load("sync_panel_button_pressed", "sync_panel_button_pressed.png");
            } catch (IOException | RuntimeException failure) {
                loadFailed = true;
            }
        }

        private static TextureHandle load(String id, String fileName) throws IOException {
            String resource = "assets/stationarenear/textures/gui/sync/" + fileName;
            ClassLoader loader = SyncBatteryMinigameScreen.class.getClassLoader();
            try (InputStream stream = loader.getResourceAsStream(resource)) {
                if (stream == null) throw new IOException("Missing sync texture resource: " + resource);
                return UniGuiTextures.replace("stationarenear:dynamic/sync/" + id,
                        NativeImage.read(stream),
                        TextureOptions.nearest());
            }
        }
    }
}