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

package com.kingsrook.qbits.sqlworkbench.engine;


import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


/*******************************************************************************
 ** Reads a ResultSet into JSON-safe values, stopping at the row cap or the
 ** byte budget.
 **
 ** Conversions: null stays null; integers and decimals within +/-2^53 are
 ** numbers, wider ones are strings (no precision loss in JavaScript);
 ** dates/times are ISO-8601 strings; json/jsonb are strings (kind json);
 ** binary is {"$binary": true, "length": n, "hexPreview": "..."}; arrays are
 ** lists; anything else is its toString, capped at maxCellChars.
 *******************************************************************************/
public class ResultCollector
{
   private static final BigDecimal MAX_SAFE = BigDecimal.valueOf(9007199254740991L);
   private static final int        HEX_PREVIEW_BYTES = 32;

   private final int  maxRows;
   private final long maxBytes;
   private final int  maxCellChars;



   /*******************************************************************************
    **
    *******************************************************************************/
   public ResultCollector(int maxRows, long maxBytes, int maxCellChars)
   {
      this.maxRows = maxRows;
      this.maxBytes = maxBytes;
      this.maxCellChars = maxCellChars;
   }



   /*******************************************************************************
    ** Read the columns of a result set.
    *******************************************************************************/
   public static List<ColumnInfo> readColumns(ResultSetMetaData metaData) throws SQLException
   {
      List<ColumnInfo> columns = new ArrayList<>();
      for(int i = 1; i <= metaData.getColumnCount(); i++)
      {
         String typeName = metaData.getColumnTypeName(i);
         int    jdbcType = metaData.getColumnType(i);
         columns.add(new ColumnInfo(metaData.getColumnName(i), metaData.getColumnLabel(i), typeName, jdbcType, kindOf(jdbcType, typeName)));
      }
      return (columns);
   }



   /*******************************************************************************
    ** Collect rows until the result ends, maxRows rows are read (then one more
    ** row is probed to detect truncation), or the byte budget runs out.
    *******************************************************************************/
   public CollectedResult collect(ResultSet resultSet) throws SQLException
   {
      List<ColumnInfo>   columns = readColumns(resultSet.getMetaData());
      List<List<Object>> rows    = new ArrayList<>();
      long               bytes   = 0;

      while(resultSet.next())
      {
         if(rows.size() >= maxRows)
         {
            return (new CollectedResult(columns, rows, true, "ROWS", bytes));
         }

         List<Object> row = new ArrayList<>(columns.size());
         for(int i = 0; i < columns.size(); i++)
         {
            Object value = readValue(resultSet, i + 1, columns.get(i));
            bytes += estimateBytes(value);
            row.add(value);
         }

         if(bytes > maxBytes)
         {
            return (new CollectedResult(columns, rows, true, "BYTES", bytes));
         }
         rows.add(row);
      }
      return (new CollectedResult(columns, rows, false, null, bytes));
   }



   /*******************************************************************************
    ** Map a JDBC type to a UI rendering kind.
    *******************************************************************************/
   static String kindOf(int jdbcType, String typeName)
   {
      String type = typeName == null ? "" : typeName.toLowerCase(Locale.ROOT);
      if(type.equals("json") || type.equals("jsonb"))
      {
         return ("json");
      }

      return switch(jdbcType)
      {
         case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.FLOAT, Types.REAL, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL -> "number";
         case Types.BIT, Types.BOOLEAN -> type.startsWith("bit") && !type.equals("bit") ? "string" : "boolean";
         case Types.DATE -> "date";
         case Types.TIME, Types.TIME_WITH_TIMEZONE -> "time";
         case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "datetime";
         case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> "binary";
         case Types.ARRAY -> "array";
         case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB -> "string";
         default -> "other";
      };
   }



   /*******************************************************************************
    ** Read and convert one value.
    *******************************************************************************/
   Object readValue(ResultSet resultSet, int index, ColumnInfo column) throws SQLException
   {
      ////////////////////////////////////////////////////////////////////
      // pgjdbc reports timestamptz as TIMESTAMP; read it with its offset //
      // rather than shifting it into the JVM's zone                      //
      ////////////////////////////////////////////////////////////////////
      int jdbcType = column.jdbcType() == Types.TIMESTAMP && isZoned(column.typeName()) ? Types.TIMESTAMP_WITH_TIMEZONE : column.jdbcType();
      switch(jdbcType)
      {
         case Types.DATE ->
         {
            Object raw = resultSet.getObject(index);
            if(raw == null)
            {
               return (null);
            }
            return (raw instanceof Date date ? date.toLocalDate().toString() : convert(raw));
         }
         case Types.TIME ->
         {
            Object raw = resultSet.getObject(index);
            if(raw == null)
            {
               return (null);
            }
            return (raw instanceof Time time ? time.toLocalTime().toString() : convert(raw));
         }
         case Types.TIMESTAMP ->
         {
            Object raw = resultSet.getObject(index);
            if(raw == null)
            {
               return (null);
            }
            return (raw instanceof Timestamp timestamp ? timestamp.toLocalDateTime().toString() : convert(raw));
         }
         case Types.TIMESTAMP_WITH_TIMEZONE ->
         {
            try
            {
               OffsetDateTime value = resultSet.getObject(index, OffsetDateTime.class);
               return (value == null ? null : value.toString());
            }
            catch(SQLException | RuntimeException e)
            {
               return (convert(resultSet.getObject(index)));
            }
         }
         default ->
         {
            if("json".equals(column.kind()))
            {
               return (capString(resultSet.getString(index)));
            }
            return (convert(resultSet.getObject(index)));
         }
      }
   }



   /*******************************************************************************
    ** Whether a type name is a zoned timestamp (timestamptz, timestamp with
    ** time zone).
    *******************************************************************************/
   static boolean isZoned(String typeName)
   {
      String type = typeName == null ? "" : typeName.toLowerCase(Locale.ROOT);
      return (type.equals("timestamptz") || type.contains("with time zone"));
   }



   /*******************************************************************************
    ** Convert a driver value to a JSON-safe value.
    *******************************************************************************/
   Object convert(Object value) throws SQLException
   {
      if(value == null)
      {
         return (null);
      }
      if(value instanceof Boolean || value instanceof Integer || value instanceof Short || value instanceof Byte)
      {
         return (value);
      }
      if(value instanceof Long l)
      {
         return (Math.abs(l) <= MAX_SAFE.longValue() ? l : l.toString());
      }
      if(value instanceof BigInteger bi)
      {
         return (bi.abs().compareTo(MAX_SAFE.toBigInteger()) <= 0 ? bi.longValue() : bi.toString());
      }
      if(value instanceof BigDecimal bd)
      {
         boolean safe = bd.abs().compareTo(MAX_SAFE) <= 0 && bd.precision() <= 15;
         return (safe ? bd : bd.toPlainString());
      }
      if(value instanceof Double d)
      {
         return (d.isNaN() || d.isInfinite() ? d.toString() : d);
      }
      if(value instanceof Float f)
      {
         return (f.isNaN() || f.isInfinite() ? f.toString() : f.doubleValue());
      }
      if(value instanceof Number n)
      {
         return (n.toString());
      }
      if(value instanceof byte[] bytes)
      {
         return (binary(bytes, bytes.length));
      }
      if(value instanceof Blob blob)
      {
         long length = blob.length();
         return (binary(blob.getBytes(1, (int) Math.min(length, HEX_PREVIEW_BYTES)), length));
      }
      if(value instanceof Clob clob)
      {
         long length = clob.length();
         return (capString(clob.getSubString(1, (int) Math.min(length, maxCellChars + 1L))));
      }
      if(value instanceof Array array)
      {
         Object raw = array.getArray();
         List<Object> list = new ArrayList<>();
         if(raw instanceof Object[] objects)
         {
            for(Object o : objects)
            {
               list.add(convert(o));
            }
         }
         return (list);
      }
      if(value instanceof Timestamp timestamp)
      {
         return (timestamp.toLocalDateTime().toString());
      }
      if(value instanceof Date date)
      {
         return (date.toLocalDate().toString());
      }
      if(value instanceof Time time)
      {
         return (time.toLocalTime().toString());
      }
      if(value instanceof TemporalAccessor)
      {
         return (value.toString());
      }
      return (capString(value.toString()));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Map<String, Object> binary(byte[] bytes, long length)
   {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("$binary", true);
      map.put("length", length);
      byte[] preview = bytes.length > HEX_PREVIEW_BYTES ? java.util.Arrays.copyOf(bytes, HEX_PREVIEW_BYTES) : bytes;
      map.put("hexPreview", HexFormat.of().formatHex(preview));
      return (map);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private String capString(String value)
   {
      if(value == null || value.length() <= maxCellChars)
      {
         return (value);
      }
      return (value.substring(0, maxCellChars) + "…");
   }



   /*******************************************************************************
    ** Rough in-memory / on-the-wire size of a converted value.
    *******************************************************************************/
   static long estimateBytes(Object value)
   {
      if(value == null)
      {
         return (4);
      }
      if(value instanceof String s)
      {
         return (16L + 2L * s.length());
      }
      if(value instanceof List<?> list)
      {
         long total = 16;
         for(Object o : list)
         {
            total += estimateBytes(o);
         }
         return (total);
      }
      if(value instanceof Map<?, ?>)
      {
         return (128);
      }
      return (16);
   }

}
