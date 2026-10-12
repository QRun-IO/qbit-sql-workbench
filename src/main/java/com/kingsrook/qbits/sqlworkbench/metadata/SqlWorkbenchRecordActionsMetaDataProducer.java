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

package com.kingsrook.qbits.sqlworkbench.metadata;


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.model.ShareAudienceType;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebook;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookShare;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookVersion;
import com.kingsrook.qbits.sqlworkbench.processes.SqlWorkbenchRecordActionSteps;
import com.kingsrook.qqq.backend.core.actions.permissions.TablePermissionSubType;
import com.kingsrook.qqq.backend.core.actions.permissions.UseTablePermissionCustomPermissionChecker;
import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducerMultiOutput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QBackendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QComponentType;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendComponentMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFunctionInputMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QRecordListMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitComponentMetaDataProducer;


/*******************************************************************************
 ** Record actions on the workbench tables (never nav entries):
 ** - Share Notebook (notebook): share with a user, a group, or everyone (the
 **   shared library); needs share-table insert permission.
 ** - Duplicate Notebook (notebook): a personal copy of a readable notebook;
 **   needs notebook insert permission.
 ** - Restore Version (notebook version): restore it as a new version of the
 **   (owned) notebook; needs notebook edit permission.
 *******************************************************************************/
public class SqlWorkbenchRecordActionsMetaDataProducer extends QBitComponentMetaDataProducer<MetaDataProducerMultiOutput, SqlWorkbenchQBitConfig>
{
   public static final String SHARE_NOTEBOOK     = "sqlWorkbenchShareNotebook";
   public static final String DUPLICATE_NOTEBOOK = "sqlWorkbenchDuplicateNotebook";
   public static final String RESTORE_VERSION    = "sqlWorkbenchRestoreNotebookVersion";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public MetaDataProducerMultiOutput produce(QInstance qInstance) throws QException
   {
      SqlWorkbenchQBitConfig config   = getQBitConfig();
      String                 notebook = config.applyPrefix(SqlWorkbenchNotebook.TABLE_NAME);

      MetaDataProducerMultiOutput output = new MetaDataProducerMultiOutput();
      output.add(recordAction(config.applyPrefix(SHARE_NOTEBOOK), "Share Notebook", "share", notebook,
         config.applyPrefix(SqlWorkbenchNotebookShare.TABLE_NAME), TablePermissionSubType.INSERT, SqlWorkbenchRecordActionSteps.ShareNotebookStep.class,
         List.of(new QFieldMetaData("audienceType", QFieldType.STRING).withLabel("Share With").withIsRequired(true).withPossibleValueSourceName(config.applyPrefix(ShareAudienceType.NAME)),
            new QFieldMetaData("audienceId", QFieldType.STRING).withLabel("User or Group (blank for Everyone)"))));
      output.add(recordAction(config.applyPrefix(DUPLICATE_NOTEBOOK), "Duplicate Notebook", "content_copy", notebook,
         notebook, TablePermissionSubType.INSERT, SqlWorkbenchRecordActionSteps.DuplicateNotebookStep.class, List.of()));
      output.add(recordAction(config.applyPrefix(RESTORE_VERSION), "Restore Version", "restore", config.applyPrefix(SqlWorkbenchNotebookVersion.TABLE_NAME),
         notebook, TablePermissionSubType.EDIT, SqlWorkbenchRecordActionSteps.RestoreVersionStep.class, List.of()));
      return (output);
   }



   /*******************************************************************************
    ** A one-record action: optional input form, the backend step (records
    ** loaded through the table's locks), and a result screen.
    *******************************************************************************/
   private static QProcessMetaData recordAction(String name, String label, String icon, String tableName, String permissionTable, TablePermissionSubType permission,
      Class<? extends BackendStep> step, List<QFieldMetaData> formFields)
   {
      List<QStepMetaData> steps = new ArrayList<>();
      if(!formFields.isEmpty())
      {
         steps.add(new QFrontendStepMetaData()
            .withName("input")
            .withComponent(new QFrontendComponentMetaData().withType(QComponentType.EDIT_FORM))
            .withFormFields(formFields));
      }
      steps.add(new QBackendStepMetaData()
         .withName("execute")
         .withInputData(new QFunctionInputMetaData().withRecordListMetaData(new QRecordListMetaData().withTableName(tableName)))
         .withCode(new QCodeReference(step)));
      steps.add(new QFrontendStepMetaData()
         .withName("result")
         .withComponent(new QFrontendComponentMetaData().withType(QComponentType.VIEW_FORM))
         .withViewField(new QFieldMetaData(SqlWorkbenchRecordActionSteps.RESULT_MESSAGE, QFieldType.STRING).withLabel("Result")));

      return (new QProcessMetaData()
         .withName(name)
         .withLabel(label)
         .withIcon(new QIcon(icon))
         .withTableName(tableName)
         .withMinInputRecords(1)
         .withMaxInputRecords(1)
         .withPermissionRules(new QPermissionRules()
            .withLevel(PermissionLevel.HAS_ACCESS_PERMISSION)
            .withCustomPermissionChecker(UseTablePermissionCustomPermissionChecker.build(permissionTable, permission)))
         .withStepList(steps));
   }



   /*******************************************************************************
    ** after the tables and their joins
    *******************************************************************************/
   @Override
   public int getSortOrder()
   {
      return (160);
   }

}
