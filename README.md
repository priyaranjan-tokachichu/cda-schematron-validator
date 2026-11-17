<div align="center">

<h1>CDA Validator</h1>
<p><strong>Validate clinical XML. Understand every finding.</strong></p>
<p>A self-hosted API and browser workspace for CDA and C-CDA,<br>with the original JavaScript APIs preserved.</p>

<p>
<a href="#quick-start">Quick start</a> ·
<a href="docs/api.md">API reference</a> ·
<a href="docs/deployment.md">Deployment</a> ·
<a href="docs/architecture.md">Architecture</a> ·
<a href="docs/legacy-api.md">Legacy API</a>
</p>

</div>

---

> [!NOTE]
> **Version 2 preview.** The container includes the validation API and browser UI. The npm package includes the preserved legacy APIs, a typed HTTP client, and the CLI. Node 22.12+ is required. This is a preview while C-CDA 5.0 coverage gaps are addressed; npm’s stable default remains 1.1.12. See the [release notes](https://github.com/priyaranjan-tokachichu/cda-schematron-validator/releases) for artifacts and compatibility details.

<table>
<tr>
<td width="50%"><h3>Findings you can investigate</h3><p>XML parsing, CDA schema validation, and official HL7 Schematron checks. Reports include severity, rule identifiers, XPath, and available line/column locations.</p></td>
<td width="50%"><h3>A workspace anyone can use</h3><p>Upload or paste XML, choose an implementation guide, inspect errors and warnings, jump to a line, and download the JSON report.</p></td>
</tr>
<tr>
<td width="50%"><h3>Self-hosted and offline at runtime</h3><p>One container includes the UI, API, Java runtime, and pinned validation resources. Submitted documents are not persisted or sent to external validation services.</p></td>
<td width="50%"><h3>Existing integrations preserved</h3><p>The seven legacy npm exports remain available locally. A new typed HTTP client and CLI connect applications to the modern validation engine.</p></td>
</tr>
</table>

## Quick start

Install Docker with Compose, then:

```sh
git clone https://github.com/priyaranjan-tokachichu/cda-schematron-validator.git
cd cda-schematron-validator
```

Start the service:

```sh
docker compose up --build -d
```

**Open [the validation workspace](http://localhost:17643).** Try the synthetic example, or upload a UTF-8 XML document. The example intentionally contains errors so you can explore the findings.

| Open locally | Purpose |
| --- | --- |
| [Workspace](http://localhost:17643/) | Upload, paste, validate, and inspect findings |
| [Architecture report](http://localhost:17643/architecture.html) | Design, sources, limitations, and verification evidence |
| [OpenAPI contract](http://localhost:17643/openapi.json) | Integrate the HTTP API |
| [Readiness](http://localhost:17643/health/ready) | Confirm the profiles have compiled |

The first build downloads checksum-verified resources and dependencies. Normal validation runs offline. You do not need to install Java, Maven, Python, or Node on the host to run the container.

<details>
<summary><strong>Change the port, inspect logs, or stop the service</strong></summary>

The default host port is **17643**, bound to loopback. Choose a different port if needed:

```sh
CDA_VALIDATOR_PORT=17644 docker compose up -d
docker compose logs validator
docker compose down
```

When changing the port, use the matching URL in browsers and API clients. See [deployment and configuration](docs/deployment.md).

</details>

## Choose your integration

| Use case | Interface | Runtime |
| --- | --- | --- |
| Validate a document manually | Browser workspace | Docker |
| Integrate any application or language | `POST /v1/validate` | HTTP + JSON |
| Integrate a Node application | `createClient()` | Node 22.12+ and the service |
| Validate from a terminal or script | `cda-validate` | Node 22.12+ and the service |
| Keep an existing JavaScript integration | Original `validate*`, parser, and cache APIs | Node only; no Java or Docker |

The legacy API remains a partial Schematron interpreter. Use the service for CDA XSD validation and execution of the pinned full official Schematron packages.

## Supported standards

| Profile | Official Schematron release | CDA schema |
| --- | --- | --- |
| **C-CDA 2.1** · `ccda-2.1` | `2025SEP`, with bundled vocabulary | CDA R2 + SDTC `20260205_SDTC` |
| **C-CDA 5.0** · `ccda-5.0` · default | `2026JUL` | CDA R2 + SDTC `20260205_SDTC` |

Sources are pinned by immutable release URL and SHA-256. The build verifies downloads; startup verifies extracted files. `GET /v1/profiles` exposes provenance, active limits, and known coverage gaps. [Inspect the source lock](profiles/sources.lock.json). [Coverage remediation and local terminology work](docs/coverage.md).

> [!IMPORTANT]
> **C-CDA 5.0 has known upstream conversion gaps:** 4 generation errors, 6 unhandled invariants across 4 categories, and 28 non-loaded value-set entries across 9 categories. Its embedded value-set expansions cover sets with fewer than 1,000 concepts. The service reports incomplete coverage even when execution succeeds. A supported document-level template is required for complete document validation; nested templates alone are insufficient.

Neither profile certifies clinical accuracy or every terminology requirement. See [standards, provenance, and coverage](docs/architecture.md#standards-and-coverage).

## Call the API

Save this minimal example as `request.json`. It intentionally produces validation findings:

```json
{
  "xml": "<ClinicalDocument xmlns=\"urn:hl7-org:v3\"/>",
  "profile": "ccda-2.1",
  "includeWarnings": true
}
```

```sh
curl http://localhost:17643/v1/validate \
  -H 'Content-Type: application/json' \
  --data-binary @request.json
```

| Report status | Meaning |
| --- | --- |
| `valid` | No errors; configured checks and document coverage completed without recorded generation gaps or truncation |
| `invalid` | Errors found; inspect completeness separately |
| `incomplete` | No errors found, but execution or coverage is incomplete; this is not a pass |

**HTTP 200 means a report was produced, not that the document passed.** Inspect `status`, `executionComplete`, `validationComplete`, and `findings`.

[Request/response examples, endpoints, authentication, and error codes →](docs/api.md)

## JavaScript, TypeScript, and CLI

Install the package into your Node project:

```sh
npm install cda-schematron-validator@next
```

```javascript
const { createClient } = require('cda-schematron-validator');
const { readFileSync } = require('node:fs');

async function main() {
  const client = createClient({ baseUrl: 'http://localhost:17643' });
  const report = await client.validate(readFileSync('document.xml', 'utf8'), {
    profile: 'ccda-2.1',
    includeWarnings: true
  });
  console.log(report.status, report.findings);
}

main().catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
```

From the checkout, with the service running:

```sh
node bin/cda-validate.js document.xml --profile ccda-2.1 --json
# After package installation:
cda-validate document.xml --profile ccda-2.1 --json
```

CLI exit codes: **0** valid · **1** invalid · **2** incomplete or operational failure. Use `CDA_VALIDATOR_URL`, `CDA_VALIDATOR_TOKEN`, or `--url` for your deployment. The CLI accepts `-` for stdin. Type declarations are included; CI checks Node 22 and 24.

[Client configuration →](docs/api.md#node-client-and-cli) · [Legacy API and migration →](docs/legacy-api.md)

## Capacity, queue, and privacy

| Default | Behavior |
| --- | --- |
| **20 MiB XML** | Configurable with `MAX_XML_MIB`; the API and UI advertise the active limit |
| **6 admitted requests** | Includes upload, queued, and validation work; 2 validation workers |
| **30-second upload queue wait** | Admitted uploads wait fairly for memory on their original HTTP request |
| **Shared upload memory budget** | Streaming decoding reserves memory before reading; serialized JSON is never retained in full |
| **No document persistence** | No patient-document/result cache, database, or application logging of submitted XML/findings |

**For more concurrent large uploads, increase Java heap and container memory together:**

```sh
VALIDATOR_MEMORY_LIMIT=3g VALIDATOR_JAVA_OPTIONS=-Xmx2g docker compose up -d
```

More heap can permit more large uploads concurrently. CPU and document complexity still affect validation speed. The UI's **Service capacity and queue** panel explains the active configuration. A full queue returns HTTP 429; an expired upload queue wait returns HTTP 503.

The isolated 1 GiB-heap regression processes **six simultaneous 20 MiB documents**, each encoded as roughly 120 MiB JSON, with both known-length and chunked uploads. All six complete without retries; health probes and post-load validation pass. [Recorded evidence](reports/upload-memory.json).

[Memory tuning, security, authentication, deadlines, and all limits →](docs/deployment.md)

## Documentation

| Guide | What you will find |
| --- | --- |
| [HTTP API](docs/api.md) | Payloads, reports, endpoints, errors, Node client, and CLI |
| [Deployment](docs/deployment.md) | Docker, configuration, queueing, heap tuning, and secure shared access |
| [Architecture](docs/architecture.md) | Runtime design, standards, provenance, privacy, and verification |
| [Legacy API](docs/legacy-api.md) | Existing exports, defaults, examples, and migration boundaries |
| [Historical documentation](docs/history.md) | Original release history, inherited examples, and attribution |
| [Contributing](CONTRIBUTING.md) | Development checks and owner-managed contribution policy |
| [Security](SECURITY.md) | Reporting vulnerabilities and deployment responsibilities |

For the complete HTML engineering report, start the service and open [localhost:17643/architecture.html](http://localhost:17643/architecture.html). Its source and evidence are in [reports/](reports/).

## Development

Local service development requires Java 21+, Maven 3.9+, and Python 3.11+. Node is required for the JavaScript library and its tests.

```sh
npm ci --ignore-scripts
npm test
npm run service:build
npm run service:start
```

`npm run service:test` exercises both official HL7 packages. `python3 scripts/check-upload-memory.py` runs the constrained-heap queue regression using Docker and curl. These checks run in CI alongside CodeQL. [Verification details](docs/architecture.md#verification).

Public issues are welcome. This repository accepts owner-authored PRs; changes reach `main` through review. See [CONTRIBUTING.md](CONTRIBUTING.md).

---

**License and attribution** · [MIT](LICENSE.md) · [Third-party notices](THIRD_PARTY_NOTICES.md)

Original JavaScript implementation by **Eric Wadkins**, developed further by **Priyaranjan (Raj) Tokachichu**. John D'Amore's public clinical samples are credited in the source fixtures. This product includes software developed by Health Level Seven. HL7 resources and bundled dependencies retain their own licenses and notices.
