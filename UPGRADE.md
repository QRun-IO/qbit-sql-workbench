# Upgrading

## Table help and `auditRetentionDays` (unreleased)

No action needed. The qbit now fills empty `help` slots on its app and tables (help a host already set is kept). The new `auditRetentionDays` config defaults to null, which keeps every audit row as before; set it to bound the size of `sql_workbench_execution`. `notebookVersionRetention` (default 100 per notebook) was already enforced. There is no DDL change and no seed.

## To 1.0.0-RC.4: snake_case names (metadata only)

The rename shipped in 1.0.0-RC.4. 1.0.0-RC.3 on Maven Central is an accidental republish of the RC.2 code (see CHANGELOG.md), so upgrading from RC.3 is the same as upgrading from RC.2.

The owner naming rule now applies to every QRun-IO qbit: table names are lowercase snake_case, prefixed with the qbit's name. In 1.0.0-RC.4 the SQL Workbench's QQQ names change to match. **The database does not change**: the physical tables were already `sql_workbench_*` (`docs/ddl/postgres.sql`), so no DDL runs. What changes is the names QQQ uses, and anything your app stored under the old names.

| Kind | Before (RC.1, RC.2, RC.3) | From RC.4 |
|---|---|---|
| Tables (and the notebook possible-value source) | `sqlWorkbenchNotebook`, `sqlWorkbenchCell`, `sqlWorkbenchNotebookVersion`, `sqlWorkbenchResultSnapshot`, `sqlWorkbenchNotebookShare`, `sqlWorkbenchExecution` | `sql_workbench_notebook`, `sql_workbench_cell`, `sql_workbench_notebook_version`, `sql_workbench_result_snapshot`, `sql_workbench_notebook_share`, `sql_workbench_execution` |
| Enum possible-value source | `sqlWorkbenchShareAudienceType` | `sql_workbench_share_audience_type` |
| Permissions | `sqlWorkbench.hasAccess`, `sqlWorkbench.write`, `sqlWorkbench.share`, `sqlWorkbenchAdmin.hasAccess`, `sqlWorkbenchNotebook.read` etc. | `sql_workbench.hasAccess`, `sql_workbench.write`, `sql_workbench.share`, `sql_workbench_admin.hasAccess`, `sql_workbench_notebook.read` etc. |
| Security key types / all-access key | `sqlWorkbenchUser` (`sqlWorkbenchAllAccess`), `sqlWorkbenchAudience` | `sql_workbench_user` (`sql_workbench_all_access`), `sql_workbench_audience` |
| Joins | `sqlWorkbenchNotebookJoinSqlWorkbenchCell` etc. | `sql_workbench_notebook_join_sql_workbench_cell` etc. |

Unchanged: the `sqlWorkbench` app, the `sqlWorkbenchWidget` widget, the record-action process names, the REST routes, field names, and Java class names.

A `tableNamePrefix` (or QBit namespace) is now normalized to lowercase snake_case and joined with one `_`: `ns` and `ns_` give `ns_sql_workbench_notebook`, and `myNs` gives `my_ns_sql_workbench_notebook`. Prefixed tables follow the same rename.

The old names stay available as `@Deprecated` constants for migration scripts only: `SqlWorkbenchNotebook.LEGACY_TABLE_NAME` (and the same on each entity), `ShareAudienceType.LEGACY_NAME`, and `SqlWorkbenchQBitConfig.LEGACY_*`. They will be removed in 1.1.

### Steps

1. **Backend table names.** If your host mapped the old camelCase names to `sql_workbench_*` (for example with `inferBackendName`), nothing else is needed: the new names are already the backend names, and the mapping becomes a no-op. If your backend tables were really named after the camelCase QQQ names, rename them (`ALTER TABLE "sqlWorkbenchNotebook" RENAME TO sql_workbench_notebook;` and so on) or set the backend table names explicitly.
2. **Permissions.** Grant the new names. If you set the permission names through the config getters (`getUserPermissionNames()`, `getSharePermissionNames()`, `getAdminPermissionNames()`), this is automatic. If roles live in an identity provider or a permissions table, rename them there. If you pinned the old names in config (`withAccessPermissionBaseName("sqlWorkbench")`), you can keep them, but the table permissions still change.
3. **Security keys.** `SqlWorkbenchSecurity.applyToSession` sets the new key types. Custom code that put `sqlWorkbenchUser` or `sqlWorkbenchAudience` values in a session must use the new names (`config.getUserSecurityKeyTypeName()` and so on).
4. **Stored references.** Rename the table names your app stored. A Postgres example, with the usual QQQ table and column names (adjust to your app; run inside one transaction, after a backup):

```sql
BEGIN;
CREATE TEMP TABLE swb_rename (old_name TEXT, new_name TEXT) ON COMMIT DROP;
INSERT INTO swb_rename VALUES
  ('sqlWorkbenchNotebook',        'sql_workbench_notebook'),
  ('sqlWorkbenchCell',            'sql_workbench_cell'),
  ('sqlWorkbenchNotebookVersion', 'sql_workbench_notebook_version'),
  ('sqlWorkbenchResultSnapshot',  'sql_workbench_result_snapshot'),
  ('sqlWorkbenchNotebookShare',   'sql_workbench_notebook_share'),
  ('sqlWorkbenchExecution',       'sql_workbench_execution');

-- audit: QQQ's audit_table holds the QQQ table name
UPDATE audit_table a SET name = r.new_name FROM swb_rename r WHERE a.name = r.old_name;

-- saved views and saved reports
UPDATE saved_view v   SET table_name = r.new_name FROM swb_rename r WHERE v.table_name = r.old_name;
UPDATE saved_report s SET table_name = r.new_name FROM swb_rename r WHERE s.table_name = r.old_name;

-- any app table that stores QQQ table names (for example a search index or a shared-record table)
-- UPDATE quick_search_index q SET table_name = r.new_name FROM swb_rename r WHERE q.table_name = r.old_name;
COMMIT;
```

   Saved views and reports can also name columns of joined tables (`sqlWorkbenchCell.title`) inside their JSON filter or column lists; search those columns for `sqlWorkbench` and replace the table part. Rebuild any search index whose document ids embed table names.
5. **Frontends.** Links to `/app/sqlWorkbench/sqlWorkbenchNotebook/...` become `/app/sqlWorkbench/sql_workbench_notebook/...`.
6. **Navigation and the notebook UI.** The app's home (`/app/sqlWorkbench`) is now a dashboard; the notebook editor moved to the Runner (`/app/sqlWorkbenchRunner`, `?notebook=<id>` opens one). Update bookmarks and links that expected the editor on the app page. The nav lists only Notebooks and the execution audit; if your host builds its own menus or nav tests, drop the cell, version, pinned-result and share tables from them (they are child lists on a notebook). Nothing to grant: the Runner and the dashboard use the app's access permission, and Activity by User the admin permission.
7. **Optional: ESB and Quick Search.** Wire `SqlWorkbenchIntegrations.esbTableNames` and `quickSearchTables` into your configuration (README, "ESB and Quick Search"). Adding tables to Quick Search needs an index rebuild or a basepull for the existing rows.

## To 1.0.0-RC.2

See the "Upgrade from RC.1" section of [CHANGELOG.md](CHANGELOG.md).
