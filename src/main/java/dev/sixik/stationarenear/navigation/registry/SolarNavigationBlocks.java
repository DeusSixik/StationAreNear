package dev.sixik.stationarenear.navigation.registry;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.navigation.block.SolarNavigationTerminalBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class SolarNavigationBlocks {

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, StationAreNear.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, StationAreNear.MODID);

    public static final RegistryObject<SolarNavigationTerminalBlock> SOLAR_NAVIGATION_TERMINAL = BLOCKS.register(
            "solar_navigation_terminal",
            () -> new SolarNavigationTerminalBlock(BlockBehaviour.Properties.of()
                    .strength(2.5F, 8.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .requiresCorrectToolForDrops())
    );

    public static final RegistryObject<Item> SOLAR_NAVIGATION_TERMINAL_ITEM = ITEMS.register(
            "solar_navigation_terminal",
            () -> new BlockItem(SOLAR_NAVIGATION_TERMINAL.get(), new Item.Properties())
    );

    private SolarNavigationBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(SolarNavigationBlocks::addCreativeTabItems);
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(SOLAR_NAVIGATION_TERMINAL_ITEM.get());
        }
    }
}
