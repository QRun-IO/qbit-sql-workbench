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


import java.util.Map;


/*******************************************************************************
 ** A request to run SQL.
 **
 ** @param backendName         target backend
 ** @param sql                 the SQL text
 ** @param params              values for :name parameters (JSON types)
 ** @param write               true for write mode
 ** @param notebookId          where it ran from (optional)
 ** @param cellKey             which cell (optional)
 ** @param pageSize            rows in the first page (optional)
 ** @param confirmToken        write mode: the preview's token (commit)
 ** @param acknowledgeRowGuard write mode: the caller accepted the row guard
 ** @param executionUuid       optional client-chosen id (a UUID), so the client
 **                            can cancel the run before it answers; generated
 **                            when absent
 *******************************************************************************/
public record ExecutionRequest(String backendName, String sql, Map<String, Object> params, boolean write, Integer notebookId, String cellKey,
                               Integer pageSize, String confirmToken, boolean acknowledgeRowGuard, String executionUuid)
{

   /*******************************************************************************
    ** Without a client-chosen execution id.
    *******************************************************************************/
   public ExecutionRequest(String backendName, String sql, Map<String, Object> params, boolean write, Integer notebookId, String cellKey,
                           Integer pageSize, String confirmToken, boolean acknowledgeRowGuard)
   {
      this(backendName, sql, params, write, notebookId, cellKey, pageSize, confirmToken, acknowledgeRowGuard, null);
   }



   /*******************************************************************************
    ** A read-only request.
    *******************************************************************************/
   public static ExecutionRequest readOnly(String backendName, String sql, Map<String, Object> params)
   {
      return (new ExecutionRequest(backendName, sql, params, false, null, null, null, null, false));
   }

}
