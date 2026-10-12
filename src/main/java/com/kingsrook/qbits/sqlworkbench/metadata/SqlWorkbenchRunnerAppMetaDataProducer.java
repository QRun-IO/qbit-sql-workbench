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
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitComponentMetaDataProducer;


/*******************************************************************************
 ** The Runner: a child app of the sqlWorkbench app whose page is the
 ** SQL_WORKBENCH widget (the interactive notebook editor the Next UI draws).
 ** It is a custom screen, so it has a nav entry; processes never do. Open a
 ** notebook with /app/sqlWorkbenchRunner?notebook=<id>. Same permission as
 ** the app.
 *******************************************************************************/
public class SqlWorkbenchRunnerAppMetaDataProducer extends QBitComponentMetaDataProducer<QAppMetaData, SqlWorkbenchQBitConfig>
{
   public static final String APP_NAME = "sqlWorkbenchRunner";
   public static final String LABEL    = "Runner";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public QAppMetaData produce(QInstance qInstance) throws QException
   {
      SqlWorkbenchQBitConfig config = getQBitConfig();
      return (new QAppMetaData()
         .withName(config.applyPrefix(APP_NAME))
         .withLabel(LABEL)
         .withIcon(new QIcon("play_circle"))
         .withPermissionRules(config.getAccessPermissionRules())
         .withWidgets(List.of(config.applyPrefix(SqlWorkbenchWidgetMetaDataProducer.NAME))));
   }



   /*******************************************************************************
    ** after the widget, before the sqlWorkbench app (its parent)
    *******************************************************************************/
   @Override
   public int getSortOrder()
   {
      return (850);
   }

}
