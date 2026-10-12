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


import java.io.Serializable;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchRuntime;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchSecurity;
import com.kingsrook.qbits.sqlworkbench.notebook.NotebookVersioning;
import com.kingsrook.qqq.backend.core.actions.QBackendTransaction;
import com.kingsrook.qqq.backend.core.actions.customizers.AbstractPreInsertCustomizer;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.statusmessages.BadInputStatusMessage;
import com.kingsrook.qqq.backend.core.model.statusmessages.PermissionDeniedMessage;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Shared plumbing for the workbench table customizers.
 **
 ** Pre-insert customizers run before QQQ's validations, so they can stamp
 ** the owner / defaults the record locks and required-field checks then
 ** verify. Update and delete validations (locks included) run before the
 ** pre-update / pre-delete customizers, which skip records that already
 ** carry errors and never rewrite a lock field.
 *******************************************************************************/
public abstract class WorkbenchTableCustomizer implements TableCustomizerInterface
{
   protected static final QLogger LOG = QLogger.getLogger(WorkbenchTableCustomizer.class);


   /*******************************************************************************
    ** Stamp defaults before QQQ validates (required fields, record locks).
    *******************************************************************************/
   @Override
   public AbstractPreInsertCustomizer.WhenToRun whenToRunPreInsert(InsertInput insertInput, boolean isPreview)
   {
      return (AbstractPreInsertCustomizer.WhenToRun.BEFORE_ALL_VALIDATIONS);
   }



   /*******************************************************************************
    ** Pass-through; subclasses override the hooks they need.
    *******************************************************************************/
   @Override
   public List<QRecord> preInsert(InsertInput insertInput, List<QRecord> records, boolean isPreview) throws QException
   {
      return (records);
   }



   /*******************************************************************************
    ** Pass-through.
    *******************************************************************************/
   @Override
   public List<QRecord> postInsert(InsertInput insertInput, List<QRecord> records) throws QException
   {
      return (records);
   }



   /*******************************************************************************
    ** Pass-through.
    *******************************************************************************/
   @Override
   public List<QRecord> preUpdate(UpdateInput updateInput, List<QRecord> records, boolean isPreview, Optional<List<QRecord>> oldRecordList) throws QException
   {
      return (records);
   }



   /*******************************************************************************
    ** Pass-through.
    *******************************************************************************/
   @Override
   public List<QRecord> postUpdate(UpdateInput updateInput, List<QRecord> records, Optional<List<QRecord>> oldRecordList) throws QException
   {
      return (records);
   }



   /*******************************************************************************
    ** Pass-through.
    *******************************************************************************/
   @Override
   public List<QRecord> preDelete(DeleteInput deleteInput, List<QRecord> records, boolean isPreview) throws QException
   {
      return (records);
   }



   /*******************************************************************************
    ** Pass-through.
    *******************************************************************************/
   @Override
   public List<QRecord> postDelete(DeleteInput deleteInput, List<QRecord> records) throws QException
   {
      return (records);
   }



   /*******************************************************************************
    ** The old record for a record, by primary key "id".
    *******************************************************************************/
   protected static QRecord oldRecord(Optional<List<QRecord>> oldRecordList, QRecord record)
   {
      Serializable id = record.getValue("id");
      for(QRecord old : oldRecordList.orElse(List.of()))
      {
         if(id != null && Objects.equals(String.valueOf(id), String.valueOf(old.getValue("id"))))
         {
            return (old);
         }
      }
      return (null);
   }



   /*******************************************************************************
    ** The installed workbench's configuration.
    *******************************************************************************/
   protected static SqlWorkbenchQBitConfig config() throws QException
   {
      return (SqlWorkbenchRuntime.get().getConfig());
   }



   /*******************************************************************************
    ** The workbench clock.
    *******************************************************************************/
   protected static Clock clock() throws QException
   {
      return (SqlWorkbenchRuntime.get().getClock());
   }



   /*******************************************************************************
    ** True if the record has no errors yet.
    *******************************************************************************/
   protected static boolean ok(QRecord record)
   {
      return (CollectionUtils.nullSafeIsEmpty(record.getErrors()));
   }



   /*******************************************************************************
    ** Add a bad-input error to a record.
    *******************************************************************************/
   protected static void badInput(QRecord record, String message)
   {
      record.addError(new BadInputStatusMessage(message));
   }



   /*******************************************************************************
    ** Add a permission-denied error to a record.
    *******************************************************************************/
   protected static void denied(QRecord record, String message)
   {
      record.addError(new PermissionDeniedMessage(message));
   }



   /*******************************************************************************
    ** True if the record sets the field to a value different from the old
    ** record's (absent fields are unchanged).
    *******************************************************************************/
   protected static boolean changes(QRecord record, QRecord oldRecord, String field)
   {
      if(!record.getValues().containsKey(field))
      {
         return (false);
      }
      Serializable oldValue = oldRecord == null ? null : oldRecord.getValue(field);
      return (!Objects.equals(normalize(record.getValue(field)), normalize(oldValue)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String normalize(Serializable value)
   {
      return (value == null ? null : value.toString());
   }



   /*******************************************************************************
    ** The distinct notebook ids of the error-free records.
    *******************************************************************************/
   protected static Set<Integer> notebookIds(List<QRecord> records)
   {
      Set<Integer> ids = new LinkedHashSet<>();
      for(QRecord record : CollectionUtils.nonNullList(records))
      {
         if(ok(record) && record.getValueInteger("notebookId") != null)
         {
            ids.add(record.getValueInteger("notebookId"));
         }
      }
      return (ids);
   }



   /*******************************************************************************
    ** Append one version per notebook.
    *******************************************************************************/
   protected static void appendVersions(Set<Integer> notebookIds, String comment, QBackendTransaction transaction) throws QException
   {
      for(Integer notebookId : notebookIds)
      {
         try
         {
            NotebookVersioning.appendVersion(config(), clock(), notebookId, comment, transaction);
         }
         catch(QException e)
         {
            ////////////////////////////////////////////////////////////////////
            // QQQ turns post-customizer exceptions into record warnings, so //
            // log here too: a change without its version must be visible    //
            ////////////////////////////////////////////////////////////////////
            LOG.warn("Could not append a notebook version", e, logPair("notebookId", notebookId), logPair("comment", comment));
            throw (e);
         }
      }
   }



   /*******************************************************************************
    ** True for the qbit's own (system user) writes.
    *******************************************************************************/
   protected static boolean isSystemUser()
   {
      return (SqlWorkbenchSecurity.isSystemUser());
   }
}
