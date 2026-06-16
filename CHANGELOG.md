# Changelog

All notable changes to this project will be documented in this file.

## [1.3] - 2026-06-16
### Added
- **Tab Completion**: Full tab completion support for `/endlock` and `/lock` commands.
  - Available options: `status`, `lock`, `unlock`
  - Works with both command aliases
- **Explicit Subcommands**: New dedicated subcommands for lock/unlock operations.
  - `/endlock lock` – Explicitly lock the End
  - `/endlock unlock` – Explicitly unlock the End
  - `/endlock status` – Check current lock status (no permission required)
  - Toggle command still available for backward compatibility

### Changed
- **Paper 26.2 Support**: Updated to Paper 26.2 (was 26.1.2)
  - `paper-api` dependency updated to `26.2.build.+`
  - `api-version` in `plugin.yml` set to `26.2`
  - Server runtime updated to `26.2`
- Command syntax in `plugin.yml` updated to reflect new subcommand options
- README requirements updated to Paper 26.2

### Notes
- Build the release JAR with `./gradlew shadowJar` and upload the resulting
  `build/libs/lock-end-1.3.jar` to GitHub Releases or Modrinth.
- **Backward Compatibility**: Toggle command (no arguments) still works as before.

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
