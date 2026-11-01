rootProject.name = "lock-end"

// Lock-End multi-module monorepo. The shared modules (lock-end-api,
// lock-end-core) are added by the migration phases in
// feature/multiplatform-core.md; each platform lives in its own module and
// no platform module depends on another platform module.

// The existing Paper/Folia plugin (production baseline).
include("platforms:paper")

// lock-end-core: added with the core extraction phase.
// lock-end-api: added with the shared API phase.
// platforms/fabric, platforms/neoforge, platforms/forge, datapack: added by
// their respective migration phases.
