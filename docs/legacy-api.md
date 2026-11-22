# Legacy JavaScript API and migration

[← Project home](../README.md) · [Modern API](api.md) · [Historical documentation](history.md)

The original JavaScript APIs remain local and retain their result shape and synchronous/Promise contracts. They do not require the Java service or Docker. The version 2 package targets Node **22.12+**; older runtimes should retain a compatible earlier release.

> [!IMPORTANT]
> The legacy implementation is a **partial Schematron interpreter**, with no XSD validation. Installing the modern package does not turn `validate()` into the full service engine. Use `createClient()` for the modern pipeline.

## Exports preserved

| Export | Behavior |
| --- | --- |
| `validate(xml, schematron, options)` | Synchronous validation |
| `validateAsync(xml, schematron, options)` | Promise returning one legacy result |
| `validateFileListAsync(documents, schematron, options)` | Ordered results with `fileIndex` |
| `validateFileObjectAsync(documents, schematron, options)` | Results with `fileInfo` |
| `parseSchematron(document, options)` | Parse a DOM Schematron document into a reusable map |
| `clearCache()` | Clear synchronous validator caches |
| `clearCacheAsync()` | Clear asynchronous validator caches |

The additional `createClient()` export connects to the HTTP service. Existing exports were not replaced with network calls.

## Synchronous example

```javascript
const { readFileSync } = require('node:fs');
const { validate } = require('cda-schematron-validator');

const result = validate(
  readFileSync('document.xml', 'utf8'),
  readFileSync('rules.sch', 'utf8'),
  { includeWarnings: true }
);
console.log(result.errorCount, result.errors);
```

Legacy calls accept XML/Schematron strings or file paths. `schematron` may be `null` when `options.parsedSchematronMap` is supplied. Unlike the modern service, legacy findings may include XML snippets.

## Results and options

Results retain `errorCount`, `warningCount`, `ignoredCount`, `errors`, `warnings`, and `ignored`. Ignored findings should be reviewed when assessing interpreter coverage; a zero error count does not establish full CDA conformance.

| Option | Purpose |
| --- | --- |
| `includeWarnings` | Default **false** for legacy calls; modern API default is true |
| `resourceDir` | Resolve legacy external vocabulary/document resources |
| `xmlSnippetMaxLength` | Bound snippets in legacy findings |
| `parsedSchematronMap` | Reuse a parsed Schematron map |

[Type declarations](../validator.d.ts) describe result/finding fields and batch signatures. The [archived documentation](history.md) preserves inherited examples, including the parent package name; use `cda-schematron-validator` for this package.

## Correctness fixes in version 2

Document context caches are isolated, caller options are not mutated, asynchronous empty patterns settle, batches retain input ordering, and rules without IDs receive generated identifiers. Resource caches distinguish absolute paths and file versions, with a bounded parsed-map cache. Missing files and empty input fail explicitly.

These fixes correct previously incorrect results. They do not preserve false passes as compatibility behavior.

## Move to the modern pipeline

1. Run the container service.
2. Replace local validation calls with `createClient().validate(xml, { profile })` where full XSD/official-package execution is needed.
3. Consume modern `findings` and both completeness flags; do not assume legacy result fields on the new response.
4. Keep existing legacy calls for integrations that still require their established contract.

The modern service accepts installed profile IDs and XML text; it does not accept your arbitrary Schematron, resource directory, file path, or parsed legacy map.

## Release boundary

Version 2.0.0 preserves the seven legacy exports and adds the HTTP client and CLI. It requires Node 22.12+. Version `1.1.12` remains available for older integrations with an explicit version selection. Install the stable package without `@next`; preview builds remain separately versioned. The Java service is distributed separately as a container. Merging alone does not publish either artifact.
