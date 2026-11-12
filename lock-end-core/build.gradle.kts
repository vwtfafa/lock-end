plugins {
    `java-library`
    id("lockend.java-conventions")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":lock-end-api"))
    implementation("com.google.code.gson:gson:2.15.0")
    implementation("org.slf4j:slf4j-api:2.0.17")
    // Plain-text lock-reason stripping for SharedLockEndApiProvider (Adventure
    // is not a Minecraft dependency; Paper also ships 5.2.0 at runtime).
    implementation("net.kyori:adventure-text-minimessage:5.2.0")
}
