rootProject.name = "eparagony-java-sdk"

// Reactor.
//   eparagony-rest-models   — Layer 1, generated transport POJOs (internal, never exported).
//   eparagony-client        — the SDK: sdk.core + internal transport + sdk.domain facades (published).
//   eparagony-demo          — RESERVED, no sources yet. Intended as a live sandbox runner; the live
//                             coverage that would justify it currently lives in the client module's
//                             @Tag("e2e") suite.
//   eparagony-examples      — RESERVED, no sources yet. Intended for compile-checked consumer
//                             snippets; the README snippets are hand-verified until then.
//   eparagony-jpms-consumer — modular consumer that proves the internal and generated packages do not leak.
// See ADR/ADR-002-three-layers-and-jpms.md.
include("eparagony-rest-models")
include("eparagony-client")
include("eparagony-demo")
include("eparagony-examples")
include("eparagony-jpms-consumer")
