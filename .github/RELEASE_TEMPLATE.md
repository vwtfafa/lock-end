## 🔒 Lock-End @TITLE@

@WARNING@

**Requirements:** Paper @PAPER@+, Java @JAVA@

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
- **LuckPerms** — Context `lockend:locked=true|false`
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
- Integration toggles (PAPI, LuckPerms, MiniMessage)

### 🧱 Compatibility

| Platform | Version | Support |
| -------- | ------- | ------- |
| **Paper** | @PAPER@+ | ✅ Recommended |
| **Purpur** | @PAPER@+ | ✅ Works |

### 📦 Installation

1. Download `lock-end-@VERSION@.jar` below
2. Place it in your `plugins/` folder
3. Restart your server (Paper @PAPER@+, Java @JAVA@)

### 📚 Documentation

- [README](https://github.com/vwtfafa/lock-end/blob/@BRANCH@/README.md) – Full docs & examples
- [Modrinth](https://modrinth.com/plugin/lock-end) – Download on Modrinth

---

*Built from commit @GITHUB_SHA@ on @BUILD_DATE@*