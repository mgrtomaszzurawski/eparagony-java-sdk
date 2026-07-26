// eparagony-client — the published SDK. Holds the three hand-written layers:
//   sdk.core     (public)  — auth, config, errors, retry, shared value types, webhook verification
//   internal     (hidden)  — HttpRuntime over java.net.http, token management, JSON codec, mapping
//   sdk.domain.* (public)  — typed facades; consumers import only these plus sdk.core
// Layer 1 (eparagony-rest-models) and Jackson are `implementation` deps: they are transport details
// and must never appear in an exported signature (the JPMS-consumer module enforces this). ADR-002.

plugins {
    `java-library`
    jacoco
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withSourcesJar()
}

jacoco {
    toolVersion = "0.8.12"
}

dependencies {
    // Layer 1 raw models + Jackson stay internal to this module (never re-exported to consumers).
    implementation(project(":eparagony-rest-models"))
    implementation(libs.jackson.databind)
    // Declared directly because JsonCodec imports JavaTimeModule and module-info `requires` it. It
    // also arrives transitively through eparagony-rest-models' `api`, but relying on that would break
    // this module the day Layer 1 narrows that dependency.
    implementation(libs.jackson.datatype.jsr310)
    // Same reasoning for the nullable module: `nullable: true` properties are generated as
    // JsonNullable<T>, and JsonCodec registers the module that decodes them.
    implementation(libs.jackson.databind.nullable)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    // WireMock drives HttpRuntime tests against a real local HTTP server (verify-on-write).
    testImplementation(libs.wiremock.standalone)
}

tasks.test {
    // Unit tests only. Live @Tag("e2e") tests hit the sandbox and run via the e2eTest task below.
    useJUnitPlatform {
        excludeTags("e2e")
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(
        classDirectories.files.map { classDir ->
            fileTree(classDir) { exclude("module-info.class") }
        }
    )
}

// Live end-to-end tests against the eparagony.pl sandbox. Credentials come from the environment
// (EPARAGONY_CLIENT_ID / EPARAGONY_CLIENT_SECRET / EPARAGONY_POS_ID); each e2e test self-skips when
// they are absent. Run: `./gradlew :eparagony-client:e2eTest`.
tasks.register<Test>("e2eTest") {
    description = "Runs @Tag(\"e2e\") live-sandbox tests (requires EPARAGONY_* credentials)."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("e2e")
    }
    // Always re-run: results depend on live server state, not just inputs.
    outputs.upToDateWhen { false }
}
