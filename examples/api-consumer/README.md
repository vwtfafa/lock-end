# EndLockApiConsumer – 3.1 API-Verbraucher-Prototyp

> **Verify-Hinweis:** Dieses Projekt ist die **live verifizierte**
> Referenzimplementierung für den 3.1-Verbraucherpfad: Service-Abfrage,
> Event-Dispatch und die Konsol-/Undo-/Zeitplanwege wurden auf Paper 26.3 und
> Folia 26.3 Build 5 (ALPHA) getestet; siehe
> [docs/3.1-prototype-verification.md](../../docs/3.1-prototype-verification.md).
> Spieler-triggered Pfade auf Folia erfordern weiterhin eine manuelle
> Verifikation mit echten Spielersitzungen.

> **Ausführliche Anleitung:** Für die Integration in ein eigenes Plugin
> (Dependency-Setup, Service-Lookup, Event-Beobachtung, Paper/Folia-
> Threadregeln, vollständiges Beispiel) siehe
> [docs/3.1-integration-guide.md](../../docs/3.1-integration-guide.md).

Eigenständiges Mini-Projekt (Plugin `EndLockApiConsumer` v0.1.0), **nicht Teil
des Haupt-EndLock-Builds**. Es simuliert den Verbraucherfall aus
[docs/3.1-api-contract.md](../../docs/3.1-api-contract.md) §7/§8 und
verifiziert die Prototyp-Ziele aus
[docs/3.1-api-feasibility.md §9](../../docs/3.1-api-feasibility.md):

- **Status-Query beim Start:** `getRegistration(EndLockApi.class)` in
  `onEnable` (mit `softdepend: [EndLock]` ist EndLock davor geladen). Fehlt
  der Service (EndLock fehlt/deaktiviert), läuft der Prototyp trotzdem weiter
  – „EndLock API not available - running without integration" – und
  `onEnable` registriert trotzdem Listener und Tasks.
- **Genau ein Event je Änderung:** ein `@EventHandler` für
  `EndLockStateChangeEvent` loggt jedes Feuer; das Hauptprojekt wirft laut
  Vertrag genau ein Event pro erfolgreichem `changeLockState` (Guard
  verhindert Doppel-Events bei bereits aktivem Zielzustand).
- **Kontext-Log der Threads:** der Event-Log enthält
  `primaryThread=` (`server.isPrimaryThread()`) und `threadName=`
  (`Thread.currentThread().getName()`) – das belegt den synchronen
  Dispatch-Kontext (Paper: Main-Thread; Folia: Region-/Global-Region-Thread).

Wie im Vertrag vorgeschrieben: Der Provider wird **nie gecacht**; jeder
Status-Tick holt `getRegistration(...)` frisch. Kein Code außerhalb von
paper-api + dem EndLock-Release-Jar, keine Welt-/Spieler-Operationen,
keine Scheduler-Aufrufe im Event-Handler.

Zusätzlich fürs Prototyping:

- **Status-Tick alle 60 s** (`status tick: locked=..., reason=...`) über
  `getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> ..., 1200, 1200)`
  – identisch auf Paper und Folia.
- **Selbst-Shutdown nach 4 Minuten** über
  `getServer().getGlobalRegionScheduler().runDelayed(this, task -> getServer().shutdown(), 4000)`,
  damit der Prototyp-Server ohne Eingriff endet.

## Voraussetzung

Das Hauptprojekt muss ein Release-Jar erzeugt haben (steht für die „3.1
Verteilung"; der aktuelle Stand `lock-end-3.0.2` enthält das API-Paket):

```powershell
.\gradlew.bat jar --no-daemon    # aus dem Repo-Wurzelverzeichnis
# -> build\libs\lock-end-3.0.2.jar
```

Der Consumer-Build bindet `..\..\build\libs\lock-end-*.jar` per
`compileOnly fileTree` ein – genau wie künftig das veröffentlichte 3.1-Jar.

## Kompilieren (aus dem Repo-Wurzelverzeichnis)

```powershell
# nur Kompilierprüfung gegen jar + paper-api
.\gradlew.bat compileJava -p examples\api-consumer --no-daemon

# Klassik-Version, falls der -p-Wrapper-Aufruf zickt:
powershell -c "Set-Location examples\api-consumer; ..\..\gradlew.bat build --no-daemon"
```

## Laufen lassen

1. Baue das Jar: `.\gradlew.bat -p examples\api-consumer build --no-daemon`
   → `examples\api-consumer\build\libs\EndLockApiConsumer-0.1.0.jar`.
2. Jar zusammen mit dem EndLock-Jar in den `plugins/`-Ordner eines
   Paper-26.3- bzw. Folia-26.3-Build-5-Servers legen (EULA akzeptiert,
   `eula=true`).
3. Server starten und Log betrachten. Erwartet:
   - `startup status: locked=..., reason=...` (oder „not available"),
   - bei `/endlock lock` bzw. `/endlock unlock` (Konsole oder Spieler)
     genau ein `state-change: previousLocked=..., newLocked=..., ...`-Log,
   - `status tick: locked=..., reason=...` alle 60 s,
   - Selbst-Shutdown nach 4 Minuten.
