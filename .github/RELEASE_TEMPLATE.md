## 🔒 Lock-End @VERSION@

**Requirements:** Paper 26.2+, Java 25

### ✨ Features

* 🔐 Lock or unlock the End with explicit commands
* 📋 Full tab completion support for all commands
* 📢 Broadcast notifications (actionbar + chat)
* 📝 Event logging with timestamps
* 🧪 Portal blocking test command
* 🌍 Multi-language support (8 languages)
* 📊 bStats integration (anonymous metrics)
* ⬆️ Update checker for new versions

### 📜 Commands

| Command | Description | Permission |
| ------- | ----------- | ---------- |
| `/endlock` | Toggle the End lock | `endlock.toggle` |
| `/endlock lock` | Lock the End | `endlock.toggle` |
| `/endlock unlock` | Unlock the End | `endlock.toggle` |
| `/endlock status` | Show current lock status | — |
| `/endlock test` | Test if portal blocking works | `endlock.admin` |
| `/lock` | Alias for `/endlock` | `endlock.toggle` |

### 🔧 Configuration

The plugin creates `plugins/EndLock/config.yml` with options for:
- **Broadcast notifications** - Send alerts when End is locked/unlocked (actionbar or chat, all players or Ops only)
- **Event logging** - Track lock/unlock events with timestamps in `plugins/EndLock/logs/EndLock.log`
- **Test command** - Enable/disable the `/endlock test` command (Ops only)
- **Update checker** - Notify Ops when updates are available
- **bStats metrics** - Anonymous usage statistics (helps developers)

### 🧱 Compatibility

| Platform | Version | Support |
| -------- | ------- | ------- |
| **Paper** | 26.2+ | ✅ Recommended |
| **Purpur** | 26.2+ | ✅ Works |
| **Spigot** | — | ❌ Not supported |
| **Bukkit** | — | ❌ Not supported |

### 📦 Installation

1. Download `lock-end-@VERSION@.jar` below
2. Place it in your `plugins/` folder
3. Restart your server (Paper 26.2+, Java 25)
4. (Optional) Configure `plugins/EndLock/config.yml`

### 📚 Documentation

- [README](https://github.com/vwtfafa/lock-end/blob/master/README.md) – Full documentation & examples
- [Modrinth](https://modrinth.com/plugin/lock-end) – Download on Modrinth

### 🆕 What's New in 1.3

- ✅ **Tab Completion** - Full tab completion for all commands
- ✅ **Explicit Subcommands** - `/endlock lock`, `/endlock unlock`, `/endlock test`
- ✅ **Broadcast System** - Actionbar + chat notifications when End is locked/unlocked
- ✅ **Event Logging** - Track lock/unlock events with timestamps
- ✅ **Portal Test Command** - Verify portal blocking works with `/endlock test`
- ✅ **Paper 26.2 Support** - Updated to latest Paper version
- ✅ **Backward Compatible** - Toggle command (no args) still works

---

*Built automatically from commit @GITHUB_SHA@ on @BUILD_DATE@*
