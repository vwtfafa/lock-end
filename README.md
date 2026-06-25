# EndLock

Paper plugin to lock or unlock the End dimension globally.

| | |
|---|---|
| **Paper** | 26.2+ |
| **Java** | 25 |
| **Download** | [Releases](https://github.com/vwtfafa/lock-end/releases) |
| **Docs site** | [GitHub Pages](https://vwtfafa.github.io/lock-end/) |

## Install

1. Download `lock-end-4.0.jar` from [Releases](https://github.com/vwtfafa/lock-end/releases)
2. Put it in `plugins/`
3. Restart the server
4. Edit `plugins/EndLock/config.yml` if needed

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/endlock` | `endlock.toggle` | Toggle lock |
| `/endlock lock` | `endlock.toggle` | Lock the End |
| `/endlock unlock` | `endlock.toggle` | Unlock the End |
| `/endlock status` | — | Show status (+ remaining time if scheduled) |
| `/endlock unlockin <time>` | `endlock.toggle` | Lock until duration elapses (e.g. `7d`, `12h`, `30m`) |
| `/endlock unlockat <date> <time>` | `endlock.toggle` | Lock until datetime (`2026-07-01 18:00`) |
| `/endlock stats` | — | Show lock / block counters |
| `/endlock export` | `endlock.admin` | Copy JSON log to export file |
| `/endlock test` | `endlock.admin` | Test portal blocking |
| `/lock` | `endlock.toggle` | Alias for `/endlock` |

Console can run all commands without permissions.

## Permissions

| Node | Default | Purpose |
|------|---------|---------|
| `endlock.toggle` | op | Lock / unlock / schedule |
| `endlock.admin` | op | Export, test, update notifications |

### LuckPerms context

When LuckPerms is installed, EndLock registers context `lockend_locked` (`true` / `false`).

Example — deny End entry permission while locked:

```
/lp group default permission set end.enter false world=world_the_end lockend_locked=true
```

## PlaceholderAPI

| Placeholder | Output |
|-------------|--------|
| `%lockend_status%` | `Locked` / `Unlocked` (localized) |
| `%lockend_remaining%` | Time until scheduled unlock |
| `%lockend_lock_count%` | Total lock actions |
| `%lockend_blocked_count%` | Blocked travel attempts |

## Features

- Blocks portals and teleports into the End
- Scheduled unlock (`unlockin`, `unlockat`)
- Join notification when End is locked
- Statistics (locks, unlocks, blocked attempts)
- Text + JSON logging with world and teleport cause
- MiniMessage support in language files
- Optional DiscordSRV notifications (set `integrations.discordsrv.channel-id`)
- Update checker with clickable download link for admins
- 8 languages, bStats metrics

## Configuration

See `plugins/EndLock/config.yml` after first run. Key sections:

- `join-notification` — message on join when locked
- `logging.json-enabled` — JSON lines log alongside text log
- `integrations` — PlaceholderAPI, LuckPerms context, DiscordSRV
- `messages.use-minimessage` — parse `<red>`, `<bold>`, etc. in message files

Copy `messages_en.yml` (or any language) from the JAR into `plugins/EndLock/` to customize.

## Build

```bash
./gradlew shadowJar
```

Local test server:

```bash
./gradlew runServer
```

## Releases

- Push to **`master`** → stable release `v{version}` with changelog since last tag
- Push to **`beta`** → single pre-release `v{version}-beta` (replaced each push)
- Stable release removes beta tags

## Limitations

- Only players are blocked; entities are not teleported out of the End
- Language changes need a restart

## Author

**vwtfafa**
