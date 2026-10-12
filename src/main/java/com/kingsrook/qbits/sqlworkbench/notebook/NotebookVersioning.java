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

package com.kingsrook.qbits.sqlworkbench.notebook;


import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchSecurity;
import com.kingsrook.qbits.sqlworkbench.SystemUser;
import com.kingsrook.qbits.sqlworkbench.customizers.SqlWorkbenchActionFlag;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchCell;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebook;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookVersion;
import com.kingsrook.qqq.backend.core.actions.QBackendTransaction;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;


/*******************************************************************************
 ** Notebook versions: every change to a notebook's content (from the
 ** notebook UI or from the table UI / API) appends one immutable version
 ** holding the whole notebook, and bumps the notebook's currentVersion.
 **
 ** The caller's change has already passed the record locks (only the owner,
 ** or an admin, gets here); the bookkeeping itself is written as the system
 ** user, because versions are append-only for everyone else.
 *******************************************************************************/
public final class NotebookVersioning
{

   /*******************************************************************************
    **
    *******************************************************************************/
   private NotebookVersioning()
   {
   }



   /*******************************************************************************
    ** Append a version of the notebook as stored now (in the transaction, if
    ** any), saved by the current session's user. Returns the new version
    ** number, or null if the notebook does not exist.
    *******************************************************************************/
   public static Integer appendVersion(SqlWorkbenchQBitConfig config, Clock clock, Integer notebookId, String comment, QBackendTransaction transaction) throws QException
   {
      QSession session  = QContext.getQSession();
      String   userId   = SqlWorkbenchSecurity.userId(session);
      String   userName = SqlWorkbenchSecurity.userName(session);

      return (SystemUser.call(() ->
      {
         List<QRecord> found = new QueryAction().execute(new QueryInput(config.applyPrefix(SqlWorkbenchNotebook.TABLE_NAME))
            .withFilter(new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.EQUALS, notebookId)))
            .withTransaction(transaction)).getRecords();
         if(found.isEmpty())
         {
            return (null);
         }

         SqlWorkbenchNotebook notebook = new SqlWorkbenchNotebook(found.get(0));
         Instant              now      = clock.instant();
         int                  version  = (notebook.getCurrentVersion() == null ? 0 : notebook.getCurrentVersion()) + 1;

         new UpdateAction().execute(new UpdateInput(config.applyPrefix(SqlWorkbenchNotebook.TABLE_NAME))
            .withFlag(SqlWorkbenchActionFlag.INTERNAL)
            .withTransaction(transaction)
            .withRecord(new QRecord().withValue("id", notebookId).withValue("currentVersion", version).withValue("modifyDate", now)));

         SqlWorkbenchNotebookVersion versionRow = new SqlWorkbenchNotebookVersion()
            .withNotebookId(notebookId)
            .withVersionNumber(version)
            .withContentJson(JsonUtils.toJson(content(notebook, cells(config, notebookId, transaction))))
            .withSavedByUserId(userId)
            .withSavedByName(userName)
            .withComment(NotebookService.truncate(comment, 255))
            .withCreateDate(now);
         InsertOutput output = new InsertAction().execute(new InsertInput(config.applyPrefix(SqlWorkbenchNotebookVersion.TABLE_NAME))
            .withFlag(SqlWorkbenchActionFlag.INTERNAL)
            .withTransaction(transaction)
            .withRecordEntity(versionRow));
         NotebookService.throwIfErrors(output.getRecords().get(0));

         prune(config, notebookId, transaction);
         return (version);
      }));
   }



   /*******************************************************************************
    ** A notebook's cells, in order (as the current session).
    *******************************************************************************/
   static List<SqlWorkbenchCell> cells(SqlWorkbenchQBitConfig config, Integer notebookId, QBackendTransaction transaction) throws QException
   {
      QQueryFilter filter = new QQueryFilter(new QFilterCriteria("notebookId", QCriteriaOperator.EQUALS, notebookId))
         .withOrderBy(new QFilterOrderBy("sortOrder"))
         .withOrderBy(new QFilterOrderBy("id"));
      List<SqlWorkbenchCell> cells = new ArrayList<>();
      for(QRecord record : new QueryAction().execute(new QueryInput(config.applyPrefix(SqlWorkbenchCell.TABLE_NAME)).withFilter(filter).withTransaction(transaction)).getRecords())
      {
         cells.add(new SqlWorkbenchCell(record));
      }
      return (cells);
   }



   /*******************************************************************************
    ** The content a version holds.
    *******************************************************************************/
   static Map<String, Object> content(SqlWorkbenchNotebook notebook, List<SqlWorkbenchCell> cells)
   {
      Map<String, Object> content = new LinkedHashMap<>();
      content.put("title", notebook.getTitle());
      content.put("description", notebook.getDescription());
      content.put("defaultBackendName", notebook.getDefaultBackendName());
      content.put("parameters", NotebookService.parseParameters(notebook.getParametersJson()));
      content.put("cells", cells.stream().map(NotebookService::cellView).toList());
      return (content);
   }



   /*******************************************************************************
    ** Delete versions beyond the retention, oldest first (system user).
    *******************************************************************************/
   private static void prune(SqlWorkbenchQBitConfig config, Integer notebookId, QBackendTransaction transaction) throws QException
   {
      QueryInput input = new QueryInput(config.applyPrefix(SqlWorkbenchNotebookVersion.TABLE_NAME))
         .withFilter(new QQueryFilter(new QFilterCriteria("notebookId", QCriteriaOperator.EQUALS, notebookId)).withOrderBy(new QFilterOrderBy("versionNumber", false)))
         .withTransaction(transaction);
      List<QRecord> records = new QueryAction().execute(input).getRecords();
      if(records.size() <= config.getNotebookVersionRetention())
      {
         return;
      }

      List<Serializable> stale = new ArrayList<>();
      for(QRecord record : records.subList(config.getNotebookVersionRetention(), records.size()))
      {
         stale.add(record.getValueInteger("id"));
      }
      new DeleteAction().execute(new DeleteInput(config.applyPrefix(SqlWorkbenchNotebookVersion.TABLE_NAME))
         .withFlag(SqlWorkbenchActionFlag.INTERNAL)
         .withTransaction(transaction)
         .withPrimaryKeys(stale));
   }
}
