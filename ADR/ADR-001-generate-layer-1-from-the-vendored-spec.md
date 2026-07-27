# ADR-001 — Generate Layer 1 from the vendored specification

**Status:** Accepted
**Date:** 2026-07-26

## Context

eparagony.pl publishes an OpenAPI 3.0.0 document for the Documents API v3 at
`https://docs.eparagony.pl/redocusaurus/documents_en_v3.yaml`. It is small in surface (seven
operations) and large in payload (118 component schemas), because the weight of the API is the fiscal
document model, not the endpoint count.

Two things about that document are load-bearing for this decision.

First, it declares `openapi: 3.0.0` but uses the 3.1 keyword `const`. Every child of a discriminated
parent narrows the discriminator property with `{type: string, const: <VALUE>}` while the parent
declares it as a typed `enum`. `openapi-generator` ignores `const`, so it generates the child's getter
returning `String` over a parent getter returning the enum — an illegal override. Seven schemas across
`Ticket` and `PDCorrectiveInvoice` hit this, and the module does not compile.

Second, the API's own integration guide states that the contract is not rigid and that new fields may
be added without prior warning.

## Decision

Generate the Layer-1 transport models from the specification. Vendor the spec at `openapi/`, never
hand-edit it, and never commit generated sources.

Where the generator cannot consume the spec as published, fix it in a **build-only normalization
step** that writes a transformed copy, leaving the vendored file pristine. The step is narrow by
construction: it removes a child's redeclaration of an inherited discriminator property **only** when
that redeclaration is a pure `const` narrowing. A child that redefines the property any other way is
left alone and must be handled deliberately.

Do not wrap a vendor SDK. There is none for this API, and reimplementing the transport ourselves is
what lets the SDK own its retry, error mapping and authentication behaviour.

## Consequences

- The vendored spec is source-of-truth and must never appear in a diff. `git diff --name-only` before
  every commit.
- Regenerating against a newer spec is a spec swap, not a merge.
- The normalization step logs exactly what it changed on every build, so a silent divergence between
  the published contract and the generated code is not possible.
- Because unknown properties are ignored on read (see ADR-003), a server-side field addition is a
  non-event for deployed consumers rather than an outage.
- Layer 1 is not linted, not covered by JaCoCo and excluded from Sonar. It is not ours to fix.
