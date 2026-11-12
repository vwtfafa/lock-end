# Lock-End Shared API (lock-end-api)

The shared API is the platform-independent, type-safe surface introduced by
the Lock-End 4.0 multiplatform migration.

## Modules

| Type | Content |
|---|---|
| `org.vwtfafa.lockend.api.LockEndApi` | Read-only status access plus listener registration |
| `org.vwtfafa.lockend.api.LockState` | `LOCKED` / `UNLOCKED` enum representation of the runtime state |
| `org.vwtfafa.lockend.api.LockAction` | Trigger of a completed change (`LOCK`, `UNLOCK`, `UNDO`, `SCHEDULED_LOCK`, `SCHEDULED_UNLOCK`, ...) |
| `org.vwtfafa.lockend.api.LockStateChange` | Immutable description of one completed change |
| `org.vwtfafa.lockend.api.LockStateSnapshot` | Consistent point-in-time pair of state and lock reason |
| `org.vwtfafa.lockend.api.LockStateChangeListener` | Functional listener interface for completed changes |

## Hard requirements

- The module depends on nothing outside plain Java. Bukkit, Paper, Fabric,
  NeoForge, Forge and other Minecraft implementation dependencies must never
  appear here.
- All data structures are immutable and safe to share across threads.
- The module must compile and run its tests without Minecraft.

## Compatibility rules

- Enum members are **not** a closed set. Move added values in with new API
  releases; consumers must tolerate unknown values (default branches, not
  exhaustive switches).
- The action identifiers mirror the documented string actions of the legacy
  Paper `EndLockStateChangeEvent` (version 3.1) so both APIs stay aligned.
- `actorName` / `actorUuid` may be `null` (unknown actor). Never assume they
  are present.
- Listener dispatch happens in the tick context of the change. See
  [Listener dispatch context](#listener-dispatch-context) and the Javadoc of
  `LockStateChangeListener`.

## How to obtain the API

### Paper / Folia

Both the legacy 3.1 `EndLockApi` and the shared 4.x `LockEndApi` are
registered in the Bukkit `ServicesManager`:

```java
RegisteredServiceProvider<LockEndApi> registration =
        Bukkit.getServicesManager().getRegistration(LockEndApi.class);
if (registration != null) {
    LockEndApi api = registration.getProvider();
}
```

Do not cache the provider across an EndLock disable/reload; look it up again
per use. See also the legacy lookup for `org.vwtfafa.lockEnd.api.EndLockApi`.

### Fabric

```java
LockEndApi api = LockEndFabricMod.getApi();
```

Throws `IllegalStateException` if called before mod init completes or after
the server has stopped (the static is cleared on `SERVER_STOPPING` and
re-published on the next server start).

### NeoForge

```java
LockEndApi api = LockEndNeoForgeMod.getApi();
```

Same lifecycle rules as Fabric: available after mod construction, cleared on
`ServerStoppingEvent`, re-published on the next `ServerStartedEvent`.

## Listener dispatch context

| Platform | Shared `LockStateChangeListener` dispatch context |
|---|---|
| Paper 26.3 | Server main tick thread |
| Folia 26.3 | Global-region tick thread (command mutations and scheduled due actions run via `EndLockCommand` / `executeGlobal`) |
| Fabric | Dedicated/integrated server main thread |
| NeoForge | Dedicated/integrated server main thread |

Handlers must only read the payload. Schedule world, block, and entity work
through the platform's appropriate schedulers. Never assume an async or
arbitrary entity-region thread.

## Paper compatibility adapter (dual APIs)

The existing Paper API (`org.vwtfafa.lockEnd.api.EndLockApi`,
`EndLockStateChangeEvent`) is the version 3.1 read-only surface of the
plugin. It must not be removed without a documented migration path.

The adapter is wired: the Paper module depends on the shared core, the
runtime lock state lives in `LockStateManager`, and both services are
registered in the Bukkit `ServicesManager` (`EndLockApi` legacy,
shared `LockEndApi`):

```text
Shared API (lock-end-api)
    |
    └── SharedLockEndApiProvider (lock-end-core; Paper ServicesManager,
        Fabric LockEndFabricMod.getApi(), NeoForge LockEndNeoForgeMod.getApi())
            └── LockStateManager
    |
    └── Legacy EndLockApi / EndLockStateChangeEvent (3.1, Paper only)
```

### Ordering on Paper (dual surface)

On Paper, a completed lock-state transition notifies both APIs in this order:

1. `LockStateManager.updateLocked(...)` flips the shared runtime state.
2. Shared `LockStateChangeListener`s fire **immediately inside**
   `LockStateManager`, still before Paper-side side effects (history,
   persistence, broadcasts, grace period, evacuation, and related work).
3. Paper then runs those side effects.
4. The legacy Bukkit `EndLockStateChangeEvent` fires **after** those
   Paper-side side effects, with the previously documented suppression
   behavior when dispatch itself fails.

Consumers that need to observe the change before Paper side effects should
use the shared `LockEndApi` listeners. Consumers that need the post-side-effect
Paper timing should listen for `EndLockStateChangeEvent`.

Adapter contract:

- `EndLockApi.isLocked()` and the shared `isLocked()` observe the same
  runtime state (the shared `LockStateManager`).
- The plain text stripping rule (MiniMessage/section codes) is identical in
  both providers, so both APIs expose identical reason texts.
- No removal or signature change of the legacy classes; the shared API is an
  additional surface.

## Testing

Like the shared core later, this module's tests run without a Minecraft
server (`lock-end-api/src/test/java/org/vwtfafa/lockend/api/`).
