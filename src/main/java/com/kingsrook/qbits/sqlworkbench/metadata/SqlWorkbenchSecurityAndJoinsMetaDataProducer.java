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


import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchSecurity;
import com.kingsrook.qbits.sqlworkbench.model.CellType;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducerMultiOutput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaDataInterface;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.joins.QJoinMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSourceType;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitComponentMetaDataProducer;
import com.kingsrook.qqq.backend.core.model.metadata.security.QSecurityKeyType;


/*******************************************************************************
 ** Produces what the tables' locks and record views need: the two security
 ** key types, the share audience-type PVS, the notebook joins, and the
 ** child-record list widgets on a notebook's record view.
 *******************************************************************************/
public class SqlWorkbenchSecurityAndJoinsMetaDataProducer extends QBitComponentMetaDataProducer<MetaDataProducerMultiOutput, SqlWorkbenchQBitConfig>
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public MetaDataProducerMultiOutput produce(QInstance qInstance) throws QException
   {
      SqlWorkbenchQBitConfig      config = getQBitConfig();
      MetaDataProducerMultiOutput output = new MetaDataProducerMultiOutput();
      for(QSecurityKeyType keyType : SqlWorkbenchSecurity.buildSecurityKeyTypes(config))
      {
         output.add(keyType);
      }
      output.add(SqlWorkbenchTableMetaDataHelper.buildAudienceTypePossibleValueSource(config));
      output.add(QPossibleValueSource.newForEnum(config.applyPrefix(CellType.NAME), CellType.values()));
      output.add(new QPossibleValueSource().withName(config.applyPrefix(WorkbenchBackendPossibleValueProvider.NAME))
         .withType(QPossibleValueSourceType.CUSTOM).withIdType(QFieldType.STRING)
         .withCustomCodeReference(new QCodeReference(WorkbenchBackendPossibleValueProvider.class)));
      for(QJoinMetaData join : SqlWorkbenchTableMetaDataHelper.buildJoins(config))
      {
         output.add(join);
      }
      for(QWidgetMetaDataInterface widget : SqlWorkbenchTableMetaDataHelper.buildChildListWidgets(config))
      {
         output.add(widget);
      }
      return (output);
   }



   /*******************************************************************************
    ** after the tables, before the PVS, widget and app
    *******************************************************************************/
   @Override
   public int getSortOrder()
   {
      return (150);
   }

}
