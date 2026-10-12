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

package com.kingsrook.qbits.sqlworkbench.developer;


import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QNotFoundException;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanel;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelBlock;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelContext;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelLink;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelProviderInterface;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelTone;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchRuntime;
import com.kingsrook.qbits.sqlworkbench.engine.WorkbenchBackends;
import com.kingsrook.qbits.sqlworkbench.metadata.SqlWorkbenchAppMetaDataProducer;
import com.kingsrook.qqq.backend.module.rdbms.actions.AbstractRDBMSAction;


/*******************************************************************************
 ** Record developer panel "SQL Workbench": read-only SELECTs for the record
 ** (and its table) in the backend's own table and column names, with links
 ** that open them in the workbench as new cells (never run).
 **
 ** Available when the workbench is in the instance (its runtime and app), the
 ** user has the workbench access permission (the same check its endpoints
 ** make), and the table's backend is an RDBMS backend the workbench allows.
 ** The panel only builds text: nothing is executed, and the user's write
 ** permission makes no difference.
 *******************************************************************************/
public class SqlWorkbenchDeveloperPanelProvider implements DeveloperPanelProviderInterface
{
   public static final String ID = "sqlWorkbench";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getId()
   {
      return (ID);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getTitle()
   {
      return ("SQL Workbench");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public Integer getOrder()
   {
      return (450);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getIcon()
   {
      return ("terminal");
   }



   /*******************************************************************************
    ** Available for a table on an allowed RDBMS backend, to users with the
    ** workbench access permission, when the workbench app exists.
    *******************************************************************************/
   @Override
   public boolean isAvailable(DeveloperPanelContext context)
   {
      return (resolve(context) != null);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public DeveloperPanel build(DeveloperPanelContext context) throws QException
   {
      Resolved resolved = resolve(context);
      if(resolved == null)
      {
         throw (new QNotFoundException("The SQL Workbench is not available for this table."));
      }

      QTableMetaData           table      = context.getTable();
      WorkbenchBackends.Target target     = resolved.target();
      String                   appName    = resolved.config().applyPrefix(SqlWorkbenchAppMetaDataProducer.APP_NAME);
      Serializable             primaryKey = context.getPrimaryKey();

      DeveloperPanel panel = new DeveloperPanel()
         .withDescription("Read-only SQL for this record on the " + target.name() + " backend, in the database's own table and column names.")
         .withStatusLabel("Read-only")
         .withStatusTone(DeveloperPanelTone.INFO);

      DeveloperPanelBlock links = DeveloperPanelBlock.links("Open in SQL Workbench");

      if(primaryKey != null)
      {
         String recordSql = RecordSql.selectRecord(table, target.dialect(), primaryKey);
         panel.withBlock(DeveloperPanelBlock.code("Read-only query for this record", "sql", recordSql));
         links.withLink(new DeveloperPanelLink()
            .withLabel("Open in SQL Workbench")
            .withHref(workbenchHref(appName, recordSql, target.name()))
            .withDescription("This record's query, as a new cell (not run)"));
      }

      String tableSql = RecordSql.selectTable(table, target.dialect());
      panel.withBlock(DeveloperPanelBlock.code("Read-only query for this table (first " + RecordSql.TABLE_ROW_LIMIT + " rows)", "sql", tableSql));
      links.withLink(new DeveloperPanelLink()
         .withLabel(primaryKey == null ? "Open in SQL Workbench" : "Open table query in SQL Workbench")
         .withHref(workbenchHref(appName, tableSql, target.name()))
         .withDescription("The table's query, as a new cell (not run)"));

      panel.withBlock(DeveloperPanelBlock.keyValues("Backend")
         .withItem("Backend", target.name())
         .withItem("Dialect", target.dialect().getUiName())
         .withItem("Database table", AbstractRDBMSAction.getTableName(table))
         .withItem("Read-only replica", describeReadOnlySource(resolved)));

      panel.withBlock(links);

      panel.withBlock(DeveloperPanelBlock.text("The workbench runs only what you execute, under your SQL Workbench permissions. "
         + "These queries are SELECTs; opening them does not run them.", DeveloperPanelTone.INFO));

      return (panel);
   }



   /*******************************************************************************
    ** The workbench page with the SQL to open (as a new cell) and its backend.
    *******************************************************************************/
   static String workbenchHref(String appName, String sql, String backendName)
   {
      return ("/app/" + encode(appName) + "?sql=" + encode(sql) + "&backend=" + encode(backendName));
   }



   /*******************************************************************************
    ** URL-encode a query parameter (spaces as %20, not '+').
    *******************************************************************************/
   static String encode(String value)
   {
      return (URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"));
   }



   /*******************************************************************************
    ** Where read-only workbench sessions on the target connect, as
    ** WorkbenchBackends.connectionSource resolves it.
    *******************************************************************************/
   private String describeReadOnlySource(Resolved resolved)
   {
      if(resolved.backends().hasReadOnlyCredentials(resolved.target()))
      {
         return ("No (dedicated read-only credentials)");
      }
      if(Boolean.TRUE.equals(resolved.config().getUseBackendReadOnlyReplica()) && resolved.target().backend().getReadOnlyBackendMetaData() != null)
      {
         return ("Yes (" + resolved.target().backend().getReadOnlyBackendMetaData().getName() + ")");
      }
      return ("No");
   }



   /*******************************************************************************
    ** The workbench config and target for the context's table, or null when
    ** the panel is not available.
    *******************************************************************************/
   private Resolved resolve(DeveloperPanelContext context)
   {
      QInstance qInstance = QContext.getQInstance();
      QSession  session   = QContext.getQSession();
      if(qInstance == null || session == null || context == null || context.getTable() == null)
      {
         return (null);
      }

      QTableMetaData table = context.getTable();
      if(!StringUtils.hasContent(table.getBackendName()) || !StringUtils.hasContent(table.getPrimaryKeyField()) || table.getField(table.getPrimaryKeyField()) == null)
      {
         return (null);
      }

      SqlWorkbenchRuntime runtime;
      try
      {
         runtime = SqlWorkbenchRuntime.get(qInstance);
      }
      catch(QNotFoundException e)
      {
         return (null);
      }

      SqlWorkbenchQBitConfig config = runtime.getConfig();
      if(qInstance.getApp(config.applyPrefix(SqlWorkbenchAppMetaDataProducer.APP_NAME)) == null || !session.hasPermission(config.getAccessPermissionName()))
      {
         return (null);
      }

      WorkbenchBackends backends = new WorkbenchBackends(qInstance, runtime);
      try
      {
         return (new Resolved(config, backends, backends.getTarget(table.getBackendName())));
      }
      catch(QNotFoundException e)
      {
         return (null);
      }
   }



   /*******************************************************************************
    ** The workbench config, backends and target for a table.
    *******************************************************************************/
   private record Resolved(SqlWorkbenchQBitConfig config, WorkbenchBackends backends, WorkbenchBackends.Target target)
   {
   }

}
