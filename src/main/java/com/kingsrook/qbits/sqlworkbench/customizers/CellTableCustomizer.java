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

package com.kingsrook.qbits.sqlworkbench.customizers;


import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchCell;
import com.kingsrook.qbits.sqlworkbench.notebook.NotebookService;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Cell invariants, the same for the notebook UI and the table UI / API:
 ** type SQL or MARKDOWN, content within the SQL length limit, a stable
 ** cellKey, at most NotebookService.MAX_CELLS per notebook, new cells
 ** appended at the end, cells never move between notebooks, and every
 ** change appends a notebook version. Ownership is the notebook's (the
 ** record lock checks it through the notebook).
 *******************************************************************************/
public class CellTableCustomizer extends WorkbenchTableCustomizer
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> preInsert(InsertInput insertInput, List<QRecord> records, boolean isPreview) throws QException
   {
      SqlWorkbenchQBitConfig config        = config();
      Instant                now           = clock().instant();
      boolean                wholeNotebook = insertInput.hasFlag(SqlWorkbenchActionFlag.NOTEBOOK_SAVE) || insertInput.hasFlag(SqlWorkbenchActionFlag.INTERNAL);

      Map<Integer, int[]> nextSortOrderAndCount = new HashMap<>();
      for(QRecord record : records)
      {
         Integer notebookId = record.getValueInteger("notebookId");
         if(notebookId == null)
         {
            badInput(record, "A cell needs a notebook.");
            continue;
         }

         validate(config, record, true);
         if(!StringUtils.hasContent(record.getValueString("cellKey")))
         {
            record.setValue("cellKey", UUID.randomUUID().toString());
         }
         record.setValue("createDate", now);
         record.setValue("modifyDate", now);

         if(!wholeNotebook)
         {
            int[] state = nextSortOrderAndCount.get(notebookId);
            if(state == null)
            {
               state = existing(config, notebookId);
               nextSortOrderAndCount.put(notebookId, state);
            }
            if(state[1] >= NotebookService.MAX_CELLS)
            {
               badInput(record, "A notebook can have at most " + NotebookService.MAX_CELLS + " cells.");
               continue;
            }
            state[1]++;
            if(record.getValueInteger("sortOrder") == null)
            {
               record.setValue("sortOrder", state[0]++);
            }
         }
      }
      return (records);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> postInsert(InsertInput insertInput, List<QRecord> records) throws QException
   {
      if(!insertInput.hasFlag(SqlWorkbenchActionFlag.NOTEBOOK_SAVE) && !insertInput.hasFlag(SqlWorkbenchActionFlag.INTERNAL))
      {
         appendVersions(notebookIds(records), "Cell added", insertInput.getTransaction());
      }
      return (records);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> preUpdate(UpdateInput updateInput, List<QRecord> records, boolean isPreview, Optional<List<QRecord>> oldRecordList) throws QException
   {
      if(updateInput.hasFlag(SqlWorkbenchActionFlag.INTERNAL))
      {
         return (records);
      }

      SqlWorkbenchQBitConfig config = config();
      Instant                now    = clock().instant();
      for(QRecord record : records)
      {
         if(!ok(record))
         {
            continue;
         }
         QRecord old = oldRecord(oldRecordList, record);
         if(changes(record, old, "notebookId"))
         {
            badInput(record, "A cell cannot move to another notebook.");
            continue;
         }
         if(changes(record, old, "cellKey"))
         {
            badInput(record, "A cell's key cannot change.");
            continue;
         }
         record.removeValue("createDate");
         validate(config, record, false);
         record.setValue("modifyDate", now);
      }
      return (records);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> postUpdate(UpdateInput updateInput, List<QRecord> records, Optional<List<QRecord>> oldRecordList) throws QException
   {
      if(!updateInput.hasFlag(SqlWorkbenchActionFlag.NOTEBOOK_SAVE) && !updateInput.hasFlag(SqlWorkbenchActionFlag.INTERNAL))
      {
         Set<Integer> ids = new LinkedHashSet<>();
         for(QRecord record : records)
         {
            QRecord old = oldRecord(oldRecordList, record);
            if(ok(record) && old != null && old.getValueInteger("notebookId") != null)
            {
               ids.add(old.getValueInteger("notebookId"));
            }
         }
         appendVersions(ids, "Cell edited", updateInput.getTransaction());
      }
      return (records);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> postDelete(DeleteInput deleteInput, List<QRecord> records) throws QException
   {
      if(!deleteInput.hasFlag(SqlWorkbenchActionFlag.NOTEBOOK_SAVE) && !deleteInput.hasFlag(SqlWorkbenchActionFlag.INTERNAL))
      {
         appendVersions(notebookIds(records), "Cell removed", deleteInput.getTransaction());
      }
      return (records);
   }



   /*******************************************************************************
    ** Type, content and key checks.
    *******************************************************************************/
   private static void validate(SqlWorkbenchQBitConfig config, QRecord record, boolean inserting)
   {
      if(inserting || record.getValues().containsKey("cellType"))
      {
         String type = record.getValueString("cellType");
         type = StringUtils.hasContent(type) ? type.trim().toUpperCase(Locale.ROOT) : NotebookService.CELL_SQL;
         if(!NotebookService.CELL_SQL.equals(type) && !NotebookService.CELL_MARKDOWN.equals(type))
         {
            badInput(record, "cellType must be SQL or MARKDOWN.");
            return;
         }
         record.setValue("cellType", type);
      }

      if(inserting && record.getValue("content") == null)
      {
         record.setValue("content", "");
      }
      String content = record.getValueString("content");
      if(content != null && content.length() > config.getMaxSqlLength())
      {
         badInput(record, "A cell is longer than the limit of " + config.getMaxSqlLength() + " characters.");
         return;
      }

      String key = record.getValueString("cellKey");
      if(key != null && key.length() > 64)
      {
         badInput(record, "A cell key is at most 64 characters.");
      }
   }



   /*******************************************************************************
    ** {next sort order, current cell count} of a notebook, as the session sees
    ** it (a notebook the session cannot read counts as empty; its insert then
    ** fails the record lock).
    *******************************************************************************/
   private static int[] existing(SqlWorkbenchQBitConfig config, Integer notebookId) throws QException
   {
      List<QRecord> cells = new QueryAction().execute(new QueryInput(config.applyPrefix(SqlWorkbenchCell.TABLE_NAME))
         .withFilter(new QQueryFilter(new QFilterCriteria("notebookId", QCriteriaOperator.EQUALS, notebookId)))).getRecords();
      int max = -1;
      for(QRecord cell : cells)
      {
         Integer sortOrder = cell.getValueInteger("sortOrder");
         if(sortOrder != null && sortOrder > max)
         {
            max = sortOrder;
         }
      }
      return (new int[] { max + 1, cells.size() });
   }
}
