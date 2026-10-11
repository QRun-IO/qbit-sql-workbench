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
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.actions.permissions.TablePermissionSubType;
import com.kingsrook.qqq.backend.core.exceptions.QNotFoundException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qbits.sqlworkbench.BaseSqlWorkbenchTest;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchRuntime;
import com.kingsrook.qbits.sqlworkbench.api.SqlWorkbenchApi;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchCell;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchExecution;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebook;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookVersion;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchResultSnapshot;
import com.kingsrook.qqq.middleware.javalin.executors.TableQueryExecutor;
import com.kingsrook.qqq.middleware.javalin.executors.io.TableQueryInput;
import com.kingsrook.qqq.middleware.javalin.executors.io.TableQueryOutputInterface;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** The qbit's tables are not reachable through the generic QQQ table API by
 ** ordinary workbench users (only by holders of the admin permission), while
 ** the workbench's own endpoints keep working for them.
 *******************************************************************************/
class TableAccessTest extends BaseSqlWorkbenchTest
{
   private static final List<String> TABLES = List.of(SqlWorkbenchNotebook.TABLE_NAME, SqlWorkbenchCell.TABLE_NAME, SqlWorkbenchNotebookVersion.TABLE_NAME,
      SqlWorkbenchResultSnapshot.TABLE_NAME, SqlWorkbenchExecution.TABLE_NAME);



   /*******************************************************************************
    ** Query a table the way /qqq/v1/table/{table}/query does.
    *******************************************************************************/
   private static List<QRecord> tableApiQuery(String tableName) throws Exception
   {
      List<QRecord> records = new ArrayList<>();
      TableQueryInput input = new TableQueryInput();
      input.setTableName(tableName);
      new TableQueryExecutor().execute(input, (TableQueryOutputInterface) records::addAll);
      return (records);
   }



   /*******************************************************************************
    ** A writer's personal notebook (with a cell, a version and a pinned
    ** snapshot) and an audited execution.
    *******************************************************************************/
   private Integer seedWriterData(SqlWorkbenchApi api) throws Exception
   {
      loginAs(WRITER);
      Map<String, Object> notebook = api.notebooks().create(Map.of("title", "Private payroll", "cells",
         List.of(Map.of("cellKey", "a", "cellType", "SQL", "content", "SELECT salary FROM person"))), caller(WRITER));
      Integer id = (Integer) notebook.get("id");

      Map<String, Object> response = api.execute(caller(WRITER), Map.of("backendName", BACKEND_NAME, "sql", "SELECT first_name, salary FROM person ORDER BY id"));
      assertThat(response.get("status")).isEqualTo("SUCCEEDED");
      api.createSnapshot(caller(WRITER), id, Map.of("executionUuid", response.get("executionUuid"), "cellKey", "a"));
      return (id);
   }



   /*******************************************************************************
    ** Another ordinary user cannot read the writer's personal notebook, cells,
    ** versions, snapshot or audit row through the generic table API, nor
    ** write any of those tables; the owner cannot either (no generic access
    ** at all). The workbench API still hides the notebook from the other user.
    *******************************************************************************/
   @Test
   void testOrdinaryUsersHaveNoGenericTableAccess() throws Exception
   {
      SqlWorkbenchApi api = new SqlWorkbenchApi(qInstance, SqlWorkbenchRuntime.get(qInstance));
      Integer         id  = seedWriterData(api);

      for(String user : List.of(OTHER_READER, WRITER))
      {
         loginAs(user);
         for(String table : TABLES)
         {
            assertThatThrownBy(() -> tableApiQuery(table)).as(user + " querying " + table).isInstanceOf(QPermissionDeniedException.class);
            for(TablePermissionSubType subType : List.of(TablePermissionSubType.READ, TablePermissionSubType.INSERT, TablePermissionSubType.EDIT, TablePermissionSubType.DELETE))
            {
               assertThat(PermissionsHelper.hasTablePermission(new QueryInput(table), table, subType)).as(user + " " + subType + " " + table).isFalse();
            }
         }
      }

      ///////////////////////////////////////////////////////////////////
      // through the workbench API the notebook does not exist for the //
      // other reader, and their history holds none of the writer's    //
      ///////////////////////////////////////////////////////////////////
      loginAs(OTHER_READER);
      assertThatThrownBy(() -> api.notebooks().get(id, caller(OTHER_READER))).isInstanceOf(QNotFoundException.class);
      assertThatThrownBy(() -> api.notebooks().snapshots(id, caller(OTHER_READER))).isInstanceOf(QNotFoundException.class);
      assertThat(api.notebooks().list("mine", caller(OTHER_READER))).isEmpty();
      assertThat(api.notebooks().list("shared", caller(OTHER_READER))).isEmpty();
      assertThat((List<?>) api.history(caller(OTHER_READER), 50, null).get("executions")).isEmpty();
   }



   /*******************************************************************************
    ** The workbench's own endpoints work for an ordinary user (session without
    ** the admin permission): notebooks, versions, snapshots, history.
    *******************************************************************************/
   @Test
   void testWorkbenchApiStillWorksForOrdinaryUsers() throws Exception
   {
      SqlWorkbenchApi api = new SqlWorkbenchApi(qInstance, SqlWorkbenchRuntime.get(qInstance));
      Integer         id  = seedWriterData(api);

      loginAs(WRITER);
      assertThat(api.notebooks().get(id, caller(WRITER)).get("title")).isEqualTo("Private payroll");
      assertThat(api.notebooks().update(id, Map.of("title", "Payroll"), caller(WRITER)).get("currentVersion")).isEqualTo(2);
      assertThat(api.notebooks().versions(id, caller(WRITER))).hasSize(2);
      assertThat(api.notebooks().snapshots(id, caller(WRITER))).hasSize(1);
      assertThat(api.notebooks().list("mine", caller(WRITER))).hasSize(1);
      assertThat((List<?>) api.history(caller(WRITER), 50, null).get("executions")).hasSize(1);

      api.notebooks().publish(id, true, caller(WRITER));
      loginAs(READER);
      assertThat(api.notebooks().get(id, caller(READER)).get("canEdit")).isEqualTo(false);
      assertThat(api.notebooks().duplicate(id, caller(READER)).get("ownerUserId")).isEqualTo(READER);

      loginAs(WRITER);
      api.notebooks().delete(id, caller(WRITER));
      assertThat(api.notebooks().list("mine", caller(WRITER))).isEmpty();
   }



   /*******************************************************************************
    ** A holder of the admin permission can read everything through the
    ** generic table API.
    *******************************************************************************/
   @Test
   void testAdminHasGenericTableAccess() throws Exception
   {
      SqlWorkbenchApi api = new SqlWorkbenchApi(qInstance, SqlWorkbenchRuntime.get(qInstance));
      seedWriterData(api);

      loginAs(ADMIN);
      assertThat(tableApiQuery(SqlWorkbenchNotebook.TABLE_NAME)).hasSize(1);
      assertThat(tableApiQuery(SqlWorkbenchCell.TABLE_NAME)).hasSize(1);
      assertThat(tableApiQuery(SqlWorkbenchNotebookVersion.TABLE_NAME)).hasSize(1);
      assertThat(tableApiQuery(SqlWorkbenchResultSnapshot.TABLE_NAME)).hasSize(1);
      assertThat(tableApiQuery(SqlWorkbenchExecution.TABLE_NAME)).hasSize(1);
   }

}
