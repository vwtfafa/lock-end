# AGENTS.md

Working agreement for AI coding agents (OpenCode, Claude Code, Codex, ...) in this
repository. It records how we work on EndLock. `build.gradle.kts`, `plugin.yml` and
CI are the source of truth for versions and commands; if this file disagrees with
them, the build files win and this file should be corrected.

Legacy note: `CLAUDE.md` and the files under `.claude/` predate this document and
are partially outdated (some still mention Paper 26.2). This file takes precedence.

---

## 1. Project at a glance

| Topic | Value |
|---|---|
| Product | EndLock - Paper/Folia plugin that locks access to the End dimension |
| Main class | `org.vwtfafa.lockEnd.LockEnd` |
| Language | Java 25 (`targetJavaVersion = 25` in `build.gradle.kts`) |
| Server API | Paper 26.3, `io.papermc.paper:paper-api:26.3.build.209-stable` |
| Minecraft | 26.3 (`api-version: '26.3'`, `runServer { minecraftVersion("26.3") }`) |
| Build | Gradle 9.8.1 wrapper, Java toolchain 25 |
| Text API | Adventure (bundled with `paper-api`), MiniMessage |
| Platforms | Paper 26.3 and Folia 26.3 (`folia-supported: true`) |
| Optional deps | PlaceholderAPI 2.12.3 (`compileOnly` + `softdepend`), bStats 3.2.1 (shaded, relocated) |
| Quality gates | JUnit 5, Checkstyle 10.12.4, SpotBugs 4.10.3 |
| Branch | `beta` is the active branch, remote `origin` = `vwtfafa/lock-end` |

The version in `build.gradle.kts` (`paperApiVersion`) is authoritative. Do not
"remember" a version from documentation or old notes; read the build file.

---

## 2. Golden rules

1. **Paper API first.** Prefer `io.papermc.paper.*` over `org.bukkit.*` whenever
   Paper provides an equivalent. Fall back to Bukkit only when there is no Paper
   alternative, and say why in the commit body or code comment if it is not obvious.
2. **Verify every API before using it.** Never guess or invent classes, methods,
   parameters, or return types. Check the official Javadoc for the exact configured
   version (see section 3). If you cannot verify an API, say so instead of writing
   speculative code.
3. **Use Adventure for all player-facing text.** `Component`, `MiniMessage`,
   `TextReplacementConfig`, `sendActionBar(Component)`. No `ChatColor`, no
   `player.sendMessage(String)`, no legacy `§` codes in bundled resources.
4. **Target only what the build targets:** Paper/Minecraft 26.3 and Java 25. Do not
   use newer or older APIs "because they are nicer", and do not upgrade Paper,
   Gradle, Java, or plugins without being asked.
5. **Paper + Folia compatibility is a feature.** Use `PaperScheduler` (or Paper's
   region/entity/async schedulers) instead of `Bukkit.getScheduler()`.
6. **No NMS, CraftBukkit internals, or reflection** unless explicitly required and
   approved. If it is unavoidable, explain why first.
7. **Smallest reasonable change.** Read the existing code, reuse its utilities and
   patterns, and do not refactor unrelated code in the same change.
8. **Build before commit.** A change is not done until `gradlew build` passes.
9. **Commit after every change.** One logical change = one commit, created
   immediately after the change is verified. Do not batch unrelated work.
10. **Never push, tag, or publish a release unless explicitly asked.** Committing
    locally is the default; the user decides when `origin` is updated.

---

## 3. Verifying Paper and Adventure APIs

### Official sources

- Paper Javadoc for the configured version: <https://jd.papermc.io/paper/26.3/>
- Paper developer docs: <https://docs.papermc.io/paper/dev/api/>
- Adventure Javadoc: <https://jd.advntr.dev/api/latest/>
- PaperMC repository / builds: <https://repo.papermc.io/> and <https://papermc.io/downloads/paper>

### Verification protocol

Before writing code that uses an unfamiliar, uncertain, or version-sensitive API:

1. Open the Javadoc and locate the exact class/method.
2. Confirm the package, modifiers, parameter types, return type, and checked exceptions.
3. Read the method description, not just the signature, to confirm behavior and threading.
4. Check for `@Deprecated` and a `@since` tag newer than 26.3. Use the non-deprecated
   replacement if one exists.
5. Check whether the same capability exists in `io.papermc.paper.*` before falling
   back to `org.bukkit.*`.
6. Compile. `gradlew compileJava` is the final proof: if the symbol does not resolve,
   it does not exist in this API version.

### Offline / authoritative fallback

The resolved dependency in the Gradle cache can be inspected without the website:

```powershell
$jar = Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\io.papermc.paper\paper-api\26.3.build.209-stable" `
    -Recurse -Filter "paper-api-26.3.build.209-stable.jar" | Select-Object -First 1
javap -classpath $jar.FullName org.bukkit.entity.Player
```

For implementation details, read the matching `*-sources.jar` instead. Do not treat
old `.claude/` memory files or blog posts as API documentation.

### Common deprecations in this codebase

| Avoid | Prefer |
|---|---|
| `Bukkit.getScheduler()` | `PaperScheduler` / Paper region & entity schedulers |
| `Sound.valueOf(...)` as the only lookup | sound registry lookup, with the existing fallback |
| `player.getName()` for identity | `player.getUniqueId()`, or `player.name()` for display |
| `ChatColor` / `§` strings | Adventure `Component` / MiniMessage |
| `sendMessage(String)` | `sendMessage(Component)` |
| `getConfig().getString(...)` without defaults | explicit defaults, matching the validator |

Do not add `@SuppressWarnings("deprecation")` to hide a problem. Fix the call site or
document why the deprecated API is the only option.

---

## 4. Paper / Folia implementation rules

- **Scheduling:** use `org.vwtfafa.lockEnd.util.PaperScheduler` for global, async,
  and entity-bound work. Global region tasks for world/state changes, entity
  schedulers for player/entity work, async scheduler for IO. Never call the Bukkit
  scheduler, and never touch the Bukkit API from an async thread.
- **Commands:** registered as a native Brigadier tree through Paper's lifecycle API
  (`EndLockCommand#buildNode`). Do not add commands to `plugin.yml`.
- **Events:** use Paper events where they exist (`io.papermc.paper.event.*`). Keep
  handlers small and delegate to the existing services.
- **Config:** every new option needs a default in `config.yml`, a read path in the
  owning service, and validation in the config validator. Preserve backwards
  compatibility and migration behavior (`ConfigMigrator`).
- **Persistence:** use Bukkit's `PersistentDataContainer` or the existing file
  stores. History writes go through `HistoryStore`, not ad-hoc file IO.
- **Logging:** respect `logging.enabled` and route through the existing async logger.
- **Placeholders:** extend `LockEndExpansion`, keep `%lockend_*%` names stable.
- **Metrics:** bStats stays shaded and relocated; do not add a hard dependency.

---

## 5. Text, messages, and translations

- All bundled language files live in `src/main/resources/lang/messages_<code>.yml`
  and use **MiniMessage only**. Never use `§` there.
- Supported languages: `de`, `en`, `es`, `fr`, `it`, `ja`, `ru`, `zh`.
- `en` is the reference file. Every key must exist in **all eight** files;
  `LanguageKeyParityTest` fails otherwise.
- Values that feed PlaceholderAPI (`remaining-unlocked`, `remaining-permanent`) must
  stay plain text without formatting codes.
- Custom server files may still use `§`; `MessageService` translates them. That is a
  compatibility path, not a reason to add new legacy codes.
- The `hooks.mini-message` toggle must keep working: disabling it strips tags instead
  of leaking raw markup.
- When adding a key: add it to every language file, then run
  `gradlew test --tests "org.vwtfafa.lockEnd.LanguageKeyParityTest"`.

---

## 6. Build, test, and run

Use the wrapper. On Windows use `gradlew.bat`; on Linux/CI use `./gradlew`.

```powershell
# Full gate: compile + unit tests + Checkstyle + SpotBugs + jar
.\gradlew.bat build --no-daemon

# Faster iteration
.\gradlew.bat compileJava --no-daemon
.\gradlew.bat test --no-daemon
.\gradlew.bat test --tests "org.vwtfafa.lockEnd.MessageServiceRenderTest" --no-daemon

# Static analysis only
.\gradlew.bat checkstyleMain spotbugsMain --no-daemon

# Output jar
# build/libs/lock-end-<version>.jar (shadowJar is the distributable)
```

Rules:

- `build` must pass. Do not commit with failing tests, Checkstyle, or SpotBugs.
- Checkstyle enforces: no star imports, no unused imports, no empty blocks,
  `@Override` where required.
- SpotBugs runs at maximum effort / low confidence on production code; tests are
  exempt. Fix findings instead of excluding them.
- Keep test coverage for behavior changes. Look for an existing test class first;
  add a focused test rather than a new framework.

### Local servers

```powershell
.\gradlew.bat runServer --no-daemon   # Paper 26.3, run/
.\gradlew.bat runFolia --no-daemon    # Folia 26.3 Build 3 (ALPHA), run/folia/
```

Place the built jar in the server's `plugins/` folder, start the server, and verify
at minimum:

- log shows `EndLock v...` and `enabled (Paper 26.3+)`,
- `/endlock status` and `/endlock history status` respond,
- locking and unlocking the End still blocks/permits travel,
- reload and shutdown complete without exceptions.

CI pins Paper 26.3 Build 209 and Folia 26.3 Build 3 and runs the same smoke checks.
Keep both green; Folia support is not theoretical.

---

## 7. Git workflow

### Commit after every change

1. Inspect `git status` before editing. The working tree may contain the user's
   unfinished work - never stage or commit files you did not touch.
2. Make one logical change.
3. Verify it (`gradlew build`, or the narrowest relevant test).
4. Stage explicit paths: `git add <path> [<path> ...]`. Do not use `git add -A` or
   `git add .` while unrelated changes exist.
5. Commit immediately.
6. Report the commit hash and what was verified.

If a change is not ready to commit, it is not done. Do not leave a finished change
uncommitted "for later".

### Message format (Conventional Commits, English)

```text
<type>(<scope>): <imperative summary, <= 72 chars>

Why the change is needed, behavior before/after, and how it was verified.
Wrap the body at ~72-100 chars.
```

Types: `feat`, `fix`, `docs`, `chore`, `refactor`, `perf`, `test`, `build`, `ci`,
`style`, `revert`. Use `!` for breaking changes (`feat!: ...`).

Scopes used in this repo: `gate`, `respawn`, `schedule`, `history`, `placeholders`,
`update`, `messages`, `sounds`, `reload`, `commands`, `validator`, `evacuation`,
`logging`, `release`, `build`, `i18n`.

Rules:

- Choose the type from the actual diff. `feat` only for new functionality, `fix`
  only for corrected behavior.
- Subject in imperative mood, no trailing period.
- Body explains the **why**, plus command results when relevant.
- Never amend or force-push a commit that already exists on `origin`.
- Never commit `build/`, `run/`, `run/folia/`, `.gradle/`, `.claude/`, logs, jars, or
  secrets.
- Do not push unless the user asks.

Examples from the history:

```text
fix(respawn): redirect blocked respawns out of the End entirely
fix(schedule): preserve disabled schedule settings on a bad action
perf(history): batch disk writes
ci: retire the stale main and paper-26.3 release branches
chore(release): prepare 3.0.1 with Paper 26.3 build 209
```

---

## 8. Recurring tasks

### Adding a feature or fixing a bug

1. Read the relevant classes and reuse existing services/utilities.
2. Check the API (section 3) and pick the Paper-native approach.
3. Implement the smallest change, including config defaults and lang keys.
4. Add or update tests.
5. Update `CHANGELOG.md` for user-visible changes.
6. Run `gradlew build`.
7. Commit.

### Changing versions or dependencies

Only when explicitly requested. Update **all** of these together:

- `paperApiVersion` / plugin versions in `build.gradle.kts`,
- `api-version` and `minecraftVersion` in `plugin.yml` / `build.gradle.kts`,
- CI smoke-test download URLs and SHA-256 hashes in `.github/workflows/code-quality.yml`,
- `README.md`, `MODRINTH.md`, `CHANGELOG.md`, and `version` in `build.gradle.kts`.

Then run `gradlew build` and both smoke tests locally if possible.

### Translating / syncing messages

Start from `messages_en.yml`, add the key to all eight files, run the parity test,
and make sure every value parses as MiniMessage. Never ship an English placeholder
as a "translation" without noting it.

### Preparing a release

1. Bump the version and changelog.
2. Align README, Modrinth, and CI pins.
3. Run the full build and both server smoke tests.
4. Commit as `chore(release): ...`; do not tag or publish unless asked.

---

## 9. Definition of done

- [ ] Uses Paper API where possible; every unfamiliar API was verified in the 26.3 Javadoc.
- [ ] No NMS, reflection, deprecated calls, or legacy color codes introduced.
- [ ] Java 25, Paper/Minecraft 26.3, and Folia compatibility preserved.
- [ ] Text uses Adventure components / MiniMessage.
- [ ] New config keys have defaults, reads, and validation.
- [ ] New message keys exist in all eight language files.
- [ ] Tests added or updated; `gradlew build` passes.
- [ ] Changelog updated for user-visible changes.
- [ ] Exactly the intended files were staged, and the change was committed with a
      Conventional Commit message.
- [ ] Nothing was pushed unless the user asked.

---

## 10. Useful paths

| Path | Purpose |
|---|---|
| `build.gradle.kts` | Versions, dependencies, quality gates, server tasks |
| `src/main/resources/plugin.yml` | Plugin metadata, permissions, `api-version` |
| `src/main/resources/config.yml` | Default configuration |
| `src/main/resources/lang/` | Eight MiniMessage language files |
| `src/main/java/org/vwtfafa/lockEnd/util/PaperScheduler.java` | Folia-safe scheduling |
| `src/main/java/org/vwtfafa/lockEnd/MessageService.java` | Message lookup and rendering |
| `src/main/java/org/vwtfafa/lockEnd/commands/` | Brigadier command tree and history/undo |
| `.github/workflows/code-quality.yml` | CI build + Paper/Folia smoke tests |
| `.claude/skills/` | Local helper skills (`run-lock-end`, `i18n-sync`); gitignored |
