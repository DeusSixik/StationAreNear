package dev.sixik.stationarenear.quest.client;

import dev.sixik.stationarenear.quest.data.QuestPlacementZoneHint;

import java.util.ArrayList;
import java.util.List;

public final class QuestPlacementZoneClientState {

    private static List<QuestPlacementZoneHint> hints = List.of();

    private QuestPlacementZoneClientState() {
    }

    public static synchronized void setHints(List<QuestPlacementZoneHint> newHints) {
        hints = newHints == null ? List.of() : new ArrayList<>(newHints);
    }

    public static synchronized List<QuestPlacementZoneHint> hints() {
        return hints;
    }

    public static synchronized void clear() {
        hints = List.of();
    }
}
