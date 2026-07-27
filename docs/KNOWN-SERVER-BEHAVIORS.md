# Known server behaviours

Things the eparagony.pl API does that its specification does not say, or says wrongly. Every entry
was observed on the live sandbox, with the date it was confirmed. This file exists so that the next
person to hit one of these finds it in two minutes rather than two hours.

---

## The `tokenUrl` in the specification is wrong

*Confirmed 2026-07-26.*

`securitySchemes.oauth-cc.flows.clientCredentials.tokenUrl` says
`https://api.eparagony.pl/auth/token`. That URL answers **404**. Authentication lives on a separate
`login.` host:

| | Auth | API |
|---|---|---|
| Sandbox | `https://login.sandbox.eparagony.pl` | `https://sandbox.eparagony.pl` |
| Production | `https://login.eparagony.pl` | `https://api.eparagony.pl` |

The specification's own `Host` parameter lists `login.eparagony.pl` among its examples, so the
`tokenUrl` field contradicts the rest of the document. `Environment` carries both URLs.

## An unrecognised scope yields HTTP 200 and a token that 403s everywhere

*Confirmed 2026-07-26. The single most expensive behaviour in this API.*

The `scope` field is documented as comma-separated. It is **space**-separated (RFC 6749). Send a
comma-joined list and the server reads it as one unrecognised scope name — then answers **200**,
omits `scope` from the response, and issues a token. Every endpoint rejects that token with
`403 {"statusCode":403,"error":"Forbidden","message":"Access denied"}`, mentioning neither scopes nor
tokens.

| `scope` sent | HTTP | `scope` returned | Usable |
|---|---|---|---|
| `document_create` | 200 | `document_create` | yes |
| `document_create printer_get` | 200 | `document_create printer_get` | yes |
| `document_create document_action_get` (not entitled) | **400** `invalid_scope` | names the offender | correct failure |
| `document_create,printer_get` (comma) | **200** | **absent** | **no** |
| `nonsense_scope` | **200** | **absent** | **no** |

The SDK refuses a token whose granted `scope` does not cover the request. See ADR-003.

## 403, not 404, for a document that is not yours

*Confirmed 2026-07-26.*

`GET /documents/{unknown-uuid}/status` with a valid token answers `403 Access denied`. "No such
document" and "not your document" are indistinguishable from outside.

## The API version is sticky per document

*Confirmed 2026-07-26.*

The version a document was created under governs how it can be read back:

```
GET /documents/<v1-created>/status   X-Api-Version: 3  ->  400 {"errorCode":134}
GET /documents/<v1-created>/status   X-Api-Version: 1  ->  200  (legacy shape, no top-level `status`)
GET /documents/<v1-created>/status   X-Api-Version: 2  ->  200  (same legacy shape)
```

This SDK is v3-only and therefore **cannot read documents issued by an older integration against the
same `posId`**. `X-Api-Version: 2` also rejected a v3-shaped receipt payload with
`400 {"errorCode":120}`.

## The polling endpoint and the webhook emit different status sets

*Confirmed 2026-07-26.*

- Polling emits `PENDING`, `CONFIRMED`, `OFFLINE`, `ERROR`.
- The webhook emits `READY`, `CONFIRMED`, `OFFLINE`, `ERROR` — never `PENDING`.
- `READY` is sent only when `print: true` was requested, and means the paper printed; the document may
  not have reached the repository yet.

Modelling these as one enum without recording which channel produces which leads to handlers waiting
for a state their channel will never send.

## `X-Integration-Id` is optional despite the changelog

*Confirmed 2026-07-26.*

The 3.0 changelog states that `X-Integration-Id` became required for generic integrations. The
specification marks it `required: false`, and live calls succeed without it. The SDK sends it when
configured and omits it otherwise.

## The sandbox fakes more than device timing

*Confirmed 2026-07-26 across four documents issued with different payloads.*

The test environment is backed by a fiscal printer emulator. Everything originating from the device is
**constant across every document it will ever issue**:

| Field | Value |
|---|---|
| `fiscalDeviceUniqueNumber` | `ZBN1901007833` |
| `fiscalDocumentId` | `ZBN1901007833/570` |
| `fiscalDocumentNumber` | `570` — does not increment |
| `receiptNumber` | `210` — does not increment |
| `endTime` | `2022-07-20T11:26:38.596Z` |

Everything the caller supplied — `orderId`, `merchantDocumentId`, `printed`, `posId` — round-trips
correctly, and the tokens and URLs are unique per document.

**Consequence for tests:** request mapping and metadata round-trip may be asserted against the
sandbox. Document numbering, timestamps and monotonicity may not. A test asserting that two sandbox
receipts get different numbers fails forever.

Further sandbox limitations, per the vendor's documentation: the visualization always renders the same
fiscal document regardless of payload, fiscalization errors (such as *schodek podatkowy*) cannot be
provoked, and fiscalization lags roughly a minute. The eparagony.pl Document Print Service does not
run against the test environment at all.

## Scopes granted to our sandbox client

*Confirmed 2026-07-26.*

Granted: `document_create`, `printer_get`, `ecommerce`.
Refused with `400 invalid_scope`: `document_action_get`, `document_get_jws`, `report_fiscal_get`.

`ecommerce` appears in the status endpoint's `security` block but is **absent from
`securitySchemes`** — undocumented, yet accepted by the authorization server.

## Sign and type conventions that read backwards

*From the specification, 2026-07-26. Each of these was implemented wrongly first and caught by review.*

- **A rebate is a NEGATIVE value.** `RebatesMarkups.value` and `LineRebate.value`: "Negative value
  indicates a rebate/reduction, positive value indicates a markup." A positive number under a line
  labelled *Rabat* is a surcharge. The SDK's `RebateOrMarkup.rebate` / `markup` take a magnitude and
  apply the sign so a caller never faces this.
- **A barcode content line has no `BARCODE` type.** The `type` discriminator *is* the symbology, one
  of nineteen values from `EAN13` to `PHARMACODE`. The QR variant is `QR`, not `QR_CODE`.
- **Warranty `periodUnit` is upper-case** and has four values including `HOUR`; `dateTo` is a full
  ISO-8601 instant with offset (`2019-09-02T23:59:59.999+02:00`), not a bare date.
- **Loyalty `pointsAdded` and `newBalance` are strings**, and the specification's own examples are
  fractional (`-20.98`, `1234.56`).
- **`PackageReturn` requires `packageNumber` and not `name`** — the reverse of what the field names
  suggest. Note also that the vendor's own example for this schema contradicts the schema three ways
  (`productOrServiceName` instead of `name`, a string `quantity`, no `packageNumber`), so this one
  wants a live probe before it is trusted in production.

## `grossSaleValue` must net BOTH discount mechanisms, and the server checks

*Confirmed 2026-07-27 by direct probe — three receipts posted to the sandbox.*

A receipt can reduce the sale two ways: `rebatesMarkups` attached to a product line, and a standalone
`REBATE` line. Both must be reflected in `metadata.grossSaleValue`. Line-level rebates are **not**
already inside `totalLineValue` — the specification defines that field as the gross value "before
applying any markups and discounts", so it never moves.

Same shape, one product line of 10000 carrying a `-300` line rebate plus a `-100` standalone rebate
line, posted three ways:

| Declared `grossSaleValue` | Reading | Result |
|---|---|---|
| 9802 (the spec's own worked example) | both count | **202** → `CONFIRMED` |
| 9600 | both count | **202** → `CONFIRMED` |
| 9900 | only the `REBATE` line counts | **400** `{"errorCode":41,"message":"Incorrectly calculated value of 'eReceipt.metadata.grossSaleValue'"}` |

This is one of the few places the sandbox validates arithmetic rather than rubber-stamping it, so it
is worth knowing that a mistake here fails loudly at submission rather than silently at the register.

The specification's `Paragon - wszystkie dane` example is a usable oracle for this: a 9802 line with a
`+100` markup and a `-100` `REBATE` line declaring `grossSaleValue: 9802`. That total only reconciles
if both adjustments participate. The SDK derives the figure so a caller never has to.

Note the asymmetry with invoices, which is what makes the receipt rule easy to get backwards:
`FullInvoiceLine.totalLineValue` *may* already include its rebates, governed by
`rebatesMarkups.includedInTotalLineValue`. That flag exists on the invoice line and not on the receipt
line precisely because a receipt's discounts always sit outside the line total.

## Observed timings

*Sandbox, 2026-07-26.* Access token lifetime `expires_in: 3600`. A fiscalized receipt reached
`CONFIRMED` roughly 50 seconds after `POST /documents` returned 202. Production is documented as
faster, without the emulator's deliberate delay.
