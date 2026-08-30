package dev.sixik.stationarenear.terminal.shop;

import dev.sixik.stationarenear.StationAreNear;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerBalanceSavedData extends SavedData {

    private static final String DATA_NAME = StationAreNear.MODID + "_player_balance";
    public static final double DEFAULT_BALANCE = 250.0d;

    private final Map<UUID, Double> balances = new HashMap<>();

    public static PlayerBalanceSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage()
                .computeIfAbsent(PlayerBalanceSavedData::load,
                        PlayerBalanceSavedData::new,
                        DATA_NAME);
    }

    public double getBalance(UUID playerId) {
        return balances.getOrDefault(playerId, DEFAULT_BALANCE);
    }

    public void setBalance(UUID playerId, double amount) {
        balances.put(playerId, Math.max(0.0, amount));
        setDirty();
    }

    public double addBalance(UUID playerId, double delta) {
        double current = getBalance(playerId);
        double next = Math.max(0.0, current + delta);
        balances.put(playerId, next);
        setDirty();
        return next;
    }

    public boolean canAfford(UUID playerId, double amount) {
        return getBalance(playerId) >= amount - 1e-9;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag balancesTag = new CompoundTag();
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            balancesTag.putDouble(entry.getKey().toString(), entry.getValue());
        }
        tag.put("balances", balancesTag);
        return tag;
    }

    private static PlayerBalanceSavedData load(CompoundTag tag) {
        PlayerBalanceSavedData data = new PlayerBalanceSavedData();
        CompoundTag balancesTag = tag.getCompound("balances");
        for (String key : balancesTag.getAllKeys()) {
            try {
                data.balances.put(UUID.fromString(key), balancesTag.getDouble(key));
            } catch (IllegalArgumentException ex) {
                StationAreNear.LOGGER.warn("[PlayerBalanceSavedData] Skipped invalid UUID: {}", key);
            }
        }
        return data;
    }
}
