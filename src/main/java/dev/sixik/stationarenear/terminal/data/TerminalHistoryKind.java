package dev.sixik.stationarenear.terminal.data;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Locale;

public enum TerminalHistoryKind {
    COMMAND,
    INFO,
    WARNING,
    ERROR,
    OUTPUT;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this);
    }

    public static TerminalHistoryKind decode(FriendlyByteBuf buffer) {
        return buffer.readEnum(TerminalHistoryKind.class);
    }

    public static TerminalHistoryKind byId(String id) {
        if (id != null) {
            for (TerminalHistoryKind kind : values()) {
                if (kind.id().equals(id) || kind.name().equalsIgnoreCase(id)) {
                    return kind;
                }
            }
        }
        return OUTPUT;
    }
}
