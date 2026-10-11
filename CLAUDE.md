# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Project Overview

**QBit SQL Workbench**: a QQQ QBit that provides a super-user SQL notebook. Sessions are read-only by default, write mode is gated, and everything is audited. It works over any RDBMS backend of the instance. The UI is the Next UI `SQL_WORKBENCH` widget. Design: `docs/DESIGN.md`.

## Build Commands

```bash
./mvnw compile          # Compile (always use the wrapper: Maven 3.10 cannot read the parent POM)
./mvnw test             # Unit tests (H2 MODE=MySQL, SQLite, Javalin over HTTP)
./mvnw test -Dtest=ClassName
CI=true ./mvnw verify   # Unit + Postgres integration tests (Docker) + JaCoCo gate (70% instructions, 90% classes)
./mvnw verify -Dpostgres.test.image=postgres:17-alpine
```

How tests are split:
- **Integration tests** (`*IntegrationTest`) run under failsafe and use `@ExtendWith(RequiresDockerCondition.class)`. Without Docker they are skipped locally; with `CI=true` the build fails instead.
- **Unit tests** extend `BaseSqlWorkbenchTest`. It sets up an H2 backend `h2` with the snake_case DDL from `src/test/resources/sql-workbench-h2.sql`, MOCK auth, and a session customizer. The session id prefix picks the permissions: `reader-*` gets access, `writer-*` gets access plus write plus share, anything else gets nothing.

## Architecture

**Entry point:** `SqlWorkbenchQBitProducer implements QBitMetaDataProducer<SqlWorkbenchQBitConfig>`.
- Component producers are found in the `metadata` package only (`getPackageNameForFindingMetaDataProducers`), because `api` needs the optional Javalin dependency.
- `postProduceActions` builds `SqlWorkbenchRuntime` and calls `api.SqlWorkbenchRoutes.register` when Javalin is on the classpath.

**Packages:**
- `sql/`: `SqlScanner` (dialect-aware lexer), `SqlClassifier` (split, then classify as READ, EXPLAIN_ANALYZE, WRITE, DDL, or FORBIDDEN), `NamedParameterSql` (`:name` → `?`). Pure Java.
- `engine/`:
  - `SqlExecutionEngine` handles read-only runs, the write preview/commit protocol, and EXPLAIN.
  - `SessionGuard` sets up the per-vendor read-only transaction and timeouts, and always restores the connection (or aborts it).
  - `WorkbenchBackends` resolves targets and derives backends for dedicated credentials.
  - `ResultCollector` converts types and applies the row/byte caps.
  - `ResultCache`, `RunningStatements`, and `WritePreviewTokens` are the runtime state.
  - `ExecutionAuditor` writes audits through QQQ actions as `QSystemUserSession`.
- `explain/`: `PlanParser` (Postgres JSON, MySQL JSON/text, SQLite rows, text) and `PlanNode`.
- `schema/`: `SchemaIntrospector` (JDBC `DatabaseMetaData` plus the QQQ table and field to SQL name mapping), cached in the runtime.
- `notebook/`: `NotebookService` (CRUD, visibility, versions in one transaction, snapshots).
- `api/`: `SqlWorkbenchApi` (transport-agnostic, permission checks), `SqlWorkbenchRouteProvider` (Javalin 7 routes under `/qqq/v1/sql-workbench`), `ResultExporter` (CSV/JSON/XLSX via fastexcel), and `SqlWorkbenchRoutes`.
- `metadata/`: the 5 tables, the notebook PVS, the `SQL_WORKBENCH` widget and its renderer, and the `sqlWorkbench` app.

**Invariants (do not break):**
- Never log SQL text, parameters, or results. Only the audit table holds them.
- Audit `start()` runs before any SQL. If it fails, the SQL does not run.
- Audit updates are deferred until the session's connection is released (`Deferred` in the engine), so the engine never needs two pooled connections at once.
- Engine-side session settings must be transaction-scoped (`SET LOCAL`) or reverted in `SessionGuard.close`.
- SQL errors are results (`status` FAILED, TIMED_OUT, or CANCELLED). Exceptions are only for 400, 401, 403, and 404.

## Code Style

- 3-space indentation, opening braces on the next line, `return (x);`.
- Javadoc flower-box comments (`/*****` with `**` lines) on every class and method.
- Fluent setters (`withX()`).
- Wrapper types (`Integer`, `Boolean`) in config and entities.

## Dependencies

- Java 21.
- Parent `com.kingsrook:qbit-build-parent:2.0.0` (qqq 4.0.0). The default-active `qqq-snapshot` profile imports `qqq-bom-pom:4.1.0-RC.2` ahead of it, as in qbit-quick-search (ADR-0007). Remove that activation when the parent moves to 4.1.
- `qqq-backend-core`, `qqq-backend-module-rdbms`, and `qqq-middleware-javalin` (optional).
- Tests: JUnit 5, AssertJ, Mockito, H2, `qqq-backend-module-sqlite`/`-postgres` (drivers), Testcontainers 1.21.4.

## Release

- CircleCI qqq-orb 0.6.8.
- `develop` publishes SNAPSHOTs.
- A `release/*` branch publishes RCs (the orb bumps the version).
- A `v*` tag publishes the release.
