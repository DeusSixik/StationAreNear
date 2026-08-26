package dev.sixik.stationarenear.quest.data;

public record QuestLocalization(String playerText, String samText) {

    public QuestLocalization {
        playerText = playerText == null ? "" : playerText.trim();
        samText = samText == null ? "" : samText.trim();
    }

    public static QuestLocalization of(String playerText, String samText) {
        return new QuestLocalization(playerText, samText);
    }

    public static QuestLocalization fallback(String id) {
        return new QuestLocalization(id, id);
    }

    public String playerText(String fallback) {
        return playerText.isBlank() ? fallback : playerText;
    }

    public String samText(String fallback) {
        return samText.isBlank() ? playerText(fallback) : samText;
    }
}
