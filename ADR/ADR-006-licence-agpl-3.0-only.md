# ADR-006 — Licence: AGPL-3.0-only

**Status:** Accepted
**Date:** 2026-07-26

## Context

The repository is public so that the work is visible and usable. That is not the same as wanting it
absorbed into a closed commercial product without conversation.

## Decision

AGPL-3.0-only, matching the sibling SDKs in this family (`ksef-java-sdk`, `allegro-java-sdk`,
`erli-java-sdk`).

The network clause is the point. A hosted service that integrates this SDK and offers it to third
parties is required to publish its own source. For a fiscal-integration library that is a meaningful
deterrent to silent commercial reuse, and it leaves the door open to a negotiated dual licence.

Every source file carries the licence header, enforced by Spotless.

## Consequences

- Commercial use in a closed product requires a separate licence from the copyright holder.
- External contributions need operator sign-off and copyright assignment, or dual licensing becomes
  impossible later.
- The repository is public with `develop` as the default branch; `main` carries only tagged releases.
