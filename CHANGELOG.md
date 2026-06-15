# Changelog

All notable changes to this project will be documented in this file.

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
