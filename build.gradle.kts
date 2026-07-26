// Root build. Holds shared coordinates and repositories; each module applies its own plugins.
// Quality gates (Spotless, Checkstyle, PMD, SpotBugs, JaCoCo, Sonar) are documented in the binding
// CLAUDE.md before they are wired — an undocumented gate does not get run.

plugins {
    java
}

allprojects {
    group = "io.github.mgrtomaszzurawski"

    repositories {
        mavenCentral()
    }

    // Generated Layer-1 sources carry Polish text from the spec descriptions; compile as UTF-8.
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
