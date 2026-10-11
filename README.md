# QBit SQL Workbench

A super-user SQL notebook for any [QQQ](https://github.com/QRun-IO/qqq) application.

You write SQL and markdown in notebook cells, run them against any RDBMS backend of the instance (Postgres, MySQL, SQLite, H2), and see typed, paged, exportable results, quick charts, and visual EXPLAIN plans. The notebook UI lives in the [Next UI](https://github.com/QRun-IO/qqq-frontend-next) as a `SQL_WORKBENCH` widget on the qbit's app page.

- **Read-only by default.** A default session runs one statement inside a database `READ ONLY` transaction, and a statement classifier refuses anything that isn't a read.
- **Gated write mode.** Writes need a separate permission and two steps. First a dry run executes in a transaction that is rolled back, so you see the affected-row counts. You then confirm with a single-use token bound to that exact SQL. A row guard, a hard cap, a drift check, and statement timeouts apply.
- **Everything is audited.** The `sqlWorkbenchExecution` table records who ran what, when, on which backend, in which mode, how long it took, how many rows it returned or affected, and any error. SQL text and results never go to the logs.
- **Notebooks.** Notebooks are personal by default and can be published to a shared library. They have named `:parameters` (always bound, never spliced), version history, and pinned result snapshots.

> **Raw SQL bypasses QQQ.** It ignores record security locks, table and field permissions, hidden fields, and record locks. It also skips QQQ audits, record-change listeners, automations, ESB events, and search indexing. That is why the workbench is for super-users only. Use QQQ processes for business changes.

The full design (architecture, data model, security model, limits, API contract, UX, test strategy) is in [docs/DESIGN.md](docs/DESIGN.md).

## Requirements

- Java 21 and QQQ 4.1 (built against `4.1.0-RC.1`).
- `qqq-backend-module-rdbms` for the targets. `qqq-middleware-javalin` for the REST routes; it is optional, and without it the engine still works but no routes are registered.
- A Next UI release that renders the `SQL_WORKBENCH` widget.

## Install

```xml
<dependency>
   <groupId>com.kingsrook.qbits</groupId>
   <artifactId>qbit-sql-workbench</artifactId>
   <version>1.0.0-RC.1</version>
</dependency>
```

```java
SqlWorkbenchQBitConfig config = new SqlWorkbenchQBitConfig()
   .withBackendName("postgres")                              // where the qbit's 5 tables live
   .withTableMetaDataCustomizer(new SnakeCaseCustomizer())   // optional: map names to snake_case
   .withReadOnlyCredentials("postgres", new SqlWorkbenchCredentials()
      .withUsername("${env.SQLWB_RO_USER}")                  // optional, recommended: a SELECT-only role
      .withPassword("${env.SQLWB_RO_PASSWORD}"));

new SqlWorkbenchQBitProducer().withQBitConfig(config).produce(qInstance);
```

The producer adds the following to the instance:

- **Tables:** `sqlWorkbenchNotebook`, `sqlWorkbenchCell`, `sqlWorkbenchNotebookVersion`, `sqlWorkbenchResultSnapshot`, `sqlWorkbenchExecution`.
- **A possible value source** over the notebook table.
- **The `sqlWorkbenchWidget` widget** (type `SQL_WORKBENCH`).
- **The `sqlWorkbench` app**, which contains the widget plus the notebook and audit tables. It has no processes.
- **The `/qqq/v1/sql-workbench/*` routes**, registered through `QJavalinMetaData`, as `qqq-esb` does.

To move the app under another app (for example an Admin app), re-parent `SqlWorkbenchQBitProducer.APP_NAME` after producing.

## Storage

Create the five tables on `backendName`. [docs/ddl/postgres.sql](docs/ddl/postgres.sql) has idempotent PostgreSQL DDL that you can paste into a Liquibase formatted-SQL changeset.

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

## Permissions

Everything is denied by default.

| Permission | Grants |
|---|---|
| `sqlWorkbench.hasAccess` | The app, widget, and every workbench endpoint: read-only SQL, EXPLAIN, schema, your notebooks and the shared library, your history |
| `sqlWorkbench.write` | Write mode (preview and commit) and EXPLAIN ANALYZE |
| `sqlWorkbench.share` | Publishing your notebooks to the shared library |
| `sqlWorkbenchAdmin.hasAccess` | The qbit's five tables in the generic QQQ table UI / API (`/qqq/v1/table/...`): **every** user's notebooks (personal ones included), cells, versions, pinned result snapshots (real query data), and the full execution audit |

The names are configurable (`accessPermissionBaseName`, `writePermissionName`, `sharePermissionName`, `adminPermissionBaseName`).

Grant `sqlWorkbenchAdmin.hasAccess` only to administrators or auditors. Ordinary workbench users do not need it: the workbench endpoints check ownership, visibility, and the share permission themselves, then read and write the tables as the system user. Without the admin permission a user cannot reach another user's personal notebook or audit row through the generic table API. Cells, versions, snapshots, and the audit are read-only even for admins (insert, edit, and delete are disabled; only the qbit writes them).

`write` and `share` are plain session permissions. QQQ 4.1 does not list them as available permissions, so grant them where you grant roles, for example in an authentication customizer.

`writeModeEnabled=false` turns write mode off for everyone.

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
