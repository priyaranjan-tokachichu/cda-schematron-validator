# Operator-supplied terminology

[← Coverage inventory](coverage.md) · [Deployment](deployment.md)

This checkout adds **unreleased** coverage and packaging changes after `2.0.0-preview.2`. Existing published images are unchanged. New public images omit the entire mixed-license C-CDA 2.1 `voc.xml`, which contains CPT entries. The service can start and show the UI without it; C-CDA 2.1 schema checks run, and its Schematron stage explicitly reports setup required until an authorized pinned vocabulary is installed. The legacy npm API remains compatible and accepts caller-supplied validation resources.

## Licensing and source selection

The application’s MIT license does not license terminology data. A user’s CPT license does not automatically grant this project distribution rights. Giving the application away or adding a notice does not replace the required agreement. AMA’s [licensing guidance](https://www.ama-assn.org/practice-management/cpt/cpt-licensing-frequently-asked-questions-faqs) also covers CPT-dependent products, so user-supplied data is not a blanket licensing exemption for the application. No CPT distribution agreement has been established for this project.

[LOINC permits redistribution under conditions](https://loinc.org/kb/license), including notices and applicable third-party terms; it does not require every user to download their own copy. We support operator-supplied LOINC alongside other sources. The local UMLS 2026AB metadata identifies LOINC 2.83; the standalone [LOINC Complete distribution](https://loinc.org/downloads/) contains additional tables useful for producing reviewed expansions. This importer accepts complete FHIR expansions, rather than treating an entire source download as an already resolved value set.

Use authorized VSAC exports, source-system distributions, or complete FHIR expansions from an appropriately configured terminology service. Resolve licensing for the intended deployment. The software does not accept terms, create accounts or store terminology credentials. Never send clinical XML to obtain an expansion.

## Checks and data readiness

`GET /v1/profiles` exposes:

- `supplementalChecks`: person-name, procedure timestamp, conditional procedure-membership and conditional location-union checks.
- `supplementalBindingChecks`: installed checks at the exact locations in the checksum-verified HL7 binding catalog. Required bindings produce errors; preferred and extensible bindings preserve the upstream warning severity.
- `terminologySetup`: exact missing and installed value-set canonicals, a data-readiness flag and the setup guide URL. Readiness is separate from full validation coverage.
- `terminologySnapshots` and `grammarDefinitions`: pinned editions, counts and hashes. Unknown or unimplemented canonicals remain inactive.

Code/system pairs use exact membership. Bindings on primitive code attributes use exact code membership. Each supplemental binding has a stable ID derived from its canonical, context and strength, and runs in a separate pattern so overlapping contexts cannot suppress another binding. The original HL7 assets and generation report remain unchanged except for the explicitly marked **public-image derivative** of the legacy vocabulary inventory.

The local Procedure expansion enables `1198-8487` and `1198-8511`. All three location expansions must be installed before `shall-use-one-binding` is enabled. Date checks compare calendar fields and offsets; insufficient precision or timezone information produces an explicit coverage finding even when advisory warnings are hidden. It is never silently treated as a pass.

C-CDA 5.0 still reports incomplete coverage while generation issues and independent conformance verification remain unresolved. Installing data does not clear those issues.

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

Preserve existing `valueSets` when editing the manifest. UCUM uses `org.fhir:ucum:1.0.10`; XML DTDs are prohibited before definition parsing. Language checks use strict BCP 47 syntax, the installed registered subtags, grandfathered/redundant tags, private-use ranges, extlang prefixes and registered extension singletons. Finite snapshots for these canonicals remain inactive and cannot satisfy grammar readiness.

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

Paths must stay inside the operator folder. Snapshot files are bounded to 64 MiB and 1,000,000 total entries; each grammar file is bounded to 5 MiB and the legacy vocabulary to 150 MiB. Startup verifies checksums and counts. Raw exports, normalized indexes and local vocabularies are excluded from Git and the public build context.

For memory regression with an operator pack:

```sh
python3 scripts/check-upload-memory.py \
  --image cda-validator:coverage-followup \
  --terminology-dir service/assets/terminology \
  --output .cache/coverage-upload-memory.json
```

## Remaining prerequisites

The verified local pack contains 27 snapshots (475,729 membership entries), of which 26 are active, plus UCUM and language grammars. All six supplemental invariants are enabled with this pack; the binding catalog installs 47 additional checks. The three missing canonical datasets are US Core condition codes, US Core procedure codes and encounter discharge disposition. UMLS 2026AB lists CDT 2025, CPT 2026, HCPCS and diagnosis sources, but no NUBC source was found. Inventory presence is not proof of a complete value-set expansion or licensing entitlement.

The US Core procedure definition also includes ADA CDT, LOINC, SNOMED CT, HCPCS and ICD-10-PCS. Licensing requirements apply to each source. A free application and a license notice do not replace an AMA distribution agreement; operator-supplied CPT also does not automatically exempt a CPT-dependent product from AMA product licensing. See the [AMA licensing FAQ](https://www.ama-assn.org/practice-management/cpt/cpt-licensing-frequently-asked-questions-faqs).

Targeted offline comparisons with HL7 FHIR Validator 7.0.6 agree on six definite name/date probes. The service explicitly reports indeterminate coverage for a partial date that the reference does not reject. Terminology was disabled in the reference run, so this does not establish terminology equivalence. CarePlanAct generation-error review and broader model comparison remain release work. The historical generation report is preserved and C-CDA 5.0 `validationComplete` remains false.
