# EndLock

**EndLock** is a lightweight Paper plugin that lets you globally lock or unlock access to the End dimension with a single command. Ideal for progression servers, events, or worlds where the End should stay closed until you decide otherwise.

## Requirements

| Requirement | Version |
|-------------|---------|
| Server | [Paper](https://papermc.io/) **26.1.2** or newer (26.1.x) |
| Minecraft | **26.1.2** |
| Java | **25** |

> Spigot, Purpur, and older Minecraft versions are **not** supported. The plugin uses `api-version: 26.1`.

## Installation

1. Download the latest `lock-end-1.1.0.jar` from [Releases](https://github.com/vwtfafa/lock-end/releases) or Modrinth.
2. Place the file in your server's `plugins/` folder.
3. Start or restart the server.
4. Edit `plugins/EndLock/config.yml` if needed (language, initial lock state).

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/endlock` | Toggle End access (locked ↔ open) | `endlock.toggle` |
| `/lock` | Alias for `/endlock` | `endlock.toggle` |
| `/endlock status` | Show whether the End is locked or open | *(none)* |

- **Console** can toggle without a permission node.
- **Players** need `endlock.toggle` to lock or unlock.

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `endlock.toggle` | `op` | Lock or unlock the End via command |

## Features

- Blocks player travel into the End (portals, `/tp`, `/execute`, and most plugin teleports)
- Lock state persists in `config.yml` across restarts
- Eight built-in languages (configurable)
- No dependencies, small JAR (~6 KB)

## Configuration

`plugins/EndLock/config.yml`:

```yaml
locked: false      # true = End is locked on startup
language: en       # en, de, fr, es, it, ru, zh, ja
```

### Custom messages

Copy a language file from the JAR (e.g. `messages_en.yml`) into `plugins/EndLock/` and edit it. If the file exists on disk, it overrides the built-in version.

Message keys: `locked`, `toggle`, `status`, `permission`, `open`, `closed` — use `%status%` in `toggle` and `status`.

## Build from source

```bash
./gradlew build
```

Output: `build/libs/lock-end-1.1.0.jar`

## Automatic releases (GitHub Actions)

On every push to **`master`** or **`main`**, GitHub Actions will:

1. Build the plugin with Java 25
2. Read the version from `build.gradle`
3. Create or update a GitHub Release tagged **`v{version}`** (e.g. `v1.1.0`)
4. Attach `lock-end-{version}.jar` to that release

**Before merging:** bump `version` in `build.gradle` (and `plugin.yml` if you set it manually) when you want a new release. Repeated pushes with the same version update the existing release instead of creating a duplicate tag.

Run a local test server (downloads Paper 26.1.2):

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
