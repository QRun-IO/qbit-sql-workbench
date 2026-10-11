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


/*******************************************************************************
 ** One statement of a SQL text, with its classification.
 **
 ** @param text           the statement's source text (no trailing ';')
 ** @param tokens         its significant tokens (offsets are into the full text)
 ** @param kind           its classification
 ** @param leadingKeyword its first keyword, upper case (null if none)
 ** @param reason         why it got this kind, for messages
 *******************************************************************************/
public record SqlStatement(String text, List<SqlToken> tokens, StatementKind kind, String leadingKeyword, String reason)
{

   /*******************************************************************************
    ** Names of the :parameters this statement uses, in order of appearance
    ** (repeats included).
    *******************************************************************************/
   public List<String> parameterNames()
   {
      return (tokens.stream().filter(t -> t.type() == SqlToken.Type.PARAMETER).map(SqlToken::text).toList());
   }

}
