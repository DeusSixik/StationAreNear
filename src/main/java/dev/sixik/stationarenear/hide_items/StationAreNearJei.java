package dev.sixik.stationarenear.hide_items;

import dev.sixik.stationarenear.StationAreNear;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@JeiPlugin
public class StationAreNearJei implements IModPlugin {

    public static IJeiRuntime runtime;

    private static final Set<String> HIDDEN_IDS = Set.of(
            "stationarenear:ship_television",
            "stationarenear:solar_navigation_terminal",
            "stationarenear:terminal",
            "stationarenear:station_map_terminal",
            "stationarenear:pressure_tight_door",
            "stationarenear:station_pressure_tight_door",
            "stationarenear:living_trash_spawn_egg",
            "stationarenear:cadaver_spawn_egg",
            "stationarenear:energy_panel",
            "stationarenear:station_sheathing",
            "stationarenear:console_no_angle"
    );

    @Override
    public @Nullable ResourceLocation getPluginUid() {
        return ResourceLocation.tryBuild(StationAreNear.MODID, "main");
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(dev.sixik.stationarenear.quest.registry.QuestBlocks.WORKBENCH.get()), RecipeTypes.CRAFTING);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;

        IIngredientManager ingredientManager = jeiRuntime.getIngredientManager();
        Collection<ItemStack> allItemStacks = ingredientManager.getAllIngredients(VanillaTypes.ITEM_STACK);

        List<ItemStack> toHide = new ArrayList<>();
        for (ItemStack stack : allItemStacks) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id == null) {
                continue;
            }
            if (id.getNamespace().equals("minecraft") || HIDDEN_IDS.contains(id.toString())) {
                toHide.add(stack);
            }
        }

        if (!toHide.isEmpty()) {
            ingredientManager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, toHide);
        }

//        var recipeManager = jeiRuntime.getRecipeManager();
//        List<net.minecraft.world.item.crafting.CraftingRecipe> vanillaCrafting = recipeManager.createRecipeLookup(RecipeTypes.CRAFTING)
//                .get()
//                .filter(recipe -> "minecraft".equals(recipe.getId().getNamespace()))
//                .toList();
//        if (!vanillaCrafting.isEmpty()) {
//            recipeManager.hideRecipes(RecipeTypes.CRAFTING, vanillaCrafting);
//        }
    }
}
