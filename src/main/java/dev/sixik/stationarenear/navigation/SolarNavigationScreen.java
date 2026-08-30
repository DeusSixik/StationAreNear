package dev.sixik.stationarenear.navigation;

import com.mojang.blaze3d.platform.NativeImage;
import dev.sixik.unigui.api.core.FrameContext;
import dev.sixik.unigui.api.core.InvalidationFlags;
import dev.sixik.unigui.api.core.UIContext;
import dev.sixik.unigui.api.core.UnityLikeUIScaleProvider;
import dev.sixik.unigui.api.debug.DebugFlags;
import dev.sixik.unigui.api.input.KeyCodes;
import dev.sixik.unigui.api.input.KeyboardState;
import dev.sixik.unigui.api.layout.Alignment;
import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.math.RectView;
import dev.sixik.unigui.api.render.DrawPoint;
import dev.sixik.unigui.api.render.DrawScope;
import dev.sixik.unigui.api.render.TextureHandle;
import dev.sixik.unigui.api.render.TextureOptions;
import dev.sixik.unigui.api.render.UiRenderPolicy;
import dev.sixik.unigui.api.text.RichText;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftClipboardService;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftFonts;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftWidgetScreen;
import dev.sixik.unigui.backend.minecraft_impl.UniGuiTextures;
import dev.sixik.unigui.impl.core.DefaultUIContext;
import dev.sixik.unigui.widgets.world.WorldCanvas;
import dev.sixik.stationarenear.navigation.StationCodeGenerator;
import java.io.IOException;
import java.io.InputStream;
import dev.sixik.stationarenear.navigation.data.SolarNavigationDockedStation;
import dev.sixik.stationarenear.navigation.data.SolarNavigationQuestMarker;
import dev.sixik.stationarenear.navigation.data.SolarNavigationShipState;
import dev.sixik.stationarenear.navigation.network.SolarNavigationNetwork;
import dev.sixik.stationarenear.navigation.network.packet.ClearDockedSolarStationPacket;
import dev.sixik.stationarenear.navigation.network.packet.DockSolarStationPacket;
import dev.sixik.stationarenear.navigation.network.packet.UpdateSolarNavigationInputPacket;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class SolarNavigationScreen {
    private static SolarNavigationCanvas currentCanvas;
    private static final double BLOCK_UI_MAX_DISTANCE_SQ = 16.0D;

    private SolarNavigationScreen() {
    }

    public static void syncQuestMarkers(List<SolarNavigationQuestMarker> questMarkers) {
        if (currentCanvas != null) {
            currentCanvas.replaceQuestMarkers(questMarkers);
        }
    }

    public static void syncShipState(BlockPos terminalPos, SolarNavigationShipState state) {
        if (currentCanvas != null) {
            currentCanvas.applyServerState(terminalPos, state);
        }
    }

    public static void syncAsteroidOffset(BlockPos terminalPos, long asteroidSeed, float offsetX, float offsetY, float velocityX, float velocityY) {
        if (currentCanvas != null) {
            currentCanvas.applyServerAsteroidOffset(terminalPos, asteroidSeed, offsetX, offsetY, velocityX, velocityY);
        }
    }

    public static void openGui() {
        openGui(0x5EED_51A7L, BlockPos.ZERO, SolarNavigationShipState.DEFAULT, List.of(), List.of(), false, false);
    }

    public static void openGui(long seed, BlockPos terminalPos) {
        openGui(seed, terminalPos, SolarNavigationShipState.DEFAULT, List.of(), List.of(), false, false);
    }

    public static void openGui(long seed, BlockPos terminalPos, SolarNavigationShipState shipState, List<SolarNavigationQuestMarker> questMarkers, List<SolarNavigationDockedStation> restoredDockedStations) {
        openGui(seed, terminalPos, shipState, questMarkers, restoredDockedStations, false, false);
    }

    public static void openGui(long seed, BlockPos terminalPos, SolarNavigationShipState shipState, List<SolarNavigationQuestMarker> questMarkers, List<SolarNavigationDockedStation> restoredDockedStations, boolean hasManeuverability, boolean hasStationLocator) {
        UnityLikeUIScaleProvider scaleProvider = new UnityLikeUIScaleProvider()
                .referenceResolution(1920.0f, 1080.0f)
                .matchBalanced()
                .scaleRange(0.60f, 2.50f);
        DefaultUIContext context = new DefaultUIContext(new MinecraftClipboardService())
                .scaleProvider(scaleProvider);

        SolarNavigationCanvas canvas = new SolarNavigationCanvas(seed, terminalPos, shipState, questMarkers, restoredDockedStations, hasManeuverability, hasStationLocator);
        currentCanvas = canvas;
        canvas.layout(style -> style
                .align(Alignment.STRETCH, Alignment.STRETCH)
                .flexGrow(1.0f)
                .flexShrink(1.0f));


        MinecraftWidgetScreen screen = new MinecraftWidgetScreen(
                Component.translatable("screen.stationarenear.solar_navigation.title"), canvas, context) {
            @Override
            public void tick() {
                super.tick();
                if (shouldCloseBecauseTooFar(terminalPos)) {
                    onClose();
                }
            }

            @Override
            public void onClose() {
                if (currentCanvas == canvas) {
                    currentCanvas = null;
                }
                super.onClose();
            }

            @Override
            public boolean isPauseScreen() {
                return false;
            }
        };
        screen.renderPolicy(UiRenderPolicy.continuous());
        screen.scaleWithMinecraftGui(false);
        screen.postEffect(SolarNavigationPostEffects.retroTerminal(scaleProvider, canvas::impactInterference));
        Minecraft.getInstance().setScreen(screen);

    }
    private static boolean shouldCloseBecauseTooFar(BlockPos terminalPos) {
        if (terminalPos.equals(BlockPos.ZERO)) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return true;
        }

        return minecraft.player.distanceToSqr(Vec3.atCenterOf(terminalPos)) > BLOCK_UI_MAX_DISTANCE_SQ;
    }

    private static final class SolarNavigationCanvas extends WorldCanvas {
        private static final float WORLD_LIMIT = 1_000_000.0f;
        private static final float SHIP_RADIUS = 18.0f;
        private static final float SHIP_NOSE = 28.0f;
        private static final float SHIP_TAIL = 17.0f;
        private static final float SHIP_HALF_WIDTH = 12.0f;
        private static final float MAX_TURN_SPEED = 2.65f;
        private static final float TURN_ACCELERATION = 7.4f;
        private static final float TURN_DECAY = 0.035f;
        private static final float ASTEROID_PUSH_MULTIPLIER = 0.62f;
        private static final float ASTEROID_PUSH_PENETRATION_FORCE = 7.5f;
        private static final float ASTEROID_MAX_PUSH_OFFSET = 280.0f;
        private static final float ASTEROID_VISUAL_RETURN = 0.72f;
        private static final float ASTEROID_VISUAL_DAMPING = 0.26f;
        private static final float ASTEROID_VISUAL_HIT_COOLDOWN = 0.28f;
        private static final float IMPACT_INTERFERENCE_DECAY = 0.08f;
        private static final float CAMERA_ZOOM = 0.42f;
        private static final float FIRST_FRAME_DELTA = 1.0f / 60.0f;
        private static final float MAX_FRAME_DELTA = 0.25f;
        private static final float POSITION_SMOOTHING = 18.0f;
        private static final float VELOCITY_SMOOTHING = 16.0f;
        private static final float SNAP_DISTANCE = 900.0f;
        private static final float DOCK_HOLD_SECONDS = 5.0f;

        private static final MutableColor BACKGROUND_TOP = MutableColor.fromHex("#050814");
        private static final MutableColor BACKGROUND_BOTTOM = MutableColor.fromHex("#10172A");
        private static final MutableColor GRID_MAJOR = MutableColor.rgba(0.20f, 0.45f, 0.65f, 0.16f);
        private static final MutableColor GRID_MINOR = MutableColor.rgba(0.15f, 0.32f, 0.48f, 0.075f);
        private static final MutableColor HUD_BG = MutableColor.rgba(0.015f, 0.025f, 0.045f, 0.78f);
        private static final MutableColor HUD_BORDER = MutableColor.rgba(0.28f, 0.58f, 0.72f, 0.65f);
        private static final MutableColor CYAN = MutableColor.fromHex("#60D8FF");
        private static final MutableColor CYAN_DIM = MutableColor.rgba(0.30f, 0.86f, 1.0f, 0.40f);
        private static final MutableColor AMBER = MutableColor.fromHex("#F7C45A");
        private static final MutableColor ORANGE = MutableColor.fromHex("#FF8A4C");
        private static final MutableColor RED = MutableColor.fromHex("#FF5F6F");
        private static final MutableColor WHITE = MutableColor.fromHex("#EAF7FF");
        private static final MutableColor MUTED = MutableColor.rgba(0.70f, 0.82f, 0.92f, 0.72f);
        private static final MutableColor ASTEROID = MutableColor.fromHex("#6C7482");
        private static final MutableColor ASTEROID_DARK = MutableColor.fromHex("#323945");
        private static final MutableColor STATION = MutableColor.fromHex("#8AE6FF");
        private static final MutableColor STATION_FILL = MutableColor.rgba(0.10f, 0.38f, 0.52f, 0.58f);
        private static final MutableColor STATION_ROOM_FILL = MutableColor.rgba(0.035f, 0.115f, 0.135f, 0.82f);
        private static final MutableColor STATION_ROOM_SHADOW = MutableColor.rgba(0.0f, 0.0f, 0.0f, 0.34f);
        private static final MutableColor STAR = MutableColor.rgba(0.88f, 0.96f, 1.0f, 0.55f);

        private final List<Asteroid> asteroids = new ObjectArrayList<>();
        private final Long2ObjectMap<AsteroidOffset> asteroidOffsets = new Long2ObjectOpenHashMap<>();
        private final List<Station> stations = new ObjectArrayList<>();
        private final List<Star> stars = new ObjectArrayList<>();
        private final List<SolarNavigationQuestMarker> questMarkers = new ObjectArrayList<>();
        private final LongOpenHashSet dockedStations = new LongOpenHashSet();
        private final List<DockedStation> activeDockedStations = new ObjectArrayList<>();
        private final long seed;
        private final BlockPos terminalPos;

        private int generatedMinSectorX = Integer.MIN_VALUE;
        private int generatedMaxSectorX = Integer.MIN_VALUE;
        private int generatedMinSectorY = Integer.MIN_VALUE;
        private int generatedMaxSectorY = Integer.MIN_VALUE;
        private float shipX;
        private float shipY;
        private float velocityX;
        private float velocityY;
        private float angle = 0.0f;
        private float turnVelocity;
        private float renderShipX;
        private float renderShipY;
        private float renderVelocityX;
        private float renderVelocityY;
        private float renderAngle = 0.0f;
        private boolean renderStateInitialized;
        private float timeSeconds;
        private long lastFrameNanos;
        private float inputSyncSeconds;
        private int lastInputMask = -1;
        private float dockMessageSeconds;
        private String dockMessage = net.minecraft.client.resources.language.I18n.get("screen.stationarenear.solar_navigation.find_quest_station");
        private float dockingProgressSeconds;
        private float impactInterference;
        private long dockingTargetSeed = Long.MIN_VALUE;
        private String dockingTargetName = "";
        private final boolean hasManeuverability;
        private final boolean hasStationLocator;

        private SolarNavigationCanvas(long seed, BlockPos terminalPos, SolarNavigationShipState initialState, List<SolarNavigationQuestMarker> questMarkers, List<SolarNavigationDockedStation> restoredDockedStations, boolean hasManeuverability, boolean hasStationLocator) {
            this.seed = seed;
            this.terminalPos = terminalPos;
            this.hasManeuverability = hasManeuverability;
            this.hasStationLocator = hasStationLocator;
            this.questMarkers.addAll(questMarkers);
            for (SolarNavigationDockedStation station : restoredDockedStations) {
                dockedStations.add(station.seed());
                activeDockedStations.add(new DockedStation(station.seed(), station.name(), station.code(), station.x(), station.y()));
            }
            shipX = initialState.shipX();
            shipY = initialState.shipY();
            velocityX = initialState.velocityX();
            velocityY = initialState.velocityY();
            angle = initialState.angle();
            snapRenderState();
            preferredSize(1280.0f, 720.0f);
            viewport(0.0f, 0.0f, CAMERA_ZOOM);
            worldBounds(-WORLD_LIMIT, -WORLD_LIMIT, WORLD_LIMIT * 2.0f, WORLD_LIMIT * 2.0f);
            panningEnabled(false);
            zoomEnabled(false);
            wheelPanningEnabled(false);
            consumeWheelWhileHovered(false);
            addWorldLayer(this::renderScene);
            refreshDynamicObjects(true);
        }

        @Override
        public void tick(FrameContext frame) {
            super.tick(frame);
            float frameDelta = nextFrameDelta();
            timeSeconds += frameDelta;

            KeyboardState keyboard = uiContext() == null ? KeyboardState.NONE : uiContext().keyboard();
            int turnAxis = turnAxis(keyboard);
            predictLocalRotation(turnAxis, frameDelta);
            updateDockingProgress(keyboard, frameDelta);
            updateImpactInterference(frameDelta);
            syncInput(keyboard, frameDelta);
            updateRenderState(frameDelta);

            updateCamera();
            refreshDynamicObjects(false);
            updateAsteroidOffsets(frameDelta);

            if (dockMessageSeconds > 0.0f) {
                dockMessageSeconds = Math.max(0.0f, dockMessageSeconds - frameDelta);
            }
            invalidate(InvalidationFlags.VISUAL);
        }

        private void applyServerState(BlockPos terminalPos, SolarNavigationShipState state) {
            if (!this.terminalPos.equals(terminalPos)) {
                return;
            }
            shipX = state.shipX();
            shipY = state.shipY();
            velocityX = state.velocityX();
            velocityY = state.velocityY();
            angle = state.angle();
            if (!renderStateInitialized || distanceSquared(renderShipX, renderShipY, shipX, shipY) > SNAP_DISTANCE * SNAP_DISTANCE) {
                snapRenderState();
            }
        }

        private void updateRenderState(float delta) {
            renderShipX = smooth(renderShipX, shipX, POSITION_SMOOTHING, delta);
            renderShipY = smooth(renderShipY, shipY, POSITION_SMOOTHING, delta);
            renderVelocityX = smooth(renderVelocityX, velocityX, VELOCITY_SMOOTHING, delta);
            renderVelocityY = smooth(renderVelocityY, velocityY, VELOCITY_SMOOTHING, delta);
            renderAngle = angle;
        }

        private void snapRenderState() {
            renderShipX = shipX;
            renderShipY = shipY;
            renderVelocityX = velocityX;
            renderVelocityY = velocityY;
            renderAngle = angle;
            renderStateInitialized = true;
        }

        private void syncInput(KeyboardState keyboard, float delta) {
            inputSyncSeconds += delta;
            int inputMask = inputMask(keyboard);
            if (inputMask == lastInputMask && inputSyncSeconds < 0.05f) {
                return;
            }
            inputSyncSeconds = 0.0f;
            lastInputMask = inputMask;
            SolarNavigationNetwork.sendInput(terminalPos, inputMask);
        }

        private int inputMask(KeyboardState keyboard) {
            int mask = 0;
            if (keyboard.isDown(KeyCodes.W)) {
                mask |= UpdateSolarNavigationInputPacket.FORWARD;
            }
            if (keyboard.isDown(KeyCodes.S)) {
                mask |= UpdateSolarNavigationInputPacket.BACKWARD;
            }
            if (keyboard.isDown(KeyCodes.A)) {
                mask |= UpdateSolarNavigationInputPacket.LEFT;
            }
            if (keyboard.isDown(KeyCodes.D)) {
                mask |= UpdateSolarNavigationInputPacket.RIGHT;
            }
            return mask;
        }

        private float nextFrameDelta() {
            long now = System.nanoTime();
            if (lastFrameNanos == 0L) {
                lastFrameNanos = now;
                return FIRST_FRAME_DELTA;
            }
            float delta = (now - lastFrameNanos) / 1_000_000_000.0f;
            lastFrameNanos = now;
            return clamp(delta, 0.0f, MAX_FRAME_DELTA);
        }

        private void replaceQuestMarkers(List<SolarNavigationQuestMarker> questMarkers) {
            this.questMarkers.clear();
            this.questMarkers.addAll(questMarkers);
            refreshDynamicObjects(true);
            invalidate(InvalidationFlags.VISUAL);
        }

        private void refreshDynamicObjects(boolean force) {
            int sectorSize = SolarNavigationConfig.SECTOR_SIZE.get();
            SectorRange range = visibleSectorRange(sectorSize);
            if (!force
                    && range.minX() == generatedMinSectorX
                    && range.maxX() == generatedMaxSectorX
                    && range.minY() == generatedMinSectorY
                    && range.maxY() == generatedMaxSectorY) {
                return;
            }

            generatedMinSectorX = range.minX();
            generatedMaxSectorX = range.maxX();
            generatedMinSectorY = range.minY();
            generatedMaxSectorY = range.maxY();
            asteroids.clear();
            stations.clear();
            stars.clear();

            for (int x = range.minX(); x <= range.maxX(); x++) {
                for (int y = range.minY(); y <= range.maxY(); y++) {
                    generateSector(x, y, sectorSize);
                }
            }

            for (SolarNavigationQuestMarker marker : questMarkers) {
                stations.add(new Station(
                        marker.x(),
                        marker.y(),
                        marker.name(),
                        StationCodeGenerator.code(marker.seed(), marker.x(), marker.y()),
                        marker.radius(),
                        true,
                        marker.seed(),
                        createDungeonRooms(marker.seed(), true),
                        marker.color()
                ));
            }
        }

        private SectorRange visibleSectorRange(int sectorSize) {
            int padding = SolarNavigationConfig.SECTOR_RENDER_RADIUS.get();
            RectView bounds = layoutBounds();
            if (bounds.width() <= 0.0f || bounds.height() <= 0.0f) {
                int sectorX = floorDiv(shipX, sectorSize);
                int sectorY = floorDiv(shipY, sectorSize);
                return new SectorRange(sectorX - padding, sectorX + padding, sectorY - padding, sectorY + padding);
            }

            float minWorldX = rootToWorldX(bounds.x());
            float maxWorldX = rootToWorldX(bounds.x() + bounds.width());
            float minWorldY = rootToWorldY(bounds.y());
            float maxWorldY = rootToWorldY(bounds.y() + bounds.height());
            return new SectorRange(
                    floorDiv(Math.min(minWorldX, maxWorldX), sectorSize) - padding,
                    floorDiv(Math.max(minWorldX, maxWorldX), sectorSize) + padding,
                    floorDiv(Math.min(minWorldY, maxWorldY), sectorSize) - padding,
                    floorDiv(Math.max(minWorldY, maxWorldY), sectorSize) + padding
            );
        }

        private void generateSector(int sectorX, int sectorY, int sectorSize) {
            Random random = new Random(sectorSeed(sectorX, sectorY, 0x51A7_EC70_5EEDL));
            float minX = sectorX * (float) sectorSize;
            float minY = sectorY * (float) sectorSize;
            for (int i = 0; i < SolarNavigationConfig.STARS_PER_SECTOR.get(); i++) {
                stars.add(new Star(
                        minX + random.nextFloat() * sectorSize,
                        minY + random.nextFloat() * sectorSize,
                        randomRange(random, 0.8f, 2.2f),
                        randomRange(random, 0.28f, 0.95f)));
            }

            long asteroidSectorSeed = sectorSeed(sectorX, sectorY, 0xA57E_201DL);
            float asteroidMinRadius = Math.min(SolarNavigationConfig.ASTEROID_MIN_RADIUS.get().floatValue(), SolarNavigationConfig.ASTEROID_MAX_RADIUS.get().floatValue());
            float asteroidMaxRadius = Math.max(SolarNavigationConfig.ASTEROID_MIN_RADIUS.get().floatValue(), SolarNavigationConfig.ASTEROID_MAX_RADIUS.get().floatValue());
            for (int i = 0; i < SolarNavigationConfig.ASTEROIDS_PER_SECTOR.get(); i++) {
                long asteroidSeed = asteroidSectorSeed ^ (long) i * 0x9E37_79B9_7F4A_7C15L;
                Random asteroidRandom = new Random(asteroidSeed);
                asteroids.add(new Asteroid(
                        minX + randomRange(asteroidRandom, sectorSize * 0.08f, sectorSize * 0.92f),
                        minY + randomRange(asteroidRandom, sectorSize * 0.08f, sectorSize * 0.92f),
                        randomRange(asteroidRandom, asteroidMinRadius, asteroidMaxRadius),
                        randomRange(asteroidRandom, 0.0f, (float) Math.PI * 2.0f),
                        9 + asteroidRandom.nextInt(6),
                        asteroidSeed));
            }

            SolarNavigationProceduralMap.randomStation(
                    seed,
                    new SolarNavigationShipState(shipX, shipY, velocityX, velocityY, angle),
                    sectorX,
                    sectorY,
                    sectorSize,
                    Float.MAX_VALUE
            ).ifPresent(station -> stations.add(new Station(
                    station.x(),
                    station.y(),
                    station.name(),
                    station.code(),
                    station.radius(),
                    false,
                    station.seed(),
                    createDungeonRooms(station.seed(), false),
                    station.color()
            )));
        }

        private int turnAxis(KeyboardState keyboard) {
            return (keyboard.isDown(KeyCodes.D) ? 1 : 0) - (keyboard.isDown(KeyCodes.A) ? 1 : 0);
        }

        private void predictLocalRotation(int turnAxis, float delta) {
            if (hasManeuverability) {
                if (turnAxis != 0) {
                    turnVelocity = turnAxis * MAX_TURN_SPEED;
                } else {
                    turnVelocity = 0.0f;
                }
            } else {
                if (turnAxis != 0) {
                    turnVelocity += turnAxis * TURN_ACCELERATION * delta;
                    turnVelocity = clamp(turnVelocity, -MAX_TURN_SPEED, MAX_TURN_SPEED);
                } else if (turnVelocity != 0.0f) {
                    turnVelocity *= (float) Math.pow(TURN_DECAY, delta);
                    if (Math.abs(turnVelocity) < 0.01f) {
                        turnVelocity = 0.0f;
                    }
                }
            }
            angle = wrapRadians(angle + turnVelocity * delta);
        }

        private void applyServerAsteroidOffset(BlockPos terminalPos, long asteroidSeed, float offsetX, float offsetY, float velocityX, float velocityY) {
            if (!this.terminalPos.equals(terminalPos)) {
                return;
            }
            AsteroidOffset offset = asteroidOffsets.computeIfAbsent(asteroidSeed, ignored -> new AsteroidOffset());
            float previousSpeed = (float) Math.sqrt(offset.velocityX * offset.velocityX + offset.velocityY * offset.velocityY);
            float newSpeed = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);
            triggerImpactInterference(Math.max(0.0f, newSpeed - previousSpeed));
            offset.offsetX = offsetX;
            offset.offsetY = offsetY;
            offset.velocityX = velocityX;
            offset.velocityY = velocityY;
            offset.hitCooldown = ASTEROID_VISUAL_HIT_COOLDOWN;
            offset.clampOffset();
        }

        private float impactInterference() {
            return impactInterference;
        }

        private void updateImpactInterference(float delta) {
            if (impactInterference <= 0.0f) {
                return;
            }
            impactInterference *= (float) Math.pow(IMPACT_INTERFERENCE_DECAY, delta);
            if (impactInterference < 0.01f) {
                impactInterference = 0.0f;
            }
        }

        private void triggerImpactInterference(float impulse) {
            if (impulse < 25.0f) {
                return;
            }
            float strength = clamp((impulse - 25.0f) / 260.0f, 0.16f, 1.0f);
            impactInterference = clamp(impactInterference + strength, 0.0f, 1.0f);
        }

        private void updateAsteroidOffsets(float delta) {
            tickAsteroidOffsets(delta);
            for (Asteroid asteroid : asteroids) {
                float asteroidX = asteroidVisualX(asteroid);
                float asteroidY = asteroidVisualY(asteroid);
                float minDistance = asteroid.radius() + SHIP_RADIUS;
                float dx = shipX - asteroidX;
                float dy = shipY - asteroidY;
                float distanceSq = dx * dx + dy * dy;
                if (distanceSq <= 0.001f || distanceSq >= minDistance * minDistance) continue;

                AsteroidOffset offset = asteroidOffsets.computeIfAbsent(asteroid.seed(), ignored -> new AsteroidOffset());
                if (offset.hitCooldown > 0.0f) continue;

                float distance = (float) Math.sqrt(distanceSq);
                float normalX = dx / distance;
                float normalY = dy / distance;
                float impactSpeed = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);
                float dot = velocityX * normalX + velocityY * normalY;
                pushAsteroid(asteroid.seed(), -normalX, -normalY, dot < 0.0f ? -dot : impactSpeed * 0.18f, minDistance - distance);
                offset.hitCooldown = ASTEROID_VISUAL_HIT_COOLDOWN;
            }
        }

        private void tickAsteroidOffsets(float delta) {
            Iterator<Map.Entry<Long, AsteroidOffset>> iterator = asteroidOffsets.entrySet().iterator();
            while (iterator.hasNext()) {
                AsteroidOffset offset = iterator.next().getValue();
                offset.tick(delta);
                if (offset.isIdle()) {
                    iterator.remove();
                }
            }
        }

        private void pushAsteroid(long asteroidSeed, float directionX, float directionY, float impactSpeed, float penetration) {
            AsteroidOffset offset = asteroidOffsets.computeIfAbsent(asteroidSeed, ignored -> new AsteroidOffset());
            float impulse = impactSpeed * ASTEROID_PUSH_MULTIPLIER + penetration * ASTEROID_PUSH_PENETRATION_FORCE;
            offset.velocityX += directionX * impulse;
            offset.velocityY += directionY * impulse;
            offset.offsetX += directionX * Math.min(10.0f, penetration * 0.35f);
            offset.offsetY += directionY * Math.min(10.0f, penetration * 0.35f);
            offset.clampOffset();
            triggerImpactInterference(impulse);
        }

        private float asteroidVisualX(Asteroid asteroid) {
            AsteroidOffset offset = asteroidOffsets.get(asteroid.seed());
            return asteroid.x() + (offset == null ? 0.0f : offset.offsetX);
        }

        private float asteroidVisualY(Asteroid asteroid) {
            AsteroidOffset offset = asteroidOffsets.get(asteroid.seed());
            return asteroid.y() + (offset == null ? 0.0f : offset.offsetY);
        }

        private void clearFarDockedStations() {
            if (activeDockedStations.isEmpty()) {
                return;
            }
            float unloadDistance = SolarNavigationConfig.STATION_UNLOAD_DISTANCE.get().floatValue();
            float unloadDistanceSq = unloadDistance * unloadDistance;
            Iterator<DockedStation> iterator = activeDockedStations.iterator();
            while (iterator.hasNext()) {
                DockedStation station = iterator.next();
                if (distanceSquared(shipX, shipY, station.x(), station.y()) <= unloadDistanceSq) {
                    continue;
                }
                SolarNavigationNetwork.sendClearDockedStation(new ClearDockedSolarStationPacket(terminalPos, station.name(), station.code(), station.seed()));
                dockedStations.remove(station.seed());
                iterator.remove();
            }
        }

        private void updateCamera() {
            RectView bounds = layoutBounds();
            if (bounds.width() <= 0.0f || bounds.height() <= 0.0f) return;
            viewport(bounds.width() * 0.5f - renderShipX * CAMERA_ZOOM,
                    bounds.height() * 0.5f - renderShipY * CAMERA_ZOOM,
                    CAMERA_ZOOM);
        }

        private void updateDockingProgress(KeyboardState keyboard, float delta) {
            if (!keyboard.isDown(KeyCodes.SPACE)) {
                resetDockingProgress();
                return;
            }

            Station nearest = nearestDockingTarget();
            if (nearest == null) {
                resetDockingProgress();
                if (keyboard.wasPressed(KeyCodes.SPACE)) {
                    dockMessage = net.minecraft.client.resources.language.I18n.get("screen.stationarenear.solar_navigation.no_target");
                    dockMessageSeconds = 1.5f;
                }
                return;
            }

            if (dockedStations.contains(nearest.seed())) {
                resetDockingProgress();
                if (keyboard.wasPressed(KeyCodes.SPACE)) {
                    dockMessage = net.minecraft.client.resources.language.I18n.get("screen.stationarenear.solar_navigation.already_docked", nearest.name());
                    dockMessageSeconds = 1.8f;
                }
                return;
            }

            if (dockingTargetSeed != nearest.seed()) {
                dockingTargetSeed = nearest.seed();
                dockingTargetName = nearest.name();
                dockingProgressSeconds = 0.0f;
            }

            dockingProgressSeconds = Math.min(DOCK_HOLD_SECONDS, dockingProgressSeconds + delta);
            if (dockingProgressSeconds >= DOCK_HOLD_SECONDS) {
                completeDock(nearest);
                resetDockingProgress();
            }
        }

        private Station nearestDockingTarget() {
            Station nearest = null;
            float nearestDistanceSq = Float.MAX_VALUE;
            for (Station station : stations) {
                float distanceSq = distanceSquared(shipX, shipY, station.x(), station.y());
                float dockRadius = station.radius() + 42.0f;
                if (distanceSq <= dockRadius * dockRadius && distanceSq < nearestDistanceSq) {
                    nearest = station;
                    nearestDistanceSq = distanceSq;
                }
            }
            return nearest;
        }

        private void completeDock(Station station) {
            dockedStations.add(station.seed());
            activeDockedStations.add(new DockedStation(station.seed(), station.name(), station.code(), station.x(), station.y()));
            dev.sixik.stationarenear.navigation.client.StationDockingOverlay.show(station.name(), station.code(), 8000L);
            SolarNavigationNetwork.sendDock(new DockSolarStationPacket(terminalPos, station.name(), station.code(), station.seed(), station.quest(), station.x(), station.y()));
            dockMessage = station.quest()
                    ? net.minecraft.client.resources.language.I18n.get("screen.stationarenear.solar_navigation.docking_request_quest", station.code())
                    : net.minecraft.client.resources.language.I18n.get("screen.stationarenear.solar_navigation.docking_request", station.code());
            dockMessageSeconds = 3.0f;
            velocityX = 0.0f;
            velocityY = 0.0f;
            renderVelocityX = 0.0f;
            renderVelocityY = 0.0f;
        }

        private void resetDockingProgress() {
            dockingProgressSeconds = 0.0f;
            dockingTargetSeed = Long.MIN_VALUE;
            dockingTargetName = "";
        }

        private void renderScene(WorldCanvas canvas, DrawScope draw) {
            RectView b = canvas.layoutBounds();
            float x = b.x();
            float y = b.y();
            float w = b.width();
            float h = b.height();
            if (w <= 0.0f || h <= 0.0f) return;

            draw.addRectFilledMultiColor(x, y, w, h, BACKGROUND_TOP, BACKGROUND_TOP, BACKGROUND_BOTTOM, BACKGROUND_BOTTOM);
            drawGrid(canvas, draw, x, y, w, h);
            drawStars(canvas, draw);
            drawAsteroids(canvas, draw);
            drawStations(canvas, draw);
            drawQuestEdgeMarkers(canvas, draw, x, y, w, h);
            drawShip(canvas, draw);
            drawHud(draw, x, y, w, h);
            drawDockingProgress(draw, x, y, w, h);
        }

        private void drawGrid(WorldCanvas canvas, DrawScope draw, float x, float y, float w, float h) {
            float minWorldX = canvas.rootToWorldX(x);
            float maxWorldX = canvas.rootToWorldX(x + w);
            float minWorldY = canvas.rootToWorldY(y);
            float maxWorldY = canvas.rootToWorldY(y + h);

            drawWorldGrid(canvas, draw, minWorldX, maxWorldX, minWorldY, maxWorldY, 100.0f, GRID_MINOR, 1.0f);
            drawWorldGrid(canvas, draw, minWorldX, maxWorldX, minWorldY, maxWorldY, 500.0f, GRID_MAJOR, 1.25f);
        }

        private void drawWorldGrid(WorldCanvas canvas, DrawScope draw,
                                   float minWorldX, float maxWorldX,
                                   float minWorldY, float maxWorldY,
                                   float step, MutableColor color, float thickness) {
            float firstX = (float) Math.floor(minWorldX / step) * step;
            for (float gx = firstX; gx <= maxWorldX; gx += step) {
                float rootX = canvas.worldToRootX(gx);
                draw.addLine(rootX, canvas.worldToRootY(minWorldY), rootX, canvas.worldToRootY(maxWorldY), color, thickness);
            }
            float firstY = (float) Math.floor(minWorldY / step) * step;
            for (float gy = firstY; gy <= maxWorldY; gy += step) {
                float rootY = canvas.worldToRootY(gy);
                draw.addLine(canvas.worldToRootX(minWorldX), rootY, canvas.worldToRootX(maxWorldX), rootY, color, thickness);
            }
        }

        private void drawStars(WorldCanvas canvas, DrawScope draw) {
            for (Star star : stars) {
                float sx = canvas.worldToRootX(star.x());
                float sy = canvas.worldToRootY(star.y());
                float alphaPulse = 0.65f + 0.35f * (float) Math.sin(timeSeconds * 1.7f + star.x() * 0.017f);
                MutableColor color = MutableColor.rgba(STAR.r(), STAR.g(), STAR.b(), STAR.a() * star.alpha() * alphaPulse);
                draw.addCircleFilled(sx, sy, star.size(), color, 8);
            }
        }

        private void drawAsteroids(WorldCanvas canvas, DrawScope draw) {
            for (Asteroid asteroid : asteroids) {
                float sx = canvas.worldToRootX(asteroidVisualX(asteroid));
                float sy = canvas.worldToRootY(asteroidVisualY(asteroid));
                float radius = asteroid.radius() * canvas.viewport().zoom();
                if (!visible(canvas.layoutBounds(), sx, sy, radius + 6.0f)) continue;

                TextureHandle texture = SpaceTextures.forAsteroid(asteroid.seed());
                if (texture != null) {
                    float size = radius * 2.0f;
                    drawRotatedImage(draw, texture, sx, sy, size, size, asteroid.rotation());
                } else {
                    draw.addCircleFilled(sx + 3.0f, sy + 4.0f, radius + 1.0f, MutableColor.rgba(0.0f, 0.0f, 0.0f, 0.28f), asteroid.segments());
                    draw.addNgonFilled(sx, sy, radius, ASTEROID, asteroid.segments());
                    draw.addNgon(sx, sy, radius, ASTEROID_DARK, asteroid.segments(), 2.0f);
                    draw.addCircleFilled(sx - radius * 0.23f, sy - radius * 0.18f, Math.max(2.0f, radius * 0.18f), ASTEROID_DARK, 8);
                }
            }
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

        private static DrawPoint rotated(float originX, float originY, float angle, float localX, float localY) {
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            return new DrawPoint(originX + localX * cos - localY * sin, originY + localX * sin + localY * cos);
        }

        private void drawStations(WorldCanvas canvas, DrawScope draw) {
            for (Station station : stations) {
                float sx = canvas.worldToRootX(station.x());
                float sy = canvas.worldToRootY(station.y());
                float radius = station.radius() * canvas.viewport().zoom();
                if (!visible(canvas.layoutBounds(), sx, sy, radius + 80.0f)) continue;

                MutableColor ring = station.quest() ? colorFromArgb(station.color()) : STATION;
                float pulse = station.quest() ? 1.0f + 0.10f * (float) Math.sin(timeSeconds * 4.0f) : 1.0f;
                float r = radius * pulse;
                drawDungeonPreview(canvas, draw, station, ring);
                draw.addCircleFilled(sx, sy, Math.max(4.0f, r * 0.22f), STATION_FILL, 18);
                draw.addCircle(sx, sy, Math.max(6.0f, r * 0.30f), ring, 24, station.quest() ? 2.2f : 1.35f);

                text(draw, station.code(), sx - 120.0f, sy - r - 36.0f, 240.0f, 20.0f, 13.0f,
                        station.quest() ? ring : WHITE);
            }
        }

        private void drawDungeonPreview(WorldCanvas canvas, DrawScope draw, Station station, MutableColor border) {
            float zoom = canvas.viewport().zoom();
            for (DungeonRoom room : station.dungeonRooms()) {
                float left = canvas.worldToRootX(station.x() + room.x());
                float top = canvas.worldToRootY(station.y() + room.y());
                float width = room.width() * zoom;
                float height = room.height() * zoom;
                float lineWidth = room.core() ? 1.8f : 1.25f;
                MutableColor fill = room.core()
                        ? MutableColor.rgba(STATION_FILL.r(), STATION_FILL.g(), STATION_FILL.b(), 0.74f)
                        : STATION_ROOM_FILL;

                draw.addRectFilled(left + 2.0f, top + 2.0f, width, height, 1.5f, STATION_ROOM_SHADOW);
                draw.addRectFilled(left, top, width, height, 1.5f, fill);
                draw.addRect(left, top, width, height, 1.5f, border, lineWidth);
            }
        }

        private void drawShip(WorldCanvas canvas, DrawScope draw) {
            float dirX = (float) Math.cos(renderAngle);
            float dirY = (float) Math.sin(renderAngle);
            float sideX = -dirY;
            float sideY = dirX;

            float noseX = canvas.worldToRootX(renderShipX + dirX * SHIP_NOSE);
            float noseY = canvas.worldToRootY(renderShipY + dirY * SHIP_NOSE);
            float leftX = canvas.worldToRootX(renderShipX - dirX * SHIP_TAIL + sideX * SHIP_HALF_WIDTH);
            float leftY = canvas.worldToRootY(renderShipY - dirY * SHIP_TAIL + sideY * SHIP_HALF_WIDTH);
            float rightX = canvas.worldToRootX(renderShipX - dirX * SHIP_TAIL - sideX * SHIP_HALF_WIDTH);
            float rightY = canvas.worldToRootY(renderShipY - dirY * SHIP_TAIL - sideY * SHIP_HALF_WIDTH);
            float centerX = canvas.worldToRootX(renderShipX);
            float centerY = canvas.worldToRootY(renderShipY);

            float speed = (float) Math.sqrt(renderVelocityX * renderVelocityX + renderVelocityY * renderVelocityY);
            if (speed > 18.0f) {
                float velocityDirX = renderVelocityX / speed;
                float velocityDirY = renderVelocityY / speed;
                float trailLength = clamp(speed * 0.12f, 18.0f, 74.0f);
                float slip = Math.abs(dirX * velocityDirY - dirY * velocityDirX);
                draw.addLine(centerX - velocityDirX * 12.0f, centerY - velocityDirY * 12.0f,
                        centerX - velocityDirX * trailLength, centerY - velocityDirY * trailLength,
                        MutableColor.rgba(0.38f, 0.86f, 1.0f, 0.35f + Math.min(0.25f, slip * 0.35f)), 3.0f + slip * 2.0f);
                if (slip > 0.16f) {
                    float driftSideX = -velocityDirY;
                    float driftSideY = velocityDirX;
                    draw.addLine(centerX - driftSideX * SHIP_HALF_WIDTH * 0.85f, centerY - driftSideY * SHIP_HALF_WIDTH * 0.85f,
                            centerX - velocityDirX * trailLength * 0.62f - driftSideX * SHIP_HALF_WIDTH * 1.45f,
                            centerY - velocityDirY * trailLength * 0.62f - driftSideY * SHIP_HALF_WIDTH * 1.45f,
                            MutableColor.rgba(0.65f, 0.92f, 1.0f, Math.min(0.42f, slip * 0.55f)), 1.6f);
                    draw.addLine(centerX + driftSideX * SHIP_HALF_WIDTH * 0.85f, centerY + driftSideY * SHIP_HALF_WIDTH * 0.85f,
                            centerX - velocityDirX * trailLength * 0.62f + driftSideX * SHIP_HALF_WIDTH * 1.45f,
                            centerY - velocityDirY * trailLength * 0.62f + driftSideY * SHIP_HALF_WIDTH * 1.45f,
                            MutableColor.rgba(0.65f, 0.92f, 1.0f, Math.min(0.42f, slip * 0.55f)), 1.6f);
                }
            }

            draw.addTriangleFilled(new DrawPoint(noseX, noseY), new DrawPoint(leftX, leftY), new DrawPoint(rightX, rightY), CYAN);
            draw.addTriangle(new DrawPoint(noseX, noseY), new DrawPoint(leftX, leftY), new DrawPoint(rightX, rightY), WHITE, 1.25f);
            draw.addCircle(centerX, centerY, SHIP_RADIUS * 0.9f, CYAN_DIM, 28, 1.0f);
        }

        private void drawQuestEdgeMarkers(WorldCanvas canvas, DrawScope draw, float x, float y, float w, float h) {
            for (Station station : stations) {
                if (!station.quest()) {
                    continue;
                }
                float targetX = canvas.worldToRootX(station.x());
                float targetY = canvas.worldToRootY(station.y());
                float margin = 28.0f;
                if (targetX >= x + margin && targetX <= x + w - margin && targetY >= y + margin && targetY <= y + h - margin) {
                    continue;
                }

                float centerX = x + w * 0.5f;
                float centerY = y + h * 0.5f;
                float dx = targetX - centerX;
                float dy = targetY - centerY;
                float length = Math.max(0.001f, (float) Math.sqrt(dx * dx + dy * dy));
                dx /= length;
                dy /= length;

                float edgeX = centerX + dx * (w * 0.5f - margin);
                float edgeY = centerY + dy * (h * 0.5f - margin);
                edgeX = clamp(edgeX, x + margin, x + w - margin);
                edgeY = clamp(edgeY, y + margin, y + h - margin);

                MutableColor color = colorFromArgb(station.color());
                float sideX = -dy;
                float sideY = dx;
                DrawPoint p1 = new DrawPoint(edgeX + dx * 16.0f, edgeY + dy * 16.0f);
                DrawPoint p2 = new DrawPoint(edgeX - dx * 12.0f + sideX * 10.0f, edgeY - dy * 12.0f + sideY * 10.0f);
                DrawPoint p3 = new DrawPoint(edgeX - dx * 12.0f - sideX * 10.0f, edgeY - dy * 12.0f - sideY * 10.0f);
                draw.addTriangleFilled(p1, p2, p3, color);
                draw.addTriangle(p1, p2, p3, MutableColor.rgba(1.0f, 1.0f, 1.0f, 0.70f), 1.0f);

                float distance = (float) Math.sqrt(distanceSquared(shipX, shipY, station.x(), station.y()));
                text(draw, Math.round(distance) + "u", edgeX - 34.0f, edgeY + 18.0f, 68.0f, 18.0f, 12.0f, color);
            }
        }

        private void drawHud(DrawScope draw, float x, float y, float w, float h) {
            float panelW = 390.0f;
            float panelX = x + (w - panelW) * 0.5f;
            float panelY = y + 18.0f;
            float panelH = dockMessageSeconds > 0.0f ? 134.0f : 112.0f;
            draw.addRectFilled(panelX, panelY, panelW, panelH, 8.0f, HUD_BG);
            draw.addRect(panelX, panelY, panelW, panelH, 8.0f, HUD_BORDER, 1.25f);
            text(draw, net.minecraft.client.resources.language.I18n.get("screen.stationarenear.solar_navigation.hud_title"), panelX + 16.0f, panelY + 12.0f, panelW - 32.0f, 18.0f, 14.0f, CYAN);

            float speed = (float) Math.sqrt(renderVelocityX * renderVelocityX + renderVelocityY * renderVelocityY);
            text(draw, net.minecraft.client.resources.language.I18n.get("screen.stationarenear.solar_navigation.controls"), panelX + 16.0f, panelY + 38.0f, panelW - 32.0f, 18.0f, 11.0f, MUTED);
            text(draw, net.minecraft.client.resources.language.I18n.get("screen.stationarenear.solar_navigation.speed_seed", Math.round(speed), Long.toHexString(seed).toUpperCase()),
                    panelX + 16.0f, panelY + 62.0f, panelW - 32.0f, 18.0f, 11.0f, WHITE);

            Station nearestQuest = nearestQuestStation();
            if (nearestQuest != null) {
                float questDistance = (float) Math.sqrt(distanceSquared(shipX, shipY, nearestQuest.x(), nearestQuest.y()));
                text(draw, net.minecraft.client.resources.language.I18n.get("screen.stationarenear.solar_navigation.quest_dock", nearestQuest.name(), Math.round(questDistance)),
                        panelX + 16.0f, panelY + 84.0f, panelW - 32.0f, 18.0f, 11.0f, colorFromArgb(nearestQuest.color()));
            }
            if (dockMessageSeconds > 0.0f) {
                text(draw, dockMessage, panelX + 16.0f, panelY + 108.0f, panelW - 32.0f, 18.0f, 12.0f,
                        dockMessage.contains("impact") || dockMessage.contains("No ") ? RED : ORANGE);
            }

            if (hasStationLocator) {
                drawRadar(draw, radarX(x, w, h), radarY(y), radarSize(w, h));
            }
        }

        private void drawDockingProgress(DrawScope draw, float x, float y, float w, float h) {
            if (dockingTargetSeed == Long.MIN_VALUE || dockingProgressSeconds <= 0.0f) {
                return;
            }

            float progress = clamp(dockingProgressSeconds / DOCK_HOLD_SECONDS, 0.0f, 1.0f);
            float barW = Math.min(560.0f, Math.max(220.0f, w - 96.0f));
            float barH = 22.0f;
            float barX = x + (w - barW) * 0.5f;
            float barY = y + h - 48.0f;
            float fillW = Math.max(0.0f, (barW - 6.0f) * progress);

            draw.addRectFilled(barX, barY, barW, barH, 6.0f, MutableColor.rgba(0.015f, 0.025f, 0.045f, 0.86f));
            draw.addRect(barX, barY, barW, barH, 6.0f, HUD_BORDER, 1.25f);
            draw.addRectFilled(barX + 3.0f, barY + 3.0f, fillW, barH - 6.0f, 4.0f,
                    progress >= 1.0f ? CYAN : AMBER);
            text(draw,
                    net.minecraft.client.resources.language.I18n.get("screen.stationarenear.solar_navigation.docking_progress", dockingTargetName, Math.round(progress * 100.0f)),
                    barX + 10.0f,
                    barY + 4.0f,
                    barW - 20.0f,
                    16.0f,
                    11.0f,
                    WHITE);
        }

        private float radarSize(float w, float h) {
            return Math.min(170.0f, Math.max(120.0f, Math.min(w, h) * 0.18f));
        }

        private float radarX(float x, float w, float h) {
            return x + w - radarSize(w, h) - 26.0f;
        }

        private float radarY(float y) {
            return y + 22.0f;
        }

        private void drawRadar(DrawScope draw, float x, float y, float size) {
            float cx = x + size * 0.5f;
            float cy = y + size * 0.5f;
            float radius = size * 0.5f;
            draw.addCircleFilled(cx, cy, radius, MutableColor.rgba(0.02f, 0.04f, 0.07f, 0.68f), 40);
            draw.addCircle(cx, cy, radius, HUD_BORDER, 40, 1.1f);
            draw.addCircle(cx, cy, radius * 0.55f, MutableColor.rgba(0.28f, 0.58f, 0.72f, 0.25f), 36, 1.0f);
            draw.addLine(cx - radius, cy, cx + radius, cy, MutableColor.rgba(0.28f, 0.58f, 0.72f, 0.22f), 1.0f);
            draw.addLine(cx, cy - radius, cx, cy + radius, MutableColor.rgba(0.28f, 0.58f, 0.72f, 0.22f), 1.0f);
            draw.addCircleFilled(cx, cy, 3.5f, CYAN, 12);

            float radarRange = SolarNavigationConfig.SECTOR_SIZE.get() * (SolarNavigationConfig.SECTOR_RENDER_RADIUS.get() + 0.75f);
            float scale = radius / radarRange;
            for (Station station : stations) {
                float sx = cx + (station.x() - renderShipX) * scale;
                float sy = cy + (station.y() - renderShipY) * scale;
                if (distanceSquared(sx, sy, cx, cy) > radius * radius) continue;
                draw.addCircleFilled(sx, sy, station.quest() ? 4.0f : 2.7f, station.quest() ? colorFromArgb(station.color()) : STATION, 12);
            }
        }

        private void text(DrawScope draw, String value, float x, float y, float width, float height,
                          float size, MutableColor color) {
            RichText text = RichText.of(value, MinecraftFonts.defaultFace(), size, color);
            draw.addText(text, x, y, width, height, color);
        }

        private static boolean visible(RectView bounds, float x, float y, float radius) {
            return x + radius >= bounds.x()
                    && x - radius <= bounds.x() + bounds.width()
                    && y + radius >= bounds.y()
                    && y - radius <= bounds.y() + bounds.height();
        }

        private static List<DungeonRoom> createDungeonRooms(long stationSeed, boolean quest) {
            Random random = new Random(stationSeed ^ 0xD06E_0B0E_51A7_10DEL);
            List<DungeonRoom> rooms = new ArrayList<>();
            rooms.add(new DungeonRoom(-18.0f, -42.0f, 36.0f, 84.0f, true));
            rooms.add(new DungeonRoom(-70.0f, -14.0f, 52.0f, 28.0f, false));

            int minRooms = Math.max(1, SolarNavigationConfig.DUNGEON_PREVIEW_MIN_ROOMS.get());
            int maxRooms = Math.max(minRooms, SolarNavigationConfig.DUNGEON_PREVIEW_MAX_ROOMS.get());
            int targetRooms = minRooms + random.nextInt(maxRooms - minRooms + 1) + (quest ? 2 : 0);
            int attempts = 0;
            while (rooms.size() < targetRooms && attempts++ < 80) {
                DungeonRoom parent = rooms.get(random.nextInt(rooms.size()));
                int direction = random.nextInt(4);
                float width = 26.0f + random.nextInt(3) * 14.0f;
                float height = 22.0f + random.nextInt(3) * 14.0f;
                if (random.nextBoolean()) {
                    float swap = width;
                    width = height;
                    height = swap;
                }

                float x;
                float y;
                switch (direction) {
                    case 0 -> {
                        x = parent.x() + parent.width();
                        y = parent.y() + parent.height() * 0.5f - height * 0.5f;
                    }
                    case 1 -> {
                        x = parent.x() - width;
                        y = parent.y() + parent.height() * 0.5f - height * 0.5f;
                    }
                    case 2 -> {
                        x = parent.x() + parent.width() * 0.5f - width * 0.5f;
                        y = parent.y() - height;
                    }
                    default -> {
                        x = parent.x() + parent.width() * 0.5f - width * 0.5f;
                        y = parent.y() + parent.height();
                    }
                }

                x = Math.round(x / 2.0f) * 2.0f;
                y = Math.round(y / 2.0f) * 2.0f;
                DungeonRoom candidate = new DungeonRoom(x, y, width, height, false);
                if (!overlapsDungeonRoom(rooms, candidate, 0.0f)) {
                    rooms.add(candidate);
                }
            }
            return List.copyOf(rooms);
        }

        private static boolean overlapsDungeonRoom(List<DungeonRoom> rooms, DungeonRoom candidate, float padding) {
            for (DungeonRoom room : rooms) {
                boolean separated = candidate.x() + candidate.width() <= room.x() - padding
                        || candidate.x() >= room.x() + room.width() + padding
                        || candidate.y() + candidate.height() <= room.y() - padding
                        || candidate.y() >= room.y() + room.height() + padding;
                if (!separated) {
                    return true;
                }
            }
            return false;
        }

        private Station nearestQuestStation() {
            Station nearest = null;
            float nearestDistanceSq = Float.MAX_VALUE;
            for (Station station : stations) {
                if (!station.quest()) {
                    continue;
                }
                float distanceSq = distanceSquared(shipX, shipY, station.x(), station.y());
                if (distanceSq < nearestDistanceSq) {
                    nearest = station;
                    nearestDistanceSq = distanceSq;
                }
            }
            return nearest;
        }

        private String randomStationName(Random random) {
            String[] prefixes = {"Kappa", "Vega", "Astra", "Orion", "Helio", "Nova", "Rhea", "Ceres", "Iris", "Taurus"};
            String[] suffixes = {"Gate", "Relay", "Port", "Array", "Dock", "Spire", "Hold", "Foundry", "Bastion", "Crossing"};
            return prefixes[random.nextInt(prefixes.length)] + " " + suffixes[random.nextInt(suffixes.length)];
        }

        private long sectorSeed(int sectorX, int sectorY, long salt) {
            long value = seed ^ salt;
            value ^= (long) sectorX * 0x9E37_79B9_7F4A_7C15L;
            value ^= (long) sectorY * 0xC2B2_AE3D_27D4_EB4FL;
            value ^= value >>> 33;
            value *= 0xFF51_AFD7_ED55_8CCDL;
            value ^= value >>> 33;
            value *= 0xC4CE_B9FE_1A85_EC53L;
            return value ^ (value >>> 33);
        }

        private static int floorDiv(float value, int divisor) {
            return (int) Math.floor(value / divisor);
        }

        private static MutableColor colorFromArgb(int argb) {
            float alpha = ((argb >>> 24) & 255) / 255.0f;
            float red = ((argb >>> 16) & 255) / 255.0f;
            float green = ((argb >>> 8) & 255) / 255.0f;
            float blue = (argb & 255) / 255.0f;
            return MutableColor.rgba(red, green, blue, alpha <= 0.0f ? 1.0f : alpha);
        }

        private static float randomRange(Random random, float min, float max) {
            return min + random.nextFloat() * (max - min);
        }

        private static float distanceSquared(float ax, float ay, float bx, float by) {
            float dx = ax - bx;
            float dy = ay - by;
            return dx * dx + dy * dy;
        }

        private static float smooth(float current, float target, float smoothing, float delta) {
            float factor = 1.0f - (float) Math.exp(-smoothing * delta);
            return current + (target - current) * factor;
        }

        private static float smoothAngle(float current, float target, float smoothing, float delta) {
            float factor = 1.0f - (float) Math.exp(-smoothing * delta);
            return current + wrapRadians(target - current) * factor;
        }

        private static float wrapRadians(float value) {
            while (value <= -(float) Math.PI) {
                value += (float) Math.PI * 2.0f;
            }
            while (value > (float) Math.PI) {
                value -= (float) Math.PI * 2.0f;
            }
            return value;
        }

        private static float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

        private record Asteroid(float x, float y, float radius, float rotation, int segments, long seed) {
        }

        private static final class AsteroidOffset {
            private float offsetX;
            private float offsetY;
            private float velocityX;
            private float velocityY;
            private float hitCooldown;

            private void tick(float delta) {
                hitCooldown = Math.max(0.0f, hitCooldown - delta);
                velocityX -= offsetX * ASTEROID_VISUAL_RETURN * delta;
                velocityY -= offsetY * ASTEROID_VISUAL_RETURN * delta;
                offsetX += velocityX * delta;
                offsetY += velocityY * delta;
                float damping = (float) Math.pow(ASTEROID_VISUAL_DAMPING, delta);
                velocityX *= damping;
                velocityY *= damping;
                clampOffset();
            }

            private void clampOffset() {
                float distance = (float) Math.sqrt(offsetX * offsetX + offsetY * offsetY);
                if (distance > ASTEROID_MAX_PUSH_OFFSET) {
                    float scale = ASTEROID_MAX_PUSH_OFFSET / distance;
                    offsetX *= scale;
                    offsetY *= scale;
                }
            }

            private boolean isIdle() {
                return hitCooldown <= 0.0f
                        && Math.abs(offsetX) < 0.05f
                        && Math.abs(offsetY) < 0.05f
                        && Math.abs(velocityX) < 0.05f
                        && Math.abs(velocityY) < 0.05f;
            }
        }

        private record DockedStation(long seed, String name, String code, float x, float y) {
        }

        private record SectorRange(int minX, int maxX, int minY, int maxY) {
        }

        private record Station(float x, float y, String name, String code, float radius, boolean quest, long seed, List<DungeonRoom> dungeonRooms, int color) {
        }

        private record DungeonRoom(float x, float y, float width, float height, boolean core) {
        }

        private record Star(float x, float y, float size, float alpha) {
        }

        private static final class SpaceTextures {
            private static final TextureHandle[] METEORS = new TextureHandle[6];
            private static boolean loadAttempted = false;

            private static TextureHandle forAsteroid(long seed) {
                ensureLoaded();
                int index = (int) Math.floorMod(seed ^ 0x51A7A57EL, METEORS.length);
                return METEORS[index];
            }

            private static void ensureLoaded() {
                if (loadAttempted) return;
                loadAttempted = true;
                for (int i = 0; i < 6; i++) {
                    try {
                        METEORS[i] = load("meteor_" + (i + 1), "meteor_" + (i + 1) + ".png");
                    } catch (IOException | RuntimeException ignored) {
                    }
                }
            }

            private static TextureHandle load(String id, String fileName) throws IOException {
                String resource = "assets/stationarenear/textures/gui/space/" + fileName;
                ClassLoader loader = SolarNavigationScreen.class.getClassLoader();
                try (InputStream stream = loader.getResourceAsStream(resource)) {
                    if (stream == null) {
                        throw new IOException("Missing space texture resource: " + resource);
                    }
                    return UniGuiTextures.replace("stationarenear:dynamic/space/" + id,
                            NativeImage.read(stream),
                            TextureOptions.nearest());
                }
            }
        }
    }
}


