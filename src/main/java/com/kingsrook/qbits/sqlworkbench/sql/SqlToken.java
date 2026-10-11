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
 ** One significant token of a SQL text. Comments and whitespace produce no
 ** tokens; start and end are offsets into the original text (end exclusive).
 **
 ** @param type  kind of token
 ** @param text  the token's source text (for PARAMETER, the name without ':')
 ** @param start offset of the token's first character
 ** @param end   offset after the token's last character
 *******************************************************************************/
public record SqlToken(Type type, String text, int start, int end)
{

   /*******************************************************************************
    ** Token kinds.
    *******************************************************************************/
   public enum Type
   {
      WORD,
      QUOTED_IDENTIFIER,
      STRING,
      NUMBER,
      PARAMETER,
      SEMICOLON,
      OPEN_PAREN,
      CLOSE_PAREN,
      SYMBOL
   }



   /*******************************************************************************
    ** Upper-cased text of a WORD token, or null for any other token.
    *******************************************************************************/
   public String keyword()
   {
      return (type == Type.WORD ? text.toUpperCase(Locale.ROOT) : null);
   }



   /*******************************************************************************
    ** Whether this is a WORD equal (ignoring case) to the given keyword.
    *******************************************************************************/
   public boolean isKeyword(String keyword)
   {
      return (type == Type.WORD && text.equalsIgnoreCase(keyword));
   }

}
