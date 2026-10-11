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

package com.kingsrook.qbits.sqlworkbench.api;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QAuthenticationException;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QNotFoundException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitProducer;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchRuntime;
import com.kingsrook.qbits.sqlworkbench.SystemUser;
import com.kingsrook.qbits.sqlworkbench.engine.Caller;
import com.kingsrook.qbits.sqlworkbench.engine.ColumnInfo;
import com.kingsrook.qbits.sqlworkbench.engine.ExecutionRequest;
import com.kingsrook.qbits.sqlworkbench.engine.ResultCache;
import com.kingsrook.qbits.sqlworkbench.engine.SqlExecutionEngine;
import com.kingsrook.qbits.sqlworkbench.engine.WorkbenchBackends;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchExecution;
import com.kingsrook.qbits.sqlworkbench.notebook.NotebookService;
import com.kingsrook.qbits.sqlworkbench.schema.SchemaIntrospector;
import com.kingsrook.qbits.sqlworkbench.sql.SqlDialect;


/*******************************************************************************
 ** The SQL Workbench API, independent of HTTP: maps in, maps out, QQQ
 ** exceptions for errors (400 / 401 / 403 / 404). Works on the QContext's
 ** instance and session; every method first requires the access permission.
 *******************************************************************************/
public class SqlWorkbenchApi
{
   private final QInstance              qInstance;
   private final SqlWorkbenchRuntime    runtime;
   private final SqlWorkbenchQBitConfig config;
   private final SqlExecutionEngine     engine;
   private final NotebookService        notebooks;



   /*******************************************************************************
    ** For the QContext's instance.
    *******************************************************************************/
   public SqlWorkbenchApi() throws QNotFoundException
   {
      this(QContext.getQInstance(), SqlWorkbenchRuntime.get());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public SqlWorkbenchApi(QInstance qInstance, SqlWorkbenchRuntime runtime)
   {
      this.qInstance = qInstance;
      this.runtime = runtime;
      this.config = runtime.getConfig();
      this.engine = new SqlExecutionEngine(qInstance, runtime);
      this.notebooks = new NotebookService(runtime);
   }



   /*******************************************************************************
    ** The caller, after checking the access permission.
    *******************************************************************************/
   public Caller caller(String clientIp) throws QException
   {
      QSession session = QContext.getQSession();
      if(session == null)
      {
         throw (new QAuthenticationException("Not authenticated."));
      }
      if(!session.hasPermission(config.getAccessPermissionName()))
      {
         throw (new QPermissionDeniedException("Permission denied."));
      }

      String userId   = session.getUser() != null && StringUtils.hasContent(session.getUser().getIdReference()) ? session.getUser().getIdReference() : session.getIdReference();
      String userName = session.getUser() != null && StringUtils.hasContent(session.getUser().getFullName()) ? session.getUser().getFullName() : userId;
      if(!StringUtils.hasContent(userId))
      {
         throw (new QAuthenticationException("The session has no user."));
      }

      boolean canWrite = Boolean.TRUE.equals(config.getWriteModeEnabled()) && session.hasPermission(config.getWritePermissionName());
      boolean canShare = session.hasPermission(config.getSharePermissionName());
      return (new Caller(userId, userName, canWrite, canShare, clientIp));
   }



   /*******************************************************************************
    ** GET /capabilities
    *******************************************************************************/
   public Map<String, Object> capabilities(Caller caller)
   {
      Map<String, Object> limits = new LinkedHashMap<>();
      limits.put("maxRows", config.getMaxRows());
      limits.put("pageSize", config.getDefaultPageSize());
      limits.put("maxPageSize", config.getMaxPageSize());
      limits.put("maxResultBytes", config.getMaxResultBytes());
      limits.put("statementTimeoutSeconds", config.getStatementTimeoutSeconds());
      limits.put("writeStatementTimeoutSeconds", config.getWriteStatementTimeoutSeconds());
      limits.put("maxAffectedRowsWithoutAcknowledgement", config.getMaxAffectedRowsWithoutAcknowledgement());
      limits.put("maxAffectedRows", config.getMaxAffectedRows());
      limits.put("maxSqlLength", config.getMaxSqlLength());
      limits.put("maxStatementsPerRun", config.getMaxStatementsPerRun());

      List<Map<String, Object>> backends = new ArrayList<>();
      for(WorkbenchBackends.Target target : engine.getBackends().listTargets())
      {
         Map<String, Object> view = new LinkedHashMap<>();
         view.put("name", target.name());
         view.put("label", target.name());
         view.put("vendor", target.backend().getVendor());
         view.put("dialect", target.dialect().getUiName());
         view.put("readOnlyCredentials", engine.getBackends().hasReadOnlyCredentials(target));
         view.put("readOnlyEnforced", target.dialect() != SqlDialect.H2 && target.dialect() != SqlDialect.GENERIC || engine.getBackends().hasReadOnlyCredentials(target));
         view.put("transactionalDdl", target.dialect().isTransactionalDdl());
         view.put("writeAllowed", caller.canWrite());
         backends.add(view);
      }

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("version", SqlWorkbenchQBitProducer.getVersion());
      body.put("canWrite", caller.canWrite());
      body.put("canShare", caller.canShare());
      body.put("writeModeEnabled", config.getWriteModeEnabled());
      body.put("limits", limits);
      body.put("backends", backends);
      return (body);
   }



   /*******************************************************************************
    ** GET /schema/{backendName}
    *******************************************************************************/
   public Map<String, Object> schema(Caller caller, String backendName, boolean refresh) throws QException
   {
      return (new SchemaIntrospector(qInstance, runtime, engine.getBackends()).describe(backendName, refresh));
   }



   /*******************************************************************************
    ** POST /execute
    *******************************************************************************/
   public Map<String, Object> execute(Caller caller, Map<String, Object> body) throws QException
   {
      ExecutionRequest request = new ExecutionRequest(
         requireString(body, "backendName"),
         requireString(body, "sql"),
         paramsOf(body),
         "WRITE".equalsIgnoreCase(String.valueOf(body.get("mode"))),
         intOrNull(body.get("notebookId")),
         body.get("cellKey") == null ? null : body.get("cellKey").toString(),
         intOrNull(body.get("pageSize")),
         body.get("confirmToken") == null ? null : body.get("confirmToken").toString(),
         Boolean.TRUE.equals(body.get("acknowledgeRowGuard")),
         body.get("executionUuid") == null ? null : normalizeUuid(body.get("executionUuid").toString()));
      return (engine.execute(request, caller));
   }



   /*******************************************************************************
    ** POST /explain
    *******************************************************************************/
   public Map<String, Object> explain(Caller caller, Map<String, Object> body) throws QException
   {
      return (engine.explain(requireString(body, "backendName"), requireString(body, "sql"), paramsOf(body), Boolean.TRUE.equals(body.get("analyze")), caller));
   }



   /*******************************************************************************
    ** GET /executions/{uuid}/rows
    *******************************************************************************/
   public Map<String, Object> rows(Caller caller, String executionUuid, Integer offset, Integer limit, Integer sortColumn, String direction) throws QException
   {
      String            uuid  = normalizeUuid(executionUuid);
      ResultCache.Entry entry = cached(caller, uuid);
      int from = offset == null ? 0 : Math.max(0, offset);
      int size = limit == null || limit <= 0 ? config.getDefaultPageSize() : Math.min(limit, config.getMaxPageSize());

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("executionUuid", uuid);
      body.put("columns", entry.result().columns().stream().map(ColumnInfo::toMap).toList());
      body.put("rows", ResultCache.page(entry.result(), from, size, sortColumn, "desc".equalsIgnoreCase(direction)));
      body.put("offset", from);
      body.put("rowCount", entry.result().rows().size());
      body.put("truncated", entry.result().truncated());
      body.put("truncatedReason", entry.result().truncatedReason());
      return (body);
   }



   /*******************************************************************************
    ** GET /executions/{uuid}/export - the cached entry to write.
    *******************************************************************************/
   public ResultCache.Entry exportable(Caller caller, String executionUuid) throws QException
   {
      return (cached(caller, normalizeUuid(executionUuid)));
   }



   /*******************************************************************************
    ** POST /executions/{uuid}/cancel
    *******************************************************************************/
   public Map<String, Object> cancel(Caller caller, String executionUuid)
   {
      return (Map.of("cancelled", engine.cancel(normalizeUuid(executionUuid), caller)));
   }



   /*******************************************************************************
    ** GET /history - the caller's own executions, newest first.
    *******************************************************************************/
   public Map<String, Object> history(Caller caller, Integer limit, String backendName) throws QException
   {
      QQueryFilter filter = new QQueryFilter(new QFilterCriteria("userId", QCriteriaOperator.EQUALS, caller.userId()))
         .withOrderBy(new QFilterOrderBy("id", false))
         .withLimit(limit == null || limit <= 0 ? 50 : Math.min(limit, 200));
      if(StringUtils.hasContent(backendName))
      {
         filter.withCriteria(new QFilterCriteria("backendName", QCriteriaOperator.EQUALS, backendName));
      }

      List<Map<String, Object>> executions = new ArrayList<>();
      //////////////////////////////////////////////////////////////////////
      // the audit table needs the admin permission; the filter above is //
      // the boundary (own rows only), so read it as the system user     //
      //////////////////////////////////////////////////////////////////////
      for(QRecord record : SystemUser.call(() -> QueryAction.execute(config.applyPrefix(SqlWorkbenchExecution.TABLE_NAME), filter)))
      {
         SqlWorkbenchExecution execution = new SqlWorkbenchExecution(record);
         Map<String, Object>   view      = new LinkedHashMap<>();
         view.put("executionUuid", execution.getExecutionUuid());
         view.put("backendName", execution.getBackendName());
         view.put("mode", execution.getMode());
         view.put("status", execution.getStatus());
         view.put("sqlText", execution.getSqlText());
         view.put("notebookId", execution.getNotebookId());
         view.put("cellKey", execution.getCellKey());
         view.put("startTime", execution.getStartTime() == null ? null : execution.getStartTime().toString());
         view.put("durationMs", execution.getDurationMs());
         view.put("rowsReturned", execution.getRowsReturned());
         view.put("rowsAffected", execution.getRowsAffected());
         view.put("errorMessage", execution.getErrorMessage());
         executions.add(view);
      }
      return (Map.of("executions", executions));
   }



   /*******************************************************************************
    ** Notebook endpoints.
    *******************************************************************************/
   public NotebookService notebooks()
   {
      return (notebooks);
   }



   /*******************************************************************************
    ** POST /notebooks/{id}/snapshots - the snapshot keeps the audited SQL.
    *******************************************************************************/
   public Map<String, Object> createSnapshot(Caller caller, Integer notebookId, Map<String, Object> body) throws QException
   {
      String executionUuid = normalizeUuid(requireString(body, "executionUuid"));
      cached(caller, executionUuid);
      List<QRecord> audit = SystemUser.call(() -> QueryAction.execute(config.applyPrefix(SqlWorkbenchExecution.TABLE_NAME),
         new QQueryFilter(new QFilterCriteria("executionUuid", QCriteriaOperator.EQUALS, executionUuid))
            .withCriteria(new QFilterCriteria("userId", QCriteriaOperator.EQUALS, caller.userId()))
            .withLimit(1)));
      String sqlText = audit.isEmpty() ? null : audit.get(0).getValueString("sqlText");

      Map<String, Object> normalized = new LinkedHashMap<>(body);
      normalized.put("executionUuid", executionUuid);
      return (notebooks.createSnapshot(notebookId, normalized, caller, sqlText));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private ResultCache.Entry cached(Caller caller, String executionUuid) throws QNotFoundException
   {
      return (runtime.getResultCache().get(executionUuid, caller.userId())
         .orElseThrow(() -> new QNotFoundException("That result is no longer available; run the SQL again.")));
   }



   /*******************************************************************************
    ** Execution ids are stored and cached lower-case; accept any case (and
    ** surrounding blanks) from clients.
    *******************************************************************************/
   static String normalizeUuid(String executionUuid)
   {
      return (executionUuid == null ? null : executionUuid.trim().toLowerCase(Locale.ROOT));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   static Map<String, Object> paramsOf(Map<String, Object> body) throws QBadRequestException
   {
      Object params = body.get("params");
      if(params == null)
      {
         return (Map.of());
      }
      if(!(params instanceof Map<?, ?>))
      {
         throw (new QBadRequestException("params must be an object of name: value."));
      }
      return (new LinkedHashMap<>((Map<String, Object>) params));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static String requireString(Map<String, Object> body, String key) throws QBadRequestException
   {
      Object value = body == null ? null : body.get(key);
      if(value == null || !StringUtils.hasContent(value.toString()))
      {
         throw (new QBadRequestException(key + " is required."));
      }
      return (value.toString());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static Integer intOrNull(Object value) throws QBadRequestException
   {
      if(value == null || "".equals(value))
      {
         return (null);
      }
      if(value instanceof Number n)
      {
         return (n.intValue());
      }
      try
      {
         return (Integer.parseInt(value.toString().trim()));
      }
      catch(NumberFormatException e)
      {
         throw (new QBadRequestException("Expected an integer, got: " + value));
      }
   }

}
