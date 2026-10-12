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
import java.util.List;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducerMultiOutput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitComponentMetaDataProducer;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitProductionContext;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchExecution;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebook;


/*******************************************************************************
 ** The sqlWorkbench app. Its home page is an information dashboard (see
 ** SqlWorkbenchHomeWidgetsMetaDataProducer); its nav lists:
 **
 ** - Runner: the interactive notebook editor (the SQL_WORKBENCH widget), a
 **   child app of its own;
 ** - the main tables people use directly: Notebooks and the Execution
 **   audit.
 **
 ** The sub-tables (cells, versions, pinned results, shares) are not in the
 ** nav: they are child lists and relations on a notebook's record view, and
 ** stay standard QQQ tables reachable by URL and the table API. Gated by the
 ** access permission; each table also needs its own read permission to
 ** appear. No processes in the nav (record actions live on the tables).
 ** Hosts can re-parent it (e.g. under an Admin app); its name stays the same.
 *******************************************************************************/
public class SqlWorkbenchAppMetaDataProducer extends QBitComponentMetaDataProducer<QAppMetaData, SqlWorkbenchQBitConfig>
{
   public static final String APP_NAME = "sqlWorkbench";

   /////////////////////////////////////////////////////////////////////////
   // the main tables, in nav order; the rest are reached from a notebook //
   /////////////////////////////////////////////////////////////////////////
   public static final List<String> MAIN_TABLES = List.of(SqlWorkbenchNotebook.TABLE_NAME, SqlWorkbenchExecution.TABLE_NAME);



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public QAppMetaData produce(QInstance qInstance) throws QException
   {
      SqlWorkbenchQBitConfig config = getQBitConfig();

      QAppMetaData app = new QAppMetaData()
         .withName(config.applyPrefix(APP_NAME))
         .withLabel("SQL Workbench")
         .withIcon(new QIcon("terminal"))
         .withPermissionRules(config.getAccessPermissionRules())
         .withWidgets(new ArrayList<>(SqlWorkbenchHomeWidgetsMetaDataProducer.homeWidgetNames().stream().map(config::applyPrefix).toList()));

      MetaDataProducerMultiOutput produced = QBitProductionContext.peekMetaDataProducerMultiOutput();
      if(produced != null)
      {
         QAppMetaData runner = produced.get(QAppMetaData.class, config.applyPrefix(SqlWorkbenchRunnerAppMetaDataProducer.APP_NAME));
         if(runner != null)
         {
            app.withChild(runner);
         }
         for(String tableName : MAIN_TABLES)
         {
            QTableMetaData table = produced.get(QTableMetaData.class, config.applyPrefix(tableName));
            if(table != null)
            {
               app.withChild(table);
            }
         }
      }
      return (app);
   }



   /*******************************************************************************
    ** last, so the tables, widgets and Runner app exist in the output
    *******************************************************************************/
   @Override
   public int getSortOrder()
   {
      return (900);
   }

}
