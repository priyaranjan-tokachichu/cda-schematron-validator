# Local terminology snapshots

[← Coverage inventory](coverage.md) · [Deployment](deployment.md)

This is development work following `2.0.0-preview.1`; the published preview.1 image does not contain this adapter. The public distribution does not include the operator's licensed terminology exports.

## What this adapter checks

When a reviewed Procedure expansion is installed, the service restores the omitted `1198-8487` and `1198-8511` checks at the operative-note and procedure-note service-event code locations. It preserves the invariant's ICD-9-CM procedure, ICD-10-PCS, and CPT alternatives, and the additional HCPCS alternative for procedure notes. SNOMED CT membership uses an exact code-system/code pair, with no substring matching.

The supplied expansion is a frozen local snapshot. Its code-system versions, definition revision, source status, retrieval time, original export checksum, normalized checksum, and concept count appear in `terminologySnapshots`. Only the Procedure index currently has `activeForValidation: true`; other imported snapshots are verified and disclosed without being represented as implemented binding coverage. Their indexes are not retained in memory.

The person-name supplement separately reapplies the pinned patient-name constraints at person-name locations, preserving the allowed plain-text alternative. `supplementalChecks` identifies the added checks. The original upstream report and counts are retained. **C-CDA 5.0 completeness remains false:** remaining date, location, generation, and terminology gaps have not been cleared.

## Prepare a local pack

1. Export the required expansions as XML from an authorized [VSAC session](https://vsac.nlm.nih.gov/). Include retired sets only when the pinned guide references them, and retain their status for review.
2. Record each displayed full code count in a local JSON checklist. For example:

```json
[
  { "oid": "2.16.840.1.113883.3.88.12.80.28", "expectedCount": 62110 }
]
```

This is the count observed for the downloaded snapshot, not a permanent count to assume for future exports. Record and review the actual count for your selected version.

3. Import the checked exports after preparing the standard assets:

```sh
npm run assets
python3 scripts/import-svs.py \
  --input-glob '/private/path/RetrieveMultipleValueSets*.xml' \
  --expected /private/path/expected-counts.json \
  --output service/assets/terminology
```

The importer rejects empty, duplicate, conflicting, entity-bearing, incomplete, and count-mismatched exports. All reviewed OIDs must be present. It validates all inputs before changing the manifest and uses content-addressed data filenames. Original XML files are not copied into the public package.

`service/assets/` and `.cache/` are ignored by Git and excluded from the Docker build context. The normal multi-stage image prepares only the pinned public validation assets; it does not copy this local pack. Keep exports outside Git and apply the terms of each terminology source to any redistribution.

## Run the development image with the local pack

Build the reviewed development checkout, then use a read-only asset mount. The following uses a separate local port so an installed release can remain running:

```sh
docker build -t cda-validator:coverage-review .
docker run -d --name cda-validator-coverage \
  --memory=1536m --cpus=2 --pids-limit=128 \
  --read-only --tmpfs /tmp:size=64m,noexec,nosuid \
  --cap-drop=ALL --security-opt=no-new-privileges \
  -e JAVA_TOOL_OPTIONS=-Xmx1g \
  -p 127.0.0.1:17644:17643 \
  --mount "type=bind,source=$PWD/service/assets,target=/app/assets,readonly" \
  cda-validator:coverage-review
```

Open `http://localhost:17644`. The service verifies the normalized file hashes and exact counts before accepting the pack. The parser is strict, paths must remain inside the trusted asset directory, total normalized files are bounded to 64 MiB, and the pack is bounded to 1,000,000 concept entries. Loading additional data alone does not turn completeness into a pass. There are no terminology network calls during validation and submitted clinical documents never enter a download request.

More heap/container memory can support larger deployments; use the existing [capacity guidance](deployment.md#increase-concurrent-large-upload-capacity). The constrained-heap regression can also exercise a local pack:

```sh
python3 scripts/check-upload-memory.py \
  --image cda-validator:coverage-review \
  --assets-dir service/assets \
  --output .cache/upload-memory-with-terminology.json
```

## Data recovered during this work

The authorized local VSAC exports contain 20 expansions and 383,629 total membership entries. Nineteen match VSAC canonicals in the upstream omission report; the additional occupational candidate is kept distinct from the PHINVADS canonical. These data are local and are not committed or published. Two exported sets are marked retired, and remain inactive in the adapter.

The published C-CDA 5.0 FHIR package declares dependencies on `us.cdc.phinvads#0.12.0`, `hl7.fhir.us.core#9.0.0`, and `hl7.terminology.r5#7.1.0`. The CDC package includes the explicit occupational lists of 19,254 and 59,583 concepts that the generator excluded. Those definitions were recovered separately; their PHINVADS identities are not silently replaced with similarly numbered VSAC data. A downloaded UMLS archive is a resource for further source-specific work, not an executable complete value-set validator by itself.

Further binding integration, grammar-based language/UCUM checks, source-system mappings, and independent validation comparisons remain necessary. This adapter currently enables only the two procedure invariants described above.
