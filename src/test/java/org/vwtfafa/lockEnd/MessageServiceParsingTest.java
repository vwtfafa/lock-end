package org.vwtfafa.lockEnd;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The bundled language files mix legacy section codes with MiniMessage markup,
 * so parsing must accept both instead of throwing on legacy input.
 */
class MessageServiceParsingTest {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static Component render(String raw) {
        return MessageService.render(raw, MINI_MESSAGE, true);
    }

    @Test
    void legacyActionbarLockedDoesNotThrow() {
        Component rendered = assertDoesNotThrow(() -> render("§c✗ End is locked"));

        assertEquals("✗ End is locked", plain(rendered));
    }

    @Test
    void legacyActionbarUnlockedDoesNotThrow() {
        Component rendered = assertDoesNotThrow(() -> render("§a✓ End is unlocked"));

        assertEquals("✓ End is unlocked", plain(rendered));
    }

    @Test
    void legacyBroadcastKeepsPlaceholderLiteral() {
        Component rendered = assertDoesNotThrow(
                () -> render("§6[§cEndLock§6] End has been §clocked§6 by §b%player%"));

        assertEquals("[EndLock] End has been locked by %player%", plain(rendered));
    }

    @Test
    void legacyUsageHintKeepsAngleBracketsLiteral() {
        Component rendered = assertDoesNotThrow(
                () -> render("§cUsage: /endlock unlockin <days> or /endlock unlockat <yyyy-MM-dd> <HH:mm>"));

        assertEquals("Usage: /endlock unlockin <days> or /endlock unlockat <yyyy-MM-dd> <HH:mm>",
                plain(rendered));
    }

    @Test
    void miniMessageGradientStillParses() {
        Component rendered = assertDoesNotThrow(
                () -> render("<gradient:#ff0000:#ffff00>The End is locked!</gradient>"));

        assertEquals("The End is locked!", plain(rendered));
    }

    @Test
    void malformedMiniMessageFallsBackInsteadOfThrowing() {
        Component rendered = assertDoesNotThrow(() -> render("<red>oops"));

        assertEquals("oops", plain(rendered));
    }

    @Test
    void nullReturnsEmptyComponent() {
        assertEquals("", plain(render(null)));
    }

    @Test
    void disabledMiniMessageUsesLegacy() {
        Component rendered = MessageService.render("§cHi", MINI_MESSAGE, false);

        assertEquals("Hi", plain(rendered));
    }
}
