rootProject.name = "eparagony-java-sdk"

// Reactor.
//   eparagony-rest-models   — Layer 1, generated transport POJOs (internal, never exported).
//   eparagony-client        — the SDK: sdk.core + internal transport + sdk.domain facades (published).
//   eparagony-demo          — live end-to-end runner against the sandbox (not published).
//   eparagony-examples      — compile-only consumer snippets (documentation, not published).
//   eparagony-jpms-consumer — modular consumer that proves internal/*Raw packages do not leak.
// See ADR/ADR-002-three-layers-jpms-java17.md.
include("eparagony-rest-models")
include("eparagony-client")
include("eparagony-demo")
include("eparagony-examples")
include("eparagony-jpms-consumer")
