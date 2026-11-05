# EndLock

EndLock controls access to the End dimension. The 4.x monorepo includes a
Paper/Folia plugin, Fabric and NeoForge mods, and a limited vanilla datapack.

[![FastStats servers](https://img.shields.io/endpoint?url=https%3A%2F%2Ffaststats.dev%2Fapi%2Fshields%2Flock-end%3Fmetric%3Dservers)](https://faststats.dev/project/lock-end)
[![FastStats downloads](https://img.shields.io/endpoint?url=https%3A%2F%2Ffaststats.dev%2Fapi%2Fshields%2Flock-end%3Fmetric%3Ddownloads)](https://faststats.dev/project/lock-end)
[![FastStats online players](https://img.shields.io/endpoint?url=https%3A%2F%2Ffaststats.dev%2Fapi%2Fshields%2Flock-end%3Fmetric%3Donline_players)](https://faststats.dev/project/lock-end)

[![FastStats Client chart](https://faststats.dev/embed/32f0e494-2943-4fb5-83c0-34d653ca0dd4?w=400&h=200)](https://faststats.dev/project/lock-end)


## Platforms

| Platform | Target | Artifact | Status |
|---|---|---|---|
| Paper / Folia | Minecraft 26.3 | `lock-end-<version>.jar` | Production baseline |
| Fabric | Minecraft 26.3 | `lock-end-fabric-<version>.jar` | Singleplayer verified; CI smoke-tested |
| NeoForge | Minecraft 26.3 | `lock-end-neoforge-<version>.jar` | Dev-server smoke-tested; in-game verification pending |
| Vanilla datapack | Vanilla | `lock-end-datapack-<version>.zip` | Limited feature set |

Forge is not currently supported. The datapack cannot prevent travel before it
happens; use a mod or plugin for stronger enforcement. See the
[datapack notes](datapack/README.md) and
[feature-parity roadmap](docs/multiplatform-feature-parity.md).

## Features

- Lock or unlock the End, with persistent state across restarts.
- Block player travel through portals and common teleport paths.
- Schedule locks and unlocks; pause, resume, or cancel schedules.
- Set a lock reason and optionally warn or evacuate players already in the End.
- Paper/Folia adds history, undo, permissions, placeholders, and the public
  read-only plugin API.

Platform commands and capabilities vary. `/endlock lock`, `/endlock unlock`,
and `/endlock status` are available on the Paper plugin and both mods. The
datapack provides the lock/unlock/status subset through functions.

## Install

Download the artifact for your platform from
[GitHub Releases](https://github.com/vwtfafa/lock-end/releases) or
[Modrinth](https://modrinth.com/plugin/lock-end). Install it as a plugin, mod,
or datapack using the standard location for that platform, then restart or
reload the world/server.

Paper/Folia targets Minecraft 26.3 and Java 25. Fabric and NeoForge target
Minecraft 26.3. See the platform notes above for current verification status.
The client-optional label means clients do not need the mod to join a
multiplayer server. Fabric is verified in singleplayer; NeoForge client-side
loading is not yet verified. Multiplayer End locking is enforced server-side.

## Metrics

FastStats metrics and context-aware error reports are enabled on Paper/Folia,
Fabric, and NeoForge. Set `LOCKEND_FASTSTATS_TOKEN` in the server environment
to override the bundled public project token. Reports may include platform
details and error messages or stack traces. Paper/Folia also retains bStats for
the initial releases; bStats can be disabled in its global configuration.

## Build from source

The repository includes a Gradle wrapper:

```bash
./gradlew build
```

The build produces platform artifacts in the corresponding module's
`build/libs/` directory.

## Links

- [Releases](https://github.com/vwtfafa/lock-end/releases)
- [Issues and support](https://github.com/vwtfafa/lock-end/issues)
- [License](LICENSE) (MIT)
