## 🔒 Lock-End @VERSION@

**Requirements:** Paper 26.2+, Java 25

### 📋 Changes since last release

@CHANGELOG@

### 📜 Commands

| Command | Description | Permission |
| ------- | ----------- | ---------- |
| `/endlock` | Toggle the End lock | `endlock.toggle` |
| `/endlock lock` | Lock the End | `endlock.toggle` |
| `/endlock unlock` | Unlock the End | `endlock.toggle` |
| `/endlock unlockin <time>` | Schedule unlock (e.g. 7d, 12h) | `endlock.admin` |
| `/endlock unlockat <datetime>` | Schedule unlock (e.g. 2026-07-01_18:00) | `endlock.admin` |
| `/endlock status` | Show current lock status | — |
| `/endlock stats` | Show plugin statistics | `endlock.admin` |
| `/endlock test` | Test if portal blocking works | `endlock.admin` |
| `/lock` | Alias for `/endlock` | `endlock.toggle` |

### 🔌 Integrations

- **PlaceholderAPI** — `%lockend_status%`, `%lockend_remaining%`
- **DiscordSRV** — Lock/unlock events sent to Discord channel
- **LuckPerms** — Context `lockend:locked=true\|false`
- **MiniMessage** — Full RGB & gradient message support

### 🔧 Configuration

`plugins/EndLock/config.yml`:
- Lock state, language (8 languages)
- Broadcast notifications (actionbar or chat, scope)
- Event logging with JSON export
- Scheduled unlocks
- Join notifications
- Update checker with clickable chat link
- bStats metrics
- Integration toggles (PAPI, DiscordSRV, LuckPerms, MiniMessage)

### 🧱 Compatibility

| Platform | Version | Support |
| -------- | ------- | ------- |
| **Paper** | 26.2+ | ✅ Recommended |
| **Purpur** | 26.2+ | ✅ Works |

### 📦 Installation

1. Download `lock-end-@VERSION@.jar` below
2. Place it in your `plugins/` folder
3. Restart your server (Paper 26.2+, Java 25)

### 📚 Documentation

- [README](https://github.com/vwtfafa/lock-end/blob/master/README.md) – Full docs & examples
- [Modrinth](https://modrinth.com/plugin/lock-end) – Download on Modrinth

---

*Built from commit @GITHUB_SHA@ on @BUILD_DATE@*
