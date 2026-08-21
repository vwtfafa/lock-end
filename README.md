# EndLock

**EndLock** is a lightweight Paper plugin that lets you globally lock or unlock access to the End dimension with a single command. Ideal for progression servers, events, or worlds where the End should stay closed until you decide otherwise.

## What's new in 2.0.0

- **Lock reasons**: Customizable reasons displayed when blocking (e.g., "Maintenance", "Event in progress")
- **Grace period**: Temporary unlock after locking to allow players to exit safely
- **Whitelists**: Player/entity whitelists to bypass the lock
- **Preview notifications**: Warn players before automatic lock/unlock
- **Sound effects**: Play custom sounds when access is denied
- **Rate limiting**: Prevent log spam from rapid attempts
- **Detailed logging**: Log player, world, and method (portal/teleport)
- **Countdown timers**: Visible countdown before scheduled unlock
- **Schedule pause/resume**: Override scheduled events temporarily with `/endlock pause` and `/endlock resume`
- **Lock history**: View recent actions with `/endlock history`
- **Undo command**: Reverse the last action with `/endlock undo`
- **Config validator**: Check config for errors with `/endlock validateconfig`
- **Async logging**: File I/O moved off main thread
- **Mobile alias**: `/el` as short command alias
- **Scoped locking**: Restrict the lock to configured End worlds and optionally block End returns or End gateways
- **Schedule controls**: Cancel scheduled unlocks and change the lock reason with `/endlock cancel` and `/endlock reason <reason>`
- **Restart-safe scheduling**: Scheduled unlocks keep their absolute target time after reloads and restarts
- **Scheduled locking**: Lock the End later with `/endlock lockin <minutes>` or `/endlock lockat <yyyy-MM-dd> <HH:mm>`
- **Persistent history**: Recent history is stored in `plugins/EndLock/history.yml`
- **Lifecycle safety**: Scheduled tasks, preview notifications, grace periods, integrations, and logging are cleaned up on reload and shutdown

## Requirements

| Requirement | Version |
|-------------|---------|
| Server | [Paper](https://papermc.io/) **26.2** or newer |
| Minecraft | **26.2** |
| Java | **25** |

> Spigot, Purpur, and older Minecraft versions are **not** supported. The plugin uses `api-version: 26.2`.

## Installation

1. Download the latest `lock-end-2.0.0-SNAPSHOT.jar` from [Releases](https://github.com/vwtfafa/lock-end/releases) or Modrinth.
2. Place the file in your server's `plugins/` folder.
3. Start or restart the server.
4. Edit `plugins/EndLock/config.yml` if needed (language, initial lock state, update checker, metrics).

## Commands

All subcommands are available under the three base commands: `/endlock`, `/lock`, and `/el` (mobile‑friendly alias).  
For example: `/endlock history`, `/lock history`, `/el history` all work the same.

| Subcommand | Description | Permission |
|------------|-------------|----------|
| (no argument) | Toggle End access (locked ↔ open) | `endlock.toggle` |
| `lock` | Explicitly lock the End | `endlock.admin` |
| `unlock` | Explicitly unlock the End | `endlock.admin` |
| `status` | Show whether the End is locked or open | *(none)* |
| `test` | Test if portal blocking works | *(no permission, configurable)* |
| `stats` | Show lock and blocked-attempt counters | *(none)* |
| `reload` | Reload configuration without restart | `endlock.reload` |
| `history` | View recent lock/unlock history | `endlock.history` |
| `undo` | Undo the last lock/unlock action | `endlock.undo` |
| `validateconfig` | Validate configuration file | `endlock.validate` |
| `pause` | Pause scheduled unlock timers | `endlock.admin` |
| `resume` | Resume paused scheduled unlock timers | `endlock.admin` |
| `cancel` | Cancel the scheduled unlock | `endlock.admin` |
| `reason <reason>` | Set the lock reason | `endlock.admin` |
| `unlockin <days>` | Schedule an automatic unlock | `endlock.toggle` |
| `unlockat <yyyy-MM-dd> <HH:mm>` | Schedule an automatic unlock | `endlock.toggle` |
| `lockin <minutes>` | Schedule an automatic lock | `endlock.toggle` |
| `lockat <yyyy-MM-dd> <HH:mm>` | Schedule an automatic lock | `endlock.toggle` |

- **Console** can toggle without a permission node.
- **Players** need `endlock.toggle` to lock or unlock.

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
- **Tab Completion**: Full command completion support for all subcommands
- **Explicit Subcommands**: All subcommands accessible via `/endlock`, `/lock`, and `/el` (e.g., `/endlock lock`, `/endlock history`, `/el stats`)
- **Broadcast System**: Optional alerts when End is locked/unlocked (actionbar or chat)
- **Logging & History**: Automatic log file tracking lock/unlock events and access attempts
- **Test Command**: Verify portal blocking works correctly
- **Update Checker**: Automatically notifies Ops when updates are available (optional)
- **bStats Integration**: Anonymous usage statistics to help developers improve the plugin (optional)
- **Lock Reasons**: Customizable reason displayed when blocking
- **Grace Period**: Temporary unlock after locking for safe player exit
- **Whitelists**: Player/entity whitelists to bypass the lock
- **Preview Notifications**: Warn players before automatic lock/unlock
- **Sound Effects**: Play sounds when access is denied
- **Rate Limiting**: Prevent log spam from rapid attempts
- **Detailed Logging**: Log player, world, and access method
- **Countdown Timers**: Visible countdown before scheduled unlock
- **Schedule Pause/Resume**: Override scheduled events temporarily
- **Lock History**: View recent actions with `/endlock history`
- **Undo Command**: Reverse the last action with `/endlock undo`
- **Config Validator**: Check config for errors with `/endlock validateconfig`
- **Async Logging**: File I/O off main thread to prevent lag
- **Mobile Alias**: `/el` short command for console/mobile
- No dependencies (bStats is shaded), small JAR (~40 KB)

## Configuration

`plugins/EndLock/config.yml`:

```yaml
# EndLock Plugin Configuration
locked: false
language: en
lock-reason: "Maintenance"

# End access scope. An empty world list means all End worlds.
end:
  worlds: []
  block-return: false
  block-end-gateway: true

# Update Checker: Notifications for available updates
update-checker:
  enabled: true
  notify-ops: true
  notify-chat: true

# bStats Metrics
metrics:
  enabled: true

# Broadcast: Send alerts to all players when End is locked/unlocked
broadcast:
  enabled: true
  use-actionbar: true  # Send as action bar instead of chat
  notify-all: true     # Notify all players (if false, only Ops)

# Preview notifications - Warn players before automatic lock/unlock
preview-notifications:
  enabled: false
  seconds: 30  # How many seconds before lock/unlock to send preview

# Action bar customization
actionbar:
  use-alt-char: false  # Use alternate character for overflow handling
  alt-char: "|"

# Sound effects for access denial
sound-effects:
  enabled: false
  sound: "BLOCK_ANVIL_LAND"
  volume: 1.0
  pitch: 1.0

# Lock reasons
lock-reasons:
  default: "Maintenance"
  maintenance: "Maintenance in progress"
  event: "Event in progress"

# Grace period - Temporary unlock after locking to allow safe exit
grace-period:
  enabled: false
  duration: 10  # seconds

# Whitelists
whitelists:
  players: []  # Player names that can bypass the lock
  entities: []  # Entity types that can bypass the lock

# Logging & Analytics
logging:
  enabled: true
  log-file: "EndLock.log"  # Created in plugins/EndLock/logs/
  log-attempts: true       # Log attempted access to locked End
  rate-limit-seconds: 5    # Minimum seconds between logged attempts per player

# Schedule pause/resume
schedule:
  paused: false

# Test Command: Allow /endlock test for Ops to verify blocking works
test-command:
  enabled: true

# Optional statistics collection
stats:
  enabled: true
  lock-count: 0
  blocked-count: 0

# Optional join notifications for players joining while the End is locked
join-notifications:
  enabled: false
  show-remaining: true

# Optional scheduled unlock
scheduled-unlock:
  enabled: false
  action: "unlock"     # lock or unlock
  mode: "days"        # days or datetime
  days: 7
  datetime: ""
  target-datetime: "" # Persisted absolute target; maintained by EndLock
  # Countdown timer
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

Copy a language file from the JAR (e.g. `messages_en.yml`) into `plugins/EndLock/` and edit it. If the file exists on disk, it overrides the built-in version.

Message keys: `locked`, `toggle`, `status`, `permission`, `open`, `closed` — use `%status%` in `toggle` and `status`.

## Build from source

```bash
./gradlew shadowJar
```

Output: `build/libs/lock-end-2.0.0-SNAPSHOT.jar`

## Automatic releases (GitHub Actions)

On every push to **`main`**, GitHub Actions will:

1. Build the plugin with Java 25
2. Read the version from `build.gradle`
3. Create or **fully overwrite** the GitHub Release tagged **`v{version}`**
4. Replace the release text from [`.github/RELEASE_TEMPLATE.md`](.github/RELEASE_TEMPLATE.md)
5. Remove old JAR assets and upload the new `lock-end-{version}.jar`
6. Move the tag to the latest commit

**Same version, new push?** Title, description, JAR, and tag are replaced automatically — you do not need to edit anything on GitHub.

**New release version:** bump `version` in `build.gradle` (and `plugin.yml`).

**Customize release text:** edit `.github/RELEASE_TEMPLATE.md` only (placeholders: `@VERSION@`, `@GITHUB_SHA@`, `@BUILD_DATE@`).

Run a local test server (downloads Paper 26.2):

```bash
./gradlew runServer
```

## Limitations

- Only **player** movement is blocked; entity whitelist entries do not control entity teleport events.
- Players **already in the End** when you lock it are not teleported out.
- Language and configuration changes can be applied with `/endlock reload`.
- The default lock blocks travel into the End; returning from the End is allowed unless `end.block-return` is enabled.

## License

This project is licensed under the **All Rights Reserved** license. See the `LICENSE` file for details.

## Author

**vwtfafa**
