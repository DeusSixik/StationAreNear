package dev.sixik.stationarenear.core.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mixin(RecipeManager.class)
public class RecipeManagerMixin {

    @Inject(method = "apply", at = @At("HEAD"))
    private void stationarenear$removeVanillaCraftingServer(Map<ResourceLocation, JsonElement> map, ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo ci) {
        map.keySet().removeIf(id -> "minecraft".equals(id.getNamespace()) && isCraftingRecipe(map.get(id)));
    }

    @ModifyVariable(method = "replaceRecipes", at = @At("HEAD"), argsOnly = true)
    private Iterable<Recipe<?>> stationarenear$removeVanillaCraftingClient(Iterable<Recipe<?>> recipes) {
        List<Recipe<?>> filtered = new ArrayList<>();
        for (Recipe<?> recipe : recipes) {
            if (recipe.getType() == RecipeType.CRAFTING && "minecraft".equals(recipe.getId().getNamespace())) {
                continue;
            }
            filtered.add(recipe);
        }
        return filtered;
    }

    private static boolean isCraftingRecipe(JsonElement element) {
        if (element != null && element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("type")) {
                String type = obj.get("type").getAsString();
                return type.startsWith("minecraft:crafting_") || type.startsWith("crafting_");
            }
        }
        return false;
    }
}
