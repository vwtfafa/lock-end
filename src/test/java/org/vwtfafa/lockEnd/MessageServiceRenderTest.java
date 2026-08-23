package org.vwtfafa.lockEnd;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the literal placeholder rendering used by cached message templates.
 */
class MessageServiceRenderTest {

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void placeholdersAreReplacedWithLiteralValues() {
        Component template = Component.text("The End is locked! Reason: %reason%");

        Component rendered = MessageService.applyPlaceholders(template, Map.of("%reason%", "Maintenance"));

        assertEquals("The End is locked! Reason: Maintenance", plain(rendered));
    }

    @Test
    void valuesAreNeverReparsedAsFormatting() {
        Component template = Component.text("Reason: %reason%");

        // MiniMessage tags in user input must render literally, not as formatting.
        Component rendered = MessageService.applyPlaceholders(template,
                Map.of("%reason%", "<red>evil</red>"));

        assertEquals("Reason: <red>evil</red>", plain(rendered));
    }

    @Test
    void multiplePlaceholdersAreAllReplaced() {
        Component template = Component.text("%a% then %b% then %a% again");

        Component rendered = MessageService.applyPlaceholders(template,
                Map.of("%a%", "1", "%b%", "<gold>2"));

        assertEquals("1 then 2 then 1 again", plain(rendered));
    }

    @Test
    void unknownTokensStayVisible() {
        Component rendered = MessageService.applyPlaceholders(
                Component.text("x %missing% y"), Map.of());

        assertEquals("x %missing% y", plain(rendered));
    }
}
