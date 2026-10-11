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

package com.kingsrook.qbits.sqlworkbench.engine;


import java.time.Duration;
import java.time.Instant;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.CapturedContext;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchExecution;


/*******************************************************************************
 ** Writes the execution audit: a RUNNING row before the SQL runs (if that
 ** fails, the SQL does not run) and the outcome after. Writes go through QQQ
 ** actions in a system-session context, because the audit table's insert
 ** and edit capabilities are disabled for users.
 *******************************************************************************/
public class ExecutionAuditor
{
   public static final int MAX_ERROR_LENGTH = 4000;

   private final SqlWorkbenchQBitConfig config;



   /*******************************************************************************
    **
    *******************************************************************************/
   public ExecutionAuditor(SqlWorkbenchQBitConfig config)
   {
      this.config = config;
   }



   /*******************************************************************************
    ** The audit table's (possibly prefixed) name.
    *******************************************************************************/
   public String tableName()
   {
      return (config.applyPrefix(SqlWorkbenchExecution.TABLE_NAME));
   }



   /*******************************************************************************
    ** Insert the RUNNING row.
    **
    ** @return the row's id
    *******************************************************************************/
   public Integer start(SqlWorkbenchExecution execution) throws QException
   {
      execution.setStatus(ExecutionStatus.RUNNING.name());
      execution.setCreateDate(execution.getStartTime());
      QRecord[] inserted = new QRecord[1];
      asSystem(() ->
      {
         InsertOutput output = new InsertAction().execute(new InsertInput(tableName()).withRecordEntity(execution));
         inserted[0] = output.getRecords().get(0);
      });

      if(inserted[0] == null || (inserted[0].getErrors() != null && !inserted[0].getErrors().isEmpty()) || inserted[0].getValueInteger("id") == null)
      {
         throw (new QException("Could not write the SQL Workbench audit record; the SQL was not run."));
      }
      execution.setId(inserted[0].getValueInteger("id"));
      return (execution.getId());
   }



   /*******************************************************************************
    ** Record the outcome.
    *******************************************************************************/
   public void finish(SqlWorkbenchExecution execution, ExecutionStatus status, Instant endTime, Integer rowsReturned, Long rowsAffected, Boolean truncated, String errorMessage) throws QException
   {
      execution.setStatus(status.name());
      execution.setEndTime(endTime);
      execution.setDurationMs(execution.getStartTime() == null ? null : (int) Math.min(Integer.MAX_VALUE, Duration.between(execution.getStartTime(), endTime).toMillis()));
      execution.setRowsReturned(rowsReturned);
      execution.setRowsAffected(rowsAffected == null ? null : (int) Math.min(Integer.MAX_VALUE, rowsAffected));
      execution.setTruncated(truncated);
      execution.setErrorMessage(truncate(errorMessage));

      QRecord record = new QRecord()
         .withValue("id", execution.getId())
         .withValue("status", execution.getStatus())
         .withValue("endTime", execution.getEndTime())
         .withValue("durationMs", execution.getDurationMs())
         .withValue("rowsReturned", execution.getRowsReturned())
         .withValue("rowsAffected", execution.getRowsAffected())
         .withValue("truncated", execution.getTruncated())
         .withValue("errorMessage", execution.getErrorMessage());

      asSystem(() -> new UpdateAction().execute(new UpdateInput(tableName()).withRecords(List.of(record))));
   }



   /*******************************************************************************
    ** Insert a complete row for a run that never started (e.g. rejected by
    ** validation or permissions).
    *******************************************************************************/
   public void record(SqlWorkbenchExecution execution) throws QException
   {
      execution.setCreateDate(execution.getStartTime());
      execution.setErrorMessage(truncate(execution.getErrorMessage()));
      asSystem(() -> new InsertAction().execute(new InsertInput(tableName()).withRecordEntity(execution)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static String truncate(String message)
   {
      if(message == null || message.length() <= MAX_ERROR_LENGTH)
      {
         return (message);
      }
      return (message.substring(0, MAX_ERROR_LENGTH - 1) + "…");
   }



   /*******************************************************************************
    ** Run as the system user, in the current instance.
    *******************************************************************************/
   static void asSystem(QContextRunnable runnable) throws QException
   {
      QInstance qInstance = QContext.getQInstance();
      QException[] thrown = new QException[1];
      QContext.withTemporaryContext(new CapturedContext(qInstance, new QSystemUserSession()), () ->
      {
         try
         {
            runnable.run();
         }
         catch(QException e)
         {
            thrown[0] = e;
         }
      });
      if(thrown[0] != null)
      {
         throw (thrown[0]);
      }
   }



   /*******************************************************************************
    ** A block that may throw QException.
    *******************************************************************************/
   @FunctionalInterface
   public interface QContextRunnable
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      void run() throws QException;
   }

}
