// java
package fr.univtln.pegliasco.tp;

import java.util.ArrayList;
import java.util.List;

public final class MessageUtils {

    private static final int DISCORD_LIMIT = 2000;

    private MessageUtils() {}

    public static List<String> splitForDiscord(String text) {
        List<String> parts = new ArrayList<>();
        if (text == null || text.isEmpty()) return parts;

        int start = 0;
        final int n = text.length();

        while (start < n) {
            int hardEnd = Math.min(start + DISCORD_LIMIT, n);
            int cut;

            // Évite de couper une paire supplémente (emoji)
            if (end < n && Character.isLowSurrogate(text.charAt(end - 1))) {
                end--;
            }

            // Préfère un saut de ligne raisonnablement proche
            int nl = text.lastIndexOf('\n', end - 1);
            if (nl >= start + 200) {
                end = nl + 1;
            } else {
                // Sinon, coupe à l'espace pour éviter de casser un mot
                int sp = text.lastIndexOf(' ', end - 1);
                if (sp >= start + 200) end = sp + 1;
            }

            parts.add(text.substring(start, end));
            start = end;
        }
        return parts;
    }

    public static String escapeDiscordMarkdown(String text) {
        if (text == null) return "";
        // Échappement minimal pour lisibilité
        return text
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("~", "\\~");
    }
}
