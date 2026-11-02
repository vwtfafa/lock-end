# EndLock 3.1 – Roadmap and API

> **Status:** EndLock 3.1.0 is implemented. This document records the release
> scope and remaining verification work; it is no longer a proposal to build
> the API from scratch.
>
> **Baseline:** EndLock 3.0.2, Paper/Minecraft 26.3, and Java 25. The API
> work does not require upgrading Paper, Minecraft, Java, or Gradle.
>
> **Verification status:** The API, consumer example, contract, and
> integration guide are present. Console and scheduled paths were live-tested
> on Paper 26.3 and Folia 26.3 Build 5 ALPHA. Player-driven Folia paths remain
> unverified without a live player. See the
> [prototype report](docs/3.1-prototype-verification.md).

## 1. Release goal

EndLock already provides administration commands, schedules, history, and
evacuation. Version 3.1 focuses on interoperability with other plugins and
reliable platform behavior rather than adding unrelated commands.

The central feature is a small, read-only plugin API. The release also includes
an integration guide and consumer example. API scope deliberately excludes
mutation methods, internal configuration, and mutable implementation state.

## 2. Implemented API scope

### Public types

The supported package is `org.vwtfafa.lockEnd.api`:

```java
public interface EndLockApi {
    boolean isLocked();

    String getLockReason();
}
```

`EndLockApi` is registered through Paper's `ServicesManager`. It exposes the
current runtime state and effective lock reason as plain text; it does not
expose configuration objects or state-changing methods.

`EndLockStateChangeEvent` is a synchronous, non-cancellable event containing
the previous and new state, action, actor name, and optional actor UUID. It
reports a runtime transition that has already occurred. The current action
values are `LOCK`, `UNLOCK`, `UNDO`, `SCHEDULED_LOCK`, and
`SCHEDULED_UNLOCK`; consumers must tolerate future values.

The runtime state is assigned before persistence and subsequent side effects.
The event is dispatched after every actual state transition, including when a
later side effect throws. A side-effect exception continues to propagate; an
event-dispatch exception is suppressed onto it if both occur. The event
therefore reports the runtime transition and does not guarantee completion of
all side effects.

### Boundaries

- The API remains read-only in 3.1.
- State changes continue through EndLock's commands and existing transition
  logic, preserving history, persistence, broadcasts, schedules, grace
  periods, and evacuation.
- Implementation classes, config structures, and mutable collections remain
  internal.
- Consumers treat EndLock as optional, dynamically look up the service, and
  isolate API class references if they must load without EndLock.
- Hot-enabling or hot-reloading EndLock with an enabled consumer is not
  supported; restart the server after changing EndLock.
- The API is distributed in the EndLock release JAR as a consumer
  `compileOnly` dependency. No Maven publication or JitPack setup was added.

See the [API contract](docs/3.1-api-contract.md) and
[integration guide](docs/3.1-integration-guide.md) for the full details.

## 3. Remaining Folia player-path verification

The prototype currently covers service lifecycle, console commands, undo,
scheduled actions, and global synchronous event dispatch. It does not claim
live verification for player/world paths. When a real Folia player test is
available, verify:

- Portal travel and teleport attempts into a locked End.
- Respawn when the End is the death world.
- Manual and automatic evacuation.
- Join notifications and grace periods.
- Player-issued lock/unlock commands.

Also run the consumer without EndLock installed to exercise its optional
class-loading behavior. Fix any scheduler violations before expanding
compatibility claims. If a live test is unavailable, document that limit
rather than claiming unverified support.

## 4. FastStats and metrics

FastStats is not part of 3.1. Its integration was removed in 3.0.2 because
token configuration and setup were confusing; bStats remains. The plugin API
addresses third-party integration directly without adding another analytics
service or token requirement.

Reconsider FastStats only if a concrete dashboard or usage requirement
emerges. First define setup, privacy, opt-out, failure handling, and tests.

## 5. Out of scope

- Upgrading Paper, Minecraft, Java, or Gradle without a concrete requirement.
- Reintroducing FastStats automatically.
- Adding API methods to lock or unlock the End.
- Adding configuration options without a specific use case.
- Removing or changing existing commands, PlaceholderAPI placeholders, or
  bStats metrics.

## 6. Release acceptance criteria and status

| Criterion | Status |
|---|---|
| Service exists only while EndLock is enabled; optional consumers handle absence | Implemented; lifecycle live-tested |
| Queries report runtime state and do not expose internal config | Implemented; build and prototype checked |
| Event has correct transition data and fires once per actual transition | Implemented; normal paths live-tested |
| Existing command, schedule, and undo flows retain their side effects | Implemented; no regression found in build/prototype checks |
| API and Folia threading rules are documented | Documented; console and scheduled dispatch live-tested |
| Player-driven Folia paths tested with a real player | Open |
| Consumer startup without EndLock tested at runtime | Open; class-loading isolation implemented |
| Build, tests, Checkstyle, and SpotBugs pass | Verified with `gradlew build` |

## 7. Completed implementation sequence

1. Verify Paper 26.3 API availability and Folia threading assumptions.
2. Settle the API surface, distribution model, and event semantics.
3. Build a consumer prototype and exercise console and scheduled paths.
4. Implement the service, event, focused tests, documentation, and example.
5. Complete live Folia player-path verification when a player test environment
   is available.
