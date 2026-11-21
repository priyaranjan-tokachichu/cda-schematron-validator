# HTTP API and clients

[← Project home](../README.md) · [Deployment](deployment.md) · [Legacy API](legacy-api.md)

The API accepts UTF-8 XML text and an installed profile ID. It does not accept file paths, remote URLs, or uploaded Schematron. Default local base URL: `http://localhost:17643`.

## Endpoints

| Method | Route | Result |
| --- | --- | --- |
| POST | `/v1/validate` | Validation report for one document |
| GET | `/v1/profiles` | Installed profiles, source provenance, coverage gaps, and active capacity/size limits |
| GET | `/v1/version` | Application and engine versions |
| GET | `/health/live` | Process liveness |
| GET | `/health/ready` | Readiness after profile compilation |
| GET | `/openapi.json` | OpenAPI 3.1 contract |
| GET | `/` | Browser workspace |
| GET | `/architecture.html` | Full HTML engineering report |

[OpenAPI source](../service/web/openapi.json) is the machine-readable contract.

## Validate a document

Send `Content-Type: application/json`. Fields may appear in any order. Unknown or duplicate fields are rejected.

| Field | Required | Default | Meaning |
| --- | --- | --- | --- |
| `xml` | Yes | — | Nonempty XML text, UTF-8; 20 MiB default maximum |
| `profile` | No | `ccda-5.0` | `ccda-2.1` or `ccda-5.0`; maximum 128 UTF-8 bytes |
| `includeWarnings` | No | `true` | Include advisory Schematron findings; coverage limitations remain explicit |

```sh
curl http://localhost:17643/v1/validate \
  -H 'Content-Type: application/json' \
  --data-binary @request.json
```

`request.json` can contain:

```json
{
  "xml": "<ClinicalDocument xmlns=\"urn:hl7-org:v3\"/>",
  "profile": "ccda-2.1",
  "includeWarnings": true
}
```

This XML is intentionally minimal and will produce findings. Replace it with your document, or use the browser/Node client to load XML from a file.

## Understand the report

**HTTP 200 means the service produced a report. It does not mean the document passed.**

| Field | Interpretation |
| --- | --- |
| `validationScope` | Pinned XSD/Schematron pipeline with reviewed supplements; not full FHIR or clinical certification |
| `status` | `valid`, `invalid`, or `incomplete` |
| `executionComplete` | Whether the configured engine stages finished without truncation |
| `validationComplete` | Also requires supported document-level coverage, reviewed generator replacements, determinate applicable checks and no truncation |
| `truncated` | The finding limit was reached; the report is partial |
| `summary` | Errors, warnings, total findings, and fired rules |
| `findings` | Severity, stage, message, rule ID, assertion test, XPath/context, and available locations |
| `stages` | XML, XSD, and Schematron execution status |
| `profile`, `sources`, `engine` | Exact pinned validation resources and engine versions |
| `uploadQueueWaitMs` | Wait for upload memory before decoding |
| `durationMs` | Engine validation duration, separate from upload queue wait |
| `requestId`, `validatedAt` | Request identity and timestamp |

`invalid` can coexist with `validationComplete: false`: errors were found, and some coverage is missing. `incomplete` is never a pass. C-CDA 5.0 preserves its historical upstream conversion report and lists reviewed replacements in `effectiveCoverage`. Missing data affects document completion at applicable locations; the dataset inventory remains separate. A missing or unsupported root document template cannot be rescued by nested section/entry rules firing.

See an [actual synthetic-example response](../reports/example-result.json). The service does not return XML snippets. Finding messages can contain document values, so callers must handle reports as sensitive.

## Authentication and errors

When the deployment sets `API_TOKEN`, `/v1/*` requests require:

```http
Authorization: Bearer YOUR_SERVICE_TOKEN
```

Liveness, readiness, and static UI routes remain available. Use authenticated HTTPS and access controls for shared deployments; see [deployment](deployment.md#shared-deployments).

| HTTP | Meaning |
| --- | --- |
| 200 | Report produced; inspect its status and completeness |
| 400 | Malformed JSON, invalid UTF-8, missing/wrong fields, or duplicate/unknown fields |
| 401 | Missing or incorrect bearer token |
| 403 | Cross-origin submission rejected |
| 413 | Decoded XML or serialized request exceeds its active limit |
| 415 | Unsupported content type |
| 422 | Unknown validation profile |
| 429 | Bounded request/worker capacity is full; `Retry-After: 2` |
| 500 | Engine or execution failure; a partial report may be included |
| 503 | Upload queue wait expired or service is stopping |
| 504 | Validation deadline exceeded |

Admitted uploads queue fairly for up to 30 seconds while waiting for memory. Validation then has its own 30-second deadline, including any wait for a validation worker. The server bounds the total HTTP upload/read window to 65 seconds. Validation cancellation is cooperative; it is not hard process termination. Configure proxies and client timeouts for the whole request lifecycle.

## Node client and CLI

Requires Node 22.12+. Install the version 2 preview:

```sh
npm install cda-schematron-validator@next
```

```javascript
const { createClient } = require('cda-schematron-validator');
const client = createClient({
  baseUrl: 'http://localhost:17643',
  token: process.env.CDA_VALIDATOR_TOKEN,
  timeoutMs: 110000
});
```

`client.validate(xml, { profile, includeWarnings })` returns the modern report. `client.profiles()` returns profile/source metadata and active limits. `client.version()` returns version information. The default timeout is 110 seconds to accommodate upload and queueing. Non-2xx responses throw an error with `status` and `report` properties; validation findings in a 200 report do not throw.

```sh
cda-validate document.xml --profile ccda-2.1 --json
cat document.xml | cda-validate - --profile ccda-2.1 --json
```

From the checkout, use `node bin/cda-validate.js` in place of `cda-validate`.

| CLI setting | Purpose |
| --- | --- |
| `--profile ccda-2.1` | Select the guide |
| `--url URL` / `CDA_VALIDATOR_URL` | Service address |
| `CDA_VALIDATOR_TOKEN` | Service bearer token |
| `--json` | Print the full JSON report |
| `--no-warnings` | Exclude advisory Schematron findings |

Exit **0**: valid. Exit **1**: invalid. Exit **2**: incomplete or operational failure.

[Type declarations](../validator.d.ts) cover both modern and legacy interfaces.
