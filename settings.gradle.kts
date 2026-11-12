// Fabric Loom lives outside the Gradle Plugin Portal; resolve it from the
// official Fabric Maven before falling back to the portal. Versions for
// platform dependencies are centralized in gradle.properties; the Loom
// version itself is declared in platforms/fabric/build.gradle.kts because
// plugin versions must be constant expressions there.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven {
            name = "fabricmc"
            url = uri("https://maven.fabricmc.net/")
        }
    }
}

// Provisions toolchains the modules don't have locally (e.g. ModDevGradle
// runs its asset download with a Java 21 toolchain while the project itself
// builds on the locally installed Java 25). Verified version.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Lock-End multi-module monorepo. Each platform lives in its own module and
// no platform module depends on another platform module. Forge remains
// deliberately deferred (no platforms/forge module).

// Shared, platform-independent modules.
include("lock-end-api")
include("lock-end-core")

// Platforms.
include("platforms:paper")
include("platforms:fabric")
include("platforms:neoforge")

// Limited vanilla datapack adapter.
include("datapack")
