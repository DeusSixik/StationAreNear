package dev.sixik.stationarenear.quest;

import dev.sixik.stationarenear.quest.command.QuestCommands;
import dev.sixik.stationarenear.quest.runtime.QuestAnnouncementHandler;
import dev.sixik.stationarenear.quest.runtime.QuestTimerManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

public final class QuestModule {

    private QuestModule() {
    }

    public static void register(IEventBus modEventBus) {
        QuestCommands.register();
        MinecraftForge.EVENT_BUS.addListener(QuestTimerManager::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(QuestTimerManager::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(QuestAnnouncementHandler::onQuestStarted);
    }
}
