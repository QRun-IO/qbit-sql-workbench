# Changelog

All notable changes to this project are documented here. The format follows Keep a Changelog; versions follow semantic versioning.

## [1.0.0] - Unreleased

First release. Requires QQQ 4.1 (built against 4.1.0-RC.1).

### Added
- `SqlWorkbenchQBitProducer` / `SqlWorkbenchQBitConfig`: a standard QQQ 4.1 QBit (`com.kingsrook.qbits:sql-workbench`).
- Tables:
  - `sqlWorkbenchNotebook`, `sqlWorkbenchCell`, `sqlWorkbenchNotebookVersion`, `sqlWorkbenchResultSnapshot`.
  - `sqlWorkbenchExecution`: the audit table, read-only for users.
- The `SQL_WORKBENCH` widget and the `sqlWorkbench` app. There are no processes.
- Execution engine:
  - Read-only by default: a database `READ ONLY` transaction plus a statement classifier.
  - Gated write mode: preview with a rolled-back dry run, single-use confirmation tokens, a row guard, a hard cap, and a drift check.
  - Per-vendor session guard with clean connection return.
  - Statement timeouts, row and byte caps, cancellation, a per-user concurrency limit.
- Dedicated read-only and write credentials per backend, plus use of the backend's read-only replica.
- EXPLAIN / EXPLAIN ANALYZE (always rolled back) normalized into a plan tree for Postgres, MySQL, SQLite, and H2.
- Schema introspection for autocomplete, with QQQ table and field to SQL name mapping.
- Notebooks:
  - Personal notebooks and a shared library.
  - Named parameters.
  - Version history with retention.
  - Pinned result snapshots.
- REST API under `/qqq/v1/sql-workbench`, with CSV/XLSX/JSON export.
- PostgreSQL DDL in `docs/ddl/postgres.sql`.
