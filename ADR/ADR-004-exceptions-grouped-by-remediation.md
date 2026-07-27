# ADR-004 — Exceptions grouped by remediation

**Status:** Accepted
**Date:** 2026-07-26

## Context

A one-exception-per-HTTP-status hierarchy pushes the mapping work onto every consumer: they catch a
`Http400Exception` and then have to know what a 400 means for this endpoint. A single
`EparagonyException` pushes it further, into string matching.

## Decision

Group by what the caller does about it. The test for whether two failures belong together is: *what
would the consumer do differently if they caught this rather than its parent?* Same answer, same type.

| Type | Trigger | Remediation |
|---|---|---|
| `EparagonyConfigurationException` | assembly-time invalid config | fix and restart; raised at construction, never mid-call |
| `EparagonyAuthException` | 401, rejected credentials, un-granted scope | fix credentials or have the scope granted |
| `EparagonyAccessDeniedException` | 403 | check scope coverage, `posId` ownership, document ownership |
| `EparagonyValidationException` | 400, carries `errorCode` | correct the payload |
| `EparagonyIdempotencyException` | 422 | supply a key, or a fresh one for a changed payload |
| `EparagonyNotFoundException` | 404 | check the identifier |
| `EparagonyServerException` | 5xx, network failure, timeout | back off and retry; check `requestMayHaveBeenApplied()` |
| `WebhookSignatureException` | local signature mismatch | reject the notification; do not retry, do not process |

Two consequences of the grouping deserve naming.

**5xx, network failure and timeout are one type.** The remediation is identical — wait and try again —
so splitting them would ask for three catch blocks with the same body. What the caller *does* need is
whether the request may already have taken effect, which is a boolean on the type
(`requestMayHaveBeenApplied()`), not a separate class. A timeout on `POST /documents` is the dangerous
case: the sale may already be fiscalized, and reissuing under a fresh idempotency key would fiscalize
it twice.

**403 is separate from 401** because the credential is fine and something else is wrong. Note that
this API answers 403, not 404, for a document belonging to another client — so "not found" and "not
yours" are indistinguishable from outside, and `EparagonyNotFoundException`'s absence is not proof a
document exists.

All types are unchecked. A fiscal document is issued from application code that already has an error
path; checked exceptions on every call would be noise.

## Consequences

- The transport layer is invisible from the domain. No `IOException`, no `HttpResponse`, no Jackson
  type reaches a consumer.
- `EparagonyValidationException` surfaces the server's numeric `errorCode` as a raw number rather than
  an enum. The codes are not enumerated in the specification, and an enum the SDK guessed at would be
  wrong the first time a new code appeared.
- Adding a status mapping is a change to `ErrorMapper` alone.
