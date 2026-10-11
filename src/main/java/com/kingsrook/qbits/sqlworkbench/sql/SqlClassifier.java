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


import java.util.ArrayList;
import java.util.List;
import java.util.Set;


/*******************************************************************************
 ** Splits a SQL text into statements and classifies each one.
 **
 ** Classification is defence in depth, not the security boundary: read-only
 ** sessions also run inside a database READ ONLY transaction (and, when
 ** configured, with read-only credentials). It is deliberately conservative:
 ** anything it does not recognise as a read is a write, and a "read" that
 ** mentions a mutating keyword anywhere (data-modifying CTEs, SELECT ... INTO,
 ** SELECT ... FOR UPDATE) is a write.
 *******************************************************************************/
public class SqlClassifier
{
   private static final Set<String> READ_LEADERS = Set.of("SELECT", "WITH", "VALUES", "TABLE", "SHOW", "DESCRIBE", "DESC", "EXPLAIN", "PRAGMA");

   private static final Set<String> MUTATING_WORDS = Set.of("INSERT", "UPDATE", "DELETE", "MERGE", "UPSERT", "INTO", "TRUNCATE", "CREATE", "DROP", "ALTER",
      "GRANT", "REVOKE", "COPY", "CALL", "LOCK", "NEXTVAL", "SETVAL");

   private static final Set<String> DDL_LEADERS = Set.of("CREATE", "ALTER", "DROP", "TRUNCATE", "RENAME", "COMMENT", "GRANT", "REVOKE", "REINDEX", "CLUSTER",
      "VACUUM", "ANALYZE", "OPTIMIZE", "REFRESH", "SECURITY");

   private static final Set<String> FORBIDDEN_LEADERS = Set.of("BEGIN", "START", "COMMIT", "END", "ROLLBACK", "ABORT", "SAVEPOINT", "RELEASE", "SET", "RESET",
      "DISCARD", "LISTEN", "UNLISTEN", "NOTIFY", "PREPARE", "EXECUTE", "DEALLOCATE", "USE", "ATTACH", "DETACH", "LOAD", "SHUTDOWN", "RUNSCRIPT", "SCRIPT",
      "BACKUP", "CONNECT", "CHECKPOINT", "KILL", "FLUSH", "INSTALL", "UNINSTALL", "HANDLER", "IMPORT", "DO");



   /*******************************************************************************
    ** Split and classify. Statements made only of comments are dropped.
    *******************************************************************************/
   public static List<SqlStatement> split(String sql, SqlDialect dialect)
   {
      String          text       = sql == null ? "" : sql;
      List<SqlToken>  tokens     = SqlScanner.scan(text, dialect);
      List<SqlStatement> result  = new ArrayList<>();
      List<SqlToken>  current    = new ArrayList<>();

      for(SqlToken token : tokens)
      {
         if(token.type() == SqlToken.Type.SEMICOLON)
         {
            addStatement(text, current, result);
            current = new ArrayList<>();
         }
         else
         {
            current.add(token);
         }
      }
      addStatement(text, current, result);
      return (result);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void addStatement(String text, List<SqlToken> tokens, List<SqlStatement> result)
   {
      if(tokens.isEmpty())
      {
         return;
      }

      String statementText = text.substring(tokens.get(0).start(), tokens.get(tokens.size() - 1).end());
      result.add(classify(statementText, List.copyOf(tokens)));
   }



   /*******************************************************************************
    ** Classify one statement's tokens.
    *******************************************************************************/
   static SqlStatement classify(String text, List<SqlToken> tokens)
   {
      ////////////////////////////////////////////////////////
      // skip leading parens, as in "(SELECT ...) UNION ..." //
      ////////////////////////////////////////////////////////
      int i = 0;
      while(i < tokens.size() && tokens.get(i).type() == SqlToken.Type.OPEN_PAREN)
      {
         i++;
      }
      if(i >= tokens.size() || tokens.get(i).type() != SqlToken.Type.WORD)
      {
         return (new SqlStatement(text, tokens, StatementKind.WRITE, null, "does not start with a keyword"));
      }

      String leader = tokens.get(i).keyword();

      if(FORBIDDEN_LEADERS.contains(leader))
      {
         return (new SqlStatement(text, tokens, StatementKind.FORBIDDEN, leader, leader + " controls the transaction or session, which the workbench manages"));
      }

      if(DDL_LEADERS.contains(leader))
      {
         return (new SqlStatement(text, tokens, StatementKind.DDL, leader, leader + " changes the schema"));
      }

      if(!READ_LEADERS.contains(leader))
      {
         return (new SqlStatement(text, tokens, StatementKind.WRITE, leader, leader + " is not a read-only statement"));
      }

      if("PRAGMA".equals(leader))
      {
         boolean assigns = tokens.stream().anyMatch(t -> t.type() == SqlToken.Type.SYMBOL && "=".equals(t.text()));
         return (assigns
            ? new SqlStatement(text, tokens, StatementKind.FORBIDDEN, leader, "PRAGMA assignments change connection state")
            : new SqlStatement(text, tokens, StatementKind.READ, leader, "read-only PRAGMA"));
      }

      if("EXPLAIN".equals(leader) && isExplainAnalyze(tokens, i + 1))
      {
         return (new SqlStatement(text, tokens, StatementKind.EXPLAIN_ANALYZE, leader, "EXPLAIN ANALYZE executes the statement"));
      }

      for(int j = i + 1; j < tokens.size(); j++)
      {
         String word = tokens.get(j).keyword();
         if(word != null && MUTATING_WORDS.contains(word))
         {
            ///////////////////////////////////////////////////////////////////
            // "IS DISTINCT FROM"-style false positives are not a concern:   //
            // none of these words is an operator. nextval/setval only count //
            // when called, i.e. followed by '('.                            //
            ///////////////////////////////////////////////////////////////////
            if(("NEXTVAL".equals(word) || "SETVAL".equals(word)) && !(j + 1 < tokens.size() && tokens.get(j + 1).type() == SqlToken.Type.OPEN_PAREN))
            {
               continue;
            }
            StatementKind kind = Set.of("CREATE", "DROP", "ALTER", "TRUNCATE", "GRANT", "REVOKE").contains(word) ? StatementKind.DDL : StatementKind.WRITE;
            return (new SqlStatement(text, tokens, kind, leader, leader + " statement contains " + word));
         }
      }

      return (new SqlStatement(text, tokens, StatementKind.READ, leader, "read-only " + leader));
   }



   /*******************************************************************************
    ** EXPLAIN ANALYZE ..., EXPLAIN (ANALYZE[, ...]) ..., EXPLAIN (... ANALYZE
    ** true ...) - but not EXPLAIN (ANALYZE false) or EXPLAIN QUERY PLAN.
    *******************************************************************************/
   private static boolean isExplainAnalyze(List<SqlToken> tokens, int start)
   {
      if(start >= tokens.size())
      {
         return (false);
      }

      SqlToken next = tokens.get(start);
      if(next.isKeyword("ANALYZE") || next.isKeyword("ANALYSE"))
      {
         return (true);
      }

      if(next.type() == SqlToken.Type.OPEN_PAREN)
      {
         for(int j = start + 1; j < tokens.size() && tokens.get(j).type() != SqlToken.Type.CLOSE_PAREN; j++)
         {
            if(tokens.get(j).isKeyword("ANALYZE") || tokens.get(j).isKeyword("ANALYSE"))
            {
               SqlToken value = j + 1 < tokens.size() ? tokens.get(j + 1) : null;
               boolean off = value != null && (value.isKeyword("FALSE") || value.isKeyword("OFF") || "0".equals(value.text()));
               return (!off);
            }
         }
      }
      return (false);
   }

}
