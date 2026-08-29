package dev.sixik.stationarenear.quest.world;

import dev.sixik.stationarenear.StationAreNear;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class BalanceSavedData extends SavedData {

    private static final String DATA_NAME = StationAreNear.MODID + "_balance";

    private double balance;

    public static BalanceSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(BalanceSavedData::load, BalanceSavedData::new, DATA_NAME);
    }

    public double balance() {
        return balance;
    }

    public double add(double amount) {
        if (!Double.isFinite(amount) || amount == 0.0D) {
            return balance;
        }
        balance += amount;
        setDirty();
        return balance;
    }

    public double set(double balance) {
        this.balance = Double.isFinite(balance) ? balance : 0.0D;
        setDirty();
        return this.balance;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putDouble("balance", balance);
        return tag;
    }

    private static BalanceSavedData load(CompoundTag tag) {
        BalanceSavedData data = new BalanceSavedData();
        data.balance = tag.contains("balance") ? tag.getDouble("balance") : 0.0D;
        return data;
    }
}
