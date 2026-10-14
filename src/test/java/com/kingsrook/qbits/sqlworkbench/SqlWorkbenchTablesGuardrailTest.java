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


import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import com.kingsrook.qbits.sqlworkbench.metadata.SqlWorkbenchHelp;
import com.kingsrook.qqq.backend.core.model.metadata.help.QHelpContent;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Guardrail (owner requirement 2026-10-14: "all tables should be populated and
 ** the qbit should be fully using them"). For every sql_workbench_* table:
 ** - runtime code (not just its model, metadata or home dashboard) reads or
 **   writes it, so no table exists only on paper;
 ** - it is listed in STARTS_EMPTY with why (every table here is user content or
 **   a log, so none is seeded);
 ** - it has bundle help that says what it is for and how its rows get created.
 ** A new table fails this test until it is wired and explained.
 *******************************************************************************/
class SqlWorkbenchTablesGuardrailTest extends BaseSqlWorkbenchTest
{
   private static final Path MAIN = Path.of("src", "main", "java", "com", "kingsrook", "qbits", "sqlworkbench");

   /*******************************************************************************
    ** Every table legitimately starts empty, and where its rows come from.
    *******************************************************************************/
   static final Map<String, String> STARTS_EMPTY = Map.of(
      "sql_workbench_notebook", "user content: someone saves a notebook",
      "sql_workbench_cell", "user content: the cells of a saved notebook",
      "sql_workbench_notebook_version", "runtime: every notebook save",
      "sql_workbench_notebook_share", "user content: someone shares a notebook",
      "sql_workbench_result_snapshot", "user content: someone pins a result",
      "sql_workbench_execution", "runtime: every statement run (the audit)");



   /*******************************************************************************
    ** Every table is used by runtime code.
    *******************************************************************************/
   @Test
   void everyTableIsUsedAtRuntime() throws Exception
   {
      Map<String, String> entityByTable = entityByTable();
      List<String>        runtimeSource;
      try(Stream<Path> files = Files.walk(MAIN))
      {
         runtimeSource = files.filter(p -> p.toString().endsWith(".java"))
            .filter(p -> !p.startsWith(MAIN.resolve("model")) && !p.startsWith(MAIN.resolve("metadata")) && !p.startsWith(MAIN.resolve("home")))
            .map(SqlWorkbenchTablesGuardrailTest::read).toList();
      }
      for(String table : workbenchTables())
      {
         String entity = entityByTable.get(table);
         assertThat(entity).as("entity class for " + table).isNotNull();
         Pattern use = Pattern.compile("\\b" + entity + "(\\.TABLE_NAME|\\.class|\\(|::)");
         assertThat(runtimeSource.stream().anyMatch(source -> use.matcher(source).find()))
            .as(table + " (" + entity + ") must be read or written by runtime code, not only declared").isTrue();
      }
   }



   /*******************************************************************************
    ** Every table is explained as starting empty.
    *******************************************************************************/
   @Test
   void everyTableIsExplained()
   {
      assertThat(workbenchTables()).isEqualTo(STARTS_EMPTY.keySet().stream().sorted().toList());
   }



   /*******************************************************************************
    ** Every table and the app have bundle help that says how rows get created.
    *******************************************************************************/
   @Test
   void everyTableHasEmptyStateHelp() throws Exception
   {
      assertThat(SqlWorkbenchHelp.entries()).allSatisfy((key, entry) -> assertThat(entry.path("summary").asText().length()).as(key).isLessThanOrEqualTo(120));
      for(String table : workbenchTables())
      {
         QTableMetaData     meta = qInstance.getTable(table);
         List<QHelpContent> help = meta.getHelpContent() == null ? null : meta.getHelpContent().get(SqlWorkbenchHelp.SLOT);
         assertThat(help).as(table + " table help").isNotEmpty();
         assertThat(help.get(0).getContent()).as(table + " help says how rows get created").contains("How rows get created");
      }
      assertThat(qInstance.getApp("sqlWorkbench").getHelpContent()).containsKey(SqlWorkbenchHelp.SLOT);
      assertThat(SqlWorkbenchHelp.entries().keySet()).allMatch(key -> !key.startsWith("table:") || qInstance.getTable(key.substring(6, key.indexOf(';'))) != null,
         "help names only real tables");
   }



   /*******************************************************************************
    ** Help a host already wrote is kept; the bundle only fills empty slots.
    *******************************************************************************/
   @Test
   void hostHelpIsNotOverridden() throws Exception
   {
      QTableMetaData table = qInstance.getTable("sql_workbench_notebook");
      table.getHelpContent().remove(SqlWorkbenchHelp.SLOT);
      table.withHelpContent(SqlWorkbenchHelp.SLOT, new QHelpContent("host words"));

      SqlWorkbenchHelp.apply(qInstance, config);

      assertThat(table.getHelpContent().get(SqlWorkbenchHelp.SLOT).get(0).getContent()).isEqualTo("host words");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private List<String> workbenchTables()
   {
      List<String> tables = qInstance.getTables().values().stream().map(QTableMetaData::getName).filter(n -> n.startsWith("sql_workbench_")).sorted().toList();
      assertThat(tables).hasSize(6);
      return (tables);
   }



   /*******************************************************************************
    ** model/X.java declares TABLE_NAME = "x".
    *******************************************************************************/
   private static Map<String, String> entityByTable() throws Exception
   {
      Map<String, String> rs = new HashMap<>();
      Pattern             p  = Pattern.compile("TABLE_NAME\\s*=\\s*\"([a-z_]+)\"");
      try(Stream<Path> files = Files.list(MAIN.resolve("model")))
      {
         for(Path file : files.toList())
         {
            Matcher m = p.matcher(read(file));
            if(m.find())
            {
               rs.put(m.group(1), file.getFileName().toString().replace(".java", ""));
            }
         }
      }
      return (rs);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String read(Path path)
   {
      try
      {
         return (Files.readString(path, StandardCharsets.UTF_8));
      }
      catch(Exception e)
      {
         throw (new IllegalStateException(e));
      }
   }

}
