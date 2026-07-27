# ADR-002 — Three layers, JPMS, Java 17

**Status:** Accepted
**Date:** 2026-07-26

## Context

An SDK's public surface is a promise. Anything a consumer can import, they will import, and anything
they import becomes something we cannot change. Generated transport models are the classic leak: they
are regenerated from a spec we do not control, so promising them is promising something somebody else
gets to change.

## Decision

Three layers, with the boundary enforced by the module system rather than by convention.

| Layer | Package | Exported | Contents |
|---|---|---|---|
| 3 — public | `eparagony`, `core.*`, `domain.*` | yes | entry point, config, auth, errors, retry, value types, webhook verification, typed facades |
| 2 — internal | `internal.*` | **no** | `HttpRuntime`, `TokenManager`, `JsonCodec`, `ErrorMapper`, `ApiPaths`, per-area `*Impl` and mappers |
| 1 — generated | `rest.model.*` | **no** | transport POJOs generated from the vendored spec |

Consumers import `sdk.domain.*` and `sdk.core.*`, and nothing else. Facades are interfaces; their
implementations live in Layer 2 and are never named in a public signature. Jackson and the generated
models are `implementation` dependencies, so they do not even appear on a consumer's compile
classpath.

The baseline is Java 17: records, sealed types and switch expressions carry the domain model, and 17
is the oldest LTS that has all three.

## Consequences

- `module-info.java` is the enforcement point, and adding a domain area means adding an `exports`
  line — a deliberate act, not a side effect.
- A separate `eparagony-jpms-consumer` module compiles against the named-module surface, so a leak
  fails the build rather than being discovered by a consumer.
- `HttpClient.close()` is Java 21; on 17 the client's connections are reclaimed by the garbage
  collector. `EparagonyClient` is still `AutoCloseable` so that consumer code reads correctly today
  and keeps working when the baseline moves.
- Anything that must be shared between domain areas belongs in `core`, not copied.
