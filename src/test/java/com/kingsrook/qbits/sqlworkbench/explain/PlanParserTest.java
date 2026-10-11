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


import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.sqlworkbench.sql.SqlDialect;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Plan parsing per dialect, from fixture output.
 *******************************************************************************/
class PlanParserTest
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testExplainSql()
   {
      assertThat(PlanParser.explainSql(SqlDialect.POSTGRES, "SELECT 1", false)).isEqualTo("EXPLAIN (FORMAT JSON) SELECT 1");
      assertThat(PlanParser.explainSql(SqlDialect.POSTGRES, "SELECT 1", true)).isEqualTo("EXPLAIN (FORMAT JSON, ANALYZE, BUFFERS) SELECT 1");
      assertThat(PlanParser.explainSql(SqlDialect.MYSQL, "SELECT 1", false)).isEqualTo("EXPLAIN FORMAT=JSON SELECT 1");
      assertThat(PlanParser.explainSql(SqlDialect.MYSQL, "SELECT 1", true)).isEqualTo("EXPLAIN ANALYZE SELECT 1");
      assertThat(PlanParser.explainSql(SqlDialect.SQLITE, "SELECT 1", true)).isEqualTo("EXPLAIN QUERY PLAN SELECT 1");
      assertThat(PlanParser.explainSql(SqlDialect.H2, "SELECT 1", true)).isEqualTo("EXPLAIN ANALYZE SELECT 1");
      assertThat(PlanParser.supportsAnalyze(SqlDialect.SQLITE)).isFalse();
      assertThat(PlanParser.supportsAnalyze(SqlDialect.POSTGRES)).isTrue();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void testPostgres()
   {
      String json = """
         [{"Plan": {"Node Type": "Hash Join", "Join Type": "Inner", "Startup Cost": 1.5, "Total Cost": 10.25, "Plan Rows": 100, "Plan Width": 8,
                    "Actual Total Time": 0.5, "Actual Rows": 90, "Actual Loops": 1, "Hash Cond": "(a.id = b.a_id)",
                    "Plans": [{"Node Type": "Seq Scan", "Relation Name": "a", "Alias": "a", "Total Cost": 4.0, "Plan Rows": 100},
                              {"Node Type": "Index Scan", "Relation Name": "b", "Alias": "bb", "Index Name": "b_pk", "Total Cost": 2.0, "Plan Rows": 9}]},
           "Planning Time": 0.1, "Execution Time": 0.7}]""";
      PlanNode root = PlanParser.parse(SqlDialect.POSTGRES, List.of(List.of(json)), true);
      assertThat(root.getLabel()).isEqualTo("Inner Hash Join");
      assertThat(root.getTotalCost()).isEqualTo(10.25);
      assertThat(root.getActualRows()).isEqualTo(90.0);
      assertThat(root.getLoops()).isEqualTo(1.0);
      assertThat(root.getExtra()).containsKeys("Hash Cond", "Planning Time", "Execution Time", "Plan Width");
      assertThat(root.getChildren()).hasSize(2);
      assertThat(root.getChildren().get(0).getDetail()).isEqualTo("on a");
      assertThat(root.getChildren().get(1).getDetail()).isEqualTo("on b bb using b_pk");

      Map<String, Object> map = root.toMap();
      assertThat((List<Map<String, Object>>) map.get("children")).hasSize(2);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testMySql()
   {
      String json = """
         {"query_block": {"select_id": 1, "cost_info": {"query_cost": "12.5"},
            "nested_loop": [{"table": {"table_name": "a", "access_type": "ALL", "rows_examined_per_scan": 10, "cost_info": {"prefix_cost": "2.0"}, "attached_condition": "x"}},
                            {"table": {"table_name": "b", "access_type": "ref", "key": "PRIMARY"}}],
            "ordering_operation": {"using_filesort": true, "table": {"table_name": "c", "access_type": "index"}}}}""";
      PlanNode root = PlanParser.parse(SqlDialect.MYSQL, List.of(List.of(json)), false);
      assertThat(root.getTotalCost()).isEqualTo(12.5);
      assertThat(root.getChildren()).extracting(PlanNode::getLabel).contains("ALL", "ref", "ordering_operation");
      assertThat(root.getChildren().get(0).getPlanRows()).isEqualTo(10.0);
      assertThat(root.getChildren().get(0).getExtra()).containsKey("attached_condition");

      PlanNode analyze = PlanParser.parse(SqlDialect.MYSQL, List.of(List.of("-> Limit: 10 row(s)\n    -> Table scan on t  (cost=1.0 rows=5)\n")), true);
      assertThat(analyze.getChildren()).hasSize(1);
      assertThat(analyze.getChildren().get(0).getLabel()).isEqualTo("Limit: 10 row(s)");
      assertThat(analyze.getChildren().get(0).getChildren().get(0).getLabel()).startsWith("Table scan on t");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testSqliteTextAndFallbacks()
   {
      PlanNode sqlite = PlanParser.parse(SqlDialect.SQLITE, List.of(List.of(2, 0, 0, "SCAN a"), List.of(5, 2, 0, "SEARCH b USING INDEX"), List.of(9, 0, 0, "USE TEMP B-TREE")), false);
      assertThat(sqlite.getChildren()).extracting(PlanNode::getLabel).containsExactly("SCAN a", "USE TEMP B-TREE");
      assertThat(sqlite.getChildren().get(0).getChildren().get(0).getLabel()).isEqualTo("SEARCH b USING INDEX");

      PlanNode h2 = PlanParser.parse(SqlDialect.H2, List.of(List.of("SELECT\n    1\nFROM t")), false);
      assertThat(h2.getChildren()).hasSize(2);

      assertThat(PlanParser.parse(SqlDialect.POSTGRES, List.of(), false).getLabel()).isEqualTo("(empty plan)");
      assertThat(PlanParser.parse(SqlDialect.POSTGRES, List.of(List.of("not json")), false).getChildren()).hasSize(1);
      assertThat(PlanParser.raw(List.of(List.of(1, "a"), List.of(2, "b")))).isEqualTo("1 | a\n2 | b");
      assertThat(PlanParser.parseDouble("x")).isNull();
      assertThat(PlanParser.parseDouble(null)).isNull();
      assertThat(PlanParser.parseDouble("2.5")).isEqualTo(2.5);

      PlanNode node = new PlanNode();
      node.setLabel("l");
      node.setDetail("d");
      node.setStartupCost(1.0);
      node.setTotalCost(2.0);
      node.setPlanRows(3.0);
      node.setActualTimeMs(4.0);
      node.setActualRows(5.0);
      node.setLoops(6.0);
      assertThat(node.toMap()).containsEntry("label", "l").containsEntry("detail", "d").containsEntry("startupCost", 1.0).containsEntry("actualTimeMs", 4.0);
   }

}
