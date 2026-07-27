// Layer 1 — transport models generated from openapi/documents-v3.yaml (Jackson "native" library).
// Generated sources are NOT committed; the vendored spec is source-of-truth and never hand-edited.
// Only the generated transport POJOs live here; they are never exported to consumers.

import groovy.json.JsonOutput
import org.yaml.snakeyaml.Yaml

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // Used only by the normalizeSpec task below to read the vendored YAML spec.
        classpath("org.yaml:snakeyaml:2.3")
    }
}

plugins {
    `java-library`
    alias(libs.plugins.openapi.generator)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    api(libs.jackson.databind)
    api(libs.jackson.datatype.jsr310)
    api(libs.jackson.databind.nullable)
    compileOnly(libs.jakarta.annotation.api)
}

val vendoredSpec = rootProject.file("openapi/documents-v3.yaml")
val normalizedSpec = layout.buildDirectory.file("spec/documents-v3.normalized.json")
val generatedRoot = layout.buildDirectory.dir("generated/openapi")

// The vendored spec is upstream's and is never hand-edited. It declares `openapi: 3.0.0` but uses
// the 3.1 keyword `const` in one specific place: every child of a discriminated parent redeclares
// the discriminator property as `{type: string, const: <VALUE>}`, while the parent declares it as a
// typed `enum`. openapi-generator ignores `const`, so the child's getter is generated returning
// String while the parent's returns the enum — an illegal override, and the module does not compile
// (7 occurrences across Ticket and PDCorrectiveInvoice subtypes).
//
// This step writes a build-only normalized copy that drops exactly those child redeclarations. The
// property itself survives — the parent still declares it, with its enum type — and the
// discriminator mapping still drives polymorphic (de)serialization. Nothing else is touched, and
// the vendored spec stays pristine. See ADR/ADR-001-generate-layer-1-from-the-vendored-spec.md.
val normalizeSpec by tasks.registering {
    inputs.file(vendoredSpec)
    outputs.file(normalizedSpec)
    doLast {
        @Suppress("UNCHECKED_CAST")
        val root = Yaml().load<Map<String, Any?>>(vendoredSpec.readText()) as MutableMap<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val schemas = ((root["components"] as? Map<String, Any?>)?.get("schemas") as? Map<String, Any?>)
            ?: emptyMap()

        fun discriminatorPropertyOf(schema: Any?): String? {
            @Suppress("UNCHECKED_CAST")
            val map = schema as? Map<String, Any?> ?: return null
            @Suppress("UNCHECKED_CAST")
            val discriminator = map["discriminator"] as? Map<String, Any?> ?: return null
            return discriminator["propertyName"] as? String
        }

        val dropped = mutableListOf<String>()

        for ((schemaName, schema) in schemas) {
            @Suppress("UNCHECKED_CAST")
            val schemaMap = schema as? Map<String, Any?> ?: continue
            val branches = schemaMap["allOf"] as? List<*> ?: continue

            // Discriminator properties owned by every parent this schema extends.
            val inheritedDiscriminators = branches.mapNotNull { branch ->
                @Suppress("UNCHECKED_CAST")
                val reference = (branch as? Map<String, Any?>)?.get("\$ref") as? String
                discriminatorPropertyOf(reference?.substringAfterLast('/')?.let { schemas[it] })
            }.toSet()

            if (inheritedDiscriminators.isEmpty()) {
                continue
            }

            for (branch in branches) {
                @Suppress("UNCHECKED_CAST")
                val properties = (branch as? Map<String, Any?>)?.get("properties") as? MutableMap<String, Any?>
                    ?: continue
                for (discriminatorProperty in inheritedDiscriminators) {
                    @Suppress("UNCHECKED_CAST")
                    val declaration = properties[discriminatorProperty] as? Map<String, Any?> ?: continue
                    // Only a pure `const` narrowing is removed. A child that genuinely redefines the
                    // property some other way is left alone and must be handled deliberately.
                    if (declaration.containsKey("const")) {
                        properties.remove(discriminatorProperty)
                        dropped += "$schemaName.$discriminatorProperty=${declaration["const"]}"
                    }
                }
            }
        }

        logger.lifecycle("normalizeSpec: dropped ${dropped.size} const discriminator redeclaration(s): $dropped")
        val target = normalizedSpec.get().asFile
        target.parentFile.mkdirs()
        target.writeText(JsonOutput.toJson(root))
    }
}

openApiGenerate {
    generatorName = "java"
    inputSpec = normalizedSpec.get().asFile.toString()
    outputDir = generatedRoot.get().asFile.toString()

    // The upstream spec has minor validation gaps (see ADR-001). We never hand-edit it, so
    // validation is skipped here and covered by tests plus the live wire instead.
    validateSpec.set(false)
    skipValidateSpec.set(true)

    modelPackage = "io.github.mgrtomaszzurawski.eparagony.rest.model"
    apiPackage = "io.github.mgrtomaszzurawski.eparagony.rest.api"
    invokerPackage = "io.github.mgrtomaszzurawski.eparagony.rest.invoker"

    generateApiTests = false
    generateApiDocumentation = false
    generateModelTests = false
    generateModelDocumentation = false
    // Selective generation: models + their supporting runtime (JSON helper, AbstractOpenApiSchema,
    // date formats). NO per-endpoint api-client classes — the SDK reimplements transport itself
    // (ADR-002). Listing "models"+"supportingFiles" but not "apis" excludes apis.
    globalProperties = mapOf(
        "models" to "",
        "supportingFiles" to "",
        "modelDocs" to "false",
    )

    configOptions = mapOf(
        "library" to "native",
        "useJakartaEe" to "true",
        "openApiNullable" to "true",
        "serializationLibrary" to "jackson",
        "hideGenerationTimestamp" to "true",
        "sourceFolder" to "src/main/java",
    )
}

sourceSets {
    named("main") {
        java.srcDir(generatedRoot.map { it.dir("src/main/java") })
    }
}

tasks.named("compileJava") {
    dependsOn(tasks.named("openApiGenerate"))
}

// Layer 1 ships without a module-info (generated POJOs), so it is consumed as an automatic module.
// Pin its name so eparagony-client's module-info can `requires` it deterministically instead of
// relying on the jar-filename derivation. The package is still never re-exported (JPMS-internal).
tasks.named<Jar>("jar") {
    manifest {
        attributes("Automatic-Module-Name" to "io.github.mgrtomaszzurawski.eparagony.rest.models")
    }
}

tasks.named("openApiGenerate") {
    dependsOn(normalizeSpec)
}
