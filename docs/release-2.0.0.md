# 2.0.0 stable release

Version 2.0.0 is the stable release line prepared in PR #53. The npm package is published under `latest`; container images use the immutable version tag `v2.0.0`. Earlier preview artifacts remain unchanged.

## What version 2 provides

A self-hosted container with a browser XML workspace and structured HTTP validation API; pinned CDA R2/SDTC, C-CDA 2.1 and C-CDA 5.0 resources; detailed findings, source provenance and explicit coverage; a Node/TypeScript HTTP client and CLI; and the seven existing in-process JavaScript exports.

The pipeline is **pinned XSD and Schematron with reviewed supplements**. It is not full FHIR StructureDefinition validation or clinical certification. See [coverage semantics](coverage.md).

## Changes after preview.2

- Replace the reviewed generator omissions with person-name, procedure, timestamp, location-union, CarePlan base-root and catalog binding checks. Preserve the historical report; changed reports require review.
- Evaluate completion per document. Missing applicable data and indeterminate checks remain explicit even when advisory warnings are hidden. Unused optional data does not force every document incomplete.
- Derive reviewed US Core condition/procedure indexes offline from authorized UMLS 2026AB and LOINC Complete 2.83. Keep source editions and provenance; never bundle the operator pack.
- Require authorized operator installation of the mixed-license C-CDA 2.1 vocabulary in new public images. Schema checks still run without it, while the Schematron stage reports setup required. This changes container setup, not the legacy JavaScript API contract.
- Use digest-pinned Temurin 25.0.5.1+2 LTS on AMD64 and ARM64, including the November CVE-2026-114938 fix. Expose the actual JVM version and check it in CI; watch upstream LTS releases.
- Keep the 20 MiB XML default, streaming JSON decoding, shared upload memory budget and bounded fair queue. Preserve input-revision checks against stale UI results.
- Use independent blue/slate interface styling and refresh the standalone HTML architecture report.

## Upgrade requirements

The npm package requires **Node 22.12+**. Local service builds require **JDK 25**; running the container requires no host Java. Existing legacy calls keep their synchronous/Promise contracts but remain a partial Schematron interpreter without XSD validation.

New containers need a read-only operator terminology volume. Follow [setup](terminology.md), including authorized legacy vocabulary installation for C-CDA 2.1 Schematron. CPT/ADA and other source rights are separate from the application license. No data license is granted by installation.

The verified local pack has 29 snapshots / 1,013,055 membership entries, 28 active indexes and two grammars. NUBC discharge status remains operator-supplied. An applicable missing NUBC check prevents completion. Unsupported expressions/modifiers, mismatched editions and indeterminate grammar/timestamp cases also remain incomplete.

## Verification

65 JavaScript, 45 Java and 17 Python tests pass. Both architecture builds and actual runtime versions are checked. Fifteen targeted synthetic probes agree with an independent HL7 FHIR validator; terminology was disabled in that reference run, so no full-model or terminology equivalence is claimed. A broader comparison is inconclusive without matching terminology.

Six concurrent maximum-size uploads complete in both framing modes with the full local pack on a 1 GiB heap. Health remains available and no retries are needed for admitted uploads. Current [evidence](../reports/coverage-progress.json), [image scan](../reports/security.json) and [HTML report](../reports/architecture.html) record the exact scope and results.

## Release publication

Release publication merges the owner-authorized PR through GitHub, publishes versioned AMD64/ARM64 container artifacts from that commit, publishes npm 2.0.0 with the `latest` tag, then verifies public install, image startup and runtime metadata. Keep immutable preview artifacts intact. npm may require its separate per-publication authentication.
