# EndLock 3.1 – Vorschlag und API-Entwurf

> **Status:** Vorschlag, kein bereits zugesagter Releaseumfang.  
> **Ausgangspunkt:** EndLock 3.0.2, Paper 26.3 / Java 25. Die Versionen bleiben
> unverändert, solange ein konkreter Grund für ein Upgrade fehlt.

## Ziel für 3.1

EndLock hat bereits viele Funktionen für Administration, Zeitpläne, Verlauf und
Evakuierung. 3.1 sollte deshalb vor allem die Integration mit anderen Plugins
und die Kompatibilität im realen Serverbetrieb verbessern, statt weitere
Einzelbefehle anzuhäufen.

**Vorgeschlagener Schwerpunkt:** eine kleine, dokumentierte und zunächst
schreibgeschützte Plugin-API. Ergänzend sollten die noch nicht live geprüften
Folia-Spielerabläufe getestet und die API-Nutzung dokumentiert werden.

## Vorgeschlagener Umfang

### 1. Öffentliche Plugin-API

Andere Plugins sollen den aktuellen Sperrzustand und den Sperrgrund abfragen
und auf tatsächliche Statusänderungen reagieren können. Beispiele:

- Ein Portal- oder Fortschritts-Plugin zeigt eine eigene Meldung, wenn das End
  gesperrt ist.
- Ein Event-Plugin startet ein Event, sobald EndLock das End freigibt.
- Ein Dashboard oder ein anderes Admin-Plugin zeigt den Status und Sperrgrund an.

#### Erste API-Version: lesen und beobachten

Die API sollte zunächst nur stabile, bewusst ausgewählte Informationen
freigeben. Die folgenden Signaturen sind ein Gesprächs- und Designentwurf,
keine fertige API-Spezifikation:

```java
public interface EndLockApi {
    boolean isLocked();

    String getLockReason();
}
```

Eine Abfrage, ob eine konkrete Welt vom Lock erfasst wird, kommt nur dazu, wenn
ein tatsächlicher Verbraucherfall und eine eindeutige Semantik dafür vorliegen.
Die API sollte über einen von Paper 26.3 verifizierten Integrationsmechanismus
angeboten werden. Der `ServicesManager` ist ein Kandidat, dessen Verfügbarkeit,
Registrierung und Lebenszyklus vor der Implementierung anhand der offiziellen
Dokumentation geprüft werden müssen. Damit müssen Integrationen keine
`LockEnd`-Interna oder Config-Dateien auslesen. EndLock bleibt zur Laufzeit eine
optionale Abhängigkeit; integrierende Plugins prüfen, ob der Service verfügbar
ist.

#### Statusänderungs-Event

Zusätzlich ist ein öffentliches, nicht abbrechbares
`EndLockStateChangeEvent` vorgesehen. Es wird nur bei einer erfolgreichen
Änderung ausgelöst und enthält:

- vorherigen und neuen Sperrzustand,
- auslösende Aktion, zum Beispiel Befehl, Zeitplan oder Undo,
- optionalen Namen und UUID des Akteurs, falls vorhanden.

Das Event wird nicht ausgelöst, wenn der gewünschte Zustand bereits aktiv ist
oder eine Änderung fehlschlägt. Es informiert über eine bereits ausgeführte
Änderung und kann diese nicht nachträglich verhindern.

Vor der Umsetzung ist festzulegen, in welchem Ausführungskontext dieses Event
auf Paper und Folia ausgelöst wird. Listener dürfen daraus keine implizite
Berechtigung ableiten, beliebige Welt- oder Spielerdaten synchron zu ändern.
Wenn die Threadgarantien nicht plattformübergreifend klar und testbar sind,
muss das API-Design diese Einschränkung explizit dokumentieren oder den
Event-Vertrag entsprechend vereinfachen.

#### API-Grenzen und Kompatibilität

- Die erste API-Version ist **read-only**. Eine API-Methode zum Sperren oder
  Freigeben wird zunächst bewusst nicht angeboten.
- Die erste Version bleibt absichtlich klein: Statusabfrage und
  Statusänderungs-Event. Weltregeln, Konfiguration, Zeitpläne und Statistiken
  werden nur bei einem belegten Integrationsbedarf Teil der API.
- Änderungen laufen weiterhin über EndLocks vorhandene Befehls- und
  Zustandslogik. So bleiben Verlauf, Benachrichtigungen, Zeitpläne,
  Grace-Period und Evakuierung konsistent.
- Es werden keine Config-Objekte, internen Manager oder veränderbaren
  Sammlungen veröffentlicht.
- API-Paket und Event werden als unterstützte Oberfläche dokumentiert;
  Implementierungsklassen bleiben intern.
- Integrationen müssen mit nicht installiertem oder deaktiviertem EndLock
  umgehen. Die API selbst soll keine neue Laufzeitabhängigkeit für EndLock
  einführen.
- Auf Folia dürfen Listener nicht voraussetzen, dass sie beliebige Spieler- oder
  Weltzustände direkt von jedem Thread/Region aus bearbeiten können. Die
  konkrete Event-Ausführung und Scheduler-Regeln müssen bei der Implementierung
  gegen Paper 26.3 und Folia geprüft und dokumentiert werden.

#### API-Beispiel für Verbraucher

Ein integrierendes Plugin sollte beim Start den `EndLockApi`-Service suchen,
bei fehlendem Service ohne Fehler weiterlaufen und Statusänderungen über das
Event beobachten. Auf der Folia-Plattform muss es Spieleraktionen über den
passenden Entity-Scheduler ausführen. Ein vollständiges, versionsgeprüftes
Beispiel gehört in die spätere API-Dokumentation, sobald die konkreten Klassen
implementiert sind.

### 2. Machbarkeit vor Implementierung

Bevor die API Teil eines Releases wird:

1. Die offiziellen Paper-26.3-Dokumente für Service-Registrierung, Event-
   Dispatch und Scheduler-Verhalten prüfen; dieselben Annahmen für Folia
   verifizieren.
2. Entscheiden, wie Verbraucher die API beim Kompilieren beziehen. Für den
   ersten Schritt keine Maven-Veröffentlichung oder neue Laufzeitabhängigkeit
   voraussetzen; Distribution und optionale Abhängigkeit müssen klar
   dokumentiert sein.
3. Einen kleinen End-to-End-Prototyp mit einem Verbraucher-Plugin bauen und
   prüfen, ob der Status beim Start abfragbar ist und erfolgreiche Änderungen
   genau ein Event auslösen.
4. Erst danach den Vertrag stabilisieren und Tests, Beispiel und
   Kompatibilitätsregeln festlegen.

Wenn Service-Lebenszyklus oder Event-Threading nicht sauber mit Paper und
Folia vereinbar sind, sollte der Releaseumfang zunächst auf eine dokumentierte
Statusabfrage begrenzt werden, statt eine unsichere Event-Garantie zu
versprechen.

### 3. Folia-Spielerpfade live verifizieren

Die bisher dokumentierte Prüfung deckt vor allem Serverstart und Konsolenbefehle
ab. Für 3.1 sollte ein Testserver mit Spielern mindestens folgende Abläufe
prüfen:

- Teleport beziehungsweise Portalreise in ein gesperrtes End,
- Respawn mit dem End als Todeswelt,
- manuelle und automatische Evakuierung,
- Join-Benachrichtigungen und Grace-Period,
- Lock/Unlock durch Spieler sowie durch einen Zeitplan.

Fehler und Scheduler-Verstöße sollen behoben werden, bevor die
Kompatibilitätsaussage erweitert wird. Wenn kein echter Folia-Spielertest
möglich ist, bleibt die Dokumentation ausdrücklich auf den tatsächlich
verifizierten Umfang beschränkt.

### 4. API- und Integrationsdokumentation

Mit der API sollten eine kompakte Integrationsanleitung und ein Beispiel-Plugin
oder ein minimales Beispielprojekt veröffentlicht werden. Die Anleitung sollte
erklären:

- wie die API-Abhängigkeit eingebunden wird,
- wie ein Plugin den Service optional findet,
- wie Statusänderungen beobachtet werden,
- welche Thread- und Folia-Regeln für Listener gelten,
- welche API-Version verfügbar ist und wie Änderungen kompatibel bleiben.

## FastStats und Metriken

FastStats sollte **nicht automatisch Teil von 3.1** werden. 3.0.2 hat die
Integration entfernt, weil Token-Konfiguration und Einrichtung verwirrend
waren; bStats ist weiterhin vorhanden. Die Plugin-API deckt den
Integrationsbedarf anderer Plugins direkter ab, ohne einen weiteren
Analytics-Dienst oder Token-Setup vorauszusetzen.

FastStats kann separat neu bewertet werden, wenn ein konkreter Dashboard- oder
Nutzungsfall vorliegt. Dann müssten Einrichtung, Datenschutz, Opt-out,
Fehlerbehandlung und Tests vorab eindeutig festgelegt werden.

## Nicht im Umfang dieses Vorschlags

- Kein Upgrade von Paper, Minecraft, Java oder Gradle allein für die Versionsnummer.
- Keine automatische FastStats-Wiedereinführung.
- Keine beschreibende API zum Sperren/Freigeben in der ersten API-Version.
- Keine neuen Konfigurationsoptionen ohne konkreten Anwendungsfall.
- Keine Umstellung oder Entfernung der bestehenden Befehle, PlaceholderAPI-
  Platzhalter oder bStats-Metriken.

## Abnahmekriterien für 3.1

- Der API-Service ist nur verfügbar, solange EndLock aktiviert ist, und
  optionale Verbraucher können ihn sicher vermissen.
- API-Abfragen spiegeln den aktiven Laufzeitstatus wider und legen keine
  internen Konfigurationsobjekte offen.
- Das Status-Event enthält korrekte Vorher-/Nachher-Werte und wird genau bei
  erfolgreichen Zustandswechseln ausgelöst.
- Bestehende Wege über Befehl, Zeitplan und Undo behalten Verlauf,
  Benachrichtigungen, Evakuierung und Persistenz bei.
- API-Verhalten und Folia-Threadregeln sind dokumentiert und getestet.
- Geänderte Spielerpfade werden auf Paper und – soweit verfügbar – auf Folia
  mit einem echten Spieler geprüft.
- Die bestehende Build-, Test-, Checkstyle- und SpotBugs-Prüfung bleibt grün.

## Empfohlene Reihenfolge

1. API-Machbarkeit und Threading auf Paper 26.3 und Folia verifizieren.
2. Verbraucherfall und Abhängigkeits-/Verteilungsweg entscheiden; API-Signaturen
   und Event-Semantik finalisieren.
3. Kleinen Prototyp mit einem Verbraucher-Plugin erstellen und die
   Zustandsänderungswege prüfen.
4. Nur bei erfolgreichem Prototyp Service/Event, fokussierte Tests und
   Integrationsbeispiel implementieren.
5. Folia-Spielerpfade testen und nötige Korrekturen umsetzen.
6. Releaseumfang, Changelog und Versionsnummer erst nach erfolgreicher
   Verifikation festlegen.
