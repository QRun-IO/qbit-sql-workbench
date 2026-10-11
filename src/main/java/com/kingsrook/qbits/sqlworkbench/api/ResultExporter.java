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

package com.kingsrook.qbits.sqlworkbench.api;


import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qbits.sqlworkbench.engine.CollectedResult;
import com.kingsrook.qbits.sqlworkbench.engine.ColumnInfo;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;


/*******************************************************************************
 ** Writes a cached result as CSV (RFC 4180, UTF-8 with BOM for Excel), JSON
 ** (array of objects keyed by unique column labels) or XLSX (fastexcel,
 ** which qqq-backend-core already ships).
 *******************************************************************************/
public class ResultExporter
{
   private static final int XLSX_MAX_CELL_CHARS = 32_767;



   /*******************************************************************************
    ** Supported formats.
    *******************************************************************************/
   public enum Format
   {
      CSV("text/csv; charset=utf-8", "csv"),
      JSON("application/json", "json"),
      XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");

      private final String contentType;
      private final String extension;



      /*******************************************************************************
       **
       *******************************************************************************/
      Format(String contentType, String extension)
      {
         this.contentType = contentType;
         this.extension = extension;
      }



      /*******************************************************************************
       ** Parse a format name (case-insensitive).
       *******************************************************************************/
      public static Format of(String name) throws QBadRequestException
      {
         try
         {
            return (Format.valueOf(String.valueOf(name).toUpperCase(Locale.ROOT)));
         }
         catch(IllegalArgumentException e)
         {
            throw (new QBadRequestException("format must be csv, json or xlsx."));
         }
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      public String getContentType()
      {
         return (contentType);
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      public String getExtension()
      {
         return (extension);
      }
   }



   /*******************************************************************************
    ** Write the result in a format.
    *******************************************************************************/
   public static void write(CollectedResult result, Format format, OutputStream out) throws IOException
   {
      switch(format)
      {
         case CSV -> writeCsv(result, out);
         case JSON -> writeJson(result, out);
         case XLSX -> writeXlsx(result, out);
         default -> throw (new IllegalStateException("Unexpected format: " + format));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static void writeCsv(CollectedResult result, OutputStream out) throws IOException
   {
      Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
      writer.write('﻿');
      List<String> header = result.columns().stream().map(ColumnInfo::label).toList();
      writer.write(csvLine(new ArrayList<>(header)));
      for(List<Object> row : result.rows())
      {
         writer.write(csvLine(row));
      }
      writer.flush();
   }



   /*******************************************************************************
    ** CSV formula injection: a spreadsheet runs a text cell that starts with
    ** =, +, -, @, tab or CR as a formula. Prefix those with a quote so they
    ** open as text. (Numbers are written as numbers and are left alone.)
    *******************************************************************************/
   static String neutralizeFormula(String text)
   {
      if(!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0)
      {
         return ("'" + text);
      }
      return (text);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static String csvLine(List<?> values)
   {
      StringBuilder sb = new StringBuilder();
      for(int i = 0; i < values.size(); i++)
      {
         if(i > 0)
         {
            sb.append(',');
         }
         Object value = values.get(i);
         if(value == null)
         {
            continue;
         }
         String text = value instanceof String s ? neutralizeFormula(s) : value instanceof Number || value instanceof Boolean ? value.toString() : JsonUtils.toJson(value);
         if(text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r"))
         {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
         }
         sb.append(text);
      }
      return (sb.append("\r\n").toString());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static void writeJson(CollectedResult result, OutputStream out) throws IOException
   {
      List<String>              keys = uniqueLabels(result.columns());
      List<Map<String, Object>> rows = new ArrayList<>();
      for(List<Object> row : result.rows())
      {
         Map<String, Object> object = new LinkedHashMap<>();
         for(int i = 0; i < keys.size(); i++)
         {
            object.put(keys.get(i), row.get(i));
         }
         rows.add(object);
      }
      out.write(JsonUtils.toJson(rows).getBytes(StandardCharsets.UTF_8));
      out.flush();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static void writeXlsx(CollectedResult result, OutputStream out) throws IOException
   {
      Workbook  workbook  = new Workbook(out, "QQQ SQL Workbench", "1.0");
      Worksheet worksheet = workbook.newWorksheet("Result");
      List<ColumnInfo> columns = result.columns();
      for(int c = 0; c < columns.size(); c++)
      {
         worksheet.value(0, c, columns.get(c).label());
         worksheet.style(0, c).bold().set();
      }

      for(int r = 0; r < result.rows().size(); r++)
      {
         List<Object> row = result.rows().get(r);
         for(int c = 0; c < row.size(); c++)
         {
            Object value = row.get(c);
            if(value == null)
            {
               continue;
            }
            if(value instanceof Number n && "number".equals(columns.get(c).kind()))
            {
               worksheet.value(r + 1, c, n instanceof BigDecimal bd ? bd : new BigDecimal(n.toString()));
            }
            else if(value instanceof Boolean b)
            {
               worksheet.value(r + 1, c, b);
            }
            else
            {
               String text = value instanceof String s ? s : JsonUtils.toJson(value);
               worksheet.value(r + 1, c, text.length() > XLSX_MAX_CELL_CHARS ? text.substring(0, XLSX_MAX_CELL_CHARS) : text);
            }
         }
      }
      workbook.finish();
   }



   /*******************************************************************************
    ** Column labels made unique (a, a_2, a_3...), for JSON keys.
    *******************************************************************************/
   static List<String> uniqueLabels(List<ColumnInfo> columns)
   {
      Set<String>  seen   = new HashSet<>();
      List<String> labels = new ArrayList<>();
      for(ColumnInfo column : columns)
      {
         String label = column.label() == null ? column.name() : column.label();
         String key   = label;
         for(int n = 2; !seen.add(key); n++)
         {
            key = label + "_" + n;
         }
         labels.add(key);
      }
      return (labels);
   }

}
