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
import java.util.concurrent.atomic.AtomicLong;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.SystemUser;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchExecution;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Writes the execution audit: a RUNNING row before the SQL runs (if that
 ** fails, the SQL does not run) and the outcome after. Writes go through QQQ
 ** actions in a system-session context, because the audit table's insert
 ** and edit capabilities are disabled for users.
 *******************************************************************************/
public class ExecutionAuditor
{
   private static final QLogger LOG = QLogger.getLogger(ExecutionAuditor.class);

   public static final int MAX_ERROR_LENGTH = 4000;

   public static final long PRUNE_INTERVAL_MILLIS = 60L * 60 * 1000;

   private final SqlWorkbenchQBitConfig config;
   private final AtomicLong             lastPruneMillis = new AtomicLong(0);



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

      throwIfNotInserted(inserted[0], "Could not write the SQL Workbench audit record; the SQL was not run.");
      execution.setId(inserted[0].getValueInteger("id"));
      pruneIfDue();
      return (execution.getId());
   }



   /*******************************************************************************
    ** Apply auditRetentionDays, at most once an hour per auditor. Never fails
    ** the run being audited: a failed prune is logged and tried again later.
    *******************************************************************************/
   void pruneIfDue()
   {
      if(config.getAuditRetentionDays() == null)
      {
         return;
      }

      long now  = System.currentTimeMillis();
      long last = lastPruneMillis.get();
      if(now - last < PRUNE_INTERVAL_MILLIS || !lastPruneMillis.compareAndSet(last, now))
      {
         return;
      }

      try
      {
         pruneOlderThan(Instant.ofEpochMilli(now).minus(Duration.ofDays(config.getAuditRetentionDays())));
      }
      catch(Exception e)
      {
         LOG.warn("Could not prune old SQL Workbench audit rows", e);
      }
   }



   /*******************************************************************************
    ** Delete audit rows that started before a cutoff (system user; the table is
    ** otherwise append-only). Rows still RUNNING are kept.
    **
    ** @return the number of rows deleted
    *******************************************************************************/
   public int pruneOlderThan(Instant cutoff) throws QException
   {
      int[] deleted = new int[1];
      asSystem(() ->
      {
         QQueryFilter filter = new QQueryFilter(new QFilterCriteria("startTime", QCriteriaOperator.LESS_THAN, cutoff))
            .withCriteria(new QFilterCriteria("status", QCriteriaOperator.NOT_EQUALS, ExecutionStatus.RUNNING.name()));
         deleted[0] = new DeleteAction().execute(new DeleteInput(tableName()).withQueryFilter(filter)).getDeletedRecordCount();
      });
      return (deleted[0]);
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
      QRecord[] inserted = new QRecord[1];
      asSystem(() -> inserted[0] = new InsertAction().execute(new InsertInput(tableName()).withRecordEntity(execution)).getRecords().get(0));
      throwIfNotInserted(inserted[0], "Could not write the SQL Workbench audit record of a rejected request.");
   }



   /*******************************************************************************
    ** Whether the audit already holds a row with this execution id.
    *******************************************************************************/
   public boolean exists(String executionUuid) throws QException
   {
      return (SystemUser.call(() -> !QueryAction.execute(tableName(), new QQueryFilter(new QFilterCriteria("executionUuid", QCriteriaOperator.EQUALS, executionUuid)).withLimit(1)).isEmpty()));
   }



   /*******************************************************************************
    ** Fail loudly when an audit insert did not produce a row (record errors,
    ** e.g. a duplicate executionUuid, or no generated id).
    *******************************************************************************/
   private static void throwIfNotInserted(QRecord record, String message) throws QException
   {
      if(record == null || (record.getErrors() != null && !record.getErrors().isEmpty()) || record.getValueInteger("id") == null)
      {
         String detail = record == null || record.getErrors() == null || record.getErrors().isEmpty() ? "no id returned" : record.getErrors().get(0).getMessage();
         LOG.warn(message, logPair("detail", detail));
         throw (new QException(message));
      }
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
   static void asSystem(SystemUser.Block block) throws QException
   {
      SystemUser.run(block);
   }

}
