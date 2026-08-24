package dev.sixik.stationarenear.terminal.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public record TerminalHistoryLine(TerminalHistoryKind kind, String text) {

    public TerminalHistoryLine {
        kind = kind == null ? TerminalHistoryKind.OUTPUT : kind;
        text = text == null ? "" : text;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", kind.id());
        tag.putString("text", text);
        return tag;
    }

    public static TerminalHistoryLine load(CompoundTag tag) {
        return new TerminalHistoryLine(TerminalHistoryKind.byId(tag.getString("kind")), tag.getString("text"));
    }

    public void encode(FriendlyByteBuf buffer) {
        kind.encode(buffer);
        buffer.writeUtf(text);
    }

    public static TerminalHistoryLine decode(FriendlyByteBuf buffer) {
        return new TerminalHistoryLine(TerminalHistoryKind.decode(buffer), buffer.readUtf());
    }
}
