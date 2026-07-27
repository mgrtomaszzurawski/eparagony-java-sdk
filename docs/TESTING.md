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
./gradlew sonar --no-configuration-cache \
    -Dsonar.host.url=$SONAR_HOST_URL \
    -Dsonar.login=$SONAR_LOGIN -Dsonar.password=$SONAR_PASSWORD
```

`sonar` must run **after** `jacocoTestReport`, or the analysis reports zero coverage. `check`
produces that report, so running `check` then `sonar` is the correct order.

### Last recorded gate run

| Gate | Result |
|---|---|
| Spotless / Checkstyle / PMD / SpotBugs | 0 violations |
| JUnit (unit + contract) | 498 tests, 0 skipped, 0 failures |
| JaCoCo | instruction 83%, line 84%, method 83%, class 95% |
| Live sandbox `e2eTest` | 3 tests, **0 skipped**, 0 failures |
| SonarQube | 0 bugs, 0 vulnerabilities, 0 hotspots, 0 open smells, coverage 77%, A/A/A |

Two Sonar findings carry a recorded decision rather than a fix, and both are visible on the board with
their justification: the retry loop's multiple `continue` statements (marked won't-fix — collapsing
them needs flag variables that hide which outcome occurred), and the jitter RNG (reviewed as safe —
nothing is derived from that value and nothing is protected by its unpredictability).

`e2eTest` reads `EPARAGONY_CLIENT_ID`, `EPARAGONY_CLIENT_SECRET` and `EPARAGONY_POS_ID` from the
environment and **self-skips when they are absent**. A skip is not a pass: check the report for
`skipped="0"` before claiming live coverage.

Credentials are supplied through those environment variables and never committed. Where they are
stored is a deployment concern and deliberately not recorded in a public repository.

## Field depth

Endpoint coverage says which operations exist. It says nothing about how much of each payload the SDK
can actually express, and a facade-method self-count cannot tell you either. A deterministic tool on
the agent volume measures it: spec leaf fields as the denominator, mapper bytecode as the numerator.

| Payload root | Mapped | Leaves | Depth |
|---|---|---|---|
| `CreateReceiptDocumentPayload` | 126 | 126 | **100%** |
| `CreateGenericDocumentPayload` | 90 | 101 | 89% |
| `CreateCorrectiveInvoiceDocumentPayload` | 11 | 13 | 85% |
| `CreateTicketReceiptDocumentPayload` | 71 | 84 | 85% |
| `CreateVatInvoiceDocumentPayload` | 74 | 158 | 47% |
| `CreateSettlementInvoiceDocumentPayload` | 75 | 161 | 47% |
| `CreateAdvancePaymentInvoiceDocumentPayload` | 70 | 155 | 45% |
| response payloads (3) | 8 | 8 | 100% |
| **total** | **525** | **806** | **65%** |

An upper bound, deliberately reported as one: the generated Layer-1 classes are shared between
document types, so a field mapped for a receipt counts wherever that same field appears. The receipt
figure is the trustworthy one — it is the type with a hand-written builder for every leaf.

**A depth figure alone is not enough, and `FullReceiptPayloadTest` is why.** The tool measures that a
mapper *invokes* each Layer-1 setter; it cannot see whether the value handed to it is one the server
accepts. That test pins the full wire body of a receipt using every optional structure, and on its
first run it caught three mappings the depth number had already scored as covered: the QR
discriminator is `QR` and not `QR_CODE`, a barcode line has no `BARCODE` type at all (the symbology
*is* the type, one of nineteen), and warranty period units are upper-case. Any payload work on the
remaining document types needs the same pinned-body test beside it.

The non-receipt document types have no domain builder yet. Their depth is what they inherit from the
shared structures, not what a caller can actually set.

## Current coverage, honestly

Instruction 83%, line 84%, branch 63%, method 83%, class 95%. No floor is enforced yet; a ratchet
lands once the baseline has settled rather than being set to whatever today happens to be.

Live-verified: `POST /auth/token`, `POST /documents`, `GET /documents/{token}/status`,
`GET /printers/{device}/status`.

Not live-verified, and why:

| Endpoint / feature | State | Blocker |
|---|---|---|
| `GET /documents/{token}/actions/status` | implemented, WireMock only | `document_action_get` not granted |
| `GET /documents/{token}/jws` | implemented, WireMock only | `document_get_jws` not granted |
| `GET /printers/{device}/reports/daily` | implemented, WireMock only | `report_fiscal_get` not granted |
| Document status webhook | implemented, WireMock + unit only | no public ingress from the build environment |

**All seven endpoints are implemented.** Four are proven on the wire; three are covered by contract
tests alone, because the sandbox client is refused their scopes with `400 invalid_scope` — the SDK
cannot call them, not because it lacks the code but because the account lacks the grant. The webhook
path is implemented and covered offline against a real HMAC secret, but never exercised by a real
delivery.

Implemented and live-verified are different claims and this file keeps them apart. Stating that
plainly matters more than a coverage percentage.
