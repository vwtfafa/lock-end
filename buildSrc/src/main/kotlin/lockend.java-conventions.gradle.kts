plugins {
    java
    checkstyle
    id("com.github.spotbugs")
}

group = providers.gradleProperty("lockEndGroup").get()
version = providers.gradleProperty("lockEndVersion").get()

val lockEndJavaVersion = providers.gradleProperty("lockEndJavaVersion").get().toInt()

java {
    val javaVersion = JavaVersion.toVersion(lockEndJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion

    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(lockEndJavaVersion))
    }
}

// Gradle 9+ no longer injects the JUnit platform launcher automatically
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:${providers.gradleProperty("junitVersion").get()}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:${providers.gradleProperty("junitVersion").get()}")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"

    if (lockEndJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
        options.release.set(lockEndJavaVersion)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

checkstyle {
    toolVersion = providers.gradleProperty("checkstyleVersion").get()
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
}

// Static analysis targets production code; unit tests are exempt.
tasks.named("spotbugsTest") {
    enabled = false
}

spotbugs {
    toolVersion.set(providers.gradleProperty("spotbugsVersion").get())
    ignoreFailures.set(false)
    effort.set(com.github.spotbugs.snom.Effort.MAX)
    reportLevel.set(com.github.spotbugs.snom.Confidence.LOW)
    reportsDir.set(layout.buildDirectory.dir("reports/spotbugs"))
    includeFilter.set(rootProject.file("config/spotbugs/spotbugs.xml"))
}
