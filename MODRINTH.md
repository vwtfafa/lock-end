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
* 🔨 **Explicit Subcommands** (v1.3):
  * `/endlock lock` — Lock the End
  * `/endlock unlock` — Unlock the End
  * `/endlock test` — Test if portal blocking works (Ops only, configurable)
* 📋 **Tab Completion** — Full tab completion for all commands
* 🖥️ **Console** can toggle the End without any permission
* 🚫 Blocks **player** teleportation into the End:
  * End portals
  * `/tp` and `/execute`
  * Most plugin teleports
* 📢 **Broadcast System** (v1.3) — Actionbar + chat notifications when End is locked/unlocked
  * Customizable via config (on/off, actionbar mode, notify all players or only Ops)
* 📝 **Logging & History** (v1.3) — Track all lock/unlock events with timestamps
  * Log file: `plugins/EndLock/logs/EndLock.log`
  * Optional: Log all attempted portal/teleport access
* 🌍 Multi-language support (8 languages: EN, DE, FR, ES, IT, RU, ZH, JA)
* 💾 Lock status persists in `config.yml` after server restarts
* 📊 **bStats Integration** — Anonymous usage statistics (optional, configurable)
* ⬆️ **Update Checker** — Notifies Ops when updates are available (optional, configurable)
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
| `/lock` | Alias for `/endlock` | `endlock.toggle` |

---

## 🧩 Permissions

| Permission | Description | Default |
| ---------- | ----------- | ------- |
| `endlock.toggle` | Allows locking/unlocking the End | OP |
| `endlock.admin` | Allows using the test command | OP |

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
| **Plain Bukkit** | ❌ Not supported (requires Paper API) |

* **Minecraft:** `1.26.2+` (Minecraft 26.2.0 and higher)
* **Java:** `25` (or higher, tested with Java 25)

---

## ⚠️ Notes

* Only **players** are blocked — not mobs or items
* Players already inside the End when locking are **not** removed
* No `/reload` command — restart to change language files

### Version 1.3 Highlights

* ✅ Tab completion for all commands
* ✅ Explicit subcommands: `/endlock lock`, `/endlock unlock`, `/endlock test`
* ✅ Broadcast notifications (actionbar + chat)
* ✅ Event logging with timestamps
* ✅ Full test command for portal blocking verification
* ✅ Updated to Paper 26.2 & Java 25

---

## 💡 Example usage

```text
/endlock
/lock
/endlock status
```

---

## 📦 Installation

1. Download the latest release from Modrinth or [GitHub Releases](https://github.com/vwtfafa/lock-end/releases)
2. Put `lock-end-1.3.jar` into your `plugins` folder
3. Restart your server (Paper 26.2+, Java 25)
4. Edit `plugins/EndLock/config.yml` if you want to customize behavior

---

## 🔗 Links

* [GitHub Repository](https://github.com/vwtfafa/lock-end)
* [Issues & Support](https://github.com/vwtfafa/lock-end/issues)
```

---

## Review deines Entwurfs (Kurz)

| Punkt | Bewertung |
| ----- | --------- |
| Struktur & Lesbarkeit | Sehr gut – klar für Modrinth |
| **Bukkit ✅** | Anpassen: ohne Paper API lädt das Plugin nicht (`api-version: 26.1`) |
| **Purpur ✅** | Stimmt (Paper-Fork) |
| **Spigot ❌** | Stimmt |
| **„all teleport mechanisms“** | Etwas zu stark – nur **Spieler**-Teleports/Portale |
| **`endlock.toggle` Default OP** | War im Code nicht in `plugin.yml` → jetzt ergänzt |
| **`/endlock status` ohne Permission** | Fehlte bei dir – wichtig, jetzt drin |
| **Konsole** | Fehlte – jetzt drin |
| **Installation** | „if want“ → „if you want“; JAR-Name + Paper/Java ergänzt |
| **Limitations** | Empfohlen – jetzt unter „Notes“ |

---

## Upload-Checkliste

| Feld | Wert |
| ---- | ---- |
| Game version | `26.1.2` |
| Loader | Paper |
| Version | `1.1.0` |
| JAR | `lock-end-1.1.0.jar` |
| Icon | PNG 512×512 |

**Tags:** `management`, `utility`, `admin-tools`
