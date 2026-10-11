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


import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchRuntime;
import com.kingsrook.qbits.sqlworkbench.explain.PlanNode;
import com.kingsrook.qbits.sqlworkbench.explain.PlanParser;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchExecution;
import com.kingsrook.qbits.sqlworkbench.sql.NamedParameterSql;
import com.kingsrook.qbits.sqlworkbench.sql.SqlClassifier;
import com.kingsrook.qbits.sqlworkbench.sql.SqlDialect;
import com.kingsrook.qbits.sqlworkbench.sql.SqlStatement;
import com.kingsrook.qbits.sqlworkbench.sql.StatementKind;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Runs workbench SQL: read-only sessions, the write-mode preview / commit
 ** protocol, and EXPLAIN. Every run is validated, audited (RUNNING row
 ** first; no audit, no run), guarded (SessionGuard), bounded (timeouts, row
 ** and byte caps) and cancellable.
 **
 ** SQL errors are results, not exceptions: the response has status FAILED /
 ** TIMED_OUT / CANCELLED and the database's message. Exceptions are for the
 ** caller's mistakes (QBadRequestException), permissions
 ** (QPermissionDeniedException) and unknown targets (QNotFoundException).
 **
 ** Logs never carry SQL text, parameters or results.
 *******************************************************************************/
public class SqlExecutionEngine
{
   private static final QLogger LOG = QLogger.getLogger(SqlExecutionEngine.class);

   private static final java.util.regex.Pattern UUID_PATTERN = java.util.regex.Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

   private final SqlWorkbenchRuntime    runtime;
   private final SqlWorkbenchQBitConfig config;
   private final WorkbenchBackends      backends;
   private final ExecutionAuditor       auditor;



   /*******************************************************************************
    **
    *******************************************************************************/
   public SqlExecutionEngine(QInstance qInstance, SqlWorkbenchRuntime runtime)
   {
      this.runtime = runtime;
      this.config = runtime.getConfig();
      this.backends = new WorkbenchBackends(qInstance, runtime);
      this.auditor = new ExecutionAuditor(config);
   }



   /*******************************************************************************
    ** The target resolver this engine uses.
    *******************************************************************************/
   public WorkbenchBackends getBackends()
   {
      return (backends);
   }



   /*******************************************************************************
    ** Run SQL: read-only, write preview, or write commit.
    *******************************************************************************/
   public Map<String, Object> execute(ExecutionRequest request, Caller caller) throws QException
   {
      if(request.executionUuid() != null && !UUID_PATTERN.matcher(request.executionUuid()).matches())
      {
         throw (new QBadRequestException("executionUuid must be a UUID."));
      }

      WorkbenchBackends.Target target     = backends.getTarget(request.backendName());
      Map<String, Object>      params     = request.params() == null ? Map.of() : request.params();
      SqlWorkbenchExecution    audit      = newAudit(request, caller, params, request.write() ? ExecutionMode.WRITE_PREVIEW : ExecutionMode.READ_ONLY);
      List<SqlStatement>       statements = validate(request.sql(), target.dialect(), params, audit);
      audit.setStatementCount(statements.size());
      audit.setStatementKinds(String.join(",", statements.stream().map(s -> s.kind().name()).distinct().toList()));

      if(!request.write())
      {
         if(statements.size() != 1 || statements.get(0).kind() != StatementKind.READ)
         {
            reject(audit, "Read-only sessions run exactly one read-only statement (SELECT, WITH, VALUES, SHOW, EXPLAIN). "
               + describeNonReads(statements) + "Use write mode for anything else.");
         }
         return (runReadOnly(target, statements.get(0), params, request, caller, audit));
      }

      if(!caller.canWrite() || !Boolean.TRUE.equals(config.getWriteModeEnabled()))
      {
         rejectPermission(audit, "Write mode needs the " + config.getWritePermissionName() + " permission.");
      }
      if(statements.size() > config.getMaxStatementsPerRun())
      {
         reject(audit, "Too many statements (" + statements.size() + "); the limit is " + config.getMaxStatementsPerRun() + ".");
      }

      if(!StringUtils.hasContent(request.confirmToken()))
      {
         return (runWritePreview(target, statements, params, caller, audit));
      }
      audit.setMode(ExecutionMode.WRITE_COMMIT.name());
      return (runWriteCommit(target, statements, params, request, caller, audit));
   }



   /*******************************************************************************
    ** EXPLAIN (or EXPLAIN ANALYZE) one statement.
    *******************************************************************************/
   public Map<String, Object> explain(String backendName, String sql, Map<String, Object> rawParams, boolean analyze, Caller caller) throws QException
   {
      WorkbenchBackends.Target target = backends.getTarget(backendName);
      Map<String, Object>      params = rawParams == null ? Map.of() : rawParams;
      ExecutionRequest         request = new ExecutionRequest(backendName, sql, params, false, null, null, null, null, false);
      SqlWorkbenchExecution    audit  = newAudit(request, caller, params, analyze ? ExecutionMode.EXPLAIN_ANALYZE : ExecutionMode.EXPLAIN);

      List<SqlStatement> statements = validate(sql, target.dialect(), params, audit);
      audit.setStatementCount(statements.size());
      SqlStatement statement = statements.get(0);
      audit.setStatementKinds(statement.kind().name());

      if(statements.size() != 1)
      {
         reject(audit, "EXPLAIN takes exactly one statement.");
      }
      if("EXPLAIN".equals(statement.leadingKeyword()))
      {
         reject(audit, "Give the statement to explain, without EXPLAIN.");
      }
      if(analyze && (!caller.canWrite() || !Boolean.TRUE.equals(config.getWriteModeEnabled())))
      {
         rejectPermission(audit, "EXPLAIN ANALYZE executes the statement, so it needs the " + config.getWritePermissionName() + " permission.");
      }
      if(statement.kind() != StatementKind.READ && !(analyze && statement.kind() == StatementKind.WRITE))
      {
         reject(audit, "Only read-only statements can be explained" + (analyze ? " (or DML with EXPLAIN ANALYZE)" : "") + ".");
      }
      if(analyze && !PlanParser.supportsAnalyze(target.dialect()))
      {
         reject(audit, "EXPLAIN ANALYZE is not supported on " + target.dialect().getUiName() + ".");
      }

      boolean          readOnly = statement.kind() == StatementKind.READ;
      int              timeout  = readOnly ? config.getStatementTimeoutSeconds() : config.getWriteStatementTimeoutSeconds();
      NamedParameterSql named   = NamedParameterSql.of(statement);
      String           explain  = PlanParser.explainSql(target.dialect(), named.jdbcSql(), analyze);

      return (withRegistration(audit, caller, (registration, deferred) ->
      {
         Map<String, Object> response = baseResponse(audit);
         try(SessionGuard guard = new SessionGuard(backends.openConnection(target, readOnly), target.dialect(), readOnly, timeout).begin())
         {
            CollectedResult result = runOne(guard, explain, named.parameterOrder(), params, target.dialect(), registration).result();
            guard.rollback();

            List<List<Object>> rows = result == null ? List.of() : result.rows();
            PlanNode           plan = PlanParser.parse(target.dialect(), rows, analyze);
            deferred.set(() -> finish(audit, ExecutionStatus.SUCCEEDED, rows.size(), null, false, null));
            response.put("status", ExecutionStatus.SUCCEEDED.name());
            response.put("dialect", target.dialect().getUiName());
            response.put("analyze", analyze);
            response.put("plan", plan.toMap());
            response.put("raw", PlanParser.raw(rows));
         }
         catch(SQLException e)
         {
            sqlFailure(audit, response, e, registration);
         }
         return (response);
      }));
   }



   /*******************************************************************************
    ** Cancel a running execution of this caller.
    *******************************************************************************/
   public boolean cancel(String executionUuid, Caller caller)
   {
      return (runtime.getRunningStatements().cancel(executionUuid, caller.userId()));
   }



   /*******************************************************************************
    ** Validate length, split and classify, refuse FORBIDDEN, check params.
    *******************************************************************************/
   private List<SqlStatement> validate(String sql, SqlDialect dialect, Map<String, Object> params, SqlWorkbenchExecution audit) throws QException
   {
      if(!StringUtils.hasContent(sql))
      {
         throw (new QBadRequestException("There is no SQL to run."));
      }
      if(sql.length() > config.getMaxSqlLength())
      {
         reject(audit, "The SQL is longer than the limit of " + config.getMaxSqlLength() + " characters.");
      }

      List<SqlStatement> statements = SqlClassifier.split(sql, dialect);
      if(statements.isEmpty())
      {
         throw (new QBadRequestException("There is no SQL to run (only comments)."));
      }

      for(SqlStatement statement : statements)
      {
         if(statement.kind() == StatementKind.FORBIDDEN)
         {
            reject(audit, "Not allowed: " + statement.reason() + ".");
         }
      }

      Set<String> missing = new LinkedHashSet<>();
      for(SqlStatement statement : statements)
      {
         for(String name : statement.parameterNames())
         {
            if(!params.containsKey(name))
            {
               missing.add(name);
            }
         }
      }
      if(!missing.isEmpty())
      {
         reject(audit, "Missing value for parameter(s): " + String.join(", ", missing.stream().map(n -> ":" + n).toList()) + ".");
      }
      return (statements);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String describeNonReads(List<SqlStatement> statements)
   {
      if(statements.size() > 1)
      {
         return ("This has " + statements.size() + " statements. ");
      }
      SqlStatement statement = statements.get(0);
      return ("This statement is " + statement.kind().name().toLowerCase() + " (" + statement.reason() + "). ");
   }



   /*******************************************************************************
    ** One read-only statement.
    *******************************************************************************/
   private Map<String, Object> runReadOnly(WorkbenchBackends.Target target, SqlStatement statement, Map<String, Object> params, ExecutionRequest request, Caller caller, SqlWorkbenchExecution audit) throws QException
   {
      NamedParameterSql named = NamedParameterSql.of(statement);
      return (withRegistration(audit, caller, (registration, deferred) ->
      {
         Map<String, Object> response = baseResponse(audit);
         try(SessionGuard guard = new SessionGuard(backends.openConnection(target, true), target.dialect(), true, config.getStatementTimeoutSeconds()).begin())
         {
            StatementOutcome outcome = runOne(guard, named.jdbcSql(), named.parameterOrder(), params, target.dialect(), registration);
            guard.rollback();
            putResult(response, audit, caller, target, outcome.result(), request.pageSize());
            deferred.set(() -> finish(audit, ExecutionStatus.SUCCEEDED, outcome.result() == null ? 0 : outcome.result().rows().size(), null,
               outcome.result() != null && outcome.result().truncated(), null));
            response.put("status", ExecutionStatus.SUCCEEDED.name());
            response.put("statements", List.of(statementView(0, statement, null)));
         }
         catch(SQLException e)
         {
            sqlFailure(audit, response, e, registration);
         }
         return (response);
      }));
   }



   /*******************************************************************************
    ** Write preview: dry run (where possible) in a transaction that is always
    ** rolled back, then a confirmation token.
    *******************************************************************************/
   private Map<String, Object> runWritePreview(WorkbenchBackends.Target target, List<SqlStatement> statements, Map<String, Object> params, Caller caller, SqlWorkbenchExecution audit) throws QException
   {
      boolean anyDdl          = statements.stream().anyMatch(s -> s.kind() == StatementKind.DDL);
      boolean dryRunSupported = !anyDdl || target.dialect().isTransactionalDdl();
      String  sqlHash         = audit.getSqlHash();

      return (withRegistration(audit, caller, (registration, deferred) ->
      {
         Map<String, Object>       response = baseResponse(audit);
         List<Map<String, Object>> views    = new ArrayList<>();
         Long                      total    = null;

         if(dryRunSupported)
         {
            total = 0L;
            try(SessionGuard guard = new SessionGuard(backends.openConnection(target, false), target.dialect(), false, config.getWriteStatementTimeoutSeconds()).begin())
            {
               for(int i = 0; i < statements.size(); i++)
               {
                  NamedParameterSql named   = NamedParameterSql.of(statements.get(i));
                  StatementOutcome  outcome = runOne(guard, named.jdbcSql(), named.parameterOrder(), params, target.dialect(), registration);
                  long              affected = outcome.affectedRows();
                  total += affected;
                  views.add(statementView(i, statements.get(i), affected));
               }
               guard.rollback();
            }
            catch(SQLException e)
            {
               sqlFailure(audit, response, e, registration);
               return (response);
            }
         }
         else
         {
            for(int i = 0; i < statements.size(); i++)
            {
               views.add(statementView(i, statements.get(i), null));
            }
         }

         boolean rowGuardExceeded = total != null && total > config.getMaxAffectedRowsWithoutAcknowledgement();
         String  token            = runtime.getWritePreviewTokens().issue(caller.userId(), target.name(), sqlHash, total, rowGuardExceeded);

         finish(audit, dryRunSupported ? ExecutionStatus.ROLLED_BACK : ExecutionStatus.SUCCEEDED, null, total, false, null);
         response.put("status", "PREVIEW");
         response.put("statements", views);
         response.put("dryRunSupported", dryRunSupported);
         response.put("dryRunNote", dryRunSupported ? null : "DDL on " + target.dialect().getUiName() + " commits implicitly, so it cannot be dry-run.");
         response.put("totalAffectedRows", total);
         response.put("rowGuardExceeded", rowGuardExceeded);
         response.put("rowGuardLimit", config.getMaxAffectedRowsWithoutAcknowledgement());
         response.put("confirmToken", token);
         response.put("expiresAt", String.valueOf(runtime.getWritePreviewTokens().expiresAt(token)));
         return (response);
      }));
   }



   /*******************************************************************************
    ** Write commit: check the token, re-run in one transaction, compare with
    ** the preview, enforce the hard cap, commit.
    *******************************************************************************/
   private Map<String, Object> runWriteCommit(WorkbenchBackends.Target target, List<SqlStatement> statements, Map<String, Object> params, ExecutionRequest request, Caller caller, SqlWorkbenchExecution audit) throws QException
   {
      WritePreviewTokens         tokens  = runtime.getWritePreviewTokens();
      WritePreviewTokens.Preview preview = tokens.find(request.confirmToken(), caller.userId(), target.name(), audit.getSqlHash());
      if(preview == null)
      {
         reject(audit, "The write confirmation is missing, expired, or does not match this SQL; preview it again.");
      }
      if(preview.rowGuardExceeded() && !request.acknowledgeRowGuard())
      {
         reject(audit, "This write affects more than " + config.getMaxAffectedRowsWithoutAcknowledgement() + " rows; acknowledge the row guard to commit.");
      }
      if(!tokens.consume(request.confirmToken()))
      {
         reject(audit, "This write confirmation was already used; preview it again.");
      }

      return (withRegistration(audit, caller, (registration, deferred) ->
      {
         Map<String, Object>       response = baseResponse(audit);
         List<Map<String, Object>> views    = new ArrayList<>();
         long                      total    = 0;
         CollectedResult           last     = null;

         try(SessionGuard guard = new SessionGuard(backends.openConnection(target, false), target.dialect(), false, config.getWriteStatementTimeoutSeconds()).begin())
         {
            for(int i = 0; i < statements.size(); i++)
            {
               NamedParameterSql named   = NamedParameterSql.of(statements.get(i));
               StatementOutcome  outcome = runOne(guard, named.jdbcSql(), named.parameterOrder(), params, target.dialect(), registration);
               total += outcome.affectedRows();
               if(outcome.result() != null)
               {
                  last = outcome.result();
               }
               views.add(statementView(i, statements.get(i), outcome.affectedRows()));
            }

            long finalTotal = total;
            if(preview.affectedRows() != null && preview.affectedRows() != total)
            {
               guard.rollback();
               deferred.set(() -> finish(audit, ExecutionStatus.DRIFTED, null, finalTotal, false, "Affected rows changed since the preview (" + preview.affectedRows() + " -> " + finalTotal + ")"));
               response.put("status", ExecutionStatus.DRIFTED.name());
               response.put("previewAffectedRows", preview.affectedRows());
               response.put("actualAffectedRows", total);
               response.put("statements", views);
               return (response);
            }

            if(total > config.getMaxAffectedRows())
            {
               guard.rollback();
               deferred.set(() -> finish(audit, ExecutionStatus.REJECTED, null, finalTotal, false, "Affected rows exceed the hard limit of " + config.getMaxAffectedRows()));
               response.put("status", ExecutionStatus.REJECTED.name());
               response.put("error", "This write affects " + total + " rows, more than the limit of " + config.getMaxAffectedRows() + "; it was rolled back.");
               response.put("statements", views);
               return (response);
            }

            guard.commit();
         }
         catch(SQLException e)
         {
            sqlFailure(audit, response, e, registration);
            return (response);
         }

         putResult(response, audit, caller, target, last, request.pageSize());
         finish(audit, ExecutionStatus.SUCCEEDED, last == null ? null : last.rows().size(), total, last != null && last.truncated(), null);
         response.put("status", ExecutionStatus.SUCCEEDED.name());
         response.put("statements", views);
         response.put("rowsAffected", total);
         return (response);
      }));
   }



   /*******************************************************************************
    ** Outcome of one statement.
    **
    ** @param result      its result set, if it returned one
    ** @param updateCount its update count (-1 when it returned rows)
    *******************************************************************************/
   record StatementOutcome(CollectedResult result, long updateCount)
   {

      /*******************************************************************************
       ** Rows this statement changed (for result-returning statements, e.g.
       ** INSERT ... RETURNING, the rows returned).
       *******************************************************************************/
      long affectedRows()
      {
         if(updateCount >= 0)
         {
            return (updateCount);
         }
         return (result == null ? 0 : result.rows().size());
      }
   }



   /*******************************************************************************
    ** Prepare, bind, run and collect one statement.
    *******************************************************************************/
   private StatementOutcome runOne(SessionGuard guard, String jdbcSql, List<String> parameterOrder, Map<String, Object> params, SqlDialect dialect, RunningStatements.Running registration) throws SQLException
   {
      try(PreparedStatement statement = guard.getConnection().prepareStatement(jdbcSql))
      {
         guard.configure(statement, config.getMaxRows() + 1, config.getFetchSize());
         for(int i = 0; i < parameterOrder.size(); i++)
         {
            bind(statement, i + 1, params.get(parameterOrder.get(i)), dialect);
         }

         registration.setStatement(statement);
         try
         {
            boolean hasResultSet = statement.execute();
            if(hasResultSet)
            {
               try(ResultSet resultSet = statement.getResultSet())
               {
                  ResultCollector collector = new ResultCollector(config.getMaxRows(), config.getMaxResultBytes(), config.getMaxCellChars());
                  return (new StatementOutcome(collector.collect(resultSet), -1));
               }
            }
            return (new StatementOutcome(null, Math.max(0, statement.getUpdateCount())));
         }
         finally
         {
            registration.setStatement(null);
         }
      }
   }



   /*******************************************************************************
    ** Bind one parameter value. Strings on Postgres are bound untyped
    ** (Types.OTHER) so the server infers their type from context (dates,
    ** numbers, uuids); everywhere else they are strings, which the other
    ** databases coerce.
    *******************************************************************************/
   static void bind(PreparedStatement statement, int index, Object value, SqlDialect dialect) throws SQLException
   {
      if(value == null)
      {
         statement.setNull(index, dialect == SqlDialect.POSTGRES ? Types.OTHER : Types.NULL);
      }
      else if(value instanceof Boolean b)
      {
         statement.setBoolean(index, b);
      }
      else if(value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte || value instanceof BigInteger)
      {
         statement.setLong(index, ((Number) value).longValue());
      }
      else if(value instanceof BigDecimal bd)
      {
         statement.setBigDecimal(index, bd);
      }
      else if(value instanceof Number n)
      {
         statement.setBigDecimal(index, new BigDecimal(n.toString()));
      }
      else if(dialect == SqlDialect.POSTGRES)
      {
         statement.setObject(index, value.toString(), Types.OTHER);
      }
      else
      {
         statement.setString(index, value.toString());
      }
   }



   /*******************************************************************************
    ** Cache the result and put its first page in the response.
    *******************************************************************************/
   private void putResult(Map<String, Object> response, SqlWorkbenchExecution audit, Caller caller, WorkbenchBackends.Target target, CollectedResult result, Integer pageSize)
   {
      if(result == null)
      {
         response.put("columns", List.of());
         response.put("rows", List.of());
         response.put("rowCount", 0);
         response.put("hasMore", false);
         response.put("truncated", false);
         return;
      }

      runtime.getResultCache().put(audit.getExecutionUuid(), caller.userId(), target.name(), result);
      int size = pageSize == null || pageSize <= 0 ? config.getDefaultPageSize() : Math.min(pageSize, config.getMaxPageSize());
      response.put("columns", result.columns().stream().map(ColumnInfo::toMap).toList());
      response.put("rows", ResultCache.page(result, 0, size, null, false));
      response.put("rowCount", result.rows().size());
      response.put("hasMore", result.rows().size() > size);
      response.put("truncated", result.truncated());
      response.put("truncatedReason", result.truncatedReason());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Map<String, Object> statementView(int index, SqlStatement statement, Long affectedRows)
   {
      Map<String, Object> view = new LinkedHashMap<>();
      view.put("index", index);
      view.put("kind", statement.kind().name());
      view.put("leadingKeyword", statement.leadingKeyword());
      view.put("text", statement.text().length() > 500 ? statement.text().substring(0, 500) + "…" : statement.text());
      view.put("affectedRows", affectedRows);
      return (view);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Map<String, Object> baseResponse(SqlWorkbenchExecution audit)
   {
      Map<String, Object> response = new LinkedHashMap<>();
      response.put("executionUuid", audit.getExecutionUuid());
      response.put("mode", audit.getMode());
      response.put("backendName", audit.getBackendName());
      return (response);
   }



   /*******************************************************************************
    ** Record a SQL failure in the audit and the response.
    *******************************************************************************/
   private void sqlFailure(SqlWorkbenchExecution audit, Map<String, Object> response, SQLException e, RunningStatements.Running registration) throws QException
   {
      ExecutionStatus status;
      if(registration.isCancelled())
      {
         status = ExecutionStatus.CANCELLED;
      }
      else if(e instanceof SQLTimeoutException || "57014".equals(e.getSQLState()))
      {
         status = ExecutionStatus.TIMED_OUT;
      }
      else
      {
         status = ExecutionStatus.FAILED;
      }

      String message = e.getMessage();
      finish(audit, status, null, null, false, message);
      response.put("status", status.name());
      response.put("error", message);
      response.put("sqlState", e.getSQLState());
      LOG.info("SQL Workbench execution did not succeed", logPair("executionUuid", audit.getExecutionUuid()), logPair("backendName", audit.getBackendName()),
         logPair("mode", audit.getMode()), logPair("status", status), logPair("sqlState", e.getSQLState()));
   }



   /*******************************************************************************
    ** Register (concurrency limit), audit-start, run, and always unregister.
    *******************************************************************************/
   private Map<String, Object> withRegistration(SqlWorkbenchExecution audit, Caller caller, RegisteredRun run) throws QException
   {
      RunningStatements.Running registration = runtime.getRunningStatements().register(audit.getExecutionUuid(), caller.userId(), config.getMaxConcurrentExecutionsPerUser());
      if(registration == null)
      {
         reject(audit, "You already have " + config.getMaxConcurrentExecutionsPerUser() + " executions running; wait for one to finish or cancel it.");
      }

      try
      {
         auditor.start(audit);
         Deferred            deferred = new Deferred();
         Map<String, Object> response = run.run(registration, deferred);
         deferred.run();
         LOG.info("SQL Workbench execution", logPair("executionUuid", audit.getExecutionUuid()), logPair("backendName", audit.getBackendName()),
            logPair("mode", audit.getMode()), logPair("status", audit.getStatus()), logPair("durationMs", audit.getDurationMs()));
         response.put("durationMs", audit.getDurationMs());
         return (response);
      }
      finally
      {
         runtime.getRunningStatements().unregister(audit.getExecutionUuid());
      }
   }



   /*******************************************************************************
    ** A run with its registration, and a place to defer the audit update until
    ** the session's connection is released (so an audit write never needs a
    ** second connection from a pool the session is holding).
    *******************************************************************************/
   @FunctionalInterface
   private interface RegisteredRun
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      Map<String, Object> run(RunningStatements.Running registration, Deferred deferred) throws QException;
   }



   /*******************************************************************************
    ** One deferred audit update.
    *******************************************************************************/
   private static class Deferred
   {
      private ExecutionAuditor.QContextRunnable action;



      /*******************************************************************************
       **
       *******************************************************************************/
      void set(ExecutionAuditor.QContextRunnable action)
      {
         this.action = action;
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      void run() throws QException
      {
         if(action != null)
         {
            action.run();
         }
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void finish(SqlWorkbenchExecution audit, ExecutionStatus status, Integer rowsReturned, Long rowsAffected, Boolean truncated, String error) throws QException
   {
      auditor.finish(audit, status, runtime.getClock().instant(), rowsReturned, rowsAffected, truncated, error);
   }



   /*******************************************************************************
    ** Audit a rejected request, then throw QBadRequestException.
    *******************************************************************************/
   private void reject(SqlWorkbenchExecution audit, String message) throws QException
   {
      recordRejection(audit, message);
      throw (new QBadRequestException(message));
   }



   /*******************************************************************************
    ** Audit a permission refusal, then throw QPermissionDeniedException.
    *******************************************************************************/
   private void rejectPermission(SqlWorkbenchExecution audit, String message) throws QException
   {
      recordRejection(audit, message);
      throw (new QPermissionDeniedException(message));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void recordRejection(SqlWorkbenchExecution audit, String message) throws QException
   {
      Instant now = runtime.getClock().instant();
      audit.setStatus(ExecutionStatus.REJECTED.name());
      audit.setEndTime(now);
      audit.setDurationMs(0);
      audit.setErrorMessage(message);
      auditor.record(audit);
   }



   /*******************************************************************************
    ** A new audit record for a request.
    *******************************************************************************/
   private SqlWorkbenchExecution newAudit(ExecutionRequest request, Caller caller, Map<String, Object> params, ExecutionMode mode)
   {
      return (new SqlWorkbenchExecution()
         .withExecutionUuid(request.executionUuid() != null ? request.executionUuid().toLowerCase() : UUID.randomUUID().toString())
         .withUserId(caller.userId())
         .withUserName(caller.userName())
         .withBackendName(request.backendName())
         .withMode(mode.name())
         .withSqlText(request.sql())
         .withSqlHash(sqlHash(request.backendName(), request.sql(), params))
         .withParametersJson(params.isEmpty() ? null : JsonUtils.toJson(new TreeMap<>(params)))
         .withNotebookId(request.notebookId())
         .withCellKey(request.cellKey())
         .withClientIp(caller.clientIp())
         .withStartTime(runtime.getClock().instant()));
   }



   /*******************************************************************************
    ** SHA-256 of backend, SQL and parameters: binds a write confirmation to
    ** exactly what was previewed.
    *******************************************************************************/
   static String sqlHash(String backendName, String sql, Map<String, Object> params)
   {
      try
      {
         MessageDigest digest = MessageDigest.getInstance("SHA-256");
         String        input  = backendName + "\u0000" + sql + "\u0000" + JsonUtils.toJson(new TreeMap<>(params == null ? Map.of() : params));
         return (HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8))));
      }
      catch(NoSuchAlgorithmException e)
      {
         throw (new IllegalStateException(e));
      }
   }

}
