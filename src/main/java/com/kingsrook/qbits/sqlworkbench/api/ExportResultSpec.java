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


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.sqlworkbench.engine.Caller;
import com.kingsrook.qbits.sqlworkbench.engine.ResultCache;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qqq.middleware.javalin.executors.AbstractMiddlewareExecutor;
import com.kingsrook.qqq.middleware.javalin.executors.io.AbstractMiddlewareInput;
import com.kingsrook.qqq.middleware.javalin.executors.io.AbstractMiddlewareOutputInterface;
import com.kingsrook.qqq.middleware.javalin.specs.AbstractEndpointSpec;
import com.kingsrook.qqq.middleware.javalin.specs.BasicOperation;
import com.kingsrook.qqq.middleware.javalin.specs.BasicResponse;
import com.kingsrook.qqq.openapi.model.HttpMethod;
import com.kingsrook.qqq.openapi.model.Method;
import com.kingsrook.qqq.openapi.model.Parameter;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;


/*******************************************************************************
 ** Stream a caller-owned cached result without buffering it as a JSON response.
 *******************************************************************************/
public class ExportResultSpec extends AbstractEndpointSpec<ExportResultSpec.ExportInput, ExportResultSpec.ExportOutput, ExportResultSpec.ExportExecutor>
{
   /*******************************************************************************
    ** Describe the existing download endpoint.
    *******************************************************************************/
   @Override
   public BasicOperation defineBasicOperation()
   {
      return (SqlWorkbenchJsonSpec.operation(HttpMethod.GET, "/executions/{uuid}/export", "Export the caller's cached result as CSV, JSON or XLSX"));
   }



   /*******************************************************************************
    ** Stable identifier for generated clients.
    *******************************************************************************/
   @Override
   protected void customizeMethod(Method method)
   {
      method.setOperationId("sqlWorkbenchExportResult");
   }



   /*******************************************************************************
    ** Describe UUID and format selection.
    *******************************************************************************/
   @Override
   public List<Parameter> defineRequestParameters()
   {
      return (SqlWorkbenchJsonSpec.parametersFor(defineBasicOperation().getPath(), "format"));
   }



   /*******************************************************************************
    ** All three media types are served by the same operation.
    *******************************************************************************/
   @Override
   public List<BasicResponse> defineAdditionalBasicResponses()
   {
      List<BasicResponse> responses = new ArrayList<>(SqlWorkbenchJsonSpec.errors());
      for(ResultExporter.Format format : ResultExporter.Format.values())
      {
         responses.add(new BasicResponse(format.getContentType(), HttpStatus.OK,
            "Attachment named sql-result-{short UUID}.{format}; Cache-Control: no-store",
            format == ResultExporter.Format.JSON ? "SqlWorkbenchExportJsonV1" : "SqlWorkbenchExportFileV1", null));
      }
      return (responses);
   }



   /*******************************************************************************
    ** Check access before parsing format, just as the previous route did.
    *******************************************************************************/
   @Override
   public ExportInput buildInput(Context context) throws Exception
   {
      ExportInput input = new ExportInput();
      input.caller = new SqlWorkbenchApi().caller(context.ip());
      input.uuid = context.pathParam("uuid");
      input.format = ResultExporter.Format.of(StringUtils.hasContent(context.queryParam("format")) ? context.queryParam("format") : "csv");
      return (input);
   }



   /*******************************************************************************
    ** Stream only after the executor authorizes access to the cached result.
    *******************************************************************************/
   @Override
   public void handleOutput(Context context, ExportOutput output) throws Exception
   {
      String shortId = output.uuid.length() > 8 ? output.uuid.substring(0, 8) : output.uuid;
      context.contentType(output.format.getContentType());
      context.header("Content-Disposition", "attachment; filename=\"sql-result-" + shortId.replaceAll("[^A-Za-z0-9-]", "") + "." + output.format.getExtension() + "\"");
      context.header("Cache-Control", "no-store");
      ResultExporter.write(output.entry.result(), output.format, context.outputStream());
   }



   /*******************************************************************************
    ** Request state populated after session authentication.
    *******************************************************************************/
   public static class ExportInput extends AbstractMiddlewareInput
   {
      private Caller caller;
      private String uuid;
      private ResultExporter.Format format;
   }



   /*******************************************************************************
    ** Authorized cached data and download metadata.
    *******************************************************************************/
   public static class ExportOutput implements AbstractMiddlewareOutputInterface
   {
      private ResultCache.Entry entry;
      private String uuid;
      private ResultExporter.Format format;
   }



   /*******************************************************************************
    ** Service authorization stays identical to JSON result paging.
    *******************************************************************************/
   public static class ExportExecutor extends AbstractMiddlewareExecutor<ExportInput, ExportOutput>
   {
      /*******************************************************************************
       ** Resolve only the caller's cached execution.
       *******************************************************************************/
      @Override
      public void execute(ExportInput input, ExportOutput output) throws QException
      {
         output.entry = new SqlWorkbenchApi().exportable(input.caller, input.uuid);
         output.uuid = input.uuid;
         output.format = input.format;
      }
   }
}
