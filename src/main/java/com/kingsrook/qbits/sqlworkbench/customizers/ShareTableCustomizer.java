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


import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchSecurity;
import com.kingsrook.qbits.sqlworkbench.SystemUser;
import com.kingsrook.qbits.sqlworkbench.model.ShareAudienceType;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebook;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookShare;
import com.kingsrook.qbits.sqlworkbench.notebook.NotebookService;
import com.kingsrook.qqq.backend.core.actions.QBackendTransaction;
import com.kingsrook.qqq.backend.core.actions.tables.CountAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.count.CountInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Share invariants, the same for the notebook UI ("publish") and the table
 ** UI / API:
 ** - changing shares needs the share permission (the record lock then
 **   checks the notebook is the caller's own, or the caller is an admin);
 ** - USER and GROUP shares name their audience, ALL (the library) does not;
 **   audienceKey is computed here and is what the read locks match;
 ** - a share never moves to another notebook;
 ** - the notebook's visibility follows its shares (SHARED while it has an
 **   ALL share).
 *******************************************************************************/
public class ShareTableCustomizer extends WorkbenchTableCustomizer
{
   private static final String NEEDS_PERMISSION = "Sharing notebooks needs the share permission.";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> preInsert(InsertInput insertInput, List<QRecord> records, boolean isPreview) throws QException
   {
      boolean  allowed = mayShare();
      QSession session = QContext.getQSession();
      Instant  now     = clock().instant();
      for(QRecord record : records)
      {
         if(!allowed)
         {
            denied(record, NEEDS_PERMISSION);
            continue;
         }
         if(record.getValueInteger("notebookId") == null)
         {
            badInput(record, "A share needs a notebook.");
            continue;
         }
         applyAudience(record, record.getValueString("audienceType"), record.getValueString("audienceId"));
         if(!isSystemUser() || !StringUtils.hasContent(record.getValueString("createdByUserId")))
         {
            record.setValue("createdByUserId", SqlWorkbenchSecurity.userId(session));
            record.setValue("createdByName", SqlWorkbenchSecurity.userName(session));
         }
         record.setValue("createDate", now);
         record.setValue("modifyDate", now);
      }
      return (records);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> postInsert(InsertInput insertInput, List<QRecord> records) throws QException
   {
      syncVisibility(insertInput.hasFlag(SqlWorkbenchActionFlag.INTERNAL), notebookIds(records), insertInput.getTransaction());
      return (records);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> preUpdate(UpdateInput updateInput, List<QRecord> records, boolean isPreview, Optional<List<QRecord>> oldRecordList) throws QException
   {
      boolean allowed = mayShare();
      Instant now     = clock().instant();
      for(QRecord record : records)
      {
         if(!ok(record))
         {
            continue;
         }
         if(!allowed)
         {
            denied(record, NEEDS_PERMISSION);
            continue;
         }
         QRecord old = oldRecord(oldRecordList, record);
         if(changes(record, old, "notebookId"))
         {
            badInput(record, "A share cannot move to another notebook.");
            continue;
         }
         record.removeValue("createdByUserId");
         record.removeValue("createdByName");
         record.removeValue("createDate");
         if(record.getValues().containsKey("audienceType") || record.getValues().containsKey("audienceId"))
         {
            String type = record.getValues().containsKey("audienceType") ? record.getValueString("audienceType") : old == null ? null : old.getValueString("audienceType");
            String id   = record.getValues().containsKey("audienceId") ? record.getValueString("audienceId") : old == null ? null : old.getValueString("audienceId");
            applyAudience(record, type, id);
         }
         else
         {
            record.removeValue("audienceKey");
         }
         record.setValue("modifyDate", now);
      }
      return (records);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> postUpdate(UpdateInput updateInput, List<QRecord> records, Optional<List<QRecord>> oldRecordList) throws QException
   {
      Set<Integer> ids = new LinkedHashSet<>();
      for(QRecord record : records)
      {
         QRecord old = oldRecord(oldRecordList, record);
         if(ok(record) && old != null && old.getValueInteger("notebookId") != null)
         {
            ids.add(old.getValueInteger("notebookId"));
         }
      }
      syncVisibility(updateInput.hasFlag(SqlWorkbenchActionFlag.INTERNAL), ids, updateInput.getTransaction());
      return (records);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> preDelete(DeleteInput deleteInput, List<QRecord> records, boolean isPreview) throws QException
   {
      if(!mayShare())
      {
         for(QRecord record : records)
         {
            if(ok(record))
            {
               denied(record, NEEDS_PERMISSION);
            }
         }
      }
      return (records);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> postDelete(DeleteInput deleteInput, List<QRecord> records) throws QException
   {
      syncVisibility(deleteInput.hasFlag(SqlWorkbenchActionFlag.INTERNAL), notebookIds(records), deleteInput.getTransaction());
      return (records);
   }



   /*******************************************************************************
    ** The system user, or a session holding the share permission.
    *******************************************************************************/
   private static boolean mayShare() throws QException
   {
      QSession session = QContext.getQSession();
      return (isSystemUser() || (session != null && session.hasPermission(config().getSharePermissionName())));
   }



   /*******************************************************************************
    ** Validate and normalize the audience; compute the audience key.
    *******************************************************************************/
   private static void applyAudience(QRecord record, String typeValue, String audienceId)
   {
      ShareAudienceType type = ShareAudienceType.of(typeValue);
      if(type == null)
      {
         badInput(record, "audienceType must be USER, GROUP or ALL.");
         return;
      }

      record.setValue("audienceType", type.name());
      if(type == ShareAudienceType.ALL)
      {
         record.setValue("audienceId", null);
      }
      else if(!StringUtils.hasContent(audienceId))
      {
         badInput(record, type == ShareAudienceType.USER ? "Name the user to share with." : "Name the group to share with.");
         return;
      }
      else
      {
         record.setValue("audienceId", audienceId.trim());
      }
      record.setValue("audienceKey", type.audienceKey(audienceId));
   }



   /*******************************************************************************
    ** Set each notebook's visibility from its shares: SHARED while it has an
    ** ALL share, else PERSONAL (system user; the share change was already
    ** authorized).
    *******************************************************************************/
   private static void syncVisibility(boolean internal, Set<Integer> notebookIds, QBackendTransaction transaction) throws QException
   {
      if(internal || notebookIds.isEmpty())
      {
         return;
      }

      SqlWorkbenchQBitConfig config = config();
      Instant                now    = clock().instant();
      SystemUser.run(() ->
      {
         for(Integer notebookId : notebookIds)
         {
            Integer allShares = new CountAction().execute(new CountInput(config.applyPrefix(SqlWorkbenchNotebookShare.TABLE_NAME))
               .withTransaction(transaction)
               .withFilter(new QQueryFilter(new QFilterCriteria("notebookId", QCriteriaOperator.EQUALS, notebookId))
                  .withCriteria(new QFilterCriteria("audienceKey", QCriteriaOperator.EQUALS, SqlWorkbenchSecurity.AUDIENCE_ALL)))).getCount();
            String visibility = allShares != null && allShares > 0 ? NotebookService.VISIBILITY_SHARED : NotebookService.VISIBILITY_PERSONAL;
            new UpdateAction().execute(new UpdateInput(config.applyPrefix(SqlWorkbenchNotebook.TABLE_NAME))
               .withFlag(SqlWorkbenchActionFlag.INTERNAL)
               .withTransaction(transaction)
               .withRecord(new QRecord().withValue("id", notebookId).withValue("visibility", visibility).withValue("modifyDate", now)));
         }
      });
   }
}
