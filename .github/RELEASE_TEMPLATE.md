## 🔒 Lock-End @TITLE@

@WARNING@

**Requirements:** Paper or Folia @PAPER@+, Java @JAVA@ — plus Fabric (Loader 0.20.1+, Fabric API) or NeoForge 26.3.0.101+ mods, or a vanilla server for the datapack

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
| `/endlock reason <text>` | Set a lock reason (broadcast with announcements) | `endlock.admin` |
| `/endlock cancel` | Cancel the pending scheduled action | `endlock.admin` |
| `/endlock evacuate preview` / `execute` | Preview or eject players currently in the End | `endlock.admin` |
| `/endlock history` | View recent lock/unlock history | `endlock.history` |
| `/endlock history <page> [player\|action <value>]` | Filter history by player or action | `endlock.history` |
| `/endlock history [json\|csv] ...` | Export history as JSON or CSV download | `endlock.history` |
| `/endlock undo` | Undo the last lock/unlock action | `endlock.undo` |
| `/endlock validateconfig` | Validate configuration file | `endlock.validate` |
| `/endlock reload` | Reload configuration without restart | `endlock.reload` |
| `/endlock pause` | Pause scheduled timers | `endlock.admin` |
| `/endlock resume` | Resume paused scheduled timers | `endlock.admin` |
| `/lock` | Alias for `/endlock` | `endlock.toggle` |
| `/el` | Short alias for `/endlock` (mobile-friendly) | `endlock.toggle` |

On the Fabric and NeoForge mods the `/endlock` command tree provides the same
full surface (lock/unlock with schedules, reason, schedule, evacuate, cancel,
pause/resume, history with export, undo, status/stats, reload); the vanilla
datapack offers the limited in-game functions `/function
lockend:lock|unlock|status` plus `/function lockend:set_spawn` and
`/function lockend:evacuate`.

### 🔌 Integrations

- **PlaceholderAPI** — `%lockend_status%`, `%lockend_remaining%`, `%lockend_remaining_seconds%`, `%lockend_unlock_at%`, `%lockend_target_time%`, `%lockend_lock_reason%`, `%lockend_reason%`, `%lockend_blocked_count%`, `%lockend_unlock_count%`, `%lockend_unlocks%`, `%lockend_evacuated%`, `%lockend_schedule_action%`, `%lockend_schedule_active%`
- **MiniMessage** — Full RGB & gradient message support

### 🔧 Configuration

`plugins/EndLock/config.yml` (Paper/Folia):

- Lock state, language (8 languages)
- Broadcast notifications (actionbar or chat, scope)
- Event logging with JSON export
- Scheduled unlocks
- Join notifications
- Update checker with clickable chat link
- Metrics (bStats + FastStats)
- Integration toggles (PAPI, MiniMessage)

The Fabric and NeoForge mods ship their own lightweight configuration in
`config/lock-end.json` (lock state persists in `config/lock-end.state.json`);
the datapack is configured purely in-game (see the datapack README).

### 🧱 Compatibility

| Platform | Version | Support |
| -------- | ------- | ------- |
| **Paper** | @PAPER@+ | ✅ Recommended production baseline |
| **Purpur** | @PAPER@+ | ✅ Works |
| **Folia** | @PAPER@+ | ✅ Supported (console-verified on alpha builds) |
| **Fabric** | Loader 0.20.1+, Minecraft 26.3, Fabric API 0.165.2+ | ✅ Supported (in-game verified) |
| **NeoForge** | 26.3.0.101+ | ✅ Supported |
| **Vanilla datapack** | Minecraft 26.3 | ⚠️ Limited subset |

### 📦 Installation

1. Download the artifact for your platform below:
   - **Paper / Folia / Purpur:** `lock-end-@VERSION@.jar` → `plugins/` folder
   - **Fabric:** `lock-end-fabric-@VERSION@.jar` → `mods/` folder (with Fabric API)
   - **NeoForge:** `lock-end-neoforge-@VERSION@.jar` → `mods/` folder
   - **Vanilla datapack:** `lock-end-datapack-@VERSION@.zip` → `<world>/datapacks/` (limited subset — run `/function lockend:set_spawn` once as an operator)
2. Restart the server (Java @JAVA@).
3. Edit `plugins/EndLock/config.yml` (Paper/Folia) or `config/lock-end.json` (Fabric/NeoForge) if needed.

### 📚 Documentation

- [README](https://github.com/vwtfafa/lock-end/blob/@BRANCH@/README.md) – Full docs & examples
- [Modrinth](https://modrinth.com/plugin/lock-end) – Download on Modrinth

---

*Built from commit @GITHUB_SHA@ on @BUILD_DATE@*