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
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** Notebook versions and the execution audit are append-only: only the qbit
 ** itself (the system user) inserts them, and only its retention pruning (auditRetentionDays, notebookVersionRetention)
 ** deletes them. The tables also lack the insert / update / delete
 ** capabilities, which hides those operations in the UI and API; this
 ** customizer enforces it in the core actions, whoever calls them (admins
 ** included).
 *******************************************************************************/
public class AppendOnlyTableCustomizer extends WorkbenchTableCustomizer
{
   private static final String MESSAGE = "This table is append-only: only the SQL Workbench writes it.";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> preInsert(InsertInput insertInput, List<QRecord> records, boolean isPreview) throws QException
   {
      return (denyUnlessSystem(records));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> preUpdate(UpdateInput updateInput, List<QRecord> records, boolean isPreview, Optional<List<QRecord>> oldRecordList) throws QException
   {
      return (denyUnlessSystem(records));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> preDelete(DeleteInput deleteInput, List<QRecord> records, boolean isPreview) throws QException
   {
      return (denyUnlessSystem(records));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static List<QRecord> denyUnlessSystem(List<QRecord> records)
   {
      if(!isSystemUser())
      {
         for(QRecord record : records)
         {
            denied(record, MESSAGE);
         }
      }
      return (records);
   }
}
