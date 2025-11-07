package fr.univtln.pegliasco.tp;

import java.util.ArrayList;
import java.util.List;

public final class MessageUtils {

    private static final int DISCORD_LIMIT = 2000;

    private MessageUtils() {}

    public static List<String> splitForDiscord(String text) {
        List<String> parts = new ArrayList<>();
        if (text == null) return parts;
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + DISCORD_LIMIT, text.length());
            if (end < text.length()) {
                int nl = text.lastIndexOf('\n', end - 1);
                if (nl > start + 200) end = nl + 1;
            }
            parts.add(text.substring(start, end));
            start = end;
        }
        return parts;
    }

    public static String escapeDiscordMarkdown(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("`", "\\`")
                .replace("~", "\\~")
                .replace("|", "\\|")
                .replace(">", "\\>")
                .replace("#", "\\#");
    }
}
