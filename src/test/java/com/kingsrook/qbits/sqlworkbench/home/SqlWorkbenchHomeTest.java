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

package com.kingsrook.qbits.sqlworkbench.home;


import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.kingsrook.qbits.sqlworkbench.BaseSqlWorkbenchTest;
import com.kingsrook.qbits.sqlworkbench.SystemUser;
import com.kingsrook.qbits.sqlworkbench.metadata.SqlWorkbenchHomeWidgetsMetaDataProducer;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchExecution;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebook;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookShare;
import com.kingsrook.qqq.backend.core.actions.dashboard.RenderWidgetAction;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.ChartData;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.QWidgetData;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.RawHTML;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.TableData;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QUser;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The home dashboard widgets, rendered through RenderWidgetAction in each
 ** viewer's session: real data, record locks (own runs; admins see all),
 ** permission gates, and honest empty states.
 *******************************************************************************/
class SqlWorkbenchHomeTest extends BaseSqlWorkbenchTest
{
   private Integer orders;
   private Integer scratch;



   /*******************************************************************************
    ** writer-1 owns "Orders report" (shared with reader-1) and "Private
    ** scratch"; runs by writer-1 (read, write, failure) and reader-1 (one in
    ** the window, one 40 days ago).
    *******************************************************************************/
   private void seed() throws Exception
   {
      loginAs(WRITER);
      orders = insert(SqlWorkbenchNotebook.TABLE_NAME, Map.of("title", "Orders report", "description", "Daily orders\nsecond line")).getValueInteger("id");
      scratch = insert(SqlWorkbenchNotebook.TABLE_NAME, Map.of("title", "Private scratch")).getValueInteger("id");
      insert(SqlWorkbenchNotebookShare.TABLE_NAME, Map.of("notebookId", orders, "audienceType", "USER", "audienceId", READER));

      Instant now = Instant.now();
      SystemUser.run(() ->
      {
         run(WRITER, "READ_ONLY", "SUCCEEDED", "SELECT * FROM orders WHERE total > 100", orders, 1500, now.minus(Duration.ofMinutes(5)), null, null);
         run(WRITER, "WRITE_COMMIT", "SUCCEEDED", "UPDATE person SET first_name = 'x'", null, 40, now.minus(Duration.ofMinutes(10)), 3, null);
         run(WRITER, "READ_ONLY", "FAILED", "SELEC broken", scratch, 5, now.minus(Duration.ofDays(3)), null, "syntax error at <x>");
         run(READER, "READ_ONLY", "SUCCEEDED", "SELECT count(*) FROM orders", orders, 50, now.minus(Duration.ofDays(1)), null, null);
         run(READER, "READ_ONLY", "SUCCEEDED", "SELECT 'old'", orders, 50, now.minus(Duration.ofDays(40)), null, null);
      });
   }



   /*******************************************************************************
    ** Insert as the session.
    *******************************************************************************/
   private static QRecord insert(String table, Map<String, Serializable> values) throws QException
   {
      QRecord record = new QRecord();
      values.forEach(record::setValue);
      record = new InsertAction().execute(new InsertInput(table).withRecord(record)).getRecords().get(0);
      assertThat(record.getErrors()).isNullOrEmpty();
      return (record);
   }



   /*******************************************************************************
    ** One audit row (as the system user: the audit is append-only).
    *******************************************************************************/
   private static void run(String user, String mode, String status, String sql, Integer notebookId, int durationMs, Instant start, Integer affected, String error) throws QException
   {
      Map<String, Serializable> values = new HashMap<>();
      values.put("executionUuid", UUID.randomUUID().toString());
      values.put("userId", user);
      values.put("userName", "Name of " + user);
      values.put("backendName", BACKEND_NAME);
      values.put("mode", mode);
      values.put("status", status);
      values.put("sqlText", sql);
      values.put("notebookId", notebookId);
      values.put("durationMs", durationMs);
      values.put("startTime", start);
      values.put("endTime", start.plusMillis(durationMs));
      values.put("rowsReturned", affected == null ? 10 : null);
      values.put("rowsAffected", affected);
      values.put("errorMessage", error);
      values.put("createDate", start);
      insert(SqlWorkbenchExecution.TABLE_NAME, values);
   }



   /*******************************************************************************
    ** Render a widget through the core action (widget permission checked).
    *******************************************************************************/
   private QWidgetData render(String widgetName, Map<String, String> params) throws QException
   {
      QWidgetMetaData widget = (QWidgetMetaData) qInstance.getWidget(widgetName);
      return (new RenderWidgetAction().execute(new RenderWidgetInput().withWidgetMetaData(widget).withUrlParams(new HashMap<>(params))).getWidgetData());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private QWidgetData render(String widgetName) throws QException
   {
      return (render(widgetName, Map.of()));
   }



   /*******************************************************************************
    ** All the HTML of a table widget's cells (and its footer).
    *******************************************************************************/
   private static String cells(QWidgetData data)
   {
      StringBuilder html = new StringBuilder();
      for(Map<String, Object> row : ((TableData) data).getRows())
      {
         row.values().forEach(v -> html.append(v).append('|'));
         html.append('\n');
      }
      return (html + String.valueOf(data.getFooterHTML()));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testSummaryForOwnerAndAdmin() throws Exception
   {
      seed();

      loginAs(WRITER);
      String html = ((RawHTML) render(SqlWorkbenchHomeWidgetsMetaDataProducer.SUMMARY)).getHtml();
      assertThat(html).contains("Notebooks", ">2<", "2 yours, 0 shared with you", "Runs, 7 Days", "1 failed",
         "Writes, 30 Days", "3 rows affected", "/app/sqlWorkbenchRunner", "Open the Runner", "your own runs");

      loginAs(READER);
      html = ((RawHTML) render(SqlWorkbenchHomeWidgetsMetaDataProducer.SUMMARY)).getHtml();
      assertThat(html).contains("0 yours, 1 shared with you", "0 failed", "0 rows affected");

      loginAs(ADMIN);
      html = ((RawHTML) render(SqlWorkbenchHomeWidgetsMetaDataProducer.SUMMARY)).getHtml();
      assertThat(html).contains("0 yours, 2 by other users", "workbench admin", "3 rows affected");

      config.setWriteModeEnabled(false);
      html = ((RawHTML) render(SqlWorkbenchHomeWidgetsMetaDataProducer.SUMMARY)).getHtml();
      assertThat(html).contains("write mode is off");
      assertThat(cells(render(SqlWorkbenchHomeWidgetsMetaDataProducer.WRITES))).contains("Write mode is off");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testRunsAreLockedToTheirUserExceptForAdmins() throws Exception
   {
      seed();

      loginAs(WRITER);
      String runs = cells(render(SqlWorkbenchHomeWidgetsMetaDataProducer.RECENT_RUNS));
      assertThat(runs).contains("UPDATE person", "SELEC broken", "Orders report", "Private scratch", "you", "3 rows affected", "10 rows")
         .doesNotContain("count(*)");

      loginAs(READER);
      runs = cells(render(SqlWorkbenchHomeWidgetsMetaDataProducer.RECENT_RUNS));
      assertThat(runs).contains("SELECT count(*) FROM orders", "SELECT &#39;old&#39;", "Orders report").doesNotContain("UPDATE person");

      loginAs(ADMIN);
      runs = cells(render(SqlWorkbenchHomeWidgetsMetaDataProducer.RECENT_RUNS));
      assertThat(runs).contains("UPDATE person", "count(*)", "Name of writer-1", "Name of reader-1");

      TableData byUser = (TableData) render(SqlWorkbenchHomeWidgetsMetaDataProducer.BY_USER);
      assertThat(byUser.getRows()).hasSize(2);
      assertThat(cells(byUser)).contains("Name of writer-1", "writer-1", "Name of reader-1", "last run").doesNotContain("Only your own");
      assertThat(byUser.getColumns()).hasSize(4);

      ///////////////////////////////////////////////////////////////
      // activity by user is admin-only (the widget's permission)  //
      ///////////////////////////////////////////////////////////////
      assertThat(PermissionsHelper.hasWidgetPermission(new RenderWidgetInput(), SqlWorkbenchHomeWidgetsMetaDataProducer.BY_USER)).isTrue();
      loginAs(WRITER);
      assertThat(PermissionsHelper.hasWidgetPermission(new RenderWidgetInput(), SqlWorkbenchHomeWidgetsMetaDataProducer.BY_USER)).isFalse();
      assertThat(PermissionsHelper.hasWidgetPermission(new RenderWidgetInput(), SqlWorkbenchHomeWidgetsMetaDataProducer.SUMMARY)).isTrue();
      SqlWorkbenchHome.Viewer viewer = SqlWorkbenchHome.viewer(config, Instant.now());
      String                  own    = cells(SqlWorkbenchHome.load(SqlWorkbenchHome.KIND_BY_USER, viewer, new RenderWidgetInput()));
      assertThat(own).contains("Only your own runs").doesNotContain("reader-1");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testSlowestFailedWritesAndChart() throws Exception
   {
      seed();
      loginAs(WRITER);

      TableData slowest = (TableData) render(SqlWorkbenchHomeWidgetsMetaDataProducer.SLOWEST);
      assertThat(slowest.getRows()).hasSize(3);
      assertThat(String.valueOf(slowest.getRows().get(0).get("duration"))).contains("1.5 s");
      assertThat(slowest.getLinkURL()).isEqualTo("/app/sql_workbench_execution");

      String failed = cells(render(SqlWorkbenchHomeWidgetsMetaDataProducer.FAILED));
      assertThat(failed).contains("syntax error at &lt;x&gt;", "failed", "/app/sql_workbench_execution/").doesNotContain("UPDATE");

      String writes = cells(render(SqlWorkbenchHomeWidgetsMetaDataProducer.WRITES));
      assertThat(writes).contains("UPDATE person", "<strong>3</strong>", "Write mode is on").doesNotContain("SELECT");

      ChartData chart = (ChartData) render(SqlWorkbenchHomeWidgetsMetaDataProducer.RUNS_PER_DAY);
      assertThat(chart.getChartData().getLabels()).hasSize(30);
      assertThat(chart.getChartData().getDatasets()).extracting(ChartData.Data.Dataset::getLabel).containsExactly("Runs", "Failed", "Writes");
      assertThat(chart.getChartData().getDatasets().get(0).getData().stream().mapToLong(Number::longValue).sum()).isEqualTo(3);
      assertThat(chart.getChartData().getDatasets().get(1).getData().stream().mapToLong(Number::longValue).sum()).isEqualTo(1);
      assertThat(chart.getChartData().getDatasets().get(2).getData().stream().mapToLong(Number::longValue).sum()).isEqualTo(1);
      assertThat(chart.getSublabel()).isEqualTo("3 runs in the last 30 days, by day");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testNotebookWidgets() throws Exception
   {
      seed();

      loginAs(READER);
      TableData recent = (TableData) render(SqlWorkbenchHomeWidgetsMetaDataProducer.RECENT_NOTEBOOKS);
      assertThat(recent.getRows()).hasSize(1);
      assertThat(cells(recent)).contains("Orders report", "Daily orders", "Name of writer-1", "shared", "/app/sqlWorkbenchRunner?notebook=" + orders)
         .doesNotContain("second line", "Private scratch");

      String shared = cells(render(SqlWorkbenchHomeWidgetsMetaDataProducer.SHARED));
      assertThat(shared).contains("Orders report", "reader-1", "Name of writer-1");

      loginAs(ADMIN);
      TableData mostUsed = (TableData) render(SqlWorkbenchHomeWidgetsMetaDataProducer.MOST_USED);
      assertThat(mostUsed.getRows()).hasSize(2);
      assertThat(String.valueOf(mostUsed.getRows().get(0).get("notebook"))).contains("Orders report");
      assertThat(mostUsed.getRows().get(0).get("runs")).isEqualTo("2");
      assertThat(mostUsed.getRows().get(0).get("users")).isEqualTo("2");

      ///////////////////////////////////////////////////////////////////////
      // reader-1's own runs on a notebook they can't read: labeled        //
      // honestly, not hidden                                              //
      ///////////////////////////////////////////////////////////////////////
      SystemUser.run(() -> run(READER, "READ_ONLY", "SUCCEEDED", "SELECT 1", scratch, 5, Instant.now(), null, null));
      loginAs(READER);
      assertThat(cells(render(SqlWorkbenchHomeWidgetsMetaDataProducer.MOST_USED))).contains("Notebook #" + scratch + " (deleted, or not visible to you)");

      QWidgetData runner = render(SqlWorkbenchHomeWidgetsMetaDataProducer.NOTEBOOK_RUNNER, Map.of("id", String.valueOf(orders)));
      assertThat(((RawHTML) runner).getHtml()).contains("/app/sqlWorkbenchRunner?notebook=" + orders, "Open in Runner");
      assertThat(((RawHTML) render(SqlWorkbenchHomeWidgetsMetaDataProducer.NOTEBOOK_RUNNER)).getHtml()).contains("Save the notebook");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testEmptyStatesAndPermissions() throws Exception
   {
      loginAs(READER);
      assertThat(((TableData) render(SqlWorkbenchHomeWidgetsMetaDataProducer.RECENT_NOTEBOOKS)).getNoRowsFoundHTML()).contains("No notebooks yet");
      assertThat(((TableData) render(SqlWorkbenchHomeWidgetsMetaDataProducer.RECENT_RUNS)).getNoRowsFoundHTML()).contains("No runs yet");
      assertThat(((TableData) render(SqlWorkbenchHomeWidgetsMetaDataProducer.WRITES)).getNoRowsFoundHTML()).contains("No writes have been committed");
      assertThat(((TableData) render(SqlWorkbenchHomeWidgetsMetaDataProducer.SHARED)).getNoRowsFoundHTML()).contains("No shared notebooks");
      assertThat(((TableData) render(SqlWorkbenchHomeWidgetsMetaDataProducer.MOST_USED)).getNoRowsFoundHTML()).contains("No notebook runs");
      assertThat(((TableData) render(SqlWorkbenchHomeWidgetsMetaDataProducer.FAILED)).getNoRowsFoundHTML()).contains("No failed runs");
      assertThat(((TableData) render(SqlWorkbenchHomeWidgetsMetaDataProducer.SLOWEST)).getNoRowsFoundHTML()).contains("No completed runs");
      assertThat(((ChartData) render(SqlWorkbenchHomeWidgetsMetaDataProducer.RUNS_PER_DAY)).getSublabel()).isEqualTo("No runs in the last 30 days");

      ////////////////////////////////////////////////////////////////////////
      // no workbench access: every widget is denied (the middleware checks //
      // widget permissions); rendered anyway, the locks show nothing       //
      ////////////////////////////////////////////////////////////////////////
      loginAs("nobody-1");
      for(String name : SqlWorkbenchHomeWidgetsMetaDataProducer.homeWidgetNames())
      {
         assertThat(PermissionsHelper.hasWidgetPermission(new RenderWidgetInput(), name)).as(name).isFalse();
      }
      assertThat(((RawHTML) render(SqlWorkbenchHomeWidgetsMetaDataProducer.SUMMARY)).getHtml()).contains("can&#39;t read");

      ////////////////////////////////////////////////////////////////////////
      // workbench access but no table read permissions: says so, no zeros //
      ////////////////////////////////////////////////////////////////////////
      QSession session = new QSession().withUser(new QUser().withIdReference("limited-1"));
      session.setPermissions(new HashSet<>(List.of(config.getAccessPermissionName())));
      QContext.init(qInstance, session);
      assertThat(((RawHTML) render(SqlWorkbenchHomeWidgetsMetaDataProducer.SUMMARY)).getHtml()).contains("can&#39;t read");
      List<String> denied = new ArrayList<>();
      for(String name : List.of(SqlWorkbenchHomeWidgetsMetaDataProducer.RUNS_PER_DAY, SqlWorkbenchHomeWidgetsMetaDataProducer.RECENT_NOTEBOOKS,
         SqlWorkbenchHomeWidgetsMetaDataProducer.RECENT_RUNS, SqlWorkbenchHomeWidgetsMetaDataProducer.SLOWEST, SqlWorkbenchHomeWidgetsMetaDataProducer.FAILED,
         SqlWorkbenchHomeWidgetsMetaDataProducer.MOST_USED, SqlWorkbenchHomeWidgetsMetaDataProducer.SHARED, SqlWorkbenchHomeWidgetsMetaDataProducer.WRITES))
      {
         denied.add(((RawHTML) render(name)).getHtml());
      }
      assertThat(denied).allSatisfy(html -> assertThat(html).contains("You don&#39;t have permission to read"));

      SqlWorkbenchHome.Viewer viewer = new SqlWorkbenchHome.Viewer(config, Instant.now(), ZoneId.of("UTC"), "x", false);
      assertThat(((RawHTML) SqlWorkbenchHome.load("nope", viewer, new RenderWidgetInput())).getHtml()).contains("Unknown");
      assertThat(((RawHTML) SqlWorkbenchHome.load(null, viewer, new RenderWidgetInput())).getHtml()).contains("Unknown");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testLabelsAndHtml()
   {
      assertThat(SqlWorkbenchHome.modeLabel("WRITE_PREVIEW")).isEqualTo("write preview");
      assertThat(SqlWorkbenchHome.modeLabel("EXPLAIN")).isEqualTo("explain");
      assertThat(SqlWorkbenchHome.modeLabel("EXPLAIN_ANALYZE")).isEqualTo("explain analyze");
      assertThat(SqlWorkbenchHome.modeLabel("SOMETHING_ELSE")).isEqualTo("something else");
      assertThat(SqlWorkbenchHome.modeLabel(null)).isEmpty();
      assertThat(SqlWorkbenchHome.statusBadge(new QRecord())).isEmpty();
      assertThat(SqlWorkbenchHome.statusBadge(new QRecord().withValue("status", "RUNNING"))).contains("running", HomeHtml.INFO_COLOR);
      assertThat(SqlWorkbenchHome.statusBadge(new QRecord().withValue("status", "CANCELLED"))).contains(HomeHtml.OFF_COLOR);
      assertThat(SqlWorkbenchHome.statusBadge(new QRecord().withValue("status", "TIMED_OUT"))).contains("timed out", HomeHtml.BAD_COLOR);
      assertThat(SqlWorkbenchHome.firstLine(null, 5)).isNull();
      assertThat(SqlWorkbenchHome.firstLine("abcdefgh", 5)).isEqualTo("abcd…");

      assertThat(HomeHtml.escape(null)).isEmpty();
      assertThat(HomeHtml.escape("<a href='x'>&\"")).isEqualTo("&lt;a href=&#39;x&#39;&gt;&amp;&quot;");
      assertThat(HomeHtml.code("SELECT   *\n FROM t", 100)).contains(">SELECT * FROM t<");
      assertThat(HomeHtml.code(null, 10)).contains("><");
      assertThat(HomeHtml.code("x".repeat(3000), 10)).contains("xxxxxxxxx…");
      assertThat(HomeHtml.duration(null)).isEmpty();
      assertThat(HomeHtml.duration(850)).isEqualTo("850 ms");
      assertThat(HomeHtml.duration(4200)).isEqualTo("4.2 s");
      assertThat(HomeHtml.duration(185_000)).isEqualTo("3m 05s");
      assertThat(HomeHtml.plural(1, "run")).isEqualTo("1 run");
      assertThat(HomeHtml.card("a", null, " ")).doesNotContain("&middot;");

      ZoneId  utc = ZoneId.of("UTC");
      Instant now = Instant.parse("2026-10-12T15:00:00Z");
      assertThat(HomeHtml.when(null, now, utc)).isEmpty();
      assertThat(HomeHtml.when(Instant.parse("2026-10-12T09:05:00Z"), now, utc)).isEqualTo("9:05 AM");
      assertThat(HomeHtml.when(Instant.parse("2026-03-01T09:05:00Z"), now, utc)).isEqualTo("Mar 1, 9:05 AM");
      assertThat(HomeHtml.when(Instant.parse("2025-03-01T09:05:00Z"), now, utc)).isEqualTo("Mar 1, 2025, 9:05 AM");
      assertThat(HomeHtml.tiles(List.of(new HomeHtml.Tile("A", "1", null, null)))).contains(">1<").doesNotContain("href");
   }

}
