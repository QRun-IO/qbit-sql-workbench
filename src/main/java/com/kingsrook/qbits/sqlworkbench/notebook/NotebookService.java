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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.core.type.TypeReference;
import com.kingsrook.qqq.backend.core.actions.QBackendTransaction;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QNotFoundException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchRuntime;
import com.kingsrook.qbits.sqlworkbench.SystemUser;
import com.kingsrook.qbits.sqlworkbench.engine.Caller;
import com.kingsrook.qbits.sqlworkbench.engine.CollectedResult;
import com.kingsrook.qbits.sqlworkbench.engine.ColumnInfo;
import com.kingsrook.qbits.sqlworkbench.engine.ResultCache;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchCell;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebook;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookVersion;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchResultSnapshot;


/*******************************************************************************
 ** Notebooks, their cells, versions and pinned result snapshots.
 **
 ** Visibility: a caller reads their own notebooks and every SHARED one; only
 ** the owner edits, deletes, restores and pins; publishing needs the share
 ** permission and ownership; anyone with access can duplicate what they can
 ** read.
 **
 ** These checks are the boundary: the tables themselves are guarded by the
 ** admin permission (sqlWorkbenchAdmin.hasAccess), which ordinary users do
 ** not hold, so the generic QQQ table UI / API cannot reach other users'
 ** notebooks. Every read and write here runs as the system user (SystemUser)
 ** and every public method checks the Caller first (readable / owned /
 ** canShare) before it reads content or writes anything.
 **
 ** Saves replace a notebook's cells and append a version (full JSON), in one
 ** transaction; versions beyond notebookVersionRetention are pruned.
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
    ** Notebook summaries: scope "mine" (default) or "shared".
    *******************************************************************************/
   public List<Map<String, Object>> list(String scope, Caller caller) throws QException
   {
      return (SystemUser.call(() ->
      {
         QQueryFilter filter = "shared".equalsIgnoreCase(scope)
            ? new QQueryFilter(new QFilterCriteria("visibility", QCriteriaOperator.EQUALS, VISIBILITY_SHARED))
            : new QQueryFilter(new QFilterCriteria("ownerUserId", QCriteriaOperator.EQUALS, caller.userId()));
         filter.withOrderBy(new QFilterOrderBy("modifyDate", false)).withLimit(500);

         List<Map<String, Object>> result = new ArrayList<>();
         for(QRecord record : QueryAction.execute(table(SqlWorkbenchNotebook.TABLE_NAME), filter))
         {
            result.add(summary(new SqlWorkbenchNotebook(record), caller));
         }
         return (result);
      }));
   }



   /*******************************************************************************
    ** One notebook with its cells.
    *******************************************************************************/
   public Map<String, Object> get(Integer id, Caller caller) throws QException
   {
      return (SystemUser.call(() ->
      {
         SqlWorkbenchNotebook notebook = readable(id, caller);
         Map<String, Object>  view     = summary(notebook, caller);
         view.put("parameters", parseParameters(notebook.getParametersJson()));
         view.put("cells", cells(notebook.getId()).stream().map(NotebookService::cellView).toList());
         return (view);
      }));
   }



   /*******************************************************************************
    ** Create a personal notebook (version 1).
    *******************************************************************************/
   public Map<String, Object> create(Map<String, Object> body, Caller caller) throws QException
   {
      return (SystemUser.call(() ->
      {
         Instant now = runtime.getClock().instant();
         SqlWorkbenchNotebook notebook = new SqlWorkbenchNotebook()
            .withOwnerUserId(caller.userId())
            .withOwnerName(caller.userName())
            .withVisibility(VISIBILITY_PERSONAL)
            .withCurrentVersion(0)
            .withCreateDate(now);
         applyFields(notebook, body, true);
         List<SqlWorkbenchCell> cells = parseCells(body.get("cells"));

         try(QBackendTransaction transaction = QBackendTransaction.openFor(new InsertInput(table(SqlWorkbenchNotebook.TABLE_NAME))))
         {
            notebook.setModifyDate(now);
            InsertOutput output = new InsertAction().execute(new InsertInput(table(SqlWorkbenchNotebook.TABLE_NAME)).withRecordEntity(notebook).withTransaction(transaction));
            throwIfErrors(output.getRecords().get(0));
            notebook.setId(output.getRecords().get(0).getValueInteger("id"));
            saveContent(notebook, cells, caller, (String) body.get("comment"), transaction);
            commit(transaction);
         }
         return (get(notebook.getId(), caller));
      }));
   }



   /*******************************************************************************
    ** Save a notebook: fields, cells (replaced), and a new version.
    *******************************************************************************/
   public Map<String, Object> update(Integer id, Map<String, Object> body, Caller caller) throws QException
   {
      return (SystemUser.call(() ->
      {
         SqlWorkbenchNotebook notebook = owned(id, caller);
         applyFields(notebook, body, false);
         List<SqlWorkbenchCell> cells = body.containsKey("cells") ? parseCells(body.get("cells")) : cells(id);

         try(QBackendTransaction transaction = QBackendTransaction.openFor(new InsertInput(table(SqlWorkbenchNotebook.TABLE_NAME))))
         {
            saveContent(notebook, cells, caller, (String) body.get("comment"), transaction);
            commit(transaction);
         }
         pruneVersions(id);
         return (get(id, caller));
      }));
   }



   /*******************************************************************************
    ** Delete a notebook and everything under it.
    *******************************************************************************/
   public void delete(Integer id, Caller caller) throws QException
   {
      SystemUser.run(() ->
      {
         owned(id, caller);
         try(QBackendTransaction transaction = QBackendTransaction.openFor(new InsertInput(table(SqlWorkbenchNotebook.TABLE_NAME))))
         {
            for(String child : List.of(SqlWorkbenchCell.TABLE_NAME, SqlWorkbenchNotebookVersion.TABLE_NAME, SqlWorkbenchResultSnapshot.TABLE_NAME))
            {
               new DeleteAction().execute(new DeleteInput(table(child)).withQueryFilter(byNotebook(id)).withTransaction(transaction));
            }
            new DeleteAction().execute(new DeleteInput(table(SqlWorkbenchNotebook.TABLE_NAME)).withPrimaryKeys(List.of(id)).withTransaction(transaction));
            commit(transaction);
         }
      });
   }



   /*******************************************************************************
    ** Publish to (or withdraw from) the shared library.
    *******************************************************************************/
   public Map<String, Object> publish(Integer id, boolean shared, Caller caller) throws QException
   {
      return (SystemUser.call(() ->
      {
         if(!caller.canShare())
         {
            throw (new QPermissionDeniedException("Publishing to the shared library needs the " + config.getSharePermissionName() + " permission."));
         }
         SqlWorkbenchNotebook notebook = owned(id, caller);
         new UpdateAction().execute(new UpdateInput(table(SqlWorkbenchNotebook.TABLE_NAME)).withRecord(new QRecord()
            .withValue("id", notebook.getId())
            .withValue("visibility", shared ? VISIBILITY_SHARED : VISIBILITY_PERSONAL)
            .withValue("modifyDate", runtime.getClock().instant())));
         return (get(id, caller));
      }));
   }



   /*******************************************************************************
    ** A personal copy of a readable notebook.
    *******************************************************************************/
   public Map<String, Object> duplicate(Integer id, Caller caller) throws QException
   {
      return (SystemUser.call(() ->
      {
         SqlWorkbenchNotebook source = readable(id, caller);
         Map<String, Object>  body   = new LinkedHashMap<>();
         body.put("title", truncate("Copy of " + source.getTitle(), 255));
         body.put("description", source.getDescription());
         body.put("defaultBackendName", source.getDefaultBackendName());
         body.put("parameters", parseParameters(source.getParametersJson()));
         List<Map<String, Object>> cells = new ArrayList<>();
         for(SqlWorkbenchCell cell : cells(id))
         {
            Map<String, Object> view = cellView(cell);
            view.put("cellKey", UUID.randomUUID().toString());
            cells.add(view);
         }
         body.put("cells", cells);
         body.put("comment", "Duplicated from notebook " + id);
         return (create(body, caller));
      }));
   }



   /*******************************************************************************
    ** Version list, newest first.
    *******************************************************************************/
   public List<Map<String, Object>> versions(Integer id, Caller caller) throws QException
   {
      return (SystemUser.call(() ->
      {
         readable(id, caller);
         QQueryFilter filter = byNotebook(id).withOrderBy(new QFilterOrderBy("versionNumber", false));
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
      }));
   }



   /*******************************************************************************
    ** One version's content.
    *******************************************************************************/
   public Map<String, Object> version(Integer id, Integer versionNumber, Caller caller) throws QException
   {
      return (SystemUser.call(() ->
      {
         readable(id, caller);
         SqlWorkbenchNotebookVersion version = findVersion(id, versionNumber);
         Map<String, Object>         view    = new LinkedHashMap<>();
         view.put("versionNumber", version.getVersionNumber());
         view.put("savedByName", version.getSavedByName());
         view.put("comment", version.getComment());
         view.put("createDate", String.valueOf(version.getCreateDate()));
         view.put("notebook", parseJsonObject(version.getContentJson()));
         return (view);
      }));
   }



   /*******************************************************************************
    ** Restore a version (saved as a new version).
    *******************************************************************************/
   public Map<String, Object> restore(Integer id, Integer versionNumber, Caller caller) throws QException
   {
      return (SystemUser.call(() ->
      {
         owned(id, caller);
         Map<String, Object> content = parseJsonObject(findVersion(id, versionNumber).getContentJson());
         Map<String, Object> body    = new LinkedHashMap<>(content);
         body.put("comment", "Restored version " + versionNumber);
         return (update(id, body, caller));
      }));
   }



   /*******************************************************************************
    ** Pinned snapshots of a notebook.
    *******************************************************************************/
   public List<Map<String, Object>> snapshots(Integer id, Caller caller) throws QException
   {
      return (SystemUser.call(() ->
      {
         readable(id, caller);
         List<Map<String, Object>> result = new ArrayList<>();
         for(QRecord record : QueryAction.execute(table(SqlWorkbenchResultSnapshot.TABLE_NAME), byNotebook(id).withOrderBy(new QFilterOrderBy("id", false))))
         {
            result.add(snapshotView(new SqlWorkbenchResultSnapshot(record)));
         }
         return (result);
      }));
   }



   /*******************************************************************************
    ** Pin a cached result of the caller to a notebook cell.
    *******************************************************************************/
   public Map<String, Object> createSnapshot(Integer id, Map<String, Object> body, Caller caller, String sqlText) throws QException
   {
      return (SystemUser.call(() ->
      {
         owned(id, caller);
         String executionUuid = (String) body.get("executionUuid");
         ResultCache.Entry entry = runtime.getResultCache().get(executionUuid, caller.userId())
            .orElseThrow(() -> new QNotFoundException("That result is no longer available; run the cell again, then pin it."));

         CollectedResult    result  = entry.result();
         int                max     = config.getSnapshotMaxRows();
         List<List<Object>> rows    = result.rows().size() > max ? result.rows().subList(0, max) : result.rows();
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

         InsertOutput output = new InsertAction().execute(new InsertInput(table(SqlWorkbenchResultSnapshot.TABLE_NAME)).withRecordEntity(snapshot));
         throwIfErrors(output.getRecords().get(0));
         snapshot.setId(output.getRecords().get(0).getValueInteger("id"));
         return (snapshotView(snapshot));
      }));
   }



   /*******************************************************************************
    ** Unpin.
    *******************************************************************************/
   public void deleteSnapshot(Integer id, Integer snapshotId, Caller caller) throws QException
   {
      SystemUser.run(() ->
      {
         owned(id, caller);
         QQueryFilter filter = byNotebook(id).withCriteria(new QFilterCriteria("id", QCriteriaOperator.EQUALS, snapshotId));
         if(QueryAction.execute(table(SqlWorkbenchResultSnapshot.TABLE_NAME), filter).isEmpty())
         {
            throw (new QNotFoundException("Snapshot not found."));
         }
         new DeleteAction().execute(new DeleteInput(table(SqlWorkbenchResultSnapshot.TABLE_NAME)).withPrimaryKeys(List.of(snapshotId)));
      });
   }



   /*******************************************************************************
    ** A notebook the caller may read, or 404 (never 403: personal notebooks of
    ** others do not exist for the caller).
    *******************************************************************************/
   SqlWorkbenchNotebook readable(Integer id, Caller caller) throws QException
   {
      SqlWorkbenchNotebook notebook = find(id);
      if(notebook == null || !(Objects.equals(notebook.getOwnerUserId(), caller.userId()) || VISIBILITY_SHARED.equals(notebook.getVisibility())))
      {
         throw (new QNotFoundException("Notebook not found."));
      }
      return (notebook);
   }



   /*******************************************************************************
    ** A notebook the caller owns: 404 if unreadable, 403 if shared but not
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
    **
    *******************************************************************************/
   private SqlWorkbenchNotebook find(Integer id) throws QException
   {
      return (SystemUser.call(() ->
      {
         if(id == null)
         {
            throw (new QBadRequestException("A notebook id is required."));
         }
         List<QRecord> records = QueryAction.execute(table(SqlWorkbenchNotebook.TABLE_NAME), new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.EQUALS, id)));
         return (records.isEmpty() ? null : new SqlWorkbenchNotebook(records.get(0)));
      }));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private List<SqlWorkbenchCell> cells(Integer notebookId) throws QException
   {
      return (SystemUser.call(() ->
      {
         QQueryFilter filter = byNotebook(notebookId).withOrderBy(new QFilterOrderBy("sortOrder")).withOrderBy(new QFilterOrderBy("id"));
         return (QueryAction.execute(table(SqlWorkbenchCell.TABLE_NAME), SqlWorkbenchCell.class, filter));
      }));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private SqlWorkbenchNotebookVersion findVersion(Integer id, Integer versionNumber) throws QException
   {
      return (SystemUser.call(() ->
      {
         QQueryFilter filter = byNotebook(id).withCriteria(new QFilterCriteria("versionNumber", QCriteriaOperator.EQUALS, versionNumber));
         List<QRecord> records = QueryAction.execute(table(SqlWorkbenchNotebookVersion.TABLE_NAME), filter);
         if(records.isEmpty())
         {
            throw (new QNotFoundException("Version not found."));
         }
         return (new SqlWorkbenchNotebookVersion(records.get(0)));
      }));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static QQueryFilter byNotebook(Integer id)
   {
      return (new QQueryFilter(new QFilterCriteria("notebookId", QCriteriaOperator.EQUALS, id)));
   }



   /*******************************************************************************
    ** Write the notebook row (version + 1), replace its cells, append a
    ** version - all in the given transaction.
    *******************************************************************************/
   private void saveContent(SqlWorkbenchNotebook notebook, List<SqlWorkbenchCell> cells, Caller caller, String comment, QBackendTransaction transaction) throws QException
   {
      Instant now     = runtime.getClock().instant();
      int     version = (notebook.getCurrentVersion() == null ? 0 : notebook.getCurrentVersion()) + 1;
      notebook.setCurrentVersion(version);
      notebook.setModifyDate(now);

      new UpdateAction().execute(new UpdateInput(table(SqlWorkbenchNotebook.TABLE_NAME)).withTransaction(transaction).withRecord(new QRecord()
         .withValue("id", notebook.getId())
         .withValue("title", notebook.getTitle())
         .withValue("description", notebook.getDescription())
         .withValue("defaultBackendName", notebook.getDefaultBackendName())
         .withValue("parametersJson", notebook.getParametersJson())
         .withValue("currentVersion", version)
         .withValue("modifyDate", now)));

      new DeleteAction().execute(new DeleteInput(table(SqlWorkbenchCell.TABLE_NAME)).withQueryFilter(byNotebook(notebook.getId())).withTransaction(transaction));
      for(int i = 0; i < cells.size(); i++)
      {
         cells.get(i).withId(null).withNotebookId(notebook.getId()).withSortOrder(i).withCreateDate(now).withModifyDate(now);
      }
      if(!cells.isEmpty())
      {
         InsertOutput output = new InsertAction().execute(new InsertInput(table(SqlWorkbenchCell.TABLE_NAME)).withRecordEntities(cells).withTransaction(transaction));
         for(QRecord record : output.getRecords())
         {
            throwIfErrors(record);
         }
      }

      Map<String, Object> content = new LinkedHashMap<>();
      content.put("title", notebook.getTitle());
      content.put("description", notebook.getDescription());
      content.put("defaultBackendName", notebook.getDefaultBackendName());
      content.put("parameters", parseParameters(notebook.getParametersJson()));
      content.put("cells", cells.stream().map(NotebookService::cellView).toList());

      SqlWorkbenchNotebookVersion versionRow = new SqlWorkbenchNotebookVersion()
         .withNotebookId(notebook.getId())
         .withVersionNumber(version)
         .withContentJson(JsonUtils.toJson(content))
         .withSavedByUserId(caller.userId())
         .withSavedByName(caller.userName())
         .withComment(truncate(comment, 255))
         .withCreateDate(now);
      InsertOutput output = new InsertAction().execute(new InsertInput(table(SqlWorkbenchNotebookVersion.TABLE_NAME)).withRecordEntity(versionRow).withTransaction(transaction));
      throwIfErrors(output.getRecords().get(0));
   }



   /*******************************************************************************
    ** Delete versions beyond the retention, oldest first.
    *******************************************************************************/
   private void pruneVersions(Integer id) throws QException
   {
      SystemUser.run(() ->
      {
         QueryInput input = new QueryInput(table(SqlWorkbenchNotebookVersion.TABLE_NAME))
            .withFilter(byNotebook(id).withOrderBy(new QFilterOrderBy("versionNumber", false)));
         List<QRecord> records = new QueryAction().execute(input).getRecords();
         if(records.size() <= config.getNotebookVersionRetention())
         {
            return;
         }
         List<Serializable> stale = new ArrayList<>();
         for(QRecord record : records.subList(config.getNotebookVersionRetention(), records.size()))
         {
            stale.add(record.getValueInteger("id"));
         }
         new DeleteAction().execute(new DeleteInput(table(SqlWorkbenchNotebookVersion.TABLE_NAME)).withPrimaryKeys(stale));
      });
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
    **
    *******************************************************************************/
   private static void throwIfErrors(QRecord record) throws QException
   {
      if(record.getErrors() != null && !record.getErrors().isEmpty())
      {
         throw (new QException("Could not save: " + record.getErrors().get(0).getMessage()));
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
