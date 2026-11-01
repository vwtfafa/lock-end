rootProject.name = "lock-end"

// Lock-End multi-module monorepo. The migration phases are described in
// feature/multiplatform-core.md; each platform lives in its own module and
// no platform module depends on another platform module.

// Shared, platform-independent modules.
include("lock-end-api")

// Platforms.
include("platforms:paper")

// lock-end-core: added with the core extraction phase.
// platforms/fabric, platforms/neoforge, platforms/forge, datapack: added by
// their respective migration phases.
