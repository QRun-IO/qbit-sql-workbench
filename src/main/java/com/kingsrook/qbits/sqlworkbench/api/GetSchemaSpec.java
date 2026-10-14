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


import java.util.List;
import com.kingsrook.qbits.sqlworkbench.engine.Caller;
import com.kingsrook.qqq.middleware.javalin.specs.BasicOperation;
import com.kingsrook.qqq.openapi.model.HttpMethod;
import com.kingsrook.qqq.openapi.model.Parameter;


/*******************************************************************************
 ** Inspect a target backend schema.
 *******************************************************************************/
public class GetSchemaSpec extends SqlWorkbenchJsonSpec
{
   /*******************************************************************************
    ** Describe the operation.
    *******************************************************************************/
   @Override
   public BasicOperation defineBasicOperation()
   {
      return (operation(HttpMethod.GET, "/schema/{backendName}", "Inspect a target backend schema"));
   }



   /*******************************************************************************
    ** Describe path and query parameters.
    *******************************************************************************/
   @Override
   public List<Parameter> defineRequestParameters()
   {
      return (parameters("refresh"));
   }



   /*******************************************************************************
    ** Name the response schema.
    *******************************************************************************/
   @Override
   protected String getResponseSchemaName()
   {
      return ("SqlWorkbenchSchemaV1");
   }



   /*******************************************************************************
    ** Invoke the existing service with the authenticated caller.
    *******************************************************************************/
   @Override
   protected Object handle(JsonInput input) throws Exception
   {
      SqlWorkbenchApi api = new SqlWorkbenchApi();
      Caller caller = caller(input);
      return (api.schema(caller, input.getPathParam("backendName"), "true".equalsIgnoreCase(input.getQueryParam("refresh"))));
   }
}
