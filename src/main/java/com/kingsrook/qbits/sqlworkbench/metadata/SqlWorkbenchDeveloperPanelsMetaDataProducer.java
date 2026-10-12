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
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelsMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitComponentMetaDataProducer;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.developer.SqlWorkbenchDeveloperPanelProvider;


/*******************************************************************************
 ** Registers the "SQL Workbench" record developer panel
 ** ({@link SqlWorkbenchDeveloperPanelProvider}) in the instance's developer
 ** panels (idempotent). The provider decides per table and user whether it
 ** shows.
 *******************************************************************************/
public class SqlWorkbenchDeveloperPanelsMetaDataProducer extends QBitComponentMetaDataProducer<DeveloperPanelsMetaData, SqlWorkbenchQBitConfig>
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public DeveloperPanelsMetaData produce(QInstance qInstance) throws QException
   {
      return (DeveloperPanelsMetaData.ofOrWithNew(qInstance).withProvider(new QCodeReference(SqlWorkbenchDeveloperPanelProvider.class)));
   }

}
