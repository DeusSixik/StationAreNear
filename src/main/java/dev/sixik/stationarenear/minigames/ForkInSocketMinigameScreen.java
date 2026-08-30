package dev.sixik.stationarenear.minigames;

import com.mojang.blaze3d.platform.NativeImage;
import dev.sixik.unigui.api.core.FrameContext;
import dev.sixik.unigui.api.core.InvalidationFlags;
import dev.sixik.unigui.api.core.UIContext;
import dev.sixik.unigui.api.core.UnityLikeUIScaleProvider;
import dev.sixik.unigui.api.event.Event;
import dev.sixik.unigui.api.event.EventPhase;
import dev.sixik.unigui.api.event.PointerEvent;
import dev.sixik.unigui.api.event.PointerPressedEvent;
import dev.sixik.unigui.api.input.KeyCodes;
import dev.sixik.unigui.api.input.KeyboardState;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class ForkInSocketMinigameScreen {

    private ForkInSocketMinigameScreen() {
    }

    public static void open() {
        open(null, null);
    }

    public static void open(Runnable completionHook) {
        open(completionHook, null);
    }

    public static void open(Runnable completionHook, Runnable missHook) {
        open(ForkInSocketConfig.defaultConfig(), completionHook, missHook);
    }

    public static void open(ForkInSocketConfig config, Runnable completionHook, Runnable missHook) {
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
        ForkInSocketMinigameWidget minigame = new ForkInSocketMinigameWidget(config);
        minigame.onCompleted(() -> {
            if (completionHook != null) completionHook.run();
            if (config.autoCloseOnSuccess() && closeAction[0] != null) {
                closeAction[0].run();
            }
        });

        if (missHook != null) {
            minigame.onMiss(missHook);
        }

        MinecraftWidgetScreen screen = new MinecraftWidgetScreen(
                Component.translatable("screen.stationarenear.minigame.fork_in_socket"),
                root(minigame),
                context
        ) {
            @Override
            public void onClose() {
                Minecraft.getInstance().setScreen(previous);
            }

            @Override
            public boolean isPauseScreen() {
                return false;
            }
        };
        closeAction[0] = screen::onClose;
        screen.useContextScale().scaleWithMinecraftGui(false);
        screen.useSdfDefaultFont();
        screen.renderPolicy(UiRenderPolicy.continuous());
        minecraft.setScreen(screen);
    }

    private static Widget root(ForkInSocketMinigameWidget minigame) {
        StackPanel viewport = new StackPanel();
        viewport.layout(style -> style.sizePercent(100.0f, 100.0f));
        minigame.layout(style -> style.sizePercent(100.0f, 100.0f).align(Alignment.STRETCH, Alignment.STRETCH));
        viewport.addChild(minigame);
        return new OverlayLayer(viewport);
    }

    public record ForkInSocketConfig(
            float speed,
            float hitTolerance,
            boolean randomizeSocketOnMiss,
            boolean autoCloseOnSuccess
    ) {
        public static ForkInSocketConfig defaultConfig() {
            return new ForkInSocketConfig(1.10f, 0.065f, true, true);
        }

        public static ForkInSocketConfig easy() {
            return new ForkInSocketConfig(0.85f, 0.095f, true, true);
        }

        public static ForkInSocketConfig hard() {
            return new ForkInSocketConfig(1.45f, 0.045f, true, true);
        }
    }

    public static final class ForkInSocketMinigameWidget extends WidgetBase {
        private static final MutableColor WHITE = MutableColor.rgba(1.0f, 1.0f, 1.0f, 1.0f);
        private static final MutableColor SHADOW = MutableColor.rgba(0.0f, 0.0f, 0.0f, 0.40f);
        private static final MutableColor ZONE_READY = MutableColor.rgba(0.24f, 0.86f, 1.0f, 0.22f);
        private static final MutableColor ZONE_BORDER = MutableColor.rgba(0.35f, 0.88f, 1.0f, 0.65f);
        private static final MutableColor SPARK_CYAN = MutableColor.rgba(0.35f, 0.95f, 1.0f, 0.95f);
        private static final MutableColor SPARK_GOLD = MutableColor.rgba(1.0f, 0.88f, 0.32f, 0.95f);
        private static final MutableColor SPARK_RED = MutableColor.rgba(1.0f, 0.28f, 0.22f, 0.95f);
        private static final MutableColor CABLE_COLOR = MutableColor.rgba(0.18f, 0.20f, 0.26f, 1.0f);

        private static final TextureHandle LINE_TEXTURE = ForkInSocketTextures.line();
        private static final TextureHandle SOCKET_TEXTURE = ForkInSocketTextures.socket();
        private static final TextureHandle WILKA_TEXTURE = ForkInSocketTextures.wilka();

        private final ForkInSocketConfig config;
        private float forkPos = 0.1f;
        private float forkDirection = 1.0f;
        private float socketPos = 0.5f;
        private float thrust = 0.0f;
        private boolean isInserting;
        private boolean isConnected;
        private boolean isMissed;
        private float successTimer;
        private float mistakePulse;
        private float shakeTimer;
        private int hits;
        private int misses;

        private final List<SparkParticle> sparks = new ArrayList<>();
        private Runnable completedCallback = () -> {};
        private Runnable missCallback = () -> {};

        public ForkInSocketMinigameWidget() {
            this(ForkInSocketConfig.defaultConfig());
        }

        public ForkInSocketMinigameWidget(ForkInSocketConfig config) {
            this.config = config == null ? ForkInSocketConfig.defaultConfig() : config;
            focusable(true);
            mouseCursor(MouseCursor.POINTER);
            resetGame();
        }

        public ForkInSocketMinigameWidget onCompleted(Runnable callback) {
            this.completedCallback = callback == null ? () -> {} : callback;
            return this;
        }

        public ForkInSocketMinigameWidget onMiss(Runnable callback) {
            this.missCallback = callback == null ? () -> {} : callback;
            return this;
        }

        public void resetGame() {
            forkPos = 0.1f;
            forkDirection = 1.0f;
            socketPos = ThreadLocalRandom.current().nextFloat(0.20f, 0.80f);
            thrust = 0.0f;
            isInserting = false;
            isConnected = false;
            isMissed = false;
            successTimer = 0.0f;
            mistakePulse = 0.0f;
            shakeTimer = 0.0f;
            hits = 0;
            misses = 0;
            sparks.clear();
            invalidate(InvalidationFlags.VISUAL);
        }

        public void triggerInsert() {
            if (isConnected || isInserting) return;
            isInserting = true;
            isMissed = false;
            invalidate(InvalidationFlags.VISUAL);
        }

        @Override
        public void measure(LayoutContext context) {
            setDesiredSize(resolveDesiredSize(context, 740.0f, 420.0f));
        }

        @Override
        public void tick(FrameContext frame) {
            if (visibility() != Visibility.VISIBLE) return;
            super.tick(frame);

            float dt = frame == null || frame.deltaSeconds() <= 0.0f ? 1.0f / 60.0f : frame.deltaSeconds();

            UIContext context = uiContext();
            KeyboardState keyboard = context == null ? KeyboardState.NONE : context.keyboard();
            if (keyboard.wasPressed(KeyCodes.SPACE)) {
                triggerInsert();
            }

            // Обновление движения вилки
            if (!isConnected && !isInserting) {
                forkPos += forkDirection * config.speed() * dt;
                if (forkPos >= 1.0f) {
                    forkPos = 1.0f;
                    forkDirection = -1.0f;
                } else if (forkPos <= 0.0f) {
                    forkPos = 0.0f;
                    forkDirection = 1.0f;
                }
            }

            // Анимация вставки вилки
            if (isInserting) {
                thrust += dt * 6.5f;
                if (thrust >= 1.0f) {
                    thrust = 1.0f;
                    checkHit();
                }
            } else if (isMissed) {
                thrust = Math.max(0.0f, thrust - dt * 3.8f);
                if (thrust <= 0.0f) {
                    isMissed = false;
                }
            }

            // Эффекты
            if (shakeTimer > 0.0f) {
                shakeTimer = Math.max(0.0f, shakeTimer - dt);
            }
            if (mistakePulse > 0.0f) {
                mistakePulse = Math.max(0.0f, mistakePulse - dt * 2.8f);
            }

            // Обновление искр
            for (int i = sparks.size() - 1; i >= 0; i--) {
                SparkParticle spark = sparks.get(i);
                spark.life -= dt;
                if (spark.life <= 0.0f) {
                    sparks.remove(i);
                } else {
                    spark.x += spark.vx * dt;
                    spark.y += spark.vy * dt;
                    spark.vy += 80.0f * dt; // гравитация
                }
            }

            // Непрерывные искры при успешном подключении
            if (isConnected) {
                successTimer += dt;
                if (ThreadLocalRandom.current().nextFloat() < 0.45f) {
                    spawnSparks(socketCenterX(), socketCenterY(), 3, true);
                }
            }

            invalidate(InvalidationFlags.VISUAL);
        }

        private void checkHit() {
            isInserting = false;
            float diff = Math.abs(forkPos - socketPos);
            if (diff <= config.hitTolerance()) {
                // ПОПАДАНИЕ
                isConnected = true;
                hits++;
                spawnSparks(socketCenterX(), socketCenterY(), 22, true);
                completedCallback.run();
            } else {
                // ПРОМАХ
                isMissed = true;
                misses++;
                mistakePulse = 1.0f;
                shakeTimer = 0.28f;
                spawnSparks(forkCenterX(), socketCenterY() + 20.0f, 14, false);
                if (config.randomizeSocketOnMiss()) {
                    socketPos = ThreadLocalRandom.current().nextFloat(0.18f, 0.82f);
                }
                missCallback.run();
            }
        }

        private void spawnSparks(float cx, float cy, int count, boolean success) {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            for (int i = 0; i < count; i++) {
                float angle = rng.nextFloat(0.0f, (float) (Math.PI * 2.0));
                float speed = rng.nextFloat(30.0f, 120.0f);
                float vx = (float) Math.cos(angle) * speed;
                float vy = (float) Math.sin(angle) * speed - 20.0f;
                float maxLife = rng.nextFloat(0.20f, 0.55f);
                MutableColor col = success
                        ? (rng.nextBoolean() ? SPARK_CYAN : SPARK_GOLD)
                        : SPARK_RED;
                sparks.add(new SparkParticle(cx, cy, vx, vy, maxLife, maxLife, col, rng.nextFloat(1.5f, 3.2f)));
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
                triggerInsert();
                event.cancel();
            }
        }

        @Override
        public void render(RenderContext context) {
            if (visibility() != Visibility.VISIBLE || context == null) return;
            if (layoutBounds().width() <= 0.0f || layoutBounds().height() <= 0.0f) return;

            pushOpacity(context);
            DrawScope draw = new DrawScope(context, transform(), layoutBounds());
            try {
                renderMinigame(draw);
            } finally {
                popOpacity(context);
            }
        }

        private void renderMinigame(DrawScope draw) {
            float shakeX = 0.0f;
            float shakeY = 0.0f;
            if (shakeTimer > 0.0f) {
                float progress = shakeTimer / 0.28f;
                shakeX = (float) Math.sin(shakeTimer * 55.0f) * 6.0f * progress;
                shakeY = (float) Math.cos(shakeTimer * 45.0f) * 4.0f * progress;
            }

            float cx = layoutBounds().x() + layoutBounds().width() * 0.5f + shakeX;
            float cy = layoutBounds().y() + layoutBounds().height() * 0.5f + shakeY;
            float pScale = pixelScale();

            float lineW = lineNaturalWidth() * pScale;
            float lineH = lineNaturalHeight() * pScale;
            float lineX = cx - lineW * 0.5f;
            float lineY = cy - lineH * 0.5f - pScale * 8.0f;

            float socketW = socketNaturalWidth() * pScale;
            float socketH = socketNaturalHeight() * pScale;
            float minSocketX = lineX + pScale * 6.0f;
            float maxSocketX = lineX + lineW - pScale * (6.0f + socketNaturalWidth());
            float currentSocketX = minSocketX + socketPos * (maxSocketX - minSocketX);
            float currentSocketY = lineY + (lineH - socketH) * 0.5f;

            float forkW = forkNaturalWidth() * pScale;
            float forkH = forkNaturalHeight() * pScale;
            float minForkX = lineX + pScale * 6.0f;
            float maxForkX = lineX + lineW - pScale * (6.0f + forkNaturalWidth());
            float currentForkX = minForkX + forkPos * (maxForkX - minForkX);
            float idleForkY = lineY + lineH + pScale * 10.0f;
            float insertedForkY = currentSocketY + socketH * 0.40f - pScale * 4.0f;
            float currentForkY = idleForkY - thrust * (idleForkY - insertedForkY);

            // Отрисовка направляющей и зоны розетки
            renderTargetZone(draw, currentSocketX, currentSocketY, socketW, socketH, pScale);
            renderLine(draw, lineX, lineY, lineW, lineH, pScale);
            renderSocket(draw, currentSocketX, currentSocketY, socketW, socketH, pScale);

            // Отрисовка кабеля от нижнего кончика вилки вниз (провод заходит под вилку)
            float forkTipX = currentForkX + forkW * 0.5f;
            float forkTipY = currentForkY + pScale * 9.5f;
            renderTrailingCable(draw, forkTipX, forkTipY, cx, layoutBounds().bottom() - 12.0f, pScale);

            // Отрисовка вилки поверх начала провода
            renderFork(draw, currentForkX, currentForkY, forkW, forkH, pScale);

            // Отрисовка искр и электричества
            renderSparks(draw);
            if (isConnected) {
                renderElectricArc(draw, currentSocketX + socketW * 0.35f, currentSocketY + socketH * 0.5f,
                        currentForkX + forkW * 0.35f, currentForkY + 4.0f * pScale, pScale);
                renderElectricArc(draw, currentSocketX + socketW * 0.65f, currentSocketY + socketH * 0.5f,
                        currentForkX + forkW * 0.65f, currentForkY + 4.0f * pScale, pScale);
            }

            // Отрисовка эффекта промаха / ошибки
            if (mistakePulse > 0.0f) {
                draw.addRectFilled(layoutBounds().x(), layoutBounds().y(),
                        layoutBounds().width(), layoutBounds().height(),
                        MutableColor.rgba(1.0f, 0.15f, 0.15f, 0.14f * mistakePulse));
            }
        }

        private void renderTargetZone(DrawScope draw, float x, float y, float w, float h, float scale) {
            float padX = 2.0f * scale;
            float padY = 2.0f * scale;
            draw.addRectFilled(x - padX, y - padY, w + padX * 2.0f, h + padY * 2.0f, 2.0f * scale, ZONE_READY);
            draw.addRect(x - padX, y - padY, w + padX * 2.0f, h + padY * 2.0f, 2.0f * scale, ZONE_BORDER, 1.0f * scale);
        }

        private void renderLine(DrawScope draw, float x, float y, float w, float h, float scale) {
            if (LINE_TEXTURE != null) {
                draw.addImage(LINE_TEXTURE, x, y, w, h, WHITE);
            } else {
                draw.addRectFilled(x, y, w, h, 2.0f * scale, MutableColor.rgba(0.28f, 0.32f, 0.40f, 1.0f));
                draw.addRect(x, y, w, h, 2.0f * scale, MutableColor.rgba(0.14f, 0.16f, 0.22f, 1.0f), 1.0f * scale);
            }
        }

        private void renderSocket(DrawScope draw, float x, float y, float w, float h, float scale) {
            if (SOCKET_TEXTURE != null) {
                draw.addImage(SOCKET_TEXTURE, x, y, w, h, WHITE);
            } else {
                draw.addRectFilled(x, y, w, h, 2.0f * scale, MutableColor.rgba(0.85f, 0.88f, 0.92f, 1.0f));
                draw.addRect(x, y, w, h, 2.0f * scale, MutableColor.rgba(0.18f, 0.20f, 0.25f, 1.0f), 1.0f * scale);
                float pinW = 1.5f * scale;
                float pinH = 3.5f * scale;
                draw.addRectFilled(x + w * 0.30f - pinW * 0.5f, y + (h - pinH) * 0.5f, pinW, pinH, 0.5f * scale, MutableColor.rgba(0.10f, 0.10f, 0.12f, 1.0f));
                draw.addRectFilled(x + w * 0.70f - pinW * 0.5f, y + (h - pinH) * 0.5f, pinW, pinH, 0.5f * scale, MutableColor.rgba(0.10f, 0.10f, 0.12f, 1.0f));
            }
        }

        private void renderFork(DrawScope draw, float x, float y, float w, float h, float scale) {
            if (WILKA_TEXTURE != null) {
                draw.addImage(WILKA_TEXTURE, x, y, w, h, WHITE);
            } else {
                // Запасной процедурный рендер вилки
                float prongsH = h * 0.35f;
                float bodyH = h - prongsH;
                // Штыри
                draw.addRectFilled(x + w * 0.25f - scale, y, 2.0f * scale, prongsH, 0.5f * scale, MutableColor.rgba(0.88f, 0.58f, 0.22f, 1.0f));
                draw.addRectFilled(x + w * 0.75f - scale, y, 2.0f * scale, prongsH, 0.5f * scale, MutableColor.rgba(0.88f, 0.58f, 0.22f, 1.0f));
                // Корпус
                draw.addRectFilled(x, y + prongsH, w, bodyH, 2.0f * scale, MutableColor.rgba(0.18f, 0.20f, 0.26f, 1.0f));
                draw.addRect(x, y + prongsH, w, bodyH, 2.0f * scale, MutableColor.rgba(0.35f, 0.40f, 0.50f, 1.0f), 1.0f * scale);
            }
        }

        private void renderTrailingCable(DrawScope draw, float startX, float startY, float endX, float endY, float scale) {
            float distance = Math.abs(endY - startY);
            float c1x = startX;
            float c1y = startY + distance * 0.55f;
            float c2x = endX;
            float c2y = endY - distance * 0.35f;

            List<DrawPoint> curve = new ArrayList<>(16);
            for (int i = 0; i <= 15; i++) {
                float t = i / 15.0f;
                float inv = 1.0f - t;
                float px = inv * inv * inv * startX + 3.0f * inv * inv * t * c1x + 3.0f * inv * t * t * c2x + t * t * t * endX;
                float py = inv * inv * inv * startY + 3.0f * inv * inv * t * c1y + 3.0f * inv * t * t * c2y + t * t * t * endY;
                curve.add(new DrawPoint(px, py));
            }

            draw.addPolyline(curve, SHADOW, false, 4.5f * scale);
            draw.addPolyline(curve, CABLE_COLOR, false, 3.0f * scale);
        }

        private void renderElectricArc(DrawScope draw, float x1, float y1, float x2, float y2, float scale) {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            int segments = 5;
            List<DrawPoint> arc = new ArrayList<>(segments + 1);
            arc.add(new DrawPoint(x1, y1));
            for (int i = 1; i < segments; i++) {
                float t = i / (float) segments;
                float px = x1 + (x2 - x1) * t + rng.nextFloat(-3.0f * scale, 3.0f * scale);
                float py = y1 + (y2 - y1) * t + rng.nextFloat(-2.5f * scale, 2.5f * scale);
                arc.add(new DrawPoint(px, py));
            }
            arc.add(new DrawPoint(x2, y2));
            draw.addPolyline(arc, SPARK_CYAN, false, 1.5f * scale);
        }

        private void renderSparks(DrawScope draw) {
            for (SparkParticle spark : sparks) {
                float alpha = spark.life / spark.maxLife;
                MutableColor color = MutableColor.rgba(spark.color.r(), spark.color.g(), spark.color.b(), alpha);
                draw.addCircleFilled(spark.x, spark.y, spark.size, color, 8);
            }
        }

        private float socketCenterX() {
            float pScale = pixelScale();
            float lineW = lineNaturalWidth() * pScale;
            float lineX = layoutBounds().x() + (layoutBounds().width() - lineW) * 0.5f;
            float socketW = socketNaturalWidth() * pScale;
            float minSocketX = lineX + pScale * 6.0f;
            float maxSocketX = lineX + lineW - pScale * (6.0f + socketNaturalWidth());
            return minSocketX + socketPos * (maxSocketX - minSocketX) + socketW * 0.5f;
        }

        private float socketCenterY() {
            float pScale = pixelScale();
            float lineH = lineNaturalHeight() * pScale;
            float cy = layoutBounds().y() + layoutBounds().height() * 0.5f;
            return cy - lineH * 0.5f - pScale * 8.0f + lineH * 0.5f;
        }

        private float forkCenterX() {
            float pScale = pixelScale();
            float lineW = lineNaturalWidth() * pScale;
            float lineX = layoutBounds().x() + (layoutBounds().width() - lineW) * 0.5f;
            float forkW = forkNaturalWidth() * pScale;
            float minForkX = lineX + pScale * 6.0f;
            float maxForkX = lineX + lineW - pScale * (6.0f + forkNaturalWidth());
            return minForkX + forkPos * (maxForkX - minForkX) + forkW * 0.5f;
        }

        private float pixelScale() {
            float maxAvailW = Math.min(layoutBounds().width() * 0.82f, 620.0f);
            float maxAvailH = Math.min(layoutBounds().height() * 0.60f, 320.0f);
            float scaleFromW = maxAvailW / lineNaturalWidth();
            float scaleFromH = maxAvailH / (lineNaturalHeight() + forkNaturalHeight() + 24.0f);
            return Math.max(2.0f, Math.min(scaleFromW, scaleFromH));
        }

        private static float lineNaturalWidth() {
            return LINE_TEXTURE != null ? LINE_TEXTURE.width() : 81.0f;
        }

        private static float lineNaturalHeight() {
            return LINE_TEXTURE != null ? LINE_TEXTURE.height() : 13.0f;
        }

        private static float socketNaturalWidth() {
            return SOCKET_TEXTURE != null ? SOCKET_TEXTURE.width() : 10.0f;
        }

        private static float socketNaturalHeight() {
            return SOCKET_TEXTURE != null ? SOCKET_TEXTURE.height() : 7.0f;
        }

        private static float forkNaturalWidth() {
            return WILKA_TEXTURE != null ? WILKA_TEXTURE.width() : 7.0f;
        }

        private static float forkNaturalHeight() {
            return WILKA_TEXTURE != null ? WILKA_TEXTURE.height() : 13.0f;
        }

        private static final class SparkParticle {
            float x;
            float y;
            float vx;
            float vy;
            float life;
            float maxLife;
            MutableColor color;
            float size;

            SparkParticle(float x, float y, float vx, float vy, float life, float maxLife, MutableColor color, float size) {
                this.x = x;
                this.y = y;
                this.vx = vx;
                this.vy = vy;
                this.life = life;
                this.maxLife = maxLife;
                this.color = color;
                this.size = size;
            }
        }

        private static final class ForkInSocketTextures {
            private static TextureHandle line;
            private static TextureHandle socket;
            private static TextureHandle wilka;
            private static boolean loadFailed;

            private ForkInSocketTextures() {
            }

            private static TextureHandle line() {
                ensureLoaded();
                return line;
            }

            private static TextureHandle socket() {
                ensureLoaded();
                return socket;
            }

            private static TextureHandle wilka() {
                ensureLoaded();
                return wilka;
            }

            private static void ensureLoaded() {
                if (line != null || loadFailed) return;
                try {
                    line = load("line", "line.png");
                    socket = load("socket", "socket.png");
                    wilka = load("wilka", "wilka.png");
                } catch (IOException | RuntimeException failure) {
                    loadFailed = true;
                }
            }

            private static TextureHandle load(String id, String fileName) throws IOException {
                String resource = "assets/stationarenear/textures/gui/fork_in_socket/" + fileName;
                ClassLoader loader = ForkInSocketMinigameScreen.class.getClassLoader();
                try (InputStream stream = loader.getResourceAsStream(resource)) {
                    if (stream == null) throw new IOException("Missing fork_in_socket texture resource: " + resource);
                    return UniGuiTextures.replace("stationarenear:dynamic/fork_in_socket/" + id,
                            NativeImage.read(stream),
                            TextureOptions.nearest());
                }
            }
        }
    }
}