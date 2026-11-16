# Security policy

## Reporting a vulnerability

Use GitHub private vulnerability reporting for sensitive security reports when available. If it is unavailable, open a minimal issue requesting a private reporting channel. Do not post patient data, credentials, tokens, or sensitive exploit details in a public issue.

Public issues are welcome for ordinary bugs with synthetic examples. Pull requests are reserved for the repository owner; external reporters should use issues or private vulnerability reports rather than opening a branch or PR.

## Versions and validation coverage

The modern service is currently version `2.0.0-preview.1`. Its official resource versions, checksums, generation gaps, and execution completeness are exposed in validation reports. A preview passing its configured tests is not a certification of clinical correctness or complete conformance.

The legacy JavaScript API is retained with regression tests. Existing 1.x packages do not contain the new service, input boundaries, resource updates, or isolation fixes merely because this branch does. Security fixes should target the current development/release line; any historical backport is an explicit maintainer decision.

## Deployment responsibilities

The provided Compose service is local-only by default, runs without root, and uses a read-only filesystem and bounded resources. Shared deployments must configure authenticated HTTPS and their own access, proxy logging, retention, and monitoring policies.

The application does not persist submitted XML or log finding contents. Reports may contain document values and must be treated as sensitive by clients. Validation deadlines are cooperative; use stronger process isolation if a deployment requires guaranteed per-document hard termination.

Dependency and container reviews must be repeated for releases. A point-in-time audit result is not a promise that software is free of vulnerabilities.
