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


import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QNotFoundException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;
import com.kingsrook.qbits.sqlworkbench.engine.ResultCache;
import com.kingsrook.qbits.sqlworkbench.engine.RunningStatements;
import com.kingsrook.qbits.sqlworkbench.engine.WritePreviewTokens;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;


/*******************************************************************************
 ** Instance-scoped live state of the SQL Workbench: result cache, running
 ** statements, write-preview tokens, schema cache, and the derived backend
 ** meta-data used for dedicated credentials. Held (transient) on the config;
 ** resolved from the QContext's instance with {@link #get()}.
 *******************************************************************************/
public class SqlWorkbenchRuntime
{
   private final SqlWorkbenchQBitConfig config;
   private final Clock                  clock;
   private final ResultCache            resultCache;
   private final RunningStatements      runningStatements = new RunningStatements();
   private final WritePreviewTokens     writePreviewTokens;

   private final Map<String, CachedSchema>         schemaCache     = new ConcurrentHashMap<>();
   private final Map<String, RDBMSBackendMetaData> derivedBackends = new ConcurrentHashMap<>();



   /*******************************************************************************
    ** A cached schema description.
    **
    ** @param schema   the response body
    ** @param cachedAt when it was read
    *******************************************************************************/
   public record CachedSchema(Map<String, Object> schema, Instant cachedAt)
   {
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public SqlWorkbenchRuntime(SqlWorkbenchQBitConfig config)
   {
      this(config, Clock.systemUTC());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public SqlWorkbenchRuntime(SqlWorkbenchQBitConfig config, Clock clock)
   {
      this.config = config;
      this.clock = clock;
      this.resultCache = new ResultCache(config.getResultCacheMaxEntries(), config.getResultCacheTtlSeconds(), clock);
      this.writePreviewTokens = new WritePreviewTokens(config.getWriteConfirmTtlSeconds(), clock);
   }



   /*******************************************************************************
    ** The runtime of the SQL Workbench qbit in the QContext's instance.
    *******************************************************************************/
   public static SqlWorkbenchRuntime get() throws QNotFoundException
   {
      return (get(QContext.getQInstance()));
   }



   /*******************************************************************************
    ** The runtime of the SQL Workbench qbit in an instance.
    *******************************************************************************/
   public static SqlWorkbenchRuntime get(QInstance qInstance) throws QNotFoundException
   {
      if(qInstance != null && qInstance.getQBits() != null)
      {
         for(QBitMetaData qBit : qInstance.getQBits().values())
         {
            if(qBit.getConfig() instanceof SqlWorkbenchQBitConfig config && config.getRuntime() != null)
            {
               return (config.getRuntime());
            }
         }
      }
      throw (new QNotFoundException("The SQL Workbench is not installed in this application."));
   }



   /*******************************************************************************
    ** Cached schema for a backend, if still fresh.
    *******************************************************************************/
   public CachedSchema getCachedSchema(String backendName)
   {
      CachedSchema cached = schemaCache.get(backendName);
      if(cached != null && cached.cachedAt().plusSeconds(config.getSchemaCacheTtlSeconds()).isAfter(clock.instant()))
      {
         return (cached);
      }
      return (null);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public void putCachedSchema(String backendName, Map<String, Object> schema)
   {
      schemaCache.put(backendName, new CachedSchema(schema, clock.instant()));
   }



   /*******************************************************************************
    ** Release in-memory state (cache, tokens). Running statements finish on
    ** their own.
    *******************************************************************************/
   public void close()
   {
      resultCache.clear();
      schemaCache.clear();
   }



   /*******************************************************************************
    ** Getter for config
    *******************************************************************************/
   public SqlWorkbenchQBitConfig getConfig()
   {
      return (config);
   }



   /*******************************************************************************
    ** Getter for clock
    *******************************************************************************/
   public Clock getClock()
   {
      return (clock);
   }



   /*******************************************************************************
    ** Getter for resultCache
    *******************************************************************************/
   public ResultCache getResultCache()
   {
      return (resultCache);
   }



   /*******************************************************************************
    ** Getter for runningStatements
    *******************************************************************************/
   public RunningStatements getRunningStatements()
   {
      return (runningStatements);
   }



   /*******************************************************************************
    ** Getter for writePreviewTokens
    *******************************************************************************/
   public WritePreviewTokens getWritePreviewTokens()
   {
      return (writePreviewTokens);
   }



   /*******************************************************************************
    ** Derived backend meta-data (dedicated credentials), by derived name.
    *******************************************************************************/
   public Map<String, RDBMSBackendMetaData> getDerivedBackends()
   {
      return (derivedBackends);
   }

}
