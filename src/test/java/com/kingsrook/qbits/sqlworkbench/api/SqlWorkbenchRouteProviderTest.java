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
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qbits.sqlworkbench.BaseSqlWorkbenchTest;
import com.kingsrook.qqq.middleware.javalin.QJavalinMetaData;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The routes over real HTTP (Javalin on a random port, MOCK authentication
 ** with the sessionId cookie choosing the test user).
 *******************************************************************************/
class SqlWorkbenchRouteProviderTest extends BaseSqlWorkbenchTest
{
   private Javalin    javalin;
   private String     base;
   private HttpClient client = HttpClient.newHttpClient();



   /*******************************************************************************
    **
    *******************************************************************************/
   @BeforeEach
   void startServer()
   {
      QContext.clear();
      SqlWorkbenchRouteProvider provider = new SqlWorkbenchRouteProvider();
      provider.setQInstance(qInstance);
      javalin = Javalin.create(provider::acceptJavalinConfig).start(0);
      base = "http://localhost:" + javalin.port() + SqlWorkbenchRouteProvider.BASE_PATH;
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
      com.kingsrook.qbits.sqlworkbench.api.SqlWorkbenchRoutes.register(qInstance);
      List<QCodeReference> references = QJavalinMetaData.of(qInstance).getAdditionalRouteProviderReferences();
      assertThat(references).filteredOn(r -> r.getName().equals(SqlWorkbenchRouteProvider.class.getName())).hasSize(1);
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

}
