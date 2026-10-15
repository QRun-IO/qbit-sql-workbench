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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.kingsrook.qbits.sqlworkbench.engine.Caller;
import com.kingsrook.qbits.sqlworkbench.engine.DuplicateExecutionException;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qqq.middleware.javalin.QJavalinUtils;
import com.kingsrook.qqq.middleware.javalin.specs.BasicOperation;
import com.kingsrook.qqq.middleware.javalin.specs.BasicResponse;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.AbstractJsonEndpointSpec;
import com.kingsrook.qqq.middleware.javalin.specs.v1.responses.BasicErrorResponseV1;
import com.kingsrook.qqq.openapi.model.HttpMethod;
import com.kingsrook.qqq.openapi.model.In;
import com.kingsrook.qqq.openapi.model.Method;
import com.kingsrook.qqq.openapi.model.Parameter;
import com.kingsrook.qqq.openapi.model.RequestBody;
import com.kingsrook.qqq.openapi.model.Schema;
import com.kingsrook.qqq.openapi.model.Type;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;


/*******************************************************************************
 ** Shared transport; the existing services retain authorization, limits and audit.
 *******************************************************************************/
public abstract class SqlWorkbenchJsonSpec extends AbstractJsonEndpointSpec
{
   /*******************************************************************************
    ** Check access before parsing; preserve exact decimal parameters.
    *******************************************************************************/
   @Override
   public JsonInput buildInput(Context context) throws Exception
   {
      Caller caller = new SqlWorkbenchApi().caller(context.ip());
      WorkbenchInput input = new WorkbenchInput(this, caller);
      input.getPathParams().putAll(context.pathParamMap());
      context.queryParamMap().forEach((name, values) -> input.getQueryParams().put(name, values == null || values.isEmpty() ? null : values.get(0)));
      if(defineRequestBodySchema() != null)
      {
         input.getBody().putAll(readExactBody(context));
      }
      return (input);
   }



   /*******************************************************************************
    ** Parse the established JSON object shape without losing decimal precision.
    *******************************************************************************/
   private static Map<String, Object> readExactBody(Context context) throws QBadRequestException
   {
      if(!StringUtils.hasContent(context.body()))
      {
         return (Map.of());
      }
      if(context.contentType() == null || !context.contentType().toLowerCase().startsWith("application/json"))
      {
         throw (new QBadRequestException("Request bodies must be JSON (Content-Type: application/json)."));
      }
      try
      {
         Map<String, Object> body = JsonUtils.toObject(context.body(), new TypeReference<Map<String, Object>>() {}, mapper -> mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS));
         return (body == null ? Map.of() : body);
      }
      catch(Exception e)
      {
         throw (new QBadRequestException("The request body is not a JSON object."));
      }
   }



   /*******************************************************************************
    ** Preserve duplicate execution UUID conflicts.
    *******************************************************************************/
   @Override
   protected void handleException(Context context, Exception e)
   {
      QJavalinUtils.handleException(e instanceof DuplicateExecutionException ? org.eclipse.jetty.http.HttpStatus.Code.CONFLICT : null, context, e);
   }



   /*******************************************************************************
    ** Bodies remain optional; empty bodies retain their existing service semantics.
    *******************************************************************************/
   @Override
   public RequestBody defineRequestBody()
   {
      RequestBody body = super.defineRequestBody();
      return (body == null ? null : body.withRequired(false));
   }



   /*******************************************************************************
    ** Stable operation identifiers for generated clients.
    *******************************************************************************/
   @Override
   protected void customizeMethod(Method method)
   {
      method.setOperationId("sqlWorkbench" + getClass().getSimpleName().replace("Spec", ""));
   }



   /*******************************************************************************
    ** Keep the established extension prefix.
    *******************************************************************************/
   static BasicOperation operation(HttpMethod method, String path, String summary)
   {
      return (new BasicOperation().withHttpMethod(method).withPath("/sql-workbench" + path)
         .withShortSummary(summary).withLongDescription(summary + ". Requires SQL Workbench access; services enforce caller permissions, ownership, execution limits and audit."));
   }



   /*******************************************************************************
    ** Document existing request error statuses.
    *******************************************************************************/
   @Override
   public List<BasicResponse> defineAdditionalBasicResponses()
   {
      return (errors());
   }



   /*******************************************************************************
    ** Share error schemas with the export stream.
    *******************************************************************************/
   static List<BasicResponse> errors()
   {
      return (List.of(new BasicResponse(HttpStatus.BAD_REQUEST, "Invalid input or execution limit exceeded", BasicErrorResponseV1.class.getSimpleName()),
         new BasicResponse(HttpStatus.UNAUTHORIZED, "Sign in required", BasicErrorResponseV1.class.getSimpleName()),
         new BasicResponse(HttpStatus.FORBIDDEN, "Permission denied", BasicErrorResponseV1.class.getSimpleName()),
         new BasicResponse(HttpStatus.NOT_FOUND, "Missing or invisible resource", BasicErrorResponseV1.class.getSimpleName()),
         new BasicResponse(HttpStatus.CONFLICT, "Execution UUID already used", BasicErrorResponseV1.class.getSimpleName())));
   }



   /*******************************************************************************
    ** Path and query parameter declarations.
    *******************************************************************************/
   protected List<Parameter> parameters(String... queries)
   {
      return (parametersFor(defineBasicOperation().getPath(), queries));
   }



   /*******************************************************************************
    ** Share parameter declarations with the export stream.
    *******************************************************************************/
   static List<Parameter> parametersFor(String path, String... queries)
   {
      List<Parameter> parameters = new ArrayList<>();
      Matcher matcher = Pattern.compile("\\{([^}]+)}").matcher(path);
      while(matcher.find())
      {
         parameters.add(parameter(matcher.group(1), In.PATH));
      }
      for(String query : queries)
      {
         parameters.add(parameter(query, In.QUERY));
      }
      return (parameters);
   }



   /*******************************************************************************
    ** Document parameter types and default behavior.
    *******************************************************************************/
   private static Parameter parameter(String name, In location)
   {
      Type type = Type.STRING;
      if(List.of("id", "n", "snapshotId", "offset", "limit", "sort").contains(name))
      {
         type = Type.INTEGER;
      }
      else if("refresh".equals(name))
      {
         type = Type.BOOLEAN;
      }
      return (new Parameter().withName(name).withIn(location).withRequired(location == In.PATH)
         .withDescription(parameterDescription(name)).withSchema(new Schema().withType(type)));
   }



   /*******************************************************************************
    ** Parameter help for generated clients.
    *******************************************************************************/
   private static String parameterDescription(String name)
   {
      switch(name)
      {
         case "id":
            return ("Notebook identifier.");
         case "n":
            return ("Notebook version number.");
         case "snapshotId":
            return ("Snapshot identifier.");
         case "uuid":
            return ("Execution UUID; case insensitive.");
         case "backendName":
            return ("Configured target backend name.");
         case "offset":
            return ("Rows to skip; default zero; negative values become zero.");
         case "limit":
            return ("Page size; nonpositive or absent uses the default. Rows cap at maxPageSize; history caps at 200 (default 50).");
         case "sort":
            return ("Optional zero-based result column index.");
         case "dir":
            return ("Sort direction: desc (case insensitive) or asc (default).");
         case "refresh":
            return ("True to bypass the schema cache; default false.");
         case "scope":
            return ("mine (default) or shared notebook library.");
         case "format":
            return ("csv (default), json or xlsx; case insensitive.");
         default:
            return (name);
      }
   }



   /*******************************************************************************
    ** Preserve integer path validation.
    *******************************************************************************/
   protected static Integer pathInt(JsonInput input, String name) throws QBadRequestException
   {
      try
      {
         return (Integer.parseInt(input.getPathParam(name)));
      }
      catch(NumberFormatException e)
      {
         throw (new QBadRequestException(name + " must be an integer."));
      }
   }



   /*******************************************************************************
    ** Caller captured before body parsing, including the audit client IP.
    *******************************************************************************/
   protected static Caller caller(JsonInput input)
   {
      return (((WorkbenchInput) input).caller);
   }



   /*******************************************************************************
    ** Per-request state; spec instances themselves are shared across requests.
    *******************************************************************************/
   private static class WorkbenchInput extends JsonInput
   {
      private final SqlWorkbenchJsonSpec spec;
      private final Caller caller;



      /*******************************************************************************
       ** Capture the spec and authorized caller.
       *******************************************************************************/
      private WorkbenchInput(SqlWorkbenchJsonSpec spec, Caller caller)
      {
         this.spec = spec;
         this.caller = caller;
      }



      /*******************************************************************************
       ** Supply the spec to the framework's JSON executor.
       *******************************************************************************/
      @Override
      public AbstractJsonEndpointSpec getSpec()
      {
         return (spec);
      }



      /*******************************************************************************
       ** Preserve the established integer-query error message.
       *******************************************************************************/
      @Override
      public Integer getQueryParamInteger(String name) throws QBadRequestException
      {
         String value = getQueryParam(name);
         if(!StringUtils.hasContent(value))
         {
            return (null);
         }
         try
         {
            return (Integer.parseInt(value.trim()));
         }
         catch(NumberFormatException e)
         {
            throw (new QBadRequestException(name + " must be an integer."));
         }
      }
   }
}
