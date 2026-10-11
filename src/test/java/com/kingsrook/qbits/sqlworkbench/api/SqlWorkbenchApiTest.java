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


import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QAuthenticationException;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QNotFoundException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qbits.sqlworkbench.BaseSqlWorkbenchTest;
import com.kingsrook.qbits.sqlworkbench.engine.Caller;
import com.kingsrook.qbits.sqlworkbench.engine.CollectedResult;
import com.kingsrook.qbits.sqlworkbench.engine.ColumnInfo;
import com.kingsrook.qbits.sqlworkbench.engine.ResultCache;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** SqlWorkbenchApi: permissions, capabilities, paging, history, export.
 *******************************************************************************/
class SqlWorkbenchApiTest extends BaseSqlWorkbenchTest
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testCallerPermissions() throws Exception
   {
      loginAs("nobody");
      assertThatThrownBy(() -> new SqlWorkbenchApi().caller("1.2.3.4")).isInstanceOf(QPermissionDeniedException.class);

      loginAs(READER);
      Caller reader = new SqlWorkbenchApi().caller("1.2.3.4");
      assertThat(reader.userId()).isEqualTo(READER);
      assertThat(reader.canWrite()).isFalse();
      assertThat(reader.canShare()).isFalse();

      loginAs(WRITER);
      Caller writer = new SqlWorkbenchApi().caller(null);
      assertThat(writer.canWrite()).isTrue();
      assertThat(writer.canShare()).isTrue();

      config.setWriteModeEnabled(false);
      assertThat(new SqlWorkbenchApi().caller(null).canWrite()).isFalse();

      QContext.init(qInstance, null);
      assertThatThrownBy(() -> new SqlWorkbenchApi().caller(null)).isInstanceOf(QAuthenticationException.class);

      assertThatThrownBy(() -> com.kingsrook.qbits.sqlworkbench.SqlWorkbenchRuntime.get(new QInstance())).isInstanceOf(QNotFoundException.class);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void testCapabilitiesAndSchema() throws Exception
   {
      SqlWorkbenchApi     api          = new SqlWorkbenchApi();
      Map<String, Object> capabilities = api.capabilities(caller(READER));
      assertThat(capabilities.get("canWrite")).isEqualTo(false);
      List<Map<String, Object>> backends = (List<Map<String, Object>>) capabilities.get("backends");
      assertThat(backends).hasSize(1);
      assertThat(backends.get(0)).containsEntry("name", BACKEND_NAME).containsEntry("dialect", "h2").containsEntry("readOnlyEnforced", false);
      assertThat(capabilities.toString()).doesNotContain("username").doesNotContain("password").doesNotContain("jdbc:");
      assertThat((Map<String, Object>) capabilities.get("limits")).containsEntry("maxRows", 10_000);

      Map<String, Object> schema = api.schema(caller(READER), BACKEND_NAME, false);
      assertThat(schema.get("dialect")).isEqualTo("h2");
      assertThat(schema.toString()).contains("sql_workbench_notebook").contains("first_name");
      List<Map<String, Object>> qqqTables = (List<Map<String, Object>>) schema.get("qqqTables");
      assertThat(qqqTables).anySatisfy(t ->
      {
         assertThat(t.get("name")).isEqualTo("person");
         assertThat(t.toString()).contains("sqlName=first_name");
      });
      assertThat(qqqTables).anySatisfy(t -> assertThat(t.get("sqlName")).isEqualTo("sql_workbench_execution"));
      assertThat((List<String>) schema.get("keywords")).contains("SELECT");
      assertThat((List<String>) schema.get("functions")).contains("count");

      ////////////////////////////////////////////
      // cached until refresh                   //
      ////////////////////////////////////////////
      assertThat(api.schema(caller(READER), BACKEND_NAME, false)).isSameAs(schema);
      assertThat(api.schema(caller(READER), BACKEND_NAME, true)).isNotSameAs(schema);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void testExecuteRowsHistoryExport() throws Exception
   {
      SqlWorkbenchApi     api      = new SqlWorkbenchApi();
      Map<String, Object> response = api.execute(caller(READER), Map.of("backendName", BACKEND_NAME, "sql", "SELECT id, first_name, salary FROM person WHERE salary > :s",
         "params", Map.of("s", new BigDecimal("1")), "pageSize", 2));
      String uuid = (String) response.get("executionUuid");
      assertThat((List<?>) response.get("rows")).hasSize(2);

      Map<String, Object> page = api.rows(caller(READER), uuid, 0, 10, 2, "desc");
      List<List<Object>>  rows = (List<List<Object>>) page.get("rows");
      assertThat(rows).hasSize(4);
      assertThat(rows.get(0).get(1)).isEqualTo("Tyler");
      assertThat(api.rows(caller(READER), uuid, 3, null, null, null).get("rows")).asList().hasSize(1);
      assertThatThrownBy(() -> api.rows(caller(OTHER_READER), uuid, 0, 10, null, null)).isInstanceOf(QNotFoundException.class);

      api.explain(caller(READER), Map.of("backendName", BACKEND_NAME, "sql", "SELECT 1"));
      assertThat(api.cancel(caller(READER), uuid).get("cancelled")).isEqualTo(false);

      List<Map<String, Object>> history = (List<Map<String, Object>>) api.history(caller(READER), 10, null).get("executions");
      assertThat(history).hasSize(2);
      assertThat(history.get(0).get("mode")).isEqualTo("EXPLAIN");
      assertThat((List<?>) api.history(caller(READER), null, "other").get("executions")).isEmpty();
      assertThat((List<?>) api.history(caller(OTHER_READER), 10, null).get("executions")).isEmpty();

      ResultCache.Entry entry = api.exportable(caller(READER), uuid);
      for(ResultExporter.Format format : ResultExporter.Format.values())
      {
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         ResultExporter.write(entry.result(), format, out);
         assertThat(out.size()).isGreaterThan(10);
         if(format == ResultExporter.Format.CSV)
         {
            assertThat(out.toString(StandardCharsets.UTF_8)).contains("id,first_name,salary").contains("Darin");
         }
      }

      assertThatThrownBy(() -> api.execute(caller(READER), Map.of("sql", "SELECT 1"))).isInstanceOf(QBadRequestException.class);
      assertThatThrownBy(() -> api.execute(caller(READER), Map.of("backendName", BACKEND_NAME, "sql", "SELECT 1", "params", "x"))).isInstanceOf(QBadRequestException.class);
      assertThatThrownBy(() -> api.execute(caller(READER), Map.of("backendName", BACKEND_NAME, "sql", "SELECT 1", "pageSize", "x"))).isInstanceOf(QBadRequestException.class);
      ////////////////////////////////////////////////////////////
      // ids are normalized at the edge: any case finds the run //
      ////////////////////////////////////////////////////////////
      String upper = uuid.toUpperCase();
      assertThat(api.rows(caller(READER), upper, 0, 10, null, null).get("executionUuid")).isEqualTo(uuid);
      assertThat(api.exportable(caller(READER), " " + upper + " ")).isNotNull();
      assertThat(api.cancel(caller(READER), upper).get("cancelled")).isEqualTo(false);
      assertThat(SqlWorkbenchApi.normalizeUuid(null)).isNull();

      String clientUuid = "9B2C7A10-5E1F-4C3D-8A2B-1C0D9E8F7A6B";
      Map<String, Object> mine = api.execute(caller(READER), Map.of("backendName", BACKEND_NAME, "sql", "SELECT 1", "executionUuid", clientUuid));
      assertThat(mine.get("executionUuid")).isEqualTo(clientUuid.toLowerCase());
      assertThat(api.rows(caller(READER), clientUuid, 0, 10, null, null).get("rowCount")).isEqualTo(1);
      assertThatThrownBy(() -> api.execute(caller(READER), Map.of("backendName", BACKEND_NAME, "sql", "SELECT 1", "executionUuid", clientUuid.toLowerCase())))
         .isInstanceOf(com.kingsrook.qbits.sqlworkbench.engine.DuplicateExecutionException.class);

      assertThat(SqlWorkbenchApi.intOrNull("12")).isEqualTo(12);
      assertThat(SqlWorkbenchApi.intOrNull("")).isNull();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testSnapshotThroughApi() throws Exception
   {
      SqlWorkbenchApi     api      = new SqlWorkbenchApi();
      Integer             notebook = (Integer) api.notebooks().create(Map.of("title", "N"), caller(READER)).get("id");
      Map<String, Object> response = api.execute(caller(READER), Map.of("backendName", BACKEND_NAME, "sql", "SELECT 1 AS one"));
      Map<String, Object> snapshot = api.createSnapshot(caller(READER), notebook, Map.of("executionUuid", response.get("executionUuid")));
      assertThat(snapshot.get("rowCount")).isEqualTo(1);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testExporterDetails() throws Exception
   {
      assertThat(ResultExporter.csvLine(java.util.Arrays.asList("a,b", null, 1, true, "say \"hi\"", List.of(1, 2))))
         .isEqualTo("\"a,b\",,1,true,\"say \"\"hi\"\"\",\"[1,2]\"\r\n");
      assertThat(ResultExporter.uniqueLabels(List.of(new ColumnInfo("a", "a", "int", 4, "number"), new ColumnInfo("a", "a", "int", 4, "number"), new ColumnInfo("b", null, "int", 4, "number"))))
         .containsExactly("a", "a_2", "b");
      assertThatThrownBy(() -> ResultExporter.Format.of("pdf")).isInstanceOf(QBadRequestException.class);
      assertThat(ResultExporter.Format.of("XLSX").getExtension()).isEqualTo("xlsx");

      CollectedResult       result = new CollectedResult(List.of(new ColumnInfo("j", "j", "json", 1111, "json"), new ColumnInfo("n", "n", "int", 4, "number")),
         List.of(java.util.Arrays.asList(Map.of("$binary", true), 5), java.util.Arrays.asList(null, null)), false, null, 0);
      ByteArrayOutputStream json   = new ByteArrayOutputStream();
      ResultExporter.write(result, ResultExporter.Format.JSON, json);
      assertThat(json.toString(StandardCharsets.UTF_8)).contains("\"n\":5");
      ByteArrayOutputStream xlsx = new ByteArrayOutputStream();
      ResultExporter.write(result, ResultExporter.Format.XLSX, xlsx);
      assertThat(xlsx.size()).isGreaterThan(100);
   }

}
