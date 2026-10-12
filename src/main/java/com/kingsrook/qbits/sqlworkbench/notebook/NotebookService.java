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


import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.core.type.TypeReference;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchRuntime;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchSecurity;
import com.kingsrook.qbits.sqlworkbench.customizers.SqlWorkbenchActionFlag;
import com.kingsrook.qbits.sqlworkbench.engine.Caller;
import com.kingsrook.qbits.sqlworkbench.engine.CollectedResult;
import com.kingsrook.qbits.sqlworkbench.engine.ColumnInfo;
import com.kingsrook.qbits.sqlworkbench.engine.ResultCache;
import com.kingsrook.qbits.sqlworkbench.model.ShareAudienceType;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchCell;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebook;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookShare;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookVersion;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchResultSnapshot;
import com.kingsrook.qqq.backend.core.actions.QBackendTransaction;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QNotFoundException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateOutput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Notebooks, their cells, versions, pinned result snapshots and shares, for
 ** the notebook UI.
 **
 ** Everything here runs as the caller's own session through the standard
 ** QQQ table actions, so the same record locks and table customizers apply
 ** as for the table UI and the /qqq/v1 table API: a caller reads their own
 ** notebooks and the ones shared with them (a user / group share, or the
 ** shared library), and only the owner changes one. The explicit checks
 ** (readable / owned / canShare) stay as defense in depth and to give the
 ** notebook UI its 404 / 403 answers. Only version bookkeeping runs as the
 ** system user (NotebookVersioning), because versions are append-only.
 **
 ** Saves replace a notebook's cells and append one version (full JSON), in
 ** one transaction; versions beyond notebookVersionRetention are pruned.
 *******************************************************************************/
public class NotebookService
{
   public static final String VISIBILITY_PERSONAL = "PERSONAL";
   public static final String VISIBILITY_SHARED   = "SHARED";
   public static final String CELL_SQL           = "SQL";
   public static final String CELL_MARKDOWN      = "MARKDOWN";
   public static final int    MAX_CELLS          = 200;

   private final SqlWorkbenchRuntime    runtime;
   private final SqlWorkbenchQBitConfig config;



   /*******************************************************************************
    **
    *******************************************************************************/
   public NotebookService(SqlWorkbenchRuntime runtime)
   {
      this.runtime = runtime;
      this.config = runtime.getConfig();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private String table(String name)
   {
      return (config.applyPrefix(name));
   }



   /*******************************************************************************
    ** Make sure the session carries the workbench keys (hosts normally apply
    ** them at login, with groups).
    *******************************************************************************/
   private void prepare()
   {
      SqlWorkbenchSecurity.ensureSessionKeys(config, QContext.getQSession());
   }



   /*******************************************************************************
    ** Notebook summaries: scope "mine" (default) or "shared" (shared with the
    ** caller - directly, through a group, or in the library - and not theirs).
    *******************************************************************************/
   public List<Map<String, Object>> list(String scope, Caller caller) throws QException
   {
      prepare();
      QQueryFilter filter;
      if("shared".equalsIgnoreCase(scope))
      {
         Set<Serializable> ids = sharedWithCallerNotebookIds();
         if(ids.isEmpty())
         {
            return (new ArrayList<>());
         }
         filter = new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.IN, new ArrayList<>(ids)))
            .withCriteria(new QFilterCriteria("ownerUserId", QCriteriaOperator.NOT_EQUALS, caller.userId()));
      }
      else
      {
         filter = new QQueryFilter(new QFilterCriteria("ownerUserId", QCriteriaOperator.EQUALS, caller.userId()));
      }
      filter.withOrderBy(new QFilterOrderBy("modifyDate", false)).withLimit(500);

      List<Map<String, Object>> result = new ArrayList<>();
      for(QRecord record : QueryAction.execute(table(SqlWorkbenchNotebook.TABLE_NAME), filter))
      {
         result.add(summary(new SqlWorkbenchNotebook(record), caller));
      }
      return (result);
   }



   /*******************************************************************************
    ** One notebook with its cells.
    *******************************************************************************/
   public Map<String, Object> get(Integer id, Caller caller) throws QException
   {
      prepare();
      SqlWorkbenchNotebook notebook = readable(id, caller);
      Map<String, Object>  view     = summary(notebook, caller);
      view.put("parameters", parseParameters(notebook.getParametersJson()));
      view.put("cells", NotebookVersioning.cells(config, notebook.getId(), null).stream().map(NotebookService::cellView).toList());
      return (view);
   }



   /*******************************************************************************
    ** Create a personal notebook (version 1). The table customizer makes the
    ** caller its owner.
    *******************************************************************************/
   public Map<String, Object> create(Map<String, Object> body, Caller caller) throws QException
   {
      prepare();
      SqlWorkbenchNotebook notebook = new SqlWorkbenchNotebook();
      applyFields(notebook, body, true);
      List<SqlWorkbenchCell> cells = parseCells(body.get("cells"));
      try(QBackendTransaction transaction = QBackendTransaction.openFor(new InsertInput(table(SqlWorkbenchNotebook.TABLE_NAME))))
      {
         InsertOutput output = new InsertAction().execute(new InsertInput(table(SqlWorkbenchNotebook.TABLE_NAME))
            .withFlag(SqlWorkbenchActionFlag.NOTEBOOK_SAVE)
            .withRecordEntity(notebook)
            .withTransaction(transaction));
         throwIfErrors(output.getRecords().get(0));
         notebook.setId(output.getRecords().get(0).getValueInteger("id"));
         saveContent(notebook, cells, (String) body.get("comment"), transaction);
         commit(transaction);
      }
      return (get(notebook.getId(), caller));
   }



   /*******************************************************************************
    ** Save a notebook: fields, cells (replaced), and a new version.
    *******************************************************************************/
   public Map<String, Object> update(Integer id, Map<String, Object> body, Caller caller) throws QException
   {
      prepare();
      SqlWorkbenchNotebook notebook = owned(id, caller);
      applyFields(notebook, body, false);
      List<SqlWorkbenchCell> cells = body.containsKey("cells") ? parseCells(body.get("cells")) : NotebookVersioning.cells(config, id, null);
      try(QBackendTransaction transaction = QBackendTransaction.openFor(new InsertInput(table(SqlWorkbenchNotebook.TABLE_NAME))))
      {
         saveContent(notebook, cells, (String) body.get("comment"), transaction);
         commit(transaction);
      }
      return (get(id, caller));
   }



   /*******************************************************************************
    ** Delete a notebook; its table customizer deletes everything under it.
    *******************************************************************************/
   public void delete(Integer id, Caller caller) throws QException
   {
      prepare();
      owned(id, caller);
      try(QBackendTransaction transaction = QBackendTransaction.openFor(new InsertInput(table(SqlWorkbenchNotebook.TABLE_NAME))))
      {
         DeleteOutput output = new DeleteAction().execute(new DeleteInput(table(SqlWorkbenchNotebook.TABLE_NAME)).withPrimaryKeys(List.of(id)).withTransaction(transaction));
         throwIfErrors(output.getRecordsWithErrors());
         commit(transaction);
      }
   }



   /*******************************************************************************
    ** Publish to (or withdraw from) the shared library: an ALL share row.
    *******************************************************************************/
   public Map<String, Object> publish(Integer id, boolean shared, Caller caller) throws QException
   {
      prepare();
      if(!caller.canShare())
      {
         throw (new QPermissionDeniedException("Publishing to the shared library needs the " + config.getSharePermissionName() + " permission."));
      }
      owned(id, caller);

      QQueryFilter libraryShare = byNotebook(id).withCriteria(new QFilterCriteria("audienceKey", QCriteriaOperator.EQUALS, SqlWorkbenchSecurity.AUDIENCE_ALL));
      boolean      published    = !QueryAction.execute(table(SqlWorkbenchNotebookShare.TABLE_NAME), libraryShare).isEmpty();
      if(shared && !published)
      {
         InsertOutput output = new InsertAction().execute(new InsertInput(table(SqlWorkbenchNotebookShare.TABLE_NAME)).withRecordEntity(new SqlWorkbenchNotebookShare()
            .withNotebookId(id)
            .withAudienceType(ShareAudienceType.ALL.name())));
         throwIfErrors(output.getRecords().get(0));
      }
      else if(!shared && published)
      {
         DeleteOutput output = new DeleteAction().execute(new DeleteInput(table(SqlWorkbenchNotebookShare.TABLE_NAME)).withQueryFilter(libraryShare));
         throwIfErrors(output.getRecordsWithErrors());
      }
      return (get(id, caller));
   }



   /*******************************************************************************
    ** A personal copy of a readable notebook.
    *******************************************************************************/
   public Map<String, Object> duplicate(Integer id, Caller caller) throws QException
   {
      prepare();
      SqlWorkbenchNotebook source = readable(id, caller);
      Map<String, Object>  body   = new LinkedHashMap<>();
      body.put("title", truncate("Copy of " + source.getTitle(), 255));
      body.put("description", source.getDescription());
      body.put("defaultBackendName", source.getDefaultBackendName());
      body.put("parameters", parseParameters(source.getParametersJson()));
      List<Map<String, Object>> cells = new ArrayList<>();
      for(SqlWorkbenchCell cell : NotebookVersioning.cells(config, id, null))
      {
         Map<String, Object> view = cellView(cell);
         view.put("cellKey", UUID.randomUUID().toString());
         cells.add(view);
      }
      body.put("cells", cells);
      body.put("comment", "Duplicated from notebook " + id);
      return (create(body, caller));
   }



   /*******************************************************************************
    ** Version list, newest first.
    *******************************************************************************/
   public List<Map<String, Object>> versions(Integer id, Caller caller) throws QException
   {
      prepare();
      readable(id, caller);
      QQueryFilter              filter = byNotebook(id).withOrderBy(new QFilterOrderBy("versionNumber", false));
      List<Map<String, Object>> result = new ArrayList<>();
      for(QRecord record : QueryAction.execute(table(SqlWorkbenchNotebookVersion.TABLE_NAME), filter))
      {
         SqlWorkbenchNotebookVersion version = new SqlWorkbenchNotebookVersion(record);
         Map<String, Object>         view    = new LinkedHashMap<>();
         view.put("versionNumber", version.getVersionNumber());
         view.put("savedByName", version.getSavedByName());
         view.put("comment", version.getComment());
         view.put("createDate", String.valueOf(version.getCreateDate()));
         result.add(view);
      }
      return (result);
   }



   /*******************************************************************************
    ** One version's content.
    *******************************************************************************/
   public Map<String, Object> version(Integer id, Integer versionNumber, Caller caller) throws QException
   {
      prepare();
      readable(id, caller);
      SqlWorkbenchNotebookVersion version = findVersion(id, versionNumber);
      Map<String, Object>         view    = new LinkedHashMap<>();
      view.put("versionNumber", version.getVersionNumber());
      view.put("savedByName", version.getSavedByName());
      view.put("comment", version.getComment());
      view.put("createDate", String.valueOf(version.getCreateDate()));
      view.put("notebook", parseJsonObject(version.getContentJson()));
      return (view);
   }



   /*******************************************************************************
    ** Restore a version (saved as a new version).
    *******************************************************************************/
   public Map<String, Object> restore(Integer id, Integer versionNumber, Caller caller) throws QException
   {
      prepare();
      owned(id, caller);
      Map<String, Object> content = parseJsonObject(findVersion(id, versionNumber).getContentJson());
      Map<String, Object> body    = new LinkedHashMap<>(content);
      body.put("comment", "Restored version " + versionNumber);
      return (update(id, body, caller));
   }



   /*******************************************************************************
    ** Pinned snapshots of a notebook.
    *******************************************************************************/
   public List<Map<String, Object>> snapshots(Integer id, Caller caller) throws QException
   {
      prepare();
      readable(id, caller);
      List<Map<String, Object>> result = new ArrayList<>();
      for(QRecord record : QueryAction.execute(table(SqlWorkbenchResultSnapshot.TABLE_NAME), byNotebook(id).withOrderBy(new QFilterOrderBy("id", false))))
      {
         result.add(snapshotView(new SqlWorkbenchResultSnapshot(record)));
      }
      return (result);
   }



   /*******************************************************************************
    ** Pin a cached result of the caller to a notebook cell.
    *******************************************************************************/
   public Map<String, Object> createSnapshot(Integer id, Map<String, Object> body, Caller caller, String sqlText) throws QException
   {
      prepare();
      owned(id, caller);
      String executionUuid = (String) body.get("executionUuid");
      ResultCache.Entry entry = runtime.getResultCache().get(executionUuid, caller.userId())
         .orElseThrow(() -> new QNotFoundException("That result is no longer available; run the cell again, then pin it."));
      CollectedResult     result = entry.result();
      int                 max    = config.getSnapshotMaxRows();
      List<List<Object>>  rows   = result.rows().size() > max ? result.rows().subList(0, max) : result.rows();
      Map<String, Object> json   = new LinkedHashMap<>();
      json.put("columns", result.columns().stream().map(ColumnInfo::toMap).toList());
      json.put("rows", rows);
      SqlWorkbenchResultSnapshot snapshot = new SqlWorkbenchResultSnapshot()
         .withNotebookId(id)
         .withCellKey((String) body.get("cellKey"))
         .withExecutionUuid(executionUuid)
         .withLabel(truncate((String) body.get("label"), 255))
         .withBackendName(entry.backendName())
         .withSqlText(sqlText)
         .withResultJson(JsonUtils.toJson(json))
         .withRowCount(rows.size())
         .withTruncated(result.truncated() || result.rows().size() > max)
         .withCreatedByUserId(caller.userId())
         .withCreateDate(runtime.getClock().instant());
      InsertOutput output = new InsertAction().execute(new InsertInput(table(SqlWorkbenchResultSnapshot.TABLE_NAME))
         .withFlag(SqlWorkbenchActionFlag.SNAPSHOT_PIN)
         .withRecordEntity(snapshot));
      throwIfErrors(output.getRecords().get(0));
      snapshot.setId(output.getRecords().get(0).getValueInteger("id"));
      return (snapshotView(snapshot));
   }



   /*******************************************************************************
    ** Unpin.
    *******************************************************************************/
   public void deleteSnapshot(Integer id, Integer snapshotId, Caller caller) throws QException
   {
      prepare();
      owned(id, caller);
      QQueryFilter filter = byNotebook(id).withCriteria(new QFilterCriteria("id", QCriteriaOperator.EQUALS, snapshotId));
      if(QueryAction.execute(table(SqlWorkbenchResultSnapshot.TABLE_NAME), filter).isEmpty())
      {
         throw (new QNotFoundException("Snapshot not found."));
      }
      DeleteOutput output = new DeleteAction().execute(new DeleteInput(table(SqlWorkbenchResultSnapshot.TABLE_NAME)).withPrimaryKeys(List.of(snapshotId)));
      throwIfErrors(output.getRecordsWithErrors());
   }



   /*******************************************************************************
    ** A notebook the caller may read, or 404 (never 403: notebooks not shared
    ** with the caller do not exist for them). The record locks already hide
    ** them; the owner / share check repeats that decision explicitly.
    *******************************************************************************/
   SqlWorkbenchNotebook readable(Integer id, Caller caller) throws QException
   {
      SqlWorkbenchNotebook notebook = find(id);
      if(notebook == null)
      {
         throw (new QNotFoundException("Notebook not found."));
      }
      boolean owner = Objects.equals(notebook.getOwnerUserId(), caller.userId());
      if(!owner && !SqlWorkbenchSecurity.hasAllAccess(config) && !sharedWithCallerNotebookIds().contains(notebook.getId()))
      {
         throw (new QNotFoundException("Notebook not found."));
      }
      return (notebook);
   }



   /*******************************************************************************
    ** A notebook the caller owns: 404 if unreadable, 403 if readable but not
    ** theirs.
    *******************************************************************************/
   SqlWorkbenchNotebook owned(Integer id, Caller caller) throws QException
   {
      SqlWorkbenchNotebook notebook = readable(id, caller);
      if(!Objects.equals(notebook.getOwnerUserId(), caller.userId()))
      {
         throw (new QPermissionDeniedException("Only the notebook's owner can change it; duplicate it to make your own copy."));
      }
      return (notebook);
   }



   /*******************************************************************************
    ** The notebook, as the caller's session can see it (or null).
    *******************************************************************************/
   private SqlWorkbenchNotebook find(Integer id) throws QException
   {
      if(id == null)
      {
         throw (new QBadRequestException("A notebook id is required."));
      }
      List<QRecord> records = QueryAction.execute(table(SqlWorkbenchNotebook.TABLE_NAME), new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.EQUALS, id)));
      return (records.isEmpty() ? null : new SqlWorkbenchNotebook(records.get(0)));
   }



   /*******************************************************************************
    ** Ids of the notebooks shared with one of the session's audiences.
    *******************************************************************************/
   private Set<Serializable> sharedWithCallerNotebookIds() throws QException
   {
      Set<Serializable> ids          = new LinkedHashSet<>();
      List<String>      audienceKeys = SqlWorkbenchSecurity.sessionAudienceKeys(config);
      if(audienceKeys.isEmpty())
      {
         return (ids);
      }
      QQueryFilter filter = new QQueryFilter(new QFilterCriteria("audienceKey", QCriteriaOperator.IN, new ArrayList<>(audienceKeys)));
      for(QRecord record : QueryAction.execute(table(SqlWorkbenchNotebookShare.TABLE_NAME), filter))
      {
         ids.add(record.getValueInteger("notebookId"));
      }
      return (ids);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private SqlWorkbenchNotebookVersion findVersion(Integer id, Integer versionNumber) throws QException
   {
      QQueryFilter  filter  = byNotebook(id).withCriteria(new QFilterCriteria("versionNumber", QCriteriaOperator.EQUALS, versionNumber));
      List<QRecord> records = QueryAction.execute(table(SqlWorkbenchNotebookVersion.TABLE_NAME), filter);
      if(records.isEmpty())
      {
         throw (new QNotFoundException("Version not found."));
      }
      return (new SqlWorkbenchNotebookVersion(records.get(0)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static QQueryFilter byNotebook(Integer id)
   {
      return (new QQueryFilter(new QFilterCriteria("notebookId", QCriteriaOperator.EQUALS, id)));
   }



   /*******************************************************************************
    ** Write the notebook's fields, replace its cells, append a version - all
    ** in the given transaction, as the caller (NOTEBOOK_SAVE tells the table
    ** customizers this is one save, versioned once at the end).
    *******************************************************************************/
   private void saveContent(SqlWorkbenchNotebook notebook, List<SqlWorkbenchCell> cells, String comment, QBackendTransaction transaction) throws QException
   {
      UpdateOutput updated = new UpdateAction().execute(new UpdateInput(table(SqlWorkbenchNotebook.TABLE_NAME))
         .withFlag(SqlWorkbenchActionFlag.NOTEBOOK_SAVE)
         .withTransaction(transaction)
         .withRecord(new QRecord()
            .withValue("id", notebook.getId())
            .withValue("title", notebook.getTitle())
            .withValue("description", notebook.getDescription())
            .withValue("defaultBackendName", notebook.getDefaultBackendName())
            .withValue("parametersJson", notebook.getParametersJson())));
      throwIfErrors(updated.getRecords().get(0));

      DeleteOutput deleted = new DeleteAction().execute(new DeleteInput(table(SqlWorkbenchCell.TABLE_NAME))
         .withFlag(SqlWorkbenchActionFlag.NOTEBOOK_SAVE)
         .withQueryFilter(byNotebook(notebook.getId()))
         .withTransaction(transaction));
      throwIfErrors(deleted.getRecordsWithErrors());

      for(int i = 0; i < cells.size(); i++)
      {
         cells.get(i).withId(null).withNotebookId(notebook.getId()).withSortOrder(i);
      }
      if(!cells.isEmpty())
      {
         InsertOutput output = new InsertAction().execute(new InsertInput(table(SqlWorkbenchCell.TABLE_NAME))
            .withFlag(SqlWorkbenchActionFlag.NOTEBOOK_SAVE)
            .withRecordEntities(cells)
            .withTransaction(transaction));
         for(QRecord record : output.getRecords())
         {
            throwIfErrors(record);
         }
      }

      NotebookVersioning.appendVersion(config, runtime.getClock(), notebook.getId(), comment, transaction);
   }



   /*******************************************************************************
    ** Apply title / description / defaultBackendName / parameters from a body.
    *******************************************************************************/
   private void applyFields(SqlWorkbenchNotebook notebook, Map<String, Object> body, boolean creating) throws QException
   {
      if(creating || body.containsKey("title"))
      {
         String title = body.get("title") == null ? null : body.get("title").toString().trim();
         if(!StringUtils.hasContent(title))
         {
            throw (new QBadRequestException("A notebook needs a title."));
         }
         notebook.setTitle(truncate(title, 255));
      }
      if(body.containsKey("description"))
      {
         notebook.setDescription(body.get("description") == null ? null : body.get("description").toString());
      }
      if(body.containsKey("defaultBackendName"))
      {
         notebook.setDefaultBackendName(body.get("defaultBackendName") == null ? null : body.get("defaultBackendName").toString());
      }
      if(body.containsKey("parameters"))
      {
         notebook.setParametersJson(body.get("parameters") == null ? null : JsonUtils.toJson(body.get("parameters")));
      }
   }



   /*******************************************************************************
    ** Cells from a body: [{cellKey, cellType, title, content, backendName}].
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   List<SqlWorkbenchCell> parseCells(Object raw) throws QException
   {
      List<SqlWorkbenchCell> cells = new ArrayList<>();
      if(raw == null)
      {
         return (cells);
      }
      if(!(raw instanceof List<?> list))
      {
         throw (new QBadRequestException("cells must be a list."));
      }
      if(list.size() > MAX_CELLS)
      {
         throw (new QBadRequestException("A notebook can have at most " + MAX_CELLS + " cells."));
      }

      Set<String> keys = new HashSet<>();
      for(Object item : list)
      {
         if(!(item instanceof Map<?, ?> map))
         {
            throw (new QBadRequestException("Each cell must be an object."));
         }
         Map<String, Object> cell = (Map<String, Object>) map;
         String type = cell.get("cellType") == null ? CELL_SQL : cell.get("cellType").toString().toUpperCase();
         if(!CELL_SQL.equals(type) && !CELL_MARKDOWN.equals(type))
         {
            throw (new QBadRequestException("cellType must be SQL or MARKDOWN."));
         }
         String content = cell.get("content") == null ? "" : cell.get("content").toString();
         if(content.length() > config.getMaxSqlLength())
         {
            throw (new QBadRequestException("A cell is longer than the limit of " + config.getMaxSqlLength() + " characters."));
         }
         String key = cell.get("cellKey") == null ? null : cell.get("cellKey").toString();
         if(!StringUtils.hasContent(key) || key.length() > 64 || !keys.add(key))
         {
            key = UUID.randomUUID().toString();
            keys.add(key);
         }
         cells.add(new SqlWorkbenchCell()
            .withCellKey(key)
            .withCellType(type)
            .withTitle(truncate(cell.get("title") == null ? null : cell.get("title").toString(), 255))
            .withContent(content)
            .withBackendName(cell.get("backendName") == null ? null : cell.get("backendName").toString()));
      }
      return (cells);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private Map<String, Object> summary(SqlWorkbenchNotebook notebook, Caller caller)
   {
      Map<String, Object> view = new LinkedHashMap<>();
      view.put("id", notebook.getId());
      view.put("title", notebook.getTitle());
      view.put("description", notebook.getDescription());
      view.put("visibility", notebook.getVisibility());
      view.put("ownerUserId", notebook.getOwnerUserId());
      view.put("ownerName", notebook.getOwnerName());
      view.put("defaultBackendName", notebook.getDefaultBackendName());
      view.put("currentVersion", notebook.getCurrentVersion());
      view.put("canEdit", Objects.equals(notebook.getOwnerUserId(), caller.userId()));
      view.put("createDate", notebook.getCreateDate() == null ? null : notebook.getCreateDate().toString());
      view.put("modifyDate", notebook.getModifyDate() == null ? null : notebook.getModifyDate().toString());
      return (view);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static Map<String, Object> cellView(SqlWorkbenchCell cell)
   {
      Map<String, Object> view = new LinkedHashMap<>();
      view.put("cellKey", cell.getCellKey());
      view.put("cellType", cell.getCellType());
      view.put("title", cell.getTitle());
      view.put("content", cell.getContent());
      view.put("backendName", cell.getBackendName());
      view.put("sortOrder", cell.getSortOrder());
      return (view);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Map<String, Object> snapshotView(SqlWorkbenchResultSnapshot snapshot)
   {
      Map<String, Object> view = new LinkedHashMap<>();
      view.put("id", snapshot.getId());
      view.put("cellKey", snapshot.getCellKey());
      view.put("executionUuid", snapshot.getExecutionUuid());
      view.put("label", snapshot.getLabel());
      view.put("backendName", snapshot.getBackendName());
      view.put("rowCount", snapshot.getRowCount());
      view.put("truncated", snapshot.getTruncated());
      view.put("createDate", snapshot.getCreateDate() == null ? null : snapshot.getCreateDate().toString());
      view.put("result", parseJsonObject(snapshot.getResultJson()));
      return (view);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static List<Object> parseParameters(String json)
   {
      if(!StringUtils.hasContent(json))
      {
         return (List.of());
      }
      try
      {
         return (JsonUtils.toObject(json, new TypeReference<List<Object>>() {}));
      }
      catch(Exception e)
      {
         return (List.of());
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static Map<String, Object> parseJsonObject(String json)
   {
      if(!StringUtils.hasContent(json))
      {
         return (Map.of());
      }
      try
      {
         return (JsonUtils.toObject(json, new TypeReference<Map<String, Object>>() {}));
      }
      catch(Exception e)
      {
         return (Map.of());
      }
   }



   /*******************************************************************************
    ** Throw the first error of a record (a QPermissionDeniedException for
    ** permission errors, so the notebook UI answers 403).
    *******************************************************************************/
   static void throwIfErrors(QRecord record) throws QException
   {
      if(record != null && CollectionUtils.nullSafeHasContents(record.getErrors()))
      {
         String message = record.getErrors().get(0).getMessage();
         if(record.getErrors().get(0) instanceof com.kingsrook.qqq.backend.core.model.statusmessages.PermissionDeniedMessage)
         {
            throw (new QPermissionDeniedException(message));
         }
         throw (new QBadRequestException("Could not save: " + message));
      }
   }



   /*******************************************************************************
    ** Throw the first error of any record.
    *******************************************************************************/
   static void throwIfErrors(List<QRecord> records) throws QException
   {
      for(QRecord record : CollectionUtils.nonNullList(records))
      {
         throwIfErrors(record);
      }
   }



   /*******************************************************************************
    ** Commit, when the backend gave a transaction.
    *******************************************************************************/
   private static void commit(QBackendTransaction transaction) throws QException
   {
      if(transaction != null)
      {
         transaction.commit();
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static String truncate(String value, int max)
   {
      return (value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…");
   }
}
