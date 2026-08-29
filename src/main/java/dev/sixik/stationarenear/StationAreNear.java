package dev.sixik.stationarenear;

import com.mojang.logging.LogUtils;
import dev.sixik.stationarenear.mob.StationMobModule;
import dev.sixik.stationarenear.navigation.SolarNavigationModule;
import dev.sixik.stationarenear.quest.QuestModule;
import dev.sixik.stationarenear.sam.SamModule;
import dev.sixik.stationarenear.ship.ShipModule;
import dev.sixik.stationarenear.structures.StationStructureModule;
import dev.sixik.stationarenear.terminal.TerminalModule;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

@Mod(StationAreNear.MODID)
public class StationAreNear {

    public static final String MODID = "stationarenear";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static IEventBus modEventBus;

    public StationAreNear() {
        modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        StationMobModule.register(modEventBus);
        StationStructureModule.register(modEventBus);
        ShipModule.register(modEventBus);
        QuestModule.register(modEventBus);
        SolarNavigationModule.register(modEventBus);
        TerminalModule.register(modEventBus);
        SamModule.register(modEventBus);

        
    }

    @NotNull
    public static IEventBus getModEventBus() {
        return modEventBus;
    }
}
