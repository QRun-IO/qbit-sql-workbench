# QBit SQL Workbench

A super-user SQL notebook for any [QQQ](https://github.com/QRun-IO/qqq) application.

You write SQL and markdown in notebook cells, run them against any RDBMS backend of the instance (Postgres, MySQL, SQLite, H2), and see typed, paged, exportable results, quick charts, and visual EXPLAIN plans. The notebook UI lives in the [Next UI](https://github.com/QRun-IO/qqq-frontend-next) as a `SQL_WORKBENCH` widget on the qbit's app page.

- **Read-only by default.** A default session runs one statement inside a database `READ ONLY` transaction, and a statement classifier refuses anything that isn't a read.
- **Gated write mode.** Writes need a separate permission and two steps. First a dry run executes in a transaction that is rolled back, so you see the affected-row counts. You then confirm with a single-use token bound to that exact SQL. A row guard, a hard cap, a drift check, and statement timeouts apply.
- **Everything is audited.** The `sqlWorkbenchExecution` table records who ran what, when, on which backend, in which mode, how long it took, how many rows it returned or affected, and any error. SQL text and results never go to the logs.
- **Notebooks.** Notebooks are personal by default and can be shared, read-only, with users, groups, or everyone (the shared library). They have named `:parameters` (always bound, never spliced), version history, and pinned result snapshots.
- **Standard QQQ tables.** Notebooks, cells, versions, pinned results, shares, and the execution audit are ordinary QQQ tables in the app, with the usual query, record, edit, export, audit, and developer-mode screens. Record security locks decide who sees and changes which rows, for every entry point.

> **Raw SQL bypasses QQQ.** It ignores record security locks, table and field permissions, hidden fields, and record locks. It also skips QQQ audits, record-change listeners, automations, ESB events, and search indexing. That is why the workbench is for super-users only. Use QQQ processes for business changes.

The full design (architecture, data model, security model, limits, API contract, UX, test strategy) is in [docs/DESIGN.md](docs/DESIGN.md).

## Requirements

- Java 21 and QQQ 4.1 (built against `4.1.0-RC.4`, which is required: it lets partial edits of child rows pass joined record locks, [qqq#1073](https://github.com/QRun-IO/qqq/issues/1073)).
- `qqq-backend-module-rdbms` for the targets. `qqq-middleware-javalin` for the REST routes; it is optional, and without it the engine still works but no routes are registered.
- A Next UI release that renders the `SQL_WORKBENCH` widget.

## Install

```xml
<dependency>
   <groupId>com.kingsrook.qbits</groupId>
   <artifactId>qbit-sql-workbench</artifactId>
   <version>1.0.0-RC.2</version>
</dependency>
```

```java
SqlWorkbenchQBitConfig config = new SqlWorkbenchQBitConfig()
   .withBackendName("postgres")                              // where the qbit's 6 tables live
   .withTableMetaDataCustomizer(new SnakeCaseCustomizer())   // optional: map names to snake_case
   .withReadOnlyCredentials("postgres", new SqlWorkbenchCredentials()
      .withUsername("${env.SQLWB_RO_USER}")                  // optional, recommended: a SELECT-only role
      .withPassword("${env.SQLWB_RO_PASSWORD}"));

new SqlWorkbenchQBitProducer().withQBitConfig(config).produce(qInstance);
```

The producer adds the following to the instance:

- **Tables:** `sqlWorkbenchNotebook`, `sqlWorkbenchCell`, `sqlWorkbenchNotebookVersion`, `sqlWorkbenchResultSnapshot`, `sqlWorkbenchNotebookShare`, `sqlWorkbenchExecution`, each with record security locks, per-table permissions, and table customizers ([Permissions](#permissions)).
- **Security key types** `sqlWorkbenchUser` (all-access key `sqlWorkbenchAllAccess`) and `sqlWorkbenchAudience`.
- **Joins** from a notebook to its cells, versions, pinned results, and shares, with child-list widgets on the notebook's record view.
- **Possible value sources** over the notebook table and the share audience types.
- **Record actions** (processes on a table, never in the nav): Share Notebook, Duplicate Notebook, Restore Version.
- **The `sqlWorkbenchWidget` widget** (type `SQL_WORKBENCH`).
- **The `sqlWorkbench` app**, which contains the widget plus all six tables.
- **The `/qqq/v1/sql-workbench/*` routes**, registered through `QJavalinMetaData`, as `qqq-esb` does.

To move the app under another app (for example an Admin app), re-parent `SqlWorkbenchQBitProducer.APP_NAME` after producing.

## Storage

Create the six tables on `backendName`. [docs/ddl/postgres.sql](docs/ddl/postgres.sql) has idempotent PostgreSQL DDL that you can paste into a Liquibase formatted-SQL changeset.

The DDL uses snake_case names: `sql_workbench_notebook`, `owner_user_id`, and so on. Match them with a table customizer:

```java
public class SnakeCaseCustomizer implements MetaDataCustomizerInterface<QTableMetaData>
{
   public QTableMetaData customizeMetaData(QInstance qInstance, QTableMetaData table)
   {
      table.setBackendDetails(new RDBMSTableBackendDetails().withTableName(QInstanceEnricher.inferBackendName(table.getName())));
      QInstanceEnricher.setInferredFieldBackendNames(table);
      return (table);
   }
}
```

## Storage upgrade from 1.0.0-RC.1

RC.2 adds `sql_workbench_notebook_share`. The DDL creates it and backfills an `all` share (the shared library) for every notebook whose `visibility` is `SHARED`; it is additive and idempotent, and owners (`owner_user_id`) are unchanged. Run it before deploying RC.2: without the share rows, published notebooks are visible only to their owners.

## Permissions

Everything is denied by default. Two standard QQQ layers apply:

1. **Permissions** say which operations a user has on each table (QQQ's per-table `read`, `insert`, `edit`, `delete`) and on the endpoints.
2. **Record security locks** say which rows. QQQ's core actions enforce them, so they apply to the table UI, the `/qqq/v1` table API, exports, possible-value lookups, processes, and the workbench's own endpoints alike.

| Role | Grant | What it allows |
|---|---|---|
| User | `config.getUserPermissionNames()`: `sqlWorkbench.hasAccess`, plus read/insert/edit/delete on notebooks and cells, read on versions, shares and the audit, and read/edit/delete on pinned results | The app, widget and endpoints (read-only SQL, EXPLAIN, schema). Their own notebooks, read-only access to notebooks shared with them or in the library, and their own execution history. |
| Writer | `config.getWritePermissionName()` (`sqlWorkbench.write`) | Write mode (preview and commit) and EXPLAIN ANALYZE. |
| Sharer | `config.getSharePermissionNames()`: `sqlWorkbench.share`, plus insert/edit/delete on shares | Sharing their own notebooks with users, groups, or everyone (publishing to the library). |
| Admin | `config.getAdminPermissionNames()`: the user permissions plus `sqlWorkbenchAdmin.hasAccess` | The all-access key: every user's notebooks, cells, versions, pinned results (real query data), shares, and the whole execution audit, and editing any notebook. |

The names are configurable (`accessPermissionBaseName`, `writePermissionName`, `sharePermissionName`, `adminPermissionBaseName`). Table permissions follow the table names, including any `tableNamePrefix`. QQQ 4.1 does not list `write` and `share` as available permissions, so grant them where you grant roles, for example in an authentication customizer.

**Security keys.** Call `SqlWorkbenchSecurity.applyToSession(qInstance, session, groups)` from your authentication customizer, after the session's user and permissions are set. It puts these keys in the session:

| Key | Values | Used by |
|---|---|---|
| `sqlWorkbenchUser` | the user's id (`QUser.idReference`) | owner locks: a notebook's `ownerUserId` (its children reach it through the notebook) and an execution's `userId` |
| `sqlWorkbenchAudience` | `user:<id>`, `group:<name>` for each group passed, and `all` | share locks (read only): a share row's `audienceKey` |
| `sqlWorkbenchAllAccess` | `true` for holders of the admin permission | every row |

A session without the access permission gets no keys, so it finds no workbench rows at all, even through code paths that skip table permissions. If the host never calls `applyToSession`, the workbench endpoints add the user and `all` keys themselves, but group shares need the host to pass the groups.

**What the locks and customizers enforce:**

| Table | Read | Write |
|---|---|---|
| Notebook | the owner, a share audience of the session, or an admin | the owner or an admin. The owner and visibility are system-managed. |
| Cell, version, pinned result | as their notebook | the owner or an admin, through the notebook. Versions are append-only. |
| Share | the notebook's owner, the audience it names, or an admin | the owner or an admin, with the share permission |
| Execution (audit) | the user who ran it, or an admin | nobody: append-only, only the qbit writes it |

`writeModeEnabled=false` turns write mode off for everyone.

## Administering through the tables

Everything the notebook UI does can be done and inspected through the tables under the SQL Workbench app. The same customizers run either way.

- **Notebooks**: create, rename, describe, delete (which removes its cells, versions, pinned results, and shares). The record view lists the notebook's cells, shares, pinned results, and versions. Record actions: Share Notebook and Duplicate Notebook.
- **Cells**: add (appended at the end, with a generated key), edit, reorder (`sortOrder`), delete. Each change saves a new notebook version, exactly like a save in the notebook UI.
- **Versions**: read-only history. Restore Version saves the chosen version as a new one.
- **Shares**: who can read a notebook. `USER` and `GROUP` shares name their audience, matched without regard to case (so user ids and group names must be case-insensitive in the host); `ALL` publishes the notebook to the shared library. The notebook's `visibility` follows its shares.
- **Pinned results**: relabel or delete. Only pinning a result in the notebook UI creates them.
- **Execution audit**: read, filter, and export. Nobody can edit or delete it through QQQ.

Notebooks, cells, pinned results, and shares get record-level QQQ audits, so SQL text, result data, and share audiences are not copied into the host's audit tables (whose readers the workbench locks do not reach).

## Safety model

> **Production: configure `readOnlyCredentials` for every target backend.** Use a dedicated database role with `SELECT` grants only: no write privileges, not a superuser, no `pg_signal_backend` (or MySQL `PROCESS` / `SUPER` / `CONNECTION_ADMIN`). Without it, read-only mode connects as the application's own role and relies on the classifier and the session's `READ ONLY` transaction alone. On H2 / generic backends there is no enforced read-only transaction, so the classifier is the only control. On Postgres a `READ ONLY` transaction still lets the application role call side-effecting functions such as `pg_terminate_backend()` or `pg_cancel_backend()`. `/capabilities` reports `readOnlyCredentials: false` / `readOnlyEnforced: false` per backend so you can check.

**Read-only sessions:**
- Postgres: `setReadOnly(true)`, `SET TRANSACTION READ ONLY`, `SET LOCAL statement_timeout` and `lock_timeout`.
- MySQL: `setReadOnly(true)`, which sets the session transaction characteristic; it is reverted afterwards.
- SQLite: `PRAGMA query_only`, reset afterwards.
- H2: the classifier only. Use read-only credentials, or don't target H2 in production.

**Credentials:**
- With `readOnlyCredentials`, read-only sessions connect as a dedicated role through their own pool. A `READ ONLY` transaction does not stop side-effecting functions such as `pg_terminate_backend()`, but a least-privilege role does.
- Credentials are never returned or logged, and the config's `toString` redacts them.

**Classifier:**
- It is conservative. Anything that isn't recognized as a read is treated as a write.
- A "read" that mentions a mutating keyword is also a write: data-modifying CTEs, `SELECT ... INTO`, `FOR UPDATE`, `nextval()`.
- Transaction and session control is refused in every mode: `BEGIN`, `COMMIT`, `SET`, `RESET`, `USE`, `PRAGMA x=y`, `ATTACH`, `DO`, and others.
- On MySQL / MariaDB, comments the server executes are refused in every mode: `/*! ... */`, `/*M! ... */` (version-conditional SQL), and `/*+ ... */` (optimizer hints). `--` only starts a comment when whitespace follows, as in MySQL.

**Clean connections:**
- Every session ends with a rollback (or a commit for a confirmed write) and restores autocommit and read-only.
- If restoring fails, the connection is aborted rather than returned to the pool.
- Audit writes happen after the session's connection is released.

**Limits** (all configurable):

| Limit | Default |
|---|---|
| Read-only statement timeout | 30 s |
| Write statement timeout | 60 s |
| Rows kept per result (paged from a server-side cache) | 10,000 |
| Result size | 20 MB |
| Row guard (needs acknowledgement above this) | 1,000 rows |
| Hard cap on affected rows | 100,000 |
| Concurrent runs per user | 2 |
| SQL length | 100,000 characters |

Cancellation, single-statement read-only runs, and per-user result caches also apply.

**Audit:**
- A `RUNNING` row is written before the SQL runs. If that write fails, the SQL does not run.
- Rejected attempts are audited too.
- The audit table cannot be inserted into, edited, or deleted from through QQQ.

## REST API

Base path: `/qqq/v1/sql-workbench`. The contract is in [docs/DESIGN.md §5](docs/DESIGN.md#5-rest-api-qqqv1sql-workbench).

| Area | Endpoints |
|---|---|
| Info | `GET /capabilities`, `GET /schema/{backend}` |
| Run | `POST /execute`, `POST /explain` |
| Results | `GET /executions/{uuid}/rows`, `GET /executions/{uuid}/export?format=csv\|xlsx\|json`, `POST /executions/{uuid}/cancel` |
| History | `GET /history` |
| Notebooks | `/notebooks` (CRUD, publish, duplicate, versions, restore, snapshots) |

POST and PUT bodies must be `application/json`.

## Adopting in another app

A checklist for adding the workbench to a QQQ application. Each row points to the section above that has the details.

| Item | What to do |
|---|---|
| Coordinates | `com.kingsrook.qbits:qbit-sql-workbench`. Current release candidate: `1.0.0-RC.2` (Maven Central, published from `release/1.0`). |
| QQQ version | QQQ 4.1, `4.1.0-RC.4` or later (qqq#1073: partial edits of child rows under joined locks). Java 21. |
| Dependencies | `qqq-backend-module-rdbms` comes in transitively. Add `qqq-middleware-javalin` yourself for the REST routes; it is optional in this qbit's POM, and without it no routes are registered. |
| Frontend | A Next UI (`qqq-frontend-next`) release that renders the `SQL_WORKBENCH` widget: `v1.0.0-RC.13` or later. Other frontends show the app and tables but not the notebook. |
| Registration | `new SqlWorkbenchQBitProducer().withQBitConfig(config).produce(qInstance)` after your backends are in the instance. The producer registers its own QBit metadata; don't add it by hand. To nest the app (for example under Admin), re-parent `SqlWorkbenchQBitProducer.APP_NAME` after producing. |
| Storage | Create the six tables on `backendName` before first use ([Storage](#storage)). From RC.1, run the share-table DDL and its backfill ([upgrade](#storage-upgrade-from-100-rc1)). PostgreSQL DDL ships in [docs/ddl/postgres.sql](docs/ddl/postgres.sql) and drops into a Liquibase formatted-SQL changeset; translate it for other databases. Match the snake_case names with a `tableMetaDataCustomizer`. |
| Targets | Every RDBMS backend in the instance is a target unless you narrow it with `allowedBackendNames` or `excludedBackendNames`. |
| Secrets | In production, configure `readOnlyCredentials` for each target backend with a SELECT-only database role, and optionally `writeCredentials`. Pass them as `${env.X}` references (the example uses `SQLWB_RO_USER` and `SQLWB_RO_PASSWORD`; the names are yours). The qbit reads no other environment variables. |
| Permissions | Grant `config.getUserPermissionNames()` to workbench users, plus `getWritePermissionName()` for write mode, `getSharePermissionNames()` for sharing, and `getAdminPermissionNames()` for administrators and auditors only. See [Permissions](#permissions). |
| Security keys | Call `SqlWorkbenchSecurity.applyToSession(qInstance, session, groups)` in your authentication customizer after setting permissions. Pass the user's groups if you want group shares. |
| Navigation | The app lists the widget and the six tables. Its processes are record actions on the tables; keep them out of the nav. If your app adds explicit table menus or nav-coverage tests, include the six tables. |
| Turning writes off | `withWriteModeEnabled(false)` disables write mode for everyone. |
| Known limitations | Raw SQL bypasses QQQ security, audits, listeners and automations ([Safety model](#safety-model)). H2 and generic backends have no enforced read-only transaction. Only PostgreSQL DDL ships. Shares are read-only (no shared editing). |
| Upgrading | From RC.1: run the share-table DDL and backfill, grant the new permission lists (the tables no longer rely on `sqlWorkbenchAdmin.hasAccess` alone), call `applyToSession`, and use QQQ `4.1.0-RC.4` or later. |

Nothing in the qbit is specific to one host application.

## Build

```bash
./mvnw compile
./mvnw test                      # unit tests (H2, SQLite, Javalin over HTTP)
CI=true ./mvnw verify            # plus Postgres integration tests (Docker) and the JaCoCo gate
./mvnw verify -Dpostgres.test.image=postgres:17-alpine
```

Integration tests are skipped without Docker. With `CI=true`, a missing Docker fails the build instead.

## License

Apache License 2.0. See [LICENSE](LICENSE).
