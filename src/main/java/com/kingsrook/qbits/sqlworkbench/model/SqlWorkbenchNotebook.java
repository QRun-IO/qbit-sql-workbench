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
 ** A saved notebook: ordered SQL and markdown cells, owned by a user, personal or
 ** shared through the library.
 *******************************************************************************/
public class SqlWorkbenchNotebook extends QRecordEntity
{
   public static final String TABLE_NAME = "sql_workbench_notebook";

   /*******************************************************************************
    ** The table name before 1.0.0-RC.3 (owner rule: lowercase snake_case,
    ** prefixed with the qbit name). Kept for migrating stored references only.
    *******************************************************************************/
   @Deprecated(since = "1.0.0-RC.3")
   public static final String LEGACY_TABLE_NAME = "sqlWorkbenchNotebook";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, maxLength = 255, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String title;

   @QField()
   private String description;

   @QField(isRequired = true, isEditable = false, maxLength = 255)
   private String ownerUserId;

   @QField(isEditable = false, maxLength = 255, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String ownerName;

   @QField(isRequired = true, maxLength = 16)
   private String visibility;

   @QField(maxLength = 100)
   private String defaultBackendName;

   @QField()
   private String parametersJson;

   @QField(isEditable = false)
   private Integer currentVersion;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public SqlWorkbenchNotebook()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public SqlWorkbenchNotebook(QRecord record)
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
   public SqlWorkbenchNotebook withId(Integer id)
   {
      this.id = id;
      return (this);
   }



   /*******************************************************************************
    ** Getter for title
    *******************************************************************************/
   public String getTitle()
   {
      return (this.title);
   }



   /*******************************************************************************
    ** Setter for title
    *******************************************************************************/
   public void setTitle(String title)
   {
      this.title = title;
   }



   /*******************************************************************************
    ** Fluent setter for title
    *******************************************************************************/
   public SqlWorkbenchNotebook withTitle(String title)
   {
      this.title = title;
      return (this);
   }



   /*******************************************************************************
    ** Getter for description
    *******************************************************************************/
   public String getDescription()
   {
      return (this.description);
   }



   /*******************************************************************************
    ** Setter for description
    *******************************************************************************/
   public void setDescription(String description)
   {
      this.description = description;
   }



   /*******************************************************************************
    ** Fluent setter for description
    *******************************************************************************/
   public SqlWorkbenchNotebook withDescription(String description)
   {
      this.description = description;
      return (this);
   }



   /*******************************************************************************
    ** Getter for ownerUserId
    *******************************************************************************/
   public String getOwnerUserId()
   {
      return (this.ownerUserId);
   }



   /*******************************************************************************
    ** Setter for ownerUserId
    *******************************************************************************/
   public void setOwnerUserId(String ownerUserId)
   {
      this.ownerUserId = ownerUserId;
   }



   /*******************************************************************************
    ** Fluent setter for ownerUserId
    *******************************************************************************/
   public SqlWorkbenchNotebook withOwnerUserId(String ownerUserId)
   {
      this.ownerUserId = ownerUserId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for ownerName
    *******************************************************************************/
   public String getOwnerName()
   {
      return (this.ownerName);
   }



   /*******************************************************************************
    ** Setter for ownerName
    *******************************************************************************/
   public void setOwnerName(String ownerName)
   {
      this.ownerName = ownerName;
   }



   /*******************************************************************************
    ** Fluent setter for ownerName
    *******************************************************************************/
   public SqlWorkbenchNotebook withOwnerName(String ownerName)
   {
      this.ownerName = ownerName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for visibility
    *******************************************************************************/
   public String getVisibility()
   {
      return (this.visibility);
   }



   /*******************************************************************************
    ** Setter for visibility
    *******************************************************************************/
   public void setVisibility(String visibility)
   {
      this.visibility = visibility;
   }



   /*******************************************************************************
    ** Fluent setter for visibility
    *******************************************************************************/
   public SqlWorkbenchNotebook withVisibility(String visibility)
   {
      this.visibility = visibility;
      return (this);
   }



   /*******************************************************************************
    ** Getter for defaultBackendName
    *******************************************************************************/
   public String getDefaultBackendName()
   {
      return (this.defaultBackendName);
   }



   /*******************************************************************************
    ** Setter for defaultBackendName
    *******************************************************************************/
   public void setDefaultBackendName(String defaultBackendName)
   {
      this.defaultBackendName = defaultBackendName;
   }



   /*******************************************************************************
    ** Fluent setter for defaultBackendName
    *******************************************************************************/
   public SqlWorkbenchNotebook withDefaultBackendName(String defaultBackendName)
   {
      this.defaultBackendName = defaultBackendName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for parametersJson
    *******************************************************************************/
   public String getParametersJson()
   {
      return (this.parametersJson);
   }



   /*******************************************************************************
    ** Setter for parametersJson
    *******************************************************************************/
   public void setParametersJson(String parametersJson)
   {
      this.parametersJson = parametersJson;
   }



   /*******************************************************************************
    ** Fluent setter for parametersJson
    *******************************************************************************/
   public SqlWorkbenchNotebook withParametersJson(String parametersJson)
   {
      this.parametersJson = parametersJson;
      return (this);
   }



   /*******************************************************************************
    ** Getter for currentVersion
    *******************************************************************************/
   public Integer getCurrentVersion()
   {
      return (this.currentVersion);
   }



   /*******************************************************************************
    ** Setter for currentVersion
    *******************************************************************************/
   public void setCurrentVersion(Integer currentVersion)
   {
      this.currentVersion = currentVersion;
   }



   /*******************************************************************************
    ** Fluent setter for currentVersion
    *******************************************************************************/
   public SqlWorkbenchNotebook withCurrentVersion(Integer currentVersion)
   {
      this.currentVersion = currentVersion;
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
   public SqlWorkbenchNotebook withCreateDate(Instant createDate)
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
   public SqlWorkbenchNotebook withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }

}
