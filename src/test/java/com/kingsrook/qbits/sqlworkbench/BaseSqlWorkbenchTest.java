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
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.instances.QInstanceEnricher;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.producers.MetaDataCustomizerInterface;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QUser;
import com.kingsrook.qbits.sqlworkbench.engine.Caller;
import com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSTableBackendDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;


/*******************************************************************************
 ** Test base: an H2 (MODE=MySQL) backend "h2" holding the qbit's tables
 ** (snake_case, as hosts map them) and a "person" table, the qbit produced
 ** into a QInstance, and helpers for sessions and callers.
 *******************************************************************************/
public class BaseSqlWorkbenchTest
{
   public static final String BACKEND_NAME = "h2";
   public static final String READER       = "reader-1";
   public static final String WRITER       = "writer-1";
   public static final String OTHER_READER = "reader-2";

   protected QInstance              qInstance;
   protected SqlWorkbenchQBitConfig config;



   /*******************************************************************************
    ** The H2 backend.
    *******************************************************************************/
   public static RDBMSBackendMetaData defineH2Backend(String name)
   {
      RDBMSBackendMetaData backend = new RDBMSBackendMetaData()
         .withName(name)
         .withVendor("h2")
         .withHostName("mem")
         .withDatabaseName("sqlwb_" + name)
         .withUsername("sa");
      backend.setJdbcUrl(ConnectionManager.getJdbcUrl(backend) + ";DATABASE_TO_UPPER=FALSE");
      return (backend);
   }



   /*******************************************************************************
    ** The snake_case customizer hosts use for the qbit's tables.
    *******************************************************************************/
   public static class SnakeCaseCustomizer implements MetaDataCustomizerInterface<QTableMetaData>
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public QTableMetaData customizeMetaData(QInstance qInstance, QTableMetaData table)
      {
         table.setBackendDetails(new RDBMSTableBackendDetails().withTableName(QInstanceEnricher.inferBackendName(table.getName())));
         QInstanceEnricher.setInferredFieldBackendNames(table);
         return (table);
      }
   }



   /*******************************************************************************
    ** A default config for tests.
    *******************************************************************************/
   protected SqlWorkbenchQBitConfig defineConfig()
   {
      return (new SqlWorkbenchQBitConfig()
         .withBackendName(BACKEND_NAME)
         .withTableMetaDataCustomizer(new SnakeCaseCustomizer()));
   }



   /*******************************************************************************
    ** Build the instance, prime the database, init the context as a reader.
    *******************************************************************************/
   @BeforeEach
   void baseSetUp() throws Exception
   {
      qInstance = new QInstance();
      qInstance.addBackend(defineH2Backend(BACKEND_NAME));
      qInstance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withName("mock").withType(QAuthenticationType.MOCK)
         .withCustomizer(new QCodeReference(TestSessionCustomizer.class)));

      QTableMetaData person = new QTableMetaData()
         .withName("person")
         .withLabel("Person")
         .withBackendName(BACKEND_NAME)
         .withPrimaryKeyField("id")
         .withBackendDetails(new RDBMSTableBackendDetails().withTableName("person"))
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("firstName", QFieldType.STRING).withBackendName("first_name").withLabel("First Name"))
         .withField(new QFieldMetaData("lastName", QFieldType.STRING).withBackendName("last_name"));
      qInstance.addTable(person);

      config = defineConfig();
      customizeConfig(config);
      new SqlWorkbenchQBitProducer().withQBitConfig(config).produce(qInstance);

      primeDatabase(qInstance, BACKEND_NAME, "sql-workbench-h2.sql");
      loginAs(READER);
   }



   /*******************************************************************************
    ** Hook for subclasses to change the config before production.
    *******************************************************************************/
   protected void customizeConfig(SqlWorkbenchQBitConfig config)
   {
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void baseTearDown()
   {
      QContext.clear();
   }



   /*******************************************************************************
    ** Run a SQL script (statements split on ';' at line ends).
    *******************************************************************************/
   public static void primeDatabase(QInstance qInstance, String backendName, String resource) throws Exception
   {
      String script;
      try(InputStream in = BaseSqlWorkbenchTest.class.getClassLoader().getResourceAsStream(resource))
      {
         script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }

      List<String> statements = new ArrayList<>();
      StringBuilder current = new StringBuilder();
      for(String line : script.split("\n"))
      {
         if(line.trim().startsWith("--"))
         {
            continue;
         }
         current.append(line).append('\n');
         if(line.trim().endsWith(";"))
         {
            statements.add(current.toString().trim().replaceAll(";$", ""));
            current.setLength(0);
         }
      }

      try(Connection connection = ConnectionManager.getConnection((RDBMSBackendMetaData) qInstance.getBackend(backendName)); Statement statement = connection.createStatement())
      {
         for(String sql : statements)
         {
            statement.execute(sql);
         }
      }
   }



   /*******************************************************************************
    ** Init QContext with a session for one of the test users.
    *******************************************************************************/
   protected QSession loginAs(String userId)
   {
      QSession session = TestSessionCustomizer.sessionFor(userId);
      QContext.init(qInstance, session);
      return (session);
   }



   /*******************************************************************************
    ** The caller for a test user (permissions as TestSessionCustomizer grants).
    *******************************************************************************/
   protected Caller caller(String userId)
   {
      boolean writer = userId.startsWith("writer");
      return (new Caller(userId, "Name of " + userId, writer, writer, "127.0.0.1"));
   }



   /*******************************************************************************
    ** Grants permissions by the session id's prefix: "writer-*" gets access,
    ** write and share; "reader-*" gets access; anything else nothing. The user
    ** id is the session id.
    *******************************************************************************/
   public static class TestSessionCustomizer implements com.kingsrook.qqq.backend.core.modules.authentication.QAuthenticationModuleCustomizerInterface
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void customizeSession(QInstance qInstance, QSession qSession, Map<String, Object> context)
      {
         QSession granted = sessionFor(qSession.getUuid());
         qSession.setUser(granted.getUser());
         qSession.setPermissions(granted.getPermissions());
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      public static QSession sessionFor(String userId)
      {
         QSession session = new QSession().withUser(new QUser().withIdReference(userId).withFullName("Name of " + userId));
         session.setPermissions(new java.util.HashSet<>());
         if(userId != null && (userId.startsWith("reader") || userId.startsWith("writer")))
         {
            session.withPermission(SqlWorkbenchQBitConfig.DEFAULT_ACCESS_PERMISSION_BASE_NAME + ".hasAccess");
         }
         if(userId != null && userId.startsWith("writer"))
         {
            session.withPermission(SqlWorkbenchQBitConfig.DEFAULT_WRITE_PERMISSION_NAME);
            session.withPermission(SqlWorkbenchQBitConfig.DEFAULT_SHARE_PERMISSION_NAME);
         }
         return (session);
      }
   }

}
