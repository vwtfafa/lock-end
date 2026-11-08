plugins {
    id("lockend.java-conventions")
}

// The vanilla datapack is a pure
// resource implementation. The module ships its pack sources (validated by a
// structural validator) and packages them into the distributable zip; no
// platform integration exists by design (vanilla datapacks cannot intercept
// teleports or expose a Java API; see datapack/README.md).

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.15.0")
}

val packDir = layout.projectDirectory.dir("src/main/pack")

tasks.register<JavaExec>("validatePack") {
    group = "build"
    description = "Validates the datapack structure (pack.mcmeta, function tags, references)."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.vwtfafa.lockend.datapack.PackValidator")
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(packDir.asFile.absolutePath)
    })
}

tasks.register<Zip>("zipPack") {
    group = "build"
    description = "Packages the datapack sources into the distributable zip."
    from(packDir)
    archiveBaseName.set("lock-end-datapack")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}

// The module is resource-only: an empty distribution jar would be noise.
tasks.jar {
    enabled = false
}

tasks.build {
    dependsOn("validatePack", "zipPack")
}
