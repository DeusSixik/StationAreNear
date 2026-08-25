package dev.sixik.stationarenear.terminal;

import dev.sixik.stationarenear.terminal.network.MapTerminalNetwork;
import dev.sixik.stationarenear.terminal.network.TerminalNetwork;
import dev.sixik.stationarenear.terminal.registry.TerminalBlocks;
import net.minecraftforge.eventbus.api.IEventBus;

public final class TerminalModule {

    private TerminalModule() {
    }

    public static void register(IEventBus modEventBus) {
        TerminalNetwork.register();
        MapTerminalNetwork.register();
        TerminalBlocks.register(modEventBus);
    }
}
