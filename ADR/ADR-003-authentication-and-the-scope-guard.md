# ADR-003 — Authentication: two hosts, lazy tokens, and a scope guard

**Status:** Accepted
**Date:** 2026-07-26

## Context

Three things about eparagony.pl's OAuth 2 implementation were established by probing the live sandbox
on 2026-07-26. All three contradict the published specification, and all three fail quietly.

**The token endpoint is on a different host.** `securitySchemes.oauth-cc.flows.clientCredentials`
advertises `tokenUrl: https://api.eparagony.pl/auth/token`. That URL answers `404`. Authentication
lives on a separate `login.` host: `https://login.sandbox.eparagony.pl/auth/token` answers `200`, and
`https://login.eparagony.pl/auth/token` answers `401` to bad credentials, which is how we know the
production endpoint exists and validates. The specification's own `Host` parameter lists both
`api.eparagony.pl` and `login.eparagony.pl` as examples, so the `tokenUrl` field is simply wrong.

**The scope separator is a space, not a comma.** `AuthTokenPayload.scope` is documented as
"Requested scopes, separated by comma". The server expects the RFC 6749 space-separated list.

**Neither mistake produces an error.** This is the part that matters. Given a scope string it does not
recognise — a comma-joined list, which it reads as one opaque scope name, or a typo — the server
answers **HTTP 200**, omits `scope` from the response, and issues a token. Every endpoint then
rejects that token with `403 {"statusCode":403,"error":"Forbidden","message":"Access denied"}`, a
message that mentions neither scopes nor the token. Measured behaviour:

| `scope` sent | HTTP | `scope` returned | Usable |
|---|---|---|---|
| `document_create` | 200 | `document_create` | yes |
| `document_create printer_get` | 200 | `document_create printer_get` | yes |
| `document_create document_action_get` (not entitled) | **400** `invalid_scope` | names the offender | correct failure |
| `document_create,printer_get` (comma, both entitled) | **200** | **absent** | **no — every call 403s** |
| `nonsense_scope` | **200** | **absent** | **no — every call 403s** |

The natural reading of the documentation therefore produces a client that authenticates successfully
and then fails everywhere, with nothing in either failure pointing at the cause.

The API also warns that minting a token per request causes throttling.

## Decision

1. **Two base URLs, not one.** `Environment` carries `authBaseUrl` and `apiBaseUrl` as separate
   values. A single-base-URL client cannot authenticate against this API, so the type makes that
   impossible to express.
2. **Scopes are joined with a space**, and a regression test asserts the encoded body contains no
   `%2C` in the scope parameter.
3. **The token response is validated before use.** `TokenManager` refuses a token whose granted
   `scope` does not cover what was requested, and raises `EparagonyAuthException` naming the missing
   scopes and explaining why the token was refused. A token that would produce an unexplainable 403
   never leaves the token manager.
4. **Authentication is lazy and the token is cached** until 60 seconds before expiry, with
   single-flight acquisition so a burst of concurrent first calls mints one token rather than many.
5. **One re-authentication on 401**, once per call. Beyond that a 401 means the credential is wrong,
   and retrying is the behaviour the server throttles.
6. `Credentials` is a sealed interface permitting `ClientCredentials`. The API publishes one grant;
   sealing leaves room for a second without letting a consumer invent one.

## Consequences

- A misconfigured scope fails at the token call, in the caller's stack, with the offending scope
  named — instead of at an arbitrary later endpoint with `Access denied`.
- Requesting a superset of granted scopes fails the whole token (the server rejects it with
  `invalid_scope`), so `EparagonyConfig.scopes()` must name only what the client is actually granted.
  This is documented on the builder method.
- Refusing an under-granted token is a deliberate choice to fail early. If eparagony.pl ever starts
  omitting `scope` from a legitimate response, this guard would reject a working token — which is why
  the guard's message states exactly what it saw and what it expected.
- Client secrets and token values are redacted in `toString()`. They reach log files by accident far
  more often than by intent.
