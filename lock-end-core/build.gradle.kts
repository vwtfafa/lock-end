plugins {
    `java-library`
    id("lockend.java-conventions")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":lock-end-api"))
}
