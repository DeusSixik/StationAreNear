package dev.sixik.stationarenear.ship.runtime;

import dev.sixik.stationarenear.ship.block.PressureTightDoorBlock;
import dev.sixik.stationarenear.ship.data.ShipState;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchor;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorResolver;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorSavedData;
import dev.sixik.stationarenear.ship.event.ShipDecompressionEvent;
import dev.sixik.stationarenear.ship.world.ShipRuntimeSettingsData;
import dev.sixik.stationarenear.ship.world.ShipSavedData;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ShipDecompressionEffects {

    private static final int NO_GRAVITY_TICKS = 600;
    private static final int FREEZE_TICKS_PER_TICK = 8;
    private static final int COLD_DAMAGE_INTERVAL_TICKS = 20;
    private static final float COLD_DAMAGE = 2.0F;
    private static final int MAX_EXIT_SEARCH_VISITS = 65_536;
    private static final double EJECTION_EXIT_BLOCKS = 2.5D;
    private static final double EJECTION_SPEED = 2.15D;
    private static final Object2LongMap<UUID> NO_GRAVITY_UNTIL = new Object2LongOpenHashMap<>();
    private static final Object2LongMap<UUID> LAST_COLD_DAMAGE = new Object2LongOpenHashMap<>();

    private ShipDecompressionEffects() {
    }

    public static boolean enabled(ServerLevel level) {
        return ShipRuntimeSettingsData.get(level).decompressionEffectsEnabled();
    }

    public static void enabled(ServerLevel level, boolean enabled) {
        ShipRuntimeSettingsData.get(level).decompressionEffectsEnabled(enabled);
        if (!enabled) {
            clearTrackedPlayers(level);
        }
    }

    public static void onShipDecompression(ShipDecompressionEvent event) {
        if (!event.decompressed() || !enabled(event.level())) {
            return;
        }

        Optional<ShipDockingAnchor> anchor = shipAnchor(event.level(), event.terminalPos());
        anchor.ifPresent(value -> ejectPlayersInShip(event.level(), value));
    }

    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        ServerLevel level = player.serverLevel();
        if (!enabled(level) || ignored(player)) {
            clearPlayer(player);
            return;
        }

        long gameTime = level.getGameTime();
        tickDecompressedShips(level, player);
        tickVacuumPlayer(player, gameTime);
    }

    private static void tickDecompressedShips(ServerLevel level, ServerPlayer player) {
        ShipDockingAnchorSavedData anchors = ShipDockingAnchorSavedData.get(level);
        ShipSavedData ships = ShipSavedData.get(level);
        for (ShipDockingAnchor anchor : anchors.anchors()) {
            ShipState state = ships.ship(ShipManager.stateTerminal(level, anchor.terminalPos()));
            if (!state.decompressed() || !contains(anchor.shipBounds(), player.blockPosition())) {
                continue;
            }
            ejectPlayer(level, anchor, player);
            return;
        }
    }

    private static void ejectPlayersInShip(ServerLevel level, ShipDockingAnchor anchor) {
        for (ServerPlayer player : level.players()) {
            if (!ignored(player) && contains(anchor.shipBounds(), player.blockPosition())) {
                ejectPlayer(level, anchor, player);
            }
        }
    }

    private static void ejectPlayer(ServerLevel level, ShipDockingAnchor anchor, ServerPlayer player) {
        Optional<ExitPoint> exit = nearestExit(level, anchor, player);
        if (exit.isEmpty()) {
            return;
        }

        ExitPoint value = exit.get();
        Vec3 outward = Vec3.atLowerCornerOf(value.direction().getNormal()).normalize();
        Vec3 target = value.center().add(outward.scale(EJECTION_EXIT_BLOCKS));
        Vec3 pull = target.subtract(player.position());
        Vec3 velocity = pull.lengthSqr() > 1.0E-4D ? pull.normalize().scale(EJECTION_SPEED) : outward.scale(EJECTION_SPEED);
        player.setNoGravity(true);
        player.teleportTo(target.x, target.y, target.z);
        player.setDeltaMovement(velocity);
        player.hurtMarked = true;
        long until = level.getGameTime() + NO_GRAVITY_TICKS;
        UUID uuid = player.getUUID();
        if (NO_GRAVITY_UNTIL.getLong(uuid) < until) {
            NO_GRAVITY_UNTIL.put(uuid, until);
        }
    }

    private static void tickVacuumPlayer(ServerPlayer player, long gameTime) {
        UUID uuid = player.getUUID();
        long noGravityUntil = NO_GRAVITY_UNTIL.getLong(uuid);
        if (noGravityUntil <= 0L) {
            return;
        }

        int requiredFreezeTicks = player.getTicksRequiredToFreeze();
        player.setTicksFrozen(Math.min(requiredFreezeTicks, player.getTicksFrozen() + FREEZE_TICKS_PER_TICK));
        if (gameTime - LAST_COLD_DAMAGE.getLong(uuid) >= COLD_DAMAGE_INTERVAL_TICKS) {
            LAST_COLD_DAMAGE.put(uuid, gameTime);
            player.hurt(player.damageSources().freeze(), COLD_DAMAGE);
        }

        if (gameTime <= noGravityUntil) {
            player.setNoGravity(true);
            return;
        }

        clearPlayer(player);
    }

    private static Optional<ShipDockingAnchor> shipAnchor(ServerLevel level, BlockPos terminalPos) {
        return ShipDockingAnchorSavedData.get(level)
                .anchor(terminalPos)
                .or(() -> ShipDockingAnchorResolver.bindNearbyShip(level, terminalPos));
    }

    private static Optional<ExitPoint> nearestExit(ServerLevel level, ShipDockingAnchor anchor, ServerPlayer player) {
        BoundingBox bounds = anchor.shipBounds();
        Map<BlockPos, Direction> dockingExits = openDockingApertureExits(level, anchor);
        Optional<ExitPoint> pathExit = reachableExit(level, bounds, player.blockPosition(), dockingExits);
        if (pathExit.isPresent()) {
            return pathExit;
        }

        return dockingExits.entrySet().stream()
                .map(entry -> new ExitPoint(Vec3.atCenterOf(entry.getKey()), entry.getValue()))
                .min((left, right) -> Double.compare(left.center().distanceToSqr(player.position()), right.center().distanceToSqr(player.position())));
    }

    private static Optional<ExitPoint> reachableExit(ServerLevel level, BoundingBox bounds, BlockPos start, Map<BlockPos, Direction> dockingExits) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        addSearchStart(level, bounds, start, queue);

        while (!queue.isEmpty() && visited.size() <= MAX_EXIT_SEARCH_VISITS) {
            BlockPos current = queue.removeFirst();
            if (!visited.add(current.immutable()) || !contains(bounds, current) || !isVacuumPath(level, current)) {
                continue;
            }

            Direction dockingDirection = dockingExits.get(current);
            if (dockingDirection != null) {
                return Optional.of(new ExitPoint(Vec3.atCenterOf(current), dockingDirection));
            }

            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!contains(bounds, next)) {
                    return Optional.of(new ExitPoint(Vec3.atCenterOf(current), direction));
                }
                if (!visited.contains(next) && isVacuumPath(level, next)) {
                    queue.add(next.immutable());
                }
            }
        }

        return Optional.empty();
    }

    private static void addSearchStart(ServerLevel level, BoundingBox bounds, BlockPos start, ArrayDeque<BlockPos> queue) {
        if (contains(bounds, start) && isVacuumPath(level, start)) {
            queue.add(start.immutable());
            return;
        }

        for (Direction direction : Direction.values()) {
            BlockPos near = start.relative(direction);
            if (contains(bounds, near) && isVacuumPath(level, near)) {
                queue.add(near.immutable());
            }
        }
    }

    private static Map<BlockPos, Direction> openDockingApertureExits(ServerLevel level, ShipDockingAnchor anchor) {
        Map<BlockPos, Direction> exits = new HashMap<>();
        for (BlockPos pos : dockingAperture(anchor)) {
            BlockState state = level.getBlockState(pos);
            boolean openPressureDoor = state.getBlock() instanceof PressureTightDoorBlock && PressureTightDoorBlock.isOpen(state);
            if (state.isAir() || openPressureDoor) {
                exits.put(pos.immutable(), anchor.direction());
            }
        }
        return exits;
    }

    private static boolean isVacuumPath(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof PressureTightDoorBlock) {
            return PressureTightDoorBlock.isOpen(state);
        }
        return state.isAir();
    }

    private static Iterable<BlockPos> dockingAperture(ShipDockingAnchor anchor) {
        Set<BlockPos> positions = new HashSet<>();
        Direction side = anchor.direction().getClockWise();
        int minSide = -anchor.width() / 2;
        int maxSide = anchor.width() - anchor.width() / 2 - 1;
        int minY = -anchor.height() / 2;
        int maxY = anchor.height() - anchor.height() / 2 - 1;
        for (int sideOffset = minSide; sideOffset <= maxSide; sideOffset++) {
            for (int yOffset = minY; yOffset <= maxY; yOffset++) {
                positions.add(anchor.anchorPos().relative(side, sideOffset).above(yOffset));
            }
        }
        return positions;
    }

    private static boolean ignored(ServerPlayer player) {
        return player.isCreative() || player.isSpectator() || player.isDeadOrDying();
    }

    private static boolean contains(BoundingBox bounds, BlockPos pos) {
        return pos.getX() >= bounds.minX() && pos.getX() <= bounds.maxX()
                && pos.getY() >= bounds.minY() && pos.getY() <= bounds.maxY()
                && pos.getZ() >= bounds.minZ() && pos.getZ() <= bounds.maxZ();
    }

    private static void clearTrackedPlayers(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            clearPlayer(player);
        }
        NO_GRAVITY_UNTIL.clear();
        LAST_COLD_DAMAGE.clear();
    }

    private static void clearPlayer(ServerPlayer player) {
        NO_GRAVITY_UNTIL.removeLong(player.getUUID());
        LAST_COLD_DAMAGE.removeLong(player.getUUID());
        if (!player.isSpectator()) {
            player.setNoGravity(false);
        }
    }

    private record ExitPoint(Vec3 center, Direction direction) {
    }
}
