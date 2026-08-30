package dev.sixik.stationarenear.ship.data;

import dev.sixik.stationarenear.quest.registry.QuestItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ShipModuleRecipe {

    public record Ingredient(String itemId, int count) {

        public boolean has(ServerPlayer player) {
            int found = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (matches(stack, itemId)) {
                    found += stack.getCount();
                }
            }
            return found >= count;
        }

        public void consume(ServerPlayer player) {
            int needed = count;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (matches(stack, itemId)) {
                    int take = Math.min(needed, stack.getCount());
                    stack.shrink(take);
                    needed -= take;
                    if (needed <= 0) {
                        break;
                    }
                }
            }
        }

        public String displayName() {
            ItemStack stack = resolveStack(itemId, count);
            if (!stack.isEmpty() && !stack.is(Items.AIR)) {
                return stack.getHoverName().getString();
            }
            String name = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
            name = name.replace("_", " ");
            return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }

        public static boolean matches(ItemStack stack, String itemId) {
            if (stack.isEmpty()) {
                return false;
            }
            ResourceLocation reg = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (reg == null) {
                return false;
            }
            String path = reg.getPath().toLowerCase(Locale.ROOT);
            String full = reg.toString().toLowerCase(Locale.ROOT);
            String target = itemId.toLowerCase(Locale.ROOT).replace(" ", "_");
            if (full.equals(target) || path.equals(target)) {
                return true;
            }
            if (target.contains(":")) {
                return full.equals(target);
            }
            String cleanPath = path.replace("_", "");
            String cleanTarget = target.replace("_", "");
            if (cleanPath.equals(cleanTarget)) {
                return true;
            }
            if (cleanTarget.contains("elictricity") && cleanPath.contains("electricity")) {
                return true;
            }
            return false;
        }

        private static ItemStack resolveStack(String itemId, int count) {
            if (itemId.contains(":")) {
                ResourceLocation loc = ResourceLocation.tryParse(itemId);
                if (loc != null && ForgeRegistries.ITEMS.containsKey(loc)) {
                    return new ItemStack(ForgeRegistries.ITEMS.getValue(loc), count);
                }
            }
            for (String namespace : List.of("station_blocks", "stationarenear", "minecraft")) {
                ResourceLocation loc = ResourceLocation.tryParse(namespace + ":" + itemId.toLowerCase(Locale.ROOT).replace(" ", "_"));
                if (loc != null && ForgeRegistries.ITEMS.containsKey(loc)) {
                    return new ItemStack(ForgeRegistries.ITEMS.getValue(loc), count);
                }
            }
            return ItemStack.EMPTY;
        }
    }

    private static final Map<ShipSystemType, List<Ingredient>> RECIPES = Map.of(
            ShipSystemType.AUTO_DOORS, List.of(
                    new Ingredient("spring", 1),
                    new Ingredient("cog", 1)
            ),
            ShipSystemType.PANEL_DETECTOR, List.of(
                    new Ingredient("stationarenear:electricity_repair_kit", 1)
            ),
            ShipSystemType.CRAFT_STATION, List.of(
                    new Ingredient("stationarenear:repair_kit", 1),
                    new Ingredient("spring", 1),
                    new Ingredient("logic_component", 1),
                    new Ingredient("lamp", 1),
                    new Ingredient("cable", 1)
            ),
            ShipSystemType.EXTRA_STORAGE, List.of(
                    new Ingredient("metal_sheet", 2),
                    new Ingredient("metal_screws", 1),
                    new Ingredient("metal_rods", 3)
            ),
            ShipSystemType.MANEUVERABILITY, List.of(
                    new Ingredient("metal_sheet", 10),
                    new Ingredient("stationarenear:repair_kit", 1)
//                    new Ingredient("stationarenear:putty_bucket", 1)
            ),
            ShipSystemType.PLAYER_TRACKER, List.of(
                    new Ingredient("videocard", 1),
                    new Ingredient("monitor_item", 1)
            ),
            ShipSystemType.STATION_LOCATOR, List.of(
                    new Ingredient("satelliteantenna", 1),
                    new Ingredient("videocard", 1)
            ),
            ShipSystemType.RAD_SHIELDING, List.of(
                    new Ingredient("radiation_isolation", 20)
            )
    );

    private ShipModuleRecipe() {
    }

    public static List<Ingredient> ingredients(ShipSystemType type) {
        return RECIPES.getOrDefault(type, List.of());
    }

    public static boolean canAfford(ServerPlayer player, ShipSystemType type) {
        List<Ingredient> list = ingredients(type);
        if (list.isEmpty()) {
            return true;
        }
        for (Ingredient ing : list) {
            if (!ing.has(player)) {
                return false;
            }
        }
        return true;
    }

    public static void consume(ServerPlayer player, ShipSystemType type) {
        for (Ingredient ing : ingredients(type)) {
            ing.consume(player);
        }
    }

    public static List<String> missingIngredients(ServerPlayer player, ShipSystemType type) {
        List<String> missing = new ArrayList<>();
        for (Ingredient ing : ingredients(type)) {
            if (!ing.has(player)) {
                missing.add(ing.displayName() + " x" + ing.count());
            }
        }
        return missing;
    }
}
