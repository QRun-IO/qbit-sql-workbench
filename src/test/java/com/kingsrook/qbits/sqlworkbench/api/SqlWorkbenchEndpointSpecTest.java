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


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.kingsrook.qbits.sqlworkbench.BaseSqlWorkbenchTest;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchRuntime;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.instances.AbstractQQQApplication;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.middleware.javalin.QApplicationJavalinServer;
import com.kingsrook.qqq.middleware.javalin.specs.AbstractEndpointSpec;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.MiddlewareSpecExtensionsMetaData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The routes over real HTTP (Javalin on a random port, MOCK authentication
 ** with the sessionId cookie choosing the test user).
 *******************************************************************************/
class SqlWorkbenchEndpointSpecTest extends BaseSqlWorkbenchTest
{
   private QApplicationJavalinServer javalin;
   private String     base;
   private HttpClient client = HttpClient.newHttpClient();



   /*******************************************************************************
    **
    *******************************************************************************/
   @BeforeEach
   void startServer() throws Exception
   {
      QContext.clear();
      javalin = new QApplicationJavalinServer(new AbstractQQQApplication()
      {
         /*******************************************************************************
          ** Serve the same instance and runtime prepared by the test fixture.
          *******************************************************************************/
         @Override
         public QInstance defineQInstance()
         {
            return (qInstance);
         }
      }).withPort(0).withServeFrontendNext(false).withServeFrontendMaterialDashboard(false)
         .withServeLegacyUnversionedMiddlewareAPI(false).withStrictUiApiRoutes(true);
      javalin.start();
      base = "http://localhost:" + javalin.getBoundPort() + "/qqq/v1/sql-workbench";
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void stopServer()
   {
      javalin.stop();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private HttpResponse<String> send(String method, String path, String user, String body, String contentType) throws Exception
   {
      HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).header("Cookie", "sessionId=" + user);
      if(contentType != null)
      {
         builder.header("Content-Type", contentType);
      }
      builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
      return (client.send(builder.build(), HttpResponse.BodyHandlers.ofString()));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Map<String, Object> json(HttpResponse<String> response) throws Exception
   {
      return (JsonUtils.toObject(response.body(), new TypeReference<Map<String, Object>>() {}));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testRegistration()
   {
      SqlWorkbenchEndpoints.register(qInstance);
      var extensions = MiddlewareSpecExtensionsMetaData.ofOrWithNew(qInstance).getExtensions();
      assertThat(extensions).containsOnlyKeys("sql-workbench");
      assertThat(extensions.get("sql-workbench").getSpecs()).hasSize(21);
      assertThat(javalin.isStrictUiApiRoutes()).isTrue();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void testEndToEnd() throws Exception
   {
      assertThat(send("GET", "/capabilities", "nobody", null, null).statusCode()).isEqualTo(403);
      assertThat(send("GET", "/capabilities", READER, null, null).statusCode()).isEqualTo(200);

      ////////////////////////////////////////////////////////////
      // JSON only - a form post (CSRF shape) is refused        //
      ////////////////////////////////////////////////////////////
      assertThat(send("POST", "/execute", READER, "backendName=h2&sql=SELECT+1", "application/x-www-form-urlencoded").statusCode()).isEqualTo(400);
      assertThat(send("POST", "/execute", READER, "not json", "application/json").statusCode()).isEqualTo(400);

      HttpResponse<String> run = send("POST", "/execute", READER, "{\"backendName\":\"h2\",\"sql\":\"SELECT id, salary FROM person WHERE salary > :s ORDER BY id\",\"params\":{\"s\":25000.25}}", "application/json");
      assertThat(run.statusCode()).isEqualTo(200);
      Map<String, Object> result = json(run);
      assertThat(result.get("status")).isEqualTo("SUCCEEDED");
      assertThat(result.get("rowCount")).isEqualTo(2);
      String uuid = (String) result.get("executionUuid");

      assertThat(json(send("GET", "/executions/" + uuid + "/rows?offset=1&limit=1&sort=0&dir=desc", READER, null, null)).get("rows")).asList().hasSize(1);
      assertThat(send("GET", "/executions/" + uuid + "/rows?offset=x", READER, null, null).statusCode()).isEqualTo(400);
      assertThat(send("GET", "/executions/" + uuid + "/rows", OTHER_READER, null, null).statusCode()).isEqualTo(404);

      HttpResponse<String> csv = send("GET", "/executions/" + uuid + "/export?format=csv", READER, null, null);
      assertThat(csv.statusCode()).isEqualTo(200);
      assertThat(csv.headers().firstValue("Content-Disposition").orElse("")).contains("attachment").contains(".csv");
      assertThat(csv.body()).contains("id,salary");
      assertThat(send("GET", "/executions/" + uuid + "/export?format=pdf", READER, null, null).statusCode()).isEqualTo(400);
      assertThat(send("GET", "/executions/" + uuid + "/export", OTHER_READER, null, null).statusCode()).isEqualTo(404);

      assertThat(send("POST", "/execute", READER, "{\"backendName\":\"h2\",\"sql\":\"DELETE FROM person\",\"mode\":\"WRITE\"}", "application/json").statusCode()).isEqualTo(403);
      assertThat(send("POST", "/execute", READER, "{\"backendName\":\"nope\",\"sql\":\"SELECT 1\"}", "application/json").statusCode()).isEqualTo(404);
      assertThat(json(send("POST", "/explain", READER, "{\"backendName\":\"h2\",\"sql\":\"SELECT 1\"}", "application/json")).get("status")).isEqualTo("SUCCEEDED");
      assertThat(json(send("POST", "/executions/" + uuid + "/cancel", READER, "{}", "application/json")).get("cancelled")).isEqualTo(false);
      assertThat((List<?>) json(send("GET", "/history?limit=5", READER, null, null)).get("executions")).hasSize(3);
      assertThat(json(send("GET", "/schema/h2", READER, null, null)).get("dialect")).isEqualTo("h2");

      ///////////////////////////////////////////////////////////////////
      // a client-chosen id: any case on the paths, single-use (409)   //
      ///////////////////////////////////////////////////////////////////
      String clientUuid = "3F2504E0-4F89-11D3-9A0C-0305E82C3301";
      String body       = "{\"backendName\":\"h2\",\"sql\":\"SELECT 1 AS one\",\"executionUuid\":\"" + clientUuid + "\"}";
      assertThat(json(send("POST", "/execute", READER, body, "application/json")).get("executionUuid")).isEqualTo(clientUuid.toLowerCase());
      assertThat(send("GET", "/executions/" + clientUuid + "/rows", READER, null, null).statusCode()).isEqualTo(200);
      assertThat(send("GET", "/executions/" + clientUuid + "/export", READER, null, null).statusCode()).isEqualTo(200);
      HttpResponse<String> again = send("POST", "/execute", READER, body, "application/json");
      assertThat(again.statusCode()).isEqualTo(409);
      assertThat(again.body()).contains("already used");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testNotebookRoutes() throws Exception
   {
      Map<String, Object> created = json(send("POST", "/notebooks", WRITER, "{\"title\":\"N1\",\"cells\":[{\"cellKey\":\"a\",\"cellType\":\"SQL\",\"content\":\"SELECT 1\"}]}", "application/json"));
      Integer id = (Integer) created.get("id");

      assertThat((List<?>) json(send("GET", "/notebooks?scope=mine", WRITER, null, null)).get("notebooks")).hasSize(1);
      assertThat(json(send("GET", "/notebooks/" + id, WRITER, null, null)).get("title")).isEqualTo("N1");
      assertThat(send("GET", "/notebooks/" + id, READER, null, null).statusCode()).isEqualTo(404);
      assertThat(send("GET", "/notebooks/abc", WRITER, null, null).statusCode()).isEqualTo(400);

      assertThat(json(send("PUT", "/notebooks/" + id, WRITER, "{\"title\":\"N2\"}", "application/json")).get("currentVersion")).isEqualTo(2);
      assertThat(json(send("POST", "/notebooks/" + id + "/publish", WRITER, "{\"shared\":true}", "application/json")).get("visibility")).isEqualTo("SHARED");
      assertThat((List<?>) json(send("GET", "/notebooks?scope=shared", READER, null, null)).get("notebooks")).hasSize(1);
      assertThat(json(send("POST", "/notebooks/" + id + "/duplicate", READER, "{}", "application/json")).get("ownerUserId")).isEqualTo(READER);
      assertThat((List<?>) json(send("GET", "/notebooks/" + id + "/versions", WRITER, null, null)).get("versions")).hasSize(2);
      assertThat(json(send("GET", "/notebooks/" + id + "/versions/1", WRITER, null, null)).get("versionNumber")).isEqualTo(1);
      assertThat(json(send("POST", "/notebooks/" + id + "/versions/1/restore", WRITER, "{}", "application/json")).get("title")).isEqualTo("N1");

      String uuid = (String) json(send("POST", "/execute", WRITER, "{\"backendName\":\"h2\",\"sql\":\"SELECT 1 AS one\"}", "application/json")).get("executionUuid");
      Map<String, Object> snapshot = json(send("POST", "/notebooks/" + id + "/snapshots", WRITER, "{\"executionUuid\":\"" + uuid + "\",\"cellKey\":\"a\"}", "application/json"));
      assertThat((List<?>) json(send("GET", "/notebooks/" + id + "/snapshots", WRITER, null, null)).get("snapshots")).hasSize(1);
      assertThat(json(send("DELETE", "/notebooks/" + id + "/snapshots/" + snapshot.get("id"), WRITER, null, null)).get("deleted")).isEqualTo(true);

      assertThat(send("DELETE", "/notebooks/" + id, READER, null, null).statusCode()).isEqualTo(403);
      assertThat(json(send("DELETE", "/notebooks/" + id, WRITER, null, null)).get("deleted")).isEqualTo(true);
   }



   /*******************************************************************************
    ** Every spec checks access before parsing bodies, identifiers or formats.
    *******************************************************************************/
   @Test
   void testEveryOperationDeniesMissingAccess() throws Exception
   {
      for(var reference : SqlWorkbenchEndpoints.extension().getSpecs())
      {
         var spec = (AbstractEndpointSpec<?, ?, ?>) Class.forName(reference.getName()).getConstructor().newInstance();
         var operation = spec.defineBasicOperation();
         String path = operation.getPath().substring("/sql-workbench".length()).replaceAll("\\{[^}]+}", "invalid");
         String method = operation.getHttpMethod().name();
         boolean body = method.equals("POST") || method.equals("PUT");
         HttpResponse<String> response = send(method, path + "?format=invalid", "nobody", body ? "not json" : null, body ? "text/plain" : null);
         assertThat(response.statusCode()).as(method + " " + path).isEqualTo(403);
         assertThat(json(response)).containsEntry("error", "Permission denied.");
         HttpRequest unauthenticated = HttpRequest.newBuilder(URI.create(base + path))
            .header("Authorization", "Bearer Deny")
            .method(method, HttpRequest.BodyPublishers.noBody()).build();
         assertThat(client.send(unauthenticated, HttpResponse.BodyHandlers.ofString()).statusCode()).as(method + " authentication").isEqualTo(401);
      }
      assertThat(json(send("GET", "/history", READER, null, null)).get("executions")).asList().isEmpty();
   }



   /*******************************************************************************
    ** Write gates, read-only classification and concurrency limits remain audited.
    *******************************************************************************/
   @Test
   void testExecutionGatesAndLimits() throws Exception
   {
      String write = "{\"backendName\":\"h2\",\"sql\":\"DELETE FROM person\",\"mode\":\"WRITE\"}";
      assertThat(send("POST", "/execute", READER, write, "application/json").statusCode()).isEqualTo(403);
      config.setWriteModeEnabled(false);
      assertThat(send("POST", "/execute", WRITER, write, "application/json").statusCode()).isEqualTo(403);
      String analyze = "{\"backendName\":\"h2\",\"sql\":\"SELECT 1\",\"analyze\":true}";
      assertThat(send("POST", "/explain", WRITER, analyze, "application/json").statusCode()).isEqualTo(403);
      config.setWriteModeEnabled(true);
      assertThat(send("POST", "/explain", READER, analyze, "application/json").statusCode()).isEqualTo(403);
      assertThat(send("POST", "/execute", WRITER, write.replace(",\"mode\":\"WRITE\"", ""), "application/json").statusCode()).isEqualTo(400);

      config.setMaxConcurrentExecutionsPerUser(1);
      var running = SqlWorkbenchRuntime.get(qInstance).getRunningStatements();
      String uuid = "3f2504e0-4f89-11d3-9a0c-0305e82c3302";
      var registration = running.register(uuid, READER, 1);
      try
      {
         HttpResponse<String> limited = send("POST", "/execute", READER, "{\"backendName\":\"h2\",\"sql\":\"SELECT 1\"}", "application/json");
         assertThat(limited.statusCode()).isEqualTo(400);
         assertThat(limited.body()).contains("executions running");
         assertThat(json(send("POST", "/executions/" + uuid + "/cancel", OTHER_READER, "{}", "application/json"))).containsEntry("cancelled", false);
         assertThat(registration.isCancelled()).isFalse();
         assertThat(json(send("POST", "/executions/" + uuid + "/cancel", READER, "{}", "application/json"))).containsEntry("cancelled", true);
      }
      finally
      {
         running.unregister(uuid, registration);
      }
      List<?> history = (List<?>) json(send("GET", "/history", READER, null, null)).get("executions");
      assertThat(history).hasSize(3).allSatisfy(entry -> assertThat(((Map<?, ?>) entry).get("status")).isEqualTo("REJECTED"));
      Map<String, Object> count = json(send("POST", "/execute", READER, "{\"backendName\":\"h2\",\"sql\":\"SELECT COUNT(*) AS n FROM person\"}", "application/json"));
      assertThat(count.get("rows")).isEqualTo(List.of(List.of(5)));
   }



   /*******************************************************************************
    ** Share permission, ownership and cached-result ownership apply to mutations.
    *******************************************************************************/
   @Test
   void testNotebookMutationDenials() throws Exception
   {
      Integer id = (Integer) json(send("POST", "/notebooks", READER, "{\"title\":\"Private\"}", "application/json")).get("id");
      assertThat(send("POST", "/notebooks/" + id + "/publish", READER, "{\"shared\":true}", "application/json").statusCode()).isEqualTo(403);
      for(String suffix : List.of("", "/versions", "/versions/1", "/snapshots"))
      {
         assertThat(send("GET", "/notebooks/" + id + suffix, OTHER_READER, null, null).statusCode()).isEqualTo(404);
      }
      assertThat(send("PUT", "/notebooks/" + id, OTHER_READER, "{\"title\":\"Stolen\"}", "application/json").statusCode()).isEqualTo(404);
      assertThat(send("POST", "/notebooks/" + id + "/duplicate", OTHER_READER, "{}", "application/json").statusCode()).isEqualTo(404);
      assertThat(send("POST", "/notebooks/" + id + "/versions/1/restore", OTHER_READER, "{}", "application/json").statusCode()).isEqualTo(404);

      Integer shared = (Integer) json(send("POST", "/notebooks", WRITER, "{\"title\":\"Shared\"}", "application/json")).get("id");
      send("POST", "/notebooks/" + shared + "/publish", WRITER, "{\"shared\":true}", "application/json");
      assertThat(send("PUT", "/notebooks/" + shared, READER, "{\"title\":\"Stolen\"}", "application/json").statusCode()).isEqualTo(403);
      assertThat(send("POST", "/notebooks/" + shared + "/versions/1/restore", READER, "{}", "application/json").statusCode()).isEqualTo(403);
      String uuid = (String) json(send("POST", "/execute", WRITER, "{\"backendName\":\"h2\",\"sql\":\"SELECT 1\"}", "application/json")).get("executionUuid");
      String snapshotBody = "{\"executionUuid\":\"" + uuid + "\",\"cellKey\":\"a\"}";
      assertThat(send("POST", "/notebooks/" + id + "/snapshots", READER, snapshotBody, "application/json").statusCode()).isEqualTo(404);
      Integer snapshotId = (Integer) json(send("POST", "/notebooks/" + shared + "/snapshots", WRITER, snapshotBody, "application/json")).get("id");
      assertThat(send("DELETE", "/notebooks/" + shared + "/snapshots/" + snapshotId, READER, null, null).statusCode()).isEqualTo(403);
      assertThat(json(send("GET", "/notebooks/" + id, READER, null, null))).containsEntry("title", "Private");
   }



   /*******************************************************************************
    ** Keep decimal parameters, null bodies, SQL failures and all export formats.
    *******************************************************************************/
   @Test
   void testWireCompatibility() throws Exception
   {
      HttpResponse<String> run = send("POST", "/execute", READER,
         "{\"backendName\":\"h2\",\"sql\":\"SELECT CAST(:n AS DECIMAL(30,20)) AS n\",\"params\":{\"n\":0.12345678901234567890}}", "application/json");
      assertThat(run.statusCode()).isEqualTo(200);
      assertThat(run.body()).contains("0.12345678901234567890");
      String uuid = (String) json(run).get("executionUuid");
      for(String format : List.of("csv", "json", "xlsx"))
      {
         HttpResponse<String> export = send("GET", "/executions/" + uuid + "/export?format=" + format, READER, null, null);
         assertThat(export.statusCode()).isEqualTo(200);
         assertThat(export.headers().firstValue("Cache-Control")).contains("no-store");
         assertThat(export.headers().firstValue("Content-Disposition").orElseThrow()).endsWith("." + format + "\"");
         if(format.equals("json"))
         {
            assertThat(export.body()).contains("0.12345678901234567890");
         }
      }
      assertThat(json(send("POST", "/executions/" + uuid + "/cancel", READER, "null", "application/json"))).containsEntry("cancelled", false);
      assertThat(send("POST", "/execute", READER, "[]", "application/json").statusCode()).isEqualTo(400);
      assertThat(send("POST", "/execute", READER, "{}", null).statusCode()).isEqualTo(400);
      HttpResponse<String> failed = send("POST", "/execute", READER, "{\"backendName\":\"h2\",\"sql\":\"SELECT * FROM nonexistent_table\"}", "application/json");
      assertThat(failed.statusCode()).isEqualTo(200);
      assertThat(json(failed)).containsEntry("status", "FAILED");
      String served = client.send(HttpRequest.newBuilder(URI.create(base.replace("/sql-workbench", "/openapi.yaml"))).build(), HttpResponse.BodyHandlers.ofString()).body();
      assertThat(served).contains("sqlWorkbenchExecute", "sqlWorkbenchExportResult", "SqlWorkbenchNotebookV1");
   }

}
