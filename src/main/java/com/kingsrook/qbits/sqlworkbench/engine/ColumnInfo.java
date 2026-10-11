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


import java.util.LinkedHashMap;
import java.util.Map;


/*******************************************************************************
 ** One result column.
 **
 ** @param name     column name from the driver
 ** @param label    column label (alias) from the driver
 ** @param typeName database type name (e.g. int4, jsonb, VARCHAR)
 ** @param jdbcType java.sql.Types code
 ** @param kind     how the UI should render it: number, string, boolean,
 **                 date, datetime, time, json, binary, array, other
 *******************************************************************************/
public record ColumnInfo(String name, String label, String typeName, int jdbcType, String kind)
{

   /*******************************************************************************
    ** JSON view of this column.
    *******************************************************************************/
   public Map<String, Object> toMap()
   {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("name", name);
      map.put("label", label);
      map.put("typeName", typeName);
      map.put("jdbcType", jdbcType);
      map.put("kind", kind);
      return (map);
   }

}
