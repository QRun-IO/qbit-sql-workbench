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


import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import com.kingsrook.qbits.sqlworkbench.BaseSqlWorkbenchTest;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchIntegrations;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.SystemUser;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchCell;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchExecution;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebook;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookShare;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookVersion;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchResultSnapshot;
import com.kingsrook.qqq.backend.core.actions.dashboard.RenderWidgetAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.ChildRecordListData;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.WidgetType;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaDataInterface;
import com.kingsrook.qqq.backend.core.model.metadata.joins.JoinType;
import com.kingsrook.qqq.backend.core.model.metadata.joins.QJoinMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppChildMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QFieldSection;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The app's navigation (Runner plus the main tables only), the sub-tables as
 ** child lists on a notebook's record view (still standard tables), and the
 ** ESB / Quick Search hooks.
 *******************************************************************************/
class AppNavigationTest extends BaseSqlWorkbenchTest
{
   private static final List<String> SUB_TABLES = List.of(SqlWorkbenchCell.TABLE_NAME, SqlWorkbenchNotebookVersion.TABLE_NAME,
      SqlWorkbenchResultSnapshot.TABLE_NAME, SqlWorkbenchNotebookShare.TABLE_NAME);



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testNavListsTheRunnerAndMainTablesOnly()
   {
      QAppMetaData app = qInstance.getApp(SqlWorkbenchAppMetaDataProducer.APP_NAME);
      assertThat(app.getChildren()).extracting(QAppChildMetaData::getName)
         .containsExactly(SqlWorkbenchRunnerAppMetaDataProducer.APP_NAME, SqlWorkbenchNotebook.TABLE_NAME, SqlWorkbenchExecution.TABLE_NAME);

      //////////////////////////////////////////////////////////////////////
      // no app anywhere lists a sub-table, yet each is a standard table   //
      // with a record label (reachable by URL, the table API and search) //
      //////////////////////////////////////////////////////////////////////
      for(QAppMetaData any : qInstance.getApps().values())
      {
         assertThat(any.getChildren() == null ? List.<QAppChildMetaData>of() : any.getChildren()).extracting(QAppChildMetaData::getName)
            .as(any.getName()).doesNotContainAnyElementsOf(SUB_TABLES);
      }
      for(String subTable : SUB_TABLES)
      {
         QTableMetaData table = qInstance.getTable(subTable);
         assertThat(table).as(subTable).isNotNull();
         assertThat(table.getRecordLabelFields()).as(subTable).isNotEmpty();
         assertThat(table.getField("notebookId").getPossibleValueSourceName()).isEqualTo(SqlWorkbenchNotebook.TABLE_NAME);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testNotebookRecordViewShowsTheSubTablesAndRuns()
   {
      QTableMetaData notebook = qInstance.getTable(SqlWorkbenchNotebook.TABLE_NAME);
      List<String> widgetSections = notebook.getSections().stream().map(QFieldSection::getWidgetName).filter(Objects::nonNull).toList();
      assertThat(widgetSections).containsExactly(SqlWorkbenchHomeWidgetsMetaDataProducer.NOTEBOOK_RUNNER,
         SqlWorkbenchTableMetaDataHelper.JOIN_NOTEBOOK_CELL, SqlWorkbenchTableMetaDataHelper.JOIN_NOTEBOOK_SHARE, SqlWorkbenchTableMetaDataHelper.JOIN_NOTEBOOK_SNAPSHOT,
         SqlWorkbenchTableMetaDataHelper.JOIN_NOTEBOOK_VERSION, SqlWorkbenchTableMetaDataHelper.JOIN_NOTEBOOK_EXECUTION);

      for(String joinName : widgetSections.subList(1, widgetSections.size()))
      {
         QWidgetMetaDataInterface widget = qInstance.getWidget(joinName);
         assertThat(widget.getType()).as(joinName).isEqualTo(WidgetType.CHILD_RECORD_LIST.getType());
         QJoinMetaData join = qInstance.getJoin(joinName);
         assertThat(join.getLeftTable()).isEqualTo(SqlWorkbenchNotebook.TABLE_NAME);
         assertThat(join.getType()).isEqualTo(JoinType.ONE_TO_MANY);
      }
      assertThat(qInstance.getJoin(SqlWorkbenchTableMetaDataHelper.JOIN_NOTEBOOK_EXECUTION).getRightTable()).isEqualTo(SqlWorkbenchExecution.TABLE_NAME);
      assertThat(qInstance.getWidget(SqlWorkbenchTableMetaDataHelper.JOIN_NOTEBOOK_EXECUTION).getLabel()).isEqualTo("Runs");
      assertThat(notebook.getSections().stream().filter(s -> s.getWidgetName() != null).map(QFieldSection::getLabel).toList())
         .containsExactly("Runner", "Cells", "Shares", "Snapshots (pinned results)", "Versions", "Runs");
      assertThat(notebook.getSections().get(0).getFieldNames()).containsExactly("id", "title", "description", "ownerName", "visibility");
   }



   /*******************************************************************************
    ** The Runs child list goes through the execution audit's lock: the
    ** viewer's own runs of the notebook, every user's for an admin.
    *******************************************************************************/
   @Test
   void testRunsChildListIsLocked() throws Exception
   {
      loginAs(WRITER);
      QRecord notebook = new InsertAction().execute(new InsertInput(SqlWorkbenchNotebook.TABLE_NAME).withRecord(new QRecord().withValue("title", "Shared one"))).getRecords().get(0);
      Integer id       = notebook.getValueInteger("id");
      new InsertAction().execute(new InsertInput(SqlWorkbenchNotebookShare.TABLE_NAME).withRecord(new QRecord()
         .withValue("notebookId", id).withValue("audienceType", "USER").withValue("audienceId", READER)));
      SystemUser.run(() ->
      {
         audit(WRITER, id);
         audit(READER, id);
         audit(READER, id);
      });

      assertThat(runs(id)).hasSize(1);
      loginAs(READER);
      assertThat(runs(id)).hasSize(2).allSatisfy(r -> assertThat(r.getValueString("userId")).isEqualTo(READER));
      loginAs(ADMIN);
      assertThat(runs(id)).hasSize(3);
      assertThat(runs(id).get(0).getValueInteger("id")).isGreaterThan(runs(id).get(2).getValueInteger("id"));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testIntegrationHooks()
   {
      assertThat(SqlWorkbenchIntegrations.mainTableNames(config)).containsExactly(SqlWorkbenchNotebook.TABLE_NAME, SqlWorkbenchExecution.TABLE_NAME);
      assertThat(SqlWorkbenchIntegrations.esbTableNames(config)).isEqualTo(SqlWorkbenchIntegrations.mainTableNames(config));

      List<SqlWorkbenchIntegrations.SearchableTable> searchable = SqlWorkbenchIntegrations.quickSearchTables(config);
      assertThat(searchable).extracting(SqlWorkbenchIntegrations.SearchableTable::tableName).containsExactlyElementsOf(SqlWorkbenchIntegrations.mainTableNames(config));
      for(SqlWorkbenchIntegrations.SearchableTable table : searchable)
      {
         QTableMetaData tableMetaData = qInstance.getTable(table.tableName());
         assertThat(table.fieldWeights().keySet()).allSatisfy(field -> assertThat(tableMetaData.getFields()).containsKey(field));
         assertThat(tableMetaData.getFields()).containsKey(table.basepullTimestamp());
         assertThat(table.recordLabelFields()).allSatisfy(field -> assertThat(tableMetaData.getFields()).containsKey(field));
      }
      assertThat(searchable.get(0).fieldWeights()).containsEntry("title", 10).containsKey("description").containsKey("ownerName");
      assertThat(searchable.get(1).fieldWeights()).containsKey("sqlText").containsKey("userName").doesNotContainKey("parametersJson");

      ///////////////////////////////////////
      // the hooks follow the table prefix //
      ///////////////////////////////////////
      SqlWorkbenchQBitConfig prefixed = new SqlWorkbenchQBitConfig().withTableNamePrefix("myNs");
      assertThat(SqlWorkbenchIntegrations.esbTableNames(prefixed)).containsExactly("my_ns_sql_workbench_notebook", "my_ns_sql_workbench_execution");
      assertThat(SqlWorkbenchIntegrations.quickSearchTables(prefixed).get(1).tableName()).isEqualTo("my_ns_sql_workbench_execution");
   }



   /*******************************************************************************
    ** The notebook's Runs child list as the session sees it.
    *******************************************************************************/
   private List<QRecord> runs(Integer notebookId) throws QException
   {
      QWidgetMetaDataInterface widget = qInstance.getWidget(SqlWorkbenchTableMetaDataHelper.JOIN_NOTEBOOK_EXECUTION);
      Map<String, String>      params = new HashMap<>();
      params.put("id", String.valueOf(notebookId));
      ChildRecordListData data = (ChildRecordListData) new RenderWidgetAction().execute(new RenderWidgetInput().withWidgetMetaData(widget).withUrlParams(params)).getWidgetData();
      return (data.getQueryOutput().getRecords());
   }



   /*******************************************************************************
    ** One audit row.
    *******************************************************************************/
   private static void audit(String user, Integer notebookId) throws QException
   {
      Map<String, Serializable> values = new HashMap<>();
      values.put("executionUuid", UUID.randomUUID().toString());
      values.put("userId", user);
      values.put("mode", "READ_ONLY");
      values.put("status", "SUCCEEDED");
      values.put("sqlText", "SELECT 1");
      values.put("notebookId", notebookId);
      values.put("startTime", Instant.now());
      QRecord record = new QRecord();
      values.forEach(record::setValue);
      new InsertAction().execute(new InsertInput(SqlWorkbenchExecution.TABLE_NAME).withRecord(record));
   }

}
