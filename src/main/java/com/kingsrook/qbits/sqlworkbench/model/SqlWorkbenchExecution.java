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
 ** Audit record of one SQL Workbench execution: who, when, where, what SQL,
 ** in which mode, and how it ended. Append-only: written before the SQL runs
 ** and completed after.
 *******************************************************************************/
public class SqlWorkbenchExecution extends QRecordEntity
{
   public static final String TABLE_NAME = "sql_workbench_execution";

   /*******************************************************************************
    ** The table name before 1.0.0-RC.3 (owner rule: lowercase snake_case,
    ** prefixed with the qbit name). Kept for migrating stored references only.
    *******************************************************************************/
   @Deprecated(since = "1.0.0-RC.3")
   public static final String LEGACY_TABLE_NAME = "sqlWorkbenchExecution";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isEditable = false, maxLength = 36)
   private String executionUuid;

   @QField(isEditable = false, maxLength = 255)
   private String userId;

   @QField(isEditable = false, maxLength = 255, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String userName;

   @QField(isEditable = false, maxLength = 100)
   private String backendName;

   @QField(isEditable = false, maxLength = 24)
   private String mode;

   @QField(isEditable = false, maxLength = 16)
   private String status;

   @QField(isEditable = false)
   private String sqlText;

   @QField(isEditable = false, maxLength = 64)
   private String sqlHash;

   @QField(isEditable = false)
   private String parametersJson;

   @QField(isEditable = false)
   private Integer statementCount;

   @QField(isEditable = false, maxLength = 255, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String statementKinds;

   @QField(isEditable = false)
   private Integer notebookId;

   @QField(isEditable = false, maxLength = 64)
   private String cellKey;

   @QField(isEditable = false)
   private Instant startTime;

   @QField(isEditable = false)
   private Instant endTime;

   @QField(isEditable = false)
   private Integer durationMs;

   @QField(isEditable = false)
   private Integer rowsReturned;

   @QField(isEditable = false)
   private Integer rowsAffected;

   @QField(isEditable = false)
   private Boolean truncated;

   @QField(isEditable = false)
   private String errorMessage;

   @QField(isEditable = false, maxLength = 64)
   private String clientIp;

   @QField(isEditable = false)
   private Instant createDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public SqlWorkbenchExecution()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public SqlWorkbenchExecution(QRecord record)
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
   public SqlWorkbenchExecution withId(Integer id)
   {
      this.id = id;
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
   public SqlWorkbenchExecution withExecutionUuid(String executionUuid)
   {
      this.executionUuid = executionUuid;
      return (this);
   }



   /*******************************************************************************
    ** Getter for userId
    *******************************************************************************/
   public String getUserId()
   {
      return (this.userId);
   }



   /*******************************************************************************
    ** Setter for userId
    *******************************************************************************/
   public void setUserId(String userId)
   {
      this.userId = userId;
   }



   /*******************************************************************************
    ** Fluent setter for userId
    *******************************************************************************/
   public SqlWorkbenchExecution withUserId(String userId)
   {
      this.userId = userId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for userName
    *******************************************************************************/
   public String getUserName()
   {
      return (this.userName);
   }



   /*******************************************************************************
    ** Setter for userName
    *******************************************************************************/
   public void setUserName(String userName)
   {
      this.userName = userName;
   }



   /*******************************************************************************
    ** Fluent setter for userName
    *******************************************************************************/
   public SqlWorkbenchExecution withUserName(String userName)
   {
      this.userName = userName;
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
   public SqlWorkbenchExecution withBackendName(String backendName)
   {
      this.backendName = backendName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for mode
    *******************************************************************************/
   public String getMode()
   {
      return (this.mode);
   }



   /*******************************************************************************
    ** Setter for mode
    *******************************************************************************/
   public void setMode(String mode)
   {
      this.mode = mode;
   }



   /*******************************************************************************
    ** Fluent setter for mode
    *******************************************************************************/
   public SqlWorkbenchExecution withMode(String mode)
   {
      this.mode = mode;
      return (this);
   }



   /*******************************************************************************
    ** Getter for status
    *******************************************************************************/
   public String getStatus()
   {
      return (this.status);
   }



   /*******************************************************************************
    ** Setter for status
    *******************************************************************************/
   public void setStatus(String status)
   {
      this.status = status;
   }



   /*******************************************************************************
    ** Fluent setter for status
    *******************************************************************************/
   public SqlWorkbenchExecution withStatus(String status)
   {
      this.status = status;
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
   public SqlWorkbenchExecution withSqlText(String sqlText)
   {
      this.sqlText = sqlText;
      return (this);
   }



   /*******************************************************************************
    ** Getter for sqlHash
    *******************************************************************************/
   public String getSqlHash()
   {
      return (this.sqlHash);
   }



   /*******************************************************************************
    ** Setter for sqlHash
    *******************************************************************************/
   public void setSqlHash(String sqlHash)
   {
      this.sqlHash = sqlHash;
   }



   /*******************************************************************************
    ** Fluent setter for sqlHash
    *******************************************************************************/
   public SqlWorkbenchExecution withSqlHash(String sqlHash)
   {
      this.sqlHash = sqlHash;
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
   public SqlWorkbenchExecution withParametersJson(String parametersJson)
   {
      this.parametersJson = parametersJson;
      return (this);
   }



   /*******************************************************************************
    ** Getter for statementCount
    *******************************************************************************/
   public Integer getStatementCount()
   {
      return (this.statementCount);
   }



   /*******************************************************************************
    ** Setter for statementCount
    *******************************************************************************/
   public void setStatementCount(Integer statementCount)
   {
      this.statementCount = statementCount;
   }



   /*******************************************************************************
    ** Fluent setter for statementCount
    *******************************************************************************/
   public SqlWorkbenchExecution withStatementCount(Integer statementCount)
   {
      this.statementCount = statementCount;
      return (this);
   }



   /*******************************************************************************
    ** Getter for statementKinds
    *******************************************************************************/
   public String getStatementKinds()
   {
      return (this.statementKinds);
   }



   /*******************************************************************************
    ** Setter for statementKinds
    *******************************************************************************/
   public void setStatementKinds(String statementKinds)
   {
      this.statementKinds = statementKinds;
   }



   /*******************************************************************************
    ** Fluent setter for statementKinds
    *******************************************************************************/
   public SqlWorkbenchExecution withStatementKinds(String statementKinds)
   {
      this.statementKinds = statementKinds;
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
   public SqlWorkbenchExecution withNotebookId(Integer notebookId)
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
   public SqlWorkbenchExecution withCellKey(String cellKey)
   {
      this.cellKey = cellKey;
      return (this);
   }



   /*******************************************************************************
    ** Getter for startTime
    *******************************************************************************/
   public Instant getStartTime()
   {
      return (this.startTime);
   }



   /*******************************************************************************
    ** Setter for startTime
    *******************************************************************************/
   public void setStartTime(Instant startTime)
   {
      this.startTime = startTime;
   }



   /*******************************************************************************
    ** Fluent setter for startTime
    *******************************************************************************/
   public SqlWorkbenchExecution withStartTime(Instant startTime)
   {
      this.startTime = startTime;
      return (this);
   }



   /*******************************************************************************
    ** Getter for endTime
    *******************************************************************************/
   public Instant getEndTime()
   {
      return (this.endTime);
   }



   /*******************************************************************************
    ** Setter for endTime
    *******************************************************************************/
   public void setEndTime(Instant endTime)
   {
      this.endTime = endTime;
   }



   /*******************************************************************************
    ** Fluent setter for endTime
    *******************************************************************************/
   public SqlWorkbenchExecution withEndTime(Instant endTime)
   {
      this.endTime = endTime;
      return (this);
   }



   /*******************************************************************************
    ** Getter for durationMs
    *******************************************************************************/
   public Integer getDurationMs()
   {
      return (this.durationMs);
   }



   /*******************************************************************************
    ** Setter for durationMs
    *******************************************************************************/
   public void setDurationMs(Integer durationMs)
   {
      this.durationMs = durationMs;
   }



   /*******************************************************************************
    ** Fluent setter for durationMs
    *******************************************************************************/
   public SqlWorkbenchExecution withDurationMs(Integer durationMs)
   {
      this.durationMs = durationMs;
      return (this);
   }



   /*******************************************************************************
    ** Getter for rowsReturned
    *******************************************************************************/
   public Integer getRowsReturned()
   {
      return (this.rowsReturned);
   }



   /*******************************************************************************
    ** Setter for rowsReturned
    *******************************************************************************/
   public void setRowsReturned(Integer rowsReturned)
   {
      this.rowsReturned = rowsReturned;
   }



   /*******************************************************************************
    ** Fluent setter for rowsReturned
    *******************************************************************************/
   public SqlWorkbenchExecution withRowsReturned(Integer rowsReturned)
   {
      this.rowsReturned = rowsReturned;
      return (this);
   }



   /*******************************************************************************
    ** Getter for rowsAffected
    *******************************************************************************/
   public Integer getRowsAffected()
   {
      return (this.rowsAffected);
   }



   /*******************************************************************************
    ** Setter for rowsAffected
    *******************************************************************************/
   public void setRowsAffected(Integer rowsAffected)
   {
      this.rowsAffected = rowsAffected;
   }



   /*******************************************************************************
    ** Fluent setter for rowsAffected
    *******************************************************************************/
   public SqlWorkbenchExecution withRowsAffected(Integer rowsAffected)
   {
      this.rowsAffected = rowsAffected;
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
   public SqlWorkbenchExecution withTruncated(Boolean truncated)
   {
      this.truncated = truncated;
      return (this);
   }



   /*******************************************************************************
    ** Getter for errorMessage
    *******************************************************************************/
   public String getErrorMessage()
   {
      return (this.errorMessage);
   }



   /*******************************************************************************
    ** Setter for errorMessage
    *******************************************************************************/
   public void setErrorMessage(String errorMessage)
   {
      this.errorMessage = errorMessage;
   }



   /*******************************************************************************
    ** Fluent setter for errorMessage
    *******************************************************************************/
   public SqlWorkbenchExecution withErrorMessage(String errorMessage)
   {
      this.errorMessage = errorMessage;
      return (this);
   }



   /*******************************************************************************
    ** Getter for clientIp
    *******************************************************************************/
   public String getClientIp()
   {
      return (this.clientIp);
   }



   /*******************************************************************************
    ** Setter for clientIp
    *******************************************************************************/
   public void setClientIp(String clientIp)
   {
      this.clientIp = clientIp;
   }



   /*******************************************************************************
    ** Fluent setter for clientIp
    *******************************************************************************/
   public SqlWorkbenchExecution withClientIp(String clientIp)
   {
      this.clientIp = clientIp;
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
   public SqlWorkbenchExecution withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }

}
