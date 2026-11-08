/**
 * Public, read-only EndLock API (version 3.1), distributed with the plugin
 * release jar and consumed as a {@code compileOnly} dependency.
 *
 * <p><strong>Surface:</strong> exactly two public types, {@link EndLockApi}
 * (status service) and {@link EndLockStateChangeEvent} (non-cancellable,
 * synchronous notification of completed lock state changes). The internal
 * provider implementation lives in the main plugin package and carries no API
 * or compatibility promise. The API cannot change the lock state.</p>
 *
 * <p><strong>Consumption:</strong> query the provider through the Bukkit
 * ServicesManager ({@code softdepend: [EndLock]} in the consumer's
 * plugin.yml) and never cache it: the service is automatically removed when
 * EndLock disables and re-registered when it enables, so lookups must repeat
 * per use and event listeners must be re-registered after an EndLock reload.
 * A missing service (EndLock absent or disabled) is a normal state.</p>
 *
 * <p><strong>Folia rules:</strong> the event dispatches synchronously on the
 * tick context of the triggering action (Paper main thread; Folia entity
 * region for player commands, global region for console and scheduled due
 * actions - never an async thread). Handlers must only read the payload; any
 * world or player mutation must run through the Region/Entity/Global
 * schedulers. See docs/3.1-api-contract.md (sections 5 and 7) for the
 * authoritative contract.</p>
 */
@NullMarked
package org.vwtfafa.lockEnd.api;

import org.jspecify.annotations.NullMarked;
