package org.vwtfafa.lockend.core.api;

import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.regex.Pattern;

/**
 * Pure helper that reduces configured lock reasons to plain text for the
 * shared {@code LockEndApi#getLockReason()} and the Paper 3.1 API: supported
 * MiniMessage tags and valid legacy section codes are removed, Unicode and
 * non-markup angle-bracket text are preserved, and surrounding whitespace is
 * trimmed.
 */
public final class LockReasonSanitizer {

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
    public static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String withoutTags = MINI_MESSAGE.stripTags(raw);
        String withoutSectionCodes = SECTION_CODES.matcher(withoutTags).replaceAll("");
        return withoutSectionCodes.trim();
    }
}
