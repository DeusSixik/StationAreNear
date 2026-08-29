package dev.sixik.stationarenear.navigation.event;

import dev.sixik.stationarenear.navigation.data.SolarNavigationShipState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

public class SolarNavigationAsteroidCollisionEvent extends Event {

    private final ServerPlayer player;
    private final BlockPos terminalPos;
    private final SolarNavigationShipState shipState;
    private final long asteroidSeed;
    private final float asteroidX;
    private final float asteroidY;
    private final float asteroidRadius;
    private final float impactSpeed;

    public SolarNavigationAsteroidCollisionEvent(
            ServerPlayer player,
            BlockPos terminalPos,
            SolarNavigationShipState shipState,
            long asteroidSeed,
            float asteroidX,
            float asteroidY,
            float asteroidRadius,
            float impactSpeed
    ) {
        this.player = player;
        this.terminalPos = terminalPos;
        this.shipState = shipState;
        this.asteroidSeed = asteroidSeed;
        this.asteroidX = asteroidX;
        this.asteroidY = asteroidY;
        this.asteroidRadius = asteroidRadius;
        this.impactSpeed = impactSpeed;
    }

    public ServerPlayer player() {
        return player;
    }

    public BlockPos terminalPos() {
        return terminalPos;
    }

    public SolarNavigationShipState shipState() {
        return shipState;
    }

    public long asteroidSeed() {
        return asteroidSeed;
    }

    public float asteroidX() {
        return asteroidX;
    }

    public float asteroidY() {
        return asteroidY;
    }

    public float asteroidRadius() {
        return asteroidRadius;
    }

    public float impactSpeed() {
        return impactSpeed;
    }

    public boolean isLeftCollision() {
        float dx = asteroidX - shipState.shipX();
        float dy = asteroidY - shipState.shipY();
        float sideDot = dy * (float) Math.cos(shipState.angle()) - dx * (float) Math.sin(shipState.angle());
        return sideDot >= 0.0F;
    }
}
