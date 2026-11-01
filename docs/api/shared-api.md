# Lock-End Shared API (lock-end-api)

The shared API is the platform-independent, type-safe surface introduced by
the multiplatform migration (see
[feature/multiplatform-core.md](../feature/multiplatform-core.md), section 3).

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
- Listener dispatch happens in the tick context of the change (Paper:
  main thread; Folia: global region thread; other platforms document their
  own context). See the Javadoc of `LockStateChangeListener`.

## Paper compatibility adapter (prepared in this phase, wired in Phase 4)

The existing Paper API (`org.vwtfafa.lockEnd.api.EndLockApi`,
`EndLockStateChangeEvent`) is the version 3.1 read-only surface of the
plugin. It must not be removed without a documented migration path.

The migration introduces an adapter in the Paper platform module that backs
the legacy service with the same runtime state that backs the shared API:

```text
Shared API (lock-end-api)
    |
    └── Paper API adapter (platforms/paper, Phase 4)
            |
            └── Legacy EndLockApi / EndLockStateChangeEvent (3.1, unchanged)
```

Adapter goals (implemented when the Paper module starts depending on the
shared API):

- `EndLockApi.isLocked()` and the shared `isLocked()` observe the same
  volatile runtime state.
- A shared `LockStateChange` publication also fires the legacy Bukkit event
  so existing listeners continue to work.
- No removal or signature change of the legacy classes; the shared API is an
  additional surface.

## Testing

Like the shared core later, this module's tests run without a Minecraft
server (`lock-end-api/src/test/java/org/vwtfafa/lockend/api/`).
