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


import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchSecurity;
import com.kingsrook.qqq.backend.core.model.session.QSession;

/*******************************************************************************
 ** Who is calling, and what they may do (resolved from the QSession by the
 ** API layer).
 **
 ** @param userId   the session user's idReference (owner key everywhere)
 ** @param userName display name
 ** @param canWrite has the write permission (and write mode is enabled)
 ** @param canShare has the share permission
 ** @param clientIp request address, when known
 *******************************************************************************/
public record Caller(String userId, String userName, boolean canWrite, boolean canShare, String clientIp)
{

   /*******************************************************************************
    ** The Caller of a session (which must already be authenticated and hold
    ** the access permission - the API and the record actions check that).
    *******************************************************************************/
   public static Caller fromSession(SqlWorkbenchQBitConfig config, QSession session, String clientIp)
   {
      String  userId   = SqlWorkbenchSecurity.userId(session);
      boolean canWrite = Boolean.TRUE.equals(config.getWriteModeEnabled()) && session.hasPermission(config.getWritePermissionName());
      boolean canShare = session.hasPermission(config.getSharePermissionName());
      return (new Caller(userId, SqlWorkbenchSecurity.userName(session), canWrite, canShare, clientIp));
   }
}
