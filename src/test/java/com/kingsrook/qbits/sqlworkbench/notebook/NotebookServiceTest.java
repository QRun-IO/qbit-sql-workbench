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

package com.kingsrook.qbits.sqlworkbench.notebook;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QNotFoundException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qbits.sqlworkbench.BaseSqlWorkbenchTest;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchRuntime;
import com.kingsrook.qbits.sqlworkbench.engine.ExecutionRequest;
import com.kingsrook.qbits.sqlworkbench.engine.SqlExecutionEngine;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchCell;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Notebook CRUD, visibility, versions and snapshots.
 *******************************************************************************/
class NotebookServiceTest extends BaseSqlWorkbenchTest
{
   private NotebookService service;



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   protected void customizeConfig(SqlWorkbenchQBitConfig config)
   {
      config.setNotebookVersionRetention(3);
      config.setSnapshotMaxRows(2);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      service = new NotebookService(SqlWorkbenchRuntime.get(qInstance));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Map<String, Object> body(String title, List<Map<String, Object>> cells)
   {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("title", title);
      body.put("defaultBackendName", BACKEND_NAME);
      body.put("parameters", List.of(Map.of("name", "since", "type", "DATE", "defaultValue", "2026-01-01")));
      body.put("cells", cells);
      return (body);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Map<String, Object> cell(String key, String type, String content)
   {
      Map<String, Object> cell = new LinkedHashMap<>();
      cell.put("cellKey", key);
      cell.put("cellType", type);
      cell.put("content", content);
      return (cell);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void testCreateGetUpdateVersions() throws Exception
   {
      Map<String, Object> created = service.create(body("People", List.of(cell("a", "MARKDOWN", "# Hi"), cell("b", "sql", "SELECT 1"))), as(READER));
      Integer id = (Integer) created.get("id");
      assertThat(created.get("visibility")).isEqualTo("PERSONAL");
      assertThat(created.get("currentVersion")).isEqualTo(1);
      assertThat(created.get("canEdit")).isEqualTo(true);
      assertThat((List<Map<String, Object>>) created.get("cells")).extracting(c -> c.get("cellType")).containsExactly("MARKDOWN", "SQL");
      assertThat((List<Object>) created.get("parameters")).hasSize(1);

      Map<String, Object> update = body("People v2", List.of(cell("b", "SQL", "SELECT 2")));
      update.put("comment", "trim");
      Map<String, Object> updated = service.update(id, update, as(READER));
      assertThat(updated.get("title")).isEqualTo("People v2");
      assertThat(updated.get("currentVersion")).isEqualTo(2);
      assertThat((List<?>) updated.get("cells")).hasSize(1);
      assertThat(QueryAction.execute(SqlWorkbenchCell.TABLE_NAME, new QQueryFilter())).hasSize(1);

      List<Map<String, Object>> versions = service.versions(id, as(READER));
      assertThat(versions).extracting(v -> v.get("versionNumber")).containsExactly(2, 1);
      assertThat(versions.get(0).get("comment")).isEqualTo("trim");

      Map<String, Object> v1 = service.version(id, 1, as(READER));
      assertThat(((Map<String, Object>) v1.get("notebook")).get("title")).isEqualTo("People");

      Map<String, Object> restored = service.restore(id, 1, as(READER));
      assertThat(restored.get("title")).isEqualTo("People");
      assertThat(restored.get("currentVersion")).isEqualTo(3);
      assertThat((List<?>) restored.get("cells")).hasSize(2);

      ////////////////////////////////////
      // retention keeps the newest 3   //
      ////////////////////////////////////
      service.update(id, Map.of("title", "People v4"), as(READER));
      assertThat(QueryAction.execute(SqlWorkbenchNotebookVersion.TABLE_NAME, new QQueryFilter())).hasSize(3);
      assertThat(service.get(id, as(READER)).get("cells")).asList().hasSize(2);

      assertThatThrownBy(() -> service.version(id, 1, as(READER))).isInstanceOf(QNotFoundException.class);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testVisibilityAndSharing() throws Exception
   {
      Integer mine = (Integer) service.create(body("Mine", List.of()), as(WRITER)).get("id");

      assertThat(service.list("mine", as(WRITER))).hasSize(1);
      assertThat(service.list("mine", as(READER))).isEmpty();
      assertThat(service.list("shared", as(READER))).isEmpty();
      assertThatThrownBy(() -> service.get(mine, as(READER))).isInstanceOf(QNotFoundException.class);

      assertThatThrownBy(() -> service.publish(mine, true, as(READER))).isInstanceOf(QPermissionDeniedException.class);
      assertThat(service.publish(mine, true, as(WRITER)).get("visibility")).isEqualTo("SHARED");

      assertThat(service.list("shared", as(READER))).hasSize(1);
      assertThat(service.get(mine, as(READER)).get("canEdit")).isEqualTo(false);
      assertThatThrownBy(() -> service.update(mine, Map.of("title", "x"), as(READER))).isInstanceOf(QPermissionDeniedException.class);
      assertThatThrownBy(() -> service.delete(mine, as(READER))).isInstanceOf(QPermissionDeniedException.class);

      Map<String, Object> copy = service.duplicate(mine, as(READER));
      assertThat(copy.get("title")).isEqualTo("Copy of Mine");
      assertThat(copy.get("ownerUserId")).isEqualTo(READER);
      assertThat(copy.get("visibility")).isEqualTo("PERSONAL");

      assertThat(service.publish(mine, false, as(WRITER)).get("visibility")).isEqualTo("PERSONAL");
      assertThat(service.list("shared", as(READER))).isEmpty();

      service.delete(mine, as(WRITER));
      assertThatThrownBy(() -> service.get(mine, as(WRITER))).isInstanceOf(QNotFoundException.class);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testValidation() throws Exception
   {
      assertThatThrownBy(() -> service.create(body(" ", List.of()), as(READER))).isInstanceOf(QBadRequestException.class);
      assertThatThrownBy(() -> service.create(body("x", List.of(cell("a", "PYTHON", "x"))), as(READER))).isInstanceOf(QBadRequestException.class);
      Map<String, Object> notAList = body("x", null);
      notAList.put("cells", "nope");
      assertThatThrownBy(() -> service.create(notAList, as(READER))).isInstanceOf(QBadRequestException.class);
      Map<String, Object> notObjects = body("x", null);
      notObjects.put("cells", List.of("nope"));
      assertThatThrownBy(() -> service.create(notObjects, as(READER))).isInstanceOf(QBadRequestException.class);

      List<Map<String, Object>> tooMany = new ArrayList<>();
      for(int i = 0; i <= NotebookService.MAX_CELLS; i++)
      {
         tooMany.add(cell("k" + i, "SQL", "SELECT 1"));
      }
      assertThatThrownBy(() -> service.create(body("x", tooMany), as(READER))).isInstanceOf(QBadRequestException.class);
      assertThatThrownBy(() -> service.get(null, as(READER))).isInstanceOf(QBadRequestException.class);

      ///////////////////////////////////////////////////////
      // duplicate or missing keys are replaced by UUIDs   //
      ///////////////////////////////////////////////////////
      List<SqlWorkbenchCell> cells = service.parseCells(List.of(cell("same", "SQL", "1"), cell("same", "SQL", "2"), cell(null, null, null)));
      assertThat(cells).extracting(SqlWorkbenchCell::getCellKey).doesNotHaveDuplicates();
      assertThat(cells.get(2).getCellType()).isEqualTo("SQL");
      assertThat(cells.get(2).getContent()).isEmpty();
      assertThat(NotebookService.truncate("abcdef", 4)).isEqualTo("abc…");
      assertThat(NotebookService.parseParameters("not json")).isEmpty();
      assertThat(NotebookService.parseJsonObject(null)).isEmpty();
   }



   /*******************************************************************************
    ** With QQQ core permission enforcement on, an ordinary user's permission set
    ** (getUserPermissionNames) is enough for the whole notebook flow, including
    ** pinning a result (a top-level snapshot insert).
    *******************************************************************************/
   @Test
   void testUserPermissionsSufficeWithCoreEnforcement() throws Exception
   {
      qInstance.setEnforcePermissionsInCoreActions(true);
      try
      {
         Integer id = (Integer) service.create(body("Enforced", List.of(cell("c1", "SQL", "SELECT * FROM person"))), as(READER)).get("id");
         service.update(id, Map.of("title", "Enforced v2"), as(READER));

         SqlExecutionEngine  engine   = new SqlExecutionEngine(qInstance, SqlWorkbenchRuntime.get(qInstance));
         Map<String, Object> response = engine.execute(ExecutionRequest.readOnly(BACKEND_NAME, "SELECT * FROM person", Map.of()), as(READER));

         Map<String, Object> snapshot = service.createSnapshot(id, Map.of("executionUuid", response.get("executionUuid"), "cellKey", "c1"), as(READER), "SELECT * FROM person");
         assertThat(snapshot.get("id")).isNotNull();
         service.deleteSnapshot(id, (Integer) snapshot.get("id"), as(READER));
         service.delete(id, as(READER));
      }
      finally
      {
         qInstance.setEnforcePermissionsInCoreActions(null);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void testSnapshots() throws Exception
   {
      Integer id = (Integer) service.create(body("Snap", List.of(cell("c1", "SQL", "SELECT * FROM person"))), as(READER)).get("id");

      SqlExecutionEngine  engine   = new SqlExecutionEngine(qInstance, SqlWorkbenchRuntime.get(qInstance));
      Map<String, Object> response = engine.execute(ExecutionRequest.readOnly(BACKEND_NAME, "SELECT * FROM person", Map.of()), as(READER));
      String              uuid     = (String) response.get("executionUuid");

      assertThatThrownBy(() -> service.createSnapshot(id, Map.of("executionUuid", "missing"), as(READER), null)).isInstanceOf(QNotFoundException.class);

      Map<String, Object> snapshot = service.createSnapshot(id, Map.of("executionUuid", uuid, "cellKey", "c1", "label", "people"), as(READER), "SELECT * FROM person");
      assertThat(snapshot.get("rowCount")).isEqualTo(2);
      assertThat(snapshot.get("truncated")).isEqualTo(true);
      assertThat(((List<?>) ((Map<String, Object>) snapshot.get("result")).get("rows"))).hasSize(2);

      List<Map<String, Object>> snapshots = service.snapshots(id, as(READER));
      assertThat(snapshots).hasSize(1);

      Integer snapshotId = (Integer) snapshot.get("id");
      assertThatThrownBy(() -> service.deleteSnapshot(id, 999, as(READER))).isInstanceOf(QNotFoundException.class);
      service.deleteSnapshot(id, snapshotId, as(READER));
      assertThat(service.snapshots(id, as(READER))).isEmpty();
   }

}
