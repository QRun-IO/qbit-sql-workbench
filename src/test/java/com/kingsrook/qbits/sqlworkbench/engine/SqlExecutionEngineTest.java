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
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QNotFoundException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qbits.sqlworkbench.BaseSqlWorkbenchTest;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchRuntime;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Engine behaviour against H2: read-only runs, gating, write preview and
 ** commit, drift, guards, explain, and the audit trail.
 *******************************************************************************/
class SqlExecutionEngineTest extends BaseSqlWorkbenchTest
{
   private SqlExecutionEngine engine;



   /*******************************************************************************
    **
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      engine = new SqlExecutionEngine(qInstance, SqlWorkbenchRuntime.get(qInstance));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private Map<String, Object> run(String sql, Map<String, Object> params, String user) throws Exception
   {
      return (engine.execute(ExecutionRequest.readOnly(BACKEND_NAME, sql, params), caller(user)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private List<SqlWorkbenchExecution> audits() throws Exception
   {
      return (QueryAction.execute(SqlWorkbenchExecution.TABLE_NAME, SqlWorkbenchExecution.class, new QQueryFilter()));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void testReadOnlySelectWithTypesAndParams() throws Exception
   {
      Map<String, Object> response = run("SELECT id, first_name, birth_date, salary, active, photo, create_date FROM person WHERE id <= :maxId ORDER BY id",
         Map.of("maxId", 3), READER);

      assertThat(response.get("status")).isEqualTo("SUCCEEDED");
      assertThat(response.get("rowCount")).isEqualTo(3);
      List<Map<String, Object>> columns = (List<Map<String, Object>>) response.get("columns");
      assertThat(columns).extracting(c -> c.get("kind")).containsExactly("number", "string", "date", "number", "boolean", "binary", "datetime");

      List<List<Object>> rows = (List<List<Object>>) response.get("rows");
      assertThat(rows.get(0).get(1)).isEqualTo("Darin");
      assertThat(rows.get(0).get(2)).isEqualTo("1980-05-31");
      assertThat((BigDecimal) rows.get(0).get(3)).isEqualByComparingTo("25000.00");
      assertThat(rows.get(0).get(4)).isEqualTo(true);
      assertThat((Map<String, Object>) rows.get(0).get(5)).containsEntry("$binary", true).containsEntry("hexPreview", "cafebabe");
      assertThat(rows.get(0).get(6)).isEqualTo("2026-01-01T10:00");
      assertThat(rows.get(2).get(3)).isNull();

      List<SqlWorkbenchExecution> audits = audits();
      assertThat(audits).hasSize(1);
      SqlWorkbenchExecution audit = audits.get(0);
      assertThat(audit.getStatus()).isEqualTo("SUCCEEDED");
      assertThat(audit.getMode()).isEqualTo("READ_ONLY");
      assertThat(audit.getUserId()).isEqualTo(READER);
      assertThat(audit.getSqlText()).contains(":maxId");
      assertThat(audit.getParametersJson()).contains("maxId");
      assertThat(audit.getRowsReturned()).isEqualTo(3);
      assertThat(audit.getDurationMs()).isNotNull();
      assertThat(audit.getExecutionUuid()).isEqualTo(response.get("executionUuid"));

      //////////////////////////////////////////////
      // the result is cached for its owner only  //
      //////////////////////////////////////////////
      SqlWorkbenchRuntime runtime = SqlWorkbenchRuntime.get(qInstance);
      assertThat(runtime.getResultCache().get((String) response.get("executionUuid"), READER)).isPresent();
      assertThat(runtime.getResultCache().get((String) response.get("executionUuid"), OTHER_READER)).isEmpty();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testReadOnlyRefusals() throws Exception
   {
      assertThatThrownBy(() -> run("DELETE FROM person", Map.of(), READER)).isInstanceOf(QBadRequestException.class).hasMessageContaining("write mode");
      assertThatThrownBy(() -> run("SELECT 1; SELECT 2", Map.of(), READER)).isInstanceOf(QBadRequestException.class).hasMessageContaining("2 statements");
      assertThatThrownBy(() -> run("SET search_path = x", Map.of(), WRITER)).isInstanceOf(QBadRequestException.class).hasMessageContaining("Not allowed");
      assertThatThrownBy(() -> run("SELECT * FROM person WHERE id = :id", Map.of(), READER)).isInstanceOf(QBadRequestException.class).hasMessageContaining(":id");
      assertThatThrownBy(() -> run("   ", Map.of(), READER)).isInstanceOf(QBadRequestException.class);
      assertThatThrownBy(() -> run("-- nothing", Map.of(), READER)).isInstanceOf(QBadRequestException.class);
      assertThatThrownBy(() -> engine.execute(ExecutionRequest.readOnly("nope", "SELECT 1", Map.of()), caller(READER))).isInstanceOf(QNotFoundException.class);

      assertThat(audits()).extracting(SqlWorkbenchExecution::getStatus).containsOnly("REJECTED").hasSize(4);
      assertThat(QueryAction.execute("person", new QQueryFilter())).hasSize(5);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testSqlErrorIsAResult() throws Exception
   {
      Map<String, Object> response = run("SELECT no_such_column FROM person", Map.of(), READER);
      assertThat(response.get("status")).isEqualTo("FAILED");
      assertThat((String) response.get("error")).isNotBlank();
      assertThat(audits()).extracting(SqlWorkbenchExecution::getStatus).containsExactly("FAILED");
      assertThat(audits().get(0).getErrorMessage()).isNotBlank();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testWriteNeedsPermission() throws Exception
   {
      ExecutionRequest request = new ExecutionRequest(BACKEND_NAME, "DELETE FROM person", Map.of(), true, null, null, null, null, false);
      assertThatThrownBy(() -> engine.execute(request, caller(READER))).isInstanceOf(QPermissionDeniedException.class);
      assertThat(audits()).extracting(SqlWorkbenchExecution::getStatus).containsExactly("REJECTED");
      assertThat(QueryAction.execute("person", new QQueryFilter())).hasSize(5);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testWritePreviewThenCommit() throws Exception
   {
      String           sql     = "UPDATE person SET active = FALSE WHERE salary < :limit; UPDATE person SET last_name = 'X' WHERE id = 1";
      ExecutionRequest preview = new ExecutionRequest(BACKEND_NAME, sql, Map.of("limit", 26000), true, null, null, null, null, false);

      Map<String, Object> previewResponse = engine.execute(preview, caller(WRITER));
      assertThat(previewResponse.get("status")).isEqualTo("PREVIEW");
      assertThat(previewResponse.get("dryRunSupported")).isEqualTo(true);
      assertThat(previewResponse.get("totalAffectedRows")).isEqualTo(3L);
      assertThat(previewResponse.get("rowGuardExceeded")).isEqualTo(false);
      String token = (String) previewResponse.get("confirmToken");
      assertThat(token).isNotBlank();

      ///////////////////////////////////////
      // the dry run was rolled back       //
      ///////////////////////////////////////
      assertThat(QueryAction.execute("person", new QQueryFilter(new QFilterCriteria("lastName", QCriteriaOperator.EQUALS, "X")))).isEmpty();

      ////////////////////////////////////////////////////////////////
      // a token is bound to its user, SQL and params - and single use //
      ////////////////////////////////////////////////////////////////
      ExecutionRequest otherSql = new ExecutionRequest(BACKEND_NAME, sql + " ", Map.of("limit", 26000), true, null, null, null, token, false);
      assertThatThrownBy(() -> engine.execute(otherSql, caller(WRITER))).isInstanceOf(QBadRequestException.class).hasMessageContaining("preview it again");

      ExecutionRequest commit = new ExecutionRequest(BACKEND_NAME, sql, Map.of("limit", 26000), true, null, null, null, token, false);
      Map<String, Object> commitResponse = engine.execute(commit, caller(WRITER));
      assertThat(commitResponse.get("status")).isEqualTo("SUCCEEDED");
      assertThat(commitResponse.get("rowsAffected")).isEqualTo(3L);
      assertThat(QueryAction.execute("person", new QQueryFilter(new QFilterCriteria("lastName", QCriteriaOperator.EQUALS, "X")))).hasSize(1);

      assertThatThrownBy(() -> engine.execute(commit, caller(WRITER))).isInstanceOf(QBadRequestException.class);

      assertThat(audits()).extracting(SqlWorkbenchExecution::getMode).contains("WRITE_PREVIEW", "WRITE_COMMIT");
      assertThat(audits()).filteredOn(a -> "WRITE_COMMIT".equals(a.getMode()) && "SUCCEEDED".equals(a.getStatus())).hasSize(1)
         .first().extracting(SqlWorkbenchExecution::getRowsAffected).isEqualTo(3);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testWriteDrift() throws Exception
   {
      String           sql     = "DELETE FROM person WHERE active = TRUE";
      Map<String, Object> preview = engine.execute(new ExecutionRequest(BACKEND_NAME, sql, Map.of(), true, null, null, null, null, false), caller(WRITER));
      assertThat(preview.get("totalAffectedRows")).isEqualTo(3L);

      run("SELECT 1", Map.of(), READER);
      QueryAction.execute("person", new QQueryFilter());
      try(var connection = engine.getBackends().openConnection(engine.getBackends().getTarget(BACKEND_NAME), false); var statement = connection.createStatement())
      {
         statement.execute("UPDATE person SET active = TRUE WHERE id = 3");
      }

      Map<String, Object> commit = engine.execute(new ExecutionRequest(BACKEND_NAME, sql, Map.of(), true, null, null, null, (String) preview.get("confirmToken"), false), caller(WRITER));
      assertThat(commit.get("status")).isEqualTo("DRIFTED");
      assertThat(commit.get("actualAffectedRows")).isEqualTo(4L);
      assertThat(QueryAction.execute("person", new QQueryFilter())).hasSize(5);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testRowGuardAndHardCap() throws Exception
   {
      config.setMaxAffectedRowsWithoutAcknowledgement(2);
      config.setMaxAffectedRows(4);

      String              sql     = "UPDATE person SET first_name = upper(first_name) WHERE id <= 3";
      Map<String, Object> preview = engine.execute(new ExecutionRequest(BACKEND_NAME, sql, Map.of(), true, null, null, null, null, false), caller(WRITER));
      assertThat(preview.get("rowGuardExceeded")).isEqualTo(true);
      String token = (String) preview.get("confirmToken");

      assertThatThrownBy(() -> engine.execute(new ExecutionRequest(BACKEND_NAME, sql, Map.of(), true, null, null, null, token, false), caller(WRITER)))
         .isInstanceOf(QBadRequestException.class).hasMessageContaining("row guard");

      Map<String, Object> commit = engine.execute(new ExecutionRequest(BACKEND_NAME, sql, Map.of(), true, null, null, null, token, true), caller(WRITER));
      assertThat(commit.get("status")).isEqualTo("SUCCEEDED");

      String              all     = "UPDATE person SET first_name = lower(first_name)";
      Map<String, Object> preview2 = engine.execute(new ExecutionRequest(BACKEND_NAME, all, Map.of(), true, null, null, null, null, false), caller(WRITER));
      Map<String, Object> commit2  = engine.execute(new ExecutionRequest(BACKEND_NAME, all, Map.of(), true, null, null, null, (String) preview2.get("confirmToken"), true), caller(WRITER));
      assertThat(commit2.get("status")).isEqualTo("REJECTED");
      assertThat(QueryAction.execute("person", new QQueryFilter(new QFilterCriteria("firstName", QCriteriaOperator.EQUALS, "darin")))).isEmpty();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testDdlOnNonTransactionalDialectSkipsDryRun() throws Exception
   {
      Map<String, Object> preview = engine.execute(new ExecutionRequest(BACKEND_NAME, "CREATE TABLE scratch (id INT)", Map.of(), true, null, null, null, null, false), caller(WRITER));
      assertThat(preview.get("dryRunSupported")).isEqualTo(false);
      assertThat(preview.get("totalAffectedRows")).isNull();

      Map<String, Object> commit = engine.execute(new ExecutionRequest(BACKEND_NAME, "CREATE TABLE scratch (id INT)", Map.of(), true, null, null, null, (String) preview.get("confirmToken"), false), caller(WRITER));
      assertThat(commit.get("status")).isEqualTo("SUCCEEDED");
      assertThat(run("SELECT count(*) FROM scratch", Map.of(), READER).get("status")).isEqualTo("SUCCEEDED");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testTruncationByRowsAndBytes() throws Exception
   {
      config.setMaxRows(2);
      Map<String, Object> rows = run("SELECT * FROM person", Map.of(), READER);
      assertThat(rows.get("truncated")).isEqualTo(true);
      assertThat(rows.get("truncatedReason")).isEqualTo("ROWS");
      assertThat(rows.get("rowCount")).isEqualTo(2);

      config.setMaxRows(100);
      config.setMaxResultBytes(200L);
      Map<String, Object> bytes = run("SELECT * FROM person", Map.of(), READER);
      assertThat(bytes.get("truncatedReason")).isEqualTo("BYTES");
      assertThat(audits()).filteredOn(a -> Boolean.TRUE.equals(a.getTruncated())).hasSize(2);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testPageSize() throws Exception
   {
      Map<String, Object> response = engine.execute(new ExecutionRequest(BACKEND_NAME, "SELECT * FROM person", Map.of(), false, 7, "cell-1", 2, null, false), caller(READER));
      assertThat((List<?>) response.get("rows")).hasSize(2);
      assertThat(response.get("hasMore")).isEqualTo(true);
      assertThat(audits().get(0).getNotebookId()).isEqualTo(7);
      assertThat(audits().get(0).getCellKey()).isEqualTo("cell-1");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testExplain() throws Exception
   {
      Map<String, Object> response = engine.explain(BACKEND_NAME, "SELECT * FROM person WHERE id = :id", Map.of("id", 1), false, caller(READER));
      assertThat(response.get("status")).isEqualTo("SUCCEEDED");
      assertThat(response.get("plan")).isInstanceOf(Map.class);
      assertThat((String) response.get("raw")).containsIgnoringCase("person");

      assertThatThrownBy(() -> engine.explain(BACKEND_NAME, "SELECT 1", Map.of(), true, caller(READER))).isInstanceOf(QPermissionDeniedException.class);
      assertThatThrownBy(() -> engine.explain(BACKEND_NAME, "EXPLAIN SELECT 1", Map.of(), false, caller(READER))).isInstanceOf(QBadRequestException.class);
      assertThatThrownBy(() -> engine.explain(BACKEND_NAME, "DELETE FROM person", Map.of(), false, caller(WRITER))).isInstanceOf(QBadRequestException.class);

      Map<String, Object> analyzed = engine.explain(BACKEND_NAME, "DELETE FROM person WHERE id = 1", Map.of(), true, caller(WRITER));
      assertThat(analyzed.get("status")).isEqualTo("SUCCEEDED");
      assertThat(QueryAction.execute("person", new QQueryFilter())).hasSize(5);
      assertThat(audits()).extracting(SqlWorkbenchExecution::getMode).contains("EXPLAIN", "EXPLAIN_ANALYZE");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testConcurrencyLimitAndCancel() throws Exception
   {
      SqlWorkbenchRuntime runtime = SqlWorkbenchRuntime.get(qInstance);
      config.setMaxConcurrentExecutionsPerUser(1);
      RunningStatements.Running blocker = runtime.getRunningStatements().register("blocker", READER, 1);
      assertThat(blocker).isNotNull();

      assertThatThrownBy(() -> run("SELECT 1", Map.of(), READER)).isInstanceOf(QBadRequestException.class).hasMessageContaining("running");
      assertThat(run("SELECT 1", Map.of(), OTHER_READER).get("status")).isEqualTo("SUCCEEDED");

      assertThat(engine.cancel("blocker", caller(OTHER_READER))).isFalse();
      assertThat(engine.cancel("blocker", caller(READER))).isTrue();
      assertThat(blocker.isCancelled()).isTrue();
      runtime.getRunningStatements().unregister("blocker", blocker);
      assertThat(run("SELECT 1", Map.of(), READER).get("status")).isEqualTo("SUCCEEDED");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testSqlLengthAndStatementLimits() throws Exception
   {
      config.setMaxSqlLength(10);
      assertThatThrownBy(() -> run("SELECT 1 + 1 + 1", Map.of(), READER)).isInstanceOf(QBadRequestException.class).hasMessageContaining("longer");

      config.setMaxSqlLength(1000);
      config.setMaxStatementsPerRun(1);
      assertThatThrownBy(() -> engine.execute(new ExecutionRequest(BACKEND_NAME, "SELECT 1; SELECT 2", Map.of(), true, null, null, null, null, false), caller(WRITER)))
         .isInstanceOf(QBadRequestException.class).hasMessageContaining("Too many");

      config.setWriteModeEnabled(false);
      assertThatThrownBy(() -> engine.execute(new ExecutionRequest(BACKEND_NAME, "DELETE FROM person", Map.of(), true, null, null, null, null, false), caller(WRITER)))
         .isInstanceOf(QPermissionDeniedException.class);
   }



   /*******************************************************************************
    ** A client-chosen execution id is used (so the client can cancel early),
    ** and must be a UUID.
    *******************************************************************************/
   @Test
   void testClientExecutionUuid() throws Exception
   {
      String uuid = "0F8FAD5B-D9CB-469F-A165-70867728950E";
      Map<String, Object> response = engine.execute(new ExecutionRequest(BACKEND_NAME, "SELECT 1", Map.of(), false, null, null, null, null, false, uuid), caller(READER));
      assertThat(response.get("executionUuid")).isEqualTo(uuid.toLowerCase());
      assertThat(audits().get(0).getExecutionUuid()).isEqualTo(uuid.toLowerCase());

      assertThatThrownBy(() -> engine.execute(new ExecutionRequest(BACKEND_NAME, "SELECT 1", Map.of(), false, null, null, null, null, false, "not-a-uuid"), caller(READER)))
         .isInstanceOf(QBadRequestException.class);
   }



   /*******************************************************************************
    ** A client uuid already in the audit is refused (in any case), before
    ** anything is registered or recorded.
    *******************************************************************************/
   @Test
   void testReusedExecutionUuidIsRefused() throws Exception
   {
      String uuid = "1b4e28ba-2fa1-11d2-883f-0016d3cca427";
      engine.execute(new ExecutionRequest(BACKEND_NAME, "SELECT 1", Map.of(), false, null, null, null, null, false, uuid), caller(READER));
      assertThat(audits()).hasSize(1);

      for(String again : List.of(uuid, uuid.toUpperCase()))
      {
         assertThatThrownBy(() -> engine.execute(new ExecutionRequest(BACKEND_NAME, "SELECT 2", Map.of(), false, null, null, null, null, false, again), caller(READER)))
            .isInstanceOf(DuplicateExecutionException.class).hasMessageContaining("already used");
         assertThatThrownBy(() -> engine.execute(new ExecutionRequest(BACKEND_NAME, "SELECT 2", Map.of(), false, null, null, null, null, false, again), caller(OTHER_READER)))
            .isInstanceOf(DuplicateExecutionException.class);
      }
      assertThat(audits()).hasSize(1);
      assertThat(SqlWorkbenchRuntime.get(qInstance).getRunningStatements().size()).isZero();
   }



   /*******************************************************************************
    ** Re-sending the id of a run in flight neither overwrites nor removes its
    ** registration, so the concurrency limit and cancel still hold.
    *******************************************************************************/
   @Test
   void testInFlightExecutionUuidIsRefused() throws Exception
   {
      SqlWorkbenchRuntime runtime = SqlWorkbenchRuntime.get(qInstance);
      String              uuid    = "6fa459ea-ee8a-3ca4-894e-db77e160355e";
      config.setMaxConcurrentExecutionsPerUser(1);
      RunningStatements.Running inFlight = runtime.getRunningStatements().register(uuid, READER, 1);

      assertThatThrownBy(() -> engine.execute(new ExecutionRequest(BACKEND_NAME, "SELECT 1", Map.of(), false, null, null, null, null, false, uuid.toUpperCase()), caller(READER)))
         .isInstanceOf(DuplicateExecutionException.class);
      assertThatThrownBy(() -> engine.execute(new ExecutionRequest(BACKEND_NAME, "SELECT 1", Map.of(), false, null, null, null, null, false, uuid), caller(OTHER_READER)))
         .isInstanceOf(DuplicateExecutionException.class);

      ///////////////////////////////////////////////////
      // the original is still registered and counted //
      ///////////////////////////////////////////////////
      assertThat(runtime.getRunningStatements().isRunning(uuid)).isTrue();
      assertThatThrownBy(() -> run("SELECT 1", Map.of(), READER)).isInstanceOf(QBadRequestException.class).hasMessageContaining("running");
      assertThat(engine.cancel(uuid.toUpperCase(), caller(READER))).isTrue();
      assertThat(inFlight.isCancelled()).isTrue();
      assertThat(audits()).extracting(SqlWorkbenchExecution::getExecutionUuid).doesNotContain(uuid);

      runtime.getRunningStatements().unregister(uuid, inFlight);
      assertThat(run("SELECT 1", Map.of(), READER).get("status")).isEqualTo("SUCCEEDED");
   }



   /*******************************************************************************
    ** An audit insert that fails (here: a duplicate executionUuid) throws,
    ** rather than being dropped silently.
    *******************************************************************************/
   @Test
   void testAuditRecordFailsLoudly() throws Exception
   {
      String uuid = (String) run("SELECT 1", Map.of(), READER).get("executionUuid");
      ExecutionAuditor auditor = new ExecutionAuditor(config);
      SqlWorkbenchExecution rejected = new SqlWorkbenchExecution()
         .withExecutionUuid(uuid)
         .withUserId(READER)
         .withBackendName(BACKEND_NAME)
         .withMode(ExecutionMode.READ_ONLY.name())
         .withStatus(ExecutionStatus.REJECTED.name())
         .withSqlText("SELECT 1")
         .withStartTime(java.time.Instant.now());
      assertThatThrownBy(() -> auditor.record(rejected)).isInstanceOf(com.kingsrook.qqq.backend.core.exceptions.QException.class).hasMessageContaining("audit");
      assertThat(audits()).hasSize(1);
      assertThat(auditor.exists(uuid)).isTrue();
      assertThat(auditor.exists("00000000-0000-0000-0000-000000000000")).isFalse();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testSqlHashBindsParams()
   {
      assertThat(SqlExecutionEngine.sqlHash("a", "SELECT :x", Map.of("x", 1))).isNotEqualTo(SqlExecutionEngine.sqlHash("a", "SELECT :x", Map.of("x", 2)));
      assertThat(SqlExecutionEngine.sqlHash("a", "SELECT 1", null)).isEqualTo(SqlExecutionEngine.sqlHash("a", "SELECT 1", Map.of()));
      assertThat(SqlExecutionEngine.sqlHash("a", "SELECT 1", Map.of())).isNotEqualTo(SqlExecutionEngine.sqlHash("b", "SELECT 1", Map.of()));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testAuditCannotBeInsertedByUsers() throws Exception
   {
      run("SELECT 1", Map.of(), READER);
      List<QRecord> records = QueryAction.execute(SqlWorkbenchExecution.TABLE_NAME, new QQueryFilter());
      assertThat(records).hasSize(1);
      assertThat(qInstance.getTable(SqlWorkbenchExecution.TABLE_NAME).isCapabilityEnabled(qInstance.getBackend(BACKEND_NAME),
         com.kingsrook.qqq.backend.core.model.metadata.tables.Capability.TABLE_INSERT)).isFalse();
   }

}
