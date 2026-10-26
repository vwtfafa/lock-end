## 🔒 Lock-End @TITLE@

@WARNING@

**Requirements:** Paper or Folia @PAPER@+, Java @JAVA@

### 📋 Changes since @BASE_LABEL@

@CHANGELOG@

### 📜 Commands

| Command | Description | Permission |
| ------- | ----------- | ---------- |
| `/endlock` | Toggle the End lock | `endlock.toggle` |
| `/endlock lock` | Lock the End | `endlock.admin` |
| `/endlock unlock` | Unlock the End | `endlock.admin` |
| `/endlock unlockin <time>` | Schedule unlock (e.g. 7d, 12h) | `endlock.toggle` |
| `/endlock unlockat <date> <time>` | Schedule unlock (e.g. `2026-07-01 18:00`) | `endlock.toggle` |
| `/endlock lockin <minutes>` | Schedule a future lock | `endlock.toggle` |
| `/endlock lockat <date> <time>` | Schedule a future lock at specific time | `endlock.toggle` |
| `/endlock lock in <duration>` / `unlock in <duration>` | Admin schedule aliases (e.g. `5m`, `7d`) | `endlock.admin` |
| `/endlock status` | Show current lock status | — |
| `/endlock stats` | Show plugin statistics | — |
| `/endlock test` | Test if portal blocking works | `endlock.admin` |
| `/endlock schedule status` | Show active scheduled action | `endlock.admin` |
| `/endlock schedule clear` | Clear the active schedule | `endlock.admin` |
| `/endlock history` | View recent lock/unlock history | `endlock.history` |
| `/endlock history <page> [player|action <value>]` | Filter history by player or action | `endlock.history` |
| `/endlock undo` | Undo the last lock/unlock action | `endlock.undo` |
| `/endlock validateconfig` | Validate configuration file | `endlock.validate` |
| `/endlock pause` | Pause scheduled timers | `endlock.admin` |
| `/endlock resume` | Resume paused scheduled timers | `endlock.admin` |
| `/lock` | Alias for `/endlock` | `endlock.toggle` |
| `/el` | Short alias for `/endlock` (mobile-friendly) | `endlock.toggle` |

### 🔌 Integrations

- **PlaceholderAPI** — `%lockend_status%`, `%lockend_remaining%`, `%lockend_remaining_seconds%`, `%lockend_unlock_at%`, `%lockend_target_time%`, `%lockend_lock_reason%`, `%lockend_reason%`, `%lockend_blocked_count%`, `%lockend_unlock_count%`, `%lockend_evacuated%`, `%lockend_schedule_action%`, `%lockend_schedule_active%`
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
- Integration toggles (PAPI, MiniMessage)

### 🧱 Compatibility

| Platform | Version | Support |
| -------- | ------- | ------- |
| **Paper** | @PAPER@+ | ✅ Recommended |
| **Purpur** | @PAPER@+ | ✅ Works |
| **Folia** | @PAPER@+ | ✅ Supported (console-verified on alpha builds) |

### 📦 Installation

1. Download `lock-end-@VERSION@.jar` below
2. Place it in your `plugins/` folder
3. Restart your server (Paper or Folia @PAPER@+, Java @JAVA@)

### 📚 Documentation

- [README](https://github.com/vwtfafa/lock-end/blob/@BRANCH@/README.md) – Full docs & examples
- [Modrinth](https://modrinth.com/plugin/lock-end) – Download on Modrinth

---

*Built from commit @GITHUB_SHA@ on @BUILD_DATE@*