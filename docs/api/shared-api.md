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
- Listener dispatch happens in the tick context of the change (Paper:
  main thread; Folia: global region thread; other platforms document their
  own context). See the Javadoc of `LockStateChangeListener`.

## Paper compatibility adapter (wired in Phase 4)

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
    └── SharedEndLockApiProvider (platforms/paper) ──> LockStateManager
    |
    └── Legacy EndLockApi / EndLockStateChangeEvent (3.1, unchanged)
```

Adapter contract:

- `EndLockApi.isLocked()` and the shared `isLocked()` observe the same
  runtime state (the shared `LockStateManager`).
- A state change flips the shared state and notifies shared
  `LockStateChangeListener`s immediately in the tick context; the legacy
  Bukkit event is still fired last, after the Paper-side side effects, with
  the previously documented suppression behavior.
- The plain text stripping rule (MiniMessage/section codes) is identical in
  both providers, so both APIs expose identical reason texts.
- No removal or signature change of the legacy classes; the shared API is an
  additional surface.

## Testing

Like the shared core later, this module's tests run without a Minecraft
server (`lock-end-api/src/test/java/org/vwtfafa/lockend/api/`).
