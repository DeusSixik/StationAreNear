package dev.sixik.stationarenear;

import com.mojang.logging.LogUtils;
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
    }

    @NotNull
    public static IEventBus getModEventBus() {
        return modEventBus;
    }
}
