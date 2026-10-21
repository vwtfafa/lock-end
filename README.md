# EndLock

**EndLock** is a lightweight Paper plugin that lets you globally lock or unlock access to the End dimension with a single command. Ideal for progression servers, events, or worlds where the End should stay closed until you decide otherwise.

## What's new in 3.0

EndLock remains focused exclusively on access to the End. Version 3.0 adds a
preview-and-confirm workflow for manual End evacuation, declares support for
Folia, and makes the bounded audit history versioned, UTC-based, and safer to
migrate and write.

## Requirements

| Requirement | Version |
|-------------|---------|
| Server | [Paper](https://papermc.io/) **26.3 Build #190 (STABLE)** or newer |
| Folia | **26.3** (alpha builds; console-verified, see the note below) |
| Minecraft | **26.3** |
| Java | **25** |

> Spigot and older Minecraft versions are **not** supported. Purpur 26.3+ is supported because it is Paper-based. The plugin uses `api-version: 26.3`.

> **Folia is supported as of 3.0.0** (`folia-supported: true`). Timed work runs through the global/async schedulers and player-specific work through entity schedulers. Compatibility was verified with a console smoke test on Folia 26.3 alpha build 3: the server boots, the plugin loads, and command paths such as `/endlock status`, `/endlock lock`, and `/endlock stats` respond without errors. PaperMC still marks the Folia 26.3 builds as ALPHA; player-driven paths were not exercised with a live player.

## Installation

1. Download the latest `lock-end-3.0.0.jar` from [Releases](https://github.com/vwtfafa/lock-end/releases) or Modrinth.
2. Place the file in your server's `plugins/` folder.
3. Start or restart the server.
4. Edit `plugins/EndLock/config.yml` if needed (language, initial lock state, update checker). bStats can be disabled globally via `plugins/bStats/config.json`.

## Commands

All subcommands are available under the three base commands: `/endlock`, `/lock`, and `/el` (mobile‑friendly alias).  
For example: `/endlock history`, `/lock history`, `/el history` all work the same.

| Subcommand | Description | Permission |
|------------|-------------|----------|
| (no argument) | Toggle End access (locked ↔ open) | `endlock.toggle` |
| `lock` | Explicitly lock the End | `endlock.admin` |
| `unlock` | Explicitly unlock the End | `endlock.admin` |
| `status` | Show whether the End is locked or open | *(none)* |
| `test` | Test if portal blocking works | `endlock.admin` |
| `stats` | Show lock and blocked-attempt counters | *(none)* |
| `reload` | Reload configuration without restart | `endlock.reload` |
| `history` | View recent lock/unlock history | `endlock.history` |
| `history status` | Check history storage status and in-memory entry count | `endlock.history` |
| `undo` | Undo the last lock/unlock action | `endlock.undo` |
| `validateconfig` | Validate configuration file | `endlock.validate` |
| `pause` | Pause scheduled unlock timers | `endlock.admin` |
| `resume` | Resume paused scheduled unlock timers | `endlock.admin` |
| `cancel` | Cancel the scheduled unlock | `endlock.admin` |
| `reason <reason>` | Set the lock reason | `endlock.admin` |
| `unlockin <days>` | Schedule an automatic unlock | `endlock.toggle` |
| `unlockin <duration>` | Schedule an automatic unlock, e.g. `12h` or `7d` | `endlock.toggle` |
| `unlockat <yyyy-MM-dd> <HH:mm>` | Schedule an automatic unlock | `endlock.toggle` |
| `lockin <minutes>` | Schedule an automatic lock | `endlock.toggle` |
| `lockin <duration>` | Schedule an automatic lock, e.g. `30m` or `2h` | `endlock.toggle` |
| `lockat <yyyy-MM-dd> <HH:mm>` | Schedule an automatic lock | `endlock.toggle` |
| `lock in <duration>` / `unlock in <duration>` | Natural alias for `lockin`/`unlockin`, e.g. `lock in 5m` | `endlock.toggle` |
| `schedule status` | Show the active schedule | `endlock.admin` |
| `schedule clear` | Clear the active schedule | `endlock.admin` |
| `history <page>` | Show a history page | `endlock.history` |
| `history <page> [json|csv] [player|action <value>]` | Filter and/or export history | `endlock.history` |
| `evacuate preview` | Preview eligible End players and destination | `endlock.admin` |
| `evacuate execute` | Execute the unchanged preview within 30 seconds | `endlock.admin` |

- **Console** can toggle without a permission node.
- **Players** need `endlock.toggle` to lock or unlock.
- Manual evacuation requires the End to be locked and `evacuation.enabled: true`.
  Run a fresh preview before every execution. The preview is requester-bound,
  expires after 30 seconds, and must be repeated if the eligible players or
  destination changes. Automatic evacuation on lock remains a separate option.

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `endlock.toggle` | `op` | Lock or unlock the End via command |
| `endlock.admin` | `op` | Lock/unlock the End, pause/resume schedules, and use test command |
| `endlock.reload` | `op` | Reload the plugin configuration |
| `endlock.history` | `op` | View persistent lock history |
| `endlock.undo` | `op` | Undo the last recorded state change |
| `endlock.validate` | `op` | Validate the plugin configuration |
| `endlock.whitelist.bypass` | `op` | Bypass the End lock |

### LuckPerms Setup

If you use **LuckPerms**, add permissions like this:

```yaml
# Give a group the toggle permission
/luckperms group <groupname> permission set endlock.toggle true

# Give a group admin notifications
/luckperms group <groupname> permission set endlock.admin true
```

Or edit your `luckperms/groups/default.conf`:

```yaml
permissions:
  endlock.toggle: true
  endlock.admin: true
```
## Features

- Blocks player travel into the End (portals, `/tp`, `/execute`, and most plugin teleports)
- Lock state persists in `config.yml` across restarts
- Eight built-in languages (configurable)
- **Tab Completion**: Native Brigadier command completion for all subcommands
- **Explicit Subcommands**: All subcommands accessible via `/endlock`, `/lock`, and `/el` (e.g., `/endlock lock`, `/endlock history`, `/el stats`)
- **Broadcast System**: Optional alerts when End is locked/unlocked (actionbar or chat)
- **Logging & History**: Automatic log file tracking lock/unlock events and access attempts
- **Test Command**: Verify portal blocking works correctly
- **Update Checker**: Automatically notifies Ops when updates are available (optional)
- **bStats Integration**: Anonymous usage statistics to help developers improve the plugin (optional)
- **Lock Reasons**: Customizable reason displayed when blocking
- **Grace Period**: Temporary unlock after locking for safe player exit
- **Whitelists**: Player, UUID, and per-world whitelists to bypass the lock
- **Preview Notifications**: Warn players before automatic lock/unlock
- **Sound Effects**: Play sounds when access is denied
- **Rate Limiting**: Prevent log spam from rapid attempts
- **Detailed Logging**: Log player, world, and access method
- **Countdown Timers**: Visible countdown before scheduled unlock
- **Schedule Pause/Resume**: Override scheduled events temporarily
- **Lock History**: View recent actions with `/endlock history`
- **History Filter**: Filter history by player or action with `/endlock history [page] [json|csv] [player|action <value>]`
- **Undo Command**: Reverse the last action with `/endlock undo`
- **Config Validator**: Check config for errors with `/endlock validateconfig`
- **Async Logging**: File I/O off main thread to prevent lag
- **Mobile Alias**: `/el` short command for console/mobile
- No dependencies (bStats is shaded), small JAR (~150 KB)

## Configuration

`plugins/EndLock/config.yml`:

```yaml
# EndLock Plugin Configuration
config-version: 2
locked: false
language: en  # Supported: de en es fr it ja ru zh

# End access scope. An empty world list means all End worlds.
end:
  worlds: []
  block-end-gateway: true
  block-entities: true  # Also block non-player entities from entering the End

# Update Checker: Notifications for available updates
update-checker:
  enabled: true
  notify-ops: true
  notify-chat: true

# Broadcast: Send alerts to all players when End is locked/unlocked
broadcast:
  enabled: true
  use-actionbar: true  # Send as action bar instead of chat
  notify-all: true     # Notify all players (if false, only Ops)

# Preview notifications - Warn players before automatic lock/unlock
preview-notifications:
  enabled: false
  seconds: 30  # How many seconds before lock/unlock to send preview

# Sound effects for access denial
sound-effects:
  enabled: false
  sound: "BLOCK_ANVIL_LAND"  # Enum style or namespaced key (minecraft:block.anvil.land)
  volume: 1.0
  pitch: 1.0

# Lock reasons - Customizable reasons shown when blocking
lock-reasons:
  default: "Maintenance"
  maintenance: "Maintenance in progress"
  event: "Event in progress"

# Grace period - Delays enforcement of a new lock so players inside the
# End can finish and leave safely. Access is blocked once it ends.
grace-period:
  enabled: false
  duration: 10  # seconds

# Whitelists - Players, UUIDs, and worlds that can bypass the lock
whitelists:
  players: []  # Player names that can bypass the lock
  uuids: []    # Player UUIDs that can bypass the lock
  worlds: []   # End world names where players can bypass the lock

# Evacuation - Optional removal of players already inside the End when locking
evacuation:
  enabled: false
  warning-seconds: 10
  target-world: "world"
  exclude-bypass: true

# Logging - Audit log for lock actions and access attempts
logging:
  enabled: true
  log-file: "EndLock.log"  # Created in plugins/EndLock/logs/
  log-attempts: true       # Log attempted access to locked End
  rate-limit-seconds: 5    # Minimum seconds between logged attempts per player

# Schedule pause/resume - Temporarily override scheduled events
schedule:
  paused: false

# Test Command: Allow /endlock test for Ops to verify blocking works
test-command:
  enabled: true

# Optional statistics collection
stats:
  enabled: true
  lock-count: 0
  unlock-count: 0
  blocked-count: 0
  evacuated-count: 0

# Lock history storage
history:
  max-entries: 1000    # Oldest entries are dropped beyond this size
  retention-days: 30   # Entries older than this are removed (<=0 keeps forever)
# history.yml stores versioned entries with UTC timestamps. Legacy local
# timestamps are migrated; malformed or newer-schema files are preserved and
# reported in the server log instead of being overwritten.

# Optional join notifications for players joining while the End is locked
join-notifications:
  enabled: false

# Optional scheduled unlock
scheduled-unlock:
  enabled: false
  action: "unlock"     # lock or unlock
  mode: "days"         # days or datetime
  days: 7
  datetime: ""
  target-datetime: ""  # Internal absolute target, persisted across restarts
  # Countdown timer - Visible countdown before scheduled lock/unlock
  countdown:
    enabled: true
    interval: 10      # Seconds between countdown updates
    start-before: 300 # Show countdown N seconds before unlock

# Optional integrations and formatting
hooks:
  mini-message: true
  placeholderapi: true
```

### Custom messages

Copy a language file from the JAR (e.g. `messages_en.yml`) into `plugins/EndLock/lang/` and edit it. If the file exists on disk, it overrides the built-in version.

Message keys: `locked`, `toggle`, `status`, `permission`, `open`, `closed` — use `%status%` in `toggle` and `status`. `remaining-unlocked` and `remaining-permanent` provide the plain-text `%lockend_remaining%` PlaceholderAPI value.

### Audit history

`plugins/EndLock/history.yml` keeps a bounded, versioned history. Existing
2.x local timestamps are migrated using the server's current time zone; new
entries are stored and displayed in UTC. Writes use a temporary file and
atomic replacement where supported. If the file is malformed, contains an
invalid entry, or uses a newer schema, EndLock logs the problem and will not
overwrite that file. Manually confirmed evacuations are recorded as
per-player `EVACUATE` results with the actor UUID when available, target player
UUID, destination, and outcome (`MOVED`, `FAILED`, or a `SKIPPED_*` reason).
Lock and unlock rows record the previous and new states plus the actor UUID
when the command sender is a player. Run `/endlock history status` to check
whether persistence is `AVAILABLE`, `WRITE_FAILED`, or `READ_ONLY`; write
failures are also reported to the server log. Entries in active server memory
may still be viewed/exported after a failed write, but are not guaranteed to
survive a restart.

History is intentionally bounded by `history.max-entries` and
`history.retention-days`. It is an operational record, not an immutable or
complete long-term audit system; retain server logs separately if durable
compliance records are required.

## Build from source

```bash
./gradlew shadowJar
```

Output: `build/libs/lock-end-3.0.0.jar`

## Automatic releases (GitHub Actions)

On every push to a release-configured branch, GitHub Actions will:

1. Build the plugin with Java 25
2. Read the version from `build.gradle.kts`
3. Create or **fully overwrite** the GitHub Release tagged **`v{version}`**
4. Replace the release text from [`.github/RELEASE_TEMPLATE.md`](.github/RELEASE_TEMPLATE.md)
5. Remove old JAR assets and upload the new `lock-end-{version}.jar`
6. Move the tag to the latest commit

**Same version, new push?** Title, description, JAR, and tag are replaced automatically — you do not need to edit anything on GitHub.

**New release version:** bump `version` in `build.gradle.kts`. The build expands that value into `plugin.yml` automatically.

**Customize release text:** edit `.github/RELEASE_TEMPLATE.md` only (placeholders: `@VERSION@`, `@GITHUB_SHA@`, `@BUILD_DATE@`).

Run a local test server (downloads the latest Paper 26.3 build; currently Build #190, STABLE):

```bash
./gradlew runServer
```

Run a local Folia test server instead (pinned to Folia 26.3 alpha build 3, working directory `run/folia/`):

```bash
./gradlew runFolia
```

## Limitations

- Only **player** movement is blocked by default; non-player entities can be blocked too via `end.block-entities`.
- Players **already in the End** when you lock it stay there unless `evacuation.enabled` is set (then they are warned and teleported out).
- Language and configuration changes can be applied with `/endlock reload`.
- The default lock blocks travel into the End; returning from the End is always allowed.
- Folia is supported since 3.0.0 (`folia-supported: true`). Verification was a console smoke test on the Folia 26.3 alpha build 3; in-game player paths (region teleports, per-player messages) rely on the global/entity scheduler helpers but were not exercised live.

## License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.
Third-party components bundled into the plugin jar are listed in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

## Author

**vwtfafa**
