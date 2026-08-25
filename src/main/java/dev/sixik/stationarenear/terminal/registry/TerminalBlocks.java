package dev.sixik.stationarenear.terminal.registry;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.terminal.block.StationMapTerminalBlock;
import dev.sixik.stationarenear.terminal.block.TerminalBlock;
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

public final class TerminalBlocks {

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, StationAreNear.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, StationAreNear.MODID);

    public static final RegistryObject<TerminalBlock> TERMINAL = BLOCKS.register(
            "terminal",
            () -> new TerminalBlock(BlockBehaviour.Properties.of()
                    .strength(2.5F, 8.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops())
    );

    public static final RegistryObject<Item> TERMINAL_ITEM = ITEMS.register(
            "terminal",
            () -> new BlockItem(TERMINAL.get(), new Item.Properties())
    );

    public static final RegistryObject<StationMapTerminalBlock> STATION_MAP_TERMINAL = BLOCKS.register(
            "station_map_terminal",
            () -> new StationMapTerminalBlock(BlockBehaviour.Properties.of()
                    .strength(2.5F, 8.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops())
    );

    public static final RegistryObject<Item> STATION_MAP_TERMINAL_ITEM = ITEMS.register(
            "station_map_terminal",
            () -> new BlockItem(STATION_MAP_TERMINAL.get(), new Item.Properties())
    );

    private TerminalBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(TerminalBlocks::addCreativeTabItems);
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(TERMINAL_ITEM.get());
            event.accept(STATION_MAP_TERMINAL_ITEM.get());
        }
    }
}
