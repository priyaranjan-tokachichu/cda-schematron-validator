# Deployment and capacity

[← Project home](../README.md) · [API reference](api.md) · [Architecture](architecture.md)

This unreleased checkout mounts operator terminology separately using `CDA_TERMINOLOGY_DIR` (Compose) or `TERMINOLOGY_DIR` (JVM). New public images omit the mixed-license C-CDA 2.1 vocabulary; install an authorized pinned copy using the [setup guide](terminology.md) before using that profile’s Schematron checks. Existing published preview.2 images are unchanged.

## Run locally

```sh
docker compose up --build -d
```

Open [localhost:17643](http://localhost:17643). Compose binds the published port to `127.0.0.1`. Java and all validation resources are inside the image; ordinary validation is offline. The first build needs network access for verified resources and dependencies.

```sh
docker compose logs validator
docker compose down
```

The owner-triggered release workflow publishes versioned AMD64/ARM64 images for `ghcr.io/priyaranjan-tokachichu/cda-schematron-validator`, with SBOM and provenance. A merge alone does not publish an image. The patched `v2.0.0` image is a release candidate and is not published yet. Existing preview images do not contain these new runtime pins. Build this checkout to use the patched candidate now.

Build and run the candidate directly:

```sh
docker build -t cda-validator:local .
docker run -d --name cda-validator --memory=1536m --cpus=2 \
  --read-only --tmpfs /tmp:size=64m,noexec,nosuid \
  --cap-drop=ALL --security-opt=no-new-privileges --pids-limit=128 \
  -p 127.0.0.1:17643:17643 \
  cda-validator:local
```

## Patched Java runtime

Build and runtime images are digest-pinned to **Eclipse Temurin 25.0.5.1+2 LTS** on AMD64 and ARM64. This includes the November fix for [CVE-2026-114938](https://adoptium.net/es/news/2026/11/temurin-november-2026-cspu-java4shell). Java 27 is the newer feature line; its official image had mixed patch levels across architectures when reviewed. The selected LTS is the latest patched LTS for both supported targets, not a claim that 25 is the latest feature release.

`profiles/runtime.lock.json` records the exact versions and digests. CI checks the built runtime; the upstream watch flags a newer LTS release for review. `GET /v1/version` exposes the actual running JVM version and vendor. Published older previews do not acquire the fix automatically; rebuilding and releasing the pinned image is required. A local source build requires JDK 25.

```sh
python3 scripts/check-java-runtime.py --image cda-validator:coverage-followup
python3 scripts/check-java-runtime.py --upstream
```

## Configuration

| Setting | Default | Scope |
| --- | --- | --- |
| `CDA_VALIDATOR_PORT` | `17643` | Compose host port; container port stays `17643` |
| `MAX_XML_MIB` | `20` | Maximum decoded UTF-8 XML size in whole MiB |
| `VALIDATOR_MEMORY_LIMIT` | `1536m` | Compose container memory limit |
| `VALIDATOR_JAVA_OPTIONS` | Empty | Compose value forwarded to Java as `JAVA_TOOL_OPTIONS` |
| `HOST` | `127.0.0.1` outside Docker | Service bind address; image uses `0.0.0.0` inside the container |
| `PORT` | `17643` | Service/container listen port |
| `VALIDATION_WORKERS` | `2` | Runtime validation pool; Compose currently explicitly sets `2` |
| `API_TOKEN` | Unset | Optional bearer authentication for `/v1/*`; supply through deployment configuration |
| `ASSET_DIR`, `WEB_DIR` | Image paths | Bundled assets and UI; normally leave unchanged |

`MAX_XML_MIB` must be a positive whole number within the implementation's supported byte range. Startup also requires enough heap to reserve one maximum-size upload. When raising the XML limit, provision heap/container memory accordingly.

Change the host port:

```sh
CDA_VALIDATOR_PORT=17644 docker compose up -d
```

Point browsers and clients to the new address. The CLI accepts `CDA_VALIDATOR_URL`; the Node client accepts `baseUrl`.

## Bounded queue and memory

The default service admits **six requests total**, across upload, waiting, and validation stages. Two workers run validation. Streaming JSON decoding avoids retaining a serialized body of up to roughly 120 MiB alongside the decoded XML.

Uploads share a budget equal to **one quarter of the JVM's reported maximum heap**. Each decoder reserves conservatively for string growth, UTF-16 storage, and parser buffers. Small known-length requests reserve less; chunked requests reserve the maximum allowance. Admission and the decoded XML limit separately bound retained request documents.

When memory is busy, an admitted upload waits in a fair queue on its original HTTP request. There is no job polling or client retry required for that wait. Reservations are released after decoding or on malformed input, oversize input, or disconnect.

| Boundary | Behavior |
| --- | --- |
| Capacity available | Decode and validate |
| Upload memory busy | Wait fairly, at most 30 seconds |
| Total admitted request capacity full | HTTP 429 with `Retry-After: 2` |
| Upload queue wait expires | HTTP 503 |
| Validation deadline expires | HTTP 504; cooperative cancellation |

`GET /v1/profiles` exposes active byte limits, upload memory budget, maximum concurrent maximum-size uploads, total request capacity, and queue wait bound. Reports expose `uploadQueueWaitMs`. The UI explains capacity under **Service capacity and queue**.

## Increase concurrent large-upload capacity

Increase **both Java heap and container memory**:

```sh
VALIDATOR_MEMORY_LIMIT=3g VALIDATOR_JAVA_OPTIONS=-Xmx2g docker compose up -d
```

The image otherwise uses `-XX:MaxRAMPercentage=70`, so heap sizing follows the container memory limit. Leave room outside the heap for native memory, threads, and the JVM. More heap permits more concurrent large uploads; it does not independently guarantee faster rule execution. CPU, worker count, and document complexity also matter.

To change the worker count, use a Compose override or your deployment manifest; the checked-in Compose file sets it explicitly. Scale stateless replicas behind a gateway when appropriate.

The full local pack contains over one million membership entries. Startup estimates index memory before loading and fails clearly when the selected heap is too small. Keep at least the tested 1 GiB heap / 1.5 GiB container budget for that pack; raise both for larger packs or more concurrency.

## Other operational limits

| Limit | Default |
| --- | --- |
| Decoded UTF-8 XML | 20 MiB = 20,971,520 bytes |
| Serialized JSON | 6 × active XML byte limit + 64 KiB |
| Element depth | 128 |
| Elements | 100,000 |
| Attributes per element | 256 |
| Findings | 2,000; truncation is explicit |
| Finding message | 4,000 characters; truncation is explicit |
| Upload memory queue wait | 30 seconds |
| Total HTTP upload/read window | 65 seconds |
| Validation/worker wait deadline | 30 seconds after decoding |
| Node client's default timeout | 110 seconds |

These are distinct boundaries. A document below the byte limit can still exceed structural complexity limits. JSON accepts only `xml`, `profile`, and `includeWarnings`; profile IDs are limited to 128 UTF-8 bytes. Unknown and duplicate fields are rejected.

## Shared deployments

Use an authenticated HTTPS gateway, access controls, and deliberate logging/retention policies. The local loopback configuration is not a public hosted service.

For example, a Compose override can pass an `API_TOKEN` supplied by your deployment environment:

```yaml
# compose.auth.yaml
services:
  validator:
    environment:
      API_TOKEN: "${API_TOKEN:?Supply API_TOKEN through your deployment environment}"
```

```sh
docker compose -f compose.yaml -f compose.auth.yaml up -d
```

Provide the token through your secret-management process. Clients send `Authorization: Bearer TOKEN`; the UI accepts a token under **Service authentication**. Health and static UI routes remain available. Cross-origin validation submissions are rejected.

The container runs as UID/GID `10001`, with a read-only root, temporary filesystem, dropped capabilities, no-new-privileges, and CPU/memory/PID limits. DTDs and external entities are forbidden. Runtime resource resolution is restricted to the bundled trusted assets/compiler resources. Users cannot upload executable Schematron or request arbitrary server paths/URLs.

The application does not persist XML/reports or log submitted content, filenames, or findings. Reports can contain document values; client applications and proxies must handle them appropriately. The service does not use a database or a patient-document/result cache.

Cancellation is cooperative. Deployments needing guaranteed per-document hard termination require separately supervised, process-isolated workers; that capability is not implemented here.
