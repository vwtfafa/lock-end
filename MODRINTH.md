# Modrinth – Texte & Upload-Checkliste

---

## Kurzbeschreibung (Summary)

```
🔒 Lock or unlock the End globally — portals, /tp & plugin teleports. Tab completion, logging, broadcast, 8 languages. Paper 26.2, Java 25.
```

---

## Beschreibung (Description) – final

```markdown
# 🔒 Lock-End

**Lock-End** is a lightweight Minecraft plugin that allows server admins to globally lock or unlock access to the **End dimension** with a simple command.
Perfect for survival servers, SMPs, events, or progression-based gameplay.

---

## ✨ Features

* 🔐 Lock or unlock the End with `/endlock` (alias: `/lock`) — permission: `endlock.toggle`
* 🧭 Check the current status with `/endlock status` — **no permission required**
* 🔨 **Explicit Subcommands** (v1.5.0):
  * `/endlock lock` — Lock the End
  * `/endlock unlock` — Unlock the End
  * `/endlock status` — Show current state
  * `/endlock stats` — Show basic lock and block counters
  * `/endlock unlockin <days>` — Schedule an automatic unlock
  * `/endlock unlockat <yyyy-MM-dd> <HH:mm>` — Schedule an automatic unlock at a specific time
  * `/endlock test` — Test if portal blocking works (Ops only, configurable)
  * `/endlock reload` — Reload config, language, scheduled unlock, bStats, UpdateChecker, PlaceholderAPI
* 📋 **Tab Completion** — Full tab completion for all commands
* 🖥️ **Console** can toggle the End without any permission
* 🚫 Blocks **player** teleportation into the End:
  * End portals
  * `/tp` and `/execute`
  * Most plugin teleports
* 📢 **Broadcast System** — Actionbar + chat notifications when End is locked/unlocked
* 🔔 **Optional Join Notifications** — Players joining while the End is locked can be informed
* 📝 **Logging & History** — Track lock/unlock events with timestamps
* 📊 **bStats Integration** — Anonymous usage statistics with extra charts for lock counters and feature usage
* ⬆️ **Update Checker** — Notifies Ops when updates are available and offers a clickable link
* 🌍 Multi-language support (8 languages: EN, DE, FR, ES, IT, RU, ZH, JA)
* 📦 Lightweight (~40 KB, bStats shaded)

---

## 📜 Commands

| Command | Description | Permission |
| ------- | ----------- | ---------- |
| `/endlock` | Toggles the End lock | `endlock.toggle` |
| `/endlock lock` | Lock the End | `endlock.toggle` |
| `/endlock unlock` | Unlock the End | `endlock.toggle` |
| `/endlock status` | Show current lock status | — |
| `/endlock test` | Test if portal blocking works | `endlock.admin` (Ops, configurable) |
| `/endlock reload` | Reload configuration and dependent components | `endlock.reload` |
| `/lock` | Alias for `/endlock` | `endlock.toggle` |

---

## 🧩 Permissions

| Permission | Description | Default |
| ---------- | ----------- | ------- |
| `endlock.toggle` | Allows locking/unlocking the End | OP |
| `endlock.admin` | Allows using the test command | OP |
| `endlock.reload` | Allows reloading the plugin configuration | OP |

---

## ⚙️ Configuration

`plugins/EndLock/config.yml`

```yaml
locked: false
language: en

# Update Checker: Notifies Ops when updates are available
update-checker:
  enabled: true
  notify-ops: true
  notify-chat: true

# bStats Metrics: Helps developers understand plugin usage
metrics:
  enabled: true

# Broadcast System: Notify players when End is locked/unlocked
broadcast:
  enabled: true
  use-actionbar: true    # true = actionbar, false = chat
  notify-all: true       # true = all players, false = only Ops

# Logging & History: Track lock/unlock events
logging:
  enabled: true
  log-file: "EndLock.log"
  log-attempts: true     # Log all attempted End access

# Test Command: /endlock test (Ops only)
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
  mode: "days"        # days or datetime
  days: 7
  datetime: ""

# Optional integrations and formatting
hooks:
  mini-message: true
  placeholderapi: true
  discordsrv: false
```

### Available languages

| Code | Language |
| ---- | -------- |
| `en` | English |
| `de` | German |
| `fr` | French |
| `es` | Spanish |
| `it` | Italian |
| `ru` | Russian |
| `zh` | Chinese |
| `ja` | Japanese |

Customize messages in `plugins/EndLock/messages_xx.yml` (copy from the JAR or plugin folder after first run).

---

## 🧱 Compatibility

| Platform | Support |
| -------- | ------- |
| **Paper** 26.2+ | ✅ Recommended |
| **Purpur** 26.2+ | ✅ Paper-based, works |
| **Spigot** | ❌ Not supported |

* **Minecraft:** `26.2`
* **Java:** `25` 

---

## ⚠️ Notes

* Only **players** are blocked — not mobs or items
* Players already inside the End when locking are **not** removed
* No `/reload` command prior to v1.5.0; now available to refresh config, language, scheduled unlock, bStats, UpdateChecker, PlaceholderAPI
* The End dimension remains accessible via commands that bypass the player check (e.g., certain custom plugins); this plugin blocks the common vanilla pathways.

---

## 💡 Example usage

```text
/endlock
/lock
/endlock status
/endlock unlockin 3
/endlock unlockat 2026-12-31 23:59
/endlock reload
```

---

## 📦 Installation

1. Download the latest release from Modrinth or [GitHub Releases](https://github.com/vwtfafa/lock-end/releases)
2. Put `lock-end-1.5.0.jar` into your `plugins` folder
3. Restart your server (Paper 26.2+, Java 25)
4. Edit `plugins/EndLock/config.yml` if you want to customize behavior

---

## 🔗 Links

* [GitHub Repository](https://github.com/vwtfafa/lock-end)
* [Issues & Support](https://github.com/vwtfafa/lock-end/issues)

---
