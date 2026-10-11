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


import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitComponentMetaDataProducer;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;


/*******************************************************************************
 ** Produces the sqlWorkbenchNotebookVersion table.
 *******************************************************************************/
public class SqlWorkbenchNotebookVersionTableMetaDataProducer extends QBitComponentMetaDataProducer<QTableMetaData, SqlWorkbenchQBitConfig>
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public QTableMetaData produce(QInstance qInstance) throws QException
   {
      return (SqlWorkbenchTableMetaDataHelper.buildNotebookVersionTable(getQBitConfig()));
   }



   /*******************************************************************************
    ** tables first, then the PVS, widget and app
    *******************************************************************************/
   @Override
   public int getSortOrder()
   {
      return (120);
   }

}
