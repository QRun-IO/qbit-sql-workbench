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
 ** A statement with its :name parameters rewritten to JDBC '?' markers, plus
 ** the parameter name for each marker, in order. Values are always bound
 ** through PreparedStatement, never spliced into the text.
 **
 ** @param jdbcSql        the statement with ? markers
 ** @param parameterOrder the parameter name bound at each marker
 *******************************************************************************/
public record NamedParameterSql(String jdbcSql, List<String> parameterOrder)
{

   /*******************************************************************************
    ** Rewrite a classified statement.
    *******************************************************************************/
   public static NamedParameterSql of(SqlStatement statement)
   {
      String          text  = statement.text();
      int             base  = statement.tokens().isEmpty() ? 0 : statement.tokens().get(0).start();
      StringBuilder   sb    = new StringBuilder();
      List<String>    order = new ArrayList<>();
      int             at    = 0;

      for(SqlToken token : statement.tokens())
      {
         if(token.type() == SqlToken.Type.PARAMETER)
         {
            int start = token.start() - base;
            sb.append(text, at, start).append('?');
            at = token.end() - base;
            order.add(token.text());
         }
      }
      sb.append(text.substring(at));
      return (new NamedParameterSql(sb.toString(), List.copyOf(order)));
   }

}
