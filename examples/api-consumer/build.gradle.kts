// Standalone prototype build - NOT part of the main EndLock build.
// Consumers never depend on the EndLock build; they use the released 3.1 jar
// as a compileOnly dependency (contract docs/3.1-api-contract.md §7).
plugins {
    java
}

group = "org.vwtfafa.apicons"
version = "0.1.0"

val targetJavaVersion = 25

java {
    if (JavaVersion.current() < JavaVersion.toVersion(targetJavaVersion)) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
}

repositories {
    mavenCentral()
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.209-stable")
    // Simulates the 3.1 distribution: the released lock-end-*-3.1 jar placed in
    // the main project's build/libs provides the org.vwtfafa.lockEnd.api types.
    // compileOnly only, never shaded - EndLock offers the service at runtime.
    compileOnly(fileTree(mapOf(
        "dir" to "../../build/libs",
        "include" to listOf("lock-end-*.jar"),
    )))
}

tasks {
    jar {
        archiveBaseName.set("EndLockApiConsumer")
        archiveVersion.set(project.version.toString())
        archiveClassifier.set("")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}
