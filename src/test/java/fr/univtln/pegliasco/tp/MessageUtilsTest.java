package fr.univtln.pegliasco.tp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MessageUtilsTest {

    @Test
    void truncateSafe_shouldNotChangeWhenUnderLimit() {
        String s = "bonjour";
        assertEquals(s, MessageUtils.truncateSafe(s, 2000, "...[x]"));
    }

    @Test
    void truncateSafe_shouldAddSuffixAndRespectLimit() {
        String s = "a".repeat(2100);
        String out = MessageUtils.truncateSafe(s, 2000, "...[tronqué]");
        assertEquals(2000, out.length());
        assertTrue(out.endsWith("...[tronqué]"));
    }

    @Test
    void truncateSafe_shouldNotCutSurrogatePair() {
        // 😀 is represented as a surrogate pair in UTF-16
        String emoji = "😀";
        assertEquals(2, emoji.length());

        String s = "a".repeat(1999) + emoji; // len = 2001
        String out = MessageUtils.truncateSafe(s, 2000, "");
        assertEquals(2000, out.length());
        // last char must not be an orphan low surrogate
        assertFalse(Character.isLowSurrogate(out.charAt(out.length() - 1)));
    }

    @Test
    void splitForDiscord_shouldSplitIntoMax2000Chunks() {
        String s = "a".repeat(4500);
        List<String> parts = MessageUtils.splitForDiscord(s);
        assertEquals(3, parts.size());
        assertEquals(2000, parts.get(0).length());
        assertEquals(2000, parts.get(1).length());
        assertEquals(500, parts.get(2).length());
    }
}

