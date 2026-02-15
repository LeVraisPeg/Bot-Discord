package fr.univtln.pegliasco.tp;

import java.util.ArrayList;
import java.util.List;

public final class MessageUtils {

    private static final int DISCORD_LIMIT = 2000;

    private MessageUtils() {}

    public static int discordLimit() {
        return DISCORD_LIMIT;
    }

    /**
     * Tronque le texte à {@code maxChars} (en caractères Java), ajoute un suffixe si tronqué,
     * et évite de casser une paire surrogée (emoji, etc.).
     */
    public static String truncateSafe(String text, int maxChars, String truncatedSuffix) {
        if (text == null) return "";
        if (maxChars <= 0) return "";

        String suffix = truncatedSuffix == null ? "" : truncatedSuffix;
        if (text.length() <= maxChars) return text;

        int end = Math.max(0, maxChars - suffix.length());
        end = Math.min(end, text.length());

        if (end > 0 && end < text.length() && Character.isLowSurrogate(text.charAt(end - 1))) {
            end--;
        }

        if (end <= 0) {
            // Limite trop petite: on renvoie uniquement le suffixe (tronqué si besoin)
            return suffix.length() <= maxChars ? suffix : suffix.substring(0, maxChars);
        }

        return text.substring(0, end) + suffix;
    }

    public static List<String> splitForDiscord(String text) {
        List<String> parts = new ArrayList<>();
        if (text == null || text.isEmpty()) return parts;

        int start = 0;
        final int n = text.length();

        while (start < n) {
            int hardEnd = Math.min(start + DISCORD_LIMIT, n);
            int cut;

            // Si c'est le DERNIER chunk, on envoie tout
            if (hardEnd == n) {
                cut = n;
            } else {
                // Sinon on cherche un point de coupure "propre" proche de la limite
                cut = findBestCut(text, start, hardEnd);
                if (cut == -1) {
                    cut = hardEnd;
                }
            }

            // Sécurité anti-emoji (éviter de couper une paire surrogée)
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
