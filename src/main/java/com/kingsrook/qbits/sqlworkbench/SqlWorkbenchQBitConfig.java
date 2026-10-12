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

package com.kingsrook.qbits.sqlworkbench;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.producers.MetaDataCustomizerInterface;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitConfig;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;


/*******************************************************************************
 ** Host-facing configuration for the SQL Workbench QBit.
 **
 ** Storage: backendName (where the qbit's own tables live), tableNamePrefix,
 ** tableMetaDataCustomizer. Targets: allowedBackendNames /
 ** excludedBackendNames over the instance's RDBMS backends, optional
 ** readOnlyCredentials / writeCredentials per backend, and
 ** useBackendReadOnlyReplica. Permissions: accessPermissionBaseName
 ** (=> "sqlWorkbench.hasAccess"), writePermissionName, sharePermissionName,
 ** and adminPermissionBaseName (=> "sqlWorkbenchAdmin.hasAccess", which
 ** grants the all-access security key: every user's notebooks and the whole
 ** audit). The tables use QQQ's per-table read / insert / edit / delete
 ** permissions; getUserPermissionNames, getSharePermissionNames and
 ** getAdminPermissionNames list what to grant per role, and
 ** SqlWorkbenchSecurity.applyToSession puts the security keys in a session.
 ** Limits: timeouts, rows, bytes, statement counts, row guard, caches.
 **
 ** validate() is list-based and never throws; the producer fails production
 ** when it reports errors.
 *******************************************************************************/
public class SqlWorkbenchQBitConfig implements QBitConfig
{
   public static final String DEFAULT_ACCESS_PERMISSION_BASE_NAME = "sqlWorkbench";
   public static final String DEFAULT_WRITE_PERMISSION_NAME       = "sqlWorkbench.write";
   public static final String DEFAULT_SHARE_PERMISSION_NAME       = "sqlWorkbench.share";
   public static final String DEFAULT_ADMIN_PERMISSION_BASE_NAME  = "sqlWorkbenchAdmin";

   private String backendName;
   private String tableNamePrefix;
   private List<String> allowedBackendNames;
   private List<String> excludedBackendNames = new ArrayList<>();
   private Map<String, SqlWorkbenchCredentials> readOnlyCredentials = new LinkedHashMap<>();
   private Map<String, SqlWorkbenchCredentials> writeCredentials = new LinkedHashMap<>();
   private Boolean useBackendReadOnlyReplica = true;
   private Boolean writeModeEnabled = true;
   private String accessPermissionBaseName = DEFAULT_ACCESS_PERMISSION_BASE_NAME;
   private String writePermissionName = DEFAULT_WRITE_PERMISSION_NAME;
   private String sharePermissionName = DEFAULT_SHARE_PERMISSION_NAME;
   private String adminPermissionBaseName = DEFAULT_ADMIN_PERMISSION_BASE_NAME;
   private Integer statementTimeoutSeconds = 30;
   private Integer writeStatementTimeoutSeconds = 60;
   private Integer maxRows = 10_000;
   private Integer defaultPageSize = 500;
   private Integer maxPageSize = 2000;
   private Integer fetchSize = 500;
   private Long maxResultBytes = 20L * 1024 * 1024;
   private Integer maxCellChars = 100_000;
   private Integer maxSqlLength = 100_000;
   private Integer maxStatementsPerRun = 50;
   private Integer maxAffectedRowsWithoutAcknowledgement = 1000;
   private Integer maxAffectedRows = 100_000;
   private Integer writeConfirmTtlSeconds = 300;
   private Integer maxConcurrentExecutionsPerUser = 2;
   private Integer resultCacheTtlSeconds = 900;
   private Integer resultCacheMaxEntries = 100;
   private Integer schemaCacheTtlSeconds = 300;
   private Integer maxSchemaTables = 2000;
   private Integer snapshotMaxRows = 1000;
   private Integer notebookVersionRetention = 100;
   private Boolean registerRoutes = true;

   private transient MetaDataCustomizerInterface<QTableMetaData> tableMetaDataCustomizer;
   private transient SqlWorkbenchRuntime runtime;



   /*******************************************************************************
    ** Collect configuration errors. Never throws; null-safe for a partially
    ** built QInstance.
    *******************************************************************************/
   @Override
   public void validate(QInstance qInstance, List<String> errors)
   {
      if(!StringUtils.hasContent(backendName))
      {
         errors.add("backendName is required for SqlWorkbenchQBit");
      }
      else if(qInstance != null && qInstance.getBackends() != null && qInstance.getBackend(backendName) == null)
      {
         errors.add("Backend not found: " + backendName);
      }

      if(qInstance != null && qInstance.getBackends() != null)
      {
         for(String name : allowedBackendNames == null ? List.<String>of() : allowedBackendNames)
         {
            QBackendMetaData backend = qInstance.getBackend(name);
            if(backend == null)
            {
               errors.add("allowedBackendNames: backend not found: " + name);
            }
            else if(!(backend instanceof RDBMSBackendMetaData))
            {
               errors.add("allowedBackendNames: backend is not an RDBMS backend: " + name);
            }
         }

         for(Map<String, SqlWorkbenchCredentials> credentials : List.of(nonNull(readOnlyCredentials), nonNull(writeCredentials)))
         {
            for(Map.Entry<String, SqlWorkbenchCredentials> entry : credentials.entrySet())
            {
               if(!(qInstance.getBackend(entry.getKey()) instanceof RDBMSBackendMetaData))
               {
                  errors.add("Credentials configured for unknown or non-RDBMS backend: " + entry.getKey());
               }
               if(entry.getValue() == null || !StringUtils.hasContent(entry.getValue().getUsername()))
               {
                  errors.add("Credentials for backend " + entry.getKey() + " need a username");
               }
            }
         }
      }

      for(String name : List.of("accessPermissionBaseName", "writePermissionName", "sharePermissionName", "adminPermissionBaseName"))
      {
         String value = switch(name)
         {
            case "accessPermissionBaseName" -> accessPermissionBaseName;
            case "writePermissionName" -> writePermissionName;
            case "adminPermissionBaseName" -> adminPermissionBaseName;
            default -> sharePermissionName;
         };
         if(!StringUtils.hasContent(value))
         {
            errors.add(name + " is required");
         }
      }
      if(StringUtils.hasContent(adminPermissionBaseName) && adminPermissionBaseName.equals(accessPermissionBaseName))
      {
         errors.add("adminPermissionBaseName must differ from accessPermissionBaseName (it guards other users' notebooks and the audit)");
      }

      requirePositive(errors, "statementTimeoutSeconds", statementTimeoutSeconds);
      requirePositive(errors, "writeStatementTimeoutSeconds", writeStatementTimeoutSeconds);
      requirePositive(errors, "maxRows", maxRows);
      requirePositive(errors, "defaultPageSize", defaultPageSize);
      requirePositive(errors, "maxPageSize", maxPageSize);
      requirePositive(errors, "fetchSize", fetchSize);
      requirePositive(errors, "maxResultBytes", maxResultBytes);
      requirePositive(errors, "maxCellChars", maxCellChars);
      requirePositive(errors, "maxSqlLength", maxSqlLength);
      requirePositive(errors, "maxStatementsPerRun", maxStatementsPerRun);
      requirePositive(errors, "maxAffectedRowsWithoutAcknowledgement", maxAffectedRowsWithoutAcknowledgement);
      requirePositive(errors, "maxAffectedRows", maxAffectedRows);
      requirePositive(errors, "writeConfirmTtlSeconds", writeConfirmTtlSeconds);
      requirePositive(errors, "maxConcurrentExecutionsPerUser", maxConcurrentExecutionsPerUser);
      requirePositive(errors, "resultCacheTtlSeconds", resultCacheTtlSeconds);
      requirePositive(errors, "resultCacheMaxEntries", resultCacheMaxEntries);
      requirePositive(errors, "schemaCacheTtlSeconds", schemaCacheTtlSeconds);
      requirePositive(errors, "maxSchemaTables", maxSchemaTables);
      requirePositive(errors, "snapshotMaxRows", snapshotMaxRows);
      requirePositive(errors, "notebookVersionRetention", notebookVersionRetention);

      if(defaultPageSize != null && maxPageSize != null && defaultPageSize > maxPageSize)
      {
         errors.add("defaultPageSize must not exceed maxPageSize");
      }
      if(maxAffectedRowsWithoutAcknowledgement != null && maxAffectedRows != null && maxAffectedRowsWithoutAcknowledgement > maxAffectedRows)
      {
         errors.add("maxAffectedRowsWithoutAcknowledgement must not exceed maxAffectedRows");
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void requirePositive(List<String> errors, String name, Number value)
   {
      if(value == null || value.longValue() <= 0)
      {
         errors.add(name + " must be a positive number");
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static <K, V> Map<K, V> nonNull(Map<K, V> map)
   {
      return (map == null ? Map.of() : map);
   }



   /*******************************************************************************
    ** Apply the table name prefix, if any.
    *******************************************************************************/
   public String applyPrefix(String name)
   {
      return (StringUtils.hasContent(tableNamePrefix) ? tableNamePrefix + "_" + name : name);
   }



   /*******************************************************************************
    ** The "has access" permission: the base name plus ".hasAccess".
    *******************************************************************************/
   @JsonIgnore
   public String getAccessPermissionName()
   {
      return (accessPermissionBaseName + ".hasAccess");
   }



   /*******************************************************************************
    ** Permission rules for the app and widget: has-access on the configured
    ** base name. (The tables use getAdminPermissionRules.)
    *******************************************************************************/
   @JsonIgnore
   public QPermissionRules getAccessPermissionRules()
   {
      return (new QPermissionRules().withLevel(PermissionLevel.HAS_ACCESS_PERMISSION).withPermissionBaseName(accessPermissionBaseName));
   }



   /*******************************************************************************
    ** The admin permission: the admin base name plus ".hasAccess".
    *******************************************************************************/
   @JsonIgnore
   public String getAdminPermissionName()
   {
      return (adminPermissionBaseName + ".hasAccess");
   }



   /*******************************************************************************
    ** Permission rules for the admin-only surface: has-access on the admin
    ** base name.
    *******************************************************************************/
   @JsonIgnore
   public QPermissionRules getAdminPermissionRules()
   {
      return (new QPermissionRules().withLevel(PermissionLevel.HAS_ACCESS_PERMISSION).withPermissionBaseName(adminPermissionBaseName));
   }



   /*******************************************************************************
    ** Permission rules for the qbit's tables: QQQ's standard per-table
    ** read / insert / edit / delete permissions ("<tableName>.read", ...).
    ** Which rows a user sees and may change is decided by the tables' record
    ** security locks (owner, shares, admin all-access), not by these.
    *******************************************************************************/
   @JsonIgnore
   public QPermissionRules getTablePermissionRules()
   {
      return (new QPermissionRules().withLevel(PermissionLevel.READ_INSERT_EDIT_DELETE_PERMISSIONS));
   }



   /*******************************************************************************
    ** Security key type whose session value is the user's id; the owner
    ** locks match it.
    *******************************************************************************/
   @JsonIgnore
   public String getUserSecurityKeyTypeName()
   {
      return (applyPrefix("sqlWorkbenchUser"));
   }



   /*******************************************************************************
    ** Security key type whose session values are the user's audience keys
    ** ("user:<id>", "group:<name>", "all"); the share locks match it.
    *******************************************************************************/
   @JsonIgnore
   public String getAudienceSecurityKeyTypeName()
   {
      return (applyPrefix("sqlWorkbenchAudience"));
   }



   /*******************************************************************************
    ** All-access key of the user key type: true for workbench admins.
    *******************************************************************************/
   @JsonIgnore
   public String getAllAccessSecurityKeyName()
   {
      return (applyPrefix("sqlWorkbenchAllAccess"));
   }



   /*******************************************************************************
    ** Every permission an ordinary workbench user needs: the access
    ** permission, read / insert / edit / delete on notebooks and cells, read
    ** on versions, shares and the execution audit, and read / edit (label) /
    ** delete on pinned snapshots. Record locks narrow every one of these to
    ** the user's own (or shared-with-them, for reads) rows.
    *******************************************************************************/
   @JsonIgnore
   public List<String> getUserPermissionNames()
   {
      List<String> names = new ArrayList<>();
      names.add(getAccessPermissionName());
      addTablePermissions(names, "sqlWorkbenchNotebook", "read", "insert", "edit", "delete");
      addTablePermissions(names, "sqlWorkbenchCell", "read", "insert", "edit", "delete");
      addTablePermissions(names, "sqlWorkbenchNotebookVersion", "read");
      addTablePermissions(names, "sqlWorkbenchResultSnapshot", "read", "edit", "delete");
      addTablePermissions(names, "sqlWorkbenchNotebookShare", "read");
      addTablePermissions(names, "sqlWorkbenchExecution", "read");
      return (names);
   }



   /*******************************************************************************
    ** Permissions to share one's notebooks (with users, groups, or the shared
    ** library): the share permission plus insert / edit / delete on the share
    ** table.
    *******************************************************************************/
   @JsonIgnore
   public List<String> getSharePermissionNames()
   {
      List<String> names = new ArrayList<>();
      names.add(sharePermissionName);
      addTablePermissions(names, "sqlWorkbenchNotebookShare", "insert", "edit", "delete");
      return (names);
   }



   /*******************************************************************************
    ** Permissions for a workbench admin: everything a user has plus the admin
    ** permission, which (through SqlWorkbenchSecurity.applyToSession) puts the
    ** all-access key in the session - every user's notebooks and the whole
    ** execution audit. Versions and the audit stay append-only for admins.
    *******************************************************************************/
   @JsonIgnore
   public List<String> getAdminPermissionNames()
   {
      List<String> names = new ArrayList<>(getUserPermissionNames());
      names.add(getAdminPermissionName());
      return (names);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void addTablePermissions(List<String> names, String table, String... suffixes)
   {
      for(String suffix : suffixes)
      {
         names.add(applyPrefix(table) + "." + suffix);
      }
   }



   /*******************************************************************************
    ** QBitConfig: tables without a backend get this one.
    *******************************************************************************/
   @Override
   public String getDefaultBackendNameForTables()
   {
      return (backendName);
   }



   /*******************************************************************************
    ** Add read-only credentials for one target backend.
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withReadOnlyCredentials(String backendName, SqlWorkbenchCredentials credentials)
   {
      if(this.readOnlyCredentials == null)
      {
         this.readOnlyCredentials = new LinkedHashMap<>();
      }
      this.readOnlyCredentials.put(backendName, credentials);
      return (this);
   }



   /*******************************************************************************
    ** Add write credentials for one target backend.
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withWriteCredentials(String backendName, SqlWorkbenchCredentials credentials)
   {
      if(this.writeCredentials == null)
      {
         this.writeCredentials = new LinkedHashMap<>();
      }
      this.writeCredentials.put(backendName, credentials);
      return (this);
   }



   /*******************************************************************************
    ** Instance-scoped runtime, built by the producer.
    *******************************************************************************/
   @JsonIgnore
   public SqlWorkbenchRuntime getRuntime()
   {
      return (runtime);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public void setRuntime(SqlWorkbenchRuntime runtime)
   {
      this.runtime = runtime;
   }



   /*******************************************************************************
    ** Redacting toString: credentials are never printed.
    *******************************************************************************/
   @Override
   public String toString()
   {
      return ("SqlWorkbenchQBitConfig{backendName=" + backendName + ", tableNamePrefix=" + tableNamePrefix + ", allowedBackendNames=" + allowedBackendNames
         + ", readOnlyCredentials=" + nonNull(readOnlyCredentials).keySet() + ", writeCredentials=" + nonNull(writeCredentials).keySet()
         + ", writeModeEnabled=" + writeModeEnabled + "}");
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
   public SqlWorkbenchQBitConfig withBackendName(String backendName)
   {
      this.backendName = backendName;
      return (this);
   }


   /*******************************************************************************
    ** Getter for tableNamePrefix
    *******************************************************************************/
   public String getTableNamePrefix()
   {
      return (this.tableNamePrefix);
   }



   /*******************************************************************************
    ** Setter for tableNamePrefix
    *******************************************************************************/
   public void setTableNamePrefix(String tableNamePrefix)
   {
      this.tableNamePrefix = tableNamePrefix;
   }



   /*******************************************************************************
    ** Fluent setter for tableNamePrefix
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withTableNamePrefix(String tableNamePrefix)
   {
      this.tableNamePrefix = tableNamePrefix;
      return (this);
   }


   /*******************************************************************************
    ** Getter for allowedBackendNames
    *******************************************************************************/
   public List<String> getAllowedBackendNames()
   {
      return (this.allowedBackendNames);
   }



   /*******************************************************************************
    ** Setter for allowedBackendNames
    *******************************************************************************/
   public void setAllowedBackendNames(List<String> allowedBackendNames)
   {
      this.allowedBackendNames = allowedBackendNames;
   }



   /*******************************************************************************
    ** Fluent setter for allowedBackendNames
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withAllowedBackendNames(List<String> allowedBackendNames)
   {
      this.allowedBackendNames = allowedBackendNames;
      return (this);
   }


   /*******************************************************************************
    ** Getter for excludedBackendNames
    *******************************************************************************/
   public List<String> getExcludedBackendNames()
   {
      return (this.excludedBackendNames);
   }



   /*******************************************************************************
    ** Setter for excludedBackendNames
    *******************************************************************************/
   public void setExcludedBackendNames(List<String> excludedBackendNames)
   {
      this.excludedBackendNames = excludedBackendNames;
   }



   /*******************************************************************************
    ** Fluent setter for excludedBackendNames
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withExcludedBackendNames(List<String> excludedBackendNames)
   {
      this.excludedBackendNames = excludedBackendNames;
      return (this);
   }


   /*******************************************************************************
    ** Getter for readOnlyCredentials
    *******************************************************************************/
   @JsonIgnore
   public Map<String, SqlWorkbenchCredentials> getReadOnlyCredentials()
   {
      return (this.readOnlyCredentials);
   }



   /*******************************************************************************
    ** Setter for readOnlyCredentials
    *******************************************************************************/
   public void setReadOnlyCredentials(Map<String, SqlWorkbenchCredentials> readOnlyCredentials)
   {
      this.readOnlyCredentials = readOnlyCredentials;
   }



   /*******************************************************************************
    ** Fluent setter for readOnlyCredentials
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withReadOnlyCredentials(Map<String, SqlWorkbenchCredentials> readOnlyCredentials)
   {
      this.readOnlyCredentials = readOnlyCredentials;
      return (this);
   }


   /*******************************************************************************
    ** Getter for writeCredentials
    *******************************************************************************/
   @JsonIgnore
   public Map<String, SqlWorkbenchCredentials> getWriteCredentials()
   {
      return (this.writeCredentials);
   }



   /*******************************************************************************
    ** Setter for writeCredentials
    *******************************************************************************/
   public void setWriteCredentials(Map<String, SqlWorkbenchCredentials> writeCredentials)
   {
      this.writeCredentials = writeCredentials;
   }



   /*******************************************************************************
    ** Fluent setter for writeCredentials
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withWriteCredentials(Map<String, SqlWorkbenchCredentials> writeCredentials)
   {
      this.writeCredentials = writeCredentials;
      return (this);
   }


   /*******************************************************************************
    ** Getter for useBackendReadOnlyReplica
    *******************************************************************************/
   public Boolean getUseBackendReadOnlyReplica()
   {
      return (this.useBackendReadOnlyReplica);
   }



   /*******************************************************************************
    ** Setter for useBackendReadOnlyReplica
    *******************************************************************************/
   public void setUseBackendReadOnlyReplica(Boolean useBackendReadOnlyReplica)
   {
      this.useBackendReadOnlyReplica = useBackendReadOnlyReplica;
   }



   /*******************************************************************************
    ** Fluent setter for useBackendReadOnlyReplica
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withUseBackendReadOnlyReplica(Boolean useBackendReadOnlyReplica)
   {
      this.useBackendReadOnlyReplica = useBackendReadOnlyReplica;
      return (this);
   }


   /*******************************************************************************
    ** Getter for writeModeEnabled
    *******************************************************************************/
   public Boolean getWriteModeEnabled()
   {
      return (this.writeModeEnabled);
   }



   /*******************************************************************************
    ** Setter for writeModeEnabled
    *******************************************************************************/
   public void setWriteModeEnabled(Boolean writeModeEnabled)
   {
      this.writeModeEnabled = writeModeEnabled;
   }



   /*******************************************************************************
    ** Fluent setter for writeModeEnabled
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withWriteModeEnabled(Boolean writeModeEnabled)
   {
      this.writeModeEnabled = writeModeEnabled;
      return (this);
   }


   /*******************************************************************************
    ** Getter for accessPermissionBaseName
    *******************************************************************************/
   public String getAccessPermissionBaseName()
   {
      return (this.accessPermissionBaseName);
   }



   /*******************************************************************************
    ** Setter for accessPermissionBaseName
    *******************************************************************************/
   public void setAccessPermissionBaseName(String accessPermissionBaseName)
   {
      this.accessPermissionBaseName = accessPermissionBaseName;
   }



   /*******************************************************************************
    ** Fluent setter for accessPermissionBaseName
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withAccessPermissionBaseName(String accessPermissionBaseName)
   {
      this.accessPermissionBaseName = accessPermissionBaseName;
      return (this);
   }


   /*******************************************************************************
    ** Getter for writePermissionName
    *******************************************************************************/
   public String getWritePermissionName()
   {
      return (this.writePermissionName);
   }



   /*******************************************************************************
    ** Setter for writePermissionName
    *******************************************************************************/
   public void setWritePermissionName(String writePermissionName)
   {
      this.writePermissionName = writePermissionName;
   }



   /*******************************************************************************
    ** Fluent setter for writePermissionName
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withWritePermissionName(String writePermissionName)
   {
      this.writePermissionName = writePermissionName;
      return (this);
   }


   /*******************************************************************************
    ** Getter for sharePermissionName
    *******************************************************************************/
   public String getSharePermissionName()
   {
      return (this.sharePermissionName);
   }



   /*******************************************************************************
    ** Setter for sharePermissionName
    *******************************************************************************/
   public void setSharePermissionName(String sharePermissionName)
   {
      this.sharePermissionName = sharePermissionName;
   }



   /*******************************************************************************
    ** Fluent setter for sharePermissionName
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withSharePermissionName(String sharePermissionName)
   {
      this.sharePermissionName = sharePermissionName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for adminPermissionBaseName
    *******************************************************************************/
   public String getAdminPermissionBaseName()
   {
      return (this.adminPermissionBaseName);
   }



   /*******************************************************************************
    ** Setter for adminPermissionBaseName
    *******************************************************************************/
   public void setAdminPermissionBaseName(String adminPermissionBaseName)
   {
      this.adminPermissionBaseName = adminPermissionBaseName;
   }



   /*******************************************************************************
    ** Fluent setter for adminPermissionBaseName
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withAdminPermissionBaseName(String adminPermissionBaseName)
   {
      this.adminPermissionBaseName = adminPermissionBaseName;
      return (this);
   }


   /*******************************************************************************
    ** Getter for statementTimeoutSeconds
    *******************************************************************************/
   public Integer getStatementTimeoutSeconds()
   {
      return (this.statementTimeoutSeconds);
   }



   /*******************************************************************************
    ** Setter for statementTimeoutSeconds
    *******************************************************************************/
   public void setStatementTimeoutSeconds(Integer statementTimeoutSeconds)
   {
      this.statementTimeoutSeconds = statementTimeoutSeconds;
   }



   /*******************************************************************************
    ** Fluent setter for statementTimeoutSeconds
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withStatementTimeoutSeconds(Integer statementTimeoutSeconds)
   {
      this.statementTimeoutSeconds = statementTimeoutSeconds;
      return (this);
   }


   /*******************************************************************************
    ** Getter for writeStatementTimeoutSeconds
    *******************************************************************************/
   public Integer getWriteStatementTimeoutSeconds()
   {
      return (this.writeStatementTimeoutSeconds);
   }



   /*******************************************************************************
    ** Setter for writeStatementTimeoutSeconds
    *******************************************************************************/
   public void setWriteStatementTimeoutSeconds(Integer writeStatementTimeoutSeconds)
   {
      this.writeStatementTimeoutSeconds = writeStatementTimeoutSeconds;
   }



   /*******************************************************************************
    ** Fluent setter for writeStatementTimeoutSeconds
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withWriteStatementTimeoutSeconds(Integer writeStatementTimeoutSeconds)
   {
      this.writeStatementTimeoutSeconds = writeStatementTimeoutSeconds;
      return (this);
   }


   /*******************************************************************************
    ** Getter for maxRows
    *******************************************************************************/
   public Integer getMaxRows()
   {
      return (this.maxRows);
   }



   /*******************************************************************************
    ** Setter for maxRows
    *******************************************************************************/
   public void setMaxRows(Integer maxRows)
   {
      this.maxRows = maxRows;
   }



   /*******************************************************************************
    ** Fluent setter for maxRows
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withMaxRows(Integer maxRows)
   {
      this.maxRows = maxRows;
      return (this);
   }


   /*******************************************************************************
    ** Getter for defaultPageSize
    *******************************************************************************/
   public Integer getDefaultPageSize()
   {
      return (this.defaultPageSize);
   }



   /*******************************************************************************
    ** Setter for defaultPageSize
    *******************************************************************************/
   public void setDefaultPageSize(Integer defaultPageSize)
   {
      this.defaultPageSize = defaultPageSize;
   }



   /*******************************************************************************
    ** Fluent setter for defaultPageSize
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withDefaultPageSize(Integer defaultPageSize)
   {
      this.defaultPageSize = defaultPageSize;
      return (this);
   }


   /*******************************************************************************
    ** Getter for maxPageSize
    *******************************************************************************/
   public Integer getMaxPageSize()
   {
      return (this.maxPageSize);
   }



   /*******************************************************************************
    ** Setter for maxPageSize
    *******************************************************************************/
   public void setMaxPageSize(Integer maxPageSize)
   {
      this.maxPageSize = maxPageSize;
   }



   /*******************************************************************************
    ** Fluent setter for maxPageSize
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withMaxPageSize(Integer maxPageSize)
   {
      this.maxPageSize = maxPageSize;
      return (this);
   }


   /*******************************************************************************
    ** Getter for fetchSize
    *******************************************************************************/
   public Integer getFetchSize()
   {
      return (this.fetchSize);
   }



   /*******************************************************************************
    ** Setter for fetchSize
    *******************************************************************************/
   public void setFetchSize(Integer fetchSize)
   {
      this.fetchSize = fetchSize;
   }



   /*******************************************************************************
    ** Fluent setter for fetchSize
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withFetchSize(Integer fetchSize)
   {
      this.fetchSize = fetchSize;
      return (this);
   }


   /*******************************************************************************
    ** Getter for maxResultBytes
    *******************************************************************************/
   public Long getMaxResultBytes()
   {
      return (this.maxResultBytes);
   }



   /*******************************************************************************
    ** Setter for maxResultBytes
    *******************************************************************************/
   public void setMaxResultBytes(Long maxResultBytes)
   {
      this.maxResultBytes = maxResultBytes;
   }



   /*******************************************************************************
    ** Fluent setter for maxResultBytes
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withMaxResultBytes(Long maxResultBytes)
   {
      this.maxResultBytes = maxResultBytes;
      return (this);
   }


   /*******************************************************************************
    ** Getter for maxCellChars
    *******************************************************************************/
   public Integer getMaxCellChars()
   {
      return (this.maxCellChars);
   }



   /*******************************************************************************
    ** Setter for maxCellChars
    *******************************************************************************/
   public void setMaxCellChars(Integer maxCellChars)
   {
      this.maxCellChars = maxCellChars;
   }



   /*******************************************************************************
    ** Fluent setter for maxCellChars
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withMaxCellChars(Integer maxCellChars)
   {
      this.maxCellChars = maxCellChars;
      return (this);
   }


   /*******************************************************************************
    ** Getter for maxSqlLength
    *******************************************************************************/
   public Integer getMaxSqlLength()
   {
      return (this.maxSqlLength);
   }



   /*******************************************************************************
    ** Setter for maxSqlLength
    *******************************************************************************/
   public void setMaxSqlLength(Integer maxSqlLength)
   {
      this.maxSqlLength = maxSqlLength;
   }



   /*******************************************************************************
    ** Fluent setter for maxSqlLength
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withMaxSqlLength(Integer maxSqlLength)
   {
      this.maxSqlLength = maxSqlLength;
      return (this);
   }


   /*******************************************************************************
    ** Getter for maxStatementsPerRun
    *******************************************************************************/
   public Integer getMaxStatementsPerRun()
   {
      return (this.maxStatementsPerRun);
   }



   /*******************************************************************************
    ** Setter for maxStatementsPerRun
    *******************************************************************************/
   public void setMaxStatementsPerRun(Integer maxStatementsPerRun)
   {
      this.maxStatementsPerRun = maxStatementsPerRun;
   }



   /*******************************************************************************
    ** Fluent setter for maxStatementsPerRun
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withMaxStatementsPerRun(Integer maxStatementsPerRun)
   {
      this.maxStatementsPerRun = maxStatementsPerRun;
      return (this);
   }


   /*******************************************************************************
    ** Getter for maxAffectedRowsWithoutAcknowledgement
    *******************************************************************************/
   public Integer getMaxAffectedRowsWithoutAcknowledgement()
   {
      return (this.maxAffectedRowsWithoutAcknowledgement);
   }



   /*******************************************************************************
    ** Setter for maxAffectedRowsWithoutAcknowledgement
    *******************************************************************************/
   public void setMaxAffectedRowsWithoutAcknowledgement(Integer maxAffectedRowsWithoutAcknowledgement)
   {
      this.maxAffectedRowsWithoutAcknowledgement = maxAffectedRowsWithoutAcknowledgement;
   }



   /*******************************************************************************
    ** Fluent setter for maxAffectedRowsWithoutAcknowledgement
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withMaxAffectedRowsWithoutAcknowledgement(Integer maxAffectedRowsWithoutAcknowledgement)
   {
      this.maxAffectedRowsWithoutAcknowledgement = maxAffectedRowsWithoutAcknowledgement;
      return (this);
   }


   /*******************************************************************************
    ** Getter for maxAffectedRows
    *******************************************************************************/
   public Integer getMaxAffectedRows()
   {
      return (this.maxAffectedRows);
   }



   /*******************************************************************************
    ** Setter for maxAffectedRows
    *******************************************************************************/
   public void setMaxAffectedRows(Integer maxAffectedRows)
   {
      this.maxAffectedRows = maxAffectedRows;
   }



   /*******************************************************************************
    ** Fluent setter for maxAffectedRows
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withMaxAffectedRows(Integer maxAffectedRows)
   {
      this.maxAffectedRows = maxAffectedRows;
      return (this);
   }


   /*******************************************************************************
    ** Getter for writeConfirmTtlSeconds
    *******************************************************************************/
   public Integer getWriteConfirmTtlSeconds()
   {
      return (this.writeConfirmTtlSeconds);
   }



   /*******************************************************************************
    ** Setter for writeConfirmTtlSeconds
    *******************************************************************************/
   public void setWriteConfirmTtlSeconds(Integer writeConfirmTtlSeconds)
   {
      this.writeConfirmTtlSeconds = writeConfirmTtlSeconds;
   }



   /*******************************************************************************
    ** Fluent setter for writeConfirmTtlSeconds
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withWriteConfirmTtlSeconds(Integer writeConfirmTtlSeconds)
   {
      this.writeConfirmTtlSeconds = writeConfirmTtlSeconds;
      return (this);
   }


   /*******************************************************************************
    ** Getter for maxConcurrentExecutionsPerUser
    *******************************************************************************/
   public Integer getMaxConcurrentExecutionsPerUser()
   {
      return (this.maxConcurrentExecutionsPerUser);
   }



   /*******************************************************************************
    ** Setter for maxConcurrentExecutionsPerUser
    *******************************************************************************/
   public void setMaxConcurrentExecutionsPerUser(Integer maxConcurrentExecutionsPerUser)
   {
      this.maxConcurrentExecutionsPerUser = maxConcurrentExecutionsPerUser;
   }



   /*******************************************************************************
    ** Fluent setter for maxConcurrentExecutionsPerUser
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withMaxConcurrentExecutionsPerUser(Integer maxConcurrentExecutionsPerUser)
   {
      this.maxConcurrentExecutionsPerUser = maxConcurrentExecutionsPerUser;
      return (this);
   }


   /*******************************************************************************
    ** Getter for resultCacheTtlSeconds
    *******************************************************************************/
   public Integer getResultCacheTtlSeconds()
   {
      return (this.resultCacheTtlSeconds);
   }



   /*******************************************************************************
    ** Setter for resultCacheTtlSeconds
    *******************************************************************************/
   public void setResultCacheTtlSeconds(Integer resultCacheTtlSeconds)
   {
      this.resultCacheTtlSeconds = resultCacheTtlSeconds;
   }



   /*******************************************************************************
    ** Fluent setter for resultCacheTtlSeconds
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withResultCacheTtlSeconds(Integer resultCacheTtlSeconds)
   {
      this.resultCacheTtlSeconds = resultCacheTtlSeconds;
      return (this);
   }


   /*******************************************************************************
    ** Getter for resultCacheMaxEntries
    *******************************************************************************/
   public Integer getResultCacheMaxEntries()
   {
      return (this.resultCacheMaxEntries);
   }



   /*******************************************************************************
    ** Setter for resultCacheMaxEntries
    *******************************************************************************/
   public void setResultCacheMaxEntries(Integer resultCacheMaxEntries)
   {
      this.resultCacheMaxEntries = resultCacheMaxEntries;
   }



   /*******************************************************************************
    ** Fluent setter for resultCacheMaxEntries
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withResultCacheMaxEntries(Integer resultCacheMaxEntries)
   {
      this.resultCacheMaxEntries = resultCacheMaxEntries;
      return (this);
   }


   /*******************************************************************************
    ** Getter for schemaCacheTtlSeconds
    *******************************************************************************/
   public Integer getSchemaCacheTtlSeconds()
   {
      return (this.schemaCacheTtlSeconds);
   }



   /*******************************************************************************
    ** Setter for schemaCacheTtlSeconds
    *******************************************************************************/
   public void setSchemaCacheTtlSeconds(Integer schemaCacheTtlSeconds)
   {
      this.schemaCacheTtlSeconds = schemaCacheTtlSeconds;
   }



   /*******************************************************************************
    ** Fluent setter for schemaCacheTtlSeconds
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withSchemaCacheTtlSeconds(Integer schemaCacheTtlSeconds)
   {
      this.schemaCacheTtlSeconds = schemaCacheTtlSeconds;
      return (this);
   }


   /*******************************************************************************
    ** Getter for maxSchemaTables
    *******************************************************************************/
   public Integer getMaxSchemaTables()
   {
      return (this.maxSchemaTables);
   }



   /*******************************************************************************
    ** Setter for maxSchemaTables
    *******************************************************************************/
   public void setMaxSchemaTables(Integer maxSchemaTables)
   {
      this.maxSchemaTables = maxSchemaTables;
   }



   /*******************************************************************************
    ** Fluent setter for maxSchemaTables
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withMaxSchemaTables(Integer maxSchemaTables)
   {
      this.maxSchemaTables = maxSchemaTables;
      return (this);
   }


   /*******************************************************************************
    ** Getter for snapshotMaxRows
    *******************************************************************************/
   public Integer getSnapshotMaxRows()
   {
      return (this.snapshotMaxRows);
   }



   /*******************************************************************************
    ** Setter for snapshotMaxRows
    *******************************************************************************/
   public void setSnapshotMaxRows(Integer snapshotMaxRows)
   {
      this.snapshotMaxRows = snapshotMaxRows;
   }



   /*******************************************************************************
    ** Fluent setter for snapshotMaxRows
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withSnapshotMaxRows(Integer snapshotMaxRows)
   {
      this.snapshotMaxRows = snapshotMaxRows;
      return (this);
   }


   /*******************************************************************************
    ** Getter for notebookVersionRetention
    *******************************************************************************/
   public Integer getNotebookVersionRetention()
   {
      return (this.notebookVersionRetention);
   }



   /*******************************************************************************
    ** Setter for notebookVersionRetention
    *******************************************************************************/
   public void setNotebookVersionRetention(Integer notebookVersionRetention)
   {
      this.notebookVersionRetention = notebookVersionRetention;
   }



   /*******************************************************************************
    ** Fluent setter for notebookVersionRetention
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withNotebookVersionRetention(Integer notebookVersionRetention)
   {
      this.notebookVersionRetention = notebookVersionRetention;
      return (this);
   }


   /*******************************************************************************
    ** Getter for registerRoutes
    *******************************************************************************/
   public Boolean getRegisterRoutes()
   {
      return (this.registerRoutes);
   }



   /*******************************************************************************
    ** Setter for registerRoutes
    *******************************************************************************/
   public void setRegisterRoutes(Boolean registerRoutes)
   {
      this.registerRoutes = registerRoutes;
   }



   /*******************************************************************************
    ** Fluent setter for registerRoutes
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withRegisterRoutes(Boolean registerRoutes)
   {
      this.registerRoutes = registerRoutes;
      return (this);
   }


   /*******************************************************************************
    ** Getter for tableMetaDataCustomizer
    *******************************************************************************/
   @Override
   @JsonIgnore
   public MetaDataCustomizerInterface<QTableMetaData> getTableMetaDataCustomizer()
   {
      return (this.tableMetaDataCustomizer);
   }



   /*******************************************************************************
    ** Setter for tableMetaDataCustomizer
    *******************************************************************************/
   public void setTableMetaDataCustomizer(MetaDataCustomizerInterface<QTableMetaData> tableMetaDataCustomizer)
   {
      this.tableMetaDataCustomizer = tableMetaDataCustomizer;
   }



   /*******************************************************************************
    ** Fluent setter for tableMetaDataCustomizer
    *******************************************************************************/
   public SqlWorkbenchQBitConfig withTableMetaDataCustomizer(MetaDataCustomizerInterface<QTableMetaData> tableMetaDataCustomizer)
   {
      this.tableMetaDataCustomizer = tableMetaDataCustomizer;
      return (this);
   }

}
