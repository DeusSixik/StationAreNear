package dev.sixik.stationarenear.terminal.world;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.terminal.data.TerminalHistoryKind;
import dev.sixik.stationarenear.terminal.data.TerminalHistoryLine;
import dev.sixik.stationarenear.terminal.data.TerminalHistoryText;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

public class TerminalSavedData extends SavedData {

    private static final String DATA_NAME = StationAreNear.MODID + "_terminal_history";
    private static final int DEFAULT_MAX_HISTORY_LINES = 512;

    private final Long2ObjectMap<List<TerminalHistoryLine>> histories = new Long2ObjectLinkedOpenHashMap<>();

    public static TerminalSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TerminalSavedData::load, TerminalSavedData::new, DATA_NAME);
    }

    public List<TerminalHistoryLine> history(BlockPos terminalPos) {
        List<TerminalHistoryLine> history = histories.computeIfAbsent(terminalPos.asLong(), ignored -> defaultHistory());
        if (history.isEmpty()) {
            history.addAll(defaultHistory());
            setDirty();
        }
        return List.copyOf(TerminalHistoryText.wrapAll(history));
    }

    public void append(BlockPos terminalPos, TerminalHistoryKind kind, String text) {
        List<TerminalHistoryLine> history = histories.computeIfAbsent(terminalPos.asLong(), ignored -> defaultHistory());
        history.addAll(TerminalHistoryText.wrap(new TerminalHistoryLine(kind, text)));
        trim(history);
        setDirty();
    }

    public void appendAll(BlockPos terminalPos, List<TerminalHistoryLine> lines) {
        if (lines.isEmpty()) {
            return;
        }
        List<TerminalHistoryLine> history = histories.computeIfAbsent(terminalPos.asLong(), ignored -> defaultHistory());
        history.addAll(TerminalHistoryText.wrapAll(lines));
        trim(history);
        setDirty();
    }

    public void clear(BlockPos terminalPos) {
        histories.put(terminalPos.asLong(), defaultHistory());
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag terminalTags = new ListTag();
        for (Long2ObjectMap.Entry<List<TerminalHistoryLine>> entry : histories.long2ObjectEntrySet()) {
            CompoundTag terminalTag = new CompoundTag();
            terminalTag.putLong("terminal", entry.getLongKey());
            ListTag lineTags = new ListTag();
            for (TerminalHistoryLine line : entry.getValue()) {
                lineTags.add(line.save());
            }
            terminalTag.put("lines", lineTags);
            terminalTags.add(terminalTag);
        }
        tag.put("terminals", terminalTags);
        return tag;
    }

    private static TerminalSavedData load(CompoundTag tag) {
        TerminalSavedData data = new TerminalSavedData();
        ListTag terminalTags = tag.getList("terminals", Tag.TAG_COMPOUND);
        for (Tag terminalEntry : terminalTags) {
            try {
                CompoundTag terminalTag = (CompoundTag) terminalEntry;
                List<TerminalHistoryLine> lines = new ArrayList<>();
                ListTag lineTags = terminalTag.getList("lines", Tag.TAG_COMPOUND);
                for (Tag lineEntry : lineTags) {
                    lines.add(TerminalHistoryLine.load((CompoundTag) lineEntry));
                }
                data.histories.put(terminalTag.getLong("terminal"), lines);
            } catch (RuntimeException exception) {
                StationAreNear.LOGGER.warn("Skipped broken terminal history", exception);
            }
        }
        return data;
    }

    private static List<TerminalHistoryLine> defaultHistory() {
        List<TerminalHistoryLine> history = new ArrayList<>();
        history.add(new TerminalHistoryLine(TerminalHistoryKind.INFO, "The service is provided by Helicorp"));
        history.add(new TerminalHistoryLine(TerminalHistoryKind.INFO, "Type / or press Tab to show commands: status, modules, stations, scan <id>."));
        return history;
    }

    private static void trim(List<TerminalHistoryLine> history) {
        while (history.size() > DEFAULT_MAX_HISTORY_LINES) {
            history.remove(0);
        }
    }
}
