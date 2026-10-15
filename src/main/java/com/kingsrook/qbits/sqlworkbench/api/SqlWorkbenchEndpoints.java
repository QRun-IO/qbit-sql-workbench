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

package com.kingsrook.qbits.sqlworkbench.api;


import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.MiddlewareSpecExtension;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.MiddlewareSpecExtensionsMetaData;


/*******************************************************************************
 ** Registers all SQL Workbench operations as UI API specs.
 *******************************************************************************/
public class SqlWorkbenchEndpoints
{
   /*******************************************************************************
    ** Register the extension idempotently.
    *******************************************************************************/
   public static void register(QInstance qInstance)
   {
      MiddlewareSpecExtensionsMetaData.ofOrWithNew(qInstance).withExtension(extension());
   }



   /*******************************************************************************
    ** The complete UI API extension at its established prefix.
    *******************************************************************************/
   public static MiddlewareSpecExtension extension()
   {
      return (new MiddlewareSpecExtension().withName("sql-workbench").withPathPrefix("/sql-workbench")
         .withSemanticVersion("1.1.0").withDescription("Audited SQL execution and notebooks")
         .withSpec(GetCapabilitiesSpec.class)
         .withSpec(GetSchemaSpec.class)
         .withSpec(ExecuteSpec.class)
         .withSpec(ExplainSpec.class)
         .withSpec(GetRowsSpec.class)
         .withSpec(CancelExecutionSpec.class)
         .withSpec(GetHistorySpec.class)
         .withSpec(ListNotebooksSpec.class)
         .withSpec(CreateNotebookSpec.class)
         .withSpec(GetNotebookSpec.class)
         .withSpec(UpdateNotebookSpec.class)
         .withSpec(DeleteNotebookSpec.class)
         .withSpec(PublishNotebookSpec.class)
         .withSpec(DuplicateNotebookSpec.class)
         .withSpec(ListVersionsSpec.class)
         .withSpec(GetVersionSpec.class)
         .withSpec(RestoreVersionSpec.class)
         .withSpec(ListSnapshotsSpec.class)
         .withSpec(CreateSnapshotSpec.class)
         .withSpec(DeleteSnapshotSpec.class)
         .withSpec(ExportResultSpec.class));
   }
}
