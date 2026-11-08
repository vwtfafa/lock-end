# Lock-End Datapack (vanilla)

A deliberately limited vanilla implementation of Lock-End for servers without
a mod loader. The datapack intentionally trades the full feature set for a
zero-dependency install; the Paper/Folia plugin, the Fabric and NeoForge mods
are the full implementations.

## Install

1. Copy the zip from the project build (`build/libs/lock-end-datapack-<version>.zip`)
   into the world's `datapacks` folder (`<world>/datapacks/`), or unpack the
   zip under a folder of your choice in the same place.
2. Run `/reload` (or restart the server) to activate the pack.
3. Once, as an operator, stand at the location that locked-out players should
   arrive at (typically your world spawn) and run:

   ```
   /function lockend:set_spawn
   ```

   This places a persistent marker entity at your position. Without the
   anchor the pack cannot move players out of a locked End.

## Functions

| Command | Description |
|---|---|
| `/function lockend:lock` | Locks the End, broadcasts the change and moves every player currently inside out. |
| `/function lockend:unlock` | Unlocks the End and broadcasts the change. |
| `/function lockend:status` | Tells the calling source whether the End is locked. |
| `/function lockend:set_spawn` | Places/replaces the spawn anchor at the operator's current position. |

All functions execute their inner commands with the invoker's permission;
`scoreboard`/`tellraw` require operator level, which is the vanilla
equivalent of the mod adapters' permission gates (the Paper default).

## State and behavior

- The master lock record is a fake-player scoreboard entry (`#locked` on the
  `lockend_state` objective). Scoreboards persist with the world save, so a
  persisted active lock re-applies automatically after restarts.
- The `tick` function runs on every server tick (`minecraft:tick` tag) and
  is a no-op while the End is unlocked; the `load` function creates the
  objective on every world boot.
- Evacuation moves players to the persistent marker placed by `set_spawn`
  (a `minecraft:marker` entity), while the mod adapters teleport to the
  overworld respawn point.
- Players entering the End while it is locked are bounced out within one
  tick of arriving; a boot with a persisted active lock evacuates players
  stranded inside (restart recovery).

## Scheduled actions

Scheduling uses the vanilla `schedule` command, whose timers persist with
the world:

```
/schedule function lockend:lock 3600s      # lock in one hour
/schedule function lockend:unlock 600s     # unlock in ten minutes
/schedule clear lockend:lock                # cancel a pending lock
```

Run as an operator. Scheduled functions execute with server permissions, so
they fire even while no operator is online.

## Limitations (by design)

A datapack has no mod-loader events, so this subset is weaker than the
Paper/Fabric/NeoForge/Forge adapters:

- **No pre-travel block.** Vanilla exposes no cancellable portal or
  dimension event a datapack could hook. Entry into the End is not
  prevented; arriving players are bounced out on the next tick at the
  latest.
- **No lock reason.** Vanilla functions cannot carry arbitrary custom text;
  all broadcasts are fixed strings.
- **No custom command tree.** Everything is a `function`; there is no
  `/endlock` command, no tab completion and no aliases.
- **No history/undo chain.** Only the `lock`/`unlock` pair; there is no
  Paper-style history, and no scheduled action list beyond `schedule`.
- **Global evacuation only.** Operators inside the End are evacuated like
  everyone else; there are no per-player bypasses.
- **Fixed arrival anchor.** Players arrive at the `set_spawn` anchor, not at
  their individual respawn points: vanilla functions cannot read per-player
  respawn data.

Use the mod implementations whenever the server can load mods.
