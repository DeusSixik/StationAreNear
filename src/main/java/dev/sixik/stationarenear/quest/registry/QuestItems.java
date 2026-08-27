package dev.sixik.stationarenear.quest.registry;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.quest.item.MopItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class QuestItems {

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, StationAreNear.MODID);
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, StationAreNear.MODID);

    public static final RegistryObject<Item> BUCKET = ITEMS.register(
            "bucket",
            () -> new Item(new Item.Properties())
    );

    public static final RegistryObject<Item> CUTTERS = ITEMS.register(
            "cutters",
            () -> new Item(new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> ENGINEERING_GEAR = ITEMS.register(
            "engineering_gear",
            () -> new Item(new Item.Properties().durability(3))
    );

    public static final RegistryObject<Item> MOP = ITEMS.register(
            "mop",
            () -> new MopItem(new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> PUTTY_BUCKET = ITEMS.register(
            "putty_bucket",
            () -> new Item(new Item.Properties())
    );

    public static final RegistryObject<Item> STATION_SHEATHING = ITEMS.register(
            "station_sheathing",
            () -> new Item(new Item.Properties())
    );

    public static final RegistryObject<CreativeModeTab> STATION_ARE_NEAR_TAB = CREATIVE_TABS.register(
            "station_are_near",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.stationarenear.station_are_near"))
                    .icon(() -> new ItemStack(ENGINEERING_GEAR.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(BUCKET.get());
                        output.accept(CUTTERS.get());
                        output.accept(ENGINEERING_GEAR.get());
                        output.accept(MOP.get());
                        output.accept(PUTTY_BUCKET.get());
                        output.accept(STATION_SHEATHING.get());
                    })
                    .build()
    );

    private QuestItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
    }
}
