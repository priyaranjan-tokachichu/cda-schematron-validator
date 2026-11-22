# C-CDA 5.0 validation coverage

[← Architecture](architecture.md) · [Operator setup](terminology.md) · [HL7 validation guidance](https://hl7.org/cda/us/ccda/5.0.0/validation.html)

The service validates the **pinned CDA XSD and C-CDA Schematron, with reviewed local supplements**. It does not implement general FHIR StructureDefinition validation or clinical certification. Every report states its `validationScope`.

## Completion has a precise meaning

- `executionComplete`: the configured engine stages finished without a failure.
- `validationComplete`: execution finished, a supported document-level template matched, findings were not truncated, the pinned generator omissions have reviewed replacements, and no applicable check was indeterminate.
- `status`: `valid` requires complete validation and no errors. An error makes the document `invalid`, even if coverage is also incomplete. No errors with incomplete coverage gives `incomplete`.

A completed invalid report is possible: all applicable checks ran and found errors. Completion describes the selected pipeline, not every possible CDA/FHIR or clinical constraint.

Missing data produces a coverage finding **where a document needs that data**, including preferred bindings when warnings are hidden. Unrelated absent optional bindings do not make every document incomplete. The dataset inventory remains separate: an installation without NUBC is not data-ready even if a document that never uses that binding completes.

## Reviewed replacements for the pinned generation report

The unchanged upstream report lists **4 generation errors, 6 unhandled invariants and 28 non-loaded value-set entries**. Entries overlap. `generationReport` and `generationCoverage` preserve this historical evidence. `effectiveCoverage` accounts for the replacements; the report SHA-256 must match the reviewed release. A new upstream report requires review and cannot automatically inherit completion.

| Original omission | Implemented replacement |
| --- | --- |
| Person name `81-9371` | Reuse all 11 pinned patient-name assertions at the relevant name locations, including the permitted plain-text alternative. List-valued use/qualifier attributes are checked token by token. |
| Procedure `1198-8487`, `1198-8511` | Exact SNOMED CT Procedure membership and the original alternative code systems. Missing or indeterminate membership produces coverage findings. |
| Two procedure timestamps | Calendar and timezone comparisons with conservative handling of insufficient precision. Unknown comparisons remain coverage findings. |
| Location `shall-use-one-binding` | Three-set union: one known member establishes membership; all known nonmembers fail; an unresolved union is incomplete. |
| ActPriority expansion | Reviewed callback hierarchy and admission-urgency union from pinned HL7 terminology definitions. |
| Three CarePlanAct unions | Authoritative alternatives include the unprofiled base CDA Act or Observation, so no templateId is invented. 103 scoped assertions restore base-root cardinalities, fixed values, required bindings and II constraints, alongside XSD and narrower template rules. |
| Catalog binding omissions | Runtime checks at every recorded binding location. Required bindings are errors; preferred/extensible bindings retain warning severity. Missing data is always disclosed. |

CarePlan base checks are generated reproducibly from checksum-recorded CDA Core 2.0.3-sd, C-CDA 5.0.0 and HL7 Terminology 7.1.0 definitions. This is a scoped supplement, not a general `conformsTo()` implementation. See the embedded [model receipts](../service/src/main/resources/coverage/careplan-base.json).

## Terminology and conservative boundaries

The verified local operator pack contains **29 snapshots, 1,013,055 membership entries, 28 active indexes and two grammars**. It enables all six supplements and the catalog checks listed in current [verification evidence](../reports/coverage-progress.json). Data stays outside Git, npm and public images.

US Core condition and procedure sets are derived from the authorized UMLS 2026AB archive and LOINC Complete 2.83. SNOMED ancestry uses source-asserted AUI hierarchy paths, not UMLS concept similarity. The 62,110-member Procedure hierarchy exactly matches the independently acquired VSAC snapshot. US Core condition has 262,583 pairs; procedure has 274,743. Source editions are exposed, including **CDT 2025**, not an invented newer edition.

The only remaining absent canonical in that local inventory is **NUBC patient discharge status**. Operators must provide an authorized complete expansion; no scraped substitute is distributed. Missing applicable data prevents completion. See [setup](terminology.md).

Other explicit boundaries prevent false passes: unsupported SNOMED expressions, standalone CPT/HCPCS modifiers, mismatched declared code-system editions, ambiguous scalar vocabulary values, excessive grammar complexity, and unvalidated BCP 47 extension semantics are indeterminate. UCUM expressions use a pinned grammar; language tags use the November 18, 2026 IANA registry. Whole finite-code strings use exact membership, not substring matching.

## Independent checks

[Reference evidence](../reports/reference-comparison.json) records targeted comparisons against HL7 FHIR Validator 7.0.6, including negative CarePlan base constraints. Both validators run locally. Reference terminology is disabled, so this establishes neither terminology equivalence nor full-model equivalence. Broad FHIR slicing comparisons remain inconclusive without matching terminology and are recorded as such; they are not counted as passing comparisons.

## Historical conversion inventory

| Invariant | Template | Why omitted | Required fix and verification |
| --- | --- | --- | --- |
| `1198-8487` | `OperativeNote` | Cannot calculate memberOf, Value Set http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.88.12.80.28 is not loaded. | Resolve and pin the required terminology data, then verify positive/negative membership and allowed alternative code systems. |
| `1198-8511` | `ProcedureNote` | Cannot calculate memberOf, Value Set http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.88.12.80.28 is not loaded. | Resolve and pin the required terminology data, then verify positive/negative membership and allowed alternative code systems. |
| `active-high-ts-after-document` | `ProcedureActivityProcedure` | Unsupported function allTrue | Translate collection Boolean semantics and CDA timestamp comparisons; verify absent values, precision, timezone offsets, equality, and every comparison result. Do not compare timestamps as plain strings. |
| `ts-value-before-document` | `ProcedureActivityProcedure` | Unsupported function allTrue | Translate collection Boolean semantics and CDA timestamp comparisons; verify absent values, precision, timezone offsets, equality, and every comparison result. Do not compare timestamps as plain strings. |
| `shall-use-one-binding` | `ServiceDeliveryLocation` | Cannot calculate memberOf, Value Set http://hl7.org/cda/us/ccda/ValueSet/CMSPlaceOfServiceCodes is not loaded. | Resolve and pin the required terminology data, then verify positive/negative membership and allowed alternative code systems. |
| `81-9371` | `USRealmPersonNamePNUSFIELDED` | Unsupported function conformsTo | Execute the referenced patient-name profile at the name node; verify both valid fielded names and invalid names, including the allowed text alternative. |

### Generation errors

- CarePlanAct: Profile http://hl7.org/cda/stds/core/StructureDefinition/Act does not have an identifier
- CarePlanAct: Profile http://hl7.org/cda/stds/core/StructureDefinition/Observation does not have an identifier
- CarePlanAct: Profile http://hl7.org/cda/stds/core/StructureDefinition/Act does not have an identifier
- Failed to expand value set v3-ActPriority: Unable to expand dependent value set http://terminology.hl7.org/ValueSet/v3-ActPriorityCallback

The reviewed replacements above address this pinned report. Its original entries remain here for audit; they are not a claim that the omissions remain unimplemented.

## Terminology work

Large sets should use versioned indexed terminology snapshots or a deliberately configured terminology adapter. Increasing the generator’s inline-list limit alone can harm compilation cost and still leaves unavailable expansions unresolved. Keep runtime validation offline by default; never send a clinical document to a public terminology service.

Public specifications do not automatically grant permission to redistribute every underlying terminology. Establish the applicable source/version and redistribution terms before bundling external SNOMED CT, CPT, NUBC, or VSAC-derived data. This is a release dependency where data is unavailable, not a reason to silently accept an unverified code.

### too-costly

Split grammar-based validation from enumerated membership. For enumerated sets, obtain complete paginated/versioned expansions and verify totals; for SNOMED descendants use an edition-specific hierarchy snapshot.

- `http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1267.43 - too-costly: The value set 'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1267.38' expansion has too many codes to produce (>10000)`
- `http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1267.13 - too-costly: The value set 'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1267.13' expansion has too many codes to produce (>3000)`
- `http://hl7.org/fhir/ValueSet/all-languages - too-costly: The code System "urn:ietf:bcp:47" has a grammar, and cannot be enumerated directly`
- `http://hl7.org/fhir/us/core/ValueSet/us-core-condition-code - too-costly: The value set 'http://hl7.org/fhir/us/core/ValueSet/us-core-condition-code' expansion has too many codes to produce (>3000)`
- `http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.88.12.3221.8.9 - too-costly: The value set 'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.88.12.3221.8.9' expansion has too many codes to produce (>3000)`
- `http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1010.4 - too-costly: The value set 'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1010.5' expansion has too many codes to produce (>10000)`
- `http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1010.2 - too-costly: The value set 'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.88.12.80.17' expansion has too many codes to produce (>10000)`
- `http://hl7.org/fhir/us/core/ValueSet/us-core-documentreference-type - too-costly: The value set 'http://hl7.org/fhir/us/core/ValueSet/us-core-documentreference-type' expansion has too many codes to produce (>3000)`
- `http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.88.12.80.28 - too-costly: The value set 'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.88.12.80.28' expansion has too many codes to produce (>3000)`
- `http://hl7.org/fhir/us/core/ValueSet/us-core-procedure-code - too-costly: The code System "http://www.ama-assn.org/go/cpt" has a grammar, and cannot be enumerated directly`
- `http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1267.36 - too-costly: The value set 'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1267.36' expansion has too many codes to produce (>3000)`
### too-many-concepts

Obtain the complete expansion and store it in an indexed resource rather than a very large XPath literal. Verify membership, version, checksum, and full concept count.

- `http://phinvads.cdc.gov/fhir/ValueSet/2.16.840.1.114222.4.11.7900 (19254)`
- `http://phinvads.cdc.gov/fhir/ValueSet/2.16.840.1.114222.4.11.7901 (59583)`
- `http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.11.20.9.34 (1172)`
- `http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1240.1 (1709)`
- `http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1099.54 (1817)`
- `http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.1.11.14914 (1279)`
- `http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1267.25 (1282)`
### not-found: A definition for CodeSystem 'https://www.nubc.org/CodeSystem/PatDischargeStatus' could not be found, so the value set cannot be expanded

Resolve the named source/terminology-service failure against the intended edition. Pin the successful definition/expansion and verify complete membership; do not substitute an empty expansion or a different release.

- `http://terminology.hl7.org/ValueSet/v3-USEncounterDischargeDisposition`
### apostrophes-in-codes

Fix XPath literal escaping or use indexed vocabulary lookup; test apostrophes, quotation marks, and both together without altering codes. UCUM also requires grammar-aware validation where full enumeration is impossible.

- `http://terminology.hl7.org/ValueSet/v3-UnitsOfMeasureCaseSensitive (840)`
### Response from https://tx.fhir.org/r5 did not contain any codes in its expansion.

Resolve the named source/terminology-service failure against the intended edition. Pin the successful definition/expansion and verify complete membership; do not substitute an empty expansion or a different release.

- `http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1021.48`
- `http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1021.47`
- `http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1021.46`
- `http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1166.22`
### exception: The SNOMED CT Concept 1354756004 is not known

Resolve the named source/terminology-service failure against the intended edition. Pin the successful definition/expansion and verify complete membership; do not substitute an empty expansion or a different release.

- `http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1095.65`
### not-found: A definition for CodeSystem 'https://nahdo.org/sopt' could not be found, so the value set cannot be expanded

Resolve the named source/terminology-service failure against the intended edition. Pin the successful definition/expansion and verify complete membership; do not substitute an empty expansion or a different release.

- `http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.114222.4.11.3591`
### business-rule: Cannot process resource http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.11.20.9.46|20240606 at "filter" due to the presence of the modifier extension vsacOpModifier

Resolve the named source/terminology-service failure against the intended edition. Pin the successful definition/expansion and verify complete membership; do not substitute an empty expansion or a different release.

- `http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.11.20.9.46`
### not-found

Locate the canonical value-set definition in the pinned guide/dependencies and obtain the complete code-system data. Preserve canonical identity and provenance.

- `http://hl7.org/cda/us/ccda/ValueSet/CMSPlaceOfServiceCodes`

## Update and release policy

Preserve source identities, editions, checksums and the historical generator report when updating. Re-run positive/negative rules, legacy compatibility, encoded/decoded limits, constrained-heap concurrency and startup/performance checks. New omission categories must be reviewed before effective coverage can be complete.

Version 2.0.0 is the stable line for this bounded validation pipeline. It does not claim full FHIR or clinical certification. Older integrations can explicitly select 1.1.12; immutable preview artifacts remain available under their versioned identifiers.
