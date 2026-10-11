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


/*******************************************************************************
 ** What a statement does, as far as the workbench's gating is concerned.
 **
 ** READ               - SELECT / WITH / VALUES / TABLE / SHOW / DESCRIBE /
 **                      EXPLAIN (without ANALYZE) / read-only PRAGMA. Allowed
 **                      in read-only sessions.
 ** EXPLAIN_ANALYZE    - EXPLAIN ANALYZE executes its statement: needs the
 **                      write permission and an explicit request.
 ** WRITE              - DML (INSERT, UPDATE, DELETE, MERGE, ...). Write mode.
 ** DDL                - CREATE, ALTER, DROP, TRUNCATE, GRANT, ... Write mode;
 **                      dry run only where DDL is transactional.
 ** FORBIDDEN          - transaction and session control (BEGIN, COMMIT, SET,
 **                      USE, ATTACH, ...) and file/system commands. The engine
 **                      owns transactions and session state, so these are
 **                      refused in every mode.
 *******************************************************************************/
public enum StatementKind
{
   READ,
   EXPLAIN_ANALYZE,
   WRITE,
   DDL,
   FORBIDDEN;



   /*******************************************************************************
    ** Whether a statement of this kind may run in a read-only session.
    *******************************************************************************/
   public boolean isReadOnlySafe()
   {
      return (this == READ);
   }



   /*******************************************************************************
    ** Whether a statement of this kind changes data or schema.
    *******************************************************************************/
   public boolean isMutating()
   {
      return (this == WRITE || this == DDL);
   }

}
