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

    public static final RegistryObject<Item> REPAIR_KIT = ITEMS.register(
            "repair_kit",
            () -> new Item(new Item.Properties().durability(3))
    );

    public static final RegistryObject<Item> OXYGEN_REPAIR_KIT = ITEMS.register(
            "oxygen_repair_kit",
            () -> new Item(new Item.Properties().durability(3))
    );

    public static final RegistryObject<Item> GRAVITATION_REPAIR_KIT = ITEMS.register(
            "gravitation_repair_kit",
            () -> new Item(new Item.Properties().durability(3))
    );

    public static final RegistryObject<Item> ELECTRICITY_REPAIR_KIT = ITEMS.register(
            "electricity_repair_kit",
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
                    .icon(() -> new ItemStack(REPAIR_KIT.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(BUCKET.get());
                        output.accept(CUTTERS.get());
                        output.accept(REPAIR_KIT.get());
                        output.accept(OXYGEN_REPAIR_KIT.get());
                        output.accept(GRAVITATION_REPAIR_KIT.get());
                        output.accept(ELECTRICITY_REPAIR_KIT.get());
                        output.accept(ENGINEERING_GEAR.get());
                        output.accept(MOP.get());
                        output.accept(PUTTY_BUCKET.get());
                        output.accept(STATION_SHEATHING.get());
                        output.accept(QuestBlocks.FRIDGE_ITEM.get());
                        output.accept(QuestBlocks.KITCHEN_SINK_ITEM.get());
                        output.accept(QuestBlocks.MICROWAVE_ITEM.get());
                        output.accept(QuestBlocks.ENERGY_PANEL_ITEM.get());
                        output.accept(QuestBlocks.BROKEN_ENERGY_PANEL_ITEM.get());
                        output.accept(QuestBlocks.OXYGEN_PANEL_ITEM.get());
                        output.accept(QuestBlocks.BROKEN_OXYGEN_PANEL_ITEM.get());
                        output.accept(QuestBlocks.GRAVITATION_PANEL_ITEM.get());
                        output.accept(QuestBlocks.BROKEN_GRAVITATION_PANEL_ITEM.get());
                        output.accept(QuestBlocks.CONSOLE_NO_ANGLE_ITEM.get());
                        output.accept(QuestBlocks.WORKBENCH_ITEM.get());
                        output.accept(dev.sixik.stationarenear.terminal.registry.TerminalItems.HAND_TERMINAL.get());
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
