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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchSecurity;
import com.kingsrook.qbits.sqlworkbench.engine.ExecutionMode;
import com.kingsrook.qbits.sqlworkbench.engine.ExecutionStatus;
import com.kingsrook.qbits.sqlworkbench.metadata.SqlWorkbenchRunnerAppMetaDataProducer;
import com.kingsrook.qbits.sqlworkbench.model.ShareAudienceType;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchExecution;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebook;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookShare;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.actions.permissions.TablePermissionSubType;
import com.kingsrook.qqq.backend.core.actions.tables.AggregateAction;
import com.kingsrook.qqq.backend.core.actions.tables.CountAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.aggregate.Aggregate;
import com.kingsrook.qqq.backend.core.model.actions.tables.aggregate.AggregateInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.aggregate.AggregateOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.aggregate.AggregateResult;
import com.kingsrook.qqq.backend.core.model.actions.tables.aggregate.GroupBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.count.CountInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.ChartData;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.QWidgetData;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.RawHTML;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.TableData;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;


/*******************************************************************************
 ** The data behind the SQL Workbench home dashboard and the notebook's
 ** "Open in Runner" widget.
 **
 ** Everything is read through QQQ's core actions in the viewer's own session,
 ** so the tables' permissions and record security locks decide what each
 ** widget counts and lists: a user sees their own runs and the notebooks
 ** they can open; a workbench admin (all-access key) sees everyone's. A
 ** widget whose table the user may not read says so instead of showing
 ** zeros. Nothing here writes, and no SQL text is logged.
 *******************************************************************************/
public final class SqlWorkbenchHome
{
   public static final String KIND_SUMMARY          = "summary";
   public static final String KIND_RUNS_PER_DAY     = "runsPerDay";
   public static final String KIND_RECENT_NOTEBOOKS = "recentNotebooks";
   public static final String KIND_RECENT_RUNS      = "recentRuns";
   public static final String KIND_SLOWEST          = "slowest";
   public static final String KIND_FAILED           = "failed";
   public static final String KIND_MOST_USED        = "mostUsed";
   public static final String KIND_SHARED           = "shared";
   public static final String KIND_WRITES           = "writes";
   public static final String KIND_BY_USER          = "byUser";
   public static final String KIND_NOTEBOOK_RUNNER  = "notebookRunner";

   static final int DAYS        = 30;
   static final int ROWS        = 10;
   static final int USER_ROWS   = 25;
   static final int SCAN_LIMIT  = 20_000;
   static final int SHARE_LIMIT = 1_000;
   static final int SQL_CHARS   = 140;
   static final int ERROR_CHARS = 200;

   static final List<String> FAILED_STATUSES = List.of(ExecutionStatus.FAILED.name(), ExecutionStatus.TIMED_OUT.name(),
      ExecutionStatus.REJECTED.name(), ExecutionStatus.ROLLED_BACK.name());

   private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);



   /*******************************************************************************
    ** Who is looking, and when: the config, the clock's now, the display time
    ** zone, the viewer's user id, and whether they hold the workbench
    ** all-access key (see everyone's notebooks and runs).
    *******************************************************************************/
   record Viewer(SqlWorkbenchQBitConfig config, Instant now, ZoneId zoneId, String userId, boolean allAccess)
   {
      /*******************************************************************************
       ** The (prefixed) name of one of the qbit's tables.
       *******************************************************************************/
      String table(String tableName)
      {
         return (config.applyPrefix(tableName));
      }



      /*******************************************************************************
       ** Start of the window of the last DAYS days.
       *******************************************************************************/
      Instant windowStart()
      {
         return (today().minusDays(DAYS - 1L).atStartOfDay(zoneId).toInstant());
      }



      /*******************************************************************************
       ** Today in the display zone.
       *******************************************************************************/
      LocalDate today()
      {
         return (now.atZone(zoneId).toLocalDate());
      }
   }



   /*******************************************************************************
    ** Utility class - no instances.
    *******************************************************************************/
   private SqlWorkbenchHome()
   {
   }



   /*******************************************************************************
    ** The viewer of the current session.
    *******************************************************************************/
   static Viewer viewer(SqlWorkbenchQBitConfig config, Instant now)
   {
      QSession session = QContext.getQSession();
      SqlWorkbenchSecurity.ensureSessionKeys(config, session);
      String userId = session == null || session.getUser() == null ? null : session.getUser().getIdReference();
      return (new Viewer(config, now, ValueUtils.getSessionOrInstanceZoneId(), userId, SqlWorkbenchSecurity.hasAllAccess(config)));
   }



   /*******************************************************************************
    ** Load one widget's data.
    *******************************************************************************/
   static QWidgetData load(String kind, Viewer viewer, RenderWidgetInput input) throws QException
   {
      return (switch(kind == null ? "" : kind)
      {
         case KIND_SUMMARY -> summary(viewer, input);
         case KIND_RUNS_PER_DAY -> runsPerDay(viewer, input);
         case KIND_RECENT_NOTEBOOKS -> recentNotebooks(viewer, input);
         case KIND_RECENT_RUNS -> recentRuns(viewer, input);
         case KIND_SLOWEST -> slowest(viewer, input);
         case KIND_FAILED -> failed(viewer, input);
         case KIND_MOST_USED -> mostUsed(viewer, input);
         case KIND_SHARED -> shared(viewer, input);
         case KIND_WRITES -> writes(viewer, input);
         case KIND_BY_USER -> byUser(viewer, input);
         case KIND_NOTEBOOK_RUNNER -> notebookRunner(viewer, input);
         default -> new RawHTML(null, HomeHtml.emptyState("Unknown SQL Workbench widget."));
      });
   }



   /*******************************************************************************
    ** Tiles (notebooks, runs today, runs and failures in 7 days, writes in
    ** DAYS days), what they count, and links to the Runner and the tables.
    *******************************************************************************/
   static QWidgetData summary(Viewer viewer, RenderWidgetInput input) throws QException
   {
      String              notebooks  = viewer.table(SqlWorkbenchNotebook.TABLE_NAME);
      String              executions = viewer.table(SqlWorkbenchExecution.TABLE_NAME);
      List<HomeHtml.Tile> tiles      = new ArrayList<>();

      if(canRead(input, notebooks))
      {
         long total = count(notebooks, new QQueryFilter());
         long mine  = viewer.userId() == null ? 0 : count(notebooks, new QQueryFilter(new QFilterCriteria("ownerUserId", QCriteriaOperator.EQUALS, viewer.userId())));
         tiles.add(new HomeHtml.Tile("Notebooks", HomeHtml.number(total),
            HomeHtml.number(mine) + " yours, " + HomeHtml.number(total - mine) + (viewer.allAccess() ? " by other users" : " shared with you"), "/app/" + notebooks));
      }

      if(canRead(input, executions))
      {
         Instant startOfToday = viewer.today().atStartOfDay(viewer.zoneId()).toInstant();
         Instant weekStart    = viewer.now().minus(Duration.ofDays(7));
         Instant window       = viewer.windowStart();
         tiles.add(new HomeHtml.Tile("Runs Today", HomeHtml.number(count(executions, since(startOfToday))), null, "/app/" + executions));
         tiles.add(new HomeHtml.Tile("Runs, 7 Days", HomeHtml.number(count(executions, since(weekStart))),
            HomeHtml.number(count(executions, since(weekStart).withCriteria(new QFilterCriteria("status", QCriteriaOperator.IN, FAILED_STATUSES)))) + " failed", null));

         Writes writes = writesSince(executions, window);
         tiles.add(new HomeHtml.Tile("Writes, " + DAYS + " Days", HomeHtml.number(writes.count()),
            Boolean.TRUE.equals(viewer.config().getWriteModeEnabled()) ? HomeHtml.plural(writes.rowsAffected(), "row") + " affected" : "write mode is off", null));
      }

      if(tiles.isEmpty())
      {
         return (new RawHTML(null, HomeHtml.emptyState("You can't read the notebook or execution tables, so there is nothing to summarize.")));
      }

      String scope = viewer.allAccess()
         ? "You are a workbench admin: these counts include every user's notebooks and runs."
         : "These counts include your own runs and the notebooks you can open (yours, and those shared with you).";
      List<String[]> actions = new ArrayList<>();
      actions.add(new String[] {"Open the Runner", runnerUrl(viewer, null)});
      actions.add(new String[] {"Notebooks", "/app/" + notebooks});
      actions.add(new String[] {"Execution Audit", "/app/" + executions});
      return (new RawHTML(null, HomeHtml.tiles(tiles) + HomeHtml.actions(actions) + HomeHtml.note(scope)));
   }



   /*******************************************************************************
    ** Runs, failures and committed writes per day over the last DAYS days.
    *******************************************************************************/
   static QWidgetData runsPerDay(Viewer viewer, RenderWidgetInput input) throws QException
   {
      String executions = viewer.table(SqlWorkbenchExecution.TABLE_NAME);
      if(!canRead(input, executions))
      {
         return (noAccess("the execution audit"));
      }

      Map<LocalDate, long[]> perDay = new TreeMap<>();
      for(LocalDate day = viewer.today().minusDays(DAYS - 1L); !day.isAfter(viewer.today()); day = day.plusDays(1))
      {
         perDay.put(day, new long[3]);
      }

      List<QRecord> runs  = query(executions, since(viewer.windowStart()), SCAN_LIMIT, Set.of("id", "startTime", "status", "mode"));
      long          total = 0;
      for(QRecord run : runs)
      {
         Instant start = run.getValueInstant("startTime");
         long[]  day   = start == null ? null : perDay.get(start.atZone(viewer.zoneId()).toLocalDate());
         if(day != null)
         {
            total++;
            day[0]++;
            day[1] += FAILED_STATUSES.contains(run.getValueString("status")) ? 1 : 0;
            day[2] += ExecutionMode.WRITE_COMMIT.name().equals(run.getValueString("mode")) ? 1 : 0;
         }
      }

      List<String> labels = new ArrayList<>();
      List<Number> all    = new ArrayList<>();
      List<Number> failed = new ArrayList<>();
      List<Number> writes = new ArrayList<>();
      perDay.forEach((day, counts) ->
      {
         labels.add(DAY.format(day));
         all.add(counts[0]);
         failed.add(counts[1]);
         writes.add(counts[2]);
      });

      ChartData chart = new ChartData().withChartData(new ChartData.Data()
         .withLabels(labels)
         .withDatasets(List.of(
            new ChartData.Data.Dataset().withLabel("Runs").withData(all).withColor("#0062FF"),
            new ChartData.Data.Dataset().withLabel("Failed").withData(failed).withColor(HomeHtml.BAD_COLOR),
            new ChartData.Data.Dataset().withLabel("Writes").withData(writes).withColor(HomeHtml.WARN_COLOR))));
      String sublabel = total == 0 ? "No runs in the last " + DAYS + " days" : HomeHtml.plural(total, "run") + " in the last " + DAYS + " days, by day";
      if(runs.size() >= SCAN_LIMIT)
      {
         sublabel += " (the latest " + HomeHtml.number(SCAN_LIMIT) + " runs)";
      }
      chart.setSublabel(sublabel);
      return (chart);
   }



   /*******************************************************************************
    ** The notebooks changed most recently.
    *******************************************************************************/
   static QWidgetData recentNotebooks(Viewer viewer, RenderWidgetInput input) throws QException
   {
      String notebooks = viewer.table(SqlWorkbenchNotebook.TABLE_NAME);
      if(!canRead(input, notebooks))
      {
         return (noAccess("notebooks"));
      }

      List<QRecord> recent = query(notebooks, new QQueryFilter().withOrderBy(new QFilterOrderBy("modifyDate", false)).withOrderBy(new QFilterOrderBy("id", false)), ROWS,
         Set.of("id", "title", "description", "ownerUserId", "ownerName", "modifyDate"));
      Map<Integer, String> sharing = sharing(viewer, input, recent);

      List<Map<String, Object>> rows = new ArrayList<>();
      for(QRecord notebook : recent)
      {
         String              shared = sharing.getOrDefault(notebook.getValueInteger("id"), "personal");
         Map<String, Object> row    = new HashMap<>();
         row.put("notebook", notebookCard(viewer, notebook, HomeHtml.escape(firstLine(notebook.getValueString("description"), 120))));
         row.put("owner", HomeHtml.escape(ownerLabel(viewer, notebook)));
         row.put("shared", HomeHtml.badge(shared, "personal".equals(shared) ? HomeHtml.OFF_COLOR : HomeHtml.INFO_COLOR));
         row.put("modified", HomeHtml.escape(HomeHtml.when(notebook.getValueInstant("modifyDate"), viewer.now(), viewer.zoneId())));
         rows.add(row);
      }

      TableData table = table(List.of(column("Notebook", "notebook"), column("Owner", "owner"), column("Sharing", "shared"), column("Modified", "modified")), rows,
         "No notebooks yet. Create one in the Runner.");
      return (table.withLinkText("All notebooks").withLinkURL("/app/" + notebooks));
   }



   /*******************************************************************************
    ** The latest runs.
    *******************************************************************************/
   static QWidgetData recentRuns(Viewer viewer, RenderWidgetInput input) throws QException
   {
      String executions = viewer.table(SqlWorkbenchExecution.TABLE_NAME);
      if(!canRead(input, executions))
      {
         return (noAccess("the execution audit"));
      }

      List<QRecord>             runs  = query(executions, new QQueryFilter().withOrderBy(new QFilterOrderBy("id", false)), ROWS, null);
      Map<Integer, String>      names = notebookTitles(viewer, input, runs);
      List<Map<String, Object>> rows  = new ArrayList<>();
      for(QRecord run : runs)
      {
         Map<String, Object> row = runRow(viewer, run, names);
         row.put("result", HomeHtml.card(statusBadge(run), HomeHtml.escape(HomeHtml.duration(run.getValueInteger("durationMs"))), HomeHtml.escape(rowsText(run))));
         rows.add(row);
      }

      TableData table = table(List.of(column("When", "when"), column("Who", "who"), column("Query", "query"), column("Result", "result")), rows,
         "No runs yet. Queries run in the Runner are recorded here.");
      return (table.withLinkText("All executions").withLinkURL("/app/" + executions));
   }



   /*******************************************************************************
    ** The slowest runs of the last DAYS days.
    *******************************************************************************/
   static QWidgetData slowest(Viewer viewer, RenderWidgetInput input) throws QException
   {
      String executions = viewer.table(SqlWorkbenchExecution.TABLE_NAME);
      if(!canRead(input, executions))
      {
         return (noAccess("the execution audit"));
      }

      QQueryFilter filter = since(viewer.windowStart())
         .withCriteria(new QFilterCriteria("durationMs", QCriteriaOperator.IS_NOT_BLANK))
         .withCriteria(new QFilterCriteria("mode", QCriteriaOperator.NOT_EQUALS, ExecutionMode.WRITE_PREVIEW.name()))
         .withOrderBy(new QFilterOrderBy("durationMs", false));
      List<QRecord>             runs  = query(executions, filter, ROWS, null);
      Map<Integer, String>      names = notebookTitles(viewer, input, runs);
      List<Map<String, Object>> rows  = new ArrayList<>();
      for(QRecord run : runs)
      {
         Map<String, Object> row = runRow(viewer, run, names);
         row.put("duration", HomeHtml.card("<strong>" + HomeHtml.escape(HomeHtml.duration(run.getValueInteger("durationMs"))) + "</strong>", statusBadge(run)));
         rows.add(row);
      }

      TableData table = table(List.of(column("Duration", "duration"), column("Query", "query"), column("Who", "who"), column("When", "when")), rows,
         "No completed runs in the last " + DAYS + " days.");
      return (table.withLinkText("All executions").withLinkURL("/app/" + executions));
   }



   /*******************************************************************************
    ** Failed, timed out, rejected and rolled-back runs of the last DAYS days.
    *******************************************************************************/
   static QWidgetData failed(Viewer viewer, RenderWidgetInput input) throws QException
   {
      String executions = viewer.table(SqlWorkbenchExecution.TABLE_NAME);
      if(!canRead(input, executions))
      {
         return (noAccess("the execution audit"));
      }

      QQueryFilter filter = since(viewer.windowStart())
         .withCriteria(new QFilterCriteria("status", QCriteriaOperator.IN, FAILED_STATUSES))
         .withOrderBy(new QFilterOrderBy("id", false));
      List<QRecord>             runs  = query(executions, filter, ROWS, null);
      Map<Integer, String>      names = notebookTitles(viewer, input, runs);
      List<Map<String, Object>> rows  = new ArrayList<>();
      for(QRecord run : runs)
      {
         Map<String, Object> row = runRow(viewer, run, names);
         row.put("error", HomeHtml.card(statusBadge(run), HomeHtml.escape(firstLine(run.getValueString("errorMessage"), ERROR_CHARS))));
         rows.add(row);
      }

      TableData table = table(List.of(column("When", "when"), column("Query", "query"), column("Error", "error"), column("Who", "who")), rows,
         "No failed runs in the last " + DAYS + " days.");
      return (table.withLinkText("All executions").withLinkURL("/app/" + executions));
   }



   /*******************************************************************************
    ** Notebooks with the most runs in the last DAYS days.
    *******************************************************************************/
   static QWidgetData mostUsed(Viewer viewer, RenderWidgetInput input) throws QException
   {
      String executions = viewer.table(SqlWorkbenchExecution.TABLE_NAME);
      if(!canRead(input, executions))
      {
         return (noAccess("the execution audit"));
      }

      Aggregate runs     = new Aggregate("id", AggregateOperator.COUNT);
      Aggregate users    = new Aggregate("userId", AggregateOperator.COUNT_DISTINCT);
      Aggregate lastRun  = new Aggregate("startTime", AggregateOperator.MAX);
      GroupBy   notebook = new GroupBy(QFieldType.INTEGER, "notebookId");
      List<AggregateResult> results = new AggregateAction().execute(new AggregateInput(executions)
            .withFilter(since(viewer.windowStart()).withCriteria(new QFilterCriteria("notebookId", QCriteriaOperator.IS_NOT_BLANK)))
            .withAggregate(runs).withAggregate(users).withAggregate(lastRun).withGroupBy(notebook))
         .getResults();
      results.sort(Comparator.comparing((AggregateResult r) -> ValueUtils.getValueAsLong(r.getAggregateValue(runs))).reversed());
      List<AggregateResult> top = results.subList(0, Math.min(ROWS, results.size()));

      Set<Integer> ids = new LinkedHashSet<>();
      top.forEach(r -> ids.add(ValueUtils.getValueAsInteger(r.getGroupByValue(notebook))));
      Map<Integer, String> names = notebookTitles(viewer, input, ids);

      List<Map<String, Object>> rows = new ArrayList<>();
      for(AggregateResult result : top)
      {
         Integer             id  = ValueUtils.getValueAsInteger(result.getGroupByValue(notebook));
         Map<String, Object> row = new HashMap<>();
         row.put("notebook", notebookLink(viewer, id, names.get(id)));
         row.put("runs", HomeHtml.number(ValueUtils.getValueAsLong(result.getAggregateValue(runs))));
         row.put("users", HomeHtml.number(ValueUtils.getValueAsLong(result.getAggregateValue(users))));
         row.put("lastRun", HomeHtml.escape(HomeHtml.when(ValueUtils.getValueAsInstant(result.getAggregateValue(lastRun)), viewer.now(), viewer.zoneId())));
         rows.add(row);
      }

      return (table(List.of(column("Notebook", "notebook"), column("Runs", "runs", "right"), column("Users", "users", "right"), column("Last Run", "lastRun")), rows,
         "No notebook runs in the last " + DAYS + " days."));
   }



   /*******************************************************************************
    ** Notebooks shared with users, groups or everyone (the shared library),
    ** most recently shared first.
    *******************************************************************************/
   static QWidgetData shared(Viewer viewer, RenderWidgetInput input) throws QException
   {
      String shares = viewer.table(SqlWorkbenchNotebookShare.TABLE_NAME);
      if(!canRead(input, shares))
      {
         return (noAccess("notebook shares"));
      }

      Map<Integer, List<QRecord>> byNotebook = new LinkedHashMap<>();
      for(QRecord share : query(shares, new QQueryFilter().withOrderBy(new QFilterOrderBy("id", false)), SHARE_LIMIT, Set.of("id", "notebookId", "audienceType", "audienceId", "createDate")))
      {
         Integer notebookId = share.getValueInteger("notebookId");
         if(notebookId != null && (byNotebook.containsKey(notebookId) || byNotebook.size() < ROWS))
         {
            byNotebook.computeIfAbsent(notebookId, k -> new ArrayList<>()).add(share);
         }
      }

      Map<Integer, QRecord> notebooks = notebooks(viewer, input, byNotebook.keySet());
      List<Map<String, Object>> rows = new ArrayList<>();
      byNotebook.forEach((id, list) ->
      {
         List<String> audiences = new ArrayList<>();
         for(QRecord share : list)
         {
            audiences.add(audienceLabel(share));
         }
         QRecord             notebook = notebooks.get(id);
         Map<String, Object> row      = new HashMap<>();
         row.put("notebook", notebook == null ? notebookLink(viewer, id, null) : notebookCard(viewer, notebook));
         row.put("owner", HomeHtml.escape(notebook == null ? "" : ownerLabel(viewer, notebook)));
         row.put("audience", HomeHtml.escape(String.join(", ", audiences)));
         row.put("since", HomeHtml.escape(HomeHtml.when(list.get(list.size() - 1).getValueInstant("createDate"), viewer.now(), viewer.zoneId())));
         rows.add(row);
      });

      return (table(List.of(column("Notebook", "notebook"), column("Owner", "owner"), column("Shared With", "audience"), column("Shared Since", "since")), rows,
         "No shared notebooks. Share one from its record (Share Notebook) or in the Runner."));
   }



   /*******************************************************************************
    ** Committed writes (gated write mode): who ran what, when, rows affected.
    *******************************************************************************/
   static QWidgetData writes(Viewer viewer, RenderWidgetInput input) throws QException
   {
      String executions = viewer.table(SqlWorkbenchExecution.TABLE_NAME);
      if(!canRead(input, executions))
      {
         return (noAccess("the execution audit"));
      }

      QQueryFilter filter = new QQueryFilter(new QFilterCriteria("mode", QCriteriaOperator.EQUALS, ExecutionMode.WRITE_COMMIT.name()))
         .withOrderBy(new QFilterOrderBy("id", false));
      List<QRecord>             runs  = query(executions, filter, ROWS, null);
      Map<Integer, String>      names = notebookTitles(viewer, input, runs);
      List<Map<String, Object>> rows  = new ArrayList<>();
      for(QRecord run : runs)
      {
         Map<String, Object> row = runRow(viewer, run, names);
         Integer affected = run.getValueInteger("rowsAffected");
         row.put("affected", HomeHtml.card("<strong>" + HomeHtml.escape(affected == null ? "-" : HomeHtml.number(affected)) + "</strong>", statusBadge(run)));
         rows.add(row);
      }

      String writeMode = Boolean.TRUE.equals(viewer.config().getWriteModeEnabled())
         ? "Write mode is on: users with the write permission can preview a write, then commit it within the confirmation window. Every preview and commit is audited."
         : "Write mode is off on this server, so no new writes can be committed.";
      TableData table = table(List.of(column("When", "when"), column("Who", "who"), column("Query", "query"), column("Rows Affected", "affected")), rows,
         "No writes have been committed.");
      table.withFooterHTML(HomeHtml.note(writeMode));
      return (table.withLinkText("All executions").withLinkURL("/app/" + executions));
   }



   /*******************************************************************************
    ** Per-user activity over the last DAYS days (the widget is limited to
    ** workbench admins; the locks would show anyone else only themselves).
    *******************************************************************************/
   static QWidgetData byUser(Viewer viewer, RenderWidgetInput input) throws QException
   {
      String executions = viewer.table(SqlWorkbenchExecution.TABLE_NAME);
      if(!canRead(input, executions))
      {
         return (noAccess("the execution audit"));
      }

      Map<String, Object[]> users = new LinkedHashMap<>();
      List<QRecord>         runs  = query(executions, since(viewer.windowStart()).withOrderBy(new QFilterOrderBy("id", false)), SCAN_LIMIT,
         Set.of("id", "userId", "userName", "status", "mode", "startTime"));
      for(QRecord run : runs)
      {
         String   userId = run.getValueString("userId") == null ? "(unknown)" : run.getValueString("userId");
         Object[] totals = users.computeIfAbsent(userId, k -> new Object[] {run.getValueString("userName"), 0L, 0L, 0L, run.getValueInstant("startTime")});
         totals[1] = (Long) totals[1] + 1;
         totals[2] = (Long) totals[2] + (FAILED_STATUSES.contains(run.getValueString("status")) ? 1 : 0);
         totals[3] = (Long) totals[3] + (ExecutionMode.WRITE_COMMIT.name().equals(run.getValueString("mode")) ? 1 : 0);
      }

      List<Map.Entry<String, Object[]>> sorted = new ArrayList<>(users.entrySet());
      sorted.sort(Comparator.comparing((Map.Entry<String, Object[]> e) -> (Long) e.getValue()[1]).reversed());
      List<Map<String, Object>> rows = new ArrayList<>();
      for(Map.Entry<String, Object[]> entry : sorted.subList(0, Math.min(USER_ROWS, sorted.size())))
      {
         Object[]            totals = entry.getValue();
         Map<String, Object> row    = new HashMap<>();
         String              name   = totals[0] == null ? entry.getKey() : String.valueOf(totals[0]);
         row.put("user", HomeHtml.card(HomeHtml.escape(name), name.equals(entry.getKey()) ? null : HomeHtml.escape(entry.getKey())));
         row.put("runs", HomeHtml.number((Long) totals[1]));
         row.put("failed", HomeHtml.number((Long) totals[2]));
         row.put("writes", HomeHtml.number((Long) totals[3]));
         row.put("lastRun", HomeHtml.escape(HomeHtml.when((Instant) totals[4], viewer.now(), viewer.zoneId())));
         rows.add(row);
      }

      TableData table = table(List.of(column("User", "user"), column("Runs", "runs", "right"), column("Failed", "failed", "right"), column("Writes", "writes", "right"),
         column("Last Run", "lastRun")), rows, "No runs in the last " + DAYS + " days.");
      if(!viewer.allAccess())
      {
         table.withFooterHTML(HomeHtml.note("Only your own runs are shown; workbench admins see every user's."));
      }
      return (table);
   }



   /*******************************************************************************
    ** On a notebook's record view: open it in the Runner.
    *******************************************************************************/
   static QWidgetData notebookRunner(Viewer viewer, RenderWidgetInput input)
   {
      Integer id = ValueUtils.getValueAsInteger(input.getQueryParams() == null ? null : input.getQueryParams().get("id"));
      if(id == null)
      {
         return (new RawHTML(null, HomeHtml.emptyState("Save the notebook to open it in the Runner.")));
      }
      return (new RawHTML(null, HomeHtml.actions(List.<String[]>of(new String[] {"Open in Runner", runnerUrl(viewer, id)}))
         + HomeHtml.note("The Runner edits and runs this notebook's cells. Runs are recorded in the execution audit.")));
   }



   /*******************************************************************************
    ** The Runner's URL, opening a notebook when an id is given.
    *******************************************************************************/
   static String runnerUrl(Viewer viewer, Integer notebookId)
   {
      String url = "/app/" + viewer.config().applyPrefix(SqlWorkbenchRunnerAppMetaDataProducer.APP_NAME);
      return (notebookId == null ? url : url + "?notebook=" + notebookId);
   }



   /*******************************************************************************
    ** Committed writes since an instant, and the rows they affected.
    *******************************************************************************/
   record Writes(long count, long rowsAffected)
   {
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Writes writesSince(String executions, Instant since) throws QException
   {
      Aggregate count    = new Aggregate("id", AggregateOperator.COUNT);
      Aggregate affected = new Aggregate("rowsAffected", AggregateOperator.SUM);
      List<AggregateResult> results = new AggregateAction().execute(new AggregateInput(executions)
         .withFilter(since(since).withCriteria(new QFilterCriteria("mode", QCriteriaOperator.EQUALS, ExecutionMode.WRITE_COMMIT.name())))
         .withAggregate(count).withAggregate(affected)).getResults();
      if(results.isEmpty())
      {
         return (new Writes(0, 0));
      }
      Long total = ValueUtils.getValueAsLong(results.get(0).getAggregateValue(count));
      Long rows  = ValueUtils.getValueAsLong(results.get(0).getAggregateValue(affected));
      return (new Writes(total == null ? 0 : total, rows == null ? 0 : rows));
   }



   /*******************************************************************************
    ** The cells every run row shares: when, who, and the query (linked to its
    ** audit record, with its backend and notebook).
    *******************************************************************************/
   private static Map<String, Object> runRow(Viewer viewer, QRecord run, Map<Integer, String> notebookNames)
   {
      Map<String, Object> row        = new HashMap<>();
      Integer             notebookId = run.getValueInteger("notebookId");
      String              recordUrl  = "/app/" + viewer.table(SqlWorkbenchExecution.TABLE_NAME) + "/" + run.getValueInteger("id");
      row.put("when", HomeHtml.escape(HomeHtml.when(run.getValueInstant("startTime"), viewer.now(), viewer.zoneId())));
      row.put("who", HomeHtml.escape(userLabel(viewer, run.getValueString("userId"), run.getValueString("userName"))));
      row.put("query", HomeHtml.card("<a href=\"" + HomeHtml.escape(recordUrl) + "\" style=\"color:inherit;text-decoration:none\">"
            + HomeHtml.code(run.getValueString("sqlText"), SQL_CHARS) + "</a>",
         HomeHtml.escape(modeLabel(run.getValueString("mode"))),
         HomeHtml.escape(run.getValueString("backendName")),
         notebookId == null ? null : notebookLink(viewer, notebookId, notebookNames.get(notebookId))));
      return (row);
   }



   /*******************************************************************************
    ** A notebook's title linked to its record, with facts (already HTML) and
    ** an "Open in Runner" link.
    *******************************************************************************/
   private static String notebookCard(Viewer viewer, QRecord notebook, String... facts)
   {
      Integer      id  = notebook.getValueInteger("id");
      List<String> all = new ArrayList<>(List.of(facts));
      all.add(HomeHtml.link(runnerUrl(viewer, id), "Open in Runner"));
      return (HomeHtml.card(HomeHtml.link("/app/" + viewer.table(SqlWorkbenchNotebook.TABLE_NAME) + "/" + id, notebook.getValueString("title")), all.toArray(new String[0])));
   }



   /*******************************************************************************
    ** A notebook link by id: its title when the viewer can read it, otherwise
    ** says so (its runs stay visible in the audit after it is unshared or
    ** deleted).
    *******************************************************************************/
   private static String notebookLink(Viewer viewer, Integer id, String title)
   {
      if(title == null)
      {
         return (HomeHtml.muted("Notebook #" + id + " (deleted, or not visible to you)"));
      }
      return (HomeHtml.link("/app/" + viewer.table(SqlWorkbenchNotebook.TABLE_NAME) + "/" + id, title));
   }



   /*******************************************************************************
    ** "you", or the owner's name.
    *******************************************************************************/
   private static String ownerLabel(Viewer viewer, QRecord notebook)
   {
      return (userLabel(viewer, notebook.getValueString("ownerUserId"), notebook.getValueString("ownerName")));
   }



   /*******************************************************************************
    ** "you", the user's name, or their id.
    *******************************************************************************/
   private static String userLabel(Viewer viewer, String userId, String userName)
   {
      if(userId != null && userId.equals(viewer.userId()))
      {
         return ("you");
      }
      return (userName != null && !userName.isBlank() ? userName : (userId == null ? "unknown user" : userId));
   }



   /*******************************************************************************
    ** Plain label of an execution mode.
    *******************************************************************************/
   static String modeLabel(String mode)
   {
      if(mode == null)
      {
         return ("");
      }
      return (switch(mode)
      {
         case "READ_ONLY" -> "read-only";
         case "WRITE_PREVIEW" -> "write preview";
         case "WRITE_COMMIT" -> "write";
         case "EXPLAIN" -> "explain";
         case "EXPLAIN_ANALYZE" -> "explain analyze";
         default -> mode.toLowerCase(Locale.ROOT).replace('_', ' ');
      });
   }



   /*******************************************************************************
    ** A run's status as a colored badge.
    *******************************************************************************/
   static String statusBadge(QRecord run)
   {
      String status = run.getValueString("status");
      if(status == null)
      {
         return ("");
      }
      String color = switch(status)
      {
         case "SUCCEEDED" -> HomeHtml.OK_COLOR;
         case "RUNNING" -> HomeHtml.INFO_COLOR;
         case "CANCELLED" -> HomeHtml.OFF_COLOR;
         default -> HomeHtml.BAD_COLOR;
      };
      return (HomeHtml.badge(status.toLowerCase(Locale.ROOT).replace('_', ' '), color));
   }



   /*******************************************************************************
    ** "12 rows" returned, or affected for a write.
    *******************************************************************************/
   private static String rowsText(QRecord run)
   {
      Integer affected = run.getValueInteger("rowsAffected");
      if(affected != null)
      {
         return (HomeHtml.plural(affected, "row") + " affected");
      }
      Integer returned = run.getValueInteger("rowsReturned");
      return (returned == null ? "" : HomeHtml.plural(returned, "row"));
   }



   /*******************************************************************************
    ** How each notebook is shared, from the share rows the viewer can see:
    ** "everyone" (the shared library), "shared" (users or groups); absent
    ** when it has no visible shares.
    *******************************************************************************/
   private static Map<Integer, String> sharing(Viewer viewer, RenderWidgetInput input, List<QRecord> notebooks) throws QException
   {
      Map<Integer, String> sharing = new HashMap<>();
      String               shares  = viewer.table(SqlWorkbenchNotebookShare.TABLE_NAME);
      List<Serializable>   ids     = new ArrayList<>();
      notebooks.forEach(n -> ids.add(n.getValueInteger("id")));
      if(ids.isEmpty() || !canRead(input, shares))
      {
         return (sharing);
      }
      for(QRecord share : query(shares, new QQueryFilter(new QFilterCriteria("notebookId", QCriteriaOperator.IN, ids)), SHARE_LIMIT, Set.of("id", "notebookId", "audienceType")))
      {
         boolean everyone = ShareAudienceType.ALL.name().equals(share.getValueString("audienceType"));
         sharing.merge(share.getValueInteger("notebookId"), everyone ? "everyone" : "shared", (a, b) -> "everyone".equals(a) ? a : b);
      }
      return (sharing);
   }



   /*******************************************************************************
    ** A share's audience in words.
    *******************************************************************************/
   private static String audienceLabel(QRecord share)
   {
      String type = share.getValueString("audienceType");
      if(ShareAudienceType.ALL.name().equals(type))
      {
         return ("everyone");
      }
      String who = share.getValueString("audienceId");
      return (ShareAudienceType.GROUP.name().equals(type) ? "group " + who : who);
   }



   /*******************************************************************************
    ** The first line of a text, cut at maxChars.
    *******************************************************************************/
   static String firstLine(String text, int maxChars)
   {
      if(text == null)
      {
         return (null);
      }
      String line = text.strip().lines().findFirst().orElse("");
      return (line.length() > maxChars ? line.substring(0, maxChars - 1) + "…" : line);
   }



   /*******************************************************************************
    ** Titles of the notebooks the runs belong to, as far as the viewer can read
    ** them.
    *******************************************************************************/
   private static Map<Integer, String> notebookTitles(Viewer viewer, RenderWidgetInput input, List<QRecord> runs) throws QException
   {
      Set<Integer> ids = new LinkedHashSet<>();
      for(QRecord run : runs)
      {
         if(run.getValueInteger("notebookId") != null)
         {
            ids.add(run.getValueInteger("notebookId"));
         }
      }
      return (notebookTitles(viewer, input, ids));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Map<Integer, String> notebookTitles(Viewer viewer, RenderWidgetInput input, Set<Integer> ids) throws QException
   {
      Map<Integer, String> titles = new HashMap<>();
      notebooks(viewer, input, ids).forEach((id, notebook) -> titles.put(id, notebook.getValueString("title")));
      return (titles);
   }



   /*******************************************************************************
    ** The readable notebooks among some ids (through the notebook locks).
    *******************************************************************************/
   private static Map<Integer, QRecord> notebooks(Viewer viewer, RenderWidgetInput input, Set<Integer> ids) throws QException
   {
      Map<Integer, QRecord> notebooks = new HashMap<>();
      String                table     = viewer.table(SqlWorkbenchNotebook.TABLE_NAME);
      if(ids.isEmpty() || !canRead(input, table))
      {
         return (notebooks);
      }
      for(QRecord notebook : query(table, new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.IN, new ArrayList<Serializable>(ids))), ids.size(),
         Set.of("id", "title", "ownerUserId", "ownerName")))
      {
         notebooks.put(notebook.getValueInteger("id"), notebook);
      }
      return (notebooks);
   }



   /*******************************************************************************
    ** Whether the session may read a table.
    *******************************************************************************/
   static boolean canRead(RenderWidgetInput input, String tableName)
   {
      return (QContext.getQInstance().getTable(tableName) != null && PermissionsHelper.hasTablePermission(input, tableName, TablePermissionSubType.READ));
   }



   /*******************************************************************************
    ** The "you can't read this" state.
    *******************************************************************************/
   private static QWidgetData noAccess(String what)
   {
      return (new RawHTML(null, HomeHtml.emptyState("You don't have permission to read " + what + ".")));
   }



   /*******************************************************************************
    ** A filter on runs started at or after an instant.
    *******************************************************************************/
   private static QQueryFilter since(Instant since)
   {
      return (new QQueryFilter(new QFilterCriteria("startTime", QCriteriaOperator.GREATER_THAN_OR_EQUALS, since)));
   }



   /*******************************************************************************
    ** Count through the table's locks.
    *******************************************************************************/
   private static long count(String tableName, QQueryFilter filter) throws QException
   {
      Integer count = new CountAction().execute(new CountInput(tableName).withFilter(filter)).getCount();
      return (count == null ? 0 : count);
   }



   /*******************************************************************************
    ** Query through the table's locks.
    *******************************************************************************/
   private static List<QRecord> query(String tableName, QQueryFilter filter, int limit, Set<String> fields) throws QException
   {
      QueryInput queryInput = new QueryInput(tableName).withFilter(filter.withLimit(limit));
      if(fields != null)
      {
         queryInput.withFieldNamesToInclude(fields);
      }
      return (new QueryAction().execute(queryInput).getRecords());
   }



   /*******************************************************************************
    ** A table column of HTML cells, top-aligned.
    *******************************************************************************/
   private static TableData.Column column(String header, String accessor)
   {
      return (column(header, accessor, "left"));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static TableData.Column column(String header, String accessor, String align)
   {
      return (new TableData.Column("html", header, accessor, null, align).withVerticalAlign("top"));
   }



   /*******************************************************************************
    ** A table without pagination controls, with an honest empty state.
    *******************************************************************************/
   private static TableData table(List<TableData.Column> columns, List<Map<String, Object>> rows, String empty)
   {
      return (new TableData(null, columns, rows)
         .withRowsPerPage(Math.max(ROWS, rows.size()))
         .withHidePaginationDropdown(true)
         .withNoRowsFoundHTML(HomeHtml.emptyState(empty)));
   }

}
