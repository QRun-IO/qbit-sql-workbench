# SQL Workbench UI API contract

All 21 operations are registered by `SqlWorkbenchQBitProducer` through
`SqlWorkbenchEndpoints` as one `MiddlewareSpecExtension` named `sql-workbench`,
version `1.0.0`, with the established `/sql-workbench` prefix. Full URLs remain
`/qqq/v1/sql-workbench/...`. QQQ `4.1.0-RC.6` is the minimum core version.
`registerRoutes=false` disables extension registration; middleware remains optional.

The producer registers no raw routes. Twenty JSON operations extend
`AbstractJsonEndpointSpec` through `SqlWorkbenchJsonSpec`; `ExportResultSpec`
extends `AbstractEndpointSpec` directly and streams CSV, JSON or XLSX with the
existing attachment filename and `Cache-Control: no-store` headers.

The checked-in fragment is
[`src/main/resources/openapi/qqq-v1/sql-workbench.yaml`](../src/main/resources/openapi/qqq-v1/sql-workbench.yaml).
It is also included in a host's `/qqq/v1/openapi.yaml` and `.json`, and the
extension appears in `/qqq/v1/version`. All component names begin with
`SqlWorkbench`. Schemas explicitly describe the existing map payloads rather
than changing their serialization. SQL results include nullable scalar values,
binary previews and nested arrays; execute responses vary by execution status.

## Compatibility and security

No existing operation paths, methods, request or response shapes, status codes,
or export headers change. Next UI can vendor the fragment without adapting its
existing calls. New metadata consists of the extension/version advertisement,
OpenAPI schemas and stable operation identifiers.

Core authenticates the caller before each spec runs and clears `QContext` after
the request. Workbench access is checked before parsing bodies, path integers or
export formats. JSON object parsing retains `BigDecimal` precision, JSON-only
content-type checks, empty/null-body behavior, and existing error messages.
Duplicate execution UUIDs still return 409. SQL failures remain HTTP 200 with a
`FAILED`, `TIMED_OUT` or `CANCELLED` execution status.

Specs call the same `SqlWorkbenchApi`, notebook service and execution engine.
Write/EXPLAIN ANALYZE gates, sharing permissions, caller record locks, notebook
ownership, cached-result ownership, cancellation ownership, per-user concurrency
limits, audit-before-SQL and deferred audit updates remain in place. Client IPs
continue into execution audits. No request bodies or result data are logged by
the transport.

## Operations

| Method | Path (after `/qqq/v1/sql-workbench`) | operationId |
|---|---|---|
| GET | `/capabilities` | `sqlWorkbenchGetCapabilities` |
| POST | `/execute` | `sqlWorkbenchExecute` |
| POST | `/executions/{uuid}/cancel` | `sqlWorkbenchCancelExecution` |
| GET | `/executions/{uuid}/export` | `sqlWorkbenchExportResult` |
| GET | `/executions/{uuid}/rows` | `sqlWorkbenchGetRows` |
| POST | `/explain` | `sqlWorkbenchExplain` |
| GET | `/history` | `sqlWorkbenchGetHistory` |
| GET | `/notebooks` | `sqlWorkbenchListNotebooks` |
| POST | `/notebooks` | `sqlWorkbenchCreateNotebook` |
| DELETE | `/notebooks/{id}` | `sqlWorkbenchDeleteNotebook` |
| GET | `/notebooks/{id}` | `sqlWorkbenchGetNotebook` |
| PUT | `/notebooks/{id}` | `sqlWorkbenchUpdateNotebook` |
| POST | `/notebooks/{id}/duplicate` | `sqlWorkbenchDuplicateNotebook` |
| POST | `/notebooks/{id}/publish` | `sqlWorkbenchPublishNotebook` |
| GET | `/notebooks/{id}/snapshots` | `sqlWorkbenchListSnapshots` |
| POST | `/notebooks/{id}/snapshots` | `sqlWorkbenchCreateSnapshot` |
| DELETE | `/notebooks/{id}/snapshots/{snapshotId}` | `sqlWorkbenchDeleteSnapshot` |
| GET | `/notebooks/{id}/versions` | `sqlWorkbenchListVersions` |
| GET | `/notebooks/{id}/versions/{n}` | `sqlWorkbenchGetVersion` |
| POST | `/notebooks/{id}/versions/{n}/restore` | `sqlWorkbenchRestoreVersion` |
| GET | `/schema/{backendName}` | `sqlWorkbenchGetSchema` |

## Verification and regeneration

`SqlWorkbenchApiContractTest` regenerates the fragment in memory and checks it
against the checked-in file using core's deterministic `UiApiContract` tooling.
It also checks the namespace, secured specs, prefix, media types and 21 unique
operation IDs. Intentional contract changes can regenerate the file with:

```sh
./mvnw -B test -Dtest=SqlWorkbenchApiContractTest -DsqlWorkbench.openapi.update=true
```

Before RC.6 is released, local verification uses the separately installed core:

```sh
./mvnw -B verify -Dqqq.snapshot.version=4.1.0-ARCHRC6-SNAPSHOT
```

The POM must continue to name `4.1.0-RC.6`; the local override is never a release
dependency. On PowerShell, quote each `-D...` argument and use `mvnw.cmd`.

`SqlWorkbenchEndpointSpecTest` runs real HTTP through
`QApplicationJavalinServer.withStrictUiApiRoutes(true)`. It preserves the former
route-provider tests and adds 401/403 coverage for every operation, mutation
denials, execution limits, audited rejection, exact decimals, SQL failures and
all three exports. Engine tests continue to verify audit and transaction safety.
