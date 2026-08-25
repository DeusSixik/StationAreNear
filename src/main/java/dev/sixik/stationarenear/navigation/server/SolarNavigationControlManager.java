package dev.sixik.stationarenear.navigation.server;

import dev.sixik.stationarenear.navigation.SolarNavigationConfig;
import dev.sixik.stationarenear.navigation.block.SolarNavigationTerminalBlock;
import dev.sixik.stationarenear.navigation.data.SolarNavigationShipState;
import dev.sixik.stationarenear.navigation.event.SolarNavigationAsteroidCollisionEvent;
import dev.sixik.stationarenear.navigation.network.SolarNavigationNetwork;
import dev.sixik.stationarenear.navigation.registry.SolarNavigationBlocks;
import dev.sixik.stationarenear.navigation.world.SolarNavigationSavedData;
import dev.sixik.stationarenear.navigation.world.SolarNavigationStationCleaner;
import dev.sixik.stationarenear.ship.runtime.ShipManager;
import io.netty.util.collection.LongObjectHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SolarNavigationControlManager {

    private static final float SHIP_RADIUS = 18.0F;
    private static final float MAX_TURN_SPEED = 2.65F;
    private static final float TURN_ACCELERATION = 7.4F;
    private static final float TURN_DECAY = 0.035F;
    private static final float THRUST = 520.0F;
    private static final float REVERSE_THRUST = 300.0F;
    private static final float MAX_SPEED = 520.0F;
    private static final float ASTEROID_PUSH_MULTIPLIER = 0.62F;
    private static final float ASTEROID_PUSH_PENETRATION_FORCE = 7.5F;
    private static final float ASTEROID_MAX_PUSH_OFFSET = 280.0F;
    private static final float ASTEROID_VISUAL_RETURN = 0.72F;
    private static final float ASTEROID_VISUAL_DAMPING = 0.26F;
    private static final float SERVER_TIMESTEP = 1.0F / 20.0F;
    private static final long INPUT_TIMEOUT_TICKS = 12L;
    private static final double MAX_TERMINAL_DISTANCE_SQ = 256.0D;

    private static final Map<TerminalKey, Session> SESSIONS = new Object2ObjectLinkedOpenHashMap<>();

    private SolarNavigationControlManager() {
    }

    public static SolarNavigationShipState open(ServerPlayer player, BlockPos terminalPos, long seed, SolarNavigationShipState savedState) {
        Session session = SESSIONS.computeIfAbsent(new TerminalKey(player.level().dimension(), terminalPos.asLong()), ignored -> new Session(player.serverLevel(), terminalPos, seed, savedState));
        session.level = player.serverLevel();
        session.seed = seed;
        session.viewers.put(player.getUUID(), player);
        session.inputs.put(player.getUUID(), new PlayerInput(0, player.serverLevel().getGameTime()));
        SolarNavigationNetwork.syncState(player, terminalPos, session.state);
        return session.state;
    }

    public static void updateInput(ServerPlayer player, BlockPos terminalPos, int inputMask) {
        if (!isValidTerminal(player, terminalPos)) {
            return;
        }
        TerminalKey key = new TerminalKey(player.level().dimension(), terminalPos.asLong());
        Session session = SESSIONS.computeIfAbsent(key, ignored -> new Session(player.serverLevel(), terminalPos, 0L, SolarNavigationSavedData.get(player.serverLevel()).shipState(terminalPos)));
        session.level = player.serverLevel();
        session.viewers.put(player.getUUID(), player);
        session.inputs.put(player.getUUID(), new PlayerInput(inputMask, player.serverLevel().getGameTime()));
    }

    public static SolarNavigationShipState forceStop(ServerLevel level, BlockPos terminalPos) {
        SolarNavigationShipState savedState = SolarNavigationSavedData.get(level).shipState(terminalPos);
        SolarNavigationShipState stoppedState = new SolarNavigationShipState(savedState.shipX(), savedState.shipY(), 0.0F, 0.0F, savedState.angle());
        SolarNavigationSavedData.get(level).shipState(terminalPos, stoppedState);

        Session session = SESSIONS.get(new TerminalKey(level.dimension(), terminalPos.asLong()));
        if (session != null) {
            session.level = level;
            session.state = stoppedState;
            session.turnVelocity = 0.0F;
            session.sync();
        }
        return stoppedState;
    }

    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Iterator<Map.Entry<TerminalKey, Session>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next().getValue();
            if (session.level == null || !session.level.isLoaded(session.terminalPos) || !isTerminal(session.level, session.terminalPos)) {
                iterator.remove();
                continue;
            }
            session.prune();
            if (session.viewers.isEmpty() && session.inputs.isEmpty()) {
                iterator.remove();
                continue;
            }
            session.tick();
        }
    }

    private static boolean isValidTerminal(ServerPlayer player, BlockPos terminalPos) {
        ServerLevel level = player.serverLevel();
        return level.isLoaded(terminalPos)
                && isTerminal(level, terminalPos)
                && player.distanceToSqr(Vec3.atCenterOf(terminalPos)) <= MAX_TERMINAL_DISTANCE_SQ;
    }

    private static boolean isTerminal(ServerLevel level, BlockPos terminalPos) {
        BlockState state = level.getBlockState(terminalPos);
        return state.is(SolarNavigationBlocks.SOLAR_NAVIGATION_TERMINAL.get()) && state.hasProperty(SolarNavigationTerminalBlock.FACING);
    }

    private static int axis(int positiveMask, int negativeMask, Iterable<PlayerInput> inputs) {
        int positive = 0;
        int negative = 0;
        for (PlayerInput input : inputs) {
            if ((input.inputMask() & positiveMask) != 0) {
                positive++;
            }
            if ((input.inputMask() & negativeMask) != 0) {
                negative++;
            }
        }
        return Integer.compare(positive, negative);
    }

    private static long sectorSeed(long seed, int sectorX, int sectorY, long salt) {
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

    private static float randomRange(java.util.Random random, float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    private record TerminalKey(ResourceKey<Level> dimension, long pos) {
    }

    private record PlayerInput(int inputMask, long gameTime) {
    }

    private static final class Session {
        private ServerLevel level;
        private final BlockPos terminalPos;
        private long seed;
        private SolarNavigationShipState state;
        private float turnVelocity;
        private float asteroidCollisionCooldown;
        private final LongObjectHashMap<MovingAsteroid> movingAsteroids = new LongObjectHashMap<>();
        private final Map<UUID, ServerPlayer> viewers = new Object2ObjectLinkedOpenHashMap<>();
        private final Map<UUID, PlayerInput> inputs = new Object2ObjectLinkedOpenHashMap<>();

        private Session(ServerLevel level, BlockPos terminalPos, long seed, SolarNavigationShipState state) {
            this.level = level;
            this.terminalPos = terminalPos;
            this.seed = seed;
            this.state = state;
        }

        private void tick() {
            int thrustAxis = axis(UpdateMasks.FORWARD, UpdateMasks.BACKWARD, inputs.values());
            int turnAxis = axis(UpdateMasks.RIGHT, UpdateMasks.LEFT, inputs.values());
            simulate(thrustAxis, turnAxis);
            SolarNavigationSavedData.get(level).shipState(terminalPos, state);
            int clearedStations = SolarNavigationStationCleaner.clearFarFromShip(level, terminalPos, state, SolarNavigationConfig.STATION_UNLOAD_DISTANCE.get().floatValue());
            if (clearedStations > 0) {
                ShipManager.setDocking(level, terminalPos, false);
            }
            sync();
        }

        private void simulate(int thrustAxis, int turnAxis) {
            tickMovingAsteroids(SERVER_TIMESTEP);

            if (turnAxis != 0) {
                turnVelocity += turnAxis * TURN_ACCELERATION * SERVER_TIMESTEP;
                turnVelocity = clamp(turnVelocity, -MAX_TURN_SPEED, MAX_TURN_SPEED);
            } else if (turnVelocity != 0.0F) {
                turnVelocity *= (float) Math.pow(TURN_DECAY, SERVER_TIMESTEP);
                if (Math.abs(turnVelocity) < 0.01F) {
                    turnVelocity = 0.0F;
                }
            }

            float angle = wrapRadians(state.angle() + turnVelocity * SERVER_TIMESTEP);
            float previousVelocityX = state.velocityX();
            float previousVelocityY = state.velocityY();
            float directionX = (float) Math.cos(angle);
            float directionY = (float) Math.sin(angle);
            float speed = (float) Math.sqrt(previousVelocityX * previousVelocityX + previousVelocityY * previousVelocityY);
            float directionDot = previousVelocityX * directionX + previousVelocityY * directionY;
            float signedSpeed = directionDot < 0.0F ? -speed : speed;

            if (thrustAxis > 0) {
                signedSpeed += THRUST * SERVER_TIMESTEP;
            } else if (thrustAxis < 0) {
                signedSpeed -= REVERSE_THRUST * SERVER_TIMESTEP;
            }
            signedSpeed = clamp(signedSpeed, -MAX_SPEED, MAX_SPEED);

            float velocityX = directionX * signedSpeed;
            float velocityY = directionY * signedSpeed;
            float shipX = state.shipX() + velocityX * SERVER_TIMESTEP;
            float shipY = state.shipY() + velocityY * SERVER_TIMESTEP;
            float damping = (float) Math.pow(0.74F, SERVER_TIMESTEP);
            velocityX *= damping;
            velocityY *= damping;
            state = resolveAsteroidCollisions(new SolarNavigationShipState(shipX, shipY, velocityX, velocityY, angle), state.shipX(), state.shipY());
            asteroidCollisionCooldown = Math.max(0.0F, asteroidCollisionCooldown - SERVER_TIMESTEP);
        }

        private SolarNavigationShipState resolveAsteroidCollisions(SolarNavigationShipState currentState, float previousShipX, float previousShipY) {
            int sectorSize = SolarNavigationConfig.SECTOR_SIZE.get();
            int minSectorX = floorDiv(Math.min(previousShipX, currentState.shipX()), sectorSize) - 1;
            int maxSectorX = floorDiv(Math.max(previousShipX, currentState.shipX()), sectorSize) + 1;
            int minSectorY = floorDiv(Math.min(previousShipY, currentState.shipY()), sectorSize) - 1;
            int maxSectorY = floorDiv(Math.max(previousShipY, currentState.shipY()), sectorSize) + 1;
            SolarNavigationShipState resolved = currentState;
            for (int sectorX = minSectorX; sectorX <= maxSectorX; sectorX++) {
                for (int sectorY = minSectorY; sectorY <= maxSectorY; sectorY++) {
                    resolved = resolveAsteroidSector(resolved, previousShipX, previousShipY, sectorX, sectorY, sectorSize);
                }
            }
            return resolved;
        }

        private SolarNavigationShipState resolveAsteroidSector(SolarNavigationShipState currentState, float previousShipX, float previousShipY, int sectorX, int sectorY, int sectorSize) {
            long asteroidSectorSeed = sectorSeed(seed, sectorX, sectorY, 0xA57E_201DL);
            float minX = sectorX * (float) sectorSize;
            float minY = sectorY * (float) sectorSize;
            float asteroidMinRadius = Math.min(SolarNavigationConfig.ASTEROID_MIN_RADIUS.get().floatValue(), SolarNavigationConfig.ASTEROID_MAX_RADIUS.get().floatValue());
            float asteroidMaxRadius = Math.max(SolarNavigationConfig.ASTEROID_MIN_RADIUS.get().floatValue(), SolarNavigationConfig.ASTEROID_MAX_RADIUS.get().floatValue());
            SolarNavigationShipState resolved = currentState;
            for (int i = 0; i < SolarNavigationConfig.ASTEROIDS_PER_SECTOR.get(); i++) {
                long asteroidSeed = asteroidSectorSeed ^ (long) i * 0x9E37_79B9_7F4A_7C15L;
                java.util.Random random = new java.util.Random(asteroidSeed);
                float asteroidX = minX + randomRange(random, sectorSize * 0.08F, sectorSize * 0.92F);
                float asteroidY = minY + randomRange(random, sectorSize * 0.08F, sectorSize * 0.92F);
                float asteroidRadius = randomRange(random, asteroidMinRadius, asteroidMaxRadius);
                MovingAsteroid movingAsteroid = movingAsteroids.get(asteroidSeed);
                if (movingAsteroid != null) {
                    asteroidX += movingAsteroid.offsetX;
                    asteroidY += movingAsteroid.offsetY;
                }
                resolved = resolveAsteroid(resolved, previousShipX, previousShipY, asteroidSeed, asteroidX, asteroidY, asteroidRadius);
            }
            return resolved;
        }

        private SolarNavigationShipState resolveAsteroid(SolarNavigationShipState currentState, float previousShipX, float previousShipY, long asteroidSeed, float asteroidX, float asteroidY, float asteroidRadius) {
            float minDistance = asteroidRadius + SHIP_RADIUS;
            float currentDx = currentState.shipX() - asteroidX;
            float currentDy = currentState.shipY() - asteroidY;
            float currentDistanceSq = currentDx * currentDx + currentDy * currentDy;
            float segmentX = currentState.shipX() - previousShipX;
            float segmentY = currentState.shipY() - previousShipY;
            float segmentLengthSq = segmentX * segmentX + segmentY * segmentY;
            float hitDx = currentDx;
            float hitDy = currentDy;
            float hitDistanceSq = currentDistanceSq;

            if (segmentLengthSq > 0.001F) {
                float t = clamp(((asteroidX - previousShipX) * segmentX + (asteroidY - previousShipY) * segmentY) / segmentLengthSq, 0.0F, 1.0F);
                float closestX = previousShipX + segmentX * t;
                float closestY = previousShipY + segmentY * t;
                float sweptDx = closestX - asteroidX;
                float sweptDy = closestY - asteroidY;
                float sweptDistanceSq = sweptDx * sweptDx + sweptDy * sweptDy;
                if (sweptDistanceSq < hitDistanceSq) {
                    hitDx = sweptDx;
                    hitDy = sweptDy;
                    hitDistanceSq = sweptDistanceSq;
                }
            }

            if (hitDistanceSq >= minDistance * minDistance) {
                return currentState;
            }

            float velocityX = currentState.velocityX();
            float velocityY = currentState.velocityY();
            float impactSpeed = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);
            float hitDistance = (float) Math.sqrt(Math.max(0.0001F, hitDistanceSq));
            float normalX;
            float normalY;
            if (hitDistance > 0.01F) {
                normalX = hitDx / hitDistance;
                normalY = hitDy / hitDistance;
            } else if (impactSpeed > 0.01F) {
                normalX = -velocityX / impactSpeed;
                normalY = -velocityY / impactSpeed;
            } else {
                normalX = 1.0F;
                normalY = 0.0F;
            }

            float currentDistance = (float) Math.sqrt(Math.max(0.0001F, currentDistanceSq));
            float bodyPush = Math.max(0.0F, minDistance - currentDistance);
            float shipX = currentState.shipX() + normalX * bodyPush;
            float shipY = currentState.shipY() + normalY * bodyPush;
            float penetration = minDistance - hitDistance;
            float dot = velocityX * normalX + velocityY * normalY;
            float asteroidImpact = Math.max(dot < 0.0F ? -dot : 0.0F, impactSpeed * 0.85F);
            if (dot < 0.0F) {
                velocityX -= normalX * dot * 1.25F;
                velocityY -= normalY * dot * 1.25F;
            }
            pushAsteroid(asteroidSeed, -normalX, -normalY, asteroidImpact, penetration);
            if (asteroidCollisionCooldown <= 0.0F) {
                postAsteroidCollision(asteroidSeed, asteroidX, asteroidY, asteroidRadius, impactSpeed, new SolarNavigationShipState(shipX, shipY, velocityX, velocityY, currentState.angle()));
                asteroidCollisionCooldown = SolarNavigationConfig.ASTEROID_COLLISION_EVENT_COOLDOWN.get().floatValue();
            }
            return new SolarNavigationShipState(shipX, shipY, velocityX, velocityY, currentState.angle());
        }

        private void pushAsteroid(long asteroidSeed, float directionX, float directionY, float impactSpeed, float penetration) {
            MovingAsteroid movingAsteroid = movingAsteroids.computeIfAbsent(asteroidSeed, ignored -> new MovingAsteroid());
            float impulse = impactSpeed * ASTEROID_PUSH_MULTIPLIER + penetration * ASTEROID_PUSH_PENETRATION_FORCE;
            movingAsteroid.velocityX += directionX * impulse;
            movingAsteroid.velocityY += directionY * impulse;
            movingAsteroid.offsetX += directionX * Math.min(16.0F, penetration * 0.45F + impactSpeed * 0.012F);
            movingAsteroid.offsetY += directionY * Math.min(16.0F, penetration * 0.45F + impactSpeed * 0.012F);
            movingAsteroid.clampOffset();
            syncAsteroidOffset(asteroidSeed, movingAsteroid);
        }

        private void tickMovingAsteroids(float delta) {
            Iterator<Map.Entry<Long, MovingAsteroid>> iterator = movingAsteroids.entrySet().iterator();
            while (iterator.hasNext()) {
                MovingAsteroid movingAsteroid = iterator.next().getValue();
                movingAsteroid.tick(delta);
                if (movingAsteroid.isIdle()) {
                    iterator.remove();
                }
            }
        }

        private void syncAsteroidOffset(long asteroidSeed, MovingAsteroid movingAsteroid) {
            for (ServerPlayer viewer : new ArrayList<>(viewers.values())) {
                if (viewer == null || viewer.isRemoved()) {
                    continue;
                }
                SolarNavigationNetwork.syncAsteroidOffset(viewer, terminalPos, asteroidSeed, movingAsteroid.offsetX, movingAsteroid.offsetY, movingAsteroid.velocityX, movingAsteroid.velocityY);
            }
        }

        private void postAsteroidCollision(long asteroidSeed, float asteroidX, float asteroidY, float asteroidRadius, float impactSpeed, SolarNavigationShipState collisionState) {
            for (ServerPlayer viewer : new ArrayList<>(viewers.values())) {
                if (viewer == null || viewer.isRemoved()) {
                    continue;
                }
                MinecraftForge.EVENT_BUS.post(new SolarNavigationAsteroidCollisionEvent(viewer, terminalPos, collisionState, asteroidSeed, asteroidX, asteroidY, asteroidRadius, impactSpeed));
            }
        }

        private void sync() {
            for (ServerPlayer viewer : new ArrayList<>(viewers.values())) {
                if (viewer == null || viewer.isRemoved()) {
                    continue;
                }
                SolarNavigationNetwork.syncState(viewer, terminalPos, state);
                for (Map.Entry<Long, MovingAsteroid> asteroid : movingAsteroids.entrySet()) {
                    MovingAsteroid movingAsteroid = asteroid.getValue();
                    SolarNavigationNetwork.syncAsteroidOffset(viewer, terminalPos, asteroid.getKey(), movingAsteroid.offsetX, movingAsteroid.offsetY, movingAsteroid.velocityX, movingAsteroid.velocityY);
                }
            }
        }

        private void prune() {
            long now = level.getGameTime();
            inputs.entrySet().removeIf(entry -> now - entry.getValue().gameTime() > INPUT_TIMEOUT_TICKS);
            viewers.entrySet().removeIf(entry -> {
                ServerPlayer player = entry.getValue();
                PlayerInput input = inputs.get(entry.getKey());
                return player == null
                        || player.isRemoved()
                        || player.level() != level
                        || input == null
                        || now - input.gameTime() > INPUT_TIMEOUT_TICKS
                        || player.distanceToSqr(Vec3.atCenterOf(terminalPos)) > MAX_TERMINAL_DISTANCE_SQ;
            });
        }
    }

    private static float wrapRadians(float value) {
        while (value <= -(float) Math.PI) {
            value += (float) Math.PI * 2.0F;
        }
        while (value > (float) Math.PI) {
            value -= (float) Math.PI * 2.0F;
        }
        return value;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class MovingAsteroid {
        private float offsetX;
        private float offsetY;
        private float velocityX;
        private float velocityY;

        private void tick(float delta) {
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
            return Math.abs(offsetX) < 0.05F
                    && Math.abs(offsetY) < 0.05F
                    && Math.abs(velocityX) < 0.05F
                    && Math.abs(velocityY) < 0.05F;
        }
    }

    private static final class UpdateMasks {
        private static final int FORWARD = 1;
        private static final int BACKWARD = 1 << 1;
        private static final int LEFT = 1 << 2;
        private static final int RIGHT = 1 << 3;
    }
}
