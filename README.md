# EndLock

**EndLock** is a lightweight Paper plugin that lets you globally lock or unlock access to the End dimension with a single command. Ideal for progression servers, events, or worlds where the End should stay closed until you decide otherwise.

## What's new in 1.4.0

- Optional join notifications for players entering while the End is locked
- Optional scheduled unlocks with `/endlock unlockin` and `/endlock unlockat`
- Basic plugin statistics via `/endlock stats`
- Better update notifications with a clickable link
- Cleaner GitHub release pages and improved release automation
- Optional bStats charts for lock state, counters and feature usage

## Requirements

| Requirement | Version |
|-------------|---------|
| Server | [Paper](https://papermc.io/) **26.2** or newer |
| Minecraft | **26.2** |
| Java | **25** |

> Spigot, Purpur, and older Minecraft versions are **not** supported. The plugin uses `api-version: 26.2`.

## Installation

1. Download the latest `lock-end-1.3.jar` from [Releases](https://github.com/vwtfafa/lock-end/releases) or Modrinth.
2. Place the file in your server's `plugins/` folder.
3. Start or restart the server.
4. Edit `plugins/EndLock/config.yml` if needed (language, initial lock state, update checker, metrics).

## Commands

| Command | Description | Permission |
|---------|-------------|----------|
| `/endlock` | Toggle End access (locked ↔ open) | `endlock.toggle` |
| `/endlock lock` | Explicitly lock the End | `endlock.toggle` |
| `/endlock unlock` | Explicitly unlock the End | `endlock.toggle` |
| `/endlock status` | Show whether the End is locked or open | *(none)* |
| `/endlock test` | Test if portal blocking works | *(Ops only, configurable)* |
| `/lock` | Alias for `/endlock` | `endlock.toggle` |

- **Console** can toggle without a permission node.
- **Players** need `endlock.toggle` to lock or unlock.

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `endlock.toggle` | `op` | Lock or unlock the End via command || `endlock.admin` | `op` | Receive broadcast notifications (if `notify-all: false`) |

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
- **Explicit Subcommands**: `lock`, `unlock`, `status`, `test`
- **Broadcast System**: Optional alerts when End is locked/unlocked (actionbar or chat)
- **Logging & History**: Automatic log file tracking lock/unlock events and access attempts
- **Test Command**: Verify portal blocking works correctly
- **Update Checker**: Automatically notifies Ops when updates are available (optional)
- **bStats Integration**: Anonymous usage statistics to help developers improve the plugin (optional)
- No dependencies (bStats is shaded), small JAR (~40 KB)

## Configuration

`plugins/EndLock/config.yml`:

```yaml
locked: false      # true = End is locked on startup
language: en       # en, de, fr, es, it, ru, zh, ja

# Update Checker: Notifies Ops when updates are available
update-checker:
  enabled: true
  notify-ops: true

# bStats: Anonymous usage statistics
metrics:
  enabled: true

# Broadcast: Send alerts when End is locked/unlocked
broadcast:
  enabled: true
  use-actionbar: true     # Send as action bar (or chat if false)
  notify-all: true        # Notify all players (if false, only Ops)

# Logging: Track lock/unlock events in a log file
logging:
  enabled: true
  log-file: "EndLock.log"    # Created in plugins/EndLock/logs/
  log-attempts: true          # Log blocked access attempts

# Test Command: Allow /endlock test for Ops
test-command:
  enabled: true
```

### Custom messages

Copy a language file from the JAR (e.g. `messages_en.yml`) into `plugins/EndLock/` and edit it. If the file exists on disk, it overrides the built-in version.

Message keys: `locked`, `toggle`, `status`, `permission`, `open`, `closed` — use `%status%` in `toggle` and `status`.

## Build from source

```bash
./gradlew shadowJar
```

Output: `build/libs/lock-end-1.3.jar`

## Automatic releases (GitHub Actions)

On every push to **`master`** or **`main`**, GitHub Actions will:

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

- Only **players** are blocked; other entities are not affected.
- Players **already in the End** when you lock it are not teleported out.
- Changing language or messages requires a server restart (no `/reload` command yet).

## License

Specify your license here (e.g. MIT) and add a `LICENSE` file before publishing.

## Author

**vwtfafa**
