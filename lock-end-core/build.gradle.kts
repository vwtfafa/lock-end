plugins {
    `java-library`
    id("lockend.java-conventions")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":lock-end-api"))
    // Gson is provided by Minecraft / loaders at runtime; compileOnly so
    // platform shadow jars do not embed a second copy (ClassCastException
    // between TRANSFORMER and app classloaders).
    compileOnly("com.google.code.gson:gson:2.15.0")
    testImplementation("com.google.code.gson:gson:2.15.0")
    // Provided by Paper / Fabric / NeoForge at runtime; keep compileOnly so
    // platform shadow jars do not embed a second SLF4J (LinkageError).
    compileOnly("org.slf4j:slf4j-api:2.0.17")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
    // Plain-text lock-reason stripping for SharedLockEndApiProvider (Adventure
    // is not a Minecraft dependency; Paper also ships 5.2.0 at runtime).
    implementation("net.kyori:adventure-text-minimessage:5.2.0")
}
