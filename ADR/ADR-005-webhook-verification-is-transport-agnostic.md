# ADR-005 — Webhook verification takes raw bytes and knows nothing about HTTP

**Status:** Accepted
**Date:** 2026-07-26

## Context

eparagony.pl signs every webhook notification with `X-Signature`: an HMAC-SHA256 of the request body,
keyed with a per-integration `webhookSecret`, hex-encoded. The signature is how a consumer knows a
fiscalization notification is genuine rather than forged by anyone who guessed their callback URL.

The API's own FAQ lists the failure everybody hits: computing the digest over a re-serialized body.
Parsing the JSON and serializing it again shifts key order and whitespace, the digest changes, and
every legitimate notification starts failing verification.

Separately, this project cannot exercise webhooks end-to-end from its build environment — there is no
public ingress — so the design must be one that a later verification run confirms rather than
redesigns.

## Decision

`WebhookVerifier.verify(byte[] rawBody, String signatureHeader)`.

**Bytes, not a String, and deliberately no overload that takes one.** An API that accepts a parsed
object or a re-serialized string invites exactly the mistake the FAQ warns about. Making the raw form
the only form pushes the requirement to the point where it can still be satisfied — capture the body
before the framework touches it.

**No HTTP framework dependency.** No servlet, no Spring, no Lambda types. The verifier takes a byte
array and a header value from whatever is receiving requests. The same code therefore runs in a
servlet filter, a Lambda handler, a Ktor route and a unit test.

**Constructed from a secret, not from the client.** `EparagonyClient.webhookVerifier(WebhookSecret)`
is static. Verifying notifications and issuing documents are different concerns needing different
secrets, and the process that receives webhooks is frequently not the one that issues documents.
Nothing should require API credentials to check a signature.

Comparison is constant-time via `MessageDigest.isEqual`. A byte-by-byte early exit leaks, through
timing, how much of a guessed signature was correct.

## What the signature does not prove

Origin, not freshness. The notification carries no timestamp and no nonce, so a captured request
replays perfectly and verifies perfectly. That is a property of the API, not of this SDK, and it
cannot be fixed on the client side.

Handlers must therefore be **idempotent**: deduplicate on `documentToken`, or on `actionId` for action
notifications, and treat a repeat as the same event. A handler that emails the customer their receipt
on every verified notification will email it again for every replay.

## Consequences

- Consumers must capture the raw body themselves. This is a real burden in some frameworks, and it is
  the correct one — the alternative is a verifier that silently rejects valid notifications.
- The mismatch message names the re-serialization trap explicitly, because that is what the failure
  will usually be.
- Verification is fully unit-tested offline, including tampering, a foreign secret, malformed headers
  and a re-serialized body. Live webhook delivery remains unverified until a public endpoint exists;
  it is recorded as a known gap rather than claimed as covered.
- The polling endpoint and the webhook carry the same payload shape but **different status sets** —
  `PENDING` is polling-only, `READY` is webhook-only. `DocumentState` documents which channel emits
  which, and unknown values map to `UNKNOWN` rather than throwing, because dropping a fiscal
  notification over an unfamiliar enum constant is worse than handling it generically.
