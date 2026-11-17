# C-CDA 5.0 coverage remediation

[← Architecture](architecture.md) · [Official HL7 validation guidance](https://hl7.org/cda/us/ccda/5.0.0/validation.html)

This inventory is derived from the checksum-pinned `2026JUL/CCDA-5.0-Results.json`. It records unresolved upstream omissions, not completed fixes. The full report is also exposed as `generationReport` in profile metadata. `generationCoverage` counts individual entries rather than reason categories.

**Current totals:** 4 generation errors, 6 unhandled invariants in 4 categories, and 28 non-loaded value-set entries in 9 categories. Counts overlap: an expansion failure can also cause an invariant omission. Do not add these counts together as independent missing rules.

The earlier summary of 4 invariants and 9 value sets counted categories and understated the omissions. This preview corrects the accounting; it does not turn missing checks into passes.

## Local person-name check (follow-up to preview.1)

The local `81-9371` supplement reuses all 11 assertions in the pinned patient-name Schematron profile at the person-name locations, with the permitted plain-text alternative. List-valued use and qualifier attributes are checked token by token against complete vocabulary codes. This adds the missing given/family cardinality, suffix, use, qualifier, and mixed-content checks at those locations. The existing `81-9372` prohibition on mixing text and name parts still runs.

The check is layered onto the in-memory compilation input. Original downloaded files and the upstream generation report remain unchanged. Profile metadata lists the added rule in `supplementalChecks`; the application still reports incomplete C-CDA 5.0 coverage. This is reuse of the pinned Schematron constraints, not a general FHIR `conformsTo()` implementation.

Tests exercise valid fielded names, plain strings, null flavors, missing/duplicate name parts, multiple suffixes, invalid use/qualifier values, mixed content, and rejection of an unexpected upstream profile layout. This change is separate from the published preview.1 artifact and requires review before inclusion in a later release.

## Local procedure checks and recovered data

The optional [offline terminology adapter](terminology.md) restores `1198-8487` and `1198-8511` when a reviewed Procedure snapshot is installed. It checks exact SNOMED CT code/system membership and preserves the original alternative code systems. Other recovered expansions are not counted as implemented binding checks simply because their data are now available.

## Rule-conversion work

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

CarePlanAct profile-reference errors need comparison against the authoritative StructureDefinitions before adding any rule. A base CDA class without a template identifier cannot safely be assigned an invented templateId. ActPriority requires its dependent callback value-set definition and a complete expansion.

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

## Acceptance criteria for closing a gap

1. Trace the fix to a pinned official definition, terminology edition, or precisely identified invariant. Preserve the original upstream report and record local additions separately.
2. Add positive and negative examples that exercise the missing constraint, including nullFlavor, absent data, and relevant boundary cases. Compare against an independent reference where feasible.
3. Verify vocabulary completeness, provenance, permissions, and checksum. An unavailable external source remains an explicit unresolved gap.
4. Run both profiles, legacy compatibility, memory/concurrency tests, and compile/startup benchmarks.
5. Only remove an omission from the effective coverage summary after its replacement check and evidence exist. Do not clear the upstream report or force `validationComplete` to true.

## Release policy

Version 2 is a preview while this coverage work is unresolved. C-CDA 2.1 remains available with its bundled vocabulary snapshot. The stable npm default stays 1.1.12 until a separately authorized stable release. The preview is explicitly selected with `@next`.
