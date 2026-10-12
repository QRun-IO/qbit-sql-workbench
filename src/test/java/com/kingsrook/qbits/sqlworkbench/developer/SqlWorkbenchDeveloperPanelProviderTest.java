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

package com.kingsrook.qbits.sqlworkbench.developer;


import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.developer.DeveloperPanelsAction;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanel;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelBlock;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelBlockType;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelContext;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelLink;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelsMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qbits.sqlworkbench.BaseSqlWorkbenchTest;
import com.kingsrook.qbits.sqlworkbench.sql.SqlDialect;
import com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSTableBackendDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** Unit test for {@link SqlWorkbenchDeveloperPanelProvider} and
 ** {@link RecordSql}.
 *******************************************************************************/
class SqlWorkbenchDeveloperPanelProviderTest extends BaseSqlWorkbenchTest
{
   private static final String TABLE_NAME = "orderLine";
   private static final String SKU        = "AB'C-1";



   /*******************************************************************************
    ** A table whose backend names differ from its QQQ names, with a string
    ** primary key, plus a table on a non-RDBMS backend.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      qInstance.addTable(new QTableMetaData()
         .withName(TABLE_NAME)
         .withBackendName(BACKEND_NAME)
         .withPrimaryKeyField("sku")
         .withBackendDetails(new RDBMSTableBackendDetails().withTableName("order_line"))
         .withField(new QFieldMetaData("sku", QFieldType.STRING).withBackendName("line_sku"))
         .withField(new QFieldMetaData("quantity", QFieldType.INTEGER))
         .withField(new QFieldMetaData("unitPrice", QFieldType.DECIMAL).withBackendName("unit_price")));

      qInstance.addBackend(new QBackendMetaData().withName("memory"));
      qInstance.addTable(new QTableMetaData()
         .withName("memoryThing")
         .withBackendName("memory")
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER)));

      try(Connection connection = ConnectionManager.getConnection((RDBMSBackendMetaData) qInstance.getBackend(BACKEND_NAME)); Statement statement = connection.createStatement())
      {
         statement.execute("DROP TABLE IF EXISTS order_line");
         statement.execute("CREATE TABLE order_line (line_sku VARCHAR(40) PRIMARY KEY, quantity INT, unit_price DECIMAL(10,2))");
         statement.execute("INSERT INTO order_line VALUES ('AB''C-1', 3, 9.50), ('other', 1, 1.00)");
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private DeveloperPanelContext context(String tableName, Object primaryKey)
   {
      QTableMetaData table = qInstance.getTable(tableName);
      return (new DeveloperPanelContext().withTable(table).withRecord(new QRecord().withValue(table.getPrimaryKeyField(), (java.io.Serializable) primaryKey)));
   }



   /*******************************************************************************
    ** The producer registers the provider; the panel shows through the
    ** framework action with the record and table queries, backend details,
    ** links and the note.
    *******************************************************************************/
   @Test
   void testPanel() throws Exception
   {
      assertThat(DeveloperPanelsMetaData.of(qInstance).getProviders())
         .anyMatch(reference -> SqlWorkbenchDeveloperPanelProvider.class.getName().equals(reference.getName()));

      DeveloperPanel panel = new DeveloperPanelsAction().execute(context(TABLE_NAME, SKU)).stream()
         .filter(p -> SqlWorkbenchDeveloperPanelProvider.ID.equals(p.getId())).findFirst().orElseThrow();
      assertNull(panel.getError());
      assertEquals("SQL Workbench", panel.getTitle());
      assertEquals(450, panel.getOrder());
      assertEquals("terminal", panel.getIcon());

      List<DeveloperPanelBlock> code = panel.getBlocks().stream().filter(b -> b.getType() == DeveloperPanelBlockType.CODE).toList();
      assertEquals(2, code.size());
      assertEquals("sql", code.get(0).getLanguage());
      assertEquals("""
         SELECT
            "line_sku" AS "sku",
            "quantity",
            "unit_price" AS "unitPrice"
         FROM "order_line"
         WHERE "line_sku" = 'AB''C-1'""", code.get(0).getCode());
      assertEquals("""
         SELECT
            "line_sku" AS "sku",
            "quantity",
            "unit_price" AS "unitPrice"
         FROM "order_line"
         LIMIT 100""", code.get(1).getCode());

      ////////////////////////////////////////////////////////////
      // the generated SQL runs on the backend and finds the row //
      ////////////////////////////////////////////////////////////
      try(Connection connection = ConnectionManager.getConnection((RDBMSBackendMetaData) qInstance.getBackend(BACKEND_NAME)); Statement statement = connection.createStatement())
      {
         try(ResultSet resultSet = statement.executeQuery(code.get(0).getCode()))
         {
            assertTrue(resultSet.next());
            assertEquals(SKU, resultSet.getString("sku"));
            assertEquals(3, resultSet.getInt("quantity"));
            assertFalse(resultSet.next());
         }
         try(ResultSet resultSet = statement.executeQuery(code.get(1).getCode()))
         {
            int rows = 0;
            while(resultSet.next())
            {
               rows++;
            }
            assertEquals(2, rows);
         }
      }

      DeveloperPanelBlock keyValues = panel.getBlocks().stream().filter(b -> b.getType() == DeveloperPanelBlockType.KEY_VALUES).findFirst().orElseThrow();
      assertEquals(BACKEND_NAME, keyValues.getItems().get(0).getValue());
      assertEquals("h2", keyValues.getItems().get(1).getValue());
      assertEquals("order_line", keyValues.getItems().get(2).getValue());
      assertEquals("No", keyValues.getItems().get(3).getValue());

      DeveloperPanelBlock links = panel.getBlocks().stream().filter(b -> b.getType() == DeveloperPanelBlockType.LINKS).findFirst().orElseThrow();
      assertEquals(2, links.getLinks().size());
      DeveloperPanelLink recordLink = links.getLinks().get(0);
      assertThat(recordLink.getHref()).startsWith("/app/sqlWorkbench?sql=").endsWith("&backend=h2").doesNotContain(" ", "+", "'", "\n");
      String sqlParam = recordLink.getHref().substring("/app/sqlWorkbench?sql=".length(), recordLink.getHref().indexOf("&backend="));
      assertEquals(code.get(0).getCode(), URLDecoder.decode(sqlParam, StandardCharsets.UTF_8));
      assertThat(links.getLinks().get(1).getHref()).contains("LIMIT%20100");

      assertTrue(panel.getBlocks().stream().anyMatch(b -> b.getType() == DeveloperPanelBlockType.TEXT && b.getText().contains("runs only what you execute")));
   }



   /*******************************************************************************
    ** A prefixed workbench links to its prefixed app; the replica is reported.
    *******************************************************************************/
   @Test
   void testPrefixAndReplica() throws Exception
   {
      config.setTableNamePrefix("ops");
      qInstance.getApp("sqlWorkbench").setName("ops_sqlWorkbench");
      qInstance.getApps().put("ops_sqlWorkbench", qInstance.getApps().remove("sqlWorkbench"));
      config.setUseBackendReadOnlyReplica(true);
      ((RDBMSBackendMetaData) qInstance.getBackend(BACKEND_NAME)).setReadOnlyBackendMetaData(defineH2Backend("h2Replica"));

      DeveloperPanel panel = new SqlWorkbenchDeveloperPanelProvider().build(context("person", 7));
      String href = panel.getBlocks().stream().filter(b -> b.getType() == DeveloperPanelBlockType.LINKS).findFirst().orElseThrow().getLinks().get(0).getHref();
      assertThat(href).startsWith("/app/ops_sqlWorkbench?sql=");
      assertThat(panel.getBlocks().get(0).getCode()).endsWith("WHERE \"id\" = 7");
      DeveloperPanelBlock keyValues = panel.getBlocks().stream().filter(b -> b.getType() == DeveloperPanelBlockType.KEY_VALUES).findFirst().orElseThrow();
      assertEquals("Yes (h2Replica)", keyValues.getItems().get(3).getValue());
   }



   /*******************************************************************************
    ** Writers get the same (SELECT-only) panel.
    *******************************************************************************/
   @Test
   void testWriterSameQueries() throws Exception
   {
      String readerSql = new SqlWorkbenchDeveloperPanelProvider().build(context(TABLE_NAME, SKU)).getBlocks().get(0).getCode();
      loginAs(WRITER);
      assertEquals(readerSql, new SqlWorkbenchDeveloperPanelProvider().build(context(TABLE_NAME, SKU)).getBlocks().get(0).getCode());
   }



   /*******************************************************************************
    ** Not available without the access permission, for a non-RDBMS backend,
    ** for an excluded or not-allowed backend, or without the app.
    *******************************************************************************/
   @Test
   void testNotAvailable()
   {
      SqlWorkbenchDeveloperPanelProvider provider = new SqlWorkbenchDeveloperPanelProvider();
      assertTrue(provider.isAvailable(context(TABLE_NAME, SKU)));

      loginAs("nobody-1");
      assertFalse(provider.isAvailable(context(TABLE_NAME, SKU)));
      assertTrue(new DeveloperPanelsAction().execute(context(TABLE_NAME, SKU)).stream().noneMatch(p -> SqlWorkbenchDeveloperPanelProvider.ID.equals(p.getId())));

      loginAs(READER);
      assertFalse(provider.isAvailable(context("memoryThing", 1)));

      config.setExcludedBackendNames(List.of(BACKEND_NAME));
      assertFalse(provider.isAvailable(context(TABLE_NAME, SKU)));

      config.setExcludedBackendNames(null);
      config.setAllowedBackendNames(List.of("someOtherBackend"));
      assertFalse(provider.isAvailable(context(TABLE_NAME, SKU)));

      config.setAllowedBackendNames(null);
      qInstance.getApps().remove("sqlWorkbench");
      assertFalse(provider.isAvailable(context(TABLE_NAME, SKU)));
   }



   /*******************************************************************************
    ** Identifier quoting and literals per dialect.
    *******************************************************************************/
   @Test
   void testQuotingAndLiterals()
   {
      assertEquals("`my``col`", RecordSql.quoteIdentifier(SqlDialect.MYSQL, "my`col"));
      assertEquals("\"my\"\"col\"", RecordSql.quoteIdentifier(SqlDialect.POSTGRES, "my\"col"));
      assertEquals("'a''b\\c'", RecordSql.stringLiteral(SqlDialect.POSTGRES, "a'b\\c"));
      assertEquals("'a''b\\\\c'", RecordSql.stringLiteral(SqlDialect.MYSQL, "a'b\\c"));
      assertEquals("42", RecordSql.literal(SqlDialect.POSTGRES, QFieldType.INTEGER, 42));
      assertEquals("12.50", RecordSql.literal(SqlDialect.POSTGRES, QFieldType.DECIMAL, new java.math.BigDecimal("12.50")));
      assertEquals("'1; DROP TABLE x'", RecordSql.literal(SqlDialect.POSTGRES, QFieldType.INTEGER, "1; DROP TABLE x"));
      assertEquals("'42'", RecordSql.literal(SqlDialect.POSTGRES, QFieldType.STRING, "42"));
      assertEquals("NULL", RecordSql.literal(SqlDialect.POSTGRES, QFieldType.STRING, null));
      assertThat(RecordSql.selectTable(qInstance.getTable("person"), SqlDialect.GENERIC)).endsWith("FETCH FIRST 100 ROWS ONLY");
      assertThat(RecordSql.selectTable(qInstance.getTable("person"), SqlDialect.MYSQL)).contains("`first_name` AS `firstName`").endsWith("LIMIT 100");
      assertEquals("%27a%20b%27%26c%3D1", SqlWorkbenchDeveloperPanelProvider.encode("'a b'&c=1"));
   }

}
