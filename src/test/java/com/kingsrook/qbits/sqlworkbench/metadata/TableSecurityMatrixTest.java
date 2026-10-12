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


import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import com.kingsrook.qbits.sqlworkbench.BaseSqlWorkbenchTest;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchRuntime;
import com.kingsrook.qbits.sqlworkbench.SystemUser;
import com.kingsrook.qbits.sqlworkbench.api.SqlWorkbenchApi;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchCell;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchExecution;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebook;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookShare;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookVersion;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchResultSnapshot;
import com.kingsrook.qbits.sqlworkbench.processes.SqlWorkbenchRecordActionSteps;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.actions.permissions.TablePermissionSubType;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
import com.kingsrook.qqq.backend.core.actions.tables.CountAction;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.GetAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.actions.values.SearchPossibleValueSourceAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QNotFoundException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.count.CountInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.get.GetInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.actions.values.SearchPossibleValueSourceInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.middleware.javalin.executors.TableCountExecutor;
import com.kingsrook.qqq.middleware.javalin.executors.TableDeleteExecutor;
import com.kingsrook.qqq.middleware.javalin.executors.TableExportExecutor;
import com.kingsrook.qqq.middleware.javalin.executors.TableGetExecutor;
import com.kingsrook.qqq.middleware.javalin.executors.TableInsertExecutor;
import com.kingsrook.qqq.middleware.javalin.executors.TableQueryExecutor;
import com.kingsrook.qqq.middleware.javalin.executors.TableUpdateExecutor;
import com.kingsrook.qqq.middleware.javalin.executors.io.TableCountInput;
import com.kingsrook.qqq.middleware.javalin.executors.io.TableCountOutputInterface;
import com.kingsrook.qqq.middleware.javalin.executors.io.TableDeleteInput;
import com.kingsrook.qqq.middleware.javalin.executors.io.TableDeleteOutputInterface;
import com.kingsrook.qqq.middleware.javalin.executors.io.TableExportInput;
import com.kingsrook.qqq.middleware.javalin.executors.io.TableExportOutputInterface;
import com.kingsrook.qqq.middleware.javalin.executors.io.TableGetInput;
import com.kingsrook.qqq.middleware.javalin.executors.io.TableGetOutputInterface;
import com.kingsrook.qqq.middleware.javalin.executors.io.TableInsertInput;
import com.kingsrook.qqq.middleware.javalin.executors.io.TableInsertOutputInterface;
import com.kingsrook.qqq.middleware.javalin.executors.io.TableQueryInput;
import com.kingsrook.qqq.middleware.javalin.executors.io.TableQueryOutputInterface;
import com.kingsrook.qqq.middleware.javalin.executors.io.TableUpdateInput;
import com.kingsrook.qqq.middleware.javalin.executors.io.TableUpdateOutputInterface;
import com.kingsrook.qqq.backend.core.model.actions.reporting.ReportFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** The workbench tables' security, role x table x action: who sees which
 ** rows through the /qqq/v1 table API (query, count, get, export), the core
 ** actions, PVS lookups and record actions, and who may write them.
 **
 ** Fixture (all created by OWNER through the notebook endpoints or tables):
 ** - "Private": personal, one cell, one pinned result
 ** - "For reader-3": shared with the user reader-3
 ** - "For analysts": shared with the group Analysts (reader-4 is a member)
 ** - "Library": published to the shared library (everyone)
 ** - "Other's own": OTHER's personal notebook
 ** Every notebook has one cell and (at least) one version.
 *******************************************************************************/
class TableSecurityMatrixTest extends BaseSqlWorkbenchTest
{
   private static final String OWNER        = WRITER;
   private static final String OTHER        = OTHER_READER;
   private static final String SHARED_USER  = "reader-3";
   private static final String GROUP_MEMBER = "reader-4";
   private static final String GROUP        = "Analysts";
   private static final String OTHER_SHARER = "writer-2";
   private static final String OUTSIDER     = "nobody-1";

   private static final List<String> ALL_TITLES = List.of("Private", "For reader-3", "For analysts", "Library", "Other's own");

   private SqlWorkbenchApi     api;
   private Map<String, Integer> ids = new LinkedHashMap<>();



   /*******************************************************************************
    **
    *******************************************************************************/
   @BeforeEach
   void seed() throws Exception
   {
      api = new SqlWorkbenchApi(qInstance, SqlWorkbenchRuntime.get(qInstance));
      for(String title : List.of("Private", "For reader-3", "For analysts", "Library"))
      {
         ids.put(title, notebook(OWNER, title));
      }
      ids.put("Other's own", notebook(OTHER, "Other's own"));

      loginAs(OWNER);
      insertShare(ids.get("For reader-3"), "USER", SHARED_USER);
      tableInsert(SqlWorkbenchNotebookShare.TABLE_NAME, Map.of("notebookId", ids.get("For analysts"), "audienceType", "GROUP", "audienceId", GROUP));
      api.notebooks().publish(ids.get("Library"), true, api.caller(null));

      Map<String, Object> run = api.execute(as(OWNER), Map.of("backendName", BACKEND_NAME, "sql", "SELECT first_name, salary FROM person ORDER BY id"));
      api.createSnapshot(as(OWNER), ids.get("Private"), Map.of("executionUuid", run.get("executionUuid"), "cellKey", "c1"));
      api.execute(as(OTHER), Map.of("backendName", BACKEND_NAME, "sql", "SELECT 1"));
   }



   /*******************************************************************************
    ** Create a notebook with one cell through the notebook endpoint.
    *******************************************************************************/
   private Integer notebook(String user, String title) throws Exception
   {
      return ((Integer) api.notebooks().create(Map.of("title", title, "cells", List.of(Map.of("cellKey", "c1", "cellType", "SQL", "content", "SELECT 1"))), as(user)).get("id"));
   }



   /*******************************************************************************
    ** Log in as a user (the group member with their group).
    *******************************************************************************/
   private void login(String user)
   {
      if(GROUP_MEMBER.equals(user))
      {
         loginAs(user, GROUP);
      }
      else
      {
         loginAs(user);
      }
   }



   /*******************************************************************************
    ** The notebooks each user may read.
    *******************************************************************************/
   private static Set<String> readableTitles(String user)
   {
      return (new TreeSet<>(switch(user)
      {
         case OWNER -> List.of("Private", "For reader-3", "For analysts", "Library");
         case OTHER -> List.of("Library", "Other's own");
         case SHARED_USER -> List.of("For reader-3", "Library");
         case GROUP_MEMBER -> List.of("For analysts", "Library");
         case OTHER_SHARER -> List.of("Library");
         case ADMIN -> ALL_TITLES;
         default -> List.<String>of();
      }));
   }



   /*******************************************************************************
    ** Reads: v1 query / count / get / export, core query, and PVS lookups
    ** agree for every role on every table.
    *******************************************************************************/
   @Test
   void testReadMatrix() throws Exception
   {
      for(String user : List.of(OWNER, OTHER, SHARED_USER, GROUP_MEMBER, OTHER_SHARER, ADMIN))
      {
         login(user);
         Set<String> titles   = readableTitles(user);
         int         readable = titles.size();

         //////////////////////////////////////////////
         // notebooks: v1 query, count, export, get  //
         //////////////////////////////////////////////
         assertThat(new TreeSet<>(v1Query(SqlWorkbenchNotebook.TABLE_NAME).stream().map(r -> r.getValueString("title")).toList())).as(user + " v1 query notebooks").isEqualTo(titles);
         assertThat(v1Count(SqlWorkbenchNotebook.TABLE_NAME)).as(user + " v1 count notebooks").isEqualTo(readable);
         assertThat(v1ExportRows(SqlWorkbenchNotebook.TABLE_NAME)).as(user + " v1 export notebooks").isEqualTo(readable);
         for(String title : ALL_TITLES)
         {
            Integer id = ids.get(title);
            if(titles.contains(title))
            {
               assertThat(v1Get(SqlWorkbenchNotebook.TABLE_NAME, id).getValueString("title")).as(user + " v1 get " + title).isEqualTo(title);
               assertThat(new GetAction().executeForRecord(new GetInput(SqlWorkbenchNotebook.TABLE_NAME).withPrimaryKey(id))).as(user + " core get " + title).isNotNull();
            }
            else
            {
               assertThatThrownBy(() -> v1Get(SqlWorkbenchNotebook.TABLE_NAME, id)).as(user + " v1 get " + title).isInstanceOf(QNotFoundException.class);
               assertThat(new GetAction().executeForRecord(new GetInput(SqlWorkbenchNotebook.TABLE_NAME).withPrimaryKey(id))).as(user + " core get " + title).isNull();
            }
         }

         /////////////////////////////////////////////////////////////////////////
         // children follow their notebook: one cell and one version per        //
         // readable notebook; only "Private" has a pinned result               //
         /////////////////////////////////////////////////////////////////////////
         assertThat(v1Count(SqlWorkbenchCell.TABLE_NAME)).as(user + " cells").isEqualTo(readable);
         assertThat(v1Query(SqlWorkbenchNotebookVersion.TABLE_NAME)).as(user + " versions").extracting(r -> r.getValueInteger("notebookId"))
            .containsExactlyInAnyOrderElementsOf(titles.stream().map(ids::get).toList());
         assertThat(v1Count(SqlWorkbenchResultSnapshot.TABLE_NAME)).as(user + " snapshots").isEqualTo(titles.contains("Private") ? 1 : 0);
         assertThat(v1ExportRows(SqlWorkbenchCell.TABLE_NAME)).as(user + " export cells").isEqualTo(readable);

         ////////////////////////////////////////////////////////////////////
         // shares: the owner sees their notebooks' shares, an audience    //
         // sees the shares that name it, admins see all                   //
         ////////////////////////////////////////////////////////////////////
         List<String> shareKeys = v1Query(SqlWorkbenchNotebookShare.TABLE_NAME).stream().map(r -> r.getValueString("audienceKey")).sorted().toList();
         assertThat(shareKeys).as(user + " shares").isEqualTo(switch(user)
         {
            case OWNER, ADMIN -> List.of("all", "group:analysts", "user:reader-3");
            case SHARED_USER -> List.of("all", "user:reader-3");
            case GROUP_MEMBER -> List.of("all", "group:analysts");
            default -> List.of("all");
         });

         /////////////////////////////////////////////////
         // the audit: own executions; admins see all   //
         /////////////////////////////////////////////////
         assertThat(v1Query(SqlWorkbenchExecution.TABLE_NAME)).as(user + " executions").extracting(r -> r.getValueString("userId"))
            .containsExactlyInAnyOrderElementsOf(switch(user)
            {
               case OWNER -> List.of(OWNER);
               case OTHER -> List.of(OTHER);
               case ADMIN -> List.of(OWNER, OTHER);
               default -> List.of();
            });

         ////////////////////////////////////////////////////////////////
         // notebook PVS lookups (labels of notebookId fields) only    //
         // show readable notebooks                                    //
         ////////////////////////////////////////////////////////////////
         List<String> labels = new ArrayList<>();
         new SearchPossibleValueSourceAction().execute(new SearchPossibleValueSourceInput().withPossibleValueSourceName(SqlWorkbenchNotebook.TABLE_NAME).withSearchTerm(""))
            .getResults().forEach(pv -> labels.add(pv.getLabel()));
         assertThat(new TreeSet<>(labels)).as(user + " notebook PVS").isEqualTo(titles);
      }
   }



   /*******************************************************************************
    ** Users without workbench access: no table permission (v1 refuses), and
    ** no security keys (the core actions find nothing, not even the library).
    *******************************************************************************/
   @Test
   void testOutsider() throws Exception
   {
      loginAs(OUTSIDER);
      for(String table : AppTables.ALL)
      {
         assertThatThrownBy(() -> v1Query(table)).as(table).isInstanceOf(QPermissionDeniedException.class);
         for(TablePermissionSubType subType : TablePermissionSubType.values())
         {
            assertThat(PermissionsHelper.hasTablePermission(new QueryInput(table), table, subType)).as(subType + " " + table).isFalse();
         }
         assertThat(new CountAction().execute(new CountInput(table)).getCount()).as("core count " + table).isZero();
      }
   }



   /*******************************************************************************
    ** Writes: only the owner (or an admin) changes a notebook or its children;
    ** being shared with, or seeing it in the library, is read-only.
    *******************************************************************************/
   @Test
   void testWriteMatrix() throws Exception
   {
      Integer privateId = ids.get("Private");
      Integer userShared = ids.get("For reader-3");
      Integer groupShared = ids.get("For analysts");
      Integer library = ids.get("Library");

      //////////////////////////////////////////////////////////////
      // readable but not theirs: update / delete / child writes  //
      //////////////////////////////////////////////////////////////
      login(SHARED_USER);
      assertThatThrownBy(() -> v1Update(SqlWorkbenchNotebook.TABLE_NAME, userShared, Map.of("title", "hijacked"))).isInstanceOf(QException.class);
      assertThat(v1Delete(SqlWorkbenchNotebook.TABLE_NAME, userShared)).isZero();
      assertThatThrownBy(() -> tableInsert(SqlWorkbenchCell.TABLE_NAME, Map.of("notebookId", userShared, "content", "SELECT 2"))).isInstanceOf(QException.class);
      Integer sharedCell = cellOf(userShared);
      assertThatThrownBy(() -> v1Update(SqlWorkbenchCell.TABLE_NAME, sharedCell, Map.of("content", "DROP TABLE x"))).isInstanceOf(QException.class);
      assertThat(v1Delete(SqlWorkbenchCell.TABLE_NAME, sharedCell)).isZero();

      login(GROUP_MEMBER);
      Integer groupCell = cellOf(groupShared);
      assertThatThrownBy(() -> v1Update(SqlWorkbenchCell.TABLE_NAME, groupCell, Map.of("content", "x"))).isInstanceOf(QException.class);

      login(OTHER);
      assertThatThrownBy(() -> v1Update(SqlWorkbenchNotebook.TABLE_NAME, library, Map.of("title", "hijacked"))).isInstanceOf(QException.class);
      assertThat(v1Delete(SqlWorkbenchNotebook.TABLE_NAME, library)).isZero();
      assertThatThrownBy(() -> v1Update(SqlWorkbenchNotebook.TABLE_NAME, privateId, Map.of("title", "hijacked"))).isInstanceOf(QException.class);
      assertThatThrownBy(() -> tableInsert(SqlWorkbenchCell.TABLE_NAME, Map.of("notebookId", privateId, "content", "SELECT 2"))).isInstanceOf(QException.class);

      ////////////////////////////////////////////////////////////////////////////
      // sharing: needs the share permission, and only for one's own notebooks //
      ////////////////////////////////////////////////////////////////////////////
      assertThatThrownBy(() -> tableInsert(SqlWorkbenchNotebookShare.TABLE_NAME, Map.of("notebookId", library, "audienceType", "USER", "audienceId", OTHER)))
         .isInstanceOf(QPermissionDeniedException.class);
      login(OTHER_SHARER);
      assertThatThrownBy(() -> tableInsert(SqlWorkbenchNotebookShare.TABLE_NAME, Map.of("notebookId", library, "audienceType", "USER", "audienceId", OTHER_SHARER)))
         .isInstanceOf(QException.class);
      Integer libraryShare = shareOf(library);
      assertThat(v1Delete(SqlWorkbenchNotebookShare.TABLE_NAME, libraryShare)).isZero();

      //////////////////////////////////////////////////////////////
      // inserting a notebook "for" someone else makes it yours   //
      //////////////////////////////////////////////////////////////
      login(OTHER);
      QRecord forged = tableInsert(SqlWorkbenchNotebook.TABLE_NAME, Map.of("title", "Forged", "ownerUserId", OWNER, "visibility", "SHARED"));
      assertThat(forged.getValueString("ownerUserId")).isEqualTo(OTHER);
      assertThat(forged.getValueString("visibility")).isEqualTo("PERSONAL");

      ///////////////////////////////////////////////////
      // nothing above changed the owner's notebooks   //
      ///////////////////////////////////////////////////
      List<QRecord> stored = SystemUser.call(() -> QueryAction.execute(SqlWorkbenchNotebook.TABLE_NAME, new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.IN, List.of(privateId, userShared, groupShared, library)))));
      assertThat(stored).extracting(r -> r.getValueString("title")).containsExactlyInAnyOrder("Private", "For reader-3", "For analysts", "Library");
      assertThat(SystemUser.call(() -> QueryAction.execute(SqlWorkbenchCell.TABLE_NAME, new QQueryFilter()))).extracting(r -> r.getValueString("content")).containsOnly("SELECT 1");

      //////////////////////////////////////////////////////
      // the owner, and an admin, may change the notebook //
      //////////////////////////////////////////////////////
      login(OWNER);
      assertThat(v1Update(SqlWorkbenchNotebook.TABLE_NAME, privateId, Map.of("title", "Private v2")).getValueString("title")).isEqualTo("Private v2");
      login(ADMIN);
      assertThat(v1Update(SqlWorkbenchNotebook.TABLE_NAME, privateId, Map.of("title", "Private v3")).getValueString("title")).isEqualTo("Private v3");
      assertThat(v1Get(SqlWorkbenchNotebook.TABLE_NAME, privateId).getValueString("ownerUserId")).isEqualTo(OWNER);
   }



   /*******************************************************************************
    ** Versions and the audit are append-only for everyone - even an admin
    ** holding (ad hoc) write permissions on them - and pinned results can
    ** only be created by pinning, and only relabeled.
    *******************************************************************************/
   @Test
   void testAppendOnlyAndPinnedResults() throws Exception
   {
      for(String user : List.of(OWNER, ADMIN))
      {
         login(user);
         for(String table : List.of(SqlWorkbenchNotebookVersion.TABLE_NAME, SqlWorkbenchExecution.TABLE_NAME))
         {
            QContext.getQSession().withPermission(table + ".insert").withPermission(table + ".edit").withPermission(table + ".delete");
            Integer id = v1Query(table).get(0).getValueInteger("id");
            assertThatThrownBy(() -> v1Update(table, id, Map.of("comment", "rewritten", "sqlText", "rewritten"))).as(user + " update " + table).isInstanceOf(QException.class);
            assertThat(v1Delete(table, id)).as(user + " delete " + table).isZero();
            assertThatThrownBy(() -> tableInsert(table, Map.of("notebookId", ids.get("Private"), "versionNumber", 99, "executionUuid", "x"))).as(user + " insert " + table).isInstanceOf(QException.class);
         }
      }
      assertThat(SystemUser.call(() -> QueryAction.execute(SqlWorkbenchExecution.TABLE_NAME, new QQueryFilter()))).hasSize(2);

      login(OWNER);
      QContext.getQSession().withPermission(SqlWorkbenchResultSnapshot.TABLE_NAME + ".insert");
      assertThatThrownBy(() -> tableInsert(SqlWorkbenchResultSnapshot.TABLE_NAME, Map.of("notebookId", ids.get("Private"), "resultJson", "{\"forged\":true}")))
         .isInstanceOf(QException.class).hasMessageContaining("pinned from the notebook");
      Integer snapshotId = v1Query(SqlWorkbenchResultSnapshot.TABLE_NAME).get(0).getValueInteger("id");
      assertThat(v1Update(SqlWorkbenchResultSnapshot.TABLE_NAME, snapshotId, Map.of("label", "Payroll")).getValueString("label")).isEqualTo("Payroll");
      assertThatThrownBy(() -> v1Update(SqlWorkbenchResultSnapshot.TABLE_NAME, snapshotId, Map.of("rowCount", 999))).isInstanceOf(QException.class);
      assertThat(v1Delete(SqlWorkbenchResultSnapshot.TABLE_NAME, snapshotId)).isEqualTo(1);
   }



   /*******************************************************************************
    ** Table edits keep the same invariants as the notebook UI: ordering,
    ** keys, versions, library visibility, cascades.
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void testTableEditsKeepNotebookInvariants() throws Exception
   {
      login(OWNER);
      Integer id = ids.get("Private");
      assertThat(currentVersion(id)).isEqualTo(1);

      ////////////////////////////////////////////////////////////////////////
      // a cell added through the table goes last, gets a key, and appends  //
      // a version; the notebook endpoint shows it                          //
      ////////////////////////////////////////////////////////////////////////
      QRecord cell = tableInsert(SqlWorkbenchCell.TABLE_NAME, Map.of("notebookId", id, "cellType", "markdown", "content", "# Notes"));
      assertThat(cell.getValueInteger("sortOrder")).isEqualTo(1);
      assertThat(cell.getValueString("cellKey")).isNotBlank();
      assertThat(cell.getValueString("cellType")).isEqualTo("MARKDOWN");
      assertThat(currentVersion(id)).isEqualTo(2);
      List<Map<String, Object>> cells = (List<Map<String, Object>>) api.notebooks().get(id, as(OWNER)).get("cells");
      assertThat(cells).extracting(c -> c.get("content")).containsExactly("SELECT 1", "# Notes");

      v1Update(SqlWorkbenchCell.TABLE_NAME, cell.getValueInteger("id"), Map.of("content", "# More notes"));
      assertThat(currentVersion(id)).isEqualTo(3);
      assertThat(api.notebooks().version(id, 3, as(OWNER)).get("notebook").toString()).contains("# More notes");
      assertThatThrownBy(() -> v1Update(SqlWorkbenchCell.TABLE_NAME, cell.getValueInteger("id"), Map.of("notebookId", ids.get("Library")))).isInstanceOf(QException.class);
      assertThatThrownBy(() -> v1Update(SqlWorkbenchCell.TABLE_NAME, cell.getValueInteger("id"), Map.of("cellType", "PYTHON"))).isInstanceOf(QException.class);
      assertThatThrownBy(() -> tableInsert(SqlWorkbenchCell.TABLE_NAME, Map.of("notebookId", id, "content", "x".repeat(config.getMaxSqlLength() + 1)))).isInstanceOf(QException.class);

      assertThat(v1Delete(SqlWorkbenchCell.TABLE_NAME, cell.getValueInteger("id"))).isEqualTo(1);
      assertThat(currentVersion(id)).isEqualTo(4);

      ///////////////////////////////////////////////////////////////
      // notebook edits through the table: versioned; visibility   //
      // and system fields cannot be set directly                  //
      ///////////////////////////////////////////////////////////////
      v1Update(SqlWorkbenchNotebook.TABLE_NAME, id, Map.of("description", "via the table", "currentVersion", 99));
      assertThat(currentVersion(id)).isEqualTo(5);
      assertThatThrownBy(() -> v1Update(SqlWorkbenchNotebook.TABLE_NAME, id, Map.of("visibility", "SHARED"))).isInstanceOf(QException.class);
      assertThatThrownBy(() -> v1Update(SqlWorkbenchNotebook.TABLE_NAME, id, Map.of("title", " "))).isInstanceOf(QException.class);
      assertThatThrownBy(() -> v1Update(SqlWorkbenchNotebook.TABLE_NAME, id, Map.of("parametersJson", "{not a list"))).isInstanceOf(QException.class);

      QRecord created = tableInsert(SqlWorkbenchNotebook.TABLE_NAME, Map.of("title", "Made in the table"));
      assertThat(created.getValueInteger("currentVersion")).isEqualTo(0);
      assertThat(currentVersion(created.getValueInteger("id"))).isEqualTo(1);

      ////////////////////////////////////////////////////////////////////
      // publishing through the share table = publishing in the UI      //
      ////////////////////////////////////////////////////////////////////
      QRecord share = tableInsert(SqlWorkbenchNotebookShare.TABLE_NAME, Map.of("notebookId", id, "audienceType", "all", "audienceId", "ignored"));
      assertThat(share.getValueString("audienceKey")).isEqualTo("all");
      assertThat(share.getValueString("audienceId")).isNull();
      assertThat(api.notebooks().get(id, as(OWNER)).get("visibility")).isEqualTo("SHARED");
      assertThat(api.notebooks().list("shared", as(OTHER))).extracting(n -> n.get("id")).contains(id);
      login(OWNER);
      assertThatThrownBy(() -> tableInsert(SqlWorkbenchNotebookShare.TABLE_NAME, Map.of("notebookId", id, "audienceType", "ALL"))).isInstanceOf(QException.class);
      assertThatThrownBy(() -> tableInsert(SqlWorkbenchNotebookShare.TABLE_NAME, Map.of("notebookId", id, "audienceType", "USER"))).isInstanceOf(QException.class);
      assertThatThrownBy(() -> tableInsert(SqlWorkbenchNotebookShare.TABLE_NAME, Map.of("notebookId", id, "audienceType", "EVERYONE"))).isInstanceOf(QException.class);
      assertThat(v1Delete(SqlWorkbenchNotebookShare.TABLE_NAME, share.getValueInteger("id"))).isEqualTo(1);
      assertThat(api.notebooks().get(id, as(OWNER)).get("visibility")).isEqualTo("PERSONAL");

      ////////////////////////////////////////////////////////////////
      // a user share with different case still reaches the user    //
      ////////////////////////////////////////////////////////////////
      login(OWNER);
      tableInsert(SqlWorkbenchNotebookShare.TABLE_NAME, Map.of("notebookId", id, "audienceType", "USER", "audienceId", "  READER-2 "));
      assertThat(api.notebooks().get(id, as(OTHER)).get("canEdit")).isEqualTo(false);

      ////////////////////////////////////////////////////////////////
      // deleting a notebook through the table removes its children //
      ////////////////////////////////////////////////////////////////
      login(OWNER);
      assertThat(v1Delete(SqlWorkbenchNotebook.TABLE_NAME, id)).isEqualTo(1);
      for(String child : List.of(SqlWorkbenchCell.TABLE_NAME, SqlWorkbenchNotebookVersion.TABLE_NAME, SqlWorkbenchResultSnapshot.TABLE_NAME, SqlWorkbenchNotebookShare.TABLE_NAME))
      {
         assertThat(SystemUser.call(() -> QueryAction.execute(child, new QQueryFilter(new QFilterCriteria("notebookId", QCriteriaOperator.EQUALS, id))))).as(child).isEmpty();
      }
      assertThat(SystemUser.call(() -> QueryAction.execute(SqlWorkbenchExecution.TABLE_NAME, new QQueryFilter()))).as("the audit survives").hasSize(2);
   }



   /*******************************************************************************
    ** Editing a share re-targets it (new audience key, visibility in step);
    ** audiences cannot edit or remove the share that names them.
    *******************************************************************************/
   @Test
   void testShareEdits() throws Exception
   {
      Integer notebookId = ids.get("For reader-3");
      Integer shareId    = shareOf(notebookId);

      login(OWNER);
      QRecord retargeted = v1Update(SqlWorkbenchNotebookShare.TABLE_NAME, shareId, Map.of("audienceId", GROUP_MEMBER, "audienceKey", "all", "createdByUserId", "forged"));
      assertThat(retargeted.getValueString("audienceKey")).isEqualTo("user:reader-4");
      login(GROUP_MEMBER);
      assertThat(v1Get(SqlWorkbenchNotebook.TABLE_NAME, notebookId).getValueString("title")).isEqualTo("For reader-3");
      login(SHARED_USER);
      assertThatThrownBy(() -> v1Get(SqlWorkbenchNotebook.TABLE_NAME, notebookId)).isInstanceOf(QNotFoundException.class);

      login(OWNER);
      assertThatThrownBy(() -> v1Update(SqlWorkbenchNotebookShare.TABLE_NAME, shareId, Map.of("notebookId", ids.get("Private")))).isInstanceOf(QException.class);
      v1Update(SqlWorkbenchNotebookShare.TABLE_NAME, shareId, Map.of("audienceType", "ALL"));
      assertThat(api.notebooks().get(notebookId, as(OWNER)).get("visibility")).isEqualTo("SHARED");
      login(OWNER);
      v1Update(SqlWorkbenchNotebookShare.TABLE_NAME, shareId, Map.of("audienceType", "USER", "audienceId", SHARED_USER));
      assertThat(api.notebooks().get(notebookId, as(OWNER)).get("visibility")).isEqualTo("PERSONAL");
      assertThat(SystemUser.call(() -> new GetAction().executeForRecord(new GetInput(SqlWorkbenchNotebookShare.TABLE_NAME).withPrimaryKey(shareId))).getValueString("createdByUserId")).isEqualTo(OWNER);

      ////////////////////////////////////////////////////////////////////////
      // the audience can read its share row, but not change or remove it, //
      // even through the core actions (no table-permission check there)   //
      ////////////////////////////////////////////////////////////////////////
      login(SHARED_USER);
      assertThat(v1Get(SqlWorkbenchNotebookShare.TABLE_NAME, shareId).getValueString("audienceKey")).isEqualTo("user:reader-3");
      QRecord coreUpdate = new UpdateAction().execute(new UpdateInput(SqlWorkbenchNotebookShare.TABLE_NAME)
         .withRecord(new QRecord().withValue("id", shareId).withValue("audienceId", OTHER))).getRecords().get(0);
      assertThat(coreUpdate.getErrors()).isNotEmpty();
      DeleteOutput coreDelete = new DeleteAction().execute(new DeleteInput(SqlWorkbenchNotebookShare.TABLE_NAME).withPrimaryKeys(List.of(shareId)));
      assertThat(coreDelete.getDeletedRecordCount()).isZero();
      assertThat(SystemUser.call(() -> new GetAction().executeForRecord(new GetInput(SqlWorkbenchNotebookShare.TABLE_NAME).withPrimaryKey(shareId))).getValueString("audienceKey")).isEqualTo("user:reader-3");
   }



   /*******************************************************************************
    ** Record actions: Share, Duplicate, Restore Version - permission-gated,
    ** and their records come through the locks.
    *******************************************************************************/
   @Test
   void testRecordActions() throws Exception
   {
      Integer privateId = ids.get("Private");
      Integer library   = ids.get("Library");

      login(OWNER);
      RunProcessOutput shared = runRecordAction(SqlWorkbenchRecordActionsMetaDataProducer.SHARE_NOTEBOOK, privateId, Map.of("audienceType", "USER", "audienceId", SHARED_USER));
      assertThat(shared.getException()).isEmpty();
      assertThat(shared.getValue(SqlWorkbenchRecordActionSteps.RESULT_MESSAGE).toString()).contains("user:reader-3");
      login(SHARED_USER);
      assertThat(v1Get(SqlWorkbenchNotebook.TABLE_NAME, privateId).getValueString("title")).isEqualTo("Private");

      ///////////////////////////////////////////////////////////////////////
      // a reader without share permission cannot see or run Share; a     //
      // record they cannot read is simply not found by the action        //
      ///////////////////////////////////////////////////////////////////////
      login(OTHER);
      assertThat(PermissionsHelper.hasProcessPermission(new RunProcessInput(), SqlWorkbenchRecordActionsMetaDataProducer.SHARE_NOTEBOOK)).isFalse();
      assertThat(PermissionsHelper.hasProcessPermission(new RunProcessInput(), SqlWorkbenchRecordActionsMetaDataProducer.DUPLICATE_NOTEBOOK)).isTrue();
      RunProcessOutput copy = runRecordAction(SqlWorkbenchRecordActionsMetaDataProducer.DUPLICATE_NOTEBOOK, library, Map.of());
      assertThat(copy.getException()).isEmpty();
      assertThat(api.notebooks().list("mine", as(OTHER))).extracting(n -> n.get("title")).contains("Copy of Library");
      login(OTHER);
      assertRecordActionFails(SqlWorkbenchRecordActionsMetaDataProducer.DUPLICATE_NOTEBOOK, privateId, "Too few records");

      ///////////////////////////////////////////////////////
      // restore: the owner, as a new version; not others  //
      ///////////////////////////////////////////////////////
      login(OWNER);
      api.notebooks().update(privateId, Map.of("title", "Private v2"), as(OWNER));
      login(OWNER);
      Integer v1 = SystemUser.call(() -> QueryAction.execute(SqlWorkbenchNotebookVersion.TABLE_NAME, new QQueryFilter(new QFilterCriteria("notebookId", QCriteriaOperator.EQUALS, privateId))
         .withCriteria(new QFilterCriteria("versionNumber", QCriteriaOperator.EQUALS, 1)))).get(0).getValueInteger("id");
      RunProcessOutput restored = runRecordAction(SqlWorkbenchRecordActionsMetaDataProducer.RESTORE_VERSION, v1, Map.of());
      assertThat(restored.getException()).isEmpty();
      assertThat(v1Get(SqlWorkbenchNotebook.TABLE_NAME, privateId).getValueString("title")).isEqualTo("Private");
      assertThat(currentVersion(privateId)).isEqualTo(3);
      login(SHARED_USER);
      assertRecordActionFails(SqlWorkbenchRecordActionsMetaDataProducer.RESTORE_VERSION, v1, "Only the notebook's owner");
   }



   /*******************************************************************************
    ** The notebook endpoints see exactly what the tables show.
    *******************************************************************************/
   @Test
   void testNotebookEndpointsMatchTheTables() throws Exception
   {
      for(String user : List.of(OWNER, OTHER, SHARED_USER, GROUP_MEMBER, ADMIN))
      {
         login(user);
         Set<String> fromTables = new TreeSet<>(v1Query(SqlWorkbenchNotebook.TABLE_NAME).stream().map(r -> r.getValueString("title")).toList());
         login(user);
         List<Map<String, Object>> mine   = api.notebooks().list("mine", api.caller(null));
         List<Map<String, Object>> shared = api.notebooks().list("shared", api.caller(null));
         Set<String>               seen   = new TreeSet<>();
         mine.forEach(n -> seen.add((String) n.get("title")));
         shared.forEach(n -> seen.add((String) n.get("title")));
         if(ADMIN.equals(user))
         {
            //////////////////////////////////////////////////////////////////
            // admins read every notebook through the tables; the notebook //
            // UI still lists only their own and what is shared with them  //
            //////////////////////////////////////////////////////////////////
            assertThat(seen).isEqualTo(new TreeSet<>(List.of("Library")));
            assertThat(fromTables).isEqualTo(new TreeSet<>(ALL_TITLES));
         }
         else
         {
            assertThat(seen).as(user).isEqualTo(fromTables);
         }
         for(String title : ALL_TITLES)
         {
            Integer id = ids.get(title);
            login(user);
            if(fromTables.contains(title))
            {
               assertThat(api.notebooks().get(id, api.caller(null)).get("title")).as(user + " " + title).isEqualTo(title);
            }
            else
            {
               assertThatThrownBy(() -> api.notebooks().get(id, api.caller(null))).as(user + " " + title).isInstanceOf(QNotFoundException.class);
            }
         }
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private RunProcessOutput runRecordAction(String processName, Integer recordId, Map<String, Serializable> values) throws QException
   {
      RunProcessInput input = new RunProcessInput()
         .withProcessName(processName)
         .withFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.SKIP)
         .withValues(new LinkedHashMap<>(values))
         .withValue("recordIds", String.valueOf(recordId));
      return (new RunProcessAction().execute(input));
   }



   /*******************************************************************************
    ** A record action that must not succeed (QQQ either throws, e.g. when the
    ** locks hide the record, or returns the step's exception).
    *******************************************************************************/
   private void assertRecordActionFails(String processName, Integer recordId, String message)
   {
      Exception failure;
      try
      {
         failure = runRecordAction(processName, recordId, Map.of()).getException().orElse(null);
      }
      catch(QException e)
      {
         failure = e;
      }
      assertThat(failure).as(processName + " on " + recordId).isNotNull();
      assertThat(failure).hasMessageContaining(message);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private Integer currentVersion(Integer notebookId) throws QException
   {
      return (SystemUser.call(() -> QueryAction.execute(SqlWorkbenchNotebook.TABLE_NAME, new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.EQUALS, notebookId))).get(0).getValueInteger("currentVersion")));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private Integer cellOf(Integer notebookId) throws QException
   {
      return (SystemUser.call(() -> QueryAction.execute(SqlWorkbenchCell.TABLE_NAME, new QQueryFilter(new QFilterCriteria("notebookId", QCriteriaOperator.EQUALS, notebookId))).get(0).getValueInteger("id")));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private Integer shareOf(Integer notebookId) throws QException
   {
      return (SystemUser.call(() -> QueryAction.execute(SqlWorkbenchNotebookShare.TABLE_NAME, new QQueryFilter(new QFilterCriteria("notebookId", QCriteriaOperator.EQUALS, notebookId))).get(0).getValueInteger("id")));
   }



   /*******************************************************************************
    ** Insert a share through the core action (as the session).
    *******************************************************************************/
   private void insertShare(Integer notebookId, String type, String audienceId) throws QException
   {
      QRecord record = new InsertAction().execute(new InsertInput(SqlWorkbenchNotebookShare.TABLE_NAME).withRecord(new QRecord()
         .withValue("notebookId", notebookId).withValue("audienceType", type).withValue("audienceId", audienceId))).getRecords().get(0);
      assertThat(record.getErrors()).isNullOrEmpty();
   }



   /*******************************************************************************
    ** v1 table API: query.
    *******************************************************************************/
   private static List<QRecord> v1Query(String tableName) throws Exception
   {
      List<QRecord>   records = new ArrayList<>();
      TableQueryInput input   = new TableQueryInput();
      input.setTableName(tableName);
      new TableQueryExecutor().execute(input, (TableQueryOutputInterface) records::addAll);
      return (records);
   }



   /*******************************************************************************
    ** v1 table API: count.
    *******************************************************************************/
   private static int v1Count(String tableName) throws Exception
   {
      long[]          count = new long[1];
      TableCountInput input = new TableCountInput();
      input.setTableName(tableName);
      new TableCountExecutor().execute(input, new TableCountOutputInterface()
      {
         @Override
         public void setCount(Long value)
         {
            count[0] = value;
         }



         @Override
         public void setDistinctCount(Long value)
         {
         }
      });
      return ((int) count[0]);
   }



   /*******************************************************************************
    ** v1 table API: get.
    *******************************************************************************/
   private static QRecord v1Get(String tableName, Integer id) throws Exception
   {
      QRecord[]     record = new QRecord[1];
      TableGetInput input  = new TableGetInput();
      input.setTableName(tableName);
      input.setPrimaryKey(String.valueOf(id));
      new TableGetExecutor().execute(input, (TableGetOutputInterface) r -> record[0] = r);
      return (record[0]);
   }



   /*******************************************************************************
    ** v1 table API: export (CSV), returning the data row count.
    *******************************************************************************/
   private static int v1ExportRows(String tableName) throws Exception
   {
      InputStream[]    stream = new InputStream[1];
      TableExportInput input  = new TableExportInput();
      input.setTableName(tableName);
      input.setFormat(ReportFormat.CSV.name());
      input.setFieldNames(List.of("id"));
      new TableExportExecutor().execute(input, new TableExportOutputInterface()
      {
         @Override
         public void setReportFormat(ReportFormat format)
         {
         }



         @Override
         public void setFilename(String filename)
         {
         }



         @Override
         public void setInputStream(InputStream inputStream)
         {
            stream[0] = inputStream;
         }
      });
      String csv = new String(stream[0].readAllBytes(), StandardCharsets.UTF_8).trim();
      return (csv.isEmpty() ? 0 : csv.split("\n").length - 1);
   }



   /*******************************************************************************
    ** v1 table API: update (throws on record errors).
    *******************************************************************************/
   private static QRecord v1Update(String tableName, Integer id, Map<String, Serializable> values) throws Exception
   {
      QRecord[]        record = new QRecord[1];
      TableUpdateInput input  = new TableUpdateInput();
      input.setTableName(tableName);
      input.setPrimaryKey(String.valueOf(id));
      input.setRecordValues(new LinkedHashMap<>(values));
      new TableUpdateExecutor().execute(input, new TableUpdateOutputInterface()
      {
         @Override
         public void setRecord(QRecord r)
         {
            record[0] = r;
         }



         @Override
         public void setErrors(List<String> errors)
         {
         }



         @Override
         public void setWarnings(List<String> warnings)
         {
         }
      });
      return (record[0]);
   }



   /*******************************************************************************
    ** v1 table API: delete, returning the deleted count.
    *******************************************************************************/
   private static int v1Delete(String tableName, Integer id) throws Exception
   {
      int[]            deleted = new int[1];
      TableDeleteInput input   = new TableDeleteInput();
      input.setTableName(tableName);
      input.setPrimaryKey(String.valueOf(id));
      new TableDeleteExecutor().execute(input, new TableDeleteOutputInterface()
      {
         @Override
         public void setDeletedRecordCount(Integer count)
         {
            deleted[0] = count == null ? 0 : count;
         }



         @Override
         public void setErrors(List<String> errors)
         {
         }
      });
      return (deleted[0]);
   }



   /*******************************************************************************
    ** v1 table API: insert (throws on record errors).
    *******************************************************************************/
   private static QRecord tableInsert(String tableName, Map<String, Serializable> values) throws Exception
   {
      QRecord[]        record = new QRecord[1];
      TableInsertInput input  = new TableInsertInput();
      input.setTableName(tableName);
      input.setRecordValues(new LinkedHashMap<>(values));
      new TableInsertExecutor().execute(input, new TableInsertOutputInterface()
      {
         @Override
         public void setRecord(QRecord r)
         {
            record[0] = r;
         }



         @Override
         public void setErrors(List<String> errors)
         {
         }



         @Override
         public void setWarnings(List<String> warnings)
         {
         }
      });
      return (record[0]);
   }



   /*******************************************************************************
    ** The workbench table names.
    *******************************************************************************/
   private static final class AppTables
   {
      static final List<String> ALL = List.of(SqlWorkbenchNotebook.TABLE_NAME, SqlWorkbenchCell.TABLE_NAME, SqlWorkbenchNotebookVersion.TABLE_NAME,
         SqlWorkbenchResultSnapshot.TABLE_NAME, SqlWorkbenchNotebookShare.TABLE_NAME, SqlWorkbenchExecution.TABLE_NAME);
   }
}
