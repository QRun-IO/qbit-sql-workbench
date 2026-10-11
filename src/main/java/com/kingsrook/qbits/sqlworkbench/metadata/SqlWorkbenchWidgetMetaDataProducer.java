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
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitComponentMetaDataProducer;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;


/*******************************************************************************
 ** The workbench widget, of type SQL_WORKBENCH: the Next UI renders the whole
 ** notebook UI for it from the /qqq/v1/sql-workbench endpoints (as it renders
 ** ESB_OVERVIEW from the ESB endpoints). Same permission as the app.
 *******************************************************************************/
public class SqlWorkbenchWidgetMetaDataProducer extends QBitComponentMetaDataProducer<QWidgetMetaData, SqlWorkbenchQBitConfig>
{
   public static final String NAME = "sqlWorkbenchWidget";
   public static final String TYPE = "SQL_WORKBENCH";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public QWidgetMetaData produce(QInstance qInstance) throws QException
   {
      SqlWorkbenchQBitConfig config = getQBitConfig();
      return (new QWidgetMetaData()
         .withName(config.applyPrefix(NAME))
         .withLabel("SQL Workbench")
         .withType(TYPE)
         .withGridColumns(12)
         .withIsCard(false)
         .withCodeReference(new QCodeReference(SqlWorkbenchWidgetRenderer.class))
         .withPermissionRules(config.getAccessPermissionRules()));
   }



   /*******************************************************************************
    ** after the tables and PVS, before the app
    *******************************************************************************/
   @Override
   public int getSortOrder()
   {
      return (300);
   }

}
