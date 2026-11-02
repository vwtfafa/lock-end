# Lock-End – Multiplatform Migration Plan

**Project:** Lock-End  
**Current Branch:** `beta`  
**Target Version:** 4.0.0  
**Architecture:** Gradle Multi-Module Monorepo  
**Minecraft Support:** Latest Minecraft Version  
**API Strategy:** Shared API  
**Target Platforms:** Paper, Fabric, NeoForge, Forge and Datapack

---

## 1. Project Vision

The goal is to transform Lock-End from a Paper-only plugin into a multiplatform Minecraft project.

Lock-End should provide the same core functionality across supported platforms while keeping platform-specific implementations separate.

The project will use a shared architecture consisting of:

- A platform-independent API.
- A platform-independent core.
- Individual platform adapters.
- A separate, limited Datapack implementation.

The existing Paper plugin must remain functional throughout the migration.

### Main Principles

1. Avoid duplicated business logic.
2. Keep platform-specific dependencies out of the shared modules.
3. Preserve the existing Paper API wherever possible.
4. Use the same core behavior across all Java-based platforms.
5. Keep each platform independently buildable.
6. Prioritize maintainability, performance and compatibility.

---

## 2. Target Repository Structure

The repository will remain a single GitHub repository.

```text
lock-end/
│
├── lock-end-api/
│   └── src/
│
├── lock-end-core/
│   └── src/
│
├── platforms/
│   ├── paper/
│   │   └── src/
│   │
│   ├── fabric/
│   │   └── src/
│   │
│   ├── neoforge/
│   │   └── src/
│   │
│   └── forge/
│       └── src/
│
├── datapack/
│   └── src/
│
├── docs/
│   ├── api/
│   ├── architecture/
│   └── migration/
│
├── gradle/
├── build.gradle
├── settings.gradle
├── gradle.properties
└── README.md
```

### Module Responsibilities

| Module | Responsibility |
|---|---|
| `lock-end-api` | Shared public API and data structures |
| `lock-end-core` | Platform-independent Lock-End logic |
| `platforms/paper` | Paper plugin implementation |
| `platforms/fabric` | Fabric mod implementation |
| `platforms/neoforge` | NeoForge mod implementation |
| `platforms/forge` | Forge mod implementation |
| `datapack` | Limited vanilla implementation |

No platform module should depend on another platform module.

---

## 3. Shared API

The current Paper API uses Bukkit/Paper-specific classes.

These classes must not be moved directly into the shared API.

Instead, a new platform-independent API will be introduced.

### Proposed API Structure

```text
lock-end-api/
└── src/main/java/org/vwtfafa/lockend/api/
    ├── LockEndApi.java
    ├── LockState.java
    ├── LockAction.java
    ├── LockStateSnapshot.java
    ├── LockStateChange.java
    └── LockStateChangeListener.java
```

### Proposed Main Interface

```java
public interface LockEndApi {

    boolean isLocked();

    String getLockReason();

    LockStateSnapshot getState();

    void addStateChangeListener(
        LockStateChangeListener listener
    );

    void removeStateChangeListener(
        LockStateChangeListener listener
    );
}
```

### Proposed State Change Model

```java
public record LockStateChange(
    boolean previousLocked,
    boolean newLocked,
    LockAction action,
    String actorName,
    UUID actorUuid
) {}
```

### API Requirements

The shared API must:

- Avoid Bukkit and Paper dependencies.
- Avoid Fabric and NeoForge dependencies.
- Avoid direct Minecraft implementation dependencies.
- Provide consistent state access.
- Support state change listeners.
- Allow future API extensions.
- Be usable by external plugins and mods.

### Paper API Compatibility

The existing Paper API must remain available.

The migration should introduce a compatibility adapter:

```text
Shared API
    |
    └── Paper API Adapter
            |
            └── Existing Paper API
```

Existing Paper integrations should continue working wherever possible.

The existing `EndLockApi` and `EndLockStateChangeEvent` must not be removed without a documented migration path.

---

## 4. Shared Core

The core contains all logic that does not require direct Minecraft or platform access.

### Responsibilities

- Lock state management.
- Lock reasons.
- State transitions.
- Scheduling logic.
- Time and date handling.
- Lock policies.
- State change notifications.
- Platform-independent configuration models.
- History and statistics models.

### Proposed Core Structure

```text
lock-end-core/
└── src/main/java/org/vwtfafa/lockend/core/
    ├── LockManager.java
    ├── LockStateManager.java
    ├── ScheduleManager.java
    ├── LockPolicy.java
    ├── LockStateRepository.java
    ├── StateChangeDispatcher.java
    └── model/
```

This is a proposed target structure. Existing classes should be reused and moved incrementally rather than rewritten unnecessarily.

### Platform Abstraction

Platform-specific operations must be represented by interfaces.

Examples:

```java
public interface PlayerEvacuationHandler {

    void evacuatePlayers();
}
```

```java
public interface LockStateStorage {

    void save(LockStateSnapshot state);

    LockStateSnapshot load();
}
```

The core defines the contracts. Each platform provides its own implementation.

### Core Requirements

- No Minecraft imports.
- No Bukkit imports.
- No Fabric imports.
- No NeoForge imports.
- No Forge imports.
- Unit tests must run without starting a Minecraft server.

---

## 5. Platform Implementations

Each platform uses the same shared API and core.

### 5.1 Paper

Paper is the first platform to migrate.

Responsibilities:

- Existing plugin initialization.
- Existing commands.
- Bukkit/Paper event integration.
- Portal and teleport handling.
- Player evacuation.
- Configuration and messages.
- Existing API compatibility.
- Paper ServicesManager registration.

The existing Paper implementation must remain functional during extraction.

### 5.2 Fabric

Fabric will be the first new mod implementation.

Responsibilities:

- Fabric mod initialization.
- Server-side Lock-End integration.
- Dimension and portal handling.
- Command registration.
- Configuration.
- State persistence.
- Shared API access.

Fabric-specific event hooks or Mixins should only be used where necessary.

### 5.3 NeoForge

NeoForge receives an independent adapter.

Responsibilities:

- Mod initialization.
- Event bus integration.
- Dimension transition handling.
- Command registration.
- Configuration.
- State persistence.
- Shared API integration.

The NeoForge adapter must not depend on Fabric or Paper.

### 5.4 Forge

Forge receives a separate adapter if the selected latest Minecraft version is supported.

Responsibilities:

- Forge initialization.
- Event integration.
- Dimension transition handling.
- Commands.
- Configuration.
- Shared API integration.

Forge and NeoForge must remain separate implementations.

### 5.5 Datapack

The Datapack implementation is intentionally limited.

Possible features:

- Lock and unlock functions.
- Lock state storage.
- Scheduled actions.
- Basic status output.
- Limited portal restrictions.

A Datapack cannot implement the Java API or reliably intercept every possible teleport.

Its feature set must therefore be documented separately.

---

## 6. Migration Phases

### Phase 1 – Repository Preparation

Tasks:

- [x] Create `feature/multiplatform-core` from `beta`.
- [x] Keep `beta` as the integration branch.
- [x] Prepare the Gradle multi-module structure.
- [x] Centralize Minecraft and Java versions.
- [x] Preserve the existing Paper build.
- [x] Document the target architecture.

**Completion criteria:** The existing Paper plugin still builds successfully. ✅ (`gradlew build` green on `cc7f675`)

### Phase 2 – Shared API

Tasks:

- [x] Create `lock-end-api`.
- [x] Introduce platform-independent data structures.
- [x] Implement state change listeners.
- [x] Define API contracts.
- [x] Document API compatibility rules.
- [x] Prepare the Paper compatibility adapter.
- [x] Add API unit tests.

**Completion criteria:** The shared API compiles without Minecraft or Bukkit dependencies. ✅ (`cdccd67`)

### Phase 3 – Core Extraction

Tasks:

- [x] Analyze existing LockManager dependencies.
- [x] Extract lock state management.
- [x] Extract scheduling logic.
- [x] Extract platform-independent policies.
- [x] Introduce storage interfaces.
- [x] Introduce platform service interfaces.
- [x] Add unit tests.
- [x] Remove unnecessary platform dependencies.

**Completion criteria:** Core logic can be tested independently. ✅ (`a8405ee`, `b2420c1`)

### Phase 4 – Paper Migration

Tasks:

- [x] Connect Paper to the shared core.
- [x] Preserve existing commands.
- [x] Preserve existing Paper events.
- [x] Preserve API registration.
- [x] Migrate scheduling.
- [x] Migrate state persistence.
- [x] Run existing Paper tests.
- [x] Verify Folia compatibility where supported.

**Completion criteria:** The Paper plugin works with the shared core without losing existing functionality. ✅ (`f83d7b3`)

### Phase 5 – Fabric Implementation

Tasks:

- [x] Add Fabric Gradle module.
- [x] Add Fabric Loader and Fabric API dependencies.
- [x] Connect the shared API and core.
- [x] Implement server initialization.
- [x] Implement dimension transition restrictions.
- [x] Implement commands.
- [x] Implement configuration.
- [x] Implement state persistence.
- [ ] Test on a dedicated server.

**Completion criteria:** Fabric can run Lock-End independently of Paper. 🔄 (`c86a750` implements the adapter and the full build passes; the dedicated-server smoke test is still pending.)

### Phase 6 – NeoForge and Forge

Tasks:

- [ ] Add NeoForge module.
- [ ] Implement NeoForge adapter.
- [ ] Add shared API and core dependencies.
- [ ] Implement dimension restrictions.
- [ ] Implement commands and configuration.
- [ ] Check Forge support for the selected Minecraft version.
- [ ] Add Forge module if compatible.
- [ ] Test each platform independently.

**Completion criteria:** Each supported modloader uses the same core behavior.

### Phase 7 – Datapack

Tasks:

- [ ] Create Datapack structure.
- [ ] Implement lock and unlock functions.
- [ ] Implement state storage.
- [ ] Implement scheduled actions.
- [ ] Implement basic status output.
- [ ] Document limitations.
- [ ] Test on vanilla Minecraft.

**Completion criteria:** The Datapack provides its documented subset of Lock-End functionality.

### Phase 8 – CI and Release

Tasks:

- [ ] Configure GitHub Actions.
- [ ] Build all supported modules.
- [ ] Run shared core tests.
- [ ] Run platform-specific tests.
- [ ] Validate Datapack resources.
- [ ] Prepare separate release artifacts.
- [ ] Update README.
- [ ] Publish API documentation.

**Completion criteria:** All supported modules have reproducible builds.

---

## 7. Testing Strategy

The shared core must provide common behavior tests.

Required test cases:

- [ ] Lock activation.
- [ ] Lock deactivation.
- [ ] Lock reason retrieval.
- [ ] State change notifications.
- [ ] Scheduled locking.
- [ ] Scheduled unlocking.
- [ ] Undo operations.
- [ ] State persistence.
- [ ] Restart recovery.
- [ ] Invalid state handling.

Platform-specific tests:

| Platform | Required tests |
|---|---|
| Paper | Commands, events, portals, teleports |
| Fabric | Commands, portals, dimensions |
| NeoForge | Commands, portals, dimensions |
| Forge | Commands, portals, dimensions |
| Datapack | Functions, storage, scheduling |

The shared core tests should run without a Minecraft server.

---

## 8. Versioning

Target version:

```properties
lock_end_version=4.0.0
```

Minecraft support policy:

- Support the latest selected Minecraft version.
- Avoid maintaining unnecessary legacy compatibility.
- Update platform dependencies together where possible.
- Verify each loader's support before enabling its build.

The exact Minecraft and Java versions must be verified against the available loader toolchains before implementation.

### Release Artifacts

Each platform should produce its own artifact:

```text
LockEnd-Paper-4.0.0.jar
LockEnd-Fabric-4.0.0.jar
LockEnd-NeoForge-4.0.0.jar
LockEnd-Forge-4.0.0.jar
LockEnd-Datapack-4.0.0.zip
```

Only supported and successfully tested platforms should be included in a release.

---

## 9. Initial Commit Plan

Suggested commit sequence:

| Commit | Description |
|---|---|
| `refactor: prepare multi-module gradle structure` | Prepare repository structure |
| `feat(api): introduce shared platform-independent API` | Create shared API |
| `refactor(core): extract lock state management` | Extract state logic |
| `refactor(core): extract scheduling logic` | Extract scheduler |
| `refactor(paper): migrate paper adapter to shared core` | Migrate Paper |
| `feat(fabric): add initial fabric integration` | Start Fabric |
| `feat(neoforge): add initial neoforge integration` | Start NeoForge |
| `feat(forge): add forge integration` | Start Forge if supported |
| `feat(datapack): add vanilla datapack implementation` | Add Datapack |
| `ci: add multiplatform build and test workflows` | Configure CI |

---

## 10. Definition of Done

The migration is considered complete when:

- [ ] The shared API is independent of platform APIs.
- [ ] The shared core contains the reusable business logic.
- [ ] Paper works with the shared core.
- [ ] Existing Paper API integrations remain compatible or have a migration path.
- [ ] Fabric works independently.
- [ ] NeoForge works independently.
- [ ] Forge is included only if the target version is supported.
- [ ] The Datapack provides its documented feature subset.
- [ ] Shared behavior tests pass.
- [ ] Platform builds pass.
- [ ] GitHub Actions validates changes.
- [ ] Documentation covers installation, API usage and limitations.

---

## 11. Final Architecture Decision

Lock-End 4.0.0 will use a shared-core architecture with independent platform adapters.

**The implementation order is:**

1. Shared API.
2. Shared Core.
3. Paper migration.
4. Fabric.
5. NeoForge.
6. Forge, if supported.
7. Datapack.
8. CI and release automation.

The existing `beta` branch remains the foundation. No unnecessary rewrite of the existing Paper implementation should happen before its dependencies and responsibilities have been analyzed.

**Primary objective:** One Lock-End core, one shared API, multiple independent platform implementations.