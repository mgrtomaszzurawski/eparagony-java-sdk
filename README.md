# eparagony-java-sdk

A typed Java 17 client for the [eparagony.pl](https://www.eparagony.pl) Documents REST API v3 —
issuing Polish fiscal e-receipts through a registered cash register, and following what becomes of
them.

> **Pre-release.** All seven endpoints are implemented; four are verified against the live sandbox and
> three await scopes the sandbox account is not granted. Of the seven document *types*, only receipts
> are mapped so far — see [Supported surface](#supported-surface).

## Why this exists

The API is seven endpoints, which sounds like an afternoon's work. Three things make it not that, and
this SDK exists to absorb all three:

1. **Authentication is on a different host than the specification says**, and getting it wrong yields
   a 404 with no explanation.
2. **An unrecognised scope string returns HTTP 200 and a token that fails everywhere with a bare
   `403 Access denied`.** The specification documents the scope separator incorrectly, so following
   the documentation produces exactly this failure. The SDK refuses such a token and tells you which
   scope is missing.
3. **The document model is 118 schemas** of Polish fiscal law — VAT slot letters, amounts in grosze,
   reconciliation rules the server enforces and the printer re-enforces.

Everything learned the hard way is written down in
[`docs/KNOWN-SERVER-BEHAVIORS.md`](docs/KNOWN-SERVER-BEHAVIORS.md).

## Usage

```java
try (EparagonyClient client = EparagonyClient.of(EparagonyConfig.builder()
        .environment(Environment.SANDBOX)
        .credentials(new ClientCredentials(clientId, clientSecret))
        .posId(PosId.of("my-shop"))
        .scopes(Scope.DOCUMENT_CREATE)
        .applicationUserAgent("MyShop/1.0 (+https://myshop.example)")
        .build())) {

    ReceiptRequest receipt = ReceiptRequest.builder()
            .orderId("ORDER-2026-1183")
            .addLine(ReceiptLine.builder()
                    .productOrServiceName("Karma sucha dla psa 1 kg")
                    .ean("05902560100679")
                    .quantity(1)
                    .unitPrice(Amount.ofZloty(new BigDecimal("100.00")))
                    .taxRate(TaxRateCode.A)
                    .build())
            .addPayment(PaymentEntry.of(PaymentForm.CARD, Amount.ofGrosze(10000), "Visa"))
            .statusUrl("https://myshop.example/webhooks/eparagony")
            .build();

    IssuedDocument issued = client.documents().issue(receipt);

    // Preferred: wait for the webhook. This is the fallback when you have no public endpoint.
    DocumentStatus status = client.documents()
            .awaitTerminalStatus(issued.documentToken(), Duration.ofMinutes(3));

    if (status.isConfirmed()) {
        emailReceiptLink(status.documentUrl().orElseThrow());
    }
}
```

`applicationUserAgent` is mandatory: the API rejects a generic User-Agent, and the SDK rejects one
before you find out over the wire.

### Amounts

The API takes integer **grosze**. `Amount` makes that impossible to get wrong:

```java
Amount.ofGrosze(10000)                        // 100.00 PLN
Amount.ofZloty(new BigDecimal("100.00"))      // the same
Amount.ofZloty(new BigDecimal("10.005"))      // throws — sub-grosz precision cannot be represented
```

The builder reconciles the document before it leaves your process. If the payments do not cover the
lines, or the declared gross value disagrees with their sum, you get an exception naming both figures
instead of an HTTP 400 carrying a numeric code.

### Webhooks

Verification needs no HTTP framework and no API credentials — just the raw bytes and the header:

```java
WebhookVerifier verifier = EparagonyClient.webhookVerifier(WebhookSecret.of(secret));
verifier.verify(rawBodyBytes, request.getHeader("X-Signature"));
```

Better still, verify and parse in one step, so a payload cannot be read without its signature having
been checked:

```java
WebhookNotifications notifications = EparagonyClient.webhookNotifications(WebhookSecret.of(secret));

DocumentStatusNotification notification =
        notifications.documentStatus(rawBodyBytes, request.getHeader("X-Signature"));
```

**Pass the body exactly as received.** Parsing the JSON and re-serializing it changes key order and
whitespace, which changes the digest — the most common integration failure with this API, and the
reason there is no `String` overload.

Note that a webhook can report `READY`, which the polling endpoint never emits, and never reports
`PENDING`, which polling does. The two channels do not share a status set.

## Supported surface

| Endpoint | Status |
|---|---|
| `POST /auth/token` | implemented, live-verified |
| `POST /documents` — receipts | implemented, live-verified |
| `GET /documents/{token}/status` | implemented, live-verified |
| `GET /printers/{device}/status` | implemented, live-verified |
| `GET /documents/{token}/actions/status` | implemented, contract-tested — scope not granted, so unverified live |
| `GET /documents/{token}/jws` | implemented, contract-tested — scope not granted, so unverified live |
| `GET /printers/{device}/reports/daily` | implemented, contract-tested — scope not granted, so unverified live |
| Webhook verification and parsing | implemented, unit-tested (no public ingress to verify live) |
| `POST /documents` — invoices, corrections, tickets | **not implemented** |

All seven endpoints are implemented. The document *payload* is a separate seven-way `oneOf`, and only
its receipt branch is mapped so far — the other six are generated in Layer 1 and waiting for a domain
surface.

## Requirements

Java 17. No runtime dependencies beyond Jackson.

## Building

```bash
./gradlew check                       # tests + Spotless, Checkstyle, PMD, SpotBugs, JaCoCo
./gradlew :eparagony-client:e2eTest   # live sandbox; needs EPARAGONY_* env vars
```

See [`docs/TESTING.md`](docs/TESTING.md) for the conventions and honest coverage numbers.

## Design

Decisions are recorded in [`ADR/`](ADR/) and are immutable — superseded, never edited.

- [ADR-001](ADR/ADR-001-generate-layer-1-from-the-vendored-spec.md) — generate Layer 1 from the vendored spec
- [ADR-002](ADR/ADR-002-three-layers-and-jpms.md) — three layers, JPMS, Java 17
- [ADR-003](ADR/ADR-003-authentication-and-the-scope-guard.md) — two hosts, lazy tokens, the scope guard
- [ADR-004](ADR/ADR-004-exceptions-grouped-by-remediation.md) — exceptions grouped by remediation
- [ADR-005](ADR/ADR-005-webhook-verification-is-transport-agnostic.md) — webhook verification takes raw bytes
- [ADR-006](ADR/ADR-006-licence-agpl-3.0-only.md) — AGPL-3.0-only

## Licence

AGPL-3.0-only. See [`LICENSE.txt`](LICENSE.txt). Commercial use in a closed product requires a
separate licence from the copyright holder.
