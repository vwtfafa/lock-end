# Modrinth – Texte & Upload-Checkliste

---

## Kurzbeschreibung (Summary)

```
🔒 Lock or unlock the End globally — portals, /tp & plugin teleports. Lightweight, 8 languages, Paper 26.1+.
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
* 🖥️ **Console** can toggle the End without any permission
* 🚫 Blocks **player** teleportation into the End:
  * End portals
  * `/tp` and `/execute`
  * Most plugin teleports
* 🌍 Multi-language support (8 languages)
* 💾 Lock status persists in `config.yml` after server restarts
* 📦 Lightweight & dependency-free (~6 KB)

---

## 📜 Commands

| Command | Description | Permission |
| ------- | ----------- | ---------- |
| `/endlock` | Toggles the End lock | `endlock.toggle` |
| `/lock` | Alias for `/endlock` | `endlock.toggle` |
| `/endlock status` | Shows whether the End is locked | — |

---

## 🧩 Permissions

| Permission | Description | Default |
| ---------- | ----------- | ------- |
| `endlock.toggle` | Allows locking/unlocking the End | OP |

---

## ⚙️ Configuration

`plugins/EndLock/config.yml`

```yaml
locked: false
language: en
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
| **Paper** 26.1.2+ | ✅ Recommended |
| **Purpur** 26.1.2+ | ✅ Paper-based, works |
| **Spigot** | ❌ Not supported |
| **Plain Bukkit** | ❌ Not supported (requires Paper API) |

* **Minecraft:** `26.1.2+`
* **Java:** `25`

---

## ⚠️ Notes

* Only **players** are blocked — not mobs or items
* Players already inside the End when locking are **not** removed
* No `/reload` command — restart to change language files

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
2. Put `lock-end-1.1.0.jar` into your `plugins` folder
3. Restart your server (Paper 26.1.2+, Java 25)
4. Edit `plugins/EndLock/config.yml` if you want

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
