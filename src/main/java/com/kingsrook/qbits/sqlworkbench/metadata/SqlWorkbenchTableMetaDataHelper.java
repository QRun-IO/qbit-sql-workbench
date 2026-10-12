/*
 * Copyright 2026 Kingsrook, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.sqlworkbench.metadata;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.customizers.AppendOnlyTableCustomizer;
import com.kingsrook.qbits.sqlworkbench.customizers.CellTableCustomizer;
import com.kingsrook.qbits.sqlworkbench.customizers.NotebookTableCustomizer;
import com.kingsrook.qbits.sqlworkbench.customizers.ShareTableCustomizer;
import com.kingsrook.qbits.sqlworkbench.customizers.SnapshotTableCustomizer;
import com.kingsrook.qbits.sqlworkbench.model.ShareAudienceType;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchCell;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchExecution;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebook;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookShare;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookVersion;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchResultSnapshot;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizers;
import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.ChildRecordListRenderer;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;
import com.kingsrook.qqq.backend.core.model.metadata.audits.AuditLevel;
import com.kingsrook.qqq.backend.core.model.metadata.audits.QAuditRules;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaDataInterface;
import com.kingsrook.qqq.backend.core.model.metadata.fields.AdornmentType;
import com.kingsrook.qqq.backend.core.model.metadata.fields.FieldAdornment;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.joins.JoinOn;
import com.kingsrook.qqq.backend.core.model.metadata.joins.JoinType;
import com.kingsrook.qqq.backend.core.model.metadata.joins.QJoinMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.security.MultiRecordSecurityLock;
import com.kingsrook.qqq.backend.core.model.metadata.security.RecordSecurityLock;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Capability;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QFieldSection;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Tier;
import com.kingsrook.qqq.backend.core.model.metadata.tables.UniqueKey;


/*******************************************************************************
 ** Builds the SQL Workbench tables, joins and record-view widgets. Shared by
 ** the component producers and by tests.
 **
 ** The tables are ordinary QQQ tables (query, view, edit, export, audits,
 ** developer mode), secured by QQQ mechanisms rather than by hiding them:
 **
 ** - Permissions: QQQ's per-table read / insert / edit / delete permissions
 **   (SqlWorkbenchQBitConfig lists them per role).
 ** - Record security locks, enforced by the core actions and so for every
 **   entry point (table UI, /qqq/v1 table API, processes, PVS lookups,
 **   exports): a notebook (and its cells, versions and snapshots) is visible
 **   to its owner (sqlWorkbenchUser = ownerUserId) OR to an audience it is
 **   shared with (a share row whose audienceKey is one of the session's
 **   sqlWorkbenchAudience values); only the owner can change it. An
 **   execution is visible to its user only. Workbench admins hold the
 **   all-access key and see every row.
 ** - Append-only: versions and the execution audit have no insert / edit /
 **   delete capability, and a customizer rejects those writes in the core
 **   actions unless the qbit itself (system user) makes them.
 ** - Customizers keep the notebook invariants for edits made through the
 **   tables exactly as through the notebook UI (ownership, cell order and
 **   limits, a new version per change, share keys, library visibility).
 *******************************************************************************/
public class SqlWorkbenchTableMetaDataHelper
{
   public static final String JOIN_NOTEBOOK_CELL     = "sqlWorkbenchNotebookJoinSqlWorkbenchCell";
   public static final String JOIN_NOTEBOOK_VERSION  = "sqlWorkbenchNotebookJoinSqlWorkbenchNotebookVersion";
   public static final String JOIN_NOTEBOOK_SNAPSHOT = "sqlWorkbenchNotebookJoinSqlWorkbenchResultSnapshot";
   public static final String JOIN_NOTEBOOK_SHARE    = "sqlWorkbenchNotebookJoinSqlWorkbenchNotebookShare";
   public static final String JOIN_SHARE_NOTEBOOK    = "sqlWorkbenchNotebookShareJoinSqlWorkbenchNotebook";



   /*******************************************************************************
    ** sqlWorkbenchNotebook
    *******************************************************************************/
   public static QTableMetaData buildNotebookTable(SqlWorkbenchQBitConfig config) throws QException
   {
      QTableMetaData table = text(base(config, SqlWorkbenchNotebook.TABLE_NAME, "SQL Notebook", "menu_book", SqlWorkbenchNotebook.class), "description", "parametersJson")
         .withRecordLabelFormat("%s")
         .withRecordLabelFields("title")
         .withRecordSecurityLock(new MultiRecordSecurityLock()
            .withOperator(MultiRecordSecurityLock.BooleanOperator.OR)
            .withLock(ownerLock(config, "ownerUserId"))
            .withLock(shareLock(config, config.applyPrefix(SqlWorkbenchNotebookShare.TABLE_NAME) + ".audienceKey", config.applyPrefix(JOIN_SHARE_NOTEBOOK))))
         .withAuditRules(new QAuditRules().withAuditLevel(AuditLevel.RECORD))
         .withSection(new QFieldSection("identity", new QIcon("badge"), Tier.T1, List.of("id", "title", "visibility", "ownerName")))
         .withSection(new QFieldSection("details", new QIcon("description"), Tier.T2, List.of("description", "defaultBackendName", "parametersJson")))
         .withSection(widgetSection(config, JOIN_NOTEBOOK_CELL, "notes"))
         .withSection(widgetSection(config, JOIN_NOTEBOOK_SHARE, "share"))
         .withSection(widgetSection(config, JOIN_NOTEBOOK_SNAPSHOT, "push_pin"))
         .withSection(widgetSection(config, JOIN_NOTEBOOK_VERSION, "history"))
         .withSection(new QFieldSection("system", new QIcon("calendar_month"), Tier.T3, List.of("ownerUserId", "currentVersion", "createDate", "modifyDate")));
      table.getField("visibility").setIsEditable(false);
      table.getField("visibility").setIsRequired(false);
      table.getField("visibility").setDefaultValue("PERSONAL");
      table.getField("ownerUserId").setIsRequired(false);
      table.getField("parametersJson").withFieldAdornment(new FieldAdornment(AdornmentType.CODE_EDITOR).withValue(AdornmentType.CodeEditorValues.languageMode("json")));
      return (customize(table, NotebookTableCustomizer.class));
   }



   /*******************************************************************************
    ** sqlWorkbenchCell
    *******************************************************************************/
   public static QTableMetaData buildCellTable(SqlWorkbenchQBitConfig config) throws QException
   {
      QTableMetaData table = text(base(config, SqlWorkbenchCell.TABLE_NAME, "SQL Notebook Cell", "notes", SqlWorkbenchCell.class), "content")
         .withRecordLabelFormat("%s cell %s")
         .withRecordLabelFields("notebookId", "sortOrder")
         .withUniqueKey(new UniqueKey("notebookId", "cellKey"))
         .withRecordSecurityLock(childLock(config, JOIN_NOTEBOOK_CELL))
         .withAuditRules(new QAuditRules().withAuditLevel(AuditLevel.RECORD))
         .withSection(new QFieldSection("identity", new QIcon("badge"), Tier.T1, List.of("id", "notebookId", "sortOrder", "cellType", "title")))
         .withSection(new QFieldSection("content", new QIcon("code"), Tier.T2, List.of("content", "backendName")))
         .withSection(new QFieldSection("system", new QIcon("calendar_month"), Tier.T3, List.of("cellKey", "createDate", "modifyDate")));
      table.getField("cellKey").setIsRequired(false);
      table.getField("cellType").setIsRequired(false);
      table.getField("cellType").setDefaultValue("SQL");
      table.getField("content").withFieldAdornment(new FieldAdornment(AdornmentType.CODE_EDITOR).withValue(AdornmentType.CodeEditorValues.languageMode("sql")));
      return (customize(linkNotebook(config, table), CellTableCustomizer.class));
   }



   /*******************************************************************************
    ** sqlWorkbenchNotebookVersion (append-only)
    *******************************************************************************/
   public static QTableMetaData buildNotebookVersionTable(SqlWorkbenchQBitConfig config) throws QException
   {
      QTableMetaData table = text(base(config, SqlWorkbenchNotebookVersion.TABLE_NAME, "SQL Notebook Version", "history", SqlWorkbenchNotebookVersion.class), "contentJson")
         .withRecordLabelFormat("%s v%s")
         .withRecordLabelFields("notebookId", "versionNumber")
         .withUniqueKey(new UniqueKey("notebookId", "versionNumber"))
         .withRecordSecurityLock(childLock(config, JOIN_NOTEBOOK_VERSION))
         .withSection(new QFieldSection("identity", new QIcon("badge"), Tier.T1, List.of("id", "notebookId", "versionNumber", "comment")))
         .withSection(new QFieldSection("content", new QIcon("code"), Tier.T2, List.of("contentJson")))
         .withSection(new QFieldSection("system", new QIcon("calendar_month"), Tier.T3, List.of("savedByName", "savedByUserId", "createDate")));
      table.getField("comment").setIsEditable(false);
      table.getField("contentJson").withFieldAdornment(new FieldAdornment(AdornmentType.CODE_EDITOR).withValue(AdornmentType.CodeEditorValues.languageMode("json")));
      appendOnly(table);
      return (linkNotebook(config, table));
   }



   /*******************************************************************************
    ** sqlWorkbenchResultSnapshot (pinned from the notebook UI; label edit and
    ** delete through the table)
    *******************************************************************************/
   public static QTableMetaData buildResultSnapshotTable(SqlWorkbenchQBitConfig config) throws QException
   {
      QTableMetaData table = text(base(config, SqlWorkbenchResultSnapshot.TABLE_NAME, "SQL Result Snapshot", "push_pin", SqlWorkbenchResultSnapshot.class), "sqlText", "resultJson")
         .withRecordLabelFormat("%s %s")
         .withRecordLabelFields("notebookId", "label")
         .withRecordSecurityLock(childLock(config, JOIN_NOTEBOOK_SNAPSHOT))
         .withAuditRules(new QAuditRules().withAuditLevel(AuditLevel.RECORD))
         .withSection(new QFieldSection("identity", new QIcon("badge"), Tier.T1, List.of("id", "notebookId", "label", "cellKey")))
         .withSection(new QFieldSection("result", new QIcon("table_chart"), Tier.T2, List.of("backendName", "rowCount", "truncated", "executionUuid", "sqlText", "resultJson")))
         .withSection(new QFieldSection("system", new QIcon("calendar_month"), Tier.T3, List.of("createdByUserId", "createDate")));
      table.getField("sqlText").withFieldAdornment(new FieldAdornment(AdornmentType.CODE_EDITOR).withValue(AdornmentType.CodeEditorValues.languageMode("sql")));
      table.getField("resultJson").withFieldAdornment(new FieldAdornment(AdornmentType.CODE_EDITOR).withValue(AdornmentType.CodeEditorValues.languageMode("json")));
      table.withoutCapability(Capability.TABLE_INSERT);
      return (customize(linkNotebook(config, table), SnapshotTableCustomizer.class));
   }



   /*******************************************************************************
    ** sqlWorkbenchNotebookShare: who, besides the owner, can read a notebook.
    *******************************************************************************/
   public static QTableMetaData buildNotebookShareTable(SqlWorkbenchQBitConfig config) throws QException
   {
      QTableMetaData table = base(config, SqlWorkbenchNotebookShare.TABLE_NAME, "SQL Notebook Share", "share", SqlWorkbenchNotebookShare.class)
         .withRecordLabelFormat("%s - %s")
         .withRecordLabelFields("notebookId", "audienceKey")
         .withUniqueKey(new UniqueKey("notebookId", "audienceKey"))
         .withRecordSecurityLock(new MultiRecordSecurityLock()
            .withOperator(MultiRecordSecurityLock.BooleanOperator.OR)
            .withLock(ownerLock(config, config.applyPrefix(SqlWorkbenchNotebook.TABLE_NAME) + ".ownerUserId", config.applyPrefix(JOIN_NOTEBOOK_SHARE)))
            .withLock(shareLock(config, "audienceKey")))
         .withAuditRules(new QAuditRules().withAuditLevel(AuditLevel.FIELD))
         .withSection(new QFieldSection("identity", new QIcon("badge"), Tier.T1, List.of("id", "notebookId", "audienceType", "audienceId")))
         .withSection(new QFieldSection("system", new QIcon("calendar_month"), Tier.T3, List.of("audienceKey", "createdByName", "createdByUserId", "createDate", "modifyDate")));
      table.getField("audienceType").setPossibleValueSourceName(config.applyPrefix(ShareAudienceType.NAME));
      return (customize(linkNotebook(config, table), ShareTableCustomizer.class));
   }



   /*******************************************************************************
    ** sqlWorkbenchExecution (the audit; append-only)
    *******************************************************************************/
   public static QTableMetaData buildExecutionTable(SqlWorkbenchQBitConfig config) throws QException
   {
      QTableMetaData table = text(base(config, SqlWorkbenchExecution.TABLE_NAME, "SQL Execution Audit", "policy", SqlWorkbenchExecution.class), "sqlText", "parametersJson", "errorMessage")
         .withUniqueKey(new UniqueKey("executionUuid"))
         .withRecordLabelFormat("%s %s")
         .withRecordLabelFields("mode", "executionUuid")
         .withRecordSecurityLock(ownerLock(config, "userId"))
         .withSection(new QFieldSection("identity", new QIcon("badge"), Tier.T1, List.of("id", "executionUuid", "mode", "status", "backendName")))
         .withSection(new QFieldSection("run", new QIcon("timer"), Tier.T2, List.of("userName", "userId", "startTime", "endTime", "durationMs", "rowsReturned", "rowsAffected", "truncated",
            "statementCount", "statementKinds", "notebookId", "cellKey", "clientIp")))
         .withSection(new QFieldSection("sql", new QIcon("code"), Tier.T2, List.of("sqlText", "sqlHash", "parametersJson", "errorMessage")))
         .withSection(new QFieldSection("system", new QIcon("calendar_month"), Tier.T3, List.of("createDate")));
      table.getField("sqlText").withFieldAdornment(new FieldAdornment(AdornmentType.CODE_EDITOR).withValue(AdornmentType.CodeEditorValues.languageMode("sql")));
      table.getField("notebookId").setPossibleValueSourceName(config.applyPrefix(SqlWorkbenchNotebook.TABLE_NAME));
      appendOnly(table);
      return (table);
   }



   /*******************************************************************************
    ** Possible value source over the notebook table, for notebookId fields.
    ** Lookups go through the table's locks, so they only show readable
    ** notebooks.
    *******************************************************************************/
   public static QPossibleValueSource buildNotebookPossibleValueSource(SqlWorkbenchQBitConfig config)
   {
      return (QPossibleValueSource.newForTable(config.applyPrefix(SqlWorkbenchNotebook.TABLE_NAME)));
   }



   /*******************************************************************************
    ** Possible value source for share audience types.
    *******************************************************************************/
   public static QPossibleValueSource buildAudienceTypePossibleValueSource(SqlWorkbenchQBitConfig config)
   {
      return (QPossibleValueSource.newForEnum(config.applyPrefix(ShareAudienceType.NAME), ShareAudienceType.values()));
   }



   /*******************************************************************************
    ** All six tables.
    *******************************************************************************/
   public static List<QTableMetaData> buildAllTables(SqlWorkbenchQBitConfig config) throws QException
   {
      return (List.of(buildNotebookTable(config), buildCellTable(config), buildNotebookVersionTable(config), buildResultSnapshotTable(config),
         buildNotebookShareTable(config), buildExecutionTable(config)));
   }



   /*******************************************************************************
    ** The joins: notebook to each child (one-to-many), and share to notebook
    ** (many-to-one, the direction the share locks walk).
    *******************************************************************************/
   public static List<QJoinMetaData> buildJoins(SqlWorkbenchQBitConfig config)
   {
      String              notebook = config.applyPrefix(SqlWorkbenchNotebook.TABLE_NAME);
      Map<String, String> children = new LinkedHashMap<>();
      children.put(JOIN_NOTEBOOK_CELL, SqlWorkbenchCell.TABLE_NAME);
      children.put(JOIN_NOTEBOOK_VERSION, SqlWorkbenchNotebookVersion.TABLE_NAME);
      children.put(JOIN_NOTEBOOK_SNAPSHOT, SqlWorkbenchResultSnapshot.TABLE_NAME);
      children.put(JOIN_NOTEBOOK_SHARE, SqlWorkbenchNotebookShare.TABLE_NAME);

      List<QJoinMetaData> joins = new ArrayList<>();
      for(Map.Entry<String, String> entry : children.entrySet())
      {
         joins.add(new QJoinMetaData()
            .withName(config.applyPrefix(entry.getKey()))
            .withLeftTable(notebook)
            .withRightTable(config.applyPrefix(entry.getValue()))
            .withType(JoinType.ONE_TO_MANY)
            .withJoinOn(new JoinOn("id", "notebookId")));
      }
      joins.add(new QJoinMetaData()
         .withName(config.applyPrefix(JOIN_SHARE_NOTEBOOK))
         .withLeftTable(config.applyPrefix(SqlWorkbenchNotebookShare.TABLE_NAME))
         .withRightTable(notebook)
         .withType(JoinType.MANY_TO_ONE)
         .withJoinOn(new JoinOn("notebookId", "id")));
      return (joins);
   }



   /*******************************************************************************
    ** Child-record list widgets on the notebook's record view: cells (add and
    ** edit in place), shares (add, for users who may share), pinned results,
    ** versions.
    *******************************************************************************/
   public static List<QWidgetMetaDataInterface> buildChildListWidgets(SqlWorkbenchQBitConfig config)
   {
      Map<String, QJoinMetaData> joins = new LinkedHashMap<>();
      for(QJoinMetaData join : buildJoins(config))
      {
         joins.put(join.getName(), join);
      }

      List<QWidgetMetaDataInterface> widgets = new ArrayList<>();
      widgets.add(ChildRecordListRenderer.widgetMetaDataBuilder(joins.get(config.applyPrefix(JOIN_NOTEBOOK_CELL)))
         .withLabel("Cells").withCanAddChildRecord(true).withMaxRows(200)
         .withOnlyIncludeFieldNames(List.of("id", "sortOrder", "cellType", "title", "backendName", "modifyDate"))
         .getWidgetMetaData());
      widgets.add(ChildRecordListRenderer.widgetMetaDataBuilder(joins.get(config.applyPrefix(JOIN_NOTEBOOK_SHARE)))
         .withLabel("Shared With").withCanAddChildRecord(true)
         .withOnlyIncludeFieldNames(List.of("id", "audienceType", "audienceId", "createdByName", "createDate"))
         .getWidgetMetaData());
      widgets.add(ChildRecordListRenderer.widgetMetaDataBuilder(joins.get(config.applyPrefix(JOIN_NOTEBOOK_SNAPSHOT)))
         .withLabel("Pinned Results")
         .withOnlyIncludeFieldNames(List.of("id", "label", "cellKey", "rowCount", "truncated", "createDate"))
         .getWidgetMetaData());
      widgets.add(ChildRecordListRenderer.widgetMetaDataBuilder(joins.get(config.applyPrefix(JOIN_NOTEBOOK_VERSION)))
         .withLabel("Versions").withMaxRows(25)
         .withOnlyIncludeFieldNames(List.of("id", "versionNumber", "comment", "savedByName", "createDate"))
         .getWidgetMetaData());
      return (widgets);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static QTableMetaData base(SqlWorkbenchQBitConfig config, String name, String label, String icon, Class<? extends QRecordEntity> entity) throws QException
   {
      QTableMetaData table = new QTableMetaData();
      table.withName(config.applyPrefix(name))
         .withLabel(label)
         .withIcon(new QIcon(icon))
         .withBackendName(config.getBackendName())
         .withPrimaryKeyField("id")
         .withPermissionRules(config.getTablePermissionRules())
         .withFieldsFromEntity(entity);
      return (table);
   }



   /*******************************************************************************
    ** Owner lock: the session's sqlWorkbenchUser value must equal the field
    ** (read and write).
    *******************************************************************************/
   static RecordSecurityLock ownerLock(SqlWorkbenchQBitConfig config, String fieldName, String... joinNameChain)
   {
      RecordSecurityLock lock = new RecordSecurityLock()
         .withSecurityKeyType(config.getUserSecurityKeyTypeName())
         .withFieldName(fieldName)
         .withLockScope(RecordSecurityLock.LockScope.READ_AND_WRITE);
      if(joinNameChain.length > 0)
      {
         lock.setJoinNameChain(List.of(joinNameChain));
      }
      return (lock);
   }



   /*******************************************************************************
    ** Share lock: a share row's audienceKey is one of the session's
    ** sqlWorkbenchAudience values (read only).
    *******************************************************************************/
   static RecordSecurityLock shareLock(SqlWorkbenchQBitConfig config, String fieldName, String... joinNameChain)
   {
      RecordSecurityLock lock = new RecordSecurityLock()
         .withSecurityKeyType(config.getAudienceSecurityKeyTypeName())
         .withFieldName(fieldName)
         .withLockScope(RecordSecurityLock.LockScope.READ);
      if(joinNameChain.length > 0)
      {
         lock.setJoinNameChain(List.of(joinNameChain));
      }
      return (lock);
   }



   /*******************************************************************************
    ** Lock for a notebook child table: the notebook's owner, or (read only)
    ** an audience the notebook is shared with.
    *******************************************************************************/
   private static MultiRecordSecurityLock childLock(SqlWorkbenchQBitConfig config, String notebookJoinChild)
   {
      return (new MultiRecordSecurityLock()
         .withOperator(MultiRecordSecurityLock.BooleanOperator.OR)
         .withLock(ownerLock(config, config.applyPrefix(SqlWorkbenchNotebook.TABLE_NAME) + ".ownerUserId", config.applyPrefix(notebookJoinChild)))
         .withLock(shareLock(config, config.applyPrefix(SqlWorkbenchNotebookShare.TABLE_NAME) + ".audienceKey", config.applyPrefix(JOIN_SHARE_NOTEBOOK), config.applyPrefix(notebookJoinChild))));
   }



   /*******************************************************************************
    ** A record-view section that shows one of the child-list widgets.
    *******************************************************************************/
   private static QFieldSection widgetSection(SqlWorkbenchQBitConfig config, String joinName, String icon)
   {
      return (new QFieldSection(config.applyPrefix(joinName), new QIcon(icon), Tier.T2).withWidgetName(config.applyPrefix(joinName)));
   }



   /*******************************************************************************
    ** Only the qbit (system user) writes this table: the capabilities hide
    ** writes in the UI / API, the customizer rejects them in the core actions.
    *******************************************************************************/
   private static void appendOnly(QTableMetaData table)
   {
      table.withoutCapabilities(Capability.TABLE_INSERT, Capability.TABLE_UPDATE, Capability.TABLE_DELETE);
      customize(table, AppendOnlyTableCustomizer.class);
   }



   /*******************************************************************************
    ** Register one customizer class for every insert / update / delete hook.
    *******************************************************************************/
   private static QTableMetaData customize(QTableMetaData table, Class<?> customizer)
   {
      QCodeReference reference = new QCodeReference(customizer);
      for(TableCustomizers role : List.of(TableCustomizers.PRE_INSERT_RECORD, TableCustomizers.POST_INSERT_RECORD, TableCustomizers.PRE_UPDATE_RECORD,
         TableCustomizers.POST_UPDATE_RECORD, TableCustomizers.PRE_DELETE_RECORD, TableCustomizers.POST_DELETE_RECORD))
      {
         table.withCustomizer(role, reference);
      }
      return (table);
   }



   /*******************************************************************************
    ** Point notebookId at the (possibly prefixed) notebook PVS.
    *******************************************************************************/
   private static QTableMetaData linkNotebook(SqlWorkbenchQBitConfig config, QTableMetaData table)
   {
      QFieldMetaData field = table.getField("notebookId");
      field.setPossibleValueSourceName(config.applyPrefix(SqlWorkbenchNotebook.TABLE_NAME));
      return (table);
   }



   /*******************************************************************************
    ** Make String fields TEXT (unbounded).
    *******************************************************************************/
   private static QTableMetaData text(QTableMetaData table, String... names)
   {
      for(String name : names)
      {
         table.getField(name).setType(QFieldType.TEXT);
      }
      return (table);
   }

}
