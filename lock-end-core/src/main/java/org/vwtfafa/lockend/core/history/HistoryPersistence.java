package org.vwtfafa.lockend.core.history;

import java.util.List;

/**
 * Platform-owned history persistence contract. The coordinating
 * {@link HistoryBuffer} calls this with the full (already rotated) entry
 * list; implementations write atomically or not at all. Implementations keep
 * their own wire format and corruption policy.
 */
public interface HistoryPersistence {

    /**
     * Writes the complete entry list as the authoritative history.
     *
     * @param entries the full rotated entry list, oldest first; never
     *                {@code null}
     * @return {@code true} when the write completed
     */
    boolean save(List<HistoryEntry> entries);
}
