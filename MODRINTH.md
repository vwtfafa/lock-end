# Modrinth – Texte & Upload-Checkliste

---

## Kurzbeschreibung (Summary)

```
🔒 Lock or unlock the End globally — portals, /tp & plugin teleports. Tab completion, logging, broadcast, 8 languages.
```

---

## Beschreibung (Description) – final

```markdown
# 🔒 Lock-End

**Lock-End** is a lightweight Minecraft plugin that allows server admins to globally lock or unlock access to the **End dimension** with a simple command.
Perfect for survival servers, SMPs, events, or progression-based gameplay.

---

## ✨ Features

* 🔐 Lock/unlock the End with `/endlock` (alias: `/lock`, `/el`) — `endlock.toggle`
* 📋 Full tab completion for all commands
* 🖥️ Console can toggle without permission
* 🚫 Blocks player teleportation to End (portals, `/tp`, `/execute`, plugin teleports)
* 📢 Broadcast system (actionbar or chat) on lock/unlock
* 🕒 Restart-safe scheduled unlocks with absolute target times
* 🔒 Schedule a future lock with `/endlock lockin <minutes>` or `/endlock lockat <yyyy-MM-dd> <HH:mm>`
* 📅 Inspect and clear schedules with `/endlock schedule status` and `/endlock schedule clear`
* 🔔 Optional join notifications for locked state
* 📝 Logging with history tracking
* 📊 bStats integration for anonymous usage statistics
* ⬆️ Update checker with clickable links for Ops
* 🌍 8 languages: EN, DE, FR, ES, IT, RU, ZH, JA
---

## 📜 Commands

| Command | Description | Permission |
| ------- | ----------- | ---------- |
| `/endlock` | Toggles the End lock | `endlock.toggle` |
| `/endlock lock` | Lock the End | `endlock.admin` |
| `/endlock unlock` | Unlock the End | `endlock.admin` |
| `/endlock status` | Show current lock status | — |
| `/endlock test` | Test if portal blocking works | *(no permission, configurable)* |
| `/endlock reload` | Reload configuration and dependent components | `endlock.reload` |
| `/endlock history` | View recent lock/unlock history | `endlock.history` |
| `/endlock undo` | Undo the last lock/unlock action | `endlock.undo` |
| `/endlock validateconfig` | Validate configuration file | `endlock.validate` |
| `/endlock pause` | Pause scheduled unlock timers | `endlock.admin` |
| `/endlock resume` | Resume paused scheduled unlock timers | `endlock.admin` |
| `/endlock stats` | Show basic lock and block counters | *(no permission)* |
| `/endlock unlockin <days>` | Schedule an automatic unlock | `endlock.toggle` |
| `/endlock unlockat <yyyy-MM-dd> <HH:mm>` | Schedule an automatic unlock at a specific time | `endlock.toggle` |
| `/endlock lockin <minutes>` | Schedule an automatic lock | `endlock.toggle` |
| `/endlock lockat <yyyy-MM-dd> <HH:mm>` | Schedule an automatic lock at a specific time | `endlock.toggle` |
| `/endlock schedule status` | Show the active schedule | `endlock.admin` |
| `/endlock schedule clear` | Clear the active schedule | `endlock.admin` |
| `/lock` | Alias for `/endlock` | `endlock.toggle` |
| `/el` | Mobile-friendly alias for `/endlock` | `endlock.toggle` |

The base commands do not have a global permission in `plugin.yml`; each subcommand checks its own permission. This keeps public commands such as `status`, `stats`, and `test` available while protecting administrative actions.

---

## 🧩 Permissions

| Permission | Description | Default |
| ---------- | ----------- | ------- |
| `endlock.toggle` | Allows locking/unlocking the End | OP |
| `endlock.admin` | Allows locking/unlocking the End, pausing/resuming schedules, and using the test command | OP |
---

## ⚙️ Configuration

`plugins/EndLock/config.yml`

```yaml
# EndLock Plugin Configuration
locked: false
language: en
lock-reason: "Maintenance"

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

# v1.6: Preview notifications - Warn players before automatic lock/unlock
preview-notifications:
  enabled: false
  seconds: 30  # How many seconds before lock/unlock to send preview

# v1.6: Action bar customization
actionbar:
  use-alt-char: false  # Use alternate character for overflow handling
  alt-char: "|"

# v1.6: Sound effects for access denial
sound-effects:
  enabled: false
  sound: "BLOCK_ANVIL_LAND"
  volume: 1.0
  pitch: 1.0

# v1.6: Lock reasons
lock-reasons:
  default: "Maintenance"
  maintenance: "Maintenance in progress"
  event: "Event in progress"

# v1.6: Grace period - Temporary unlock after locking to allow safe exit
grace-period:
  enabled: false
  duration: 10  # seconds

# v1.6: Whitelists
whitelists:
  players: []  # Player names that can bypass the lock
  entities: []  # Entity types that can bypass the lock

# v1.6: Logging & Analytics
logging:
  enabled: true
  log-file: "EndLock.log"  # Created in plugins/EndLock/logs/
  log-attempts: true       # Log attempted access to locked End
  rate-limit-seconds: 5    # Minimum seconds between logged attempts per player

# v1.6: Schedule pause/resume
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
  mode: "days"        # days or datetime
  days: 7
  datetime: ""
  target-datetime: "" # Persisted absolute target; maintained by EndLock
  # v1.6: Countdown timer
  countdown:
    enabled: true
    interval: 10      # Seconds between countdown updates
    start-before: 300 # Show countdown N seconds before unlock

# Optional integrations and formatting
hooks:
  mini-message: true
  placeholderapi: true
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
* The End dimension remains accessible via commands that bypass the player check (e.g., certain custom plugins); this plugin blocks the common vanilla pathways.

---

## 💡 Example usage

```text
/endlock
/lock
/el
/endlock status
/endlock unlockin 3
/endlock unlockat 2026-12-31 23:59
/endlock reload
/endlock history
/endlock undo
/endlock validateconfig
/endlock pause
/endlock resume
```

---

## 📦 Installation

1. Download the latest release from Modrinth or [GitHub Releases](https://github.com/vwtfafa/lock-end/releases)
2. Put `lock-end-1.6.0.jar` into your `plugins` folder
3. Restart your server (Paper 26.2+, Java 25)
4. Edit `plugins/EndLock/config.yml` if you want to customize behavior

---

## 🔗 Links

* [GitHub Repository](https://github.com/vwtfafa/lock-end)
* [Issues & Support](https://github.com/vwtfafa/lock-end/issues)
```