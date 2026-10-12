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
package com.kingsrook.qbits.sqlworkbench;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.sqlworkbench.metadata.SqlWorkbenchAppMetaDataProducer;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchExecution;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebook;


/*******************************************************************************
 ** Opt-in hooks for wiring the workbench's main tables into a host's ESB and
 ** Quick Search, without the qbit depending on either module: the host reads
 ** these names and field lists and passes them to its own qqq-esb
 ** (EsbTableMetaData publications) and qbit-quick-search
 ** (SearchableTableConfig) configuration.
 **
 ** The main tables are Notebooks and the Execution audit (the tables in the
 ** app's nav). The sub-tables (cells, versions, pinned results, shares) are
 ** reached through a notebook and are left out on purpose.
 **
 ** What leaves the database: a notebook event carries the notebook row
 ** (title, description, owner, visibility, default backend, parameter
 ** definitions). An execution event carries the audit row, including the SQL
 ** text and the parameter values; results are never stored, so they are
 ** never published or indexed. Search hits are re-read through the tables'
 ** record locks, so a user only finds what they could open.
 *******************************************************************************/
public final class SqlWorkbenchIntegrations
{

   /*******************************************************************************
    ** A table for Quick Search: its fields with weights (most important
    ** first), the field basepull compares, and the label of a hit.
    **
    ** @param tableName          the (prefixed) table name
    ** @param fieldWeights       searchable field names and their weights
    ** @param basepullTimestamp  field whose value moves when a row changes
    ** @param recordLabelFormat  format of a hit's label
    ** @param recordLabelFields  fields for recordLabelFormat
    *******************************************************************************/
   public record SearchableTable(String tableName, Map<String, Integer> fieldWeights, String basepullTimestamp, String recordLabelFormat, List<String> recordLabelFields)
   {
   }



   /*******************************************************************************
    ** Utility class - no instances.
    *******************************************************************************/
   private SqlWorkbenchIntegrations()
   {
   }



   /*******************************************************************************
    ** The main tables (in nav order), with the config's prefix applied.
    *******************************************************************************/
   public static List<String> mainTableNames(SqlWorkbenchQBitConfig config)
   {
      List<String> names = new ArrayList<>();
      for(String tableName : SqlWorkbenchAppMetaDataProducer.MAIN_TABLES)
      {
         names.add(config.applyPrefix(tableName));
      }
      return (names);
   }



   /*******************************************************************************
    ** The tables whose insert / update / delete events a host may publish to
    ** its ESB (the main tables). Executions are inserted when a run starts and
    ** updated when it ends.
    *******************************************************************************/
   public static List<String> esbTableNames(SqlWorkbenchQBitConfig config)
   {
      return (mainTableNames(config));
   }



   /*******************************************************************************
    ** The main tables as Quick Search tables:
    ** - Notebooks: title, description, owner, default backend; basepull on
    **   modifyDate (cell edits bump it, as each one saves a new version).
    ** - Executions: SQL text, user, execution UUID, error, backend; basepull
    **   on createDate (the audit has no modify date; real-time indexing picks
    **   up the end-of-run update, and the reconcile catches anything missed).
    *******************************************************************************/
   public static List<SearchableTable> quickSearchTables(SqlWorkbenchQBitConfig config)
   {
      Map<String, Integer> notebook = new LinkedHashMap<>();
      notebook.put("title", 10);
      notebook.put("description", 5);
      notebook.put("ownerName", 3);
      notebook.put("defaultBackendName", 1);

      Map<String, Integer> execution = new LinkedHashMap<>();
      execution.put("sqlText", 8);
      execution.put("executionUuid", 5);
      execution.put("userName", 3);
      execution.put("errorMessage", 2);
      execution.put("backendName", 1);

      return (List.of(
         new SearchableTable(config.applyPrefix(SqlWorkbenchNotebook.TABLE_NAME), notebook, "modifyDate", "%s", List.of("title")),
         new SearchableTable(config.applyPrefix(SqlWorkbenchExecution.TABLE_NAME), execution, "createDate", "SQL run by %s (%s)", List.of("userName", "mode"))));
   }

}
