package org.vwtfafa.lockend.core;

import java.util.List;
import java.util.Locale;

/**
 * Platform-independent destination scope for guarded End dimensions. Maps
 * the Paper {@code EndAccessGate} world-scope rule onto dimension keys so
 * every platform guards the same destinations:
 *
 * <ul>
 *   <li>the default {@code minecraft:the_end} dimension is always guarded,</li>
 *   <li>configured entries add further guarded dimensions: a full
 *       {@code namespace:path} key matches that key exactly, and a bare path
 *       matches that path in any namespace (the same rule as the whitelist
 *       world list, via {@link LockBypassChecker#matchesWorldKey}), and</li>
 *   <li>an empty configuration guards only the default End dimension. Note
 *       that the Paper scope instead covers <em>all</em> worlds with the End
 *       environment; platform adapters cannot derive an environment from a
 *       dimension key alone, so custom End-like dimensions are covered by
 *       adding their keys here.</li>
 * </ul>
 *
 * <p>Entry semantics mirror the Paper gate: a dimension change is an entry
 * attempt when the destination is guarded and the origin is not. Travel from
 * one guarded End dimension into another guarded one is not an entry attempt,
 * and leaving the End is always allowed.</p>
 */
public final class EndDimensionScope {

    private static final String DEFAULT_END_KEY = "minecraft:the_end";

    private final List<String> configuredEnds;

    /**
     * Creates the scope around the configured extra guarded dimensions.
     *
     * @param configuredEnds configured dimension keys; entries are matched
     *                       with the shared world-key rule
     */
    public EndDimensionScope(List<String> configuredEnds) {
        this.configuredEnds = List.copyOf(configuredEnds);
    }

    /**
     * Decides whether a dimension change is an entry attempt into the guarded
     * scope. Callers still apply the lock state and their player/entity
     * bypass rules around this decision.
     *
     * @param originKey      key of the dimension the traveler comes from; may
     *                       be {@code null}, which counts as unguarded
     * @param destinationKey key of the target dimension; may be
     *                       {@code null} (then only the destination part of
     *                       the decision matters, and {@code null} is not
     *                       guarded)
     * @return {@code true} if the traveler is attempting to enter the scope
     */
    public boolean isEntryAttempt(String originKey, String destinationKey) {
        if (!isGuarded(destinationKey)) {
            return false;
        }
        return !isGuarded(originKey);
    }

    /**
     * Decides whether a dimension or world belongs to the guarded scope.
     *
     * @param dimensionKey the dimension key to test; may be {@code null}
     * @return {@code true} if the key is the default End dimension or a
     *         configured extra End dimension
     */
    public boolean isGuarded(String dimensionKey) {
        if (dimensionKey == null) {
            return false;
        }
        if (isDefaultEnd(dimensionKey)) {
            return true;
        }
        return configuredEnds.stream().anyMatch(
                configured -> LockBypassChecker.matchesWorldKey(configured, dimensionKey));
    }

    /**
     * @return the configured extra guarded dimension keys; unmodifiable
     */
    public List<String> configuredEnds() {
        return configuredEnds;
    }

    private static boolean isDefaultEnd(String dimensionKey) {
        return dimensionKey.trim().toLowerCase(Locale.ROOT).equals(DEFAULT_END_KEY);
    }
}
