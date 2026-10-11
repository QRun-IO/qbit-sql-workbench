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


import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.exceptions.QNotFoundException;
import com.kingsrook.qqq.backend.core.instances.QMetaDataVariableInterpreter;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchCredentials;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchRuntime;
import com.kingsrook.qbits.sqlworkbench.sql.SqlDialect;
import com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;


/*******************************************************************************
 ** Resolves the workbench's target backends (the instance's RDBMS backends,
 ** filtered by the config's allow / deny lists) and opens connections to
 ** them through QQQ's ConnectionManager.
 **
 ** Connection source, read-only sessions: readOnlyCredentials for the backend
 ** (a derived backend meta-data with its own pool), else the backend's
 ** readOnlyBackendMetaData when useBackendReadOnlyReplica, else the backend.
 ** Write sessions: writeCredentials for the backend, else the backend.
 *******************************************************************************/
public class WorkbenchBackends
{
   public static final String READ_ONLY_SUFFIX = "__sqlWorkbenchReadOnly";
   public static final String WRITE_SUFFIX     = "__sqlWorkbenchWrite";

   private final QInstance           qInstance;
   private final SqlWorkbenchRuntime runtime;



   /*******************************************************************************
    ** A target backend.
    **
    ** @param backend  the instance's backend meta-data
    ** @param dialect  its dialect
    *******************************************************************************/
   public record Target(RDBMSBackendMetaData backend, SqlDialect dialect)
   {

      /*******************************************************************************
       ** The backend's name.
       *******************************************************************************/
      public String name()
      {
         return (backend.getName());
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public WorkbenchBackends(QInstance qInstance, SqlWorkbenchRuntime runtime)
   {
      this.qInstance = qInstance;
      this.runtime = runtime;
   }



   /*******************************************************************************
    ** Every target backend, in instance order.
    *******************************************************************************/
   public List<Target> listTargets()
   {
      List<Target> targets = new ArrayList<>();
      for(QBackendMetaData backend : qInstance.getBackends().values())
      {
         if(backend instanceof RDBMSBackendMetaData rdbms && isAllowed(rdbms.getName()))
         {
            targets.add(new Target(rdbms, SqlDialect.fromVendor(rdbms.getVendor())));
         }
      }
      return (targets);
   }



   /*******************************************************************************
    ** One target, or QNotFoundException for an unknown, non-RDBMS or excluded
    ** backend.
    *******************************************************************************/
   public Target getTarget(String backendName) throws QNotFoundException
   {
      QBackendMetaData backend = StringUtils.hasContent(backendName) ? qInstance.getBackend(backendName) : null;
      if(backend instanceof RDBMSBackendMetaData rdbms && isAllowed(backendName))
      {
         return (new Target(rdbms, SqlDialect.fromVendor(rdbms.getVendor())));
      }
      throw (new QNotFoundException("Unknown SQL Workbench backend: " + backendName));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private boolean isAllowed(String backendName)
   {
      SqlWorkbenchQBitConfig config = runtime.getConfig();
      if(backendName == null || backendName.endsWith(READ_ONLY_SUFFIX) || backendName.endsWith(WRITE_SUFFIX))
      {
         return (false);
      }
      if(config.getExcludedBackendNames() != null && config.getExcludedBackendNames().contains(backendName))
      {
         return (false);
      }
      return (config.getAllowedBackendNames() == null || config.getAllowedBackendNames().contains(backendName));
   }



   /*******************************************************************************
    ** Whether read-only sessions on this target use dedicated credentials.
    *******************************************************************************/
   public boolean hasReadOnlyCredentials(Target target)
   {
      Map<String, SqlWorkbenchCredentials> credentials = runtime.getConfig().getReadOnlyCredentials();
      return (credentials != null && credentials.containsKey(target.name()));
   }



   /*******************************************************************************
    ** The backend meta-data a session connects with.
    *******************************************************************************/
   public RDBMSBackendMetaData connectionSource(Target target, boolean readOnly)
   {
      SqlWorkbenchQBitConfig config = runtime.getConfig();
      if(readOnly)
      {
         SqlWorkbenchCredentials credentials = config.getReadOnlyCredentials() == null ? null : config.getReadOnlyCredentials().get(target.name());
         if(credentials != null)
         {
            return (derive(target.backend(), credentials, READ_ONLY_SUFFIX));
         }
         if(Boolean.TRUE.equals(config.getUseBackendReadOnlyReplica()) && target.backend().getReadOnlyBackendMetaData() != null)
         {
            return (target.backend().getReadOnlyBackendMetaData());
         }
         return (target.backend());
      }

      SqlWorkbenchCredentials credentials = config.getWriteCredentials() == null ? null : config.getWriteCredentials().get(target.name());
      return (credentials == null ? target.backend() : derive(target.backend(), credentials, WRITE_SUFFIX));
   }



   /*******************************************************************************
    ** Open a connection for a session.
    *******************************************************************************/
   public Connection openConnection(Target target, boolean readOnly) throws SQLException
   {
      try
      {
         return (ConnectionManager.getConnection(connectionSource(target, readOnly)));
      }
      catch(SQLException e)
      {
         throw (new ConnectionFailedException(target.name(), e));
      }
   }



   /*******************************************************************************
    ** The connection to a target could not be opened. Its driver message may
    ** carry hosts, user names or URL details, so callers show a generic
    ** message (getMessage) and log the cause server-side.
    *******************************************************************************/
   public static class ConnectionFailedException extends SQLException
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      public ConnectionFailedException(String backendName, SQLException cause)
      {
         super("Could not connect to backend " + backendName + "; see the server log.", (String) null, cause);
      }
   }



   /*******************************************************************************
    ** A copy of a backend's connection settings with other credentials, under a
    ** derived name (so it gets its own connection provider / pool). Built once
    ** per backend and suffix.
    *******************************************************************************/
   RDBMSBackendMetaData derive(RDBMSBackendMetaData original, SqlWorkbenchCredentials credentials, String suffix)
   {
      String name = original.getName() + suffix;
      return (runtime.getDerivedBackends().computeIfAbsent(name, n ->
      {
         QMetaDataVariableInterpreter interpreter = new QMetaDataVariableInterpreter();
         RDBMSBackendMetaData derived = new RDBMSBackendMetaData();
         derived.setName(n);
         derived.setVendor(original.getVendor());
         derived.setHostName(original.getHostName());
         derived.setPort(original.getPort());
         derived.setDatabaseName(original.getDatabaseName());
         derived.setJdbcUrl(StringUtils.hasContent(credentials.getJdbcUrl()) ? interpreter.interpret(credentials.getJdbcUrl()) : ConnectionManager.getJdbcUrl(original));
         derived.setJdbcDriverClassName(ConnectionManager.getJdbcDriverClassName(original));
         derived.setConnectionProvider(original.getConnectionProvider());
         derived.setConnectionPoolSettings(original.getConnectionPoolSettings());
         derived.setQueriesForNewConnections(original.getQueriesForNewConnections());
         derived.setUsername(interpreter.interpret(credentials.getUsername()));
         derived.setPassword(interpreter.interpret(credentials.getPassword()));
         return (derived);
      }));
   }

}
