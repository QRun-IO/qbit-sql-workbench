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


import java.time.Instant;
import com.kingsrook.qqq.backend.core.model.data.QField;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;
import com.kingsrook.qqq.backend.core.model.metadata.fields.ValueTooLongBehavior;


/*******************************************************************************
 ** An immutable snapshot of a notebook and its cells, written on every save.
 *******************************************************************************/
public class SqlWorkbenchNotebookVersion extends QRecordEntity
{
   public static final String TABLE_NAME = "sql_workbench_notebook_version";

   /*******************************************************************************
    ** The table name before 1.0.0-RC.3 (owner rule: lowercase snake_case,
    ** prefixed with the qbit name). Kept for migrating stored references only.
    *******************************************************************************/
   @Deprecated(since = "1.0.0-RC.3")
   public static final String LEGACY_TABLE_NAME = "sqlWorkbenchNotebookVersion";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, isEditable = false, possibleValueSourceName = SqlWorkbenchNotebook.TABLE_NAME)
   private Integer notebookId;

   @QField(isRequired = true, isEditable = false)
   private Integer versionNumber;

   @QField(isEditable = false)
   private String contentJson;

   @QField(isEditable = false, maxLength = 255)
   private String savedByUserId;

   @QField(isEditable = false, maxLength = 255, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String savedByName;

   @QField(maxLength = 255, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String comment;

   @QField(isEditable = false)
   private Instant createDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public SqlWorkbenchNotebookVersion()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public SqlWorkbenchNotebookVersion(QRecord record)
   {
      populateFromQRecord(record);
   }



   /*******************************************************************************
    ** Getter for id
    *******************************************************************************/
   public Integer getId()
   {
      return (this.id);
   }



   /*******************************************************************************
    ** Setter for id
    *******************************************************************************/
   public void setId(Integer id)
   {
      this.id = id;
   }



   /*******************************************************************************
    ** Fluent setter for id
    *******************************************************************************/
   public SqlWorkbenchNotebookVersion withId(Integer id)
   {
      this.id = id;
      return (this);
   }



   /*******************************************************************************
    ** Getter for notebookId
    *******************************************************************************/
   public Integer getNotebookId()
   {
      return (this.notebookId);
   }



   /*******************************************************************************
    ** Setter for notebookId
    *******************************************************************************/
   public void setNotebookId(Integer notebookId)
   {
      this.notebookId = notebookId;
   }



   /*******************************************************************************
    ** Fluent setter for notebookId
    *******************************************************************************/
   public SqlWorkbenchNotebookVersion withNotebookId(Integer notebookId)
   {
      this.notebookId = notebookId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for versionNumber
    *******************************************************************************/
   public Integer getVersionNumber()
   {
      return (this.versionNumber);
   }



   /*******************************************************************************
    ** Setter for versionNumber
    *******************************************************************************/
   public void setVersionNumber(Integer versionNumber)
   {
      this.versionNumber = versionNumber;
   }



   /*******************************************************************************
    ** Fluent setter for versionNumber
    *******************************************************************************/
   public SqlWorkbenchNotebookVersion withVersionNumber(Integer versionNumber)
   {
      this.versionNumber = versionNumber;
      return (this);
   }



   /*******************************************************************************
    ** Getter for contentJson
    *******************************************************************************/
   public String getContentJson()
   {
      return (this.contentJson);
   }



   /*******************************************************************************
    ** Setter for contentJson
    *******************************************************************************/
   public void setContentJson(String contentJson)
   {
      this.contentJson = contentJson;
   }



   /*******************************************************************************
    ** Fluent setter for contentJson
    *******************************************************************************/
   public SqlWorkbenchNotebookVersion withContentJson(String contentJson)
   {
      this.contentJson = contentJson;
      return (this);
   }



   /*******************************************************************************
    ** Getter for savedByUserId
    *******************************************************************************/
   public String getSavedByUserId()
   {
      return (this.savedByUserId);
   }



   /*******************************************************************************
    ** Setter for savedByUserId
    *******************************************************************************/
   public void setSavedByUserId(String savedByUserId)
   {
      this.savedByUserId = savedByUserId;
   }



   /*******************************************************************************
    ** Fluent setter for savedByUserId
    *******************************************************************************/
   public SqlWorkbenchNotebookVersion withSavedByUserId(String savedByUserId)
   {
      this.savedByUserId = savedByUserId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for savedByName
    *******************************************************************************/
   public String getSavedByName()
   {
      return (this.savedByName);
   }



   /*******************************************************************************
    ** Setter for savedByName
    *******************************************************************************/
   public void setSavedByName(String savedByName)
   {
      this.savedByName = savedByName;
   }



   /*******************************************************************************
    ** Fluent setter for savedByName
    *******************************************************************************/
   public SqlWorkbenchNotebookVersion withSavedByName(String savedByName)
   {
      this.savedByName = savedByName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for comment
    *******************************************************************************/
   public String getComment()
   {
      return (this.comment);
   }



   /*******************************************************************************
    ** Setter for comment
    *******************************************************************************/
   public void setComment(String comment)
   {
      this.comment = comment;
   }



   /*******************************************************************************
    ** Fluent setter for comment
    *******************************************************************************/
   public SqlWorkbenchNotebookVersion withComment(String comment)
   {
      this.comment = comment;
      return (this);
   }



   /*******************************************************************************
    ** Getter for createDate
    *******************************************************************************/
   public Instant getCreateDate()
   {
      return (this.createDate);
   }



   /*******************************************************************************
    ** Setter for createDate
    *******************************************************************************/
   public void setCreateDate(Instant createDate)
   {
      this.createDate = createDate;
   }



   /*******************************************************************************
    ** Fluent setter for createDate
    *******************************************************************************/
   public SqlWorkbenchNotebookVersion withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }

}
