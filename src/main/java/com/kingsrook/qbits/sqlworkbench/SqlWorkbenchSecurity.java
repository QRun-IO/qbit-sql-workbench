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

package com.kingsrook.qbits.sqlworkbench;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.sqlworkbench.model.ShareAudienceType;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QNotFoundException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.security.QSecurityKeyType;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** The workbench's record-security keys.
 **
 ** Two security key types guard the qbit's tables:
 ** - sql_workbench_user: the session's user id. Owner locks (a notebook's
 **   ownerUserId, an execution's userId) match it. Its all-access key,
 **   sql_workbench_all_access, is set for workbench admins.
 ** - sql_workbench_audience: the session's audience keys: "user:<id>",
 **   "group:<name>" per group, and "all". Share locks (a share row's
 **   audienceKey) match it, for reads only.
 **
 ** Hosts call applyToSession from their authentication customizer, after
 ** the session's user and permissions are set, passing the user's groups
 ** (if the app has any). Without the keys the locks deny: the tables then
 ** show a user nothing, never someone else's rows.
 *******************************************************************************/
public final class SqlWorkbenchSecurity
{
   public static final String AUDIENCE_ALL = ShareAudienceType.ALL.audienceKey(null);



   /*******************************************************************************
    **
    *******************************************************************************/
   private SqlWorkbenchSecurity()
   {
   }



   /*******************************************************************************
    ** The two security key types, for the producer.
    *******************************************************************************/
   public static List<QSecurityKeyType> buildSecurityKeyTypes(SqlWorkbenchQBitConfig config)
   {
      return (List.of(
         new QSecurityKeyType().withName(config.getUserSecurityKeyTypeName()).withAllAccessKeyName(config.getAllAccessSecurityKeyName()),
         new QSecurityKeyType().withName(config.getAudienceSecurityKeyTypeName())));
   }



   /*******************************************************************************
    ** Put the workbench security keys into a session, using the installed
    ** workbench's configuration. Idempotent: earlier workbench keys are
    ** replaced.
    *******************************************************************************/
   public static void applyToSession(QInstance qInstance, QSession session, Collection<String> groups) throws QNotFoundException
   {
      applyToSession(SqlWorkbenchRuntime.get(qInstance).getConfig(), session, groups);
   }



   /*******************************************************************************
    ** Put the workbench security keys into a session: the user id, the
    ** audience keys (user, each group, all) and, for holders of the admin
    ** permission, the all-access key. A session without a user id or
    ** without the workbench access permission gets none, so the locks deny.
    ** Idempotent.
    *******************************************************************************/
   public static void applyToSession(SqlWorkbenchQBitConfig config, QSession session, Collection<String> groups)
   {
      if(session == null || session instanceof QSystemUserSession)
      {
         return;
      }

      Map<String, List<Serializable>> keys = session.getSecurityKeyValues();
      if(keys != null)
      {
         keys.remove(config.getUserSecurityKeyTypeName());
         keys.remove(config.getAudienceSecurityKeyTypeName());
         keys.remove(config.getAllAccessSecurityKeyName());
      }

      //////////////////////////////////////////////////////////////////////
      // no workbench access, no keys: even a code path that skips table  //
      // permissions then finds no workbench rows (not even the library)  //
      //////////////////////////////////////////////////////////////////////
      String userId = userId(session);
      if(!StringUtils.hasContent(userId) || !session.hasPermission(config.getAccessPermissionName()))
      {
         return;
      }

      session.withSecurityKeyValue(config.getUserSecurityKeyTypeName(), userId);
      for(String audienceKey : audienceKeys(userId, groups))
      {
         session.withSecurityKeyValue(config.getAudienceSecurityKeyTypeName(), audienceKey);
      }
      if(session.hasPermission(config.getAdminPermissionName()))
      {
         session.withSecurityKeyValue(config.getAllAccessSecurityKeyName(), true);
      }
   }



   /*******************************************************************************
    ** Add the keys only when the session has none yet (keeping the groups a
    ** host already applied). The workbench endpoints call this, so a host
    ** that never calls applyToSession still gets owner and library access
    ** there; group shares need the host to pass the groups.
    *******************************************************************************/
   public static void ensureSessionKeys(SqlWorkbenchQBitConfig config, QSession session)
   {
      if(session != null && session.getSecurityKeyValues(config.getUserSecurityKeyTypeName()).isEmpty())
      {
         applyToSession(config, session, List.of());
      }
   }



   /*******************************************************************************
    ** The audience keys of a user in some groups: "user:<id>", one
    ** "group:<name>" per group, and "all".
    *******************************************************************************/
   public static List<String> audienceKeys(String userId, Collection<String> groups)
   {
      Set<String> keys = new LinkedHashSet<>();
      if(StringUtils.hasContent(userId))
      {
         keys.add(ShareAudienceType.USER.audienceKey(userId));
      }
      if(groups != null)
      {
         for(String group : groups)
         {
            if(StringUtils.hasContent(group))
            {
               keys.add(ShareAudienceType.GROUP.audienceKey(group));
            }
         }
      }
      keys.add(AUDIENCE_ALL);
      return (new ArrayList<>(keys));
   }



   /*******************************************************************************
    ** The current session's audience keys, as applied.
    *******************************************************************************/
   public static List<String> sessionAudienceKeys(SqlWorkbenchQBitConfig config)
   {
      List<String> keys    = new ArrayList<>();
      QSession     session = QContext.getQSession();
      if(session != null)
      {
         for(Serializable value : session.getSecurityKeyValues(config.getAudienceSecurityKeyTypeName()))
         {
            keys.add(String.valueOf(value));
         }
      }
      return (keys);
   }



   /*******************************************************************************
    ** True for the system user and for sessions holding the all-access key.
    *******************************************************************************/
   public static boolean hasAllAccess(SqlWorkbenchQBitConfig config)
   {
      QSession session = QContext.getQSession();
      return (session instanceof QSystemUserSession || (session != null && session.hasSecurityKeyValue(config.getAllAccessSecurityKeyName(), true, QFieldType.BOOLEAN)));
   }



   /*******************************************************************************
    ** True when the current session is the system user.
    *******************************************************************************/
   public static boolean isSystemUser()
   {
      return (QContext.getQSession() instanceof QSystemUserSession);
   }



   /*******************************************************************************
    ** The session's user id (the user's id reference, else the session's).
    *******************************************************************************/
   public static String userId(QSession session)
   {
      if(session == null)
      {
         return (null);
      }
      if(session.getUser() != null && StringUtils.hasContent(session.getUser().getIdReference()))
      {
         return (session.getUser().getIdReference());
      }
      return (session.getIdReference());
   }



   /*******************************************************************************
    ** The session's user name (full name, else the id).
    *******************************************************************************/
   public static String userName(QSession session)
   {
      if(session != null && session.getUser() != null && StringUtils.hasContent(session.getUser().getFullName()))
      {
         return (session.getUser().getFullName());
      }
      return (userId(session));
   }
}
