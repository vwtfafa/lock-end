# Multiplatform feature-parity roadmap

## Idea

Paper/Folia remains the reference implementation. Fabric and NeoForge should
eventually offer the same player- and administrator-visible EndLock behavior,
while using each platform's native events, commands, permissions, and storage.
The adapters do not need identical internals; shared rules and state belong in
`lock-end-core` whenever they can stay independent of Minecraft platform APIs.

The vanilla datapack is intentionally a limited edition and is not part of the
full parity target. Forge remains deferred.

## Definition of parity

A feature is complete on a platform when it:

- behaves like the Paper feature, including its important edge cases;
- has equivalent configuration and permission behavior where the platform
  supports it;
- persists correctly across server restarts when applicable;
- has focused tests, plus a server smoke check for platform event behavior; and
- is documented for that platform.

Paper-only integrations such as PlaceholderAPI, Bukkit services/events, and
bStats do not need identical mod implementations. Where an equivalent mod
ecosystem integration is useful, track it separately rather than blocking core
parity.

## Current shared mod baseline

- [x] `/endlock lock`, `/endlock unlock`, and `/endlock status` on Fabric and
  NeoForge.
- [x] Persist the lock state across restarts.
- [x] Configure a lock reason and show it in status/denial feedback.
- [x] Announce lock and unlock changes to online players.
- [x] Protect players from entering a locked End.
- [x] Evacuate players already in the End when locking and recover stranded
  players after startup with the lock active.
- [x] Support player-name, UUID, destination-dimension, and permission bypasses
  on Fabric and NeoForge.
- [ ] Align permission granularity with Paper's per-command permissions.
- [ ] Align evacuation behavior and options with Paper's configuration.

Fabric and NeoForge read the bypass lists from `config/lock-end.json`:

```json
{
  "lockReason": "Maintenance",
  "playerWhitelist": ["ExamplePlayer"],
  "uuidWhitelist": [],
  "worldWhitelist": ["minecraft:the_end"]
}
```

World entries match a full dimension key or its path without the namespace.
Fabric also supports the `lock-end:whitelist.bypass` permission and
`lock-end:bypass.world.<namespace>.<path>` per-dimension permission, both with
an operator-level fallback. NeoForge uses Minecraft's native admin permission
for permission bypasses; its vanilla permission system does not provide
arbitrary named permission nodes.

The travel guards are intentionally platform-specific: Fabric uses an arrival
bounce plus a periodic safety scan; NeoForge cancels pre-travel. Confirm their
behavior with live server tests as part of any guard changes.

## Checklist to reach Paper parity

### 1. Access rules and administration

- [ ] Support configured End dimensions/worlds consistently, including custom
  dimensions where the loader permits it.
- [x] Add player-name, UUID, world, and permission-based bypass rules on Fabric
  and NeoForge (permission-node granularity remains platform-specific; see
  above).
- [x] Add a command to set/clear the lock reason at runtime: `/endlock reason
  set <reason>` persists the reason to `config/lock-end.json` and
  `/endlock reason clear` restores the built-in default ("Maintenance"), on
  both Fabric and NeoForge. The mods keep one editable reason per server;
  Paper additionally offers reason keys via its `lock-reasons` registry.
- [ ] Add configuration reload and validation with clear error reporting.
- [ ] Match Paper's command permissions instead of using one blanket operator
  gate; document the platform-specific permission nodes/defaults.
- [ ] Match the configurable non-player entity and End-gateway protections
  where the platform APIs allow them.

### 2. Scheduling

- [ ] Schedule lock and unlock by duration and by date/time.
- [ ] Persist scheduled actions and resume them safely after restart.
- [ ] Add schedule status, clear/cancel, pause, and resume controls.
- [ ] Add configurable countdown and pre-action preview notifications.
- [ ] Test clock changes, invalid input, already-locked states, and restart
  recovery.

### 3. History and operations

- [ ] Persist bounded lock/unlock history with retention settings.
- [ ] Add history status and paginated history commands.
- [ ] Filter history by player/action and export JSON/CSV.
- [ ] Add undo for the last supported state change.
- [ ] Add lock, unlock, blocked-attempt, and evacuation statistics.
- [ ] Add configurable audit logging and attempt rate limiting.

### 4. Player-facing behavior

- [ ] Match broadcast settings (enabled, audience, and chat/action-bar style
  where supported).
- [ ] Add configurable messages and translations; use the same message keys
  across platforms where practical.
- [ ] Add optional join notifications while the End is locked.
- [ ] Add configurable denial sound effects where supported.
- [ ] Add grace periods so players can leave safely after a lock.
- [ ] Add configurable automatic evacuation, warnings, destination, and bypass
  exclusions.
- [ ] Add a preview/confirm flow for manual evacuation.

### 5. Release and parity guardrails

- [ ] Keep shared state transitions, schedule rules, and other platform-neutral
  behavior in `lock-end-core` rather than implementing them independently.
- [ ] Add shared contract tests for state changes, schedules, and persistence
  semantics.
- [ ] Add a Paper/Fabric/NeoForge command-and-behavior matrix to release checks.
- [ ] Run loader-specific smoke tests for travel blocking, permissions,
  configuration, and restart recovery.
- [ ] Update platform documentation and release notes whenever parity changes.

## Suggested order

1. Finish access-rule and permission parity first; these determine who can
   enter the End and who can change its state.
2. Implement scheduling next, including persistence and restart tests.
3. Add history, undo, and operational statistics.
4. Complete player-facing options such as grace periods, notifications, and
   evacuation previews.
5. Enforce the behavior matrix in CI and mark individual checklist items done
   only after their tests and documentation are in place.
