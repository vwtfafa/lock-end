plugins {
    id("lockend.java-conventions")
    // ModDevGradle version must be a constant expression (Gradle restriction);
    // verified against the plugin portal metadata before use.
    id("net.neoforged.moddev") version "2.0.148"
    id("com.gradleup.shadow") version "9.6.1"
}

// The NeoForge version itself comes from gradle.properties (verified against
// maven.neoforged.net before bumping).
val neoForgeVersion = providers.gradleProperty("neoForgeVersion").get()

repositories {
    mavenCentral()
}

dependencies {
    // Shared Lock-End modules (also merged into the distribution jar below).
    implementation(project(":lock-end-api"))
    implementation(project(":lock-end-core"))

    testImplementation("com.google.code.gson:gson:2.11.0")
}

neoForge {
    // Enables modding on the main source set and pulls the NeoForge/Minecraft
    // toolchain artifacts (ModDevGradle resolves maven.neoforged.net itself).
    version = neoForgeVersion

    runs {
        create("server") {
            server()
        }
    }

    mods {
        create("lockend") {
            sourceSet(sourceSets.main.get())
        }
    }
}

tasks {
    shadowJar {
        archiveBaseName.set("lock-end-neoforge")
        archiveVersion.set(project.version.toString())
        archiveClassifier.set("")

        configurations = listOf(project.configurations.runtimeClasspath.get())
        dependencies {
            // Only merge the shared Lock-End modules into the distribution
            // jar; everything else is provided by NeoForge at runtime.
            // Mirrors the Paper and Fabric modules' shadow filter.
            exclude {
                !it.moduleGroup.startsWith("org.vwtfafa")
            }
        }
    }

    // ModDevGradle registers no remapping tasks (NeoForge uses the official
    // Mojang names), so the plain jar becomes the dev artifact and the shadow
    // jar is the distributable mod.
    jar {
        archiveClassifier.set("dev")
    }
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"

    // Ship the mod version into neoforge.mods.toml like the other modules do.
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(props)
    }

    from(rootProject.file("THIRD-PARTY-NOTICES.md")) {
        into("META-INF")
    }
}
