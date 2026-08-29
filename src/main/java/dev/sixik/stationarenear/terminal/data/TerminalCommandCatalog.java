package dev.sixik.stationarenear.terminal.data;

import java.util.List;
import java.util.stream.Collectors;

public final class TerminalCommandCatalog {

    public static final List<TerminalCommandDefinition> COMMANDS = List.of(
            new TerminalCommandDefinition("help", "available terminal commands"),
            new TerminalCommandDefinition("status", "ship HP, sealing and navigation speed"),
            new TerminalCommandDefinition("modules", "installed ship modules"),
            new TerminalCommandDefinition("door", "door open|close [id] pressure-tight door"),
            new TerminalCommandDefinition("tv", "tv text|ship_status|ship_scan|clear television"),
            new TerminalCommandDefinition("tv_clear", "clear manual ship television text"),
            new TerminalCommandDefinition("tv_pos", "CENTER|TOP|DOWN television manual text position"),
            new TerminalCommandDefinition("tv_scale", "set manual television text scale"),
            new TerminalCommandDefinition("objectives", "current mission objectives"),
            new TerminalCommandDefinition("map", "open docked station level map"),
            new TerminalCommandDefinition("stations", "known station IDs near current solar position"),
            new TerminalCommandDefinition("scan", "scan <station_id> and show distance"),
            new TerminalCommandDefinition("store", "store [<index> <count>] — browse or buy items"),
            new TerminalCommandDefinition("balance", "show your current credit balance"),
            new TerminalCommandDefinition("clear", "clear shared terminal history"),
            new TerminalCommandDefinition("cls", "alias for clear")
    );

    private TerminalCommandCatalog() {
    }

    public static String summary() {
        return COMMANDS.stream()
                .map(TerminalCommandDefinition::command)
                .collect(Collectors.joining(", "));
    }
}
