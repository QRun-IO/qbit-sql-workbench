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

package com.kingsrook.qbits.sqlworkbench.developer;


import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qbits.sqlworkbench.sql.SqlDialect;
import com.kingsrook.qqq.backend.module.rdbms.actions.AbstractRDBMSAction;


/*******************************************************************************
 ** Builds read-only SELECT statements for a QQQ table in its RDBMS backend's
 ** own names (the table's backend table name and each field's backend column
 ** name, as QQQ's RDBMS module resolves them), with identifiers quoted and
 ** the primary-key value written as an escaped literal for the dialect.
 **
 ** The SQL is only shown to the user (and opened, not run, in the
 ** workbench), so it uses literals rather than parameters.
 *******************************************************************************/
public final class RecordSql
{
   public static final int TABLE_ROW_LIMIT = 100;



   /*******************************************************************************
    **
    *******************************************************************************/
   private RecordSql()
   {
   }



   /*******************************************************************************
    ** SELECT the table's columns for one primary-key value.
    *******************************************************************************/
   public static String selectRecord(QTableMetaData table, SqlDialect dialect, Serializable primaryKey)
   {
      QFieldMetaData primaryKeyField = table.getField(table.getPrimaryKeyField());
      return (selectColumns(table, dialect)
         + "\nWHERE " + quoteIdentifier(dialect, AbstractRDBMSAction.getColumnName(primaryKeyField)) + " = " + literal(dialect, primaryKeyField.getType(), primaryKey));
   }



   /*******************************************************************************
    ** SELECT the table's columns, first rows only.
    *******************************************************************************/
   public static String selectTable(QTableMetaData table, SqlDialect dialect)
   {
      String select = selectColumns(table, dialect);
      if(dialect == SqlDialect.GENERIC)
      {
         return (select + "\nFETCH FIRST " + TABLE_ROW_LIMIT + " ROWS ONLY");
      }
      return (select + "\nLIMIT " + TABLE_ROW_LIMIT);
   }



   /*******************************************************************************
    ** SELECT clause listing every field's column (aliased to the QQQ field
    ** name where the names differ) and the FROM clause.
    *******************************************************************************/
   static String selectColumns(QTableMetaData table, SqlDialect dialect)
   {
      List<String> columns = new ArrayList<>();
      for(QFieldMetaData field : table.getFields().values())
      {
         String column = AbstractRDBMSAction.getColumnName(field);
         columns.add(column.equals(field.getName())
            ? quoteIdentifier(dialect, column)
            : quoteIdentifier(dialect, column) + " AS " + quoteIdentifier(dialect, field.getName()));
      }

      return ("SELECT\n   " + String.join(",\n   ", columns)
         + "\nFROM " + quoteIdentifier(dialect, AbstractRDBMSAction.getTableName(table)));
   }



   /*******************************************************************************
    ** Quote an identifier: backticks for MySQL, double quotes otherwise (ANSI;
    ** Postgres, SQLite and H2 all accept them). A quote character inside the
    ** identifier is doubled.
    *******************************************************************************/
   public static String quoteIdentifier(SqlDialect dialect, String identifier)
   {
      String quote = dialect == SqlDialect.MYSQL ? "`" : "\"";
      return (quote + identifier.replace(quote, quote + quote) + quote);
   }



   /*******************************************************************************
    ** A value as a SQL literal: NULL; a plain number for numeric fields whose
    ** value parses as one; otherwise a single-quoted string with quotes
    ** doubled (and, for MySQL, backslashes escaped).
    *******************************************************************************/
   public static String literal(SqlDialect dialect, QFieldType type, Serializable value)
   {
      if(value == null)
      {
         return ("NULL");
      }

      if(type == QFieldType.INTEGER || type == QFieldType.LONG || type == QFieldType.DECIMAL || value instanceof Number)
      {
         try
         {
            return (new BigDecimal(value.toString().trim()).toPlainString());
         }
         catch(NumberFormatException e)
         {
            ///////////////////////////////////////////////////////
            // not a number after all: fall through to a string //
            ///////////////////////////////////////////////////////
         }
      }

      return (stringLiteral(dialect, value.toString()));
   }



   /*******************************************************************************
    ** A single-quoted string literal.
    *******************************************************************************/
   public static String stringLiteral(SqlDialect dialect, String value)
   {
      String escaped = value;
      if(dialect.isBackslashEscapes())
      {
         escaped = escaped.replace("\\", "\\\\");
      }
      return ("'" + escaped.replace("'", "''") + "'");
   }

}
