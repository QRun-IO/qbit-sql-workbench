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

package com.kingsrook.qbits.sqlworkbench.model;


import java.util.Locale;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.PossibleValueEnum;


/*******************************************************************************
 ** Who a notebook share grants read access to.
 *******************************************************************************/
public enum ShareAudienceType implements PossibleValueEnum<String>
{
   USER("User", "user"),
   GROUP("Group", "group"),
   ALL("Everyone (shared library)", "all");

   public static final String NAME = "sql_workbench_share_audience_type";

   /*******************************************************************************
    ** The possible-value source name before 1.0.0-RC.4.
    *******************************************************************************/
   @Deprecated(since = "1.0.0-RC.4")
   public static final String LEGACY_NAME = "sqlWorkbenchShareAudienceType";

   private final String label;
   private final String keyPrefix;



   /*******************************************************************************
    **
    *******************************************************************************/
   ShareAudienceType(String label, String keyPrefix)
   {
      this.label = label;
      this.keyPrefix = keyPrefix;
   }



   /*******************************************************************************
    ** The audience key for this type and id: "user:alice", "group:analysts",
    ** or "all". Ids are trimmed and lower-cased, as are the session's values.
    *******************************************************************************/
   public String audienceKey(String audienceId)
   {
      if(this == ALL)
      {
         return (keyPrefix);
      }
      return (keyPrefix + ":" + normalize(audienceId));
   }



   /*******************************************************************************
    ** Trim and lower-case an audience id (null stays null).
    *******************************************************************************/
   public static String normalize(String audienceId)
   {
      return (audienceId == null ? null : audienceId.trim().toLowerCase(Locale.ROOT));
   }



   /*******************************************************************************
    ** Look up by name, ignoring case; null when unknown.
    *******************************************************************************/
   public static ShareAudienceType of(Object value)
   {
      if(value == null)
      {
         return (null);
      }
      for(ShareAudienceType type : values())
      {
         if(type.name().equalsIgnoreCase(value.toString().trim()))
         {
            return (type);
         }
      }
      return (null);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getPossibleValueId()
   {
      return (name());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getPossibleValueLabel()
   {
      return (label);
   }
}
