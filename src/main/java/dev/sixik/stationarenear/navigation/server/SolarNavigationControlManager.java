package dev.sixik.stationarenear.navigation.server;

import dev.sixik.stationarenear.navigation.SolarNavigationConfig;
import dev.sixik.stationarenear.navigation.block.SolarNavigationTerminalBlock;
import dev.sixik.stationarenear.navigation.data.SolarNavigationShipState;
import dev.sixik.stationarenear.navigation.event.SolarNavigationAsteroidCollisionEvent;
import dev.sixik.stationarenear.navigation.network.SolarNavigationNetwork;
import dev.sixik.stationarenear.navigation.registry.SolarNavigationBlocks;
import dev.sixik.stationarenear.navigation.world.SolarNavigationSavedData;
import dev.sixik.stationarenear.navigation.world.SolarNavigationStationCleaner;
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
    private static final float TURN_SPEED = 2.65F;
    private static final float THRUST = 520.0F;
    private static final float REVERSE_THRUST = 300.0F;
    private static final float MAX_SPEED = 520.0F;
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
        private float asteroidCollisionCooldown;
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
            SolarNavigationStationCleaner.clearFarFromShip(level, terminalPos, state, SolarNavigationConfig.STATION_UNLOAD_DISTANCE.get().floatValue());
            sync();
        }

        private void simulate(int thrustAxis, int turnAxis) {
            float angle = state.angle() + turnAxis * TURN_SPEED * SERVER_TIMESTEP;
            float velocityX = state.velocityX();
            float velocityY = state.velocityY();
            float directionX = (float) Math.cos(angle);
            float directionY = (float) Math.sin(angle);
            if (thrustAxis > 0) {
                velocityX += directionX * THRUST * SERVER_TIMESTEP;
                velocityY += directionY * THRUST * SERVER_TIMESTEP;
            } else if (thrustAxis < 0) {
                velocityX -= directionX * REVERSE_THRUST * SERVER_TIMESTEP;
                velocityY -= directionY * REVERSE_THRUST * SERVER_TIMESTEP;
            }

            float speed = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);
            if (speed > MAX_SPEED) {
                float scale = MAX_SPEED / speed;
                velocityX *= scale;
                velocityY *= scale;
            }

            float shipX = state.shipX() + velocityX * SERVER_TIMESTEP;
            float shipY = state.shipY() + velocityY * SERVER_TIMESTEP;
            float damping = (float) Math.pow(0.74F, SERVER_TIMESTEP);
            velocityX *= damping;
            velocityY *= damping;
            state = resolveAsteroidCollisions(new SolarNavigationShipState(shipX, shipY, velocityX, velocityY, angle));
            asteroidCollisionCooldown = Math.max(0.0F, asteroidCollisionCooldown - SERVER_TIMESTEP);
        }

        private SolarNavigationShipState resolveAsteroidCollisions(SolarNavigationShipState currentState) {
            int sectorSize = SolarNavigationConfig.SECTOR_SIZE.get();
            int shipSectorX = floorDiv(currentState.shipX(), sectorSize);
            int shipSectorY = floorDiv(currentState.shipY(), sectorSize);
            SolarNavigationShipState resolved = currentState;
            for (int sectorX = shipSectorX - 1; sectorX <= shipSectorX + 1; sectorX++) {
                for (int sectorY = shipSectorY - 1; sectorY <= shipSectorY + 1; sectorY++) {
                    resolved = resolveAsteroidSector(resolved, sectorX, sectorY, sectorSize);
                }
            }
            return resolved;
        }

        private SolarNavigationShipState resolveAsteroidSector(SolarNavigationShipState currentState, int sectorX, int sectorY, int sectorSize) {
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
                resolved = resolveAsteroid(resolved, asteroidSeed, asteroidX, asteroidY, asteroidRadius);
            }
            return resolved;
        }

        private SolarNavigationShipState resolveAsteroid(SolarNavigationShipState currentState, long asteroidSeed, float asteroidX, float asteroidY, float asteroidRadius) {
            float minDistance = asteroidRadius + SHIP_RADIUS;
            float dx = currentState.shipX() - asteroidX;
            float dy = currentState.shipY() - asteroidY;
            float distanceSq = dx * dx + dy * dy;
            if (distanceSq <= 0.001F || distanceSq >= minDistance * minDistance) {
                return currentState;
            }

            float distance = (float) Math.sqrt(distanceSq);
            float normalX = dx / distance;
            float normalY = dy / distance;
            float push = minDistance - distance;
            float shipX = currentState.shipX() + normalX * push;
            float shipY = currentState.shipY() + normalY * push;
            float velocityX = currentState.velocityX();
            float velocityY = currentState.velocityY();
            float impactSpeed = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);
            float dot = velocityX * normalX + velocityY * normalY;
            if (dot < 0.0F) {
                velocityX -= normalX * dot * 1.55F;
                velocityY -= normalY * dot * 1.55F;
            }
            if (asteroidCollisionCooldown <= 0.0F) {
                postAsteroidCollision(asteroidSeed, asteroidX, asteroidY, asteroidRadius, impactSpeed, new SolarNavigationShipState(shipX, shipY, velocityX, velocityY, currentState.angle()));
                asteroidCollisionCooldown = SolarNavigationConfig.ASTEROID_COLLISION_EVENT_COOLDOWN.get().floatValue();
            }
            return new SolarNavigationShipState(shipX, shipY, velocityX, velocityY, currentState.angle());
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

    private static final class UpdateMasks {
        private static final int FORWARD = 1;
        private static final int BACKWARD = 1 << 1;
        private static final int LEFT = 1 << 2;
        private static final int RIGHT = 1 << 3;
    }
}
