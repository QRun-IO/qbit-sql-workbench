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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import com.fasterxml.jackson.core.type.TypeReference;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchSecurity;
import com.kingsrook.qbits.sqlworkbench.SystemUser;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchCell;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookShare;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookVersion;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchResultSnapshot;
import com.kingsrook.qbits.sqlworkbench.notebook.NotebookService;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Notebook invariants, the same for the notebook UI and the table UI / API:
 ** - the owner is the creating session's user (only the qbit itself, as the
 **   system user, may insert for someone else, e.g. a migration);
 ** - visibility is derived from the shares (an ALL share = the library);
 ** - currentVersion / createDate are system-managed; a title is required;
 **   parameters are a JSON list;
 ** - every content change appends a version;
 ** - deleting a notebook deletes its cells, versions, pinned results and
 **   shares (only after QQQ has authorized the delete).
 *******************************************************************************/
public class NotebookTableCustomizer extends WorkbenchTableCustomizer
{
   private static final List<String> CONTENT_FIELDS = List.of("title", "description", "defaultBackendName", "parametersJson");



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> preInsert(InsertInput insertInput, List<QRecord> records, boolean isPreview) throws QException
   {
      boolean system   = isSystemUser();
      String  userId   = SqlWorkbenchSecurity.userId(QContext.getQSession());
      String  userName = SqlWorkbenchSecurity.userName(QContext.getQSession());
      Instant now      = clock().instant();

      for(QRecord record : records)
      {
         if(!system)
         {
            if(!StringUtils.hasContent(userId))
            {
               denied(record, "The session has no user.");
               continue;
            }
            record.setValue("ownerUserId", userId);
            record.setValue("ownerName", userName);
            record.setValue("visibility", NotebookService.VISIBILITY_PERSONAL);
            record.setValue("currentVersion", 0);
         }
         else
         {
            if(!StringUtils.hasContent(record.getValueString("ownerUserId")))
            {
               badInput(record, "A notebook needs an owner.");
            }
            if(!StringUtils.hasContent(record.getValueString("visibility")))
            {
               record.setValue("visibility", NotebookService.VISIBILITY_PERSONAL);
            }
            if(record.getValue("currentVersion") == null)
            {
               record.setValue("currentVersion", 0);
            }
         }
         validateContent(record, true);
         record.setValue("createDate", now);
         record.setValue("modifyDate", now);
      }
      return (records);
   }



   /*******************************************************************************
    ** A new notebook made through the table gets version 1 (the notebook UI
    ** writes its own, with the cells).
    *******************************************************************************/
   @Override
   public List<QRecord> postInsert(InsertInput insertInput, List<QRecord> records) throws QException
   {
      if(!insertInput.hasFlag(SqlWorkbenchActionFlag.NOTEBOOK_SAVE) && !insertInput.hasFlag(SqlWorkbenchActionFlag.INTERNAL))
      {
         Set<Integer> ids = new LinkedHashSet<>();
         for(QRecord record : records)
         {
            if(ok(record) && record.getValueInteger("id") != null)
            {
               ids.add(record.getValueInteger("id"));
            }
         }
         appendVersions(ids, "Created", insertInput.getTransaction());
      }
      return (records);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> preUpdate(UpdateInput updateInput, List<QRecord> records, boolean isPreview, Optional<List<QRecord>> oldRecordList) throws QException
   {
      if(isSystemUser() || updateInput.hasFlag(SqlWorkbenchActionFlag.INTERNAL))
      {
         return (records);
      }

      Instant now = clock().instant();
      for(QRecord record : records)
      {
         if(!ok(record))
         {
            continue;
         }

         QRecord old = oldRecord(oldRecordList, record);
         if(changes(record, old, "visibility"))
         {
            badInput(record, "Visibility follows the notebook's shares: share it with Everyone to publish it to the shared library.");
            continue;
         }
         if(!changes(record, old, "ownerUserId"))
         {
            record.removeValue("ownerName");
         }
         record.removeValue("currentVersion");
         record.removeValue("createDate");
         validateContent(record, false);
         record.setValue("modifyDate", now);
      }
      return (records);
   }



   /*******************************************************************************
    ** A content edit made through the table appends a version.
    *******************************************************************************/
   @Override
   public List<QRecord> postUpdate(UpdateInput updateInput, List<QRecord> records, Optional<List<QRecord>> oldRecordList) throws QException
   {
      if(updateInput.hasFlag(SqlWorkbenchActionFlag.NOTEBOOK_SAVE) || updateInput.hasFlag(SqlWorkbenchActionFlag.INTERNAL))
      {
         return (records);
      }

      Set<Integer> ids = new LinkedHashSet<>();
      for(QRecord record : records)
      {
         if(ok(record) && record.getValueInteger("id") != null && CONTENT_FIELDS.stream().anyMatch(field -> record.getValues().containsKey(field)))
         {
            ids.add(record.getValueInteger("id"));
         }
      }
      appendVersions(ids, "Edited", updateInput.getTransaction());
      return (records);
   }



   /*******************************************************************************
    ** Delete the children of the notebooks QQQ authorized for deletion (the
    ** foreign keys need them gone first).
    *******************************************************************************/
   @Override
   public List<QRecord> preDelete(DeleteInput deleteInput, List<QRecord> records, boolean isPreview) throws QException
   {
      if(isPreview || deleteInput.hasFlag(SqlWorkbenchActionFlag.INTERNAL))
      {
         return (records);
      }

      List<Serializable> ids = new ArrayList<>();
      for(QRecord record : records)
      {
         if(ok(record) && record.getValue("id") != null)
         {
            ids.add(record.getValue("id"));
         }
      }
      if(ids.isEmpty())
      {
         return (records);
      }

      SqlWorkbenchQBitConfig config = config();
      SystemUser.run(() ->
      {
         for(String child : List.of(SqlWorkbenchCell.TABLE_NAME, SqlWorkbenchNotebookVersion.TABLE_NAME, SqlWorkbenchResultSnapshot.TABLE_NAME, SqlWorkbenchNotebookShare.TABLE_NAME))
         {
            new DeleteAction().execute(new DeleteInput(config.applyPrefix(child))
               .withFlag(SqlWorkbenchActionFlag.INTERNAL)
               .withTransaction(deleteInput.getTransaction())
               .withQueryFilter(new QQueryFilter(new QFilterCriteria("notebookId", QCriteriaOperator.IN, ids))));
         }
      });
      return (records);
   }



   /*******************************************************************************
    ** Title (required on insert, never blank) and parameters (a JSON list).
    *******************************************************************************/
   private static void validateContent(QRecord record, boolean inserting)
   {
      if(inserting || record.getValues().containsKey("title"))
      {
         String title = record.getValueString("title");
         if(!StringUtils.hasContent(title))
         {
            badInput(record, "A notebook needs a title.");
         }
         else
         {
            record.setValue("title", title.trim());
         }
      }

      String parameters = record.getValueString("parametersJson");
      if(StringUtils.hasContent(parameters))
      {
         try
         {
            JsonUtils.toObject(parameters, new TypeReference<List<Object>>() {});
         }
         catch(Exception e)
         {
            badInput(record, "Parameters must be a JSON list.");
         }
      }
   }
}
