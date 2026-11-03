# Modrinth – Texte & Upload-Checkliste

Vor dem Upload: Loader pro Version wählen (Fabric- und NeoForge-Jar jeweils
als eigene Version mit eigenem Loader), Game Version `26.3`, Java 25.
Abhängigkeiten pro Version:

| Variante | Abhängigkeit |
| --- | --- |
| Fabric-Version | Fabric API (required) |
| NeoForge-Version | keine (nur Loader/Game Version) |
| Paper-Version | keine |
| Datapack | keins |

---

## Kurzbeschreibung (Summary)

```
🔒 Lock or unlock the End globally — portals, /tp & plugin teleports. One project for Paper/Folia, Fabric, NeoForge & vanilla datapack.
```

---

## Beschreibung (Description) – final

```markdown
# 🔒 Lock-End

**Lock-End** globally locks or unlocks access to the **End dimension** with
one command — portals, `/tp` and plugin teleports are blocked while locked.
One project, four distributions: the **Paper/Folia plugin** (full feature
set), a **Fabric mod**, a **NeoForge mod** (same core, reduced surface) and
a **limited vanilla datapack** for servers without a mod loader.

## ✨ What can it do?

**Paper/Folia plugin** — the full implementation:

* Lock/unlock with `/endlock` (aliases `/lock`, `/el`), native tab completion
* Blocks portals, `/tp`, `/execute` and plugin teleports into the End
* Schedules: unlock/lock in X or at a datetime, countdown, restart-safe
* Bypass rules (player, UUID, world, permission), PlaceholderAPI, audit
  history with JSON/CSV export and undo, evacuation with preview &
  confirmation, broadcasts, sounds, grace period, configurable lock reasons,
  full config + 8 languages, update checker, bStats

**Fabric mod** — compact server-side mod (Fabric API required):

* `/endlock lock|unlock|status` (operators)
* Bounces players out of a locked End within a tick of arrival — including
  teleports (arrival guard plus a tick-scan sweep)
* Broadcast message on lock/unlock, players still inside are evacuated
* Lock state persists as JSON in the config folder; small config file
* English messages (no per-language files yet)

**NeoForge mod** — compact server-side mod (no other mods needed):

* `/endlock lock|unlock|status` (operators)
* Blocks entering the End pre-travel (cancellable dimension-transit event)
* Broadcast message on lock/unlock, players still inside are evacuated
* Lock state persists as JSON; small config file; mod id `lockend`
* English messages (no per-language files yet)

**Vanilla datapack** — zero dependencies, deliberately limited subset:

* `/function lockend:lock|unlock|status` plus a one-time
  `/function lockend:set_spawn` arrival anchor
* Players are moved out within one tick of arriving in a locked End
* Lock state persists in a scoreboard (survives restarts), scheduled lock/unlock
  via vanilla `/schedule function lockend:...`
* Limitations by design: no pre-travel block, no `/endlock` command tree,
  no bypasses, no history, fixed broadcast texts — use the mods when
  your server can load them

> In short: all four distributions keep the End closed until it is unlocked
> again.

## 🧱 Compatibility & install

| Distribution | Loader | Needs | Install |
| --- | --- | --- | --- |
| `lock-end-<version>.jar` | Paper / Folia / Purpur | Java 25 | `plugins/` folder |
| `lock-end-fabric-<version>.jar` | Fabric | Fabric API | `mods/` folder |
| `lock-end-neoforge-<version>.jar` | NeoForge | — | `mods/` folder |
| `lock-end-datapack-<version>.zip` | Vanilla | — | `<world>/datapacks/`, then set the anchor with `/function lockend:set_spawn` |

Minecraft 26.3. The Paper plugin exposes an 8-language,
PlaceholderAPI-enabled feature set; the mods and the datapack intentionally
ship a smaller surface. Full command/permission/config reference for the
Paper plugin: [README on GitHub](https://github.com/vwtfafa/lock-end#commands).

## 🔗 Links

* [GitHub Repository](https://github.com/vwtfafa/lock-end)
* [Issues & Support](https://github.com/vwtfafa/lock-end/issues)
```

---
