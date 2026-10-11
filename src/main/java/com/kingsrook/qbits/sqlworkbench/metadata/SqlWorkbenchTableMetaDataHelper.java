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


import java.util.List;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Capability;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.UniqueKey;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchCell;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchExecution;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebook;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookVersion;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchResultSnapshot;


/*******************************************************************************
 ** Builds the QTableMetaData of the five SQL Workbench tables. Shared by the
 ** component producers and by tests.
 **
 ** All tables are protected by the ADMIN permission
 ** (sqlWorkbenchAdmin.hasAccess), not the access permission every workbench
 ** user holds: the generic QQQ table UI / API would otherwise let any user
 ** read other users' personal notebooks, pinned result data and the full
 ** execution audit. The workbench endpoints do their own owner / visibility
 ** checks and then read and write these tables as the system user.
 **
 ** Cells, versions, snapshots and the audit are read-only even for admins
 ** (insert / edit / delete capabilities disabled); only the qbit writes
 ** them. Notebooks stay editable for admins (e.g. to clean up).
 *******************************************************************************/
public class SqlWorkbenchTableMetaDataHelper
{

   /*******************************************************************************
    ** sqlWorkbenchNotebook
    *******************************************************************************/
   public static QTableMetaData buildNotebookTable(SqlWorkbenchQBitConfig config) throws QException
   {
      return (text(base(config, SqlWorkbenchNotebook.TABLE_NAME, "SQL Notebook", "menu_book", SqlWorkbenchNotebook.class), "description", "parametersJson")
         .withRecordLabelFormat("%s")
         .withRecordLabelFields("title"));
   }



   /*******************************************************************************
    ** sqlWorkbenchCell
    *******************************************************************************/
   public static QTableMetaData buildCellTable(SqlWorkbenchQBitConfig config) throws QException
   {
      QTableMetaData table = text(base(config, SqlWorkbenchCell.TABLE_NAME, "SQL Notebook Cell", "notes", SqlWorkbenchCell.class), "content");
      table.setIsHidden(true);
      table.withUniqueKey(new UniqueKey("notebookId", "cellKey"));
      readOnly(table);
      return (linkNotebook(config, table));
   }



   /*******************************************************************************
    ** sqlWorkbenchNotebookVersion
    *******************************************************************************/
   public static QTableMetaData buildNotebookVersionTable(SqlWorkbenchQBitConfig config) throws QException
   {
      QTableMetaData table = text(base(config, SqlWorkbenchNotebookVersion.TABLE_NAME, "SQL Notebook Version", "history", SqlWorkbenchNotebookVersion.class), "contentJson");
      table.setIsHidden(true);
      table.withUniqueKey(new UniqueKey("notebookId", "versionNumber"));
      readOnly(table);
      return (linkNotebook(config, table));
   }



   /*******************************************************************************
    ** sqlWorkbenchResultSnapshot
    *******************************************************************************/
   public static QTableMetaData buildResultSnapshotTable(SqlWorkbenchQBitConfig config) throws QException
   {
      QTableMetaData table = text(base(config, SqlWorkbenchResultSnapshot.TABLE_NAME, "SQL Result Snapshot", "push_pin", SqlWorkbenchResultSnapshot.class), "sqlText", "resultJson");
      table.setIsHidden(true);
      readOnly(table);
      return (linkNotebook(config, table));
   }



   /*******************************************************************************
    ** sqlWorkbenchExecution (the audit)
    *******************************************************************************/
   public static QTableMetaData buildExecutionTable(SqlWorkbenchQBitConfig config) throws QException
   {
      QTableMetaData table = text(base(config, SqlWorkbenchExecution.TABLE_NAME, "SQL Execution Audit", "policy", SqlWorkbenchExecution.class), "sqlText", "parametersJson", "errorMessage");
      table.withUniqueKey(new UniqueKey("executionUuid"));
      readOnly(table);
      table.withRecordLabelFormat("%s %s").withRecordLabelFields("mode", "executionUuid");
      return (table);
   }



   /*******************************************************************************
    ** Possible value source over the notebook table, for notebookId fields.
    *******************************************************************************/
   public static QPossibleValueSource buildNotebookPossibleValueSource(SqlWorkbenchQBitConfig config)
   {
      return (QPossibleValueSource.newForTable(config.applyPrefix(SqlWorkbenchNotebook.TABLE_NAME)));
   }



   /*******************************************************************************
    ** All five tables.
    *******************************************************************************/
   public static List<QTableMetaData> buildAllTables(SqlWorkbenchQBitConfig config) throws QException
   {
      return (List.of(buildNotebookTable(config), buildCellTable(config), buildNotebookVersionTable(config), buildResultSnapshotTable(config), buildExecutionTable(config)));
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
         .withPermissionRules(config.getAdminPermissionRules())
         .withFieldsFromEntity(entity);
      return (table);
   }



   /*******************************************************************************
    ** Only the qbit (as the system user) writes this table.
    *******************************************************************************/
   private static void readOnly(QTableMetaData table)
   {
      table.withoutCapabilities(Capability.TABLE_INSERT, Capability.TABLE_UPDATE, Capability.TABLE_DELETE);
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
