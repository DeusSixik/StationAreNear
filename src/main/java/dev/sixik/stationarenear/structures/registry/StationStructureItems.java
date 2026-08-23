package dev.sixik.stationarenear.structures.registry;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.structures.item.StationStructureToolItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class StationStructureItems {

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, StationAreNear.MODID);

    public static final RegistryObject<Item> STATION_STRUCTURE_TOOL = ITEMS.register(
            "station_structure_tool",
            () -> new StationStructureToolItem(new Item.Properties().stacksTo(1))
    );

    private StationStructureItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
