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
val modmenuVersion = providers.gradleProperty("modmenuVersion").get()
val fastStatsVersion = providers.gradleProperty("fastStatsVersion").get()
val fastStatsMinecraftVersion = providers.gradleProperty("fastStatsMinecraftVersion").get()

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.faststats.dev/releases")
    }
    maven {
        name = "Terraformers"
        url = uri("https://maven.terraformersmc.com/")
    }
}

val sharedModules by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    minecraft("com.mojang:minecraft:$fabricMinecraftVersion")
    // Official Mojang mappings are the Loom default and the documented
    // approach for Minecraft 26.3 (no Yarn mappings exist for it; verified
    // against meta.fabricmc.net/v2/versions/yarn/26.3 and the example mod).
    implementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    implementation("dev.faststats.metrics:fabric:$fastStatsVersion+mc$fastStatsMinecraftVersion")
    include("dev.faststats.metrics:fabric:$fastStatsVersion+mc$fastStatsMinecraftVersion")

    // Optional Mod Menu API for the info screen entrypoint; never bundled.
    // Loom 1.19 / MC 26.3 no longer exposes modCompileOnly; compileOnly is
    // enough because Mod Menu is not remapped into the distributable jar.
    compileOnly("com.terraformersmc:modmenu:$modmenuVersion")

    // Shared Lock-End modules (also merged into the distribution jar below).
    implementation(project(":lock-end-api"))
    implementation(project(":lock-end-core"))
    sharedModules(project(":lock-end-api"))
    sharedModules(project(":lock-end-core"))

    testImplementation("com.google.code.gson:gson:2.15.0")
}

tasks {
    shadowJar {
        archiveBaseName.set("lock-end-fabric")
        archiveVersion.set(project.version.toString())
        archiveClassifier.set("")

        dependsOn("jar")
        configurations = listOf(sharedModules)
        filesMatching("fabric.mod.json") {
            filter { line: String ->
                line.replace(
                    "\"jars\": []",
                    "\"jars\": [{\"file\": \"META-INF/jars/fabric-$fastStatsVersion+mc$fastStatsMinecraftVersion.jar\"}]"
                )
            }
        }
        from({
            zipTree(project.tasks.named("jar").get().outputs.files.singleFile)
        }) {
            include("META-INF/jars/**")
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
