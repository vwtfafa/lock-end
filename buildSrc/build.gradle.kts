plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // External Gradle plugins used by precompiled script plugins must be
    // declared here; they must not carry versions inside the script itself.
    implementation("com.github.spotbugs:com.github.spotbugs.gradle.plugin:${property("spotbugsPluginVersion")}")
}
