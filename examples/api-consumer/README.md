# EndLockApiConsumer – 3.1 API Consumer Prototype

> **Verification:** This project is the live-tested reference consumer for the
> 3.1 API. Service lookup, event dispatch, and console/undo/scheduled paths
> were tested on Paper 26.3 and Folia 26.3 Build 5 ALPHA. Player-driven Folia
> paths still require testing with live players. See the
> [prototype report](../../docs/3.1-prototype-verification.md).
>
> **Integration guide:** For dependency setup, service lookup, event
> observation, Paper/Folia threading, and a complete example, see the
> [integration guide](../../docs/3.1-integration-guide.md).

This standalone mini-project builds the `EndLockApiConsumer` 0.1.0 plugin. It
is not part of the main EndLock build. It demonstrates the consumer pattern
defined in the [API contract](../../docs/3.1-api-contract.md) and tests the
prototype scenarios described in the
[feasibility notes](../../docs/3.1-api-feasibility.md).

## What it verifies

- **Startup lookup:** The API-dependent `EndLockApiIntegration` class is
  loaded only if EndLock is enabled. It calls
  `getRegistration(EndLockApi.class)`. If EndLock is absent, the main
  consumer class has no API type references and continues without the
  integration. Hot-enabling or hot-reloading EndLock while the consumer
  remains enabled is not supported; restart the server.
- **State-change events:** An `@EventHandler` logs each event. There is one
  event per actual runtime state transition, including when a later side
  effect fails. No event is fired when the requested state is already active.
- **Dispatch context:** The event log includes `primaryThread=` and
  `threadName=` to observe the synchronous dispatch context. Expected
  contexts are Paper's main thread and Folia's global-region tick thread.

The service provider is never cached; each status check performs a fresh
lookup. The integration uses no API beyond Paper and the EndLock release JAR.
It performs no world/player operations and schedules no tasks from the event
handler. API references are confined to the integration class so the
optional runtime dependency does not prevent the consumer from loading.

The prototype also:

- Checks status every 60 seconds through
  `GlobalRegionScheduler.runAtFixedRate(...)`.
- Shuts down the test server after four minutes through
  `GlobalRegionScheduler.runDelayed(...)`.

## Prerequisites

Build the main project first so its 3.1 release JAR is present:

```powershell
.\gradlew.bat jar --no-daemon
# -> build\libs\lock-end-3.1.0.jar
```

The consumer uses `..\..\build\libs\lock-end-*.jar` as a `compileOnly`
dependency. It is not shaded into the consumer.

## Build

Run from the repository root:

```powershell
.\gradlew.bat compileJava -p examples\api-consumer --no-daemon
.\gradlew.bat -p examples\api-consumer build --no-daemon
```

The standalone JAR is written to
`examples\api-consumer\build\libs\EndLockApiConsumer-0.1.0.jar`.

## Run

1. Put the consumer JAR and EndLock JAR in the `plugins/` directory of a
   Paper 26.3 or Folia 26.3 Build 5 server.
2. Start the server and inspect the log for startup status, status ticks,
   and event entries after console commands such as `/endlock lock` and
   `/endlock unlock`.
3. Confirm that repeating a lock command while already locked does not
   produce another event.
4. The prototype shuts down the server after four minutes.

For the scenarios actually run and their limits, see the
[live verification report](../../docs/3.1-prototype-verification.md).
