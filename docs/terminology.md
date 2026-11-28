# Administrator-supplied terminology

[← Coverage inventory](coverage.md) · [Deployment](deployment.md)

Version 2.0.0 adds coverage and packaging changes after `2.0.0-preview.2`. Older published images are unchanged. New public images omit the entire mixed-license C-CDA 2.1 `voc.xml`, which contains CPT entries. The service can start and show the UI without it; C-CDA 2.1 schema checks run, and its Schematron stage explicitly reports setup required until an authorized pinned vocabulary is installed. The legacy npm API remains compatible and accepts caller-supplied validation resources.

## Who is the operator?

**Operator** means the person or organization that runs this validator: you on a personal computer, or the administrator/IT team responsible for a hosted installation. **Operator-supplied terminology** means terminology files that the administrator obtains from the source publisher and installs in the local `terminology` folder. It does not mean patient data, a clinician entering codes, or a separate commercial operator service.

The application includes its validation rules and import/install tools. Public npm packages and final container images do not include the separately installed terminology pack. Download access and checksums do not establish permission to use or redistribute the underlying terminology; consult the applicable publisher terms for your deployment.

## What was installed and validated locally?

The recorded 2.0.0 verification used the pinned C-CDA 2.1 `voc.xml`, plus a local C-CDA 5.0 pack containing **29 snapshots / 1,013,055 membership entries**, **28 active indexes**, and **two grammars** (UCUM and language tags). The pack was assembled from 21 reviewed VSAC exports, four reviewed public HL7/CDC expansions, a HealthcareServiceLocation expansion, and three LOINC/UMLS-derived sets. Source editions include UMLS 2026AB, LOINC 2.83 and the editions listed [below](#prepare-us-core-condition-and-procedure-sets-from-umls).

This was **not every terminology set**: NUBC patient discharge status was not installed. The pinned XSD/Schematron stages and reviewed supplements were exercised with local data, including concurrent maximum-size uploads. Individual documents can complete when they do not need missing data; an applicable missing NUBC check, unsupported expression or other indeterminate check prevents completion. Successful engine execution or an HTTP 200 response is not evidence that a document is valid.

The recorded automated suite comprised 65 JavaScript, 45 Java and 17 Python tests. Fifteen targeted comparisons against HL7 FHIR Validator used **reference terminology disabled**; they do not prove complete terminology or StructureDefinition equivalence. See the dated [verification evidence](../reports/coverage-progress.json) and [reference comparison](../reports/reference-comparison.json). The local pack is not a downloadable project artifact.

A [November 28, 2026 smoke check](../reports/terminology-setup-verification.json) of the published image confirmed that C-CDA 2.1 Schematron is skipped without the vocabulary and completes after installation (110 rules fired on `test/VitalSigns.xml`). Both stages also completed for the C-CDA 5.0 fixture with the local pack. The fixtures reported `invalid` with `validationComplete: true`: all applicable checks completed and found document errors. These are execution checks, not passing-document or clinical certification claims.

## Choose your setup

| Goal | Data to install | Result |
| --- | --- | --- |
| C-CDA 2.1 schema checks | No separate terminology | XSD runs; Schematron reports setup required until its vocabulary is installed. |
| C-CDA 2.1 XSD and Schematron | The exact pinned HL7 2025SEP `voc.xml` | Both stages can run; terminology coverage remains limited to that upstream snapshot. |
| Broader C-CDA 5.0 supplements and binding checks | The separate VSAC, HL7/CDC, LOINC/UMLS and grammar pack | Applicable installed checks run; missing NUBC or other required data still prevents completion. |

The 29-snapshot C-CDA 5.0 pack is **not a prerequisite** for enabling C-CDA 2.1 Schematron. Start with the next section for that profile.

## Get and install the C-CDA 2.1 vocabulary

Prerequisites: Git, Python 3 and Docker. No host Java or Node is needed for these commands. After reviewing the source terms applicable to your use, obtain the **2025SEP** archive from the [official HL7 release](https://github.com/HL7/CDA-ccda-2.1/releases/tag/2025SEP). It is named `CDAR2_IG_CCDA_CLINNOTES_R1_DSTU2.1_2025SEP.zip` and contains `voc.xml`. A direct publisher download is available from that release; do not substitute a newer or edited vocabulary for the pinned file.

The existing asset preparation command downloads the official pinned archives, checks their SHA-256 values against [the source lock](../profiles/sources.lock.json), and extracts the vocabulary locally:

```sh
git clone https://github.com/priyaranjan-tokachichu/cda-schematron-validator.git
cd cda-schematron-validator
python3 scripts/prepare-assets.py
```

The required file is now `service/assets/ccda-2.1/voc.xml`. This command prepares all three pinned validation-asset archives; it does **not** download the separate VSAC/UMLS/LOINC pack. The C-CDA 2.1 archive SHA-256 is `9c9a73294fbc4bea2c31e3650184609fa2d8894a848da6240c0916cd1e1c0d23`. If you download the ZIP manually, verify that checksum and extract its `voc.xml`, then use its absolute path in the input mount below.

Install into your own terminology folder using the published application image:

```sh
IMAGE=ghcr.io/priyaranjan-tokachichu/cda-schematron-validator:v2.0.0
docker pull "$IMAGE"
mkdir -p terminology
docker run --rm --network none --entrypoint java \
  --user "$(id -u):$(id -g)" \
  -v "$PWD/terminology:/app/terminology" \
  -v "$PWD/service/assets/ccda-2.1/voc.xml:/input/voc.xml:ro" \
  "$IMAGE" -cp /app/app.jar org.cdavalidator.TerminologyTool \
  install-legacy /input/voc.xml
```

The installer checks the vocabulary checksum required by the image and writes `terminology/ccda-2.1-voc.xml`. An incorrect edition is rejected. The installation stays local; do not commit or publish the vocabulary or terminology folder.

Start the service with that folder mounted read-only:

```sh
docker run --rm --read-only --tmpfs /tmp:size=64m,noexec,nosuid \
  --memory=1536m -e JAVA_TOOL_OPTIONS=-Xmx1g \
  -p 127.0.0.1:17643:17643 \
  -v "$PWD/terminology:/app/terminology:ro" \
  "$IMAGE"
```

Use an unused host port if 17643 is occupied, and use that same port in the following commands. In another terminal:

```sh
curl --fail --silent --show-error http://127.0.0.1:17643/v1/profiles
```

For profile `ccda-2.1`, check `terminologySetup.ready: true`. The `ccda-5.0` inventory can still report missing datasets; installing the legacy vocabulary alone does not make the other profile data-ready. The existing `doctor` command [below](#mount-diagnose-and-run) checks both profiles and can therefore still exit with code 2.

Submit a local document with the C-CDA 2.1 profile (run from the repository folder):

```sh
python3 - <<'PYDOC' > /tmp/ccda-21-request.json
import json
from pathlib import Path
print(json.dumps({"xml": Path("test/VitalSigns.xml").read_text(),
                  "profile": "ccda-2.1", "includeWarnings": True}))
PYDOC
curl --fail --silent --show-error http://127.0.0.1:17643/v1/validate \
  -H 'Content-Type: application/json' \
  --data-binary @/tmp/ccda-21-request.json
```

`test/VitalSigns.xml` is a test fixture, not a guaranteed valid clinical document. Inspect `stages`, `executionComplete`, `validationComplete` and `status`; findings may be expected. Restart the service after changing terminology files.

## Recreate the broader local terminology setup

Use a checkout containing this guide and its checklists. Obtain restricted source downloads through your own publisher accounts; the project does not distribute the local pack or accept source terms for you.

| Component | Where to obtain it | Local preparation |
| --- | --- | --- |
| 21 VSAC snapshots | [NLM VSAC](https://vsac.nlm.nih.gov/); search each OID in the [reviewed checklist](../profiles/terminology-svs-checklist.json), select the reviewed expansion and export complete SVS XML | Use `import-svs.py` with that checklist as shown below. Include retired sets referenced by the pinned guide after review. |
| Four public HL7/CDC expansions and two grammars | Publisher URLs and hashes in [the terminology lock](../profiles/terminology-sources.lock.json) | `python3 scripts/prepare-public-terminology.py --download --grammars --output terminology` |
| HealthcareServiceLocation union | Obtain a complete FHIR expansion of `http://terminology.hl7.org/ValueSet/v3-HealthcareServiceLocation`, definition version `4.0.0`, from your configured terminology service | Import with the [location checklist](../profiles/terminology-location-checklist.json) and [explicit system mappings](../profiles/terminology-location-systems.json), as shown below. |
| LOINC document types | [LOINC Complete 2.83](https://loinc.org/downloads/) through your LOINC account | `python3 scripts/import-loinc.py --input /private/path/Loinc_2.83.zip --output terminology` |
| US Core condition and procedure sets | [NLM UMLS downloads](https://www.nlm.nih.gov/research/umls/licensedcontent/umlsknowledgesources.html): the full **2026AB distribution archive**, plus LOINC Complete 2.83 | Use `import-umls.py` [below](#prepare-us-core-condition-and-procedure-sets-from-umls). Do not substitute a precomputed subset or individual RRF download; archive hashes must match the lock. |
| NUBC discharge disposition | An authorized source providing the applicable complete, versioned expansion | Use the FHIR importer with your reviewed version, count and OID mappings. This dataset was **not** in the recorded local pack. |

For the VSAC export collection:

```sh
python3 scripts/import-svs.py \
  --input-glob '/private/path/RetrieveMultipleValueSets*.xml' \
  --expected profiles/terminology-svs-checklist.json \
  --output terminology
```

The checklist records the actual local counts, source hashes, normalized snapshot hashes and status. Its source-version label was `Latest` in those exports; it is **not an immutable release identifier**. A later export can differ. Counts alone do not establish identical membership: compare the imported snapshot hashes with the checklist for exact reproduction. Review changed editions/counts explicitly instead of editing the checklist merely to force an import to pass. No terminology codes or descriptions are included in these checklists.

For the 274-member HealthcareServiceLocation expansion:

```sh
python3 scripts/import-fhir-terminology.py \
  --input /private/path/healthcare-location.json \
  --expected profiles/terminology-location-checklist.json \
  --systems profiles/terminology-location-systems.json \
  --output terminology
```

The two import commands preserve other installed expansions. The public-source preparation command uses the pinned source hashes; if a live grammar registry has changed and the pinned edition is unavailable, it stops rather than silently accepting newer bytes. Use a verified copy of that pinned edition or review and update the lock. After assembling the pack, mount it read-only, run `doctor`, and inspect each profile's missing-data inventory. A 29-snapshot count does not by itself prove matching membership or complete validation coverage.



## Licensing and source selection

The application’s MIT license does not license terminology data. A user’s CPT license does not automatically grant this project distribution rights. Giving the application away or adding a notice does not replace the required agreement. AMA’s [licensing guidance](https://www.ama-assn.org/practice-management/cpt/cpt-licensing-frequently-asked-questions-faqs) also covers CPT-dependent products, so user-supplied data is not a blanket licensing exemption for the application. No CPT distribution agreement has been established for this project.

[LOINC permits redistribution under conditions](https://loinc.org/kb/license), including notices and applicable third-party terms; it does not require every user to download their own copy. We support operator-supplied LOINC alongside other sources. The local UMLS 2026AB metadata identifies LOINC 2.83; the standalone [LOINC Complete distribution](https://loinc.org/downloads/) contains additional tables useful for producing reviewed expansions. This importer accepts complete FHIR expansions, rather than treating an entire source download as an already resolved value set.

Use authorized VSAC exports, source-system distributions, or complete FHIR expansions from an appropriately configured terminology service. Resolve licensing for the intended deployment. The software does not accept terms, create accounts or store terminology credentials. Never send clinical XML to obtain an expansion.

## Checks and data readiness

`GET /v1/profiles` exposes:

- `supplementalChecks`: person-name, procedure timestamp, procedure-membership and location-union checks, with coverage guards for unavailable data.
- `supplementalBindingChecks`: installed checks at the exact locations in the checksum-verified HL7 binding catalog. Required bindings produce errors; preferred and extensible bindings preserve the upstream warning severity.
- `terminologySetup`: exact missing and installed value-set canonicals, a data-readiness flag and the setup guide URL. Readiness is separate from full validation coverage.
- `terminologySnapshots` and `grammarDefinitions`: pinned editions, counts and hashes. Unknown or unimplemented canonicals remain inactive.

Code/system pairs use exact membership. Bindings on primitive code attributes use exact code membership. Each supplemental binding has a stable ID derived from its canonical, context and strength, and runs in a separate pattern so overlapping contexts cannot suppress another binding. The original HL7 assets and generation report remain unchanged except for the explicitly marked **public-image derivative** of the legacy vocabulary inventory.

All six supplements are installed even when datasets are absent. A known member of any location set satisfies the union; an unresolved union produces an explicit coverage finding. Date checks compare calendar fields and offsets; insufficient precision or timezone information produces an explicit coverage finding even when advisory warnings are hidden. It is never silently treated as a pass.

The historical generation report remains unchanged. Reviewed replacement checks are reported separately in `effectiveCoverage`; document completion also requires all applicable data and determinate checks. It does not assert full FHIR model conformance.

## Prepare SVS data

Prepare assets and export complete XML from an authorized [VSAC session](https://vsac.nlm.nih.gov/). Review counts and source editions. A retired set can still be referenced by a pinned guide: review that choice explicitly; its status remains visible in metadata.

```json
[
  { "oid": "2.16.840.1.113883.3.88.12.80.28", "expectedCount": 62110 }
]
```

The number above records one previously reviewed export, not a permanent count for future releases. Import your reviewed checklist:

```sh
npm run assets
python3 scripts/import-svs.py \
  --input-glob '/private/path/RetrieveMultipleValueSets*.xml' \
  --expected /private/path/expected-counts.json \
  --output terminology
```

The SVS command imports the complete reviewed input collection and preserves other installed expansions and grammar configuration. Conflicting replacements require `--replace` after edition review. It rejects empty, duplicate, conflicting, partial, entity-bearing and count-mismatched exports. DTD rejection is performed by the XML parser and covers UTF-16. A failed import leaves the active manifest unchanged.

## Add complete FHIR expansions

Use `scripts/import-fhir-terminology.py` for other canonicals, including locally resolved US Core or source-specific expansions. Supply a reviewed checklist with the **ValueSet definition version** and complete count:

```json
[
  { "canonical": "https://example.org/ValueSet/example", "version": "reviewed-edition", "expectedCount": 42 }
]
```

Supply a separate reviewed map of FHIR system URIs to CDA OIDs. For example, LOINC’s URI is `http://loinc.org` and its CDA OID is `2.16.840.1.113883.6.1`. The tool never guesses an OID from a similar value-set identifier.

```sh
python3 scripts/import-fhir-terminology.py \
  --input /private/path/complete-expansion.json \
  --expected /private/path/reviewed-expansions.json \
  --systems /private/path/system-oids.json \
  --output terminology
```

The importer requires `expansion.total` to match both the reviewed count and all imported unique pairs, offset zero, an exact definition version and explicit system mappings. Limited, abstract, duplicate, incomplete or conflicting data is rejected. Updates that replace an existing expansion require `--replace` after reviewing the new edition. Other installed entries and grammar configuration are preserved. Filenames are content-addressed and the manifest is replaced atomically.

Both importers operate offline. Counts and hashes detect corruption and incomplete imports; they do not independently prove source authenticity, correct licensing or semantic equivalence to another edition.

## Import LOINC document types

Download **LOINC Complete 2.83** from [LOINC](https://loinc.org/downloads/) using your own account and review its license. Then run:

```sh
python3 scripts/import-loinc.py --input /private/path/Loinc_2.83.zip --output terminology
```

The importer accepts only the archive edition and SHA-256 pinned in `profiles/terminology-sources.lock.json`. It reads ZIP members without extracting them, selects all `SCALE_TYP=Doc` codes, and requires exact agreement with the primary `LP32888-7` SCALE part links. The reviewed release contains 112,405 table rows and 12,569 document-scale codes. The US Core 9.0.0 definition adds NullFlavor `UNK`, producing 12,570 exact membership pairs. No status filter is added. The normalized index contains identifiers only; the original archive stays local. New editions require source and count review before changing the lock. Existing datasets are preserved; conflicting replacements require `--replace`.

## Install UCUM and language grammars

Finite lists cannot cover all UCUM expressions or BCP 47 language tags. Download the [UCUM essence definition](https://github.com/FHIR/Ucum-java/blob/master/src/main/resources/ucum-essence.xml), the [IANA language-subtag registry](https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry) and the [IANA language extension registry](https://www.iana.org/assignments/language-tag-extensions-registry/language-tag-extensions-registry). Review their editions and SHA-256 hashes. Put the files in the terminology folder and add a `grammars` object to its manifest:

```json
{
  "schemaVersion": 1,
  "valueSets": [],
  "grammars": {
    "ucum": {
      "file": "ucum-essence.xml",
      "sha256": "REVIEWED_SHA256",
      "version": "REVIEWED_UCUM_EDITION"
    },
    "language": {
      "file": "language-subtag-registry.txt",
      "sha256": "REVIEWED_SHA256",
      "version": "REVIEWED_IANA_FILE_DATE",
      "extensionsFile": "language-tag-extensions-registry.txt",
      "extensionsSha256": "REVIEWED_SHA256"
    }
  }
}
```

Preserve existing `valueSets` when editing the manifest. UCUM uses `org.fhir:ucum:1.0.10`; XML DTDs are prohibited before definition parsing. Language checks use strict BCP 47 syntax, the installed registered subtags, grandfathered/redundant tags, private-use ranges, extlang prefixes and registered extension singletons. Registered extension singletons do not prove the semantic validity of extension keys/types; tags containing such extensions produce indeterminate coverage. Private-use tags remain supported. Finite snapshots for these canonicals remain inactive and cannot satisfy grammar readiness.

## Install the authorized C-CDA 2.1 vocabulary

Obtain the pinned upstream `voc.xml` under terms covering your use. The installation tool verifies its expected SHA-256 from the application asset manifest and copies it to the operator folder. It does not grant or verify a license.

```sh
mkdir -p terminology
docker run --rm --entrypoint java \
  --user "$(id -u):$(id -g)" \
  -v "$PWD/terminology:/app/terminology" \
  -v "/private/path/voc.xml:/input/voc.xml:ro" \
  IMAGE -cp /app/app.jar org.cdavalidator.TerminologyTool \
  install-legacy /input/voc.xml
```

Replace `IMAGE` with your selected application image. The folder may contain both this vocabulary and the C-CDA 5.0 pack. Incorrect editions and altered files are rejected. No schema or Schematron rules can be uploaded through these commands.

## Mount, diagnose and run

```sh
docker run --rm --entrypoint java \
  -v "$PWD/terminology:/app/terminology:ro" \
  IMAGE -cp /app/app.jar org.cdavalidator.TerminologyTool doctor
```

The doctor exits with code 2 while datasets are missing. A ready dataset inventory does not imply full profile conformance coverage. To start the service:

```sh
docker run -d --name cda-validator-coverage \
  --memory=1536m --cpus=2 --pids-limit=128 \
  --read-only --tmpfs /tmp:size=64m,noexec,nosuid \
  --cap-drop=ALL --security-opt=no-new-privileges \
  -e JAVA_TOOL_OPTIONS=-Xmx1g \
  -p 127.0.0.1:17645:17643 \
  --mount "type=bind,source=$PWD/terminology,target=/app/terminology,readonly" \
  IMAGE
```

Compose uses `${CDA_TERMINOLOGY_DIR:-./terminology}`. For a local JVM, set `TERMINOLOGY_DIR` to the operator directory. Restart to load changed data; the running engine uses immutable verified indexes. Existing applications on other ports remain untouched.

Paths must stay inside the operator folder. Snapshot files are bounded to 64 MiB and 1,250,000 total entries; each grammar file is bounded to 5 MiB and the legacy vocabulary to 150 MiB. Startup verifies checksums and counts. Raw exports, normalized indexes and local vocabularies are excluded from Git and the public build context.

For memory regression with an operator pack:

```sh
python3 scripts/check-upload-memory.py \
  --image cda-validator:coverage-followup \
  --terminology-dir service/assets/terminology \
  --output .cache/coverage-upload-memory.json
```

## Prepare US Core condition and procedure sets from UMLS

Download the licensed **UMLS 2026AB full archive** through your own UMLS access and **LOINC Complete 2.83** through LOINC. No credentials or license agreements are handled by the importer.

```sh
python3 scripts/import-umls.py \
  --input /private/path/umls-2026AB-full.zip \
  --loinc /private/path/Loinc_2.83.zip \
  --output terminology
```

The archive, recipe and source-row counts are pinned in `profiles/umls-sources.lock.json`. Allow approximately 2.4 GB temporary disk space for the nested metadata archive; it is removed after import. The importer rejoins arbitrary gzip fragments before decoding complete RRF records. It derives SNOMED descendants from source-asserted MRHIER AUI paths and SCUI identifiers, never from shared UMLS CUIs. Before updating a pack it requires exact agreement with an installed independent Procedure snapshot, when present. No imported codes are distributed with the software.

The recipe includes full LOINC table identifiers, appropriate CPT/CDT/HCPCS clinical identifiers, ICD-10-CM/PCS and ICD-9-CM diagnosis/procedure identifiers with the authoritative CDA system OIDs. It excludes source headings, ranges and partial axes. Standalone CPT/HCPCS modifiers and unsupported SNOMED expressions are explicitly indeterminate. The recipe records exact source editions: SNOMED US September 2026, CPT 2026, **CDT 2025**, HCPCS 2026, ICD-10-CM/PCS 2027, ICD-9-CM 2014 and LOINC 2.83. These are the reviewed archive's editions; a new edition requires review rather than relabeling old data.

The two US Core 9.0.0 definitions yield **262,583 condition** and **274,743 procedure** membership pairs. The SNOMED Procedure subset has 62,110 pairs and exactly matches the independently obtained VSAC expansion. These counts are for this pinned recipe, not promises about future editions.

## Remaining dataset and coverage boundaries

The verified local pack contains **29 snapshots / 1,013,055 membership entries**, with 28 active indexes and two grammars. The one absent canonical is NUBC patient discharge disposition. No NUBC source was present in UMLS. Operators who need this check must supply an authorized, complete versioned expansion using the FHIR importer; no scraped discharge-status list is bundled. Until installed, any applicable binding yields incomplete coverage even when warnings are hidden.

`codeSystemEditions` records supported aliases. An explicit document `codeSystemVersion` that does not match the installed reviewed edition produces coverage findings rather than silently using a different edition. Absent version attributes use the disclosed installed edition. This does not guarantee that every other historical edition or code expression is supported.

CPT/ADA CDT and other source permissions still apply. Operator-supplied data is not a blanket exemption from product licensing; see the linked licensing guidance above. The codebase, npm archive and image omit raw and normalized operator data.

Targeted offline reference checks are described in [coverage evidence](coverage.md#independent-checks). Full FHIR StructureDefinition and clinical conformance are outside this service's stated validation scope.
