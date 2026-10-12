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

package com.kingsrook.qbits.sqlworkbench.customizers;


import java.util.List;
import java.util.Optional;
import java.util.Set;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** Pinned results are created only by pinning a cached result from the
 ** notebook UI (the data must come from a real run, never from a client);
 ** through the table only the label can be edited, and the notebook's owner
 ** can delete them (the record lock checks ownership through the notebook).
 *******************************************************************************/
public class SnapshotTableCustomizer extends WorkbenchTableCustomizer
{
   private static final Set<String> EDITABLE = Set.of("id", "label");



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> preInsert(InsertInput insertInput, List<QRecord> records, boolean isPreview) throws QException
   {
      if(!isSystemUser() && !insertInput.hasFlag(SqlWorkbenchActionFlag.SNAPSHOT_PIN))
      {
         for(QRecord record : records)
         {
            denied(record, "Results are pinned from the notebook: run a cell, then pin its result.");
         }
      }
      return (records);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> preUpdate(UpdateInput updateInput, List<QRecord> records, boolean isPreview, Optional<List<QRecord>> oldRecordList) throws QException
   {
      if(isSystemUser())
      {
         return (records);
      }
      for(QRecord record : records)
      {
         if(!ok(record))
         {
            continue;
         }
         QRecord old = oldRecord(oldRecordList, record);
         for(String field : List.copyOf(record.getValues().keySet()))
         {
            if(!EDITABLE.contains(field) && changes(record, old, field))
            {
               badInput(record, "Only a pinned result's label can be edited.");
               break;
            }
         }
      }
      return (records);
   }
}
