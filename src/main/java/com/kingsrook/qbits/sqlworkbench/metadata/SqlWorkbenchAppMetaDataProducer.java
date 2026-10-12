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
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducerMultiOutput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitComponentMetaDataProducer;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitProductionContext;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchCell;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchExecution;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebook;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookShare;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookVersion;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchResultSnapshot;


/*******************************************************************************
 ** The sqlWorkbench app: the workbench widget (rendered by the Next UI as the
 ** notebook UI), plus every workbench table as a child - notebooks, cells,
 ** versions, pinned results, shares and the execution audit - so the data
 ** is administered through the standard QQQ table and record screens. Gated
 ** by the access permission; each table also needs its own read permission
 ** to appear. No processes in the nav (record actions live on the tables).
 ** Hosts can re-parent it (e.g. under an Admin app); its name stays the same.
 *******************************************************************************/
public class SqlWorkbenchAppMetaDataProducer extends QBitComponentMetaDataProducer<QAppMetaData, SqlWorkbenchQBitConfig>
{
   public static final String APP_NAME = "sqlWorkbench";

   static final List<String> TABLES_IN_NAV = List.of(SqlWorkbenchNotebook.TABLE_NAME, SqlWorkbenchCell.TABLE_NAME, SqlWorkbenchNotebookShare.TABLE_NAME,
      SqlWorkbenchResultSnapshot.TABLE_NAME, SqlWorkbenchNotebookVersion.TABLE_NAME, SqlWorkbenchExecution.TABLE_NAME);



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
         .withWidgets(List.of(config.applyPrefix(SqlWorkbenchWidgetMetaDataProducer.NAME)));

      MetaDataProducerMultiOutput produced = QBitProductionContext.peekMetaDataProducerMultiOutput();
      if(produced != null)
      {
         for(String tableName : TABLES_IN_NAV)
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
    ** last, so the tables and widget exist in the output
    *******************************************************************************/
   @Override
   public int getSortOrder()
   {
      return (900);
   }

}
