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
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchRuntime;
import com.kingsrook.qbits.sqlworkbench.engine.Caller;
import com.kingsrook.qbits.sqlworkbench.engine.ResultCache;
import com.kingsrook.qqq.middleware.javalin.QJavalinRouteProviderInterface;
import com.kingsrook.qqq.middleware.javalin.QJavalinUtils;
import com.kingsrook.qqq.middleware.javalin.executors.ExecutorSessionUtils;
import io.javalin.config.JavalinConfig;
import io.javalin.http.ContentType;
import io.javalin.http.Context;


/*******************************************************************************
 ** Javalin routes for the SQL Workbench under /qqq/v1/sql-workbench (see
 ** docs/DESIGN.md section 5 for the contract).
 **
 ** Each request gets a session from the instance's authentication module
 ** (ExecutorSessionUtils), then SqlWorkbenchApi checks permissions and does
 ** the work. Errors map through QJavalinUtils.handleException: 400 bad
 ** request, 401 unauthenticated, 403 not permitted, 404 unknown. POST / PUT
 ** bodies must be JSON (Content-Type application/json): a cross-site form
 ** cannot send that without a CORS preflight. Request and response bodies
 ** are never logged.
 *******************************************************************************/
public class SqlWorkbenchRouteProvider implements QJavalinRouteProviderInterface
{
   public static final String BASE_PATH = "/qqq/v1/sql-workbench";

   private QInstance qInstance;



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void setQInstance(QInstance qInstance)
   {
      this.qInstance = qInstance;
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void acceptJavalinConfig(JavalinConfig config)
   {
      config.routes.get(BASE_PATH + "/capabilities", ctx -> respond(ctx, false, (api, caller, body) -> api.capabilities(caller)));
      config.routes.get(BASE_PATH + "/schema/{backendName}", ctx -> respond(ctx, false, (api, caller, body) -> api.schema(caller, ctx.pathParam("backendName"), "true".equalsIgnoreCase(ctx.queryParam("refresh")))));
      config.routes.post(BASE_PATH + "/execute", ctx -> respond(ctx, true, (api, caller, body) -> api.execute(caller, body)));
      config.routes.post(BASE_PATH + "/explain", ctx -> respond(ctx, true, (api, caller, body) -> api.explain(caller, body)));
      config.routes.get(BASE_PATH + "/executions/{uuid}/rows", ctx -> respond(ctx, false, (api, caller, body) -> api.rows(caller, ctx.pathParam("uuid"),
         intParam(ctx, "offset"), intParam(ctx, "limit"), intParam(ctx, "sort"), ctx.queryParam("dir"))));
      config.routes.get(BASE_PATH + "/executions/{uuid}/export", this::export);
      config.routes.post(BASE_PATH + "/executions/{uuid}/cancel", ctx -> respond(ctx, true, (api, caller, body) -> api.cancel(caller, ctx.pathParam("uuid"))));
      config.routes.get(BASE_PATH + "/history", ctx -> respond(ctx, false, (api, caller, body) -> api.history(caller, intParam(ctx, "limit"), ctx.queryParam("backendName"))));

      config.routes.get(BASE_PATH + "/notebooks", ctx -> respond(ctx, false, (api, caller, body) -> Map.of("notebooks", api.notebooks().list(ctx.queryParam("scope"), caller))));
      config.routes.post(BASE_PATH + "/notebooks", ctx -> respond(ctx, true, (api, caller, body) -> api.notebooks().create(body, caller)));
      config.routes.get(BASE_PATH + "/notebooks/{id}", ctx -> respond(ctx, false, (api, caller, body) -> api.notebooks().get(id(ctx), caller)));
      config.routes.put(BASE_PATH + "/notebooks/{id}", ctx -> respond(ctx, true, (api, caller, body) -> api.notebooks().update(id(ctx), body, caller)));
      config.routes.delete(BASE_PATH + "/notebooks/{id}", ctx -> respond(ctx, false, (api, caller, body) ->
      {
         api.notebooks().delete(id(ctx), caller);
         return (Map.of("deleted", true));
      }));
      config.routes.post(BASE_PATH + "/notebooks/{id}/publish", ctx -> respond(ctx, true, (api, caller, body) -> api.notebooks().publish(id(ctx), Boolean.TRUE.equals(body.get("shared")), caller)));
      config.routes.post(BASE_PATH + "/notebooks/{id}/duplicate", ctx -> respond(ctx, true, (api, caller, body) -> api.notebooks().duplicate(id(ctx), caller)));
      config.routes.get(BASE_PATH + "/notebooks/{id}/versions", ctx -> respond(ctx, false, (api, caller, body) -> Map.of("versions", api.notebooks().versions(id(ctx), caller))));
      config.routes.get(BASE_PATH + "/notebooks/{id}/versions/{n}", ctx -> respond(ctx, false, (api, caller, body) -> api.notebooks().version(id(ctx), pathInt(ctx, "n"), caller)));
      config.routes.post(BASE_PATH + "/notebooks/{id}/versions/{n}/restore", ctx -> respond(ctx, true, (api, caller, body) -> api.notebooks().restore(id(ctx), pathInt(ctx, "n"), caller)));
      config.routes.get(BASE_PATH + "/notebooks/{id}/snapshots", ctx -> respond(ctx, false, (api, caller, body) -> Map.of("snapshots", api.notebooks().snapshots(id(ctx), caller))));
      config.routes.post(BASE_PATH + "/notebooks/{id}/snapshots", ctx -> respond(ctx, true, (api, caller, body) -> api.createSnapshot(caller, id(ctx), body)));
      config.routes.delete(BASE_PATH + "/notebooks/{id}/snapshots/{snapshotId}", ctx -> respond(ctx, false, (api, caller, body) ->
      {
         api.notebooks().deleteSnapshot(id(ctx), pathInt(ctx, "snapshotId"), caller);
         return (Map.of("deleted", true));
      }));
   }



   /*******************************************************************************
    ** Session, permission check, body, handler, JSON - or the error's status.
    *******************************************************************************/
   private void respond(Context context, boolean readsBody, Handler handler)
   {
      try
      {
         ExecutorSessionUtils.setupSession(context, qInstance);
         SqlWorkbenchApi     api    = new SqlWorkbenchApi(qInstance, SqlWorkbenchRuntime.get(qInstance));
         Caller              caller = api.caller(context.ip());
         Map<String, Object> body   = readsBody ? readJsonBody(context) : Map.of();
         Object              result = handler.handle(api, caller, body);

         context.contentType(ContentType.APPLICATION_JSON);
         context.result(JsonUtils.toJsonCustomized(result, builder -> builder.serializationInclusion(JsonInclude.Include.ALWAYS)));
      }
      catch(Exception e)
      {
         QJavalinUtils.handleException(null, context, e);
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    ** GET /executions/{uuid}/export?format=csv|json|xlsx
    *******************************************************************************/
   private void export(Context context)
   {
      try
      {
         ExecutorSessionUtils.setupSession(context, qInstance);
         SqlWorkbenchApi        api    = new SqlWorkbenchApi(qInstance, SqlWorkbenchRuntime.get(qInstance));
         Caller                 caller = api.caller(context.ip());
         ResultExporter.Format  format = ResultExporter.Format.of(StringUtils.hasContent(context.queryParam("format")) ? context.queryParam("format") : "csv");
         String                 uuid   = context.pathParam("uuid");
         ResultCache.Entry      entry  = api.exportable(caller, uuid);

         String shortId = uuid.length() > 8 ? uuid.substring(0, 8) : uuid;
         context.contentType(format.getContentType());
         context.header("Content-Disposition", "attachment; filename=\"sql-result-" + shortId.replaceAll("[^A-Za-z0-9-]", "") + "." + format.getExtension() + "\"");
         context.header("Cache-Control", "no-store");
         ResultExporter.write(entry.result(), format, context.outputStream());
      }
      catch(Exception e)
      {
         QJavalinUtils.handleException(null, context, e);
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    ** A JSON object body. Decimals stay exact (BigDecimal).
    *******************************************************************************/
   static Map<String, Object> readJsonBody(Context context) throws QBadRequestException
   {
      String body = context.body();
      if(!StringUtils.hasContent(body))
      {
         return (new LinkedHashMap<>());
      }

      String contentType = context.contentType();
      if(contentType == null || !contentType.toLowerCase().startsWith("application/json"))
      {
         throw (new QBadRequestException("Request bodies must be JSON (Content-Type: application/json)."));
      }

      try
      {
         Map<String, Object> map = JsonUtils.toObject(body, new TypeReference<Map<String, Object>>() {}, mapper -> mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS));
         return (map == null ? new LinkedHashMap<>() : map);
      }
      catch(Exception e)
      {
         throw (new QBadRequestException("The request body is not a JSON object."));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Integer id(Context context) throws QBadRequestException
   {
      return (pathInt(context, "id"));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Integer pathInt(Context context, String name) throws QBadRequestException
   {
      try
      {
         return (Integer.parseInt(context.pathParam(name)));
      }
      catch(NumberFormatException e)
      {
         throw (new QBadRequestException(name + " must be an integer."));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Integer intParam(Context context, String name) throws QBadRequestException
   {
      String value = context.queryParam(name);
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



   /*******************************************************************************
    ** One endpoint's work.
    *******************************************************************************/
   @FunctionalInterface
   private interface Handler
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      Object handle(SqlWorkbenchApi api, Caller caller, Map<String, Object> body) throws QException;
   }

}
