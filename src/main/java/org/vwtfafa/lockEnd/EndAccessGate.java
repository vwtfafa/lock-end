package org.vwtfafa.lockEnd;

import org.bukkit.World;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Pure decision logic for access attempts into End worlds. Deliberately free
 * of event and player dependencies so the complete rule set is unit testable.
 */
public final class EndAccessGate {

    /**
     * Outcome of an access attempt against the current lock state.
     */
    public enum Verdict {
        /** The attempt is not our business or explicitly permitted. */
        ALLOWED,
        /** The attempt must be cancelled. */
        BLOCKED,
        /** The lock is announced but not yet enforced (grace period). */
        GRACE_PERIOD
    }

    /**
     * Immutable snapshot of a movement attempt targeting a world.
     */
    public record AccessRequest(World.Environment toEnvironment,
                                String toWorldName) {}

    private final boolean locked;
    private final boolean gracePeriodActive;
    private final boolean blockEndGateway;
    private final List<String> endWorlds;

    public EndAccessGate(boolean locked, boolean gracePeriodActive,
                         boolean blockEndGateway, List<String> endWorlds) {
        this.locked = locked;
        this.gracePeriodActive = gracePeriodActive;
        this.blockEndGateway = blockEndGateway;
        this.endWorlds = List.copyOf(endWorlds);
    }

    /**
     * Decides a player movement attempt. Returning players from the End are
     * always allowed; whitelisted players may enter while locked.
     */
    public Verdict checkPlayer(AccessRequest request,
                               PlayerTeleportEvent.TeleportCause cause,
                               BooleanSupplier bypassCheck) {
        if (!locked || request.toEnvironment() != World.Environment.THE_END) {
            return Verdict.ALLOWED;
        }
        if (!inScopedWorld(request.toWorldName())) {
            return Verdict.ALLOWED;
        }
        if (cause == PlayerTeleportEvent.TeleportCause.END_GATEWAY && !blockEndGateway) {
            return Verdict.ALLOWED;
        }
        // During the grace period the lock is not yet fully enforced.
        if (gracePeriodActive) {
            return Verdict.GRACE_PERIOD;
        }
        if (bypassCheck.getAsBoolean()) {
            return Verdict.ALLOWED;
        }
        return Verdict.BLOCKED;
    }

    /**
     * Decides a non-player entity attempt. Entities have no bypass options.
     * Portal events for entities do not expose their cause, so callers report
     * end gateway travel via the dedicated flag.
     */
    public Verdict checkEntity(AccessRequest request, boolean endGateway) {
        if (!locked || request.toEnvironment() != World.Environment.THE_END) {
            return Verdict.ALLOWED;
        }
        if (!inScopedWorld(request.toWorldName())) {
            return Verdict.ALLOWED;
        }
        if (endGateway && !blockEndGateway) {
            return Verdict.ALLOWED;
        }
        if (gracePeriodActive) {
            return Verdict.GRACE_PERIOD;
        }
        return Verdict.BLOCKED;
    }

    private boolean inScopedWorld(String worldName) {
        return endWorlds.isEmpty() || endWorlds.stream().anyMatch(name -> name.equalsIgnoreCase(worldName));
    }
}
