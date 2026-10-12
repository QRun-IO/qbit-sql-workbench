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
 ** One audience a notebook is shared with (read-only): a user, a group, or
 ** everyone with workbench access (audienceType ALL - the shared library).
 ** audienceKey ("user:alice", "group:analysts", "all") is computed by the
 ** table customizer and is what the record security locks match against the
 ** session's sql_workbench_audience key values.
 *******************************************************************************/
public class SqlWorkbenchNotebookShare extends QRecordEntity
{
   public static final String TABLE_NAME = "sql_workbench_notebook_share";

   /*******************************************************************************
    ** The table name before 1.0.0-RC.3 (owner rule: lowercase snake_case,
    ** prefixed with the qbit name). Kept for migrating stored references only.
    *******************************************************************************/
   @Deprecated(since = "1.0.0-RC.3")
   public static final String LEGACY_TABLE_NAME = "sqlWorkbenchNotebookShare";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, possibleValueSourceName = SqlWorkbenchNotebook.TABLE_NAME)
   private Integer notebookId;

   @QField(isRequired = true, maxLength = 16, possibleValueSourceName = ShareAudienceType.NAME)
   private String audienceType;

   @QField(maxLength = 255, label = "User or Group")
   private String audienceId;

   @QField(isEditable = false, maxLength = 300)
   private String audienceKey;

   @QField(isEditable = false, maxLength = 255)
   private String createdByUserId;

   @QField(isEditable = false, maxLength = 255, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String createdByName;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public SqlWorkbenchNotebookShare()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public SqlWorkbenchNotebookShare(QRecord record)
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
   public SqlWorkbenchNotebookShare withId(Integer id)
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
   public SqlWorkbenchNotebookShare withNotebookId(Integer notebookId)
   {
      this.notebookId = notebookId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for audienceType
    *******************************************************************************/
   public String getAudienceType()
   {
      return (this.audienceType);
   }



   /*******************************************************************************
    ** Setter for audienceType
    *******************************************************************************/
   public void setAudienceType(String audienceType)
   {
      this.audienceType = audienceType;
   }



   /*******************************************************************************
    ** Fluent setter for audienceType
    *******************************************************************************/
   public SqlWorkbenchNotebookShare withAudienceType(String audienceType)
   {
      this.audienceType = audienceType;
      return (this);
   }



   /*******************************************************************************
    ** Getter for audienceId
    *******************************************************************************/
   public String getAudienceId()
   {
      return (this.audienceId);
   }



   /*******************************************************************************
    ** Setter for audienceId
    *******************************************************************************/
   public void setAudienceId(String audienceId)
   {
      this.audienceId = audienceId;
   }



   /*******************************************************************************
    ** Fluent setter for audienceId
    *******************************************************************************/
   public SqlWorkbenchNotebookShare withAudienceId(String audienceId)
   {
      this.audienceId = audienceId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for audienceKey
    *******************************************************************************/
   public String getAudienceKey()
   {
      return (this.audienceKey);
   }



   /*******************************************************************************
    ** Setter for audienceKey
    *******************************************************************************/
   public void setAudienceKey(String audienceKey)
   {
      this.audienceKey = audienceKey;
   }



   /*******************************************************************************
    ** Fluent setter for audienceKey
    *******************************************************************************/
   public SqlWorkbenchNotebookShare withAudienceKey(String audienceKey)
   {
      this.audienceKey = audienceKey;
      return (this);
   }



   /*******************************************************************************
    ** Getter for createdByUserId
    *******************************************************************************/
   public String getCreatedByUserId()
   {
      return (this.createdByUserId);
   }



   /*******************************************************************************
    ** Setter for createdByUserId
    *******************************************************************************/
   public void setCreatedByUserId(String createdByUserId)
   {
      this.createdByUserId = createdByUserId;
   }



   /*******************************************************************************
    ** Fluent setter for createdByUserId
    *******************************************************************************/
   public SqlWorkbenchNotebookShare withCreatedByUserId(String createdByUserId)
   {
      this.createdByUserId = createdByUserId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for createdByName
    *******************************************************************************/
   public String getCreatedByName()
   {
      return (this.createdByName);
   }



   /*******************************************************************************
    ** Setter for createdByName
    *******************************************************************************/
   public void setCreatedByName(String createdByName)
   {
      this.createdByName = createdByName;
   }



   /*******************************************************************************
    ** Fluent setter for createdByName
    *******************************************************************************/
   public SqlWorkbenchNotebookShare withCreatedByName(String createdByName)
   {
      this.createdByName = createdByName;
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
   public SqlWorkbenchNotebookShare withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }



   /*******************************************************************************
    ** Getter for modifyDate
    *******************************************************************************/
   public Instant getModifyDate()
   {
      return (this.modifyDate);
   }



   /*******************************************************************************
    ** Setter for modifyDate
    *******************************************************************************/
   public void setModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
   }



   /*******************************************************************************
    ** Fluent setter for modifyDate
    *******************************************************************************/
   public SqlWorkbenchNotebookShare withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }

}
