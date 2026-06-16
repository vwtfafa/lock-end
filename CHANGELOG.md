# Changelog

All notable changes to this project will be documented in this file.

## [1.3] - 2026-06-16
### Added
- **Tab Completion**: Full tab completion support for `/endlock` and `/lock` commands.
  - Available options: `status`, `lock`, `unlock`, `test`
  - Works with both command aliases
- **Explicit Subcommands**: New dedicated subcommands for lock/unlock operations.
  - `/endlock lock` – Explicitly lock the End
  - `/endlock unlock` – Explicitly unlock the End
  - `/endlock status` – Check current lock status (no permission required)
  - `/endlock test` – Test if portal blocking works (Ops only, configurable)
  - Toggle command still available for backward compatibility
- **Broadcast System**: Automatic alerts when End is locked/unlocked
  - Configurable in `config.yml` (`broadcast.enabled`, `broadcast.notify-all`, `broadcast.use-actionbar`)
  - Can send actionbar messages instead of chat
  - Can notify all players or only Ops
- **Logging & History**: Track all lock/unlock events and access attempts
  - Log file created in `plugins/EndLock/logs/EndLock.log`
  - Records who locked/unlocked and when (timestamp format: `yyyy-MM-dd HH:mm:ss`)
  - Optional: log all attempted portal/teleport access to locked End
  - Configurable in `config.yml` (`logging.enabled`, `logging.log-file`, `logging.log-attempts`)

### Changed
- **Paper 26.2 Support**: Updated to Paper 26.2 (was 26.1.2)
  - `paper-api` dependency updated to `26.2.build.+`
  - `api-version` in `plugin.yml` set to `26.2`
  - Server runtime updated to `26.2`
- Command syntax in `plugin.yml` updated to reflect new subcommand options
- README requirements updated to Paper 26.2
- Improved command feedback (e.g., "End is already locked!" when trying to lock twice)

### Configuration
New config options in `config.yml`:
```yaml
broadcast:
  enabled: true
  use-actionbar: true
  notify-all: true

logging:
  enabled: true
  log-file: "EndLock.log"
  log-attempts: true

test-command:
  enabled: true
```

### Notes
- Build the release JAR with `./gradlew shadowJar` and upload the resulting
  `build/libs/lock-end-1.3.jar` to GitHub Releases or Modrinth.
- **Backward Compatibility**: Toggle command (no arguments) still works as before.
- All new features are opt-in via `config.yml`

---

## [1.2] - 2026-06-15
### Added
- bStats integration (shaded) with plugin id 32010 to collect anonymous metrics.
- Update checker that queries the GitHub Releases API and notifies online Ops.
- `shadowJar` configured to shade bStats into the plugin package.
- New configuration options in `config.yml` for `update-checker` and `metrics`.

### Changed
- Project version bumped to `1.2`.

### Notes
- Build the release JAR with `./gradlew shadowJar` and upload the resulting
  `build/libs/lock-end-1.2.jar` to GitHub Releases or Modrinth.

---

## [1.1] - previous
- Initial public release baseline (prior changelog entries omitted).
