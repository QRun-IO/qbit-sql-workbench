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


import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducerMultiOutput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.help.bundles.HelpBundles;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaDataProducer;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.sqlworkbench.metadata.SqlWorkbenchHelp;
import com.kingsrook.qbits.sqlworkbench.metadata.SqlWorkbenchAppMetaDataProducer;
import com.kingsrook.qbits.sqlworkbench.metadata.SqlWorkbenchTableMetaDataHelper;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Entry point of the SQL Workbench QBit, a QQQ 4.1 QBitMetaDataProducer.
 **
 ** The framework's default produce() registers QBitMetaData
 ** (com.kingsrook.qbits:sql-workbench), validates the config, runs the
 ** component producers in the metadata package (five tables, notebook PVS,
 ** SQL_WORKBENCH widget, sqlWorkbench app), applies the config's table
 ** customizer and default backend, stamps sourceQBitName, then calls
 ** {@link #postProduceActions}, which builds the runtime and registers the
 ** /qqq/v1/sql-workbench UI API extension.
 **
 ** Host usage:
 **   new SqlWorkbenchQBitProducer().withQBitConfig(config).produce(qInstance);
 *******************************************************************************/
public class SqlWorkbenchQBitProducer implements QBitMetaDataProducer<SqlWorkbenchQBitConfig>
{
   private static final QLogger LOG = QLogger.getLogger(SqlWorkbenchQBitProducer.class);

   public static final String GROUP_ID    = "com.kingsrook.qbits";
   public static final String ARTIFACT_ID = "sql-workbench";
   public static final String APP_NAME    = SqlWorkbenchAppMetaDataProducer.APP_NAME;

   private static final String JAVALIN_CLASS = "com.kingsrook.qqq.middleware.javalin.QJavalinMetaData";
   private static final String VERSION       = loadVersion();

   private SqlWorkbenchQBitConfig qBitConfig;
   private String                 namespace;



   /*******************************************************************************
    ** Fluent setter for the config.
    *******************************************************************************/
   public SqlWorkbenchQBitProducer withQBitConfig(SqlWorkbenchQBitConfig config)
   {
      this.qBitConfig = config;
      return (this);
   }



   /*******************************************************************************
    ** Setter for the config.
    *******************************************************************************/
   public void setQBitConfig(SqlWorkbenchQBitConfig config)
   {
      this.qBitConfig = config;
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public SqlWorkbenchQBitConfig getQBitConfig()
   {
      return (this.qBitConfig);
   }



   /*******************************************************************************
    ** Namespace for this QBit instance; also the default tableNamePrefix.
    *******************************************************************************/
   public SqlWorkbenchQBitProducer withNamespace(String namespace)
   {
      this.namespace = namespace;
      return (this);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getNamespace()
   {
      return (this.namespace);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public QBitMetaData getQBitMetaData()
   {
      return (new QBitMetaData()
         .withGroupId(GROUP_ID)
         .withArtifactId(ARTIFACT_ID)
         .withVersion(VERSION)
         .withNamespace(getNamespace())
         .withConfig(getQBitConfig()));
   }



   /*******************************************************************************
    ** Component producers live in the metadata package only (the api package
    ** needs qqq-middleware-javalin, which is optional).
    *******************************************************************************/
   @Override
   public String getPackageNameForFindingMetaDataProducers()
   {
      return (SqlWorkbenchTableMetaDataHelper.class.getPackageName());
   }



   /*******************************************************************************
    ** A producer found by package scanning has no config; skip it.
    *******************************************************************************/
   @Override
   public boolean isEnabled()
   {
      return (qBitConfig != null);
   }



   /*******************************************************************************
    ** Produce and add everything to the instance. Returns an empty output so a
    ** host that also calls addSelfToInstance on the result adds nothing twice.
    *******************************************************************************/
   @Override
   public MetaDataProducerMultiOutput produce(QInstance qInstance) throws QException
   {
      if(qBitConfig == null)
      {
         throw (new QException("SqlWorkbenchQBit configuration validation failed: no config was supplied (call withQBitConfig)"));
      }

      if(StringUtils.hasContent(namespace) && !StringUtils.hasContent(qBitConfig.getTableNamePrefix()))
      {
         qBitConfig.setTableNamePrefix(namespace);
      }

      List<String> errors = new ArrayList<>();
      qBitConfig.validate(qInstance, errors);
      if(!errors.isEmpty())
      {
         throw (new QException("SqlWorkbenchQBit configuration validation failed: " + String.join("; ", errors)));
      }

      MetaDataProducerMultiOutput output = QBitMetaDataProducer.super.produce(qInstance);
      output.addSelfToInstance(qInstance);

      /////////////////////////////////////////////////////////////////////////
      // after the tables and app are in the instance: fill their empty help //
      /////////////////////////////////////////////////////////////////////////
      SqlWorkbenchHelp.apply(qInstance, qBitConfig);

      // Registration is metadata-only; core guards startup sync when helpContent is absent.
      HelpBundles.register(qInstance, "help/qbit-sql-workbench.yaml");
      return (new MetaDataProducerMultiOutput());
   }



   /*******************************************************************************
    ** Build the runtime and register the UI API extension.
    *******************************************************************************/
   @Override
   public void postProduceActions(MetaDataProducerMultiOutput output, QInstance qInstance) throws QException
   {
      SqlWorkbenchRuntime previous = qBitConfig.getRuntime();
      if(previous != null)
      {
         previous.close();
      }
      qBitConfig.setRuntime(new SqlWorkbenchRuntime(qBitConfig));

      boolean routes = false;
      if(Boolean.TRUE.equals(qBitConfig.getRegisterRoutes()) && isJavalinAvailable())
      {
         com.kingsrook.qbits.sqlworkbench.api.SqlWorkbenchEndpoints.register(qInstance);
         routes = true;
      }

      LOG.info("SQL Workbench QBit produced", logPair("version", VERSION), logPair("backendName", qBitConfig.getBackendName()),
         logPair("routes", routes), logPair("writeModeEnabled", qBitConfig.getWriteModeEnabled()));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static boolean isJavalinAvailable()
   {
      try
      {
         Class.forName(JAVALIN_CLASS, false, SqlWorkbenchQBitProducer.class.getClassLoader());
         return (true);
      }
      catch(ClassNotFoundException | LinkageError e)
      {
         return (false);
      }
   }



   /*******************************************************************************
    ** The artifact version, from the Maven-filtered sql-workbench.properties.
    *******************************************************************************/
   static String loadVersion()
   {
      try(InputStream inputStream = SqlWorkbenchQBitProducer.class.getClassLoader().getResourceAsStream("sql-workbench.properties"))
      {
         if(inputStream != null)
         {
            Properties properties = new Properties();
            properties.load(inputStream);
            String version = properties.getProperty("version");
            if(StringUtils.hasContent(version) && !version.contains("${"))
            {
               return (version);
            }
         }
      }
      catch(Exception e)
      {
         LOG.debug("Could not read sql-workbench.properties", e);
      }
      return ("unknown");
   }



   /*******************************************************************************
    ** Getter for the artifact version.
    *******************************************************************************/
   public static String getVersion()
   {
      return (VERSION);
   }

}
