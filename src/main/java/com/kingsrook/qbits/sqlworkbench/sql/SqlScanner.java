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


/*******************************************************************************
 ** A small, dialect-aware SQL scanner. It is not a parser: it only knows
 ** enough to find comments, string literals, quoted identifiers, dollar
 ** quotes, statement separators and :named parameters, so that
 ** classification and parameter binding never look inside a literal or a
 ** comment.
 **
 ** Unterminated literals and comments run to the end of the text (the
 ** database reports the syntax error); the scanner never throws.
 **
 ** MySQL executes some "comments": /*!... and /*M!... comments run their
 ** contents as SQL, and /*+... carries optimizer hints. On the MySQL
 ** dialect these become EXECUTABLE_COMMENT tokens (which the classifier
 ** forbids) instead of being skipped. MySQL also needs whitespace after
 ** "--" for a line comment ("1--1" is arithmetic), so on MySQL "--"
 ** followed by anything else is scanned as symbols.
 *******************************************************************************/
public class SqlScanner
{
   private final String     sql;
   private final SqlDialect dialect;
   private final int        length;

   private int pos = 0;



   /*******************************************************************************
    **
    *******************************************************************************/
   private SqlScanner(String sql, SqlDialect dialect)
   {
      this.sql = sql == null ? "" : sql;
      this.dialect = dialect == null ? SqlDialect.GENERIC : dialect;
      this.length = this.sql.length();
   }



   /*******************************************************************************
    ** Scan a SQL text into significant tokens.
    *******************************************************************************/
   public static List<SqlToken> scan(String sql, SqlDialect dialect)
   {
      return (new SqlScanner(sql, dialect).scanAll());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private List<SqlToken> scanAll()
   {
      List<SqlToken> tokens = new ArrayList<>();
      while(pos < length)
      {
         char c = sql.charAt(pos);

         if(Character.isWhitespace(c))
         {
            pos++;
         }
         else if(c == '-' && peek(1) == '-' && isDashCommentStart())
         {
            skipLineComment();
         }
         else if(c == '#' && dialect.isHashComments())
         {
            skipLineComment();
         }
         else if(c == '/' && peek(1) == '*' && isExecutableCommentStart())
         {
            int start = pos;
            skipBlockComment();
            tokens.add(new SqlToken(SqlToken.Type.EXECUTABLE_COMMENT, sql.substring(start, pos), start, pos));
         }
         else if(c == '/' && peek(1) == '*')
         {
            skipBlockComment();
         }
         else if(c == '\'')
         {
            tokens.add(readQuoted(SqlToken.Type.STRING, '\'', dialect.isBackslashEscapes(), pos));
         }
         else if((c == 'E' || c == 'e') && peek(1) == '\'' && dialect == SqlDialect.POSTGRES)
         {
            int start = pos;
            pos++;
            tokens.add(readQuoted(SqlToken.Type.STRING, '\'', true, start));
         }
         else if(c == '"')
         {
            tokens.add(readQuoted(dialect == SqlDialect.MYSQL ? SqlToken.Type.STRING : SqlToken.Type.QUOTED_IDENTIFIER, '"', dialect.isBackslashEscapes() && dialect == SqlDialect.MYSQL, pos));
         }
         else if(c == '`' && dialect.isBacktickIdentifiers())
         {
            tokens.add(readQuoted(SqlToken.Type.QUOTED_IDENTIFIER, '`', false, pos));
         }
         else if(c == '[' && dialect == SqlDialect.SQLITE)
         {
            int start = pos;
            int close = sql.indexOf(']', pos + 1);
            pos = close < 0 ? length : close + 1;
            tokens.add(new SqlToken(SqlToken.Type.QUOTED_IDENTIFIER, sql.substring(start, pos), start, pos));
         }
         else if(c == '$' && dialect.isDollarQuotes() && isDollarQuoteStart())
         {
            tokens.add(readDollarQuoted());
         }
         else if(c == ';')
         {
            tokens.add(new SqlToken(SqlToken.Type.SEMICOLON, ";", pos, pos + 1));
            pos++;
         }
         else if(c == '(')
         {
            tokens.add(new SqlToken(SqlToken.Type.OPEN_PAREN, "(", pos, pos + 1));
            pos++;
         }
         else if(c == ')')
         {
            tokens.add(new SqlToken(SqlToken.Type.CLOSE_PAREN, ")", pos, pos + 1));
            pos++;
         }
         else if(c == ':' && peek(1) == ':')
         {
            /////////////////////////////////////////
            // postgres cast operator, not a param //
            /////////////////////////////////////////
            tokens.add(new SqlToken(SqlToken.Type.SYMBOL, "::", pos, pos + 2));
            pos += 2;
         }
         else if(c == ':' && isIdentifierStart(peek(1)) && !isPreviousCharColon())
         {
            int start = pos;
            pos++;
            int nameStart = pos;
            while(pos < length && isIdentifierPart(sql.charAt(pos)))
            {
               pos++;
            }
            tokens.add(new SqlToken(SqlToken.Type.PARAMETER, sql.substring(nameStart, pos), start, pos));
         }
         else if(isIdentifierStart(c))
         {
            int start = pos;
            while(pos < length && (isIdentifierPart(sql.charAt(pos)) || sql.charAt(pos) == '$'))
            {
               pos++;
            }
            tokens.add(new SqlToken(SqlToken.Type.WORD, sql.substring(start, pos), start, pos));
         }
         else if(Character.isDigit(c))
         {
            int start = pos;
            while(pos < length && (Character.isLetterOrDigit(sql.charAt(pos)) || sql.charAt(pos) == '.'))
            {
               pos++;
            }
            tokens.add(new SqlToken(SqlToken.Type.NUMBER, sql.substring(start, pos), start, pos));
         }
         else
         {
            tokens.add(new SqlToken(SqlToken.Type.SYMBOL, String.valueOf(c), pos, pos + 1));
            pos++;
         }
      }
      return (tokens);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private char peek(int offset)
   {
      int i = pos + offset;
      return (i < length ? sql.charAt(i) : '\0');
   }



   /*******************************************************************************
    ** At "--": a line comment everywhere but MySQL, where the dashes must be
    ** followed by whitespace, a control character, or the end of the text.
    *******************************************************************************/
   private boolean isDashCommentStart()
   {
      if(dialect != SqlDialect.MYSQL)
      {
         return (true);
      }
      char next = peek(2);
      return (next == '\0' || Character.isWhitespace(next) || Character.isISOControl(next));
   }



   /*******************************************************************************
    ** At "/*" on MySQL: /*!, /*M! or /*+, which the server executes.
    *******************************************************************************/
   private boolean isExecutableCommentStart()
   {
      if(dialect != SqlDialect.MYSQL)
      {
         return (false);
      }
      char third = peek(2);
      return (third == '!' || third == '+' || (third == 'M' && peek(3) == '!'));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private boolean isPreviousCharColon()
   {
      return (pos > 0 && sql.charAt(pos - 1) == ':');
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static boolean isIdentifierStart(char c)
   {
      return (Character.isLetter(c) || c == '_');
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static boolean isIdentifierPart(char c)
   {
      return (Character.isLetterOrDigit(c) || c == '_');
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void skipLineComment()
   {
      while(pos < length && sql.charAt(pos) != '\n')
      {
         pos++;
      }
   }



   /*******************************************************************************
    ** Block comments; postgres nests them, the others do not.
    *******************************************************************************/
   private void skipBlockComment()
   {
      int depth = 0;
      while(pos < length)
      {
         if(sql.charAt(pos) == '/' && peek(1) == '*')
         {
            if(depth == 0 || dialect == SqlDialect.POSTGRES)
            {
               depth++;
            }
            pos += 2;
         }
         else if(sql.charAt(pos) == '*' && peek(1) == '/')
         {
            depth--;
            pos += 2;
            if(depth == 0)
            {
               return;
            }
         }
         else
         {
            pos++;
         }
      }
   }



   /*******************************************************************************
    ** Read a quoted run starting at the quote character at pos; a doubled
    ** quote is an escaped quote.
    *******************************************************************************/
   private SqlToken readQuoted(SqlToken.Type type, char quote, boolean backslashEscapes, int tokenStart)
   {
      pos++;
      while(pos < length)
      {
         char c = sql.charAt(pos);
         if(backslashEscapes && c == '\\')
         {
            pos += 2;
         }
         else if(c == quote)
         {
            if(peek(1) == quote)
            {
               pos += 2;
            }
            else
            {
               pos++;
               break;
            }
         }
         else
         {
            pos++;
         }
      }
      pos = Math.min(pos, length);
      return (new SqlToken(type, sql.substring(tokenStart, pos), tokenStart, pos));
   }



   /*******************************************************************************
    ** $$ or $tag$ where tag is an identifier (and not a $1 positional param).
    *******************************************************************************/
   private boolean isDollarQuoteStart()
   {
      if(pos > 0 && isIdentifierPart(sql.charAt(pos - 1)))
      {
         return (false);
      }
      int i = pos + 1;
      if(i < length && sql.charAt(i) == '$')
      {
         return (true);
      }
      if(i >= length || !isIdentifierStart(sql.charAt(i)))
      {
         return (false);
      }
      while(i < length && isIdentifierPart(sql.charAt(i)))
      {
         i++;
      }
      return (i < length && sql.charAt(i) == '$');
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private SqlToken readDollarQuoted()
   {
      int start    = pos;
      int tagClose = sql.indexOf('$', pos + 1);
      String tag   = sql.substring(pos, tagClose + 1);
      int close    = sql.indexOf(tag, tagClose + 1);
      pos = close < 0 ? length : close + tag.length();
      return (new SqlToken(SqlToken.Type.STRING, sql.substring(start, pos), start, pos));
   }

}
