# Testing conventions

Binding. A test that does not follow these is not evidence of anything.

## The three layers of evidence

| Layer | Task | What it proves |
|---|---|---|
| Unit | `test` | a rule holds — reconciliation, jitter bounds, HMAC, path encoding |
| Contract (WireMock) | `test` | the SDK sends and reads the wire shape **the author believes in** |
| Live sandbox | `e2eTest` | that belief is correct |

The third layer exists because the second cannot replace it. A WireMock stub asserts the author's
guess about the server; if the guess is wrong, the suite is green and the SDK is broken. Every
wire-touching change needs live evidence before it is merge-ready.

## Rules

**Prove a new test fails without its fix.** Revert the change, watch it go red, restore. A test that
was green before the fix is testing nothing.

**Verify on writes.** `assertDoesNotThrow` around a POST is false green. Assert what was sent:

```java
server.verify(1, postRequestedFor(urlPathEqualTo("/documents"))
        .withRequestBody(equalToJson(...))
        .withHeader("Idempotency-Key", matching(UUID_PATTERN)));
```

**Pin the whole request body where the shape is the contract.** `IssueReceiptTest` pins the full
receipt JSON, because every field placement in it is a v3 migration trap: `print`/`fiscalize` moved
inside `eReceipt`, `fiscalizationStatusUrl` became `statusUrl`, quantity is a string, amounts are
integer grosze. An `ignoreExtraElements` match would let any of those regress silently.

**Fix the code, not the assertion.** Two defects were found this way and both were fixed in the SDK:
scope order was non-deterministic because `Set.copyOf` randomizes iteration, and the codec was putting
six empty arrays on the wire that the caller had never populated. Either could have been "fixed" by
loosening a test.

**Assert on caller-supplied data in the sandbox; never on device-side values.** The emulator returns
the same device number, document number, receipt number and timestamp for every document ever issued.
See `docs/KNOWN-SERVER-BEHAVIORS.md`.

**No `@Disabled` in a merged branch.** A disabled test is a defect with a note attached.

**Cover the error path explicitly.** Every status the SDK maps has a test asserting the exception type
its remediation implies — not merely that something was thrown.

## Running

```bash
./gradlew test                        # unit + contract; e2e excluded by tag
./gradlew check                       # the above plus Spotless, Checkstyle, PMD, SpotBugs, JaCoCo
./gradlew :eparagony-client:e2eTest   # live sandbox
```

`e2eTest` reads `EPARAGONY_CLIENT_ID`, `EPARAGONY_CLIENT_SECRET` and `EPARAGONY_POS_ID` from the
environment and **self-skips when they are absent**. A skip is not a pass: check the report for
`skipped="0"` before claiming live coverage.

Credentials are supplied through those environment variables and never committed. Where they are
stored is a deployment concern and deliberately not recorded in a public repository.

## Current coverage, honestly

Instruction 80%, line 82%, branch 64%, method 80%, class 100%. No floor is enforced yet; a ratchet
lands once the baseline has settled rather than being set to whatever today happens to be.

Live-verified: `POST /auth/token`, `POST /documents`, `GET /documents/{token}/status`,
`GET /printers/{device}/status`.

Not live-verified, and why:

| Endpoint / feature | State | Blocker |
|---|---|---|
| `GET /documents/{token}/actions/status` | **not implemented** | `document_action_get` not granted |
| `GET /documents/{token}/jws` | **not implemented** | `document_get_jws` not granted |
| `GET /printers/{device}/reports/daily` | **not implemented** | `report_fiscal_get` not granted |
| Document status webhook | implemented, WireMock + unit only | no public ingress from the build environment |

Four of seven endpoints are proven on the wire. The other three are **not implemented at all** — only
their `ApiPaths` constants and `Scope` values exist — so calling them WireMock-covered would be false.
The webhook path is implemented and covered offline against a real HMAC secret, but never exercised by
a real delivery.

Stating this plainly matters more than a coverage percentage.
