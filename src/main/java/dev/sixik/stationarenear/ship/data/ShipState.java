package dev.sixik.stationarenear.ship.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record ShipState(float hp, float maxHp, List<ShipSystemModule> modules, boolean decompressed, String decompressionReason, boolean isDocking) {

    public static final float DEFAULT_MAX_HP = 1000.0F;

    public ShipState {
        maxHp = Math.max(1.0F, maxHp);
        hp = clamp(hp, 0.0F, maxHp);
        modules = List.copyOf(modules == null || modules.isEmpty() ? defaultModules() : modules);
        decompressionReason = decompressionReason == null ? "sealed" : decompressionReason;
    }

    public static ShipState createDefault() {
        return new ShipState(DEFAULT_MAX_HP, DEFAULT_MAX_HP, defaultModules(), false, "sealed", false);
    }

    public ShipState withHp(float hp) {
        return new ShipState(hp, maxHp, modules, decompressed, decompressionReason, isDocking);
    }

    public ShipState withDecompression(boolean decompressed, String reason) {
        return new ShipState(hp, maxHp, modules, decompressed, reason, isDocking);
    }

    public ShipState withModules(List<ShipSystemModule> modules) {
        return new ShipState(hp, maxHp, modules, decompressed, decompressionReason, isDocking);
    }

    public ShipState withDocking(boolean isDocking) {
        return new ShipState(hp, maxHp, modules, decompressed, decompressionReason, isDocking);
    }

    public boolean hasModule(ShipSystemType type) {
        for (ShipSystemModule module : modules) {
            if (module.type() == type && module.level() > 0) {
                return true;
            }
        }
        return false;
    }

    public int moduleLevel(ShipSystemType type) {
        for (ShipSystemModule module : modules) {
            if (module.type() == type) {
                return module.level();
            }
        }
        return 0;
    }

    public ShipState withInstalledModule(ShipSystemType type) {
        List<ShipSystemModule> updated = new ArrayList<>(modules);
        boolean found = false;
        for (int i = 0; i < updated.size(); i++) {
            ShipSystemModule mod = updated.get(i);
            if (mod.type() == type) {
                updated.set(i, mod.withLevel(mod.level() + 1));
                found = true;
                break;
            }
        }
        if (!found) {
            updated.add(new ShipSystemModule(type, 1, type.defaultDurability(), type.defaultDurability()));
        }
        return withModules(updated);
    }

    public float hpPercent() {
        return maxHp <= 0.0F ? 0.0F : hp / maxHp;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("hp", hp);
        tag.putFloat("maxHp", maxHp);
        tag.putBoolean("decompressed", decompressed);
        tag.putString("decompressionReason", decompressionReason);
        tag.putBoolean("isDocking", isDocking);
        ListTag moduleTags = new ListTag();
        for (ShipSystemModule module : modules) {
            moduleTags.add(module.save());
        }
        tag.put("modules", moduleTags);
        return tag;
    }

    public static ShipState load(CompoundTag tag) {
        float maxHp = tag.contains("maxHp") ? tag.getFloat("maxHp") : DEFAULT_MAX_HP;
        float hp = tag.contains("hp") ? tag.getFloat("hp") : maxHp;
        List<ShipSystemModule> modules = new ArrayList<>();
        ListTag moduleTags = tag.getList("modules", Tag.TAG_COMPOUND);
        for (Tag moduleTag : moduleTags) {
            modules.add(ShipSystemModule.load((CompoundTag) moduleTag));
        }
        boolean decompressed = tag.getBoolean("decompressed");
        String reason = tag.contains("decompressionReason") ? tag.getString("decompressionReason") : "sealed";
        boolean isDocking = tag.contains("isDocking") ? tag.getBoolean("isDocking") : tag.getBoolean("docking");
        return new ShipState(hp, maxHp, modules, decompressed, reason, isDocking);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeFloat(hp);
        buffer.writeFloat(maxHp);
        buffer.writeBoolean(decompressed);
        buffer.writeUtf(decompressionReason);
        buffer.writeBoolean(isDocking);
        buffer.writeVarInt(modules.size());
        for (ShipSystemModule module : modules) {
            module.encode(buffer);
        }
    }

    public static ShipState decode(FriendlyByteBuf buffer) {
        float hp = buffer.readFloat();
        float maxHp = buffer.readFloat();
        boolean decompressed = buffer.readBoolean();
        String reason = buffer.readUtf();
        boolean isDocking = buffer.readBoolean();
        int moduleCount = buffer.readVarInt();
        List<ShipSystemModule> modules = new ArrayList<>(moduleCount);
        for (int i = 0; i < moduleCount; i++) {
            modules.add(ShipSystemModule.decode(buffer));
        }
        return new ShipState(hp, maxHp, modules, decompressed, reason, isDocking);
    }

    private static List<ShipSystemModule> defaultModules() {
        List<ShipSystemModule> modules = new ArrayList<>();
        for (ShipSystemType type : ShipSystemType.values()) {
            if (!type.isUpgrade()) {
                modules.add(ShipSystemModule.defaultModule(type));
            }
        }
        return modules;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
