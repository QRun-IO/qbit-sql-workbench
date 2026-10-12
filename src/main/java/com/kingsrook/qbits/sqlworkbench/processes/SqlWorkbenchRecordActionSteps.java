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

package com.kingsrook.qbits.sqlworkbench.processes;


import java.util.Map;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchRuntime;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchSecurity;
import com.kingsrook.qbits.sqlworkbench.engine.Caller;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookShare;
import com.kingsrook.qbits.sqlworkbench.notebook.NotebookService;
import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QUserFacingException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertOutput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;


/*******************************************************************************
 ** The backend steps of the workbench's record actions. QQQ loads the
 ** selected records through the table's record locks (as the session), so a
 ** step only ever sees rows the user can read; the writes then go through
 ** the same table actions and customizers as everything else.
 *******************************************************************************/
public final class SqlWorkbenchRecordActionSteps
{
   public static final String RESULT_MESSAGE = "resultMessage";



   /*******************************************************************************
    **
    *******************************************************************************/
   private SqlWorkbenchRecordActionSteps()
   {
   }



   /*******************************************************************************
    ** The single selected record (min/max input records are 1).
    *******************************************************************************/
   static QRecord selected(RunBackendStepInput input) throws QException
   {
      if(CollectionUtils.nullSafeIsEmpty(input.getRecords()))
      {
         throw (new QUserFacingException("The selected record was not found."));
      }
      return (input.getRecords().get(0));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static NotebookService notebooks() throws QException
   {
      return (new NotebookService(SqlWorkbenchRuntime.get()));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static Caller caller() throws QException
   {
      SqlWorkbenchQBitConfig config = SqlWorkbenchRuntime.get().getConfig();
      SqlWorkbenchSecurity.ensureSessionKeys(config, QContext.getQSession());
      return (Caller.fromSession(config, QContext.getQSession(), null));
   }



   /*******************************************************************************
    ** Share the selected notebook with a user, a group, or everyone (the
    ** shared library): inserts a share row as the session.
    *******************************************************************************/
   public static class ShareNotebookStep implements BackendStep
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         QRecord                notebook = selected(input);
         SqlWorkbenchQBitConfig config   = SqlWorkbenchRuntime.get().getConfig();
         InsertOutput inserted = new InsertAction().execute(new InsertInput(config.applyPrefix(SqlWorkbenchNotebookShare.TABLE_NAME)).withRecordEntity(new SqlWorkbenchNotebookShare()
            .withNotebookId(notebook.getValueInteger("id"))
            .withAudienceType(input.getValueString("audienceType"))
            .withAudienceId(input.getValueString("audienceId"))));
         QRecord share = inserted.getRecords().get(0);
         if(CollectionUtils.nullSafeHasContents(share.getErrors()))
         {
            throw (new QUserFacingException(share.getErrors().get(0).getMessage()));
         }
         output.addValue(RESULT_MESSAGE, "Shared \"" + notebook.getValueString("title") + "\" (" + share.getValueString("audienceKey") + ").");
      }
   }



   /*******************************************************************************
    ** Copy the selected (readable) notebook into a new notebook the session
    ** owns.
    *******************************************************************************/
   public static class DuplicateNotebookStep implements BackendStep
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         QRecord             notebook = selected(input);
         Map<String, Object> copy     = notebooks().duplicate(notebook.getValueInteger("id"), caller());
         output.addValue(RESULT_MESSAGE, "Created \"" + copy.get("title") + "\" (notebook " + copy.get("id") + ").");
      }
   }



   /*******************************************************************************
    ** Restore the selected version of a notebook the session owns (saved as
    ** a new version).
    *******************************************************************************/
   public static class RestoreVersionStep implements BackendStep
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         QRecord             version  = selected(input);
         Map<String, Object> restored = notebooks().restore(version.getValueInteger("notebookId"), version.getValueInteger("versionNumber"), caller());
         output.addValue(RESULT_MESSAGE, "Restored version " + version.getValueInteger("versionNumber") + " as version " + restored.get("currentVersion") + ".");
      }
   }
}
