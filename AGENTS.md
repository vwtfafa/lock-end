# AGENTS.md

Working agreement for AI coding agents (OpenCode, Claude Code, Codex, ...) in this
repository. It records how we work on EndLock. `build.gradle.kts`, `plugin.yml` and
CI are the source of truth for versions and commands; if this file disagrees with
them, the build files win and this file should be corrected.

`CLAUDE.md` is retained only as a pointer to this file. `AGENTS.md` is the
authoritative project instruction document.

---

## 1. Project at a glance

| Topic | Value |
|---|---|
| Product | EndLock - Paper/Folia plugin that locks access to the End dimension |
| Main class | `org.vwtfafa.lockEnd.LockEnd` |
| Language | Java 25 (`targetJavaVersion = 25` in `build.gradle.kts`) |
| Server API | Paper 26.3, `io.papermc.paper:paper-api:26.3.build.209-stable` |
| Minecraft | 26.3 (`api-version: '26.3'`, `runServer { minecraftVersion("26.3") }`) |
| Build | Gradle 9.8.2 wrapper, Java toolchain 25 |
| Text API | Adventure (bundled with `paper-api`), MiniMessage |
| Platforms | Paper 26.3 and Folia 26.3 Build 5 (ALPHA; `folia-supported: true`) |
| Optional deps | PlaceholderAPI 2.12.3 (`compileOnly` + `softdepend`), bStats 3.2.1 (shaded, relocated) |
| Quality gates | JUnit 5, Checkstyle 10.12.4, SpotBugs 4.10.3 |
| Branch | `beta` is the active branch, remote `origin` = `vwtfafa/lock-end` |
| Current direction | Paper-first implementation with an active multiplatform migration plan for shared API/core and loader-specific adapters |

The version in `build.gradle.kts` (`paperApiVersion`) is authoritative. Do not
"remember" a version from documentation or old notes; read the build file.

The EndLock 3.1 API and release scope are documented in
[ROADMAP-3.1.md](ROADMAP-3.1.md). The API is implemented; player-driven Folia
paths remain subject to the verification limits documented there.

### Architecture direction

The repository is on its way to a multi-platform monorepo. `lock-end-api`
(shared, plain-Java surface) and `lock-end-core` (shared logic: runtime lock
state via `LockStateManager`, change dispatch, strict schedule helpers,
platform contracts) exist without platform dependencies, and the Paper plugin
lives in `platforms/paper/` connected to the shared core (state, persistence
via `ConfigLockStateRepository`, scheduling helpers, shared 4.x API service
registration). Legacy commands, events, history, messaging and the 3.1
`EndLockApi`/`EndLockStateChangeEvent` remain unchanged. Fabric, NeoForge,
Forge and the datapack are still future phases; the Paper/Folia plugin is the
production baseline that all platform abstractions preserve.

The migration plan is documented in [feature/multiplatform-core.md](feature/multiplatform-core.md).
The plan is the authoritative design direction for future refactors, but it must
not be treated as permission to rewrite the existing plugin overnight or to add
unverified loader APIs. The existing Paper implementation remains the baseline
that all platform abstractions must preserve.

Rules for future architecture work:

- Keep Paper/Folia behavior working as the baseline implementation.
- Prefer shared domain logic over copy-pasted platform code.
- Keep platform-specific dependencies out of shared modules.
- Introduce platform abstraction only when the migration is incremental and
  verifiable.
- Do not assume Fabric, NeoForge, Forge, or Datapack are already implemented just
  because the roadmap exists.

---

## 2. Golden rules

1. **Paper API first.** Prefer `io.papermc.paper.*` over `org.bukkit.*` whenever
   Paper provides an equivalent. Fall back to Bukkit only when there is no Paper
   alternative, and say why in the commit body or code comment if it is not obvious.
2. **Verify every API before using it.** Never guess or invent classes, methods,
   parameters, or return types. Check the official Javadoc for the exact configured
   Paper 26.3 version (see section 3). If you cannot verify an API, say so instead
   of writing speculative code.
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

### Developing against Paper 26.3

- Keep Paper 26.3 as the target and use its Javadocs and developer docs as the
  source of truth. Do not use APIs from another version based on memory or
  examples written for a different release.
- Look for Paper 26.3 APIs that can meaningfully improve a requested feature,
  especially lifecycle registration, Brigadier commands, events, and
  Paper/Folia schedulers. Prefer them when they fit the existing architecture;
  do not add an API merely to make the code look newer.
- Before proposing a version-sensitive design, verify the API's availability,
  behavior, deprecation status, and threading contract. If it cannot be verified,
  describe the uncertainty and keep the design at proposal level.
- Preserve the configured Paper and Folia targets unless a version change is
  explicitly requested. API exploration is not permission to upgrade dependencies.

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
- **Public integrations:** keep the EndLock API small and documented. Expose
  stable contracts, not `LockEnd` implementation classes, config objects, or
  mutable internal state. Verify any API changes against Paper 26.3 and Folia.
  The implemented 3.1 surface and its limits are in
  [ROADMAP-3.1.md](ROADMAP-3.1.md); do not expand it without a concrete need.
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

- All bundled language files live in
  `platforms/paper/src/main/resources/lang/messages_<code>.yml` and use
  **MiniMessage only**. Never use `§` there.
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
# platforms/paper/build/libs/lock-end-<version>.jar (shadowJar is the distributable)
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
.\gradlew.bat runFolia --no-daemon    # Folia 26.3 Build 5 (ALPHA), run/folia/
```

Place the built jar in the server's `plugins/` folder, start the server, and verify
at minimum:

- log shows `EndLock v...` and `enabled (Paper 26.3+)`,
- `/endlock status` and `/endlock history status` respond,
- locking and unlocking the End still blocks/permits travel,
- reload and shutdown complete without exceptions.

CI pins Paper 26.3 Build 209 and Folia 26.3 Build 5 and runs the same smoke checks.
Keep both green; Folia support is not theoretical.

---

## 7. Git workflow

### Commit after every change

1. Inspect `git status` before editing. The working tree may contain the user's
   unfinished work - never stage or commit files you did not touch.
2. Make one logical change.
3. Verify it (`gradlew build`, or the narrowest relevant test).
4. If the user asks to review changes before commit or push, leave the relevant
   changes uncommitted and unpushed, show the diff, and wait for approval.
5. Stage explicit paths: `git add <path> [<path> ...]`. Do not use `git add -A` or
   `git add .` while unrelated changes exist.
6. Commit immediately when no review gate is pending.
7. Report the commit hash and what was verified.

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

### Working on the 3.1 API

1. Treat [ROADMAP-3.1.md](ROADMAP-3.1.md) and
   [docs/3.1-api-contract.md](docs/3.1-api-contract.md) as the current
   implemented contract, not as approval for additional API scope.
2. Before changing the API, verify Paper 26.3 service/event behavior and Folia
   threading against the configured API. Do not add speculative signatures.
3. Keep the surface read-only unless a concrete consumer need justifies a
   separately reviewed change. Do not expose broad settings or internals.
4. Preserve existing state-change side effects (history, persistence,
   schedules, messages, grace periods, and evacuation).
5. Keep compatibility claims limited to verified paths. The current open
   player-path tests and optional-dependency smoke test are listed in the
   roadmap and prototype report.

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
- [ ] Paper 26.3 APIs were evaluated for the requested behavior without upgrading
      the configured target or adding APIs without a concrete benefit.
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
| `gradle.properties` | Central versions: project version, Java, Paper API, tools |
| `settings.gradle.kts` | Module includes for the multi-module monorepo |
| `lock-end-api/` | Shared, platform-independent API module (plain Java) |
| `lock-end-core/` | Shared core: state management, dispatch, schedule helpers, platform contracts |
| `docs/api/shared-api.md` | Shared API contract and Paper compatibility rules |
| `buildSrc/` | Shared `lockend.java-conventions` Gradle plugin |
| `config/checkstyle/checkstyle.xml` | Checkstyle rules shared by all Java modules |
| `config/spotbugs/spotbugs.xml` | SpotBugs filter shared by all Java modules |
| `platforms/paper/build.gradle.kts` | Paper module build (versions come from `gradle.properties`) |
| `platforms/paper/src/main/resources/plugin.yml` | Plugin metadata, permissions, `api-version` |
| `platforms/paper/src/main/resources/config.yml` | Default configuration |
| `platforms/paper/src/main/resources/lang/` | Eight MiniMessage language files |
| `platforms/paper/src/main/java/org/vwtfafa/lockEnd/util/PaperScheduler.java` | Folia-safe scheduling |
| `platforms/paper/src/main/java/org/vwtfafa/lockEnd/MessageService.java` | Message lookup and rendering |
| `platforms/paper/src/main/java/org/vwtfafa/lockEnd/commands/` | Brigadier command tree and history/undo |
| `.github/workflows/code-quality.yml` | CI build + Paper/Folia smoke tests |
| `.claude/skills/` | Local helper skills (`run-lock-end`, `i18n-sync`); gitignored |
