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

            // 🔹 Si c'est le DERNIER chunk, on envoie tout (pas besoin de chercher un joli cut)
            if (hardEnd == n) {
                cut = n;
            } else {
                // 🔹 Sinon on cherche un point de coupure "propre" proche de la limite
                cut = findBestCut(text, start, hardEnd);
                if (cut == -1) {
                    cut = hardEnd;
                }
            }

            // Sécurité anti-emoji (eviter de couper une paire surrogée)
            if (cut < n && cut > start && Character.isLowSurrogate(text.charAt(cut - 1))) {
                cut--;
            }

            parts.add(text.substring(start, cut));
            start = cut;
        }

        return parts;
    }

    /**
     * Cherche un endroit propre pour couper :
     * - un saut de ligne proche de la limite
     * - sinon un espace proche de la limite
     * On ne remonte pas de plus de 250 caractères pour éviter de faire un bloc minuscule.
     */
    private static int findBestCut(String text, int start, int hardEnd) {
        int minPreferred = Math.max(start, hardEnd - 250);

        // 1) essayer un saut de ligne
        int nl = text.lastIndexOf('\n', hardEnd - 1);
        if (nl >= minPreferred) {
            return nl + 1;
        }

        // 2) sinon, essayer un espace
        int space = text.lastIndexOf(' ', hardEnd - 1);
        if (space >= minPreferred) {
            return space + 1;
        }

        // rien trouvé de satisfaisant
        return -1;
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
