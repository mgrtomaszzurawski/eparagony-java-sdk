// The JPMS leak gate. A modular consumer that compiles against eparagony-client's NAMED MODULE
// surface rather than its classpath. Anything the SDK forgets to export — or accidentally exposes
// through an exported signature — fails compilation here rather than reaching a consumer.
//
// This module ships nothing. It exists to fail the build. Never published.

plugins {
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation(project(":eparagony-client"))
}

tasks.withType<JavaCompile>().configureEach {
    // Force the module path. Without this Gradle puts the SDK on the classpath, every package becomes
    // readable, and this module proves nothing at all.
    modularity.inferModulePath.set(true)
}
