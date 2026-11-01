package org.vwtfafa.lockEnd;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Pure helper that reduces configured lock reasons to plain text for the
 * 3.1 API ({@code EndLockApi#getLockReason()}): supported MiniMessage tags
 * and valid legacy section codes are removed, Unicode and non-markup
 * angle-bracket text are preserved, and surrounding whitespace is trimmed.
 *
 * <p>Deliberately separate from {@link MessageService}: the message renderer
 * turns section codes into formatting and renders components, while the API
 * contract requires stripping (doc removal, not a render form). This class
 * never touches behavior of existing message paths.</p>
 */
final class LockReasonSanitizer {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Pattern SECTION_CODES = Pattern.compile("(?i)§[0-9A-FK-ORX]");

    private LockReasonSanitizer() {
    }

    /**
     * Strips MiniMessage tags and section codes from a raw reason.
     *
     * @param raw the raw configured reason; may be {@code null}
     * @return the plain text without markup, trimmed; never {@code null}
     */
    static String sanitize(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        String withoutTags = MINI_MESSAGE.stripTags(raw);
        String withoutSectionCodes = SECTION_CODES.matcher(withoutTags).replaceAll("");
        return withoutSectionCodes.trim();
    }
}
