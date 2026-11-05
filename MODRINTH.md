# EndLock

Lock and unlock access to the End dimension. EndLock 4.x supports Paper/Folia,
Fabric, NeoForge, and a limited vanilla datapack.

[![FastStats servers](https://img.shields.io/endpoint?url=https%3A%2F%2Ffaststats.dev%2Fapi%2Fshields%2Flock-end%3Fmetric%3Dservers)](https://faststats.dev/project/lock-end)
[![FastStats downloads](https://img.shields.io/endpoint?url=https%3A%2F%2Ffaststats.dev%2Fapi%2Fshields%2Flock-end%3Fmetric%3Ddownloads)](https://faststats.dev/project/lock-end)
[![FastStats online players](https://img.shields.io/endpoint?url=https%3A%2F%2Ffaststats.dev%2Fapi%2Fshields%2Flock-end%3Fmetric%3Donline_players)](https://faststats.dev/project/lock-end)

[![Client optional](https://img.shields.io/badge/client-optional-green?logo=modrinth)](#platforms)
[![Server required](https://img.shields.io/badge/server-required-blue?logo=modrinth)](#platforms)

[![GitHub repository](https://img.shields.io/badge/GitHub-source-black?logo=github)](https://github.com/vwtfafa/lock-end)

## Platforms

| Platform | Target | Artifact | Status |
|---|---|---|---|
| Paper / Folia | Minecraft 26.3 | `lock-end-<version>.jar` | Production baseline |
| Fabric | Minecraft 26.3 | `lock-end-fabric-<version>.jar` | Singleplayer verified; CI smoke-tested |
| NeoForge | Minecraft 26.3 | `lock-end-neoforge-<version>.jar` | Dev-server smoke-tested; in-game verification pending |
| Vanilla datapack | Vanilla | `lock-end-datapack-<version>.zip` | Limited feature set |

Forge is not currently supported. The datapack cannot block travel before it
happens; use a plugin or mod for stronger enforcement.

## Features

- Lock/unlock the End and check its status.
- Block player travel into the End through common paths.
- Schedule locks and unlocks on the plugin and mod platforms.
- Paper/Folia also offers evacuation, history, undo, permissions, placeholders,
  and a public read-only plugin API.

Commands and options differ by platform. See the
[GitHub README](https://github.com/vwtfafa/lock-end#platforms) and
[datapack notes](https://github.com/vwtfafa/lock-end/blob/beta/datapack/README.md)
for details.

## Install

Download the artifact for your platform from the
[latest GitHub release](https://github.com/vwtfafa/lock-end/releases) or this
Modrinth page. Install it in the platform's plugin, mod, or datapack location,
then restart or reload the world/server.

Paper/Folia targets Minecraft 26.3 and Java 25. Fabric and NeoForge target
Minecraft 26.3.
The client-optional label means clients do not need the mod to join a
multiplayer server. Fabric is verified in singleplayer; NeoForge client-side
loading is not yet verified. Multiplayer End locking is enforced server-side.

## Metrics

FastStats metrics and context-aware error reports are enabled on Paper/Folia,
Fabric, and NeoForge. Set `LOCKEND_FASTSTATS_TOKEN` in the server environment
to override the bundled public token. Reports may include platform details and
error messages or stack traces. Paper/Folia retains bStats for the initial
releases; bStats can be disabled in its global configuration.

## Links

- [Source and issues](https://github.com/vwtfafa/lock-end)
- [Changelog](https://github.com/vwtfafa/lock-end/blob/beta/CHANGELOG.md)
- [License](https://github.com/vwtfafa/lock-end/blob/beta/LICENSE)
