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
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import com.kingsrook.qbits.sqlworkbench.metadata.SqlWorkbenchAppMetaDataProducer;
import com.kingsrook.qbits.sqlworkbench.metadata.SqlWorkbenchWidgetMetaDataProducer;
import com.kingsrook.qbits.sqlworkbench.metadata.SqlWorkbenchWidgetRenderer;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchExecution;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebook;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSTableBackendDetails;
import com.kingsrook.qqq.middleware.javalin.QJavalinMetaData;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Producer output and config validation.
 *******************************************************************************/
class SqlWorkbenchQBitProducerTest extends BaseSqlWorkbenchTest
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testProducedMetaData() throws Exception
   {
      assertThat(qInstance.getQBits().values()).anySatisfy(q ->
      {
         assertThat(q.getGroupId()).isEqualTo("com.kingsrook.qbits");
         assertThat(q.getArtifactId()).isEqualTo("sql-workbench");
      });

      for(String name : List.of("sqlWorkbenchNotebook", "sqlWorkbenchCell", "sqlWorkbenchNotebookVersion", "sqlWorkbenchResultSnapshot", "sqlWorkbenchExecution"))
      {
         QTableMetaData table = qInstance.getTable(name);
         assertThat(table).as(name).isNotNull();
         assertThat(table.getBackendName()).isEqualTo(BACKEND_NAME);
         assertThat(table.getPermissionRules().getLevel()).isEqualTo(PermissionLevel.HAS_ACCESS_PERMISSION);
         assertThat(table.getPermissionRules().getPermissionBaseName()).as("tables need the admin permission, not plain access").isEqualTo("sqlWorkbenchAdmin");
         boolean writable = name.equals("sqlWorkbenchNotebook");
         assertThat(table.isCapabilityEnabled(qInstance.getBackend(BACKEND_NAME), com.kingsrook.qqq.backend.core.model.metadata.tables.Capability.TABLE_INSERT)).as(name).isEqualTo(writable);
         assertThat(table.isCapabilityEnabled(qInstance.getBackend(BACKEND_NAME), com.kingsrook.qqq.backend.core.model.metadata.tables.Capability.TABLE_UPDATE)).as(name).isEqualTo(writable);
         assertThat(table.isCapabilityEnabled(qInstance.getBackend(BACKEND_NAME), com.kingsrook.qqq.backend.core.model.metadata.tables.Capability.TABLE_DELETE)).as(name).isEqualTo(writable);
         assertThat(((RDBMSTableBackendDetails) table.getBackendDetails()).getTableName()).startsWith("sql_workbench_");
         assertThat(table.getSourceQBitName()).isNotBlank();
      }

      QWidgetMetaData widget = (QWidgetMetaData) qInstance.getWidget(SqlWorkbenchWidgetMetaDataProducer.NAME);
      assertThat(widget.getType()).isEqualTo("SQL_WORKBENCH");

      QAppMetaData app = qInstance.getApp(SqlWorkbenchAppMetaDataProducer.APP_NAME);
      assertThat(app.getWidgets()).containsExactly(SqlWorkbenchWidgetMetaDataProducer.NAME);
      assertThat(app.getChildren()).extracting(c -> c.getName()).containsExactly(SqlWorkbenchNotebook.TABLE_NAME, SqlWorkbenchExecution.TABLE_NAME);
      assertThat(app.getPermissionRules().getPermissionBaseName()).isEqualTo("sqlWorkbench");
      //////////////////////////////////////////////////////////////////
      // the qbit produces no processes (only QQQ's generic bulk ones //
      // are added to its tables by the enricher)                     //
      //////////////////////////////////////////////////////////////////
      assertThat(qInstance.getProcesses() == null ? Map.of() : qInstance.getProcesses()).allSatisfy((name, process) -> assertThat(name).contains("."));

      assertThat(QJavalinMetaData.of(qInstance).getAdditionalRouteProviderReferences()).isNotEmpty();
      assertThat(SqlWorkbenchRuntime.get(qInstance)).isNotNull();
      assertThat(new SqlWorkbenchWidgetRenderer().render(null).getWidgetData().getType()).isEqualTo("SQL_WORKBENCH");
      assertThat(SqlWorkbenchQBitProducer.getVersion()).isNotBlank();
      assertThat(SqlWorkbenchQBitProducer.isJavalinAvailable()).isTrue();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testNamespacePrefixAndReproduce() throws Exception
   {
      QInstance instance = new QInstance();
      instance.addBackend(defineH2Backend("other"));
      SqlWorkbenchQBitConfig prefixed = new SqlWorkbenchQBitConfig().withBackendName("other").withRegisterRoutes(false);
      new SqlWorkbenchQBitProducer().withQBitConfig(prefixed).withNamespace("ns").produce(instance);

      assertThat(instance.getTable("ns_sqlWorkbenchNotebook")).isNotNull();
      assertThat(instance.getTable("ns_sqlWorkbenchCell").getField("notebookId").getPossibleValueSourceName()).isEqualTo("ns_sqlWorkbenchNotebook");
      assertThat(instance.getApp("ns_sqlWorkbench")).isNotNull();
      assertThat(QJavalinMetaData.of(instance)).isNull();

      SqlWorkbenchRuntime first = prefixed.getRuntime();
      new SqlWorkbenchQBitProducer().withQBitConfig(prefixed).getQBitMetaData();
      SqlWorkbenchQBitProducer producer = new SqlWorkbenchQBitProducer();
      producer.setQBitConfig(prefixed);
      producer.postProduceActions(null, instance);
      assertThat(prefixed.getRuntime()).isNotSameAs(first);
      assertThat(producer.getNamespace()).isNull();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testWithoutConfig() throws Exception
   {
      SqlWorkbenchQBitProducer producer = new SqlWorkbenchQBitProducer();
      assertThat(producer.isEnabled()).isFalse();
      assertThatThrownBy(() -> producer.produce(new QInstance())).isInstanceOf(QException.class).hasMessageContaining("no config");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testValidation()
   {
      QInstance instance = new QInstance();
      instance.addBackend(defineH2Backend("db"));
      instance.addBackend(new QBackendMetaData().withName("mem").withBackendType(MemoryBackendModule.class));

      assertThat(errors(new SqlWorkbenchQBitConfig(), instance)).anyMatch(e -> e.contains("backendName is required"));
      assertThat(errors(new SqlWorkbenchQBitConfig().withBackendName("nope"), instance)).anyMatch(e -> e.contains("Backend not found"));
      assertThat(errors(new SqlWorkbenchQBitConfig().withBackendName("db"), instance)).isEmpty();
      assertThat(errors(new SqlWorkbenchQBitConfig().withBackendName("db").withAllowedBackendNames(List.of("mem", "x")), instance))
         .anyMatch(e -> e.contains("not an RDBMS")).anyMatch(e -> e.contains("backend not found: x"));
      assertThat(errors(new SqlWorkbenchQBitConfig().withBackendName("db").withReadOnlyCredentials("mem", new SqlWorkbenchCredentials()), instance))
         .anyMatch(e -> e.contains("non-RDBMS")).anyMatch(e -> e.contains("need a username"));
      assertThat(errors(new SqlWorkbenchQBitConfig().withBackendName("db").withMaxRows(0).withWritePermissionName("").withDefaultPageSize(5000), instance))
         .anyMatch(e -> e.contains("maxRows")).anyMatch(e -> e.contains("writePermissionName")).anyMatch(e -> e.contains("defaultPageSize must not exceed"));
      assertThat(errors(new SqlWorkbenchQBitConfig().withBackendName("db").withMaxAffectedRowsWithoutAcknowledgement(10).withMaxAffectedRows(5), instance))
         .anyMatch(e -> e.contains("maxAffectedRowsWithoutAcknowledgement must not exceed"));
      assertThat(errors(new SqlWorkbenchQBitConfig().withBackendName("db").withAdminPermissionBaseName("sqlWorkbench"), instance))
         .anyMatch(e -> e.contains("adminPermissionBaseName must differ"));
      assertThat(errors(new SqlWorkbenchQBitConfig().withBackendName("db").withAdminPermissionBaseName(""), instance))
         .anyMatch(e -> e.contains("adminPermissionBaseName is required"));

      QInstance bad = new QInstance();
      bad.addBackend(defineH2Backend("db"));
      assertThatThrownBy(() -> new SqlWorkbenchQBitProducer().withQBitConfig(new SqlWorkbenchQBitConfig().withBackendName("nope")).produce(bad))
         .isInstanceOf(QException.class).hasMessageContaining("Backend not found");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testConfigDetails()
   {
      SqlWorkbenchQBitConfig config = new SqlWorkbenchQBitConfig().withBackendName("db")
         .withReadOnlyCredentials("db", new SqlWorkbenchCredentials().withUsername("ro").withPassword("secret"))
         .withWriteCredentials("db", new SqlWorkbenchCredentials().withUsername("rw").withPassword("secret2"));
      assertThat(config.toString()).doesNotContain("secret").doesNotContain("ro\"").contains("db");
      assertThat(config.getReadOnlyCredentials().get("db").toString()).doesNotContain("secret").doesNotContain("ro,");
      assertThat(config.getAccessPermissionName()).isEqualTo("sqlWorkbench.hasAccess");
      assertThat(config.getAdminPermissionName()).isEqualTo("sqlWorkbenchAdmin.hasAccess");

      ////////////////////////////////////////////////////////////////
      // credentials never serialize - jdbcUrl can carry a password //
      ////////////////////////////////////////////////////////////////
      String credentialsJson = com.kingsrook.qqq.backend.core.utils.JsonUtils.toJson(new SqlWorkbenchCredentials()
         .withUsername("ro").withPassword("secret").withJdbcUrl("jdbc:postgresql://db/app?password=urlsecret"));
      assertThat(credentialsJson).doesNotContain("secret").doesNotContain("urlsecret").doesNotContain("jdbc:");
      assertThat(config.getAdminPermissionRules().getPermissionBaseName()).isEqualTo("sqlWorkbenchAdmin");
      assertThat(config.getDefaultBackendNameForTables()).isEqualTo("db");
      assertThat(config.applyPrefix("t")).isEqualTo("t");
      assertThat(config.withTableNamePrefix("p").applyPrefix("t")).isEqualTo("p_t");

      SqlWorkbenchQBitConfig empty = new SqlWorkbenchQBitConfig();
      empty.setReadOnlyCredentials(null);
      empty.setWriteCredentials(null);
      empty.withReadOnlyCredentials("a", new SqlWorkbenchCredentials()).withWriteCredentials("a", new SqlWorkbenchCredentials());
      assertThat(empty.getReadOnlyCredentials()).containsKey("a");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static List<String> errors(SqlWorkbenchQBitConfig config, QInstance instance)
   {
      List<String> errors = new ArrayList<>();
      config.validate(instance, errors);
      return (errors);
   }

}
