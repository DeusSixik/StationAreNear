package dev.sixik.stationarenear.terminal.data;

import java.util.List;
import java.util.stream.Collectors;

public final class TerminalCommandCatalog {

    public static final List<TerminalCommandDefinition> COMMANDS = List.of(
            new TerminalCommandDefinition("help", "available terminal commands"),
            new TerminalCommandDefinition("status", "ship HP, sealing and navigation speed"),
            new TerminalCommandDefinition("modules", "installed ship modules"),
            new TerminalCommandDefinition("stations", "stations near current solar position"),
            new TerminalCommandDefinition("scan", "alias for stations"),
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
