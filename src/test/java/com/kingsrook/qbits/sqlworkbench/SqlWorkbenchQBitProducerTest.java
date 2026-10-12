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
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import com.kingsrook.qbits.sqlworkbench.metadata.SqlWorkbenchAppMetaDataProducer;
import com.kingsrook.qbits.sqlworkbench.metadata.SqlWorkbenchWidgetMetaDataProducer;
import com.kingsrook.qbits.sqlworkbench.metadata.SqlWorkbenchWidgetRenderer;
import com.kingsrook.qbits.sqlworkbench.model.ShareAudienceType;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchCell;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchExecution;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebook;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookShare;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchNotebookVersion;
import com.kingsrook.qbits.sqlworkbench.model.SqlWorkbenchResultSnapshot;
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

      ////////////////////////////////////////////////////////////////////////////
      // name -> {insert, update, delete} capabilities: notebooks, cells and   //
      // shares are fully editable; pinned results can be relabeled / deleted  //
      // but only pinned from the notebook UI; versions and the audit are      //
      // append-only. Every table is a standard per-table-permission table     //
      // with a record security lock, and none is hidden.                      //
      ////////////////////////////////////////////////////////////////////////////
      Map<String, List<Boolean>> capabilities = new java.util.LinkedHashMap<>();
      capabilities.put("sql_workbench_notebook", List.of(true, true, true));
      capabilities.put("sql_workbench_cell", List.of(true, true, true));
      capabilities.put("sql_workbench_notebook_share", List.of(true, true, true));
      capabilities.put("sql_workbench_result_snapshot", List.of(false, true, true));
      capabilities.put("sql_workbench_notebook_version", List.of(false, false, false));
      capabilities.put("sql_workbench_execution", List.of(false, false, false));
      for(Map.Entry<String, List<Boolean>> entry : capabilities.entrySet())
      {
         String         name  = entry.getKey();
         QTableMetaData table = qInstance.getTable(name);
         assertThat(table).as(name).isNotNull();
         assertThat(table.getBackendName()).isEqualTo(BACKEND_NAME);
         assertThat(table.getIsHidden()).as(name).isFalse();
         assertThat(table.getPermissionRules().getLevel()).as(name).isEqualTo(PermissionLevel.READ_INSERT_EDIT_DELETE_PERMISSIONS);
         assertThat(table.getRecordSecurityLocks()).as(name).isNotEmpty();
         com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData backend = qInstance.getBackend(BACKEND_NAME);
         assertThat(table.isCapabilityEnabled(backend, com.kingsrook.qqq.backend.core.model.metadata.tables.Capability.TABLE_INSERT)).as(name + " insert").isEqualTo(entry.getValue().get(0));
         assertThat(table.isCapabilityEnabled(backend, com.kingsrook.qqq.backend.core.model.metadata.tables.Capability.TABLE_UPDATE)).as(name + " update").isEqualTo(entry.getValue().get(1));
         assertThat(table.isCapabilityEnabled(backend, com.kingsrook.qqq.backend.core.model.metadata.tables.Capability.TABLE_DELETE)).as(name + " delete").isEqualTo(entry.getValue().get(2));
         assertThat(table.isCapabilityEnabled(backend, com.kingsrook.qqq.backend.core.model.metadata.tables.Capability.TABLE_EXPORT)).as(name + " export").isTrue();
         assertThat(((RDBMSTableBackendDetails) table.getBackendDetails()).getTableName()).startsWith("sql_workbench_");
         assertThat(table.getSourceQBitName()).isNotBlank();
      }
      assertThat(qInstance.getSecurityKeyType("sql_workbench_user").getAllAccessKeyName()).isEqualTo("sql_workbench_all_access");
      assertThat(qInstance.getSecurityKeyType("sql_workbench_audience")).isNotNull();

      ////////////////////////////////////////////////////////////////////////
      // role-management UIs list available permissions without a QContext //
      // instance; the record actions must not break that                  //
      ////////////////////////////////////////////////////////////////////////
      QContext.clear();
      assertThat(com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper.getAllAvailablePermissionNames(qInstance))
         .contains("sql_workbench_notebook.read", "sql_workbench_notebook_share.insert", "sql_workbench.hasAccess")
         .doesNotContain("sql_workbench_execution.delete", "sql_workbench_notebook_version.insert");
      loginAs(READER);

      QWidgetMetaData widget = (QWidgetMetaData) qInstance.getWidget(SqlWorkbenchWidgetMetaDataProducer.NAME);
      assertThat(widget.getType()).isEqualTo("SQL_WORKBENCH");

      QAppMetaData app = qInstance.getApp(SqlWorkbenchAppMetaDataProducer.APP_NAME);
      assertThat(app.getWidgets()).containsExactly(SqlWorkbenchWidgetMetaDataProducer.NAME);
      assertThat(app.getChildren()).extracting(c -> c.getName()).containsExactly("sql_workbench_notebook", "sql_workbench_cell", "sql_workbench_notebook_share",
         "sql_workbench_result_snapshot", "sql_workbench_notebook_version", "sql_workbench_execution");
      assertThat(app.getPermissionRules().getPermissionBaseName()).isEqualTo("sql_workbench");
      ///////////////////////////////////////////////////////////////////
      // the qbit's own processes are record actions on its tables and //
      // never app children (the nav only lists tables and the widget) //
      ///////////////////////////////////////////////////////////////////
      assertThat(app.getChildren()).allSatisfy(child -> assertThat(child).isInstanceOf(QTableMetaData.class));

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

      assertThat(instance.getTable("ns_sql_workbench_notebook")).isNotNull();
      assertThat(instance.getTable("ns_sql_workbench_cell").getField("notebookId").getPossibleValueSourceName()).isEqualTo("ns_sql_workbench_notebook");
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
    ** Owner naming rule (1.0.0-RC.3): table, possible-value source, permission
    ** and security key names are lowercase snake_case prefixed with the qbit's
    ** name; the pre-RC.3 names stay available only as deprecated constants.
    *******************************************************************************/
   @Test
   @SuppressWarnings("deprecation")
   void testSnakeCaseNamesAndLegacyConstants() throws Exception
   {
      List<String> tableNames = List.of(SqlWorkbenchNotebook.TABLE_NAME, SqlWorkbenchCell.TABLE_NAME, SqlWorkbenchNotebookVersion.TABLE_NAME,
         SqlWorkbenchResultSnapshot.TABLE_NAME, SqlWorkbenchNotebookShare.TABLE_NAME, SqlWorkbenchExecution.TABLE_NAME);
      assertThat(tableNames).containsExactly("sql_workbench_notebook", "sql_workbench_cell", "sql_workbench_notebook_version",
         "sql_workbench_result_snapshot", "sql_workbench_notebook_share", "sql_workbench_execution");
      assertThat(tableNames).allSatisfy(name -> assertThat(name).matches("sql_workbench_[a-z_]+"));
      assertThat(List.of(SqlWorkbenchNotebook.LEGACY_TABLE_NAME, SqlWorkbenchCell.LEGACY_TABLE_NAME, SqlWorkbenchNotebookVersion.LEGACY_TABLE_NAME,
         SqlWorkbenchResultSnapshot.LEGACY_TABLE_NAME, SqlWorkbenchNotebookShare.LEGACY_TABLE_NAME, SqlWorkbenchExecution.LEGACY_TABLE_NAME))
         .containsExactly("sqlWorkbenchNotebook", "sqlWorkbenchCell", "sqlWorkbenchNotebookVersion", "sqlWorkbenchResultSnapshot",
            "sqlWorkbenchNotebookShare", "sqlWorkbenchExecution");
      assertThat(ShareAudienceType.NAME).isEqualTo("sql_workbench_share_audience_type");
      assertThat(ShareAudienceType.LEGACY_NAME).isEqualTo("sqlWorkbenchShareAudienceType");

      SqlWorkbenchQBitConfig config = new SqlWorkbenchQBitConfig();
      assertThat(config.getAccessPermissionName()).isEqualTo("sql_workbench.hasAccess");
      assertThat(config.getWritePermissionName()).isEqualTo("sql_workbench.write");
      assertThat(config.getSharePermissionName()).isEqualTo("sql_workbench.share");
      assertThat(config.getAdminPermissionNames()).contains("sql_workbench_admin.hasAccess");
      assertThat(config.getUserPermissionNames()).allSatisfy(name -> assertThat(name).startsWith("sql_workbench"));
      assertThat(config.getUserSecurityKeyTypeName()).isEqualTo("sql_workbench_user");
      assertThat(config.getAudienceSecurityKeyTypeName()).isEqualTo("sql_workbench_audience");
      assertThat(config.getAllAccessSecurityKeyName()).isEqualTo("sql_workbench_all_access");
      assertThat(SqlWorkbenchQBitConfig.LEGACY_ACCESS_PERMISSION_BASE_NAME).isEqualTo("sqlWorkbench");
      assertThat(SqlWorkbenchQBitConfig.LEGACY_ADMIN_PERMISSION_BASE_NAME).isEqualTo("sqlWorkbenchAdmin");
      assertThat(SqlWorkbenchQBitConfig.LEGACY_USER_SECURITY_KEY_TYPE_NAME).isEqualTo("sqlWorkbenchUser");

      ////////////////////////////////////////////////////////////////////
      // prefixes are normalized, so a prefixed name stays snake_case  //
      ////////////////////////////////////////////////////////////////////
      assertThat(config.withTableNamePrefix("ns").applyPrefix(SqlWorkbenchNotebook.TABLE_NAME)).isEqualTo("ns_sql_workbench_notebook");
      assertThat(config.withTableNamePrefix("ns_").applyPrefix(SqlWorkbenchNotebook.TABLE_NAME)).isEqualTo("ns_sql_workbench_notebook");
      assertThat(config.withTableNamePrefix("myNs").applyPrefix(SqlWorkbenchNotebook.TABLE_NAME)).isEqualTo("my_ns_sql_workbench_notebook");
      assertThat(config.withTableNamePrefix(null).applyPrefix(SqlWorkbenchNotebook.TABLE_NAME)).isEqualTo("sql_workbench_notebook");

      //////////////////////////////////////////////////////////////////////
      // with no prefix, the default RDBMS backend table name equals the //
      // QQQ table name: no inference or DDL is needed                   //
      //////////////////////////////////////////////////////////////////////
      QInstance instance = new QInstance();
      instance.addBackend(defineH2Backend("plain"));
      new SqlWorkbenchQBitProducer().withQBitConfig(new SqlWorkbenchQBitConfig().withBackendName("plain").withRegisterRoutes(false)).produce(instance);
      for(String name : tableNames)
      {
         assertThat(instance.getTable(name)).as(name).isNotNull();
      }
      assertThat(instance.getPossibleValueSource(ShareAudienceType.NAME)).isNotNull();
      assertThat(instance.getSecurityKeyType("sql_workbench_user")).isNotNull();
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
      assertThat(errors(new SqlWorkbenchQBitConfig().withBackendName("db").withAdminPermissionBaseName("sql_workbench"), instance))
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
      assertThat(config.getAccessPermissionName()).isEqualTo("sql_workbench.hasAccess");
      assertThat(config.getAdminPermissionName()).isEqualTo("sql_workbench_admin.hasAccess");

      ////////////////////////////////////////////////////////////////
      // credentials never serialize - jdbcUrl can carry a password //
      ////////////////////////////////////////////////////////////////
      String credentialsJson = com.kingsrook.qqq.backend.core.utils.JsonUtils.toJson(new SqlWorkbenchCredentials()
         .withUsername("ro").withPassword("secret").withJdbcUrl("jdbc:postgresql://db/app?password=urlsecret"));
      assertThat(credentialsJson).doesNotContain("secret").doesNotContain("urlsecret").doesNotContain("jdbc:");
      assertThat(config.getAdminPermissionRules().getPermissionBaseName()).isEqualTo("sql_workbench_admin");
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
