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
- [x] Protect non-player entities from entering the locked End while
  `blockEntities` is enabled (arrival bounce on Fabric, pre-travel veto on
  NeoForge); entities never bypass.
- [x] Guard configurable extra End dimensions via `endWorlds` in
  `config/lock-end.json` (always including `minecraft:the_end`).
- [x] Evacuate players already in the End when locking and recover stranded
  players after startup with the lock active.
- [x] Support player-name, UUID, destination-dimension, and permission bypasses
  on Fabric and NeoForge.
- [x] Schedule lock/unlock actions with persistence, countdown, preview, and
  `schedule`/`cancel`/`pause`/`resume` controls (see checklist section 2;
  live scheduling on a running server is still pending a smoke test).
- [x] Align permission granularity with Paper's per-command permissions.
- [x] Align evacuation behavior and options with Paper's configuration
  (enabled/warning/target-world/exclude-bypass sections plus
  `/endlock evacuate preview|execute` on both mods; note the deliberate mod
  default `enabled: true` so the previously documented always-evacuate guard
  behavior survives for existing installations - Paper defaults the whole
  feature off).

Fabric and NeoForge read the settings from `config/lock-end.json`:

```json
{
  "lockReason": "Maintenance",
  "endWorlds": [],
  "blockEntities": true,
  "playerWhitelist": ["ExamplePlayer"],
  "uuidWhitelist": [],
  "worldWhitelist": ["minecraft:the_end"],
  "countdown": {"enabled": true, "interval": 10, "startBefore": 300},
  "preview": {"enabled": false, "seconds": 30}
}
```

`endWorlds` adds guarded End dimensions beyond `minecraft:the_end` (full
dimension key or its path without the namespace; empty = vanilla End only)
and `blockEntities` blocks non-player entities. Whitelist world entries match
a full dimension key or its path without the namespace. Fabric also supports
the `lock-end:admin` and `lock-end:reload` command permission nodes, plus the
`lock-end:toggle` node for the scheduled opening commands, the
`lock-end:history` node for the history view and the `lock-end:undo` node for
undo, and the `lock-end:whitelist.bypass` and
`lock-end:bypass.world.<namespace>.<path>` bypass nodes, each with an
operator-level fallback. NeoForge uses Minecraft's
native admin permission for commands and permission bypasses; its vanilla
permission system does not provide arbitrary named permission nodes.

The travel guards are intentionally platform-specific: Fabric uses an arrival
bounce for players (plus an entity bounce for non-player arrivals) and a
periodic safety scan; NeoForge cancels pre-travel for players and, while
`blockEntities` is set, for non-player entities. Confirm their behavior with
live server tests as part of any guard changes - the entity bounce path
(`AFTER_ENTITY_CHANGE_LEVEL` teleport) and the entity pre-travel veto are the
latest additions and still need a live confirmation.

## Checklist to reach Paper parity

### 1. Access rules and administration

- [x] Support configured End dimensions/worlds consistently, including custom
  dimensions where the loader permits it: `endWorlds` in
  `config/lock-end.json` adds guarded dimension keys (full
  `namespace:path` or bare path) next to the always-guarded
  `minecraft:the_end`. Empty entries guard only the vanilla End, which is
  narrower than Paper's "all End-environment worlds" scope because dimension
  keys alone do not reveal an environment.
- [x] Add player-name, UUID, world, and permission-based bypass rules on Fabric
  and NeoForge (permission-node granularity remains platform-specific; see
  above).
- [x] Add a command to set/clear the lock reason at runtime: `/endlock reason
  set <reason>` persists the reason to `config/lock-end.json` and
  `/endlock reason clear` restores the built-in default ("Maintenance"), on
  both Fabric and NeoForge. The mods keep one editable reason per server;
  Paper additionally offers reason keys via its `lock-reasons` registry.
- [x] Add configuration reload and validation with clear error reporting:
  `/endlock reload` re-reads `lock-end.json` on both mods, republishes the
  settings to the live lock state and guards, and reports a corrupt file
  (backup kept as `lock-end.json.corrupt`, defaults rebuilt) in the command
  feedback. Structural validity is enforced by JSON parsing plus the
  blank/default rules of the loader.
- [x] Match Paper's command permissions instead of using one blanket operator
  gate. Fabric exposes per-subcommand nodes `lock-end:admin`
  (lock/unlock/reason) and `lock-end:reload`, plus public
  `lock-end:status`-style status, all with operator-level fallback.
  NeoForge gates the same subcommands on the vanilla admin level because its
  permission system has no arbitrary named nodes; permission plugins cannot
  override either platform's builder there.
- [x] Match the configurable non-player entity and End-gateway protections
  where the platform APIs allow them: non-player entities are blocked on
  NeoForge by cancelling the pre-travel event, and on Fabric by an arrival
  bounce via `AFTER_ENTITY_CHANGE_LEVEL` (governed by `blockEntities`,
  default `true`, matching Paper's `end.block-entities`), and entities never
  bypass. End-gateway travel stays inside one dimension, so neither loader
  exposes it to the guards and there is no `block-end-gateway` equivalent;
  leaving/inner-End travel is always allowed, mirroring the Paper
  `EndAccessGate` rules.

### 2. Scheduling

- [x] Schedule lock and unlock by duration and by date/time:
  `/endlock unlockin <days|duration>`, `/endlock unlockat <date> <time>`,
  `/endlock lockin <minutes|duration>`, and `/endlock lockat <date> <time>`
  on both Fabric and NeoForge. A bare number keeps the Paper legacy unit
  (days for `unlockin`, minutes for `lockin`); `90m`/`2h`/`7d` tokens
  schedule an exact point in time, with the strict shared format and
  future-target validation of the Paper commands.
- [x] Persist scheduled actions and resume them safely after restart: the
  schedule lives in `config/lock-end.schedule.json` (`action`, `target`,
  `paused`). On boot the shared core scheduler restores a future target,
  clears a lapsed target (never executes it after a restart) and rejects an
  unsupported action name instead of coercing it to `unlock`; the cleaned-up
  file is rewritten so the condition does not repeat.
- [x] Add schedule status, clear/cancel, pause, and resume controls:
  `/endlock schedule status|clear`, `/endlock cancel`, `/endlock pause`, and
  `/endlock resume` on both mods, mirroring the Paper status line shape and
  pausing without moving the target (resuming executes an already-lapsed
  target immediately).
- [x] Add configurable countdown and pre-action preview notifications:
  `countdown` (`enabled`/`interval`/`startBefore`, Paper defaults on) and
  `preview` (`enabled`/`seconds`, Paper default off) in `lock-end.json`,
  republished on `/endlock reload`. Countdown lines go to admins, the
  one-shot preview broadcasts to everyone; like Paper, both stay silent when
  the current state already matches the scheduled target state.
- [x] Test clock changes, invalid input, already-locked states, and restart
  recovery: the shared core scheduler is clock-injected and covered by
  plain JUnit tests (clock jumps both directions, invalid schedule input,
  already-matching target states, pause/resume including restored paused
  schedules, lapsed/invalid persisted data, countdown bucket timing and
  preview windows), plus repository round-trip and config-default tests on
  both mods.

### 3. History and operations

- [x] Persist bounded lock/unlock history with retention settings on both
  mods: `config/lock-end.history.json` (Paper export schema, schemaVersion 2)
  with `history.maxEntries`/`history.retentionDays` in `lock-end.json`
  (same defaults and "days retention only prunes" rules as Paper), rotated
  through the shared `HistoryBuffer`.
- [x] Add history status and paginated history commands: the
  Paper-style positional surface shortcuts
  (`history [page] [json|csv|player|action [value] [filterValue]]`) on both
  mods, funneled through `LockHistoryService` with the Paper page size and
  invalid-page handling.
- [x] Filter history by player/action and export JSON/CSV on both mods,
  writing export files under the config directory like the Paper plugin.
- [x] Add undo for the last supported state change: `/endlock undo` on both
  mods restores the recorded pre-change state via the shared `UndoTracker`
  and records an UNDO history/audit entry without stacking a second undo.
- [x] Add lock, unlock, blocked-attempt, and evacuation statistics:
  shared `LockStatistics` counters persisted (revision-debounced) in
  `config/lock-end.stats.json`, a public `/endlock stats` command, and an
  `evacuated-count` that the evacuation service increments per moved player.
  Blocked attempts are counted on every veto/bounce (Fabric arrival bounce,
  NeoForge pre-travel veto).
- [x] Add configurable audit logging and attempt rate limiting: shared
  `AuditLogWriter` under `config/logs/` (name sandboxing with the Paper
  fallback, Paper line formats `ACTION - Player: ...` and
  `[ts] Attempt - Player: ... - World: ... - Method: ... - Status: LOCKED`),
  `logging` config section with per-player attempt rate limiting; attempts
  report `ARRIVAL` (Fabric) / `PRE_TRAVEL` (NeoForge) as the method.

Live server smoke checks for both mods (history JSON round-trips, stats
surviving restarts with the counters loaded, `undo` re-locking/unlocking,
audit file appearing under `config/logs/`) are still open and stay listed
under section 5.

### 4. Player-facing behavior

- [x] Match broadcast settings (enabled, audience, and chat/action-bar style
  where supported) on both mods: `broadcast` config section with
  `enabled`/`useActionbar`/`notifyAll` (`sendOverlayMessage` for the action
  bar on the mods, admins only when `notifyAll` is off, mirroring the Paper
  message service).
- [x] Add configurable messages and translations; use the same message keys
  across platforms: shared `ModMessages` in `lock-end-core` (~51 keys
  including scheduled/preview/countdown, evacuation, stats, history,
  `reason-usage`, grace period) with per-mod plain-text overrides in
  `config/lock-end-messages.json` (seeded once with the built-in defaults on
  boot, reloaded by `/endlock reload`; MiniMessage is not supported there -
  plain text only).
- [x] Add optional join notifications while the End is locked: Fabric uses
  `ServerPlayConnectionEvents.JOIN`, NeoForge the verified
  `PlayerEvent.PlayerLoggedInEvent`; both send the `join-notification`
  message and re-arm the waiting evacuation pass like Paper's
  `onPlayerJoin`.
- [x] Add configurable denial sound effects where supported: `soundEffects`
  config section (enabled/sound/volume/pitch) with the shared `SoundKeys`
  normalization and one-time invalid-value fallback to `block.anvil.land`;
  Fabric plays it on the arrival bounce, NeoForge on the pre-travel veto
  path.
- [x] Add grace periods so players can leave safely after a lock: shared
  `GracePeriod` window started on lock when enabled; on Fabric the arrival
  bounce and tick scan defer to it with a `grace-period-active` notice, on
  NeoForge the pre-travel veto announces the window instead of cancelling;
  both allow leaving the End during the window, and unlocking cancels it.
- [x] Add configurable automatic evacuation, warnings, destination, and
  bypass exclusions: `evacuation` config section
  (enabled/warningSeconds/targetWorld/excludeBypass) with the Paper warning
  countdown on both mods. Note the mods default `enabled: true` (documented
  above) and, with evacuation enabled, locking schedules the eviction while
  the Fabric guard keeps a residents-at-lock snapshot; with it disabled,
  pre-lock residents stay, like on Paper.
- [x] Add a preview/confirm flow for manual evacuation: `/endlock evacuate
  preview` (per-requester plan bound for 30 seconds) and
  `/endlock evacuate execute` (drift re-check, exits `evacuation-preview-
  changed` on plan drift) on both mods, recording MOVED /
  SKIPPED_OFFLINE / SKIPPED_LEFT_END / SKIPPED_BYPASS / FAILED outcomes as
  EVACUATE history entries like Paper.

Live server smoke checks (action-bar broadcast, denial sound audible,
grace-period window actually letting an End entry through, evacuation
warnings and preview/execute on real players) are still open and stay
listed under section 5.

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
