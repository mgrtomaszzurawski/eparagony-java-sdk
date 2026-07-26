// Root build. Holds shared coordinates, repositories and the quality gates; each module applies its
// own plugins on top. Every gate here is documented in the binding CLAUDE.md — an undocumented gate
// does not get run, which is how two gates on a sibling project went unexecuted for six weeks.

plugins {
    java
    alias(libs.plugins.spotless)
    alias(libs.plugins.spotbugs) apply false
    alias(libs.plugins.sonarqube)
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

// The generated module is linted by nobody: its sources are rewritten from the vendored spec on
// every build, so a violation there is not actionable and a fix would not survive.
val handWrittenModules = listOf(
    "eparagony-client",
    "eparagony-demo",
    "eparagony-examples",
    "eparagony-jpms-consumer",
)

spotless {
    java {
        target("*/src/**/*.java")
        targetExclude("**/build/**", "**/module-info.java")
        licenseHeaderFile(rootProject.file("config/spotless/license-header.txt"))
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

configure(subprojects.filter { it.name in handWrittenModules }) {
    apply(plugin = "checkstyle")
    apply(plugin = "pmd")
    apply(plugin = "com.github.spotbugs")

    configure<CheckstyleExtension> {
        toolVersion = "10.21.1"
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        isIgnoreFailures = false
        maxWarnings = 0
    }

    configure<PmdExtension> {
        toolVersion = "7.8.0"
        ruleSetFiles = rootProject.files("config/pmd/ruleset.xml")
        // An empty ruleSets list is required, otherwise Gradle adds its own default on top of ours.
        ruleSets = emptyList()
        isIgnoreFailures = false
    }

    configure<com.github.spotbugs.snom.SpotBugsExtension> {
        excludeFilter.set(rootProject.file("config/spotbugs/exclude.xml"))
        ignoreFailures.set(false)
    }

    tasks.withType<Checkstyle>().configureEach {
        // module-info carries no style-checkable content and trips the package-name rule.
        exclude("**/module-info.java")
        reports {
            xml.required.set(false)
            html.required.set(true)
        }
    }

    tasks.withType<Pmd>().configureEach {
        // PMD 7 cannot parse `requires static transitive` in a module declaration.
        exclude("**/module-info.java")
        reports {
            xml.required.set(false)
            html.required.set(true)
        }
    }

    tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
        reports.create("html") { required.set(true) }
        reports.create("xml") { required.set(false) }
    }

    // Style and static analysis gate the main sources. Test sources are reviewed, not linted: a
    // ruleset tuned for a public API surface produces mostly noise on test scaffolding.
    tasks.matching { it.name in setOf("checkstyleTest", "pmdTest", "spotbugsTest") }
        .configureEach { enabled = false }
}

sonar {
    properties {
        property("sonar.projectKey", "eparagony-java-sdk")
        property("sonar.projectName", "eparagony-java-sdk")
        // Layer 1 is generated from the vendored spec; measuring it would report a codebase nobody
        // wrote and drown the hand-written surface in its numbers.
        property("sonar.exclusions", "**/build/generated/**,**/rest/model/**,**/rest/invoker/**")
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            "${rootDir}/eparagony-client/build/reports/jacoco/test/jacocoTestReport.xml",
        )
    }
}
