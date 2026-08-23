# Changelog

All notable changes to this project will be documented in this file.

## [2.0.0] - 2026-08-22
### Maintenance
- Replaced deprecated Paper metadata access with `getPluginMeta()`.
- Replaced deprecated `URL(String)` construction with URI-based URL creation.
- Fixed update checks for `-SNAPSHOT` versions.
- Removed redundant `TabCompleter` declaration and unused non-metrics fields.
- Made `/endlock reload` refresh logging, MiniMessage, PlaceholderAPI, countdown, and update-checker settings.
### Added
- `/endlock` (with aliases `/lock`, `/el`) is now registered as a native Brigadier command via Paper's lifecycle API.
- New localized message keys in all 8 languages: `usage`, `test-disabled`, `scheduled-unlock-set-days`, `scheduled-unlock-set-at`.
- Added missing `already-locked` / `already-unlocked` keys to the ES, FR, IT, JA, RU, and ZH language files.
- Scheduled locking with `/endlock lockin <minutes>` and `/endlock lockat <yyyy-MM-dd> <HH:mm>`.
- Schedule inspection and clearing with `/endlock schedule status` and `/endlock schedule clear`.
- UUID-, world-, and world-permission-based bypass rules for End access.
- Optional warning and evacuation of players already inside the End when locking.
- Added PlaceholderAPI values for status, reason, remaining seconds, target time, blocked count, and schedule action.
- Structured lock history with pagination and JSON/CSV export.
- Filter lock history by player or action: `/endlock history [page] [json|csv] [player|action <value>]`
- History exports now use timestamped filenames to prevent overwrites.
- Verified and documented that Folia is not currently supported; Paper 26.2 remains the target platform.
- Completed localized message keys for scheduling, evacuation, and audit history in all supported languages.
- English messages for EndLock functionality including notifications and commands
- Enhanced MiniMessage formatting for broadcast messages and join notification
- Centralized lock state transitions with consistent persistence, broadcasts, history, undo behavior, and scheduler cleanup
- Configurable End world scope, End return blocking, and End gateway blocking
- Countdown task for scheduled unlocks, plus `/endlock cancel` and `/endlock reason <reason>`
- Persistent bounded lock history in `plugins/EndLock/history.yml`
### Performance
- Blocked-attempt stats are kept in memory and persisted on lock/disable instead of saving the whole config on every denied access.
- End evacuation teleports players asynchronously (`teleportAsync`).
- Hot-path config values (End scope, gateway blocking, logging/stats toggles) are cached and refreshed on enable/reload.
- Remaining schedule time is computed via `ZonedDateTime`, so DST transitions no longer shift effective durations.
### Changed
- Portal and teleport denial share a single event listener (`PlayerPortalEvent` extends `PlayerTeleportEvent`).
- Main class split into focused services: `MessageService`, `ScheduleManager`, and `EvacuationService`.
- Remaining German log messages and comments translated to English.
- Update gradle wrapper to version 9.7.1 and improve startup scripts for consistency
- Update version to 2.0.0-SNAPSHOT
- Refactor: extract duplicate logging code into writeToLogFile method
- Translated bStats startup log message from German to English
- Reload and shutdown now cancel scheduled, preview, countdown, and grace-period tasks cleanly
- Preview notifications honor `preview-notifications.enabled`
- Pinned the Paper API dependency to `26.2.build.112-stable` for reproducible builds.
- Synchronized command, permission, configuration, and language documentation.
- Sound effects use the Adventure sound API (`Key` based): configured names are validated, enum style constants like `BLOCK_ANVIL_LAND` map to `minecraft:block.anvil.land`, and invalid values warn once instead of failing silently.
- bStats charts report the live lock state; `MetricsManager` works on `LockEnd` directly and uses the `PLUGIN_ID` constant.
- The grace period now starts centrally in the lock state transition, so scheduled locks behave exactly like manual ones.
- Access attempts during an active grace period are no longer counted or logged as blocked attempts.
- New localized message key `grace-period-active` added in all 8 languages.
- README, MODRINTH.md and config.yml document only existing options again.
- Tab completion only offers subcommands the sender may execute; status and stats remain public.

### Added
- JUnit 5 unit tests for update version comparison, schedule time parsing and duration formatting, whitelist bypass resolution (names, UUIDs, worlds, permissions), and history filters.
- Language files live in a `lang` folder: bundled files ship under `lang/`, custom files belong in `plugins/EndLock/lang/`; legacy files sitting in the plugin root are migrated there automatically on load (nothing is deleted; if the move fails the legacy location keeps working).
- Bundled English messages act as defaults for missing keys, so partial or outdated custom language files no longer blank out messages.

### Removed
- Unused `PermissionCache` class.
- Redundant try/catch around Adventure's `sendActionBar`.
- `commands` section from `plugin.yml` (commands register programmatically via Brigadier).
- Bundled `adventure-api` dependency; Adventure is provided by `paper-api`.
- Dead config options: `end.block-return` (its guard could never take effect), `actionbar.use-alt-char` / `actionbar.alt-char`, and `join-notifications.show-remaining`.
- Unused entity whitelist (`whitelists.entities`) including `WhitelistChecker#canBypass(Entity)`.
- The `metrics.enabled` option: bStats opt-out is handled globally via the bStats plugin config (`plugins/bStats/config.json`) instead of a duplicate switch in EndLock's config.
- The misleading literal `endlock.bypass.world.*` entry from plugin.yml; per-world bypass permissions are granted dynamically as `endlock.bypass.world.<worldname>`.

### Fixed
- Update checker: `update-checker.notify-ops` (console) and `update-checker.notify-chat` (in-game) are now independent channels; disabling chat no longer suppresses all notifications.
- Default lock reason is now resolved from `lock-reasons.default` with fallback to the top-level `lock-reason` key (previously read a non-existent key).
- Bundled language files are loaded as UTF-8, fixing mojibake for JA/ZH/RU on platforms with a non-UTF-8 default charset.
- Preview notifications are parsed through the MiniMessage/legacy pipeline instead of being sent as raw strings.
- Grace period restarts cleanly when re-locking during an active grace period and is cancelled on manual unlock.
- Unknown subcommands now show usage instead of silently toggling the lock state.
- Rate-limit map entries are cleared on player quit, preventing unbounded memory growth.
- Update release configuration for v2.0 branch
- Undo now restores the previous state through the normal state-transition path
- Update checking, MiniMessage fallback, and configured log paths are more robust
- Portal and teleport denial logic is shared and runs at high priority for consistent handling
- Configuration validation now checks schedule modes and dates, numeric ranges, supported languages, and required message keys.
- Scheduled unlocks persist an absolute target time and no longer reset after restarts or reloads.
- Long unlock and preview schedules use wall-clock rechecks instead of one large tick delay.
- Command permissions are enforced per subcommand, keeping public status and test commands accessible through all aliases.
- Grace period no longer unlocks the End permanently when it runs out: the lock stays in place and only its enforcement is delayed (attempts are allowed with a localized hint while active).
- `/endlock pause` cancels both lock and unlock preview notifications; the lock preview previously kept firing while the schedule was paused.
- Executed scheduled actions stop their countdown task and pending previews instead of leaving an idle repeating timer behind.
- `/endlock test` enforces `endlock.admin` as documented in plugin.yml instead of being callable by every player.
- `/endlock unlockat` rejects datetimes in the past, matching the existing validation of `/endlock lockat`.
- Unknown language codes fall back to English instead of German.
- The `%lockend_remaining%` PlaceholderAPI value renders as `yyyy-MM-dd HH:mm` instead of a raw ISO timestamp.
- MiniMessage tags in user-provided input (lock reasons, history filter values, actor names) are escaped via `MiniMessage#escapeTags` so they render literally instead of injecting formatting.
- Pending scheduled actions survive manual toggles: a scheduled lock still executes after a manual unlock instead of silently dying until reload or restart, and pause/resume no longer depends on the current lock state.
- `/endlock unlockin` validates positive day counts like `/endlock lockin` does for minutes, and both commands catch their own invalid input instead of leaking an exception into the executor.
- bStats charts report fresh values after `/endlock reload` instead of re-reading a stale config object captured at startup.
- Custom namespaced sound keys keep their underscores (`mymod:epic_sound_blast`); only enum style constants like `BLOCK_ANVIL_LAND` are translated to dotted keys.

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

