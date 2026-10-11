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

package com.kingsrook.qbits.sqlworkbench.explain;


import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.sqlworkbench.sql.SqlDialect;
import org.json.JSONArray;
import org.json.JSONObject;


/*******************************************************************************
 ** Builds EXPLAIN commands per dialect and normalizes their output into a
 ** PlanNode tree.
 **
 ** Postgres: EXPLAIN (FORMAT JSON[, ANALYZE, BUFFERS]) - the JSON "Plan" tree.
 ** MySQL:    EXPLAIN FORMAT=JSON (query_block, best effort) or EXPLAIN
 **           ANALYZE (text tree).
 ** SQLite:   EXPLAIN QUERY PLAN - (id, parent, notused, detail) rows.
 ** H2 / other: EXPLAIN [ANALYZE] - text, as one node per line.
 *******************************************************************************/
public class PlanParser
{
   private static final Set<String> POSTGRES_KNOWN_KEYS = Set.of("Node Type", "Plans", "Startup Cost", "Total Cost", "Plan Rows", "Actual Total Time",
      "Actual Rows", "Actual Loops", "Relation Name", "Alias", "Index Name", "Join Type", "Strategy", "Parent Relationship");



   /*******************************************************************************
    ** The EXPLAIN command for a statement.
    *******************************************************************************/
   public static String explainSql(SqlDialect dialect, String sql, boolean analyze)
   {
      return switch(dialect)
      {
         case POSTGRES -> analyze ? "EXPLAIN (FORMAT JSON, ANALYZE, BUFFERS) " + sql : "EXPLAIN (FORMAT JSON) " + sql;
         case MYSQL -> analyze ? "EXPLAIN ANALYZE " + sql : "EXPLAIN FORMAT=JSON " + sql;
         case SQLITE -> "EXPLAIN QUERY PLAN " + sql;
         default -> analyze ? "EXPLAIN ANALYZE " + sql : "EXPLAIN " + sql;
      };
   }



   /*******************************************************************************
    ** Whether this dialect can EXPLAIN ANALYZE.
    *******************************************************************************/
   public static boolean supportsAnalyze(SqlDialect dialect)
   {
      return (dialect != SqlDialect.SQLITE);
   }



   /*******************************************************************************
    ** Parse the EXPLAIN result rows into a tree.
    **
    ** @param rows the EXPLAIN output (converted values, one list per row)
    *******************************************************************************/
   public static PlanNode parse(SqlDialect dialect, List<List<Object>> rows, boolean analyze)
   {
      if(rows == null || rows.isEmpty())
      {
         return (new PlanNode("(empty plan)", null));
      }

      try
      {
         return switch(dialect)
         {
            case POSTGRES -> parsePostgres(firstCell(rows));
            case MYSQL -> analyze ? parseText(rows) : parseMySqlJson(firstCell(rows));
            case SQLITE -> parseSqlite(rows);
            default -> parseText(rows);
         };
      }
      catch(RuntimeException e)
      {
         ////////////////////////////////////////////////////////
         // unexpected shape: still show the raw plan as text  //
         ////////////////////////////////////////////////////////
         return (parseText(rows));
      }
   }



   /*******************************************************************************
    ** The raw plan text, for the "raw" toggle.
    *******************************************************************************/
   public static String raw(List<List<Object>> rows)
   {
      StringBuilder sb = new StringBuilder();
      for(List<Object> row : rows == null ? List.<List<Object>>of() : rows)
      {
         if(sb.length() > 0)
         {
            sb.append('\n');
         }
         sb.append(String.join(" | ", row.stream().map(String::valueOf).toList()));
      }
      return (sb.toString());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String firstCell(List<List<Object>> rows)
   {
      StringBuilder sb = new StringBuilder();
      for(List<Object> row : rows)
      {
         sb.append(row.isEmpty() ? "" : String.valueOf(row.get(0)));
      }
      return (sb.toString());
   }



   /*******************************************************************************
    ** Postgres FORMAT JSON: [ { "Plan": {...}, "Planning Time": n, ... } ]
    *******************************************************************************/
   static PlanNode parsePostgres(String json)
   {
      JSONArray  array = new JSONArray(json.trim());
      JSONObject top   = array.getJSONObject(0);
      PlanNode   root  = postgresNode(top.getJSONObject("Plan"));
      for(String key : List.of("Planning Time", "Execution Time"))
      {
         if(top.has(key))
         {
            root.getExtra().put(key, top.get(key));
         }
      }
      return (root);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static PlanNode postgresNode(JSONObject plan)
   {
      String label = plan.optString("Node Type", "?");
      StringBuilder detail = new StringBuilder();
      if(plan.has("Relation Name"))
      {
         detail.append("on ").append(plan.getString("Relation Name"));
         if(plan.has("Alias") && !plan.getString("Alias").equals(plan.getString("Relation Name")))
         {
            detail.append(' ').append(plan.getString("Alias"));
         }
      }
      if(plan.has("Index Name"))
      {
         detail.append(detail.length() > 0 ? " " : "").append("using ").append(plan.getString("Index Name"));
      }
      if(plan.has("Join Type"))
      {
         label = plan.getString("Join Type") + " " + label;
      }

      PlanNode node = new PlanNode(label, detail.length() == 0 ? null : detail.toString());
      node.setStartupCost(optDouble(plan, "Startup Cost"));
      node.setTotalCost(optDouble(plan, "Total Cost"));
      node.setPlanRows(optDouble(plan, "Plan Rows"));
      node.setActualTimeMs(optDouble(plan, "Actual Total Time"));
      node.setActualRows(optDouble(plan, "Actual Rows"));
      node.setLoops(optDouble(plan, "Actual Loops"));

      Iterator<String> keys = plan.keys();
      while(keys.hasNext())
      {
         String key = keys.next();
         if(!POSTGRES_KNOWN_KEYS.contains(key))
         {
            Object value = plan.get(key);
            node.getExtra().put(key, value instanceof JSONArray || value instanceof JSONObject ? value.toString() : value);
         }
      }

      JSONArray children = plan.optJSONArray("Plans");
      for(int i = 0; children != null && i < children.length(); i++)
      {
         node.withChild(postgresNode(children.getJSONObject(i)));
      }
      return (node);
   }



   /*******************************************************************************
    ** MySQL FORMAT=JSON: walk query_block, turning each object that names a
    ** table or an operation into a node.
    *******************************************************************************/
   static PlanNode parseMySqlJson(String json)
   {
      JSONObject top   = new JSONObject(json.trim());
      JSONObject block = top.optJSONObject("query_block");
      PlanNode   root  = new PlanNode("query_block", block == null ? null : "select #" + block.opt("select_id"));
      if(block != null)
      {
         JSONObject costInfo = block.optJSONObject("cost_info");
         if(costInfo != null)
         {
            root.setTotalCost(parseDouble(costInfo.opt("query_cost")));
         }
         mysqlChildren(block, root);
      }
      return (root);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void mysqlChildren(JSONObject object, PlanNode parent)
   {
      Iterator<String> keys = object.keys();
      while(keys.hasNext())
      {
         String key   = keys.next();
         Object value = object.get(key);
         if(value instanceof JSONObject child)
         {
            if("table".equals(key))
            {
               parent.withChild(mysqlTable(child));
            }
            else if(!"cost_info".equals(key))
            {
               PlanNode node = new PlanNode(key, null);
               mysqlChildren(child, node);
               parent.withChild(node);
            }
         }
         else if(value instanceof JSONArray array)
         {
            for(int i = 0; i < array.length(); i++)
            {
               if(array.get(i) instanceof JSONObject element)
               {
                  if(element.has("table"))
                  {
                     parent.withChild(mysqlTable(element.getJSONObject("table")));
                  }
                  else
                  {
                     PlanNode node = new PlanNode(key, null);
                     mysqlChildren(element, node);
                     parent.withChild(node);
                  }
               }
            }
         }
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static PlanNode mysqlTable(JSONObject table)
   {
      PlanNode node = new PlanNode(table.optString("access_type", "table"), table.optString("table_name", null));
      node.setPlanRows(parseDouble(table.opt("rows_examined_per_scan")));
      JSONObject costInfo = table.optJSONObject("cost_info");
      if(costInfo != null)
      {
         node.setTotalCost(parseDouble(costInfo.opt("prefix_cost")));
      }
      for(String key : List.of("key", "possible_keys", "attached_condition", "filtered"))
      {
         if(table.has(key))
         {
            node.getExtra().put(key, table.get(key).toString());
         }
      }
      return (node);
   }



   /*******************************************************************************
    ** SQLite EXPLAIN QUERY PLAN rows: id, parent, notused, detail.
    *******************************************************************************/
   static PlanNode parseSqlite(List<List<Object>> rows)
   {
      PlanNode              root  = new PlanNode("QUERY PLAN", null);
      Map<Object, PlanNode> byId  = new HashMap<>();
      byId.put("0", root);
      for(List<Object> row : rows)
      {
         String   id     = String.valueOf(row.get(0));
         String   parent = String.valueOf(row.get(1));
         String   detail = String.valueOf(row.get(row.size() - 1));
         PlanNode node   = new PlanNode(detail, null);
         byId.put(id, node);
         byId.getOrDefault(parent, root).withChild(node);
      }
      return (root);
   }



   /*******************************************************************************
    ** Text plans: one node per line, nested by leading indentation (MySQL
    ** EXPLAIN ANALYZE uses "-> " lines indented by 4).
    *******************************************************************************/
   static PlanNode parseText(List<List<Object>> rows)
   {
      PlanNode root = new PlanNode("Plan", null);
      java.util.Deque<Map.Entry<Integer, PlanNode>> stack = new java.util.ArrayDeque<>();
      stack.push(Map.entry(-1, root));

      for(String line : raw(rows).split("\n"))
      {
         if(line.isBlank())
         {
            continue;
         }
         int    indent = line.length() - line.stripLeading().length();
         String text   = line.strip();
         if(text.startsWith("-> "))
         {
            text = text.substring(3);
         }

         PlanNode node = new PlanNode(text, null);
         while(stack.size() > 1 && stack.peek().getKey() >= indent)
         {
            stack.pop();
         }
         stack.peek().getValue().withChild(node);
         stack.push(Map.entry(indent, node));
      }
      return (root);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Double optDouble(JSONObject object, String key)
   {
      return (object.has(key) ? parseDouble(object.get(key)) : null);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static Double parseDouble(Object value)
   {
      if(value instanceof Number n)
      {
         return (n.doubleValue());
      }
      if(value == null)
      {
         return (null);
      }
      try
      {
         return (Double.parseDouble(value.toString()));
      }
      catch(NumberFormatException e)
      {
         return (null);
      }
   }

}
