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

        updateDecompression(level, stateTerminal);
        return newState;
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
        damage(event.player().serverLevel(), event.terminalPos(), damage, "asteroid_collision", event.player());
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
