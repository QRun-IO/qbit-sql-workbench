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


import java.util.Locale;


/*******************************************************************************
 ** The SQL dialects the workbench understands. The dialect drives lexing
 ** (comment and quoting rules), read-only enforcement, EXPLAIN syntax, and
 ** whether a DDL dry run can be rolled back.
 **
 ** The UI receives the dialect's name and maps it to its editor and formatter
 ** dialects (postgresql, mysql, sqlite, sql).
 *******************************************************************************/
public enum SqlDialect
{
   POSTGRES("postgresql", true, false, false, true, false),
   MYSQL("mysql", false, true, true, false, true),
   SQLITE("sqlite", true, false, false, false, true),
   H2("h2", false, false, false, false, false),
   GENERIC("sql", false, false, false, false, false);

   private final String  uiName;
   private final boolean transactionalDdl;
   private final boolean hashComments;
   private final boolean backslashEscapes;
   private final boolean dollarQuotes;
   private final boolean backtickIdentifiers;



   /*******************************************************************************
    **
    *******************************************************************************/
   SqlDialect(String uiName, boolean transactionalDdl, boolean hashComments, boolean backslashEscapes, boolean dollarQuotes, boolean backtickIdentifiers)
   {
      this.uiName = uiName;
      this.transactionalDdl = transactionalDdl;
      this.hashComments = hashComments;
      this.backslashEscapes = backslashEscapes;
      this.dollarQuotes = dollarQuotes;
      this.backtickIdentifiers = backtickIdentifiers;
   }



   /*******************************************************************************
    ** Map a QQQ RDBMS vendor string (postgres, aurora-postgres, mysql,
    ** aurora-mysql, sqlite, h2) to a dialect; anything else is GENERIC.
    *******************************************************************************/
   public static SqlDialect fromVendor(String vendor)
   {
      if(vendor == null)
      {
         return (GENERIC);
      }

      String v = vendor.toLowerCase(Locale.ROOT);
      if(v.contains("postgres"))
      {
         return (POSTGRES);
      }
      if(v.contains("mysql") || v.contains("mariadb"))
      {
         return (MYSQL);
      }
      if(v.contains("sqlite"))
      {
         return (SQLITE);
      }
      if(v.contains("h2"))
      {
         return (H2);
      }
      return (GENERIC);
   }



   /*******************************************************************************
    ** Name the UI uses to pick its editor and formatter dialect.
    *******************************************************************************/
   public String getUiName()
   {
      return (uiName);
   }



   /*******************************************************************************
    ** Whether DDL runs inside a transaction (and so can be dry-run and rolled
    ** back). MySQL and H2 commit implicitly on DDL.
    *******************************************************************************/
   public boolean isTransactionalDdl()
   {
      return (transactionalDdl);
   }



   /*******************************************************************************
    ** Whether '#' starts a line comment.
    *******************************************************************************/
   public boolean isHashComments()
   {
      return (hashComments);
   }



   /*******************************************************************************
    ** Whether backslash escapes the next character inside string literals.
    *******************************************************************************/
   public boolean isBackslashEscapes()
   {
      return (backslashEscapes);
   }



   /*******************************************************************************
    ** Whether $tag$...$tag$ dollar-quoted strings exist.
    *******************************************************************************/
   public boolean isDollarQuotes()
   {
      return (dollarQuotes);
   }



   /*******************************************************************************
    ** Whether `identifier` quoting exists.
    *******************************************************************************/
   public boolean isBacktickIdentifiers()
   {
      return (backtickIdentifiers);
   }

}
