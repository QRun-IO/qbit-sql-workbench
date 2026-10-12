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

package com.kingsrook.qbits.sqlworkbench;


import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qbits.sqlworkbench.engine.Caller;
import com.kingsrook.qbits.sqlworkbench.engine.ExecutionRequest;
import com.kingsrook.qbits.sqlworkbench.engine.SessionGuard;
import com.kingsrook.qbits.sqlworkbench.engine.SqlExecutionEngine;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchExecution;
import com.kingsrook.qbits.sqlworkbench.notebook.NotebookService;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.actions.tables.count.CountInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.actions.tables.CountAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchCell;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebook;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookShare;
import com.kingsrook.qbits.sqlworkbench.schema.SchemaIntrospector;
import com.kingsrook.qbits.sqlworkbench.sql.SqlDialect;
import com.kingsrook.qqq.backend.module.postgres.model.metadata.PostgreSQLBackendMetaData;
import com.kingsrook.qqq.backend.module.rdbms.jdbc.C3P0PooledConnectionProvider;
import com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.ConnectionPoolSettings;
import com.kingsrook.qbits.sqlworkbench.metadata.SqlWorkbenchHomeWidgetsMetaDataProducer;
import com.kingsrook.qqq.backend.core.actions.dashboard.RenderWidgetAction;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.ChartData;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.QWidgetData;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.RawHTML;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.TableData;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** The engine against a real PostgreSQL (Testcontainers): read-only
 ** transactions, timeouts, cancellation, clean pooled connections,
 ** read-only credentials, write preview / commit, transactional DDL dry
 ** runs, EXPLAIN (ANALYZE) with rollback, types, schema, and notebooks.
 *******************************************************************************/
@ExtendWith(RequiresDockerCondition.class)
@Testcontainers
class PostgresIntegrationTest
{
   private static final String BACKEND = "pg";

   @Container
   @SuppressWarnings("resource")
   static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse(System.getProperty("postgres.test.image", "postgres:16-alpine"))
      .asCompatibleSubstituteFor("postgres"));

   private QInstance              qInstance;
   private SqlWorkbenchQBitConfig config;
   private SqlExecutionEngine     engine;



   /*******************************************************************************
    ** Schema, data, and a SELECT-only role, once.
    *******************************************************************************/
   @BeforeAll
   static void primeDatabase() throws Exception
   {
      try(Connection connection = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
          Statement statement = connection.createStatement())
      {
         statement.execute(Files.readString(Path.of("docs/ddl/postgres.sql")));
         statement.execute("""
            CREATE TABLE person (id SERIAL PRIMARY KEY, first_name TEXT, salary NUMERIC(12,2), tags TEXT[], doc JSONB, ref UUID, seen TIMESTAMPTZ);
            INSERT INTO person (first_name, salary, tags, doc, ref, seen) VALUES
               ('Darin', 25000, ARRAY['a','b'], '{"k": 1}', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '2026-01-01T10:00:00Z'),
               ('James', 26000, NULL, NULL, NULL, NULL),
               ('Tim', NULL, ARRAY[]::TEXT[], '[1,2]', NULL, NULL);
            CREATE ROLE sqlwb_ro LOGIN PASSWORD 'ro-pass';
            GRANT SELECT ON person TO sqlwb_ro;
            """);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      ConnectionManager.resetConnectionProviders();
      try(Connection connection = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
          Statement statement = connection.createStatement())
      {
         statement.execute("TRUNCATE sql_workbench_notebook_share, sql_workbench_result_snapshot, sql_workbench_notebook_version, sql_workbench_cell, sql_workbench_notebook, sql_workbench_execution");
         statement.execute("DROP TABLE IF EXISTS scratch");
      }

      PostgreSQLBackendMetaData backend = new PostgreSQLBackendMetaData()
         .withName(BACKEND)
         .withHostName(POSTGRES.getHost())
         .withPort(POSTGRES.getFirstMappedPort())
         .withDatabaseName(POSTGRES.getDatabaseName())
         .withUsername(POSTGRES.getUsername())
         .withPassword(POSTGRES.getPassword());
      backend.setConnectionProvider(new QCodeReference(C3P0PooledConnectionProvider.class));
      backend.setConnectionPoolSettings(new ConnectionPoolSettings().withInitialPoolSize(1).withMinPoolSize(1).withMaxPoolSize(1));

      qInstance = new QInstance();
      qInstance.addBackend(backend);
      qInstance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withName("mock").withType(QAuthenticationType.MOCK));

      config = new SqlWorkbenchQBitConfig().withBackendName(BACKEND).withTableMetaDataCustomizer(new BaseSqlWorkbenchTest.SnakeCaseCustomizer()).withRegisterRoutes(false);
      new SqlWorkbenchQBitProducer().withQBitConfig(config).produce(qInstance);
      QContext.init(qInstance, BaseSqlWorkbenchTest.TestSessionCustomizer.sessionFor("writer-1"));
      engine = new SqlExecutionEngine(qInstance, SqlWorkbenchRuntime.get(qInstance));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      QContext.clear();
      ConnectionManager.resetConnectionProviders();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Caller reader()
   {
      return (new Caller("reader-1", "Reader", false, false, null));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Caller writer()
   {
      return (new Caller("writer-1", "Writer", true, true, null));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private Map<String, Object> read(String sql) throws Exception
   {
      return (engine.execute(ExecutionRequest.readOnly(BACKEND, sql, Map.of()), reader()));
   }



   /*******************************************************************************
    ** The database itself refuses writes in a workbench read-only session,
    ** even for SQL the classifier would never let through.
    *******************************************************************************/
   @Test
   void testReadOnlyTransactionIsEnforcedByPostgres() throws Exception
   {
      Connection connection = ConnectionManager.getConnection((PostgreSQLBackendMetaData) qInstance.getBackend(BACKEND));
      try(SessionGuard guard = new SessionGuard(connection, SqlDialect.POSTGRES, true, 5).begin(); Statement statement = guard.getConnection().createStatement())
      {
         assertThatThrownBy(() -> statement.execute("INSERT INTO person (first_name) VALUES ('nope')"))
            .isInstanceOf(SQLException.class).hasMessageContaining("read-only transaction");
      }
      assertThat(QueryCount.of("SELECT count(*) FROM person WHERE first_name = 'nope'")).isZero();

      assertThatThrownBy(() -> read("WITH d AS (DELETE FROM person RETURNING *) SELECT * FROM d")).isInstanceOf(QBadRequestException.class);
      assertThatThrownBy(() -> read("SELECT nextval('person_id_seq')")).isInstanceOf(QBadRequestException.class);
   }



   /*******************************************************************************
    ** Pool of one connection: whatever a session did, the next borrower gets
    ** autocommit on, read-write, and no statement_timeout.
    *******************************************************************************/
   @Test
   void testPooledConnectionIsReturnedClean() throws Exception
   {
      assertThat(read("SELECT count(*) FROM person").get("status")).isEqualTo("SUCCEEDED");
      config.setStatementTimeoutSeconds(1);
      assertThat(read("SELECT pg_sleep(3)").get("status")).isEqualTo("TIMED_OUT");

      try(Connection connection = ConnectionManager.getConnection((PostgreSQLBackendMetaData) qInstance.getBackend(BACKEND)); Statement statement = connection.createStatement())
      {
         assertThat(connection.getAutoCommit()).isTrue();
         assertThat(connection.isReadOnly()).isFalse();
         try(ResultSet rs = statement.executeQuery("SELECT current_setting('transaction_read_only'), current_setting('statement_timeout')"))
         {
            rs.next();
            assertThat(rs.getString(1)).isEqualTo("off");
            assertThat(rs.getString(2)).isEqualTo("0");
         }
         statement.execute("INSERT INTO person (first_name) VALUES ('after')");
         statement.execute("DELETE FROM person WHERE first_name = 'after'");
      }

      List<SqlWorkbenchExecution> audits = SystemUser.call(() -> QueryAction.execute(SqlWorkbenchExecution.TABLE_NAME, SqlWorkbenchExecution.class, new QQueryFilter()));
      assertThat(audits).extracting(SqlWorkbenchExecution::getStatus).contains("SUCCEEDED", "TIMED_OUT");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testCancel() throws Exception
   {
      SqlWorkbenchRuntime runtime = SqlWorkbenchRuntime.get(qInstance);
      var captured = QContext.capture();
      CompletableFuture<Map<String, Object>> future = CompletableFuture.supplyAsync(() ->
      {
         try
         {
            QContext.init(captured);
            return (read("SELECT pg_sleep(20)"));
         }
         catch(Exception e)
         {
            throw (new RuntimeException(e));
         }
      });

      String uuid = null;
      for(int i = 0; i < 100 && uuid == null; i++)
      {
         Thread.sleep(100);
         uuid = QueryCount.string("SELECT execution_uuid FROM sql_workbench_execution WHERE status = 'RUNNING'");
      }
      assertThat(uuid).isNotNull();
      Thread.sleep(300);
      assertThat(engine.cancel(uuid, reader())).isTrue();
      assertThat(future.get(10, TimeUnit.SECONDS).get("status")).isEqualTo("CANCELLED");
      assertThat(runtime.getRunningStatements().size()).isZero();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testReadOnlyCredentials() throws Exception
   {
      config.withReadOnlyCredentials(BACKEND, new SqlWorkbenchCredentials().withUsername("sqlwb_ro").withPassword("ro-pass"));
      assertThat(read("SELECT current_user").get("rows").toString()).contains("sqlwb_ro");
      assertThat(read("SELECT count(*) FROM person").get("status")).isEqualTo("SUCCEEDED");

      Map<String, Object> denied = read("SELECT * FROM sql_workbench_execution");
      assertThat(denied.get("status")).isEqualTo("FAILED");
      assertThat((String) denied.get("error")).containsIgnoringCase("permission denied");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testWriteAndTransactionalDdl() throws Exception
   {
      String              sql     = "UPDATE person SET salary = salary + 1 WHERE salary IS NOT NULL";
      Map<String, Object> preview = engine.execute(new ExecutionRequest(BACKEND, sql, Map.of(), true, null, null, null, null, false), writer());
      assertThat(preview.get("totalAffectedRows")).isEqualTo(2L);
      Map<String, Object> commit = engine.execute(new ExecutionRequest(BACKEND, sql, Map.of(), true, null, null, null, (String) preview.get("confirmToken"), false), writer());
      assertThat(commit.get("status")).isEqualTo("SUCCEEDED");
      assertThat(QueryCount.of("SELECT count(*) FROM person WHERE salary = 25001")).isEqualTo(1);
      engine.execute(new ExecutionRequest(BACKEND, "UPDATE person SET salary = salary - 1 WHERE salary IS NOT NULL", Map.of(), true, null, null, null, null, false), writer());

      String              ddl        = "CREATE TABLE scratch (id INT); INSERT INTO scratch VALUES (1), (2)";
      Map<String, Object> ddlPreview = engine.execute(new ExecutionRequest(BACKEND, ddl, Map.of(), true, null, null, null, null, false), writer());
      assertThat(ddlPreview.get("dryRunSupported")).isEqualTo(true);
      assertThat(ddlPreview.get("totalAffectedRows")).isEqualTo(2L);
      assertThat(QueryCount.of("SELECT count(*) FROM information_schema.tables WHERE table_name = 'scratch'")).isZero();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void testExplainTypesSchemaAndNotebooks() throws Exception
   {
      Map<String, Object> explain = engine.explain(BACKEND, "SELECT * FROM person WHERE id = :id", Map.of("id", 1), false, reader());
      Map<String, Object> plan    = (Map<String, Object>) explain.get("plan");
      assertThat(plan.get("label")).isNotNull();
      assertThat(plan.get("totalCost")).isNotNull();

      Map<String, Object> analyzed = engine.explain(BACKEND, "DELETE FROM person WHERE id = 1", Map.of(), true, writer());
      assertThat(((Map<String, Object>) analyzed.get("plan")).get("actualTimeMs")).isNotNull();
      assertThat(QueryCount.of("SELECT count(*) FROM person WHERE id = 1")).isEqualTo(1);

      Map<String, Object> typed = engine.execute(ExecutionRequest.readOnly(BACKEND, "SELECT tags, doc, ref, seen FROM person WHERE seen > :since", Map.of("since", "2025-01-01")), reader());
      List<Map<String, Object>> columns = (List<Map<String, Object>>) typed.get("columns");
      assertThat(columns).extracting(c -> c.get("kind")).containsExactly("array", "json", "other", "datetime");
      List<Object> row = ((List<List<Object>>) typed.get("rows")).get(0);
      assertThat(row.get(0)).isEqualTo(List.of("a", "b"));
      assertThat(row.get(1)).isEqualTo("{\"k\": 1}");
      assertThat(row.get(2)).isEqualTo("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
      assertThat(java.time.OffsetDateTime.parse((String) row.get(3)).toInstant()).isEqualTo(java.time.Instant.parse("2026-01-01T10:00:00Z"));

      Map<String, Object> schema = new SchemaIntrospector(qInstance, SqlWorkbenchRuntime.get(qInstance), engine.getBackends()).describe(BACKEND, true);
      assertThat(schema.get("dialect")).isEqualTo("postgresql");
      assertThat(schema.toString()).contains("name=public").contains("name=person").contains("first_name").doesNotContain("pg_catalog");

      NotebookService notebooks = new NotebookService(SqlWorkbenchRuntime.get(qInstance));
      Integer id = (Integer) notebooks.create(Map.of("title", "PG", "cells", List.of(Map.of("cellType", "SQL", "content", "SELECT 1"))), writer()).get("id");
      notebooks.update(id, Map.of("title", "PG 2"), writer());
      assertThat(notebooks.versions(id, writer())).hasSize(2);
      notebooks.delete(id, writer());
   }



   /*******************************************************************************
    ** One-value queries against the container as the admin user.
    *******************************************************************************/
   static class QueryCount
   {
      /*******************************************************************************
       ** First column of the first row as a string, or null if no rows.
       *******************************************************************************/
      static String string(String sql) throws SQLException
      {
         try(Connection connection = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql))
         {
            return (rs.next() ? rs.getString(1) : null);
         }
      }




      /*******************************************************************************
       **
       *******************************************************************************/
      static int of(String sql) throws SQLException
      {
         try(Connection connection = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql))
         {
            rs.next();
            return (rs.getInt(1));
         }
      }
   }


   /*******************************************************************************
    ** The record locks on PostgreSQL: owner / user share / library reads
    ** through the core actions, one row per notebook even when it matches
    ** several shares (SELECT DISTINCT / COUNT(DISTINCT) over TEXT columns),
    ** and a reader cannot write a shared notebook's children.
    *******************************************************************************/
   @Test
   void testRecordLocksOnPostgres() throws Exception
   {
      NotebookService notebooks = new NotebookService(SqlWorkbenchRuntime.get(qInstance));
      login("writer-1");
      Integer both     = (Integer) notebooks.create(Map.of("title", "Both", "description", "long text", "cells", List.of(Map.of("cellType", "SQL", "content", "SELECT 1"))), writer()).get("id");
      Integer personal = (Integer) notebooks.create(Map.of("title", "Personal", "cells", List.of(Map.of("cellType", "SQL", "content", "SELECT 2"))), writer()).get("id");
      notebooks.publish(both, true, writer());
      QRecord share = new InsertAction().execute(new InsertInput(SqlWorkbenchNotebookShare.TABLE_NAME).withRecord(new QRecord()
         .withValue("notebookId", both).withValue("audienceType", "USER").withValue("audienceId", "reader-2"))).getRecords().get(0);
      assertThat(share.getErrors()).isNullOrEmpty();

      login("reader-2");
      assertThat(QueryAction.execute(SqlWorkbenchNotebook.TABLE_NAME, new QQueryFilter())).extracting(r -> r.getValueInteger("id")).containsExactly(both);
      assertThat(new CountAction().execute(new CountInput(SqlWorkbenchNotebook.TABLE_NAME)).getCount()).isEqualTo(1);
      assertThat(QueryAction.execute(SqlWorkbenchCell.TABLE_NAME, new QQueryFilter())).hasSize(1);
      assertThat(new CountAction().execute(new CountInput(SqlWorkbenchCell.TABLE_NAME)).getCount()).isEqualTo(1);
      assertThat(QueryAction.execute(SqlWorkbenchNotebookShare.TABLE_NAME, new QQueryFilter())).hasSize(2);
      assertThat(new InsertAction().execute(new InsertInput(SqlWorkbenchCell.TABLE_NAME).withRecord(new QRecord().withValue("notebookId", both).withValue("content", "x")))
         .getRecords().get(0).getErrors()).isNotEmpty();
      assertThat(new InsertAction().execute(new InsertInput(SqlWorkbenchCell.TABLE_NAME).withRecord(new QRecord().withValue("notebookId", personal).withValue("content", "x")))
         .getRecords().get(0).getErrors()).isNotEmpty();

      login("admin-1");
      assertThat(new CountAction().execute(new CountInput(SqlWorkbenchNotebook.TABLE_NAME)).getCount()).isEqualTo(2);

      login("writer-1");
      notebooks.delete(both, writer());
      assertThat(new CountAction().execute(new CountInput(SqlWorkbenchNotebookShare.TABLE_NAME)).getCount()).isZero();
   }



   /*******************************************************************************
    ** The home dashboard over real engine runs on Postgres (aggregates, IN
    ** filters and timestamps through the RDBMS module): a reader sees their
    ** own runs, an admin everyone's, including the committed write.
    *******************************************************************************/
   @Test
   void testHomeDashboardOnPostgres() throws Exception
   {
      NotebookService notebooks = new NotebookService(SqlWorkbenchRuntime.get(qInstance));
      login("writer-1");
      Integer id = (Integer) notebooks.create(Map.of("title", "Dashboard source", "cells", List.of(Map.of("cellType", "SQL", "content", "SELECT 1"))), writer()).get("id");
      engine.execute(new ExecutionRequest(BACKEND, "SELECT count(*) FROM person", Map.of(), false, id, null, null, null, false), writer());
      String              sql     = "UPDATE person SET salary = salary + 0 WHERE salary IS NOT NULL";
      Map<String, Object> preview = engine.execute(new ExecutionRequest(BACKEND, sql, Map.of(), true, null, null, null, null, false), writer());
      engine.execute(new ExecutionRequest(BACKEND, sql, Map.of(), true, null, null, null, (String) preview.get("confirmToken"), false), writer());
      login("reader-1");
      engine.execute(new ExecutionRequest(BACKEND, "SELECT broken FROM nowhere", Map.of(), false, null, null, null, null, false), reader());

      login("admin-1");
      Map<String, String> html = new HashMap<>();
      for(String name : SqlWorkbenchHomeWidgetsMetaDataProducer.homeWidgetNames())
      {
         QWidgetData data = new RenderWidgetAction().execute(new RenderWidgetInput().withWidgetMetaData((QWidgetMetaData) qInstance.getWidget(name))).getWidgetData();
         html.put(name, data instanceof TableData table ? table.getRows().toString() : data instanceof RawHTML raw ? raw.getHtml() : String.valueOf(((ChartData) data).getSublabel()));
      }
      assertThat(html.get(SqlWorkbenchHomeWidgetsMetaDataProducer.WRITES)).contains("UPDATE person", "<strong>2</strong>");
      assertThat(html.get(SqlWorkbenchHomeWidgetsMetaDataProducer.FAILED)).contains("SELECT broken FROM nowhere");
      assertThat(html.get(SqlWorkbenchHomeWidgetsMetaDataProducer.MOST_USED)).contains("Dashboard source");
      assertThat(html.get(SqlWorkbenchHomeWidgetsMetaDataProducer.BY_USER)).contains("writer-1", "reader-1");
      assertThat(html.get(SqlWorkbenchHomeWidgetsMetaDataProducer.SUMMARY)).contains("Writes, 30 Days", "2 rows affected");

      login("reader-1");
      TableData recent = (TableData) new RenderWidgetAction().execute(new RenderWidgetInput()
         .withWidgetMetaData((QWidgetMetaData) qInstance.getWidget(SqlWorkbenchHomeWidgetsMetaDataProducer.RECENT_RUNS))).getWidgetData();
      assertThat(recent.getRows().toString()).contains("SELECT broken").doesNotContain("UPDATE person");
   }



   /*******************************************************************************
    ** Log in with the workbench security keys.
    *******************************************************************************/
   private void login(String userId)
   {
      QSession session = BaseSqlWorkbenchTest.TestSessionCustomizer.sessionFor(userId);
      QContext.init(qInstance, session);
      SqlWorkbenchSecurity.applyToSession(config, session, List.of());
   }

}
