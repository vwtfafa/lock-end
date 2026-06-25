# Changelog

## [4.0] - 2026-06-25

### Added
- **Scheduled unlock** — `/endlock unlockin 7d` and `/endlock unlockat 2026-07-01 18:00`
- **Join notification** when the End is locked (with remaining time if scheduled)
- **Statistics** — lock, unlock, and blocked counters (`/endlock stats`, persisted in `data.yml`)
- **Extended logging** — world, teleport cause; JSON lines log (`EndLock.jsonl`)
- **JSON export** — `/endlock export` for admins
- **PlaceholderAPI** — `%lockend_status%`, `%lockend_remaining%`, `%lockend_lock_count%`, `%lockend_blocked_count%`
- **LuckPerms context** — `lockend_locked=true/false`
- **DiscordSRV hook** — optional channel notifications on lock/unlock
- **MiniMessage** support in language files
- **Clickable update link** in chat for admins (respects `notify-ops` config)
- **GitHub Pages** docs site (`docs/`)
- **Issue templates** — bug report and feature request
- **Auto changelog** in release notes (commits since last stable tag)
- **Single beta release** — `v{version}-beta` replaces previous experimental build

### Changed
- Refactored plugin into services (messages, logging, stats, schedule)
- Version bumped to **4.0**
- README and release template updated
- All 8 language files updated for new message keys

---

## [1.3] - 2026-06-16

See previous entries in git history.
