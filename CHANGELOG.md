# Changelog

All notable changes to this project will be documented in this file.

## [1.6.1] - 2026-08-18
### Fixed
- Improved AsyncLogger shutdown and processing loop
- Ensured delay is never negative in PreviewNotificationManager
- Added early return when logFile is null in logAction and logAttempt to prevent NPE

## [1.6.0] - 2026-08-17
### Added
- Dependabot configuration for automatic Gradle and GitHub Actions updates
- Code quality workflow (Checkstyle, SpotBugs, Tests) running on PR/Push
- Checkstyle and SpotBugs plugins with default configurations
- Preview notifications feature fully integrated (scheduled unlock/lock previews)
- Sound effect configuration reload on `/reload`
- AsyncLogger properly restarted on config reload to avoid resource leaks
- Updated Gradle wrapper to 9.7
- Updated Java target version to 25 (while maintaining compatibility)
- **Lock reasons**: Customizable reason displayed when blocking (e.g., "Maintenance", "Event in progress") - can be configured in `config.yml` and used in broadcast messages
- **Grace period**: Temporary unlock after locking to allow players to exit safely - configurable duration in seconds
- **Player/entity whitelists**: Whitelisted players/entities can bypass the lock - configured in `config.yml` with `/whitelist add` command
- **Preview notifications**: Warn players X seconds before automatic lock/unlock - configurable in `config.yml`
- **Sound effects**: Play custom sounds when attempting to access locked End - configurable in `config.yml`
- **Attempt rate limiting**: Prevent log spam from rapid attempts - configurable in `config.yml`
- **Detailed attempt logging**: Log player name, source world, method used (portal/command/teleport) - fully configurable in `config.yml`
- **Countdown timers**: Visible countdown before scheduled lock/unlock - visible in chat during scheduled events
- **Pause/resume schedules**: Temporarily override scheduled events with `/endlock pause` and `/endlock resume` (permission: `endlock.admin`)
- **Lock history**: View recent lock/unlock actions with `/endlock history` (permission: `endlock.history`) - shows last 10 entries with timestamps
- **Undo last action**: Quickly reverse the most recent lock/unlock command with `/endlock undo` (permission: `endlock.undo`) - reverses last recorded action
- **Configuration validator**: Check `config.yml` for errors with `/endlock validateconfig` (permission: `endlock.validate`) - validates YAML syntax and required settings
- **Cached permission lookups**: Optimize permission and configuration checks - uses PermissionCache for faster lookups
- **Asynchronous logging**: Move file I/O off main thread to prevent lag - async logger prevents server performance impact
- **Mobile-friendly alias**: `/el` as short alias for `/endlock`
### Fixed
- NullPointerException in LockReasonManager.getReasons() when lock-reasons section missing
- Case-sensitive player name comparison in WhitelistChecker (now case-insensitive)
- GracePeriodTask active flag made volatile for correct visibility between threads
- AsyncLogger thread leak on reload (previous instances now shut down)
- Missing preview notification scheduling for scheduled lock/unlock events
### Changed
- GracePeriodTask logic unchanged (still unlocks after lock; behavior confirmed as intended)
- Updated build.gradle to include gradlePluginPortal() for plugin resolution
- Updated checkstyle config to minimal rule set
- Updated SpotBugs version to 6.5.10 (compatible with Gradle 9+)
- Updated `config.yml` with new v1.6 options (preview-notifications, sound-effects, lock-reasons, grace-period, whitelists, logging.rate-limit-seconds, scheduled-unlock.countdown)
- Updated `messages_en.yml` and `messages_de.yml` with new message keys
- Updated `plugin.yml` with new commands and permissions
- Consolidated all subcommands (history, undo, validateconfig) to be accessible only via `/endlock`, `/lock`, and `/el` aliases to avoid command conflicts with other plugins

## [1.5.0] - 2026-08-14
### Added
- Reload command: `/endlock reload` (and `/lock reload`) to reload configuration without restart.
- Permission `endlock.reload` (default: op) for the reload command.
- Tab completion for the `reload` subcommand.
- Default language set to English (`en`).
- All log messages and comments translated to English for consistency.
- Helper method `miniMsg(String)` for MiniMessage usage.
- Scheduled unlock now uses exact delay until the configured time (supports both days and datetime).
### Changed
- Updated `plugin.yml` usage strings to include `reload` in the command aliases.
- Updated `messages_en.yml` and `messages_de.yml` with `reload-success` key.

## [1.4.0] - 2026-07-04
### Added
- Optional join notifications for players joining while the End is locked
- Scheduled unlock support with `/endlock unlockin <days>` and `/endlock unlockat <yyyy-MM-dd> <HH:mm>`
- `/endlock stats` for basic lock and block counters
- Clickable update notification links in chat for admins
- Better GitHub release templates and cleaner release pages
- PlaceholderAPI and MiniMessage preparation for upcoming integration support
### Changed
- Bumped plugin version to `1.4.0`
- Release workflow now builds cleaner stable and beta release pages
- Beta releases keep a single experiment tag (`v1.4.0-beta`) that is updated on every beta push
- Operator update notifications are now English, clickable, and link directly to the GitHub release page
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
### CI/CD & Automation
- **GitHub Actions Workflows** configured for automated releases:
  - `release.yml`: Stable releases on push to `main`
    - Automatically builds JAR with Gradle
    - Creates GitHub Release with tag `v1.3`
    - **Cleans up all beta tags** (`v1.3-beta.*`) when stable release is created
    - Sets as "Latest Release" on GitHub
  - `beta-release.yml`: Pre-releases on push to `beta` branch
    - Creates pre-releases with tags `v1.3-beta.1`, `v1.3-beta.2`, etc.
    - Useful for testing before stable release
    - Both workflows automatically upload the plugin JAR
### Notes
- Build the release JAR with `./gradlew shadowJar` and upload the resulting
  `build/libs/lock-end-1.3.jar` to GitHub Releases or Modrinth.
- **Backward Compatibility**: Toggle command (no arguments) still works as before.
- All new features are opt-in via `config.yml`
- **Automated Releases**: Just push to `main` or `beta` branch – GitHub Actions handles the rest!

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
- **Broadcast System**: Automatic alerts when End is locked/unlocked
  - Configurable in `config.yml` (`broadcast.enabled`, `broadcast.use-actionbar`, `broadcast.notify-all`)
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

## [1.1] - previous
- Initial public release baseline (prior changelog entries omitted).

