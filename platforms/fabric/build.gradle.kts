plugins {
    id("lockend.java-conventions")
    id("net.fabricmc.fabric-loom") version "1.19-SNAPSHOT"
    id("com.gradleup.shadow") version "9.6.1"
}

// Plugin versions must be constant expressions (Gradle restriction), so the
// Loom version cannot live in gradle.properties. Verified against the
// official Fabric example mod (FabricMC/fabric-example-mod) and maven.fabricmc.net
// before bumping; the game, loader and Fabric API versions below come from
// gradle.properties.
val fabricMinecraftVersion = providers.gradleProperty("fabricMinecraftVersion").get()
val fabricLoaderVersion = providers.gradleProperty("fabricLoaderVersion").get()
val fabricApiVersion = providers.gradleProperty("fabricApiVersion").get()

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:$fabricMinecraftVersion")
    // Official Mojang mappings are the Loom default and the documented
    // approach for Minecraft 26.3 (no Yarn mappings exist for it; verified
    // against meta.fabricmc.net/v2/versions/yarn/26.3 and the example mod).
    implementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    // Shared Lock-End modules (also merged into the distribution jar below).
    implementation(project(":lock-end-api"))
    implementation(project(":lock-end-core"))

    testImplementation("com.google.code.gson:gson:2.11.0")
}

tasks {
    shadowJar {
        archiveBaseName.set("lock-end-fabric")
        archiveVersion.set(project.version.toString())
        archiveClassifier.set("")

        configurations = listOf(project.configurations.runtimeClasspath.get())
        dependencies {
            // Only merge the shared Lock-End modules into the distribution
            // jar; everything else stays a (Loom-provided) platform
            // dependency at runtime. Mirrors the Paper module's shadow
            // filter.
            exclude {
                !it.moduleGroup.startsWith("org.vwtfafa")
            }
        }
    }

    // Loom 1.19 for Minecraft 26.3 needs no remapping at all (vanilla is
    // unobfuscated with official names, no Yarn mappings exist and no
    // remap* tasks are registered), so the plain jar becomes the dev
    // artifact and the shadow jar is the distributable mod.
    jar {
        archiveClassifier.set("dev")
    }
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"

    // Ship the mod version into fabric.mod.json like the Paper module does
    // for plugin.yml, plus third-party license notices.
    filesMatching("fabric.mod.json") {
        expand(props)
    }

    from(rootProject.file("THIRD-PARTY-NOTICES.md")) {
        into("META-INF")
    }
}
