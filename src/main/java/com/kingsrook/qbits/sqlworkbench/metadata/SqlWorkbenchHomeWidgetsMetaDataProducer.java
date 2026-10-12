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
package com.kingsrook.qbits.sqlworkbench.metadata;


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.home.SqlWorkbenchHome;
import com.kingsrook.qbits.sqlworkbench.home.SqlWorkbenchHomeWidgetRenderer;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.WidgetType;
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducerMultiOutput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitComponentMetaDataProducer;


/*******************************************************************************
 ** The sqlWorkbench app's home dashboard, in page order, all full width:
 ** summary tiles and links, runs per day, recent notebooks, recent runs,
 ** slowest and failed queries, most-used notebooks, shared notebooks,
 ** committed writes, and (workbench admins only) activity by user. Plus
 ** the "Open in Runner" widget on a notebook's record view.
 **
 ** The home owns this data; other dashboards should link here rather than
 ** repeat it.
 *******************************************************************************/
public class SqlWorkbenchHomeWidgetsMetaDataProducer extends QBitComponentMetaDataProducer<MetaDataProducerMultiOutput, SqlWorkbenchQBitConfig>
{
   public static final String SUMMARY          = "sqlWorkbenchHomeSummary";
   public static final String RUNS_PER_DAY     = "sqlWorkbenchHomeRunsPerDay";
   public static final String RECENT_NOTEBOOKS = "sqlWorkbenchHomeRecentNotebooks";
   public static final String RECENT_RUNS      = "sqlWorkbenchHomeRecentRuns";
   public static final String SLOWEST          = "sqlWorkbenchHomeSlowestQueries";
   public static final String FAILED           = "sqlWorkbenchHomeFailedQueries";
   public static final String MOST_USED        = "sqlWorkbenchHomeMostUsedNotebooks";
   public static final String SHARED           = "sqlWorkbenchHomeSharedNotebooks";
   public static final String WRITES           = "sqlWorkbenchHomeWrites";
   public static final String BY_USER          = "sqlWorkbenchHomeActivityByUser";
   public static final String NOTEBOOK_RUNNER  = "sqlWorkbenchNotebookOpenInRunner";

   private static final String HTML  = WidgetType.HTML.getType();
   private static final String TABLE = WidgetType.TABLE.getType();



   /*******************************************************************************
    ** The home widget names, in page order (unprefixed).
    *******************************************************************************/
   public static List<String> homeWidgetNames()
   {
      return (List.of(SUMMARY, RUNS_PER_DAY, RECENT_NOTEBOOKS, RECENT_RUNS, SLOWEST, FAILED, MOST_USED, SHARED, WRITES, BY_USER));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public MetaDataProducerMultiOutput produce(QInstance qInstance) throws QException
   {
      SqlWorkbenchQBitConfig config = getQBitConfig();
      QPermissionRules       access = config.getAccessPermissionRules();

      List<QWidgetMetaData> widgets = new ArrayList<>();
      widgets.add(widget(config, SUMMARY, "SQL Workbench", HTML, SqlWorkbenchHome.KIND_SUMMARY, access));
      widgets.add(widget(config, RUNS_PER_DAY, "Runs per Day", WidgetType.LINE_CHART.getType(), SqlWorkbenchHome.KIND_RUNS_PER_DAY, access));
      widgets.add(widget(config, RECENT_NOTEBOOKS, "Recent Notebooks", TABLE, SqlWorkbenchHome.KIND_RECENT_NOTEBOOKS, access));
      widgets.add(widget(config, RECENT_RUNS, "Recent Runs", TABLE, SqlWorkbenchHome.KIND_RECENT_RUNS, access));
      widgets.add(widget(config, SLOWEST, "Slowest Queries, 30 Days", TABLE, SqlWorkbenchHome.KIND_SLOWEST, access));
      widgets.add(widget(config, FAILED, "Failed Queries, 30 Days", TABLE, SqlWorkbenchHome.KIND_FAILED, access));
      widgets.add(widget(config, MOST_USED, "Most-Used Notebooks, 30 Days", TABLE, SqlWorkbenchHome.KIND_MOST_USED, access));
      widgets.add(widget(config, SHARED, "Shared Notebooks", TABLE, SqlWorkbenchHome.KIND_SHARED, access));
      widgets.add(widget(config, WRITES, "Writes", TABLE, SqlWorkbenchHome.KIND_WRITES, access));
      widgets.add(widget(config, BY_USER, "Activity by User, 30 Days", TABLE, SqlWorkbenchHome.KIND_BY_USER, config.getAdminPermissionRules()));
      widgets.add(widget(config, NOTEBOOK_RUNNER, "Runner", HTML, SqlWorkbenchHome.KIND_NOTEBOOK_RUNNER, access));

      MetaDataProducerMultiOutput output = new MetaDataProducerMultiOutput();
      widgets.forEach(output::add);
      return (output);
   }



   /*******************************************************************************
    ** One full-width widget rendered by SqlWorkbenchHomeWidgetRenderer.
    *******************************************************************************/
   private static QWidgetMetaData widget(SqlWorkbenchQBitConfig config, String name, String label, String type, String kind, QPermissionRules permissionRules)
   {
      return (new QWidgetMetaData()
         .withName(config.applyPrefix(name))
         .withLabel(label)
         .withType(type)
         .withGridColumns(12)
         .withIsCard(true)
         .withShowReloadButton(true)
         .withCodeReference(new QCodeReference(SqlWorkbenchHomeWidgetRenderer.class))
         .withDefaultValue(SqlWorkbenchHomeWidgetRenderer.KIND, kind)
         .withPermissionRules(permissionRules));
   }



   /*******************************************************************************
    ** with the workbench widget, before the apps
    *******************************************************************************/
   @Override
   public int getSortOrder()
   {
      return (310);
   }

}
