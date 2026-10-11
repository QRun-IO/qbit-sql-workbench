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

package com.kingsrook.qbits.sqlworkbench.schema;


import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchRuntime;
import com.kingsrook.qbits.sqlworkbench.engine.WorkbenchBackends;
import com.kingsrook.qqq.backend.module.rdbms.actions.AbstractRDBMSAction;


/*******************************************************************************
 ** Describes a target backend for autocomplete: schemas, tables, columns
 ** (JDBC DatabaseMetaData, system schemas excluded, capped at
 ** maxSchemaTables), keywords and functions, plus the instance's QQQ tables
 ** on that backend with their SQL table and column names. Cached per
 ** backend in the runtime.
 *******************************************************************************/
public class SchemaIntrospector
{
   private static final Set<String> SYSTEM_SCHEMAS = Set.of("pg_catalog", "information_schema", "pg_toast", "mysql", "performance_schema", "sys");

   private static final List<String> CORE_KEYWORDS = List.of("SELECT", "FROM", "WHERE", "GROUP BY", "HAVING", "ORDER BY", "LIMIT", "OFFSET", "JOIN",
      "LEFT JOIN", "RIGHT JOIN", "INNER JOIN", "FULL OUTER JOIN", "CROSS JOIN", "ON", "USING", "AS", "DISTINCT", "UNION", "UNION ALL", "INTERSECT", "EXCEPT",
      "WITH", "CASE", "WHEN", "THEN", "ELSE", "END", "AND", "OR", "NOT", "IN", "EXISTS", "BETWEEN", "LIKE", "IS NULL", "IS NOT NULL", "ASC", "DESC",
      "INSERT INTO", "VALUES", "UPDATE", "SET", "DELETE FROM", "RETURNING", "CREATE TABLE", "ALTER TABLE", "DROP TABLE", "EXPLAIN");

   private static final List<String> CORE_FUNCTIONS = List.of("count", "sum", "avg", "min", "max", "coalesce", "nullif", "cast", "lower", "upper", "trim",
      "length", "substring", "replace", "concat", "round", "floor", "ceil", "abs", "now", "current_date", "current_timestamp");

   private final QInstance           qInstance;
   private final SqlWorkbenchRuntime runtime;
   private final WorkbenchBackends   backends;



   /*******************************************************************************
    **
    *******************************************************************************/
   public SchemaIntrospector(QInstance qInstance, SqlWorkbenchRuntime runtime, WorkbenchBackends backends)
   {
      this.qInstance = qInstance;
      this.runtime = runtime;
      this.backends = backends;
   }



   /*******************************************************************************
    ** The schema of a backend, from cache unless refresh or stale.
    *******************************************************************************/
   public Map<String, Object> describe(String backendName, boolean refresh) throws QException
   {
      WorkbenchBackends.Target target = backends.getTarget(backendName);
      if(!refresh)
      {
         SqlWorkbenchRuntime.CachedSchema cached = runtime.getCachedSchema(target.name());
         if(cached != null)
         {
            return (cached.schema());
         }
      }

      Map<String, Object> schema = new LinkedHashMap<>();
      schema.put("backendName", target.name());
      schema.put("dialect", target.dialect().getUiName());

      try(Connection connection = backends.openConnection(target, true))
      {
         DatabaseMetaData metaData = connection.getMetaData();
         boolean[]        truncated = new boolean[1];
         schema.put("schemas", readSchemas(metaData, truncated));
         schema.put("truncated", truncated[0]);
         schema.put("keywords", keywords(metaData));
         schema.put("functions", functions(metaData));
      }
      catch(SQLException e)
      {
         throw (new QException("Could not read the schema of backend " + target.name() + ": " + e.getMessage(), e));
      }

      schema.put("qqqTables", qqqTables(target.name()));
      schema.put("cachedAt", runtime.getClock().instant().toString());
      runtime.putCachedSchema(target.name(), schema);
      return (schema);
   }



   /*******************************************************************************
    ** Schemas, tables and columns. Schema-less databases (MySQL catalogs,
    ** SQLite) report the catalog, or "main".
    *******************************************************************************/
   List<Map<String, Object>> readSchemas(DatabaseMetaData metaData, boolean[] truncated) throws SQLException
   {
      Map<String, Map<String, Map<String, Object>>> tablesBySchema = new LinkedHashMap<>();
      int                                           count          = 0;

      try(ResultSet rs = metaData.getTables(null, null, "%", null))
      {
         while(rs.next())
         {
            String type = String.valueOf(rs.getString("TABLE_TYPE")).toUpperCase(Locale.ROOT);
            if(!(type.equals("TABLE") || type.equals("VIEW") || type.equals("BASE TABLE") || type.equals("MATERIALIZED VIEW") || type.equals("PARTITIONED TABLE")))
            {
               continue;
            }

            String schemaName = schemaName(rs.getString("TABLE_SCHEM"), rs.getString("TABLE_CAT"));
            if(isSystemSchema(schemaName))
            {
               continue;
            }

            if(count >= runtime.getConfig().getMaxSchemaTables())
            {
               truncated[0] = true;
               break;
            }
            count++;

            Map<String, Object> table = new LinkedHashMap<>();
            table.put("name", rs.getString("TABLE_NAME"));
            table.put("type", type);
            table.put("columns", new ArrayList<Map<String, Object>>());
            tablesBySchema.computeIfAbsent(schemaName, s -> new LinkedHashMap<>()).put(rs.getString("TABLE_NAME"), table);
         }
      }

      readColumns(metaData, tablesBySchema);

      List<Map<String, Object>> schemas = new ArrayList<>();
      for(Map.Entry<String, Map<String, Map<String, Object>>> entry : tablesBySchema.entrySet())
      {
         Map<String, Object> schema = new LinkedHashMap<>();
         schema.put("name", entry.getKey());
         schema.put("tables", new ArrayList<>(entry.getValue().values()));
         schemas.add(schema);
      }
      return (schemas);
   }



   /*******************************************************************************
    ** One getColumns call for everything, assigned to the tables kept above.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private void readColumns(DatabaseMetaData metaData, Map<String, Map<String, Map<String, Object>>> tablesBySchema) throws SQLException
   {
      try(ResultSet rs = metaData.getColumns(null, null, "%", "%"))
      {
         while(rs.next())
         {
            Map<String, Map<String, Object>> tables = tablesBySchema.get(schemaName(rs.getString("TABLE_SCHEM"), rs.getString("TABLE_CAT")));
            Map<String, Object>              table  = tables == null ? null : tables.get(rs.getString("TABLE_NAME"));
            if(table == null)
            {
               continue;
            }

            Map<String, Object> column = new LinkedHashMap<>();
            column.put("name", rs.getString("COLUMN_NAME"));
            column.put("typeName", rs.getString("TYPE_NAME"));
            column.put("nullable", rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls);
            ((List<Map<String, Object>>) table.get("columns")).add(column);
         }
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static String schemaName(String schema, String catalog)
   {
      if(StringUtils.hasContent(schema))
      {
         return (schema);
      }
      return (StringUtils.hasContent(catalog) ? catalog : "main");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static boolean isSystemSchema(String schemaName)
   {
      String lower = schemaName.toLowerCase(Locale.ROOT);
      return (SYSTEM_SCHEMAS.contains(lower) || lower.startsWith("pg_temp") || lower.startsWith("pg_toast_temp"));
   }



   /*******************************************************************************
    ** Core keywords plus the driver's extra keywords.
    *******************************************************************************/
   static List<String> keywords(DatabaseMetaData metaData)
   {
      Set<String> keywords = new TreeSet<>(CORE_KEYWORDS);
      try
      {
         keywords.addAll(splitList(metaData.getSQLKeywords(), true));
      }
      catch(SQLException | RuntimeException e)
      {
         ////////////////////////////////////////
         // optional: keep the core keywords   //
         ////////////////////////////////////////
      }
      return (new ArrayList<>(keywords));
   }



   /*******************************************************************************
    ** Core functions plus the driver's function lists.
    *******************************************************************************/
   static List<String> functions(DatabaseMetaData metaData)
   {
      Set<String> functions = new TreeSet<>(CORE_FUNCTIONS);
      try
      {
         functions.addAll(splitList(metaData.getNumericFunctions(), false));
         functions.addAll(splitList(metaData.getStringFunctions(), false));
         functions.addAll(splitList(metaData.getSystemFunctions(), false));
         functions.addAll(splitList(metaData.getTimeDateFunctions(), false));
      }
      catch(SQLException | RuntimeException e)
      {
         ////////////////////////////////////////
         // optional: keep the core functions  //
         ////////////////////////////////////////
      }
      return (new ArrayList<>(functions));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static List<String> splitList(String list, boolean upper)
   {
      if(!StringUtils.hasContent(list))
      {
         return (List.of());
      }
      return (Arrays.stream(list.split(",")).map(String::trim).filter(s -> !s.isEmpty())
         .map(s -> upper ? s.toUpperCase(Locale.ROOT) : s.toLowerCase(Locale.ROOT)).toList());
   }



   /*******************************************************************************
    ** QQQ tables stored on this backend, with their SQL names.
    *******************************************************************************/
   List<Map<String, Object>> qqqTables(String backendName)
   {
      List<Map<String, Object>> tables = new ArrayList<>();
      for(QTableMetaData table : qInstance.getTables().values())
      {
         if(!backendName.equals(table.getBackendName()))
         {
            continue;
         }

         Map<String, Object> view = new LinkedHashMap<>();
         view.put("name", table.getName());
         view.put("label", table.getLabel());
         view.put("sqlName", AbstractRDBMSAction.getTableName(table));

         List<Map<String, Object>> fields = new ArrayList<>();
         for(QFieldMetaData field : table.getFields().values())
         {
            Map<String, Object> fieldView = new LinkedHashMap<>();
            fieldView.put("name", field.getName());
            fieldView.put("label", field.getLabel());
            fieldView.put("sqlName", AbstractRDBMSAction.getColumnName(field));
            fieldView.put("type", field.getType() == null ? null : field.getType().name());
            fields.add(fieldView);
         }
         view.put("fields", fields);
         tables.add(view);
      }
      return (tables);
   }

}
