plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
    id("com.gradleup.shadow") version "9.6.1"
    checkstyle
    id("com.github.spotbugs") version "6.5.11"
}

group = "org.vwtfafa"
version = "2.0.0"

val paperApiVersion = "26.2.build.112-stable"

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
    gradlePluginPortal()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    compileOnly("me.clip:placeholderapi:2.12.3")
    implementation("org.bstats:bstats-bukkit:3.2.1")
    // Adventure (components, MiniMessage) is provided by paper-api at runtime
    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    // Gradle 9+ no longer injects the platform launcher automatically
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks {
    runServer {
        minecraftVersion("26.2")
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
            // Only merge bStats into the final jar, no other dependencies
            exclude { it.moduleGroup != "org.bstats" }
        }

        // Relocate bStats into the plugin's package to avoid conflicts with other
        // plugins using bStats
        relocate("org.bstats", project.group.toString())
    }
}

val targetJavaVersion = 25

java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion

    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"

    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
        options.release.set(targetJavaVersion)
    }
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"

    filesMatching("plugin.yml") {
        expand(props)
    }
}

checkstyle {
    toolVersion = "10.12.4"
    configFile = file("config/checkstyle/checkstyle.xml")
}

spotbugs {
    toolVersion.set("4.10.3")
    ignoreFailures.set(false)
    effort.set(com.github.spotbugs.snom.Effort.MAX)
    reportLevel.set(com.github.spotbugs.snom.Confidence.LOW)
    reportsDir.set(file("build/reports/spotbugs"))
    includeFilter.set(file("config/spotbugs/spotbugs.xml"))
}

// Static analysis targets production code; unit tests are exempt.
tasks.named("spotbugsTest") {
    enabled = false
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
