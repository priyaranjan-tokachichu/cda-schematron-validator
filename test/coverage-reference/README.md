# Targeted reference probes

Synthetic documents with intentional unrelated validation errors. Each case tests only the named name/date constraint or CarePlanAct type-union behavior, not overall document validity. See `cases.json` and `reports/reference-comparison.json`.

The recorded reference is HL7 FHIR Validator 7.0.6 with `hl7.cda.us.ccda#5.0.0`, profile `http://hl7.org/cda/us/ccda/StructureDefinition/ContinuityofCareDocumentCCD`, `-version 5.0.0` and `-tx n/a`. After caching its official dependencies, the reference run used a container with networking disabled. Use the same input documents for `/v1/validate` with profile `ccda-5.0` and warnings enabled. Reference terminology was disabled; this comparison cannot establish terminology equivalence.

Six definite name/date expectations agree. Partial-date comparison remains explicitly indeterminate in the service. The three CarePlanAct probes test unprofiled base CDA children in a union; they do not prove complete StructureDefinition equivalence or close the original generation errors.
