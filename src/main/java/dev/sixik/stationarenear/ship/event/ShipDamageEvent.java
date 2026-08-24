package dev.sixik.stationarenear.ship.event;

import dev.sixik.stationarenear.ship.data.ShipState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;

@Cancelable
public class ShipDamageEvent extends Event {

    private final ServerLevel level;
    private final BlockPos terminalPos;
    private final ShipState currentState;
    private final String damageSource;
    @Nullable
    private final ServerPlayer actor;
    private float amount;

    public ShipDamageEvent(ServerLevel level, BlockPos terminalPos, ShipState currentState, float amount, String damageSource, @Nullable ServerPlayer actor) {
        this.level = level;
        this.terminalPos = terminalPos;
        this.currentState = currentState;
        this.amount = Math.max(0.0F, amount);
        this.damageSource = damageSource == null || damageSource.isBlank() ? "unknown" : damageSource;
        this.actor = actor;
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos terminalPos() {
        return terminalPos;
    }

    public ShipState currentState() {
        return currentState;
    }

    public float amount() {
        return amount;
    }

    public void amount(float amount) {
        this.amount = Math.max(0.0F, amount);
    }

    public String damageSource() {
        return damageSource;
    }

    @Nullable
    public ServerPlayer actor() {
        return actor;
    }
}
