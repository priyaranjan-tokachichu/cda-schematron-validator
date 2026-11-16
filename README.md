# [cda-schematron-validator](https://github.com/priyaranjan-tokachichu/cda-schematron-validator)

A self-hosted CDA validation workspace and HTTP API, with the original JavaScript Schematron API retained for existing integrations.

**Modern service:** strict XML parsing → CDA XSD → full official HL7 Schematron → detailed JSON findings. The service uses Saxon-HE and SchXslt, compiles profiles at startup, and isolates every submitted document. The browser UI supports paste/upload, profile selection, errors and warnings, line navigation, and JSON report downloads.

This product includes software developed by Health Level Seven.

## Start the UI and API

Install Docker with Compose, then run from this checkout:

```sh
docker compose up --build -d
```

Open **http://localhost:17643**. Choose an XML file or paste UTF-8 XML, select a guide, and click **Validate document**. The included synthetic example intentionally fails several rules so you can inspect the findings.

The default host port is **17643**. If it is already occupied, choose another port: `CDA_VALIDATOR_PORT=17644 docker compose up -d`, then open `http://localhost:17644`. Point CLI clients at the same address using `CDA_VALIDATOR_URL=http://localhost:17644` (or `--url`), and set `baseUrl` for JavaScript clients. No fixed port can guarantee availability on every installation.

The Compose configuration exposes the service on loopback only. The image includes Java, the application, and pinned validation resources; Node, Java, Maven, and Python are not required on the host when using Docker. Runtime validation makes no external validation or terminology calls. First build requires internet access to fetch checksum-verified resources and dependencies.

An owner-triggered release workflow is included to publish versioned AMD64/ARM64 images to `ghcr.io/priyaranjan-tokachichu/cda-schematron-validator`, with SBOM and provenance. No public registry release has been published by this implementation; until the owner releases it, build from source using Compose. Package visibility must allow public pulls if public distribution is intended.

```sh
docker compose logs validator
docker compose down
```

## Published profiles

| Profile | Schematron release | Schema |
| --- | --- | --- |
| `ccda-2.1` | HL7 C-CDA 2.1 `2025SEP` | CDA R2 + SDTC `20260205_SDTC` |
| `ccda-5.0` (default) | HL7 C-CDA 5.0 `2026JUL` | CDA R2 + SDTC `20260205_SDTC` |

Sources, SHA-256 checksums, transformation metadata, and known limitations are returned by `GET /v1/profiles`. Original release archive locks are in [profiles/sources.lock.json](profiles/sources.lock.json). Files are checked at build time and verified again at service startup. Upstream resources retain their copyright and license notices; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

The C-CDA 5.0 upstream conversion report records **4 generation errors, 4 unhandled invariants, and 9 non-loaded value sets**. Its Schematron includes expansions only for value sets with fewer than 1,000 concepts. The application reports those gaps; it does not claim complete StructureDefinition or terminology validation. The C-CDA 2.1 profile checks its bundled vocabulary snapshot. Neither profile certifies clinical accuracy, regulatory compliance, or all possible terminology semantics.

## Call the API

```sh
curl http://localhost:17643/v1/profiles

curl http://localhost:17643/v1/validate \
  -H 'Content-Type: application/json' \
  --data-binary @request.json
```

`request.json`:

```json
{
  "xml": "<ClinicalDocument xmlns=\"urn:hl7-org:v3\">...</ClinicalDocument>",
  "profile": "ccda-5.0",
  "includeWarnings": true
}
```

The response includes `status`, `validationComplete`, `executionComplete`, stage results, errors/warnings, rule IDs, XPath, available line/column locations, timing, and the exact data source versions and checksums. XML snippets are not returned by the modern service. Finding messages can contain document values and should be treated as sensitive by callers.

- `valid`: no errors and the selected profile's configured checks completed without recorded conversion gaps.
- `invalid`: validation found errors; inspect completeness separately.
- `incomplete`: no errors found, but coverage or execution was incomplete. This is not a pass.

A completed validation report returns HTTP 200 even for an invalid document. Request errors use 400/413/415/422, authentication uses 401, capacity uses 429, engine failures use 500, and deadlines use 504. Partial engine-failure reports may accompany HTTP 500. See the full [OpenAPI specification](service/web/openapi.json), also served at `/openapi.json` and summarized in the UI's API reference.

Other endpoints: `/v1/version`, `/health/live`, `/health/ready`. Readiness becomes available only after both profiles compile. Optional authentication: set `API_TOKEN` in your deployment and send `Authorization: Bearer TOKEN`. Shared/network deployments must configure authenticated HTTPS through a reverse proxy; the default local configuration is not a public hosted service.

## JavaScript and TypeScript client

Requires a supported modern Node runtime (22.12+; CI tests Node 22 and 24). Type declarations are included.

```javascript
const { createClient } = require('cda-schematron-validator');
const fs = require('node:fs');

const client = createClient({ baseUrl: 'http://localhost:17643' });
const report = await client.validate(fs.readFileSync('document.xml', 'utf8'), {
  profile: 'ccda-5.0',
  includeWarnings: true
});
console.log(report.status, report.findings);
```

The new client requires the service. Existing `validate`, `validateAsync`, `validateFileListAsync`, `validateFileObjectAsync`, `parseSchematron`, `clearCache`, and `clearCacheAsync` exports continue to run locally without Java or Docker and retain their result shape. They remain a lightweight, partial Schematron interpreter; they do **not** gain the service's XSD and standards-engine coverage. Use `createClient` for the modern pipeline. The legacy default for `includeWarnings` is **false**, despite the older inherited documentation below.

The legacy fixes isolate document caches, avoid mutating caller options, settle empty async patterns, preserve batch ordering, generate missing rule IDs, and distinguish resource paths in the external-document cache.

## Command line

After installing the Node package, or directly from this checkout:

```sh
node bin/cda-validate.js service/web/sample.xml --profile ccda-5.0 --json
# Installed package: cda-validate document.xml --profile ccda-2.1
```

Set `CDA_VALIDATOR_URL` and optionally `CDA_VALIDATOR_TOKEN` for another service. Exit codes: **0** valid, **1** invalid, **2** incomplete or operational failure. The CLI uses the same HTTP service and accepts `-` for stdin.

## Limits, privacy, and throughput

The service stores no submitted documents or reports and logs no request bodies, filenames, or validation findings. The UI retains input only in the current page's memory; downloading a report is an explicit local action. Reverse proxies and client applications must configure their own logging and retention appropriately.

Limits: **20 MiB XML by default**, configurable through `MAX_XML_MIB` (for example, `MAX_XML_MIB=40 docker compose up -d`). `/v1/profiles` advertises the active byte limits and the UI follows them; the CLI defers size enforcement to the service. The serialized JSON allowance is six times the XML byte limit plus 64 KiB for escaping and metadata. Increase container memory when raising limits for large workloads. Other limits: 128-element nesting depth, 100,000 elements, 256 attributes per element, and 2,000 findings. DTDs and external entities are prohibited. Schematron resource access is restricted to the bundled asset directories. Workers and the admission queue are bounded; default is 2 workers plus 4 waiting requests. SVRL findings are collected as a stream and capped rather than building an unlimited result tree.

The HTTP deadline is 30 seconds with cooperative cancellation checked during SVRL output. This is not process-level hard termination of an arbitrary XPath expression. Only the fixed, reviewed profiles can execute; user-supplied Schematron, paths, and remote URLs are not accepted. Container CPU/memory/PID limits provide additional isolation. Scale by increasing `VALIDATION_WORKERS` deliberately or running more container replicas behind a gateway.

No document-result cache is used: warm performance comes from immutable shared compiled profiles and schema objects, not reuse of previous patient documents. Run the repeatable benchmark with `python3 scripts/benchmark.py`; the generated report records fixture sizes, concurrency, HTTP p50/p95, throughput, and result consistency. These measurements are not a claim to be the fastest validator across all workloads.

## Develop and verify

Local service development requires Java 21+, Maven 3.9+, and Python 3.11+:

```sh
npm ci --ignore-scripts
npm test
npm run service:build
npm run service:start
```

`npm run service:test` runs focused Java tests and full official-profile integration tests. JavaScript tests use Node's built-in runner with no development dependencies. The Docker build runs service tests too. The CI workflow tests both supported Node lines, service behavior, official resources, and the container build. The weekly upstream watch reports new HL7 releases without updating packages or opening PRs automatically.

All changes must use descriptive branches and owner-authored PRs. Development tool names are forbidden in branch names. See [CONTRIBUTING.md](CONTRIBUTING.md) and [AGENTS.md](AGENTS.md).

The original release history and API documentation follow for provenance. Where an older example conflicts with the modern instructions above, use the instructions above.

## Release History

### 1.1.12

- Update xmldom to the latest version

### 1.1.11

- License file added

### 1.1.10

- Fix grunt race condition reported by github

    "file.copy operations in GruntJS are vulnerable to a TOCTOU race condition leading to arbitrary file write in GitHub repository gruntjs/grunt prior to 1.5.3. This vulnerability is capable of arbitrary file writes which can lead to local privilege escalation to the GruntJS user if a lower-privileged user has write access to both source and destination directories as the lower-privileged user can create a symlink to the GruntJS user's .bashrc file or replace /etc/shadow file if the GruntJS user is root."

### 1.1.9

- Fix grunt vulnerability

### 1.1.8

- jsdoc module added
- parseSchematron.js moved to lib

### 1.1.7

- Introduced a new method called `validateFileObjectAsync` which allows to supply an object with key, value pairs where key represents the identifier of the xml, and the value represents xml file content or path to validate
- An array of results will be returned from the `validateFileObjectAsync` method
- Test file `test/asyncFileList.test.js` has the example usage
- The result object has the property `fileInfo` that would return the `key` the identifier of the xml to identify and debug issues
- The result object of the array result from the method `validateFileListAsync` now has the property `fileIndex` that will give an indication of which xml file the result belongs to for debugging purposes

### 1.1.6

- Methods have been moved to js files and relocated to lib directory
- Introduced a new method called `validateFileListAsync` which allows to supply an array of xml file content or paths to validate
- An array of results will be returned from the `validateFileListAsync` method
- Test file `test/asyncFileList.test.js` has the example usage
- Started to make use of Promise.all for async methods
- Documentation of the methods completed by 40%

### 1.1.5

- Major methods have been divided into multiple small methods to avoid redundancy across sync and async methods, and reduce maintenance

### 1.1.4 (Stable Release)

- Major deviations from the source repository

  - By default the warning will be turned off in contrast to the parent repository

  - To get warnings, users have to explicitly specify the option `includeWarnings` with value boolean `true`

  - We also deviate from the previous behavior where all the checks for warnings were done at the assertion
    level just before testing the xpath. We now would not even parse the phase with @id="warnings" which should
    improve the performance.

- Minor cosmetic changes to the code

### 1.1.1 to 1.1.3

- Only keeping the necessary data in the test files taken from HL7 C-CDA schematron repository to avoid bloating the module

- Added pre-commit hooks with husky

- Added code standardization with prettier, ESLint, and lint-staged

### 1.1.0

- Fix the issue with the rule check which was working in the original repository, but not in the fork
- Added HL7 C-CDA schematron file and resource file from <https://github.com/HL7/CDA-ccda-2.1/releases/tag/2019JUN>
- Added a test case vitalSigns.test.js that tests the resource file and sub assertions
- Used resources <https://github.com/jddamore/ccda-samples> and <https://github.com/HL7/C-CDA-Examples/tree/master/Vital%20Signs> to create the test file vitalSigns.xml

### 1.0.10

- Fix null context issue associated with assertions within assertions
- Skip abstract rules associated with assertions within assertions

### 1.0.9

- Fixed the bug introduced in 1.0.8 trying to adjust to different data types of the return value
- Changed variable names to improve understanding of the process

### 1.0.8

- Bug fix identifying the ignored errors

### 1.0.7

- Added async validate function `validateAsync`
- Added `testsAsync` to test `validateAsync`
- Plan to use common functions between sync and async methods in the future release

### 1.0.0 to 1.0.6

A fork to the original cda-schematron. All credit to Eric Wadkins. Visit the github <https://github.com/ewadkins/cda-schematron> for the original repo.

This is just a cosmetic change with updated node modules, a couple of minor bugs observed, and esversion 6 changes.

The changes are fully backward compatible. So, leaving the original documentation as is below.

### New option parameter parsedSchematronMap

In addition to everything that was possible,
you can now supply a new option parameter `parsedSchematronMap`. You can create a schematron map as follows.

```javascript
const fs = require('fs');
const dom = require('@xmldom/xmldom').DOMParser;
const { parseSchematron, validate } = require('cda-schematron-validator');
const schematronString = fs.readFileSync('SchematronFilePath', 'utf-8').toString();
const schematronDoc = new dom().parseFromString(schematronString);
const schematronMap = parseSchematron(schematronDoc);
const options = { 
    parsedSchematronMap: schematronMap
    };
const results =  validate('xml string or file path', 'schematron sting, file path or null', options);
```

If a schematron map is provided, the schematron string or path will not be read.

To install this npm module, it will be

```javascript
npm i cda-schematron-validator
```

Mocha VS Code configuratin added to directly execute the test file in `test/tests.js` using VS Code.

## Documentation of the parent repo [cda-schematron](https://github.com/ewadkins/cda-schematron)

A javascript implementation of schematron testing for XML documents. This specifically resolves a need for a package that allows a quick, reliable install for validating HL7 clinical documents, such as C-CDA.

Check out [cda-schematron-server](https://github.com/ewadkins/cda-schematron-server), a server wrapper of **cda-schematron**, for easy schematron validation.

### Validating xml

```javascript
var validator = require('cda-schematron');

var xmlPath = 'someFile.xml';
var schematronPath = 'someFile.sch';

var fs = require('fs');
var xml = fs.readFileSync(xmlPath).toString();
var schematron = fs.readFileSync(schematronPath).toString();

var results = validator.validate(xml, schematron);
```

File paths can also be passed to the validator directly. The following lines all return the same results:

```javascript
var results = validator.validate(xml, schematronPath);
```

```javascript
var results = validator.validate(xmlPath, schematron);
```

```javascript
var results = validator.validate(xmlPath, schematronPath);
```

### Results

```results``` is an object containing arrays  ```errors```, ```warnings```, and ```ignoreds```.

**Errors** and **warnings** are reported as determined by the schematron and test descriptions. They are of the following form:

```javascript
{
    type: type,                     // "error" or "warning"
    test: test,                     // xpath test
    simplifiedTest: simplifiedTest, // xpath test with resource values included, if applicable, null otherwise
    description: description,       // schematron description of the test case
    line: line,                     // line number of the violating context
    path: path,                     // xpath path of the violating context
    patternId: patternId,           // schematron-assigned pattern id
    ruleId: ruleId,                 // schematron-assigned rule id
    assertionId: assertionId,       // schematron-assigned assertion id
    context: context,               // xpath context of the rule
    xml: xml                        // xml snippet of the violating context
}
```

**Ignored** tests are those that resulted in an exception while running (eg. the test is invalid xpath and could not be parsed properly) and require manual inspection. They are of the following form:

```javascript
{
    errorMessage: errorMessage,     // reason for the exception/ignoring the test
    type: type,                     // "error" or "warning"
    test: test,                     // xpath test
    simplifiedTest: simplifiedTest, // xpath test with resource values included, if applicable, null otherwise
    description: description,       // schematron description of the test case
    patternId: patternId,           // schematron-assigned pattern id
    ruleId: ruleId,                 // schematron-assigned rule id
    assertionId: assertionId,       // schematron-assigned assertion id
    context: context,               // xpath context of the rule
}
```

### Options

The ```validate``` function takes in an ```options``` object as an optional third argument. The three fields that can be included in ```options``` are as follows:

- **```includeWarnings```**: ```true``` or ```false```, this determines whether or not warnings should be tested and returned. Defaults to ```true```.

- **```resourceDir```**: the path to a directory containing resource files (eg. voc.xml) which may be necessary for some schematron tests. Defaults to ```'./'```, the current directory.

- **```xmlSnippetMaxLength```**: an integer, which is the maximum length of the ```xml``` field in validation results. Defaults to ```200```. Set to ```0``` for unlimited length.

Here is an example with warnings disabled:

```javascript
var results = validator.validate(xml, schematron, { includeWarnings: false });
```

### Cache

The validator uses a cache to store parsed schematrons, an intermediate data structure used to store revelant schematron information. This reduces the runtime of the validator when validating against the same schematron multiple times. You can clear the cache at any time with:

```javascript
validator.clearCache();
```

---

## License (MIT)

Copyright &copy; 2017 [Eric Wadkins](http://www.ericwadkins.com/)

Copyright &copy; 2022 [Priyaranjan (Raj) Tokachichu](https://github.com/priyaranjan-tokachichu)

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
