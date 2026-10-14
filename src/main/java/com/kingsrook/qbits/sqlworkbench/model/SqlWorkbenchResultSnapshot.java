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
 ** A result pinned to a notebook cell: the columns and (capped) rows of one
 ** execution.
 *******************************************************************************/
public class SqlWorkbenchResultSnapshot extends QRecordEntity
{
   public static final String TABLE_NAME = "sql_workbench_result_snapshot";

   /*******************************************************************************
    ** The table name before 1.0.0-RC.4 (owner rule: lowercase snake_case,
    ** prefixed with the qbit name). Kept for migrating stored references only.
    *******************************************************************************/
   @Deprecated(since = "1.0.0-RC.4")
   public static final String LEGACY_TABLE_NAME = "sqlWorkbenchResultSnapshot";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, isEditable = false, possibleValueSourceName = SqlWorkbenchNotebook.TABLE_NAME)
   private Integer notebookId;

   @QField(isEditable = false, maxLength = 64)
   private String cellKey;

   @QField(isEditable = false, maxLength = 36)
   private String executionUuid;

   @QField(maxLength = 255, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String label;

   @QField(isEditable = false, maxLength = 100)
   private String backendName;

   @QField(isEditable = false)
   private String sqlText;

   @QField(isEditable = false)
   private String resultJson;

   @QField(isEditable = false)
   private Integer rowCount;

   @QField(isEditable = false)
   private Boolean truncated;

   @QField(isEditable = false, maxLength = 255)
   private String createdByUserId;

   @QField(isEditable = false)
   private Instant createDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public SqlWorkbenchResultSnapshot()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public SqlWorkbenchResultSnapshot(QRecord record)
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
   public SqlWorkbenchResultSnapshot withId(Integer id)
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
   public SqlWorkbenchResultSnapshot withNotebookId(Integer notebookId)
   {
      this.notebookId = notebookId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for cellKey
    *******************************************************************************/
   public String getCellKey()
   {
      return (this.cellKey);
   }



   /*******************************************************************************
    ** Setter for cellKey
    *******************************************************************************/
   public void setCellKey(String cellKey)
   {
      this.cellKey = cellKey;
   }



   /*******************************************************************************
    ** Fluent setter for cellKey
    *******************************************************************************/
   public SqlWorkbenchResultSnapshot withCellKey(String cellKey)
   {
      this.cellKey = cellKey;
      return (this);
   }



   /*******************************************************************************
    ** Getter for executionUuid
    *******************************************************************************/
   public String getExecutionUuid()
   {
      return (this.executionUuid);
   }



   /*******************************************************************************
    ** Setter for executionUuid
    *******************************************************************************/
   public void setExecutionUuid(String executionUuid)
   {
      this.executionUuid = executionUuid;
   }



   /*******************************************************************************
    ** Fluent setter for executionUuid
    *******************************************************************************/
   public SqlWorkbenchResultSnapshot withExecutionUuid(String executionUuid)
   {
      this.executionUuid = executionUuid;
      return (this);
   }



   /*******************************************************************************
    ** Getter for label
    *******************************************************************************/
   public String getLabel()
   {
      return (this.label);
   }



   /*******************************************************************************
    ** Setter for label
    *******************************************************************************/
   public void setLabel(String label)
   {
      this.label = label;
   }



   /*******************************************************************************
    ** Fluent setter for label
    *******************************************************************************/
   public SqlWorkbenchResultSnapshot withLabel(String label)
   {
      this.label = label;
      return (this);
   }



   /*******************************************************************************
    ** Getter for backendName
    *******************************************************************************/
   public String getBackendName()
   {
      return (this.backendName);
   }



   /*******************************************************************************
    ** Setter for backendName
    *******************************************************************************/
   public void setBackendName(String backendName)
   {
      this.backendName = backendName;
   }



   /*******************************************************************************
    ** Fluent setter for backendName
    *******************************************************************************/
   public SqlWorkbenchResultSnapshot withBackendName(String backendName)
   {
      this.backendName = backendName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for sqlText
    *******************************************************************************/
   public String getSqlText()
   {
      return (this.sqlText);
   }



   /*******************************************************************************
    ** Setter for sqlText
    *******************************************************************************/
   public void setSqlText(String sqlText)
   {
      this.sqlText = sqlText;
   }



   /*******************************************************************************
    ** Fluent setter for sqlText
    *******************************************************************************/
   public SqlWorkbenchResultSnapshot withSqlText(String sqlText)
   {
      this.sqlText = sqlText;
      return (this);
   }



   /*******************************************************************************
    ** Getter for resultJson
    *******************************************************************************/
   public String getResultJson()
   {
      return (this.resultJson);
   }



   /*******************************************************************************
    ** Setter for resultJson
    *******************************************************************************/
   public void setResultJson(String resultJson)
   {
      this.resultJson = resultJson;
   }



   /*******************************************************************************
    ** Fluent setter for resultJson
    *******************************************************************************/
   public SqlWorkbenchResultSnapshot withResultJson(String resultJson)
   {
      this.resultJson = resultJson;
      return (this);
   }



   /*******************************************************************************
    ** Getter for rowCount
    *******************************************************************************/
   public Integer getRowCount()
   {
      return (this.rowCount);
   }



   /*******************************************************************************
    ** Setter for rowCount
    *******************************************************************************/
   public void setRowCount(Integer rowCount)
   {
      this.rowCount = rowCount;
   }



   /*******************************************************************************
    ** Fluent setter for rowCount
    *******************************************************************************/
   public SqlWorkbenchResultSnapshot withRowCount(Integer rowCount)
   {
      this.rowCount = rowCount;
      return (this);
   }



   /*******************************************************************************
    ** Getter for truncated
    *******************************************************************************/
   public Boolean getTruncated()
   {
      return (this.truncated);
   }



   /*******************************************************************************
    ** Setter for truncated
    *******************************************************************************/
   public void setTruncated(Boolean truncated)
   {
      this.truncated = truncated;
   }



   /*******************************************************************************
    ** Fluent setter for truncated
    *******************************************************************************/
   public SqlWorkbenchResultSnapshot withTruncated(Boolean truncated)
   {
      this.truncated = truncated;
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
   public SqlWorkbenchResultSnapshot withCreatedByUserId(String createdByUserId)
   {
      this.createdByUserId = createdByUserId;
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
   public SqlWorkbenchResultSnapshot withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }

}
