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

package com.kingsrook.qbits.sqlworkbench.sql;


import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Unit tests for SqlScanner, SqlClassifier and NamedParameterSql.
 *******************************************************************************/
class SqlClassifierTest
{

   /*******************************************************************************
    **
    *******************************************************************************/
   private static StatementKind kindOf(String sql, SqlDialect dialect)
   {
      List<SqlStatement> statements = SqlClassifier.split(sql, dialect);
      assertThat(statements).hasSize(1);
      return (statements.get(0).kind());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @ParameterizedTest
   @CsvSource(delimiter = '|', quoteCharacter = '~', value = {
      "SELECT * FROM person                                      | READ",
      "select 1                                                  | READ",
      "(SELECT 1) UNION (SELECT 2)                               | READ",
      "WITH x AS (SELECT 1) SELECT * FROM x                      | READ",
      "VALUES (1), (2)                                           | READ",
      "SHOW search_path                                          | READ",
      "EXPLAIN SELECT 1                                          | READ",
      "EXPLAIN (FORMAT JSON) SELECT 1                            | READ",
      "EXPLAIN (ANALYZE false) SELECT 1                          | READ",
      "EXPLAIN ANALYZE SELECT 1                                  | EXPLAIN_ANALYZE",
      "EXPLAIN (ANALYZE, BUFFERS) SELECT 1                       | EXPLAIN_ANALYZE",
      "SELECT 'delete from x' AS s                               | READ",
      "SELECT \"update\" FROM t                                  | READ",
      "SELECT last_update, created FROM t                        | READ",
      "SELECT * FROM t FOR UPDATE                                | WRITE",
      "SELECT * INTO new_t FROM t                                | WRITE",
      "WITH d AS (DELETE FROM t RETURNING *) SELECT * FROM d     | WRITE",
      "SELECT nextval('seq')                                     | WRITE",
      "SELECT nextval_count FROM t                               | READ",
      "INSERT INTO t VALUES (1)                                  | WRITE",
      "update t set a = 1                                        | WRITE",
      "DELETE FROM t                                             | WRITE",
      "CREATE TABLE x (id int)                                   | DDL",
      "DROP TABLE x                                              | DDL",
      "TRUNCATE t                                                | DDL",
      "BEGIN                                                     | FORBIDDEN",
      "COMMIT                                                    | FORBIDDEN",
      "SET search_path = evil                                    | FORBIDDEN",
      "RESET ALL                                                 | FORBIDDEN",
      "DO $$ BEGIN END $$                                        | FORBIDDEN",
      "SELECT 1 -- DELETE                                        | READ",
      "/* DELETE */ SELECT 1                                     | READ",
      "42                                                        | WRITE",
   })
   void testPostgresClassification(String sql, StatementKind expected)
   {
      assertThat(kindOf(sql.replace("\\n", "\n"), SqlDialect.POSTGRES)).isEqualTo(expected);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testOtherDialects()
   {
      assertThat(kindOf("SELECT 1 # DELETE", SqlDialect.MYSQL)).isEqualTo(StatementKind.READ);
      assertThat(kindOf("SELECT 'it\\'s DELETE' AS x", SqlDialect.MYSQL)).isEqualTo(StatementKind.READ);
      assertThat(kindOf("SELECT `delete` FROM t", SqlDialect.MYSQL)).isEqualTo(StatementKind.READ);
      assertThat(kindOf("SELECT \"delete\" AS s", SqlDialect.MYSQL)).isEqualTo(StatementKind.READ);
      assertThat(kindOf("USE other_db", SqlDialect.MYSQL)).isEqualTo(StatementKind.FORBIDDEN);
      assertThat(kindOf("PRAGMA table_info(t)", SqlDialect.SQLITE)).isEqualTo(StatementKind.READ);
      assertThat(kindOf("PRAGMA foreign_keys = OFF", SqlDialect.SQLITE)).isEqualTo(StatementKind.FORBIDDEN);
      assertThat(kindOf("SELECT [delete] FROM t", SqlDialect.SQLITE)).isEqualTo(StatementKind.READ);
      assertThat(kindOf("ATTACH 'x.db' AS x", SqlDialect.SQLITE)).isEqualTo(StatementKind.FORBIDDEN);
      assertThat(kindOf("SHUTDOWN", SqlDialect.H2)).isEqualTo(StatementKind.FORBIDDEN);
      assertThat(kindOf("SCRIPT TO 'x.sql'", SqlDialect.H2)).isEqualTo(StatementKind.FORBIDDEN);
      assertThat(kindOf("SELECT 1", null)).isEqualTo(StatementKind.READ);
      assertThat(kindOf("-- leading comment\nSELECT 1", SqlDialect.POSTGRES)).isEqualTo(StatementKind.READ);
   }



   /*******************************************************************************
    ** MySQL runs /*! ... *\/ (and MariaDB /*M! ... *\/) as SQL and reads
    ** optimizer hints from /*+ ... *\/: on MySQL these fail closed.
    *******************************************************************************/
   @ParameterizedTest
   @CsvSource(delimiter = '|', quoteCharacter = '~', value = {
      "SELECT * FROM t /*! INTO OUTFILE '/tmp/x' */",
      "SELECT * FROM t /*!50000 INTO OUTFILE '/tmp/x' */",
      "SELECT * FROM t /*M! INTO OUTFILE '/tmp/x' */",
      "/*! DROP TABLE t */",
      "SELECT /*+ MAX_EXECUTION_TIME(1) */ * FROM t",
      "SELECT /*+ SET_VAR(sql_mode='') */ 1",
      "SELECT 1 /*!",
   })
   void testMysqlExecutableCommentsAreForbidden(String sql)
   {
      List<SqlStatement> statements = SqlClassifier.split(sql, SqlDialect.MYSQL);
      assertThat(statements).hasSize(1);
      assertThat(statements.get(0).kind()).isEqualTo(StatementKind.FORBIDDEN);
      assertThat(statements.get(0).reason()).contains("executable comments");
   }



   /*******************************************************************************
    ** Plain comments stay comments; the executable forms only matter on
    ** MySQL; inside literals they are text.
    *******************************************************************************/
   @Test
   void testMysqlPlainCommentsAndOtherDialects()
   {
      assertThat(kindOf("SELECT 1 /* INTO OUTFILE */", SqlDialect.MYSQL)).isEqualTo(StatementKind.READ);
      assertThat(kindOf("SELECT '/*! INTO OUTFILE */' AS s", SqlDialect.MYSQL)).isEqualTo(StatementKind.READ);
      assertThat(kindOf("SELECT `/*!x*/` FROM t", SqlDialect.MYSQL)).isEqualTo(StatementKind.READ);
      assertThat(kindOf("SELECT 1 /*! INTO OUTFILE */", SqlDialect.POSTGRES)).isEqualTo(StatementKind.READ);
      assertThat(kindOf("SELECT 1 /*+ hint */", SqlDialect.H2)).isEqualTo(StatementKind.READ);
   }



   /*******************************************************************************
    ** MySQL only treats "--" as a comment when whitespace follows; "1--1" is
    ** arithmetic, so what follows it is still SQL.
    *******************************************************************************/
   @Test
   void testMysqlDashDashNeedsWhitespace()
   {
      assertThat(kindOf("SELECT * FROM t WHERE a=1--1 INTO OUTFILE '/tmp/x'", SqlDialect.MYSQL)).isEqualTo(StatementKind.WRITE);
      assertThat(kindOf("SELECT 1 -- INTO OUTFILE", SqlDialect.MYSQL)).isEqualTo(StatementKind.READ);
      assertThat(kindOf("SELECT 1 --\tINTO", SqlDialect.MYSQL)).isEqualTo(StatementKind.READ);
      assertThat(kindOf("SELECT 1 --", SqlDialect.MYSQL)).isEqualTo(StatementKind.READ);
      assertThat(kindOf("SELECT 1 --INTO x", SqlDialect.POSTGRES)).isEqualTo(StatementKind.READ);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testSplitting()
   {
      List<SqlStatement> statements = SqlClassifier.split("SELECT 1; SELECT ';'; ; -- only a comment\n", SqlDialect.POSTGRES);
      assertThat(statements).hasSize(2);
      assertThat(statements.get(0).text()).isEqualTo("SELECT 1");
      assertThat(statements.get(1).text()).isEqualTo("SELECT ';'");

      List<SqlStatement> dollar = SqlClassifier.split("CREATE FUNCTION f() RETURNS int AS $body$ SELECT 1; $body$ LANGUAGE sql; SELECT 2", SqlDialect.POSTGRES);
      assertThat(dollar).hasSize(2);
      assertThat(dollar.get(0).kind()).isEqualTo(StatementKind.DDL);

      assertThat(SqlClassifier.split("/* nested /* comment */ still */ SELECT 1", SqlDialect.POSTGRES)).hasSize(1).first().extracting(SqlStatement::kind).isEqualTo(StatementKind.READ);
      assertThat(SqlClassifier.split("", SqlDialect.POSTGRES)).isEmpty();
      assertThat(SqlClassifier.split(null, SqlDialect.POSTGRES)).isEmpty();
      assertThat(SqlClassifier.split("SELECT 'unterminated", SqlDialect.POSTGRES)).hasSize(1);
      assertThat(SqlClassifier.split("SELECT $$unterminated", SqlDialect.POSTGRES)).hasSize(1);
      assertThat(SqlClassifier.split("SELECT 1 /* unterminated", SqlDialect.POSTGRES)).hasSize(1);
      assertThat(SqlClassifier.split("SELECT E'a\\'; DELETE' x", SqlDialect.POSTGRES)).hasSize(1);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testNamedParameters()
   {
      SqlStatement statement = SqlClassifier.split("SELECT id::text, ':skip' FROM t WHERE a = :a AND b = :b_2 OR a = :a -- :c", SqlDialect.POSTGRES).get(0);
      assertThat(statement.parameterNames()).containsExactly("a", "b_2", "a");

      NamedParameterSql named = NamedParameterSql.of(statement);
      assertThat(named.jdbcSql()).isEqualTo("SELECT id::text, ':skip' FROM t WHERE a = ? AND b = ? OR a = ?");
      assertThat(named.parameterOrder()).containsExactly("a", "b_2", "a");

      NamedParameterSql none = NamedParameterSql.of(SqlClassifier.split("SELECT 1", SqlDialect.POSTGRES).get(0));
      assertThat(none.jdbcSql()).isEqualTo("SELECT 1");
      assertThat(none.parameterOrder()).isEmpty();

      assertThat(SqlClassifier.split("SELECT $1, 1.5e3, x$y", SqlDialect.POSTGRES).get(0).kind()).isEqualTo(StatementKind.READ);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testDialectMapping()
   {
      assertThat(SqlDialect.fromVendor("postgres")).isEqualTo(SqlDialect.POSTGRES);
      assertThat(SqlDialect.fromVendor("aurora-postgresql")).isEqualTo(SqlDialect.POSTGRES);
      assertThat(SqlDialect.fromVendor("mysql")).isEqualTo(SqlDialect.MYSQL);
      assertThat(SqlDialect.fromVendor("sqlite")).isEqualTo(SqlDialect.SQLITE);
      assertThat(SqlDialect.fromVendor("h2")).isEqualTo(SqlDialect.H2);
      assertThat(SqlDialect.fromVendor("oracle")).isEqualTo(SqlDialect.GENERIC);
      assertThat(SqlDialect.fromVendor(null)).isEqualTo(SqlDialect.GENERIC);
      assertThat(SqlDialect.POSTGRES.getUiName()).isEqualTo("postgresql");
      assertThat(SqlDialect.POSTGRES.isTransactionalDdl()).isTrue();
      assertThat(SqlDialect.MYSQL.isTransactionalDdl()).isFalse();
      assertThat(StatementKind.READ.isReadOnlySafe()).isTrue();
      assertThat(StatementKind.DDL.isMutating()).isTrue();
      assertThat(StatementKind.FORBIDDEN.isMutating()).isFalse();
   }

}
