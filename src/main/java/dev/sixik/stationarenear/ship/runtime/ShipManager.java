package dev.sixik.stationarenear.ship.runtime;

import dev.sixik.stationarenear.navigation.event.SolarNavigationAsteroidCollisionEvent;
import dev.sixik.stationarenear.navigation.registry.SolarNavigationBlocks;
import dev.sixik.stationarenear.ship.data.ShipState;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchor;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorResolver;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorSavedData;
import dev.sixik.stationarenear.ship.event.ShipDamageEvent;
import dev.sixik.stationarenear.ship.event.ShipDecompressionEvent;
import dev.sixik.stationarenear.ship.event.ShipHealthChangedEvent;
import dev.sixik.stationarenear.ship.event.ShipHullBlockDamageEvent;
import dev.sixik.stationarenear.ship.world.ShipSavedData;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class ShipManager {

    private static final Long2LongMap LAST_ASTEROID_DAMAGE_TICK = new Long2LongOpenHashMap();

    private ShipManager() {
    }

    public static ShipState state(ServerLevel level, BlockPos terminalPos) {
        return ShipSavedData.get(level).ship(stateTerminal(level, terminalPos));
    }

    public static BlockPos stateTerminal(ServerLevel level, BlockPos terminalPos) {
        ShipSavedData shipData = ShipSavedData.get(level);
        if (shipData.shipIfPresent(terminalPos).isPresent()) {
            return terminalPos;
        }

        ShipDockingAnchor anchor = ShipDockingAnchorSavedData.get(level)
                .anchor(terminalPos)
                .or(() -> ShipDockingAnchorResolver.bindNearbyShip(level, terminalPos))
                .orElse(null);
        if (anchor == null) {
            return terminalPos;
        }

        BlockPos navigationTerminal = null;
        for (Long relatedTerminal : ShipIntegrityScanner.relatedTerminalPositions(level, terminalPos, anchor)) {
            BlockPos relatedPos = BlockPos.of(relatedTerminal);
            if (shipData.shipIfPresent(relatedPos).isPresent()) {
                return relatedPos;
            }
            BlockState state = level.getBlockState(relatedPos);
            if (state.is(SolarNavigationBlocks.SOLAR_NAVIGATION_TERMINAL.get())) {
                navigationTerminal = relatedPos;
            }
        }
        return navigationTerminal == null ? terminalPos : navigationTerminal;
    }

    public static ShipState damage(ServerLevel level, BlockPos terminalPos, float amount, String source, @Nullable ServerPlayer actor) {
        if (amount <= 0.0F) {
            return ShipSavedData.get(level).ship(stateTerminal(level, terminalPos));
        }

        BlockPos stateTerminal = stateTerminal(level, terminalPos);
        ShipSavedData data = ShipSavedData.get(level);
        ShipState oldState = data.ship(stateTerminal);
        ShipDamageEvent damageEvent = new ShipDamageEvent(level, stateTerminal, oldState, amount, source, actor);
        if (MinecraftForge.EVENT_BUS.post(damageEvent) || damageEvent.amount() <= 0.0F) {
            return oldState;
        }

        ShipState newState = oldState.withHp(oldState.hp() - damageEvent.amount());
        if (newState.hp() != oldState.hp()) {
            data.ship(stateTerminal, newState);
            MinecraftForge.EVENT_BUS.post(new ShipHealthChangedEvent(level, stateTerminal, oldState, newState, damageEvent.damageSource()));
        }

        if (oldState.hp() > 0.0F && newState.hp() <= 0.0F) {
            destroyShip(level, stateTerminal, damageEvent.damageSource());
        }

        updateDecompression(level, stateTerminal);
        return newState;
    }

    public static void destroyShip(ServerLevel level, BlockPos stateTerminal, String source) {
        ShipSavedData data = ShipSavedData.get(level);
        ShipState state = data.ship(stateTerminal);
        MinecraftForge.EVENT_BUS.post(new dev.sixik.stationarenear.ship.event.ShipDestroyedEvent(level, stateTerminal, state, source));

        ShipDockingAnchor anchor = ShipDockingAnchorSavedData.get(level)
                .anchor(stateTerminal)
                .or(() -> ShipDockingAnchorResolver.bindNearbyShip(level, stateTerminal))
                .orElse(null);

        double explosionX;
        double explosionY;
        double explosionZ;

        if (anchor != null) {
            net.minecraft.world.level.levelgen.structure.BoundingBox bounds = anchor.shipBounds();
            int spanX = Math.max(1, bounds.maxX() - bounds.minX());
            int spanY = Math.max(1, bounds.maxY() - bounds.minY());
            int spanZ = Math.max(1, bounds.maxZ() - bounds.minZ());
            explosionX = bounds.minX() + level.random.nextDouble() * spanX;
            explosionY = bounds.minY() + level.random.nextDouble() * spanY;
            explosionZ = bounds.minZ() + level.random.nextDouble() * spanZ;
        } else {
            explosionX = stateTerminal.getX() + (level.random.nextDouble() - 0.5) * 6.0;
            explosionY = stateTerminal.getY() + (level.random.nextDouble() - 0.5) * 4.0;
            explosionZ = stateTerminal.getZ() + (level.random.nextDouble() - 0.5) * 6.0;
        }

        level.explode(null, explosionX, explosionY, explosionZ, 8.0F, true, net.minecraft.world.level.Level.ExplosionInteraction.BLOCK);
    }

    public static ShipState repair(ServerLevel level, BlockPos terminalPos, float amount, String source) {
        if (amount <= 0.0F) {
            return ShipSavedData.get(level).ship(stateTerminal(level, terminalPos));
        }

        BlockPos stateTerminal = stateTerminal(level, terminalPos);
        ShipSavedData data = ShipSavedData.get(level);
        ShipState oldState = data.ship(stateTerminal);
        ShipState newState = oldState.withHp(oldState.hp() + amount);
        if (newState.hp() == oldState.hp()) {
            return oldState;
        }

        data.ship(stateTerminal, newState);
        MinecraftForge.EVENT_BUS.post(new ShipHealthChangedEvent(level, stateTerminal, oldState, newState, source));
        updateDecompression(level, stateTerminal);
        return newState;
    }

    public static ShipState setDocking(ServerLevel level, BlockPos terminalPos, boolean docking) {
        BlockPos stateTerminal = stateTerminal(level, terminalPos);
        ShipSavedData data = ShipSavedData.get(level);
        ShipState oldState = data.ship(stateTerminal);
        if (oldState.isDocking() == docking) {
            return oldState;
        }

        ShipState newState = oldState.withDocking(docking);
        data.ship(stateTerminal, newState);
        updateDecompression(level, stateTerminal);
        return newState;
    }

    public static void onAsteroidCollision(SolarNavigationAsteroidCollisionEvent event) {
        long terminalKey = event.terminalPos().asLong();
        long gameTime = event.player().serverLevel().getGameTime();
        long lastDamageTick = LAST_ASTEROID_DAMAGE_TICK.put(terminalKey, gameTime);
        if (lastDamageTick == gameTime) {
            return;
        }

        float damage = Math.max(1.0F, event.impactSpeed() * 0.04F);
        ServerLevel level = event.player().serverLevel();
        BlockPos terminalPos = event.terminalPos();
        damage(level, terminalPos, damage, "asteroid_collision", event.player());

        String targetTag = event.isLeftCollision() ? "sound_left" : "sound_right";
        boolean played = false;

        for (dev.sixik.stationarenear.structures.data.StationInstance station : dev.sixik.stationarenear.structures.world.StationSavedData.get(level).stations()) {
            for (dev.sixik.stationarenear.structures.data.PlacedStationPiece piece : station.pieces()) {
                if (piece.bounds().isInside(terminalPos)) {
                    for (dev.sixik.stationarenear.structures.data.PlacedTriggerZone zone : piece.triggerZones()) {
                        if (hasSoundTag(zone, targetTag)) {
                            BlockPos soundPos = new BlockPos(
                                    (zone.min().getX() + zone.max().getX()) / 2,
                                    (zone.min().getY() + zone.max().getY()) / 2,
                                    (zone.min().getZ() + zone.max().getZ()) / 2
                            );
                            level.playSound(null, soundPos, dev.sixik.stationarenear.quest.registry.StationSounds.METEOR_COLLIDE.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.4F, 0.85F + level.random.nextFloat() * 0.3F);
                            played = true;
                        }
                    }
                    break;
                }
            }
        }

        if (!played) {
            level.playSound(null, terminalPos, dev.sixik.stationarenear.quest.registry.StationSounds.METEOR_COLLIDE.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.2F, 0.85F + level.random.nextFloat() * 0.3F);
        }
    }

    private static boolean hasSoundTag(dev.sixik.stationarenear.structures.data.PlacedTriggerZone zone, String targetTag) {
        if (zone.id().equalsIgnoreCase(targetTag)) {
            return true;
        }
        String tagsStr = zone.data().contains(dev.sixik.stationarenear.structures.util.TagsConstants.Keys.TAGS)
                ? zone.data().getString(dev.sixik.stationarenear.structures.util.TagsConstants.Keys.TAGS)
                : (zone.data().contains("tag") ? zone.data().getString("tag") : "");
        for (String tag : tagsStr.split("[,; ]+")) {
            if (tag.trim().equalsIgnoreCase(targetTag)) {
                return true;
            }
        }
        return false;
    }

    public static void onHullBlockDamage(ShipHullBlockDamageEvent event) {
        for (BlockPos terminalPos : ShipIntegrityScanner.terminalsForBlock(event.level(), event.blockPos())) {
            updateDecompression(event.level(), terminalPos);
        }
    }

    public static ShipIntegrityScanner.IntegrityReport updateDecompression(ServerLevel level, BlockPos terminalPos) {
        BlockPos stateTerminal = stateTerminal(level, terminalPos);
        ShipSavedData data = ShipSavedData.get(level);
        ShipState oldState = data.ship(stateTerminal);
        ShipIntegrityScanner.IntegrityReport report = ShipIntegrityScanner.inspect(level, terminalPos);
        if (oldState.decompressed() == report.decompressed() && oldState.decompressionReason().equals(report.reason())) {
            return report;
        }

        ShipState newState = oldState.withDecompression(report.decompressed(), report.reason());
        data.ship(stateTerminal, newState);
        MinecraftForge.EVENT_BUS.post(new ShipDecompressionEvent(level, stateTerminal, oldState, newState));
        return report;
    }
}
