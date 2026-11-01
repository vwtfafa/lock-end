plugins {
    id("lockend.java-conventions")
    id("xyz.jpenilla.run-paper") version "3.1.0"
    id("com.gradleup.shadow") version "9.6.1"
}

val paperApiVersion = providers.gradleProperty("paperApiVersion").get()

repositories {
    mavenCentral()
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "placeholderapi"
        url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    compileOnly("me.clip:placeholderapi:2.12.3")
    implementation("org.bstats:bstats-bukkit:3.2.1")
    implementation(project(":lock-end-core"))
    // Adventure (components, MiniMessage) is provided by paper-api at runtime
    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
}

tasks {
    runServer {
        minecraftVersion("26.3")
    }

    jar {
        archiveBaseName.set("lock-end")
        archiveVersion.set(project.version.toString())
        archiveClassifier.set("")
    }

    shadowJar {
        archiveBaseName.set("lock-end")
        archiveVersion.set(project.version.toString())
        archiveClassifier.set("")
        configurations = listOf(project.configurations.runtimeClasspath.get())

        dependencies {
            // Only merge bStats and the shared Lock-End modules into the final
            // jar; everything else stays a (compileOnly) platform dependency
            exclude {
                it.moduleGroup != "org.bstats" && !it.moduleGroup.startsWith("org.vwtfafa")
            }
        }

        // Relocate bStats into the plugin's package to avoid conflicts with other
        // plugins using bStats
        relocate("org.bstats", project.group.toString())
    }
}

// Local Folia test server, pinned to Folia 26.3 Build 5 (ALPHA) for compatibility testing.
runPaper.folia.registerTask {
    build(5)
    runDirectory(file("run/folia"))
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"

    // Ship third-party license notices with the distribution jar.
    from(rootProject.file("THIRD-PARTY-NOTICES.md")) {
        into("META-INF")
    }

    filesMatching("plugin.yml") {
        expand(props)
    }
}
