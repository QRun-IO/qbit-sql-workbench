# Changelog

All notable changes to this project are documented here. The format follows Keep a Changelog; versions follow semantic versioning.

## [1.0.0-RC.4] - Unreleased

### Added
- **App home dashboard.** The `sqlWorkbench` app's home is now an information dashboard of real data, read in the viewer's session (permissions and record locks apply): summary tiles and links, runs per day, recent notebooks, recent runs, slowest and failed queries, most-used notebooks, shared notebooks, committed writes, and (workbench admins only) activity by user. Each widget has an honest empty state.
- **Runner.** The notebook editor (`SQL_WORKBENCH` widget) moves to its own nav entry: the `sqlWorkbenchRunner` child app, labeled Runner. Open a notebook with `/app/sqlWorkbenchRunner?notebook=<id>`.
- On a notebook's record view: an Open in Runner widget, and a Runs child list (its executions the viewer may see) through the new join `sql_workbench_notebook_join_sql_workbench_execution`.
- `SqlWorkbenchIntegrations`: opt-in ESB (`esbTableNames`) and Quick Search (`quickSearchTables`) hooks for the main tables, with no dependency on either module.
- **Record developer panel** "SQL Workbench" (`SqlWorkbenchDeveloperPanelProvider`, registered by `SqlWorkbenchDeveloperPanelsMetaDataProducer`): read-only `SELECT`s for the record and its table in the backend's own names, backend details, and links that open them in the Runner as a new cell (`/app/sqlWorkbenchRunner?sql=&backend=`, never run). Requires QQQ 4.1.0-RC.4 (the developer panel contract) and a Next UI with the Runner `?sql=` support.

### Changed
- **The nav lists only the main tables**: Notebooks and the execution audit, after the Runner. Cells, versions, pinned results and shares are child lists on a notebook (still standard tables, reachable by URL and API). The app's widget list is now the dashboard, not the `SQL_WORKBENCH` widget; links to `/app/sqlWorkbench` that expected the editor should point at `/app/sqlWorkbenchRunner`.
- The notebook record's header shows the description.
- **Names follow the owner rule: lowercase snake_case, prefixed with the qbit's name (metadata only, no DDL).** Tables `sql_workbench_notebook`, `sql_workbench_cell`, `sql_workbench_notebook_version`, `sql_workbench_result_snapshot`, `sql_workbench_notebook_share`, `sql_workbench_execution` (they now equal their backend table names); possible-value source `sql_workbench_share_audience_type`; permissions `sql_workbench.*`, `sql_workbench_admin.hasAccess` and `<table>.read` etc.; security key types `sql_workbench_user` (all-access `sql_workbench_all_access`) and `sql_workbench_audience`; joins `sql_workbench_notebook_join_sql_workbench_*`.
- `tableNamePrefix` (and the QBit namespace) is normalized to lowercase snake_case and joined with one `_`.

### Deprecated
- `LEGACY_TABLE_NAME` on each entity, `ShareAudienceType.LEGACY_NAME` and `SqlWorkbenchQBitConfig.LEGACY_*` hold the pre-RC.3 names, for migrating stored references only.

### Upgrade from RC.2
- Follow [UPGRADE.md](UPGRADE.md): grant the renamed permissions and rename stored table names (audit, saved views and reports). No DDL.

## [1.0.0-RC.3] - 2026-10-12

**Do not use.** 1.0.0-RC.3 on Maven Central is an accidental republish of the RC.2 code by a tag build; do not use it. The next real candidate is 1.0.0-RC.4. (Cause: QRun-IO/qqq-orb#20; the `publish_release` workflow now runs on GA tags only.)

## [1.0.0-RC.2] - Unreleased

The workbench's data becomes standard QQQ tables, secured by QQQ's own mechanisms instead of being hidden ([#3](https://github.com/QRun-IO/qbit-sql-workbench/issues/3)). Requires QQQ `4.1.0-RC.4` or later ([qqq#1073](https://github.com/QRun-IO/qqq/issues/1073)).

### Added
- `sqlWorkbenchNotebookShare`: share a notebook, read-only, with a user, a group, or everyone (`ALL`, the shared library). Publishing in the notebook UI now writes an `ALL` share; a notebook's `visibility` follows its shares.
- Record security locks on every table, enforced by QQQ's core actions (table UI, `/qqq/v1` table API, export, PVS lookups, processes, the workbench endpoints): owners see and change their notebooks and children, share audiences read them, users read their own execution audit, admins (`sqlWorkbenchAdmin.hasAccess`) hold the all-access key. Security key types `sqlWorkbenchUser` and `sqlWorkbenchAudience`; `SqlWorkbenchSecurity.applyToSession(qInstance, session, groups)` puts the keys in a session.
- Per-table `read` / `insert` / `edit` / `delete` permissions, listed per role by `SqlWorkbenchQBitConfig.getUserPermissionNames()`, `getSharePermissionNames()` and `getAdminPermissionNames()`.
- All six tables are children of the `sqlWorkbench` app, with record-view sections, child-list widgets (cells, shares, pinned results, versions) and SQL / JSON code-editor adornments.
- Record actions (processes on tables, not in the nav): Share Notebook, Duplicate Notebook, Restore Version.
- Table customizers that keep the notebook invariants for every entry point: ownership from the session, cell validation and ordering, one version per change, share keys and visibility, append-only versions and audit (also for admins), pinned results only from pinning, cascading notebook deletes.
- Record-level QQQ audits for notebooks, cells, pinned results and shares.

### Changed
- `NotebookService` and the history endpoint run as the caller's session through the standard table actions (no system-user bypass); the explicit owner / share checks stay as defense in depth.
- The tables' permission rules are per-table permissions instead of `sqlWorkbenchAdmin.hasAccess`. Hosts must grant the new lists and call `applyToSession`.
- Built against QQQ `4.1.0-RC.4`.

### Upgrade from RC.1
- Run the share-table DDL from `docs/ddl/postgres.sql`: it creates `sql_workbench_notebook_share` and backfills an `all` share for each `SHARED` notebook (additive, idempotent; owners unchanged).
- Grant the permission lists and call `SqlWorkbenchSecurity.applyToSession` in the authentication customizer.

## [1.0.0-RC.1] - 2026-10-11

First release candidate of 1.0, published to Maven Central from `release/1.0`. It contains every change listed under 1.0.0 below. Requires QQQ 4.1 (built against 4.1.0-RC.2) and a Next UI release that renders the `SQL_WORKBENCH` widget (`v1.0.0-RC.13` or later).

## [1.0.0] - Unreleased

First release. Requires QQQ 4.1 (built against 4.1.0-RC.2).

### Added
- `SqlWorkbenchQBitProducer` / `SqlWorkbenchQBitConfig`: a standard QQQ 4.1 QBit, published as `com.kingsrook.qbits:qbit-sql-workbench`.
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

### Security
- The qbit's tables need a separate admin permission (`adminPermissionBaseName`, default `sqlWorkbenchAdmin` => `sqlWorkbenchAdmin.hasAccess`). Ordinary workbench users have no generic table API access to notebooks, snapshots or the audit; the workbench endpoints check ownership and act as the system user.
- Client-chosen `executionUuid`s are single-use (409 on reuse) and case-insensitive.
- On MySQL, executable comments (`/*! */`, `/*M! */`) and optimizer hints (`/*+ */`) are refused.
- CSV exports neutralize formula injection; connection errors are generic to the caller.
- Production backends should configure `readOnlyCredentials` (see README, Safety model).
