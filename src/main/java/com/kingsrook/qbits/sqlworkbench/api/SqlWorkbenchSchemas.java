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

package com.kingsrook.qbits.sqlworkbench.api;


import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.openapi.model.Schema;
import com.kingsrook.qqq.openapi.model.Type;


/*******************************************************************************
 ** Versioned wire schemas shared by SQL Workbench specs.
 *******************************************************************************/
public class SqlWorkbenchSchemas
{
   /*******************************************************************************
    ** Explicit schemas for the existing map payloads; no serialization changes.
    *******************************************************************************/
   public static Map<String, Schema> components()
   {
      Map<String, Schema> schemas = new LinkedHashMap<>();
      schemas.put("SqlWorkbenchErrorV1", new Schema().withType(Type.OBJECT)
         .withDescription("Deprecated since 1.1.0: errors use BasicErrorResponseV1.")
         .withProperty("error", new Schema().withType(Type.STRING)));
      schemas.put("SqlWorkbenchEmptyRequestV1", new Schema().withType(Type.OBJECT));
      schemas.put("SqlWorkbenchDeletedV1", new Schema().withType(Type.OBJECT)
         .withProperty("deleted", new Schema().withType(Type.BOOLEAN)));
      schemas.put("SqlWorkbenchCancelledV1", new Schema().withType(Type.OBJECT)
         .withProperty("cancelled", new Schema().withType(Type.BOOLEAN)));
      schemas.put("SqlWorkbenchLimitsV1", new Schema().withType(Type.OBJECT)
         .withProperty("maxRows", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("pageSize", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("maxPageSize", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("maxResultBytes", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("statementTimeoutSeconds", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("writeStatementTimeoutSeconds", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("maxAffectedRowsWithoutAcknowledgement", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("maxAffectedRows", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("maxSqlLength", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("maxStatementsPerRun", new Schema().withType(Type.INTEGER).withFormat("int64")));
      schemas.put("SqlWorkbenchBackendV1", new Schema().withType(Type.OBJECT)
         .withProperty("name", new Schema().withType(Type.STRING))
         .withProperty("label", new Schema().withType(Type.STRING))
         .withProperty("vendor", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("dialect", ref("Dialect"))
         .withProperty("readOnlyCredentials", new Schema().withType(Type.BOOLEAN))
         .withProperty("readOnlyEnforced", new Schema().withType(Type.BOOLEAN))
         .withProperty("transactionalDdl", new Schema().withType(Type.BOOLEAN))
         .withProperty("writeAllowed", new Schema().withType(Type.BOOLEAN)));
      schemas.put("SqlWorkbenchCapabilitiesV1", new Schema().withType(Type.OBJECT)
         .withProperty("version", new Schema().withType(Type.STRING))
         .withProperty("canWrite", new Schema().withType(Type.BOOLEAN))
         .withProperty("canShare", new Schema().withType(Type.BOOLEAN))
         .withProperty("writeModeEnabled", new Schema().withType(Type.BOOLEAN))
         .withProperty("limits", ref("Limits"))
         .withProperty("backends", array(ref("Backend"))));
      schemas.put("SqlWorkbenchColumnV1", new Schema().withType(Type.OBJECT)
         .withProperty("name", new Schema().withType(Type.STRING))
         .withProperty("label", new Schema().withType(Type.STRING))
         .withProperty("typeName", new Schema().withType(Type.STRING))
         .withProperty("jdbcType", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("kind", ref("ColumnKind")));
      schemas.put("SqlWorkbenchBinaryValueV1", new Schema().withType(Type.OBJECT)
         .withProperty("$binary", new Schema().withType(Type.BOOLEAN))
         .withProperty("length", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("hexPreview", new Schema().withType(Type.STRING)));
      schemas.put("SqlWorkbenchStatementV1", new Schema().withType(Type.OBJECT)
         .withProperty("index", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("kind", ref("StatementKind"))
         .withProperty("leadingKeyword", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("text", new Schema().withType(Type.STRING))
         .withProperty("affectedRows", new Schema().withType(Type.INTEGER).withFormat("int64").withNullable(true)));
      schemas.put("SqlWorkbenchExecuteRequestV1", new Schema().withType(Type.OBJECT)
         .withProperty("backendName", new Schema().withType(Type.STRING))
         .withProperty("sql", new Schema().withType(Type.STRING))
         .withProperty("params", ref("Parameters"))
         .withProperty("mode", new Schema().withType(Type.STRING))
         .withProperty("notebookId", new Schema().withType(Type.INTEGER).withFormat("int64").withNullable(true))
         .withProperty("cellKey", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("pageSize", new Schema().withType(Type.INTEGER).withFormat("int64").withNullable(true))
         .withProperty("confirmToken", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("acknowledgeRowGuard", new Schema().withType(Type.BOOLEAN))
         .withProperty("executionUuid", new Schema().withType(Type.STRING).withNullable(true)));
      schemas.put("SqlWorkbenchExplainRequestV1", new Schema().withType(Type.OBJECT)
         .withProperty("backendName", new Schema().withType(Type.STRING))
         .withProperty("sql", new Schema().withType(Type.STRING))
         .withProperty("params", ref("Parameters"))
         .withProperty("analyze", new Schema().withType(Type.BOOLEAN)));
      schemas.put("SqlWorkbenchExecutionV1", new Schema().withType(Type.OBJECT)
         .withProperty("executionUuid", new Schema().withType(Type.STRING))
         .withProperty("status", ref("Status"))
         .withProperty("mode", new Schema().withType(Type.STRING))
         .withProperty("backendName", new Schema().withType(Type.STRING))
         .withProperty("durationMs", new Schema().withType(Type.INTEGER).withFormat("int64").withNullable(true))
         .withProperty("statements", array(ref("Statement")))
         .withProperty("columns", array(ref("Column")))
         .withProperty("rows", ref("RowsData"))
         .withProperty("rowCount", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("hasMore", new Schema().withType(Type.BOOLEAN))
         .withProperty("truncated", new Schema().withType(Type.BOOLEAN))
         .withProperty("truncatedReason", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("rowsAffected", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("error", new Schema().withType(Type.STRING))
         .withProperty("sqlState", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("dryRunSupported", new Schema().withType(Type.BOOLEAN))
         .withProperty("dryRunNote", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("totalAffectedRows", new Schema().withType(Type.INTEGER).withFormat("int64").withNullable(true))
         .withProperty("rowGuardExceeded", new Schema().withType(Type.BOOLEAN))
         .withProperty("rowGuardLimit", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("confirmToken", new Schema().withType(Type.STRING))
         .withProperty("expiresAt", new Schema().withType(Type.STRING))
         .withProperty("previewAffectedRows", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("actualAffectedRows", new Schema().withType(Type.INTEGER).withFormat("int64")));
      schemas.put("SqlWorkbenchRowsV1", new Schema().withType(Type.OBJECT)
         .withProperty("executionUuid", new Schema().withType(Type.STRING))
         .withProperty("columns", array(ref("Column")))
         .withProperty("rows", ref("RowsData"))
         .withProperty("offset", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("rowCount", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("truncated", new Schema().withType(Type.BOOLEAN))
         .withProperty("truncatedReason", new Schema().withType(Type.STRING).withNullable(true)));
      schemas.put("SqlWorkbenchPlanNodeV1", new Schema().withType(Type.OBJECT)
         .withProperty("label", new Schema().withType(Type.STRING))
         .withProperty("detail", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("startupCost", new Schema().withType(Type.NUMBER).withNullable(true))
         .withProperty("totalCost", new Schema().withType(Type.NUMBER).withNullable(true))
         .withProperty("planRows", new Schema().withType(Type.INTEGER).withFormat("int64").withNullable(true))
         .withProperty("actualTimeMs", new Schema().withType(Type.NUMBER).withNullable(true))
         .withProperty("actualRows", new Schema().withType(Type.INTEGER).withFormat("int64").withNullable(true))
         .withProperty("loops", new Schema().withType(Type.INTEGER).withFormat("int64").withNullable(true))
         .withProperty("extra", ref("FreeObject"))
         .withProperty("children", array(ref("PlanNode"))));
      schemas.put("SqlWorkbenchExplainV1", new Schema().withType(Type.OBJECT)
         .withProperty("executionUuid", new Schema().withType(Type.STRING))
         .withProperty("status", ref("Status"))
         .withProperty("mode", new Schema().withType(Type.STRING))
         .withProperty("backendName", new Schema().withType(Type.STRING))
         .withProperty("dialect", ref("Dialect"))
         .withProperty("analyze", new Schema().withType(Type.BOOLEAN))
         .withProperty("plan", ref("PlanNode"))
         .withProperty("raw", new Schema().withType(Type.STRING))
         .withProperty("error", new Schema().withType(Type.STRING))
         .withProperty("sqlState", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("durationMs", new Schema().withType(Type.INTEGER).withFormat("int64").withNullable(true)));
      schemas.put("SqlWorkbenchSchemaColumnV1", new Schema().withType(Type.OBJECT)
         .withProperty("name", new Schema().withType(Type.STRING))
         .withProperty("typeName", new Schema().withType(Type.STRING))
         .withProperty("nullable", new Schema().withType(Type.BOOLEAN)));
      schemas.put("SqlWorkbenchSchemaTableV1", new Schema().withType(Type.OBJECT)
         .withProperty("name", new Schema().withType(Type.STRING))
         .withProperty("type", new Schema().withType(Type.STRING))
         .withProperty("columns", array(ref("SchemaColumn"))));
      schemas.put("SqlWorkbenchDatabaseSchemaV1", new Schema().withType(Type.OBJECT)
         .withProperty("name", new Schema().withType(Type.STRING))
         .withProperty("tables", array(ref("SchemaTable"))));
      schemas.put("SqlWorkbenchQqqFieldV1", new Schema().withType(Type.OBJECT)
         .withProperty("name", new Schema().withType(Type.STRING))
         .withProperty("label", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("sqlName", new Schema().withType(Type.STRING))
         .withProperty("type", new Schema().withType(Type.STRING).withNullable(true)));
      schemas.put("SqlWorkbenchQqqTableV1", new Schema().withType(Type.OBJECT)
         .withProperty("name", new Schema().withType(Type.STRING))
         .withProperty("label", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("sqlName", new Schema().withType(Type.STRING))
         .withProperty("fields", array(ref("QqqField"))));
      schemas.put("SqlWorkbenchSchemaV1", new Schema().withType(Type.OBJECT)
         .withProperty("backendName", new Schema().withType(Type.STRING))
         .withProperty("dialect", ref("Dialect"))
         .withProperty("schemas", array(ref("DatabaseSchema")))
         .withProperty("truncated", new Schema().withType(Type.BOOLEAN))
         .withProperty("keywords", array(new Schema().withType(Type.STRING)))
         .withProperty("functions", array(new Schema().withType(Type.STRING)))
         .withProperty("qqqTables", array(ref("QqqTable")))
         .withProperty("cachedAt", new Schema().withType(Type.STRING)));
      schemas.put("SqlWorkbenchHistoryEntryV1", new Schema().withType(Type.OBJECT)
         .withProperty("executionUuid", new Schema().withType(Type.STRING))
         .withProperty("backendName", new Schema().withType(Type.STRING))
         .withProperty("mode", new Schema().withType(Type.STRING))
         .withProperty("status", new Schema().withType(Type.STRING))
         .withProperty("sqlText", new Schema().withType(Type.STRING))
         .withProperty("notebookId", new Schema().withType(Type.INTEGER).withFormat("int64").withNullable(true))
         .withProperty("cellKey", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("startTime", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("durationMs", new Schema().withType(Type.INTEGER).withFormat("int64").withNullable(true))
         .withProperty("rowsReturned", new Schema().withType(Type.INTEGER).withFormat("int64").withNullable(true))
         .withProperty("rowsAffected", new Schema().withType(Type.INTEGER).withFormat("int64").withNullable(true))
         .withProperty("errorMessage", new Schema().withType(Type.STRING).withNullable(true)));
      schemas.put("SqlWorkbenchHistoryV1", new Schema().withType(Type.OBJECT)
         .withProperty("executions", array(ref("HistoryEntry"))));
      schemas.put("SqlWorkbenchNotebookParameterV1", new Schema().withType(Type.OBJECT)
         .withProperty("name", new Schema().withType(Type.STRING))
         .withProperty("type", new Schema().withType(Type.STRING))
         .withProperty("defaultValue", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("label", new Schema().withType(Type.STRING).withNullable(true)));
      schemas.put("SqlWorkbenchCellV1", new Schema().withType(Type.OBJECT)
         .withProperty("cellKey", new Schema().withType(Type.STRING))
         .withProperty("cellType", new Schema().withType(Type.STRING))
         .withProperty("title", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("content", new Schema().withType(Type.STRING))
         .withProperty("backendName", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("sortOrder", new Schema().withType(Type.INTEGER).withFormat("int64")));
      schemas.put("SqlWorkbenchNotebookSummaryV1", new Schema().withType(Type.OBJECT)
         .withProperty("id", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("title", new Schema().withType(Type.STRING))
         .withProperty("description", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("visibility", new Schema().withType(Type.STRING))
         .withProperty("ownerUserId", new Schema().withType(Type.STRING))
         .withProperty("ownerName", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("defaultBackendName", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("currentVersion", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("canEdit", new Schema().withType(Type.BOOLEAN))
         .withProperty("createDate", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("modifyDate", new Schema().withType(Type.STRING).withNullable(true)));
      schemas.put("SqlWorkbenchNotebookV1", new Schema().withType(Type.OBJECT)
         .withProperty("id", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("title", new Schema().withType(Type.STRING))
         .withProperty("description", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("visibility", new Schema().withType(Type.STRING))
         .withProperty("ownerUserId", new Schema().withType(Type.STRING))
         .withProperty("ownerName", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("defaultBackendName", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("currentVersion", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("canEdit", new Schema().withType(Type.BOOLEAN))
         .withProperty("createDate", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("modifyDate", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("parameters", array(ref("NotebookParameter")))
         .withProperty("cells", array(ref("Cell"))));
      schemas.put("SqlWorkbenchNotebookContentV1", new Schema().withType(Type.OBJECT)
         .withProperty("title", new Schema().withType(Type.STRING))
         .withProperty("description", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("defaultBackendName", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("parameters", array(ref("NotebookParameter")))
         .withProperty("cells", array(ref("Cell"))));
      schemas.put("SqlWorkbenchNotebookRequestV1", new Schema().withType(Type.OBJECT)
         .withProperty("title", new Schema().withType(Type.STRING))
         .withProperty("description", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("defaultBackendName", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("parameters", array(ref("NotebookParameter")))
         .withProperty("cells", array(ref("Cell")))
         .withProperty("comment", new Schema().withType(Type.STRING)));
      schemas.put("SqlWorkbenchNotebooksV1", new Schema().withType(Type.OBJECT)
         .withProperty("notebooks", array(ref("NotebookSummary"))));
      schemas.put("SqlWorkbenchPublishRequestV1", new Schema().withType(Type.OBJECT)
         .withProperty("shared", new Schema().withType(Type.BOOLEAN)));
      schemas.put("SqlWorkbenchVersionSummaryV1", new Schema().withType(Type.OBJECT)
         .withProperty("versionNumber", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("savedByName", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("comment", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("createDate", new Schema().withType(Type.STRING)));
      schemas.put("SqlWorkbenchVersionV1", new Schema().withType(Type.OBJECT)
         .withProperty("versionNumber", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("savedByName", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("comment", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("createDate", new Schema().withType(Type.STRING))
         .withProperty("notebook", ref("NotebookContent")));
      schemas.put("SqlWorkbenchVersionsV1", new Schema().withType(Type.OBJECT)
         .withProperty("versions", array(ref("VersionSummary"))));
      schemas.put("SqlWorkbenchSnapshotRequestV1", new Schema().withType(Type.OBJECT)
         .withProperty("executionUuid", new Schema().withType(Type.STRING))
         .withProperty("cellKey", new Schema().withType(Type.STRING))
         .withProperty("label", new Schema().withType(Type.STRING).withNullable(true)));
      schemas.put("SqlWorkbenchSnapshotResultV1", new Schema().withType(Type.OBJECT)
         .withProperty("columns", array(ref("Column")))
         .withProperty("rows", ref("RowsData")));
      schemas.put("SqlWorkbenchSnapshotV1", new Schema().withType(Type.OBJECT)
         .withProperty("id", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("cellKey", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("executionUuid", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("label", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("backendName", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("rowCount", new Schema().withType(Type.INTEGER).withFormat("int64"))
         .withProperty("truncated", new Schema().withType(Type.BOOLEAN))
         .withProperty("createDate", new Schema().withType(Type.STRING).withNullable(true))
         .withProperty("result", ref("SnapshotResult")));
      schemas.put("SqlWorkbenchSnapshotsV1", new Schema().withType(Type.OBJECT)
         .withProperty("snapshots", array(ref("Snapshot"))));
      schemas.put("SqlWorkbenchDialectV1", new Schema().withType(Type.STRING).withEnumValues(List.of("postgresql", "mysql", "sqlite", "h2", "sql")));
      schemas.put("SqlWorkbenchColumnKindV1", new Schema().withType(Type.STRING).withEnumValues(List.of("number", "string", "boolean", "date", "datetime", "time", "json", "binary", "array", "other")));
      schemas.put("SqlWorkbenchStatementKindV1", new Schema().withType(Type.STRING).withEnumValues(List.of("READ", "EXPLAIN_ANALYZE", "WRITE", "DDL", "FORBIDDEN")));
      schemas.put("SqlWorkbenchStatusV1", new Schema().withType(Type.STRING).withEnumValues(List.of("SUCCEEDED", "FAILED", "TIMED_OUT", "CANCELLED", "REJECTED", "PREVIEW", "DRIFTED")));
      schemas.put("SqlWorkbenchParametersV1", new Schema().withType(Type.OBJECT).withAdditionalProperties(new Schema())
         .withDescription("Named SQL parameter values. JSON decimals are parsed exactly as BigDecimal."));
      schemas.put("SqlWorkbenchFreeObjectV1", new Schema().withType(Type.OBJECT).withAdditionalProperties(true));
      schemas.put("SqlWorkbenchValueV1", new Schema().withAnyOf(List.of(
         new Schema().withType(Type.STRING).withNullable(true), new Schema().withType(Type.NUMBER), new Schema().withType(Type.BOOLEAN),
         ref("BinaryValue"), array(ref("Value")), ref("FreeObject")))
         .withDescription("JSON-safe JDBC value; null, scalar, binary preview, nested array or vendor JSON object."));
      schemas.put("SqlWorkbenchRowsDataV1", array(array(ref("Value"))));
      schemas.put("SqlWorkbenchExportFileV1", new Schema().withType(Type.STRING).withFormat("binary"));
      schemas.put("SqlWorkbenchExportJsonV1", array(new Schema().withType(Type.OBJECT).withAdditionalProperties(ref("Value")))
         .withDescription("Rows keyed by unique column labels, with duplicate labels suffixed."));
      return (schemas);
   }



   /*******************************************************************************
    ** Reference a component in this extension's namespace.
    *******************************************************************************/
   public static Schema ref(String name)
   {
      return (new Schema().withRefToSchema("SqlWorkbench" + name + "V1"));
   }



   /*******************************************************************************
    ** An array with explicitly described items.
    *******************************************************************************/
   private static Schema array(Schema items)
   {
      return (new Schema().withType(Type.ARRAY).withItems(items));
   }
}
