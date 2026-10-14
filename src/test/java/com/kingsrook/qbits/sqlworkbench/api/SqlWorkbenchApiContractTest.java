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


import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import com.kingsrook.qqq.middleware.javalin.specs.v1.MiddlewareVersionV1;
import com.kingsrook.qqq.middleware.javalin.tools.UiApiContract;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The committed fragment describes exactly the shipped extension operations.
 *******************************************************************************/
class SqlWorkbenchApiContractTest
{
   /*******************************************************************************
    ** Regenerate with -DsqlWorkbench.openapi.update=true after intentional changes.
    *******************************************************************************/
   @Test
   void contract() throws Exception
   {
      var extension = SqlWorkbenchEndpoints.extension();
      var version = new MiddlewareVersionV1();
      String generated = UiApiContract.generateExtensionYaml(version, extension);
      Path path = Path.of("src/main/resources/openapi/qqq-v1/sql-workbench.yaml");
      if(Boolean.getBoolean("sqlWorkbench.openapi.update"))
      {
         Files.createDirectories(path.getParent());
         Files.writeString(path, generated);
      }
      assertThat(UiApiContract.check(generated, Files.readString(path), null, 1,
         "./mvnw -Dtest=SqlWorkbenchApiContractTest -DsqlWorkbench.openapi.update=true test")).isEmpty();
      var model = version.generateExtensionOpenAPIModel("qqq", extension);
      assertThat(model.getComponents().getSchemas().keySet()).allMatch(name -> name.startsWith("SqlWorkbench"));
      assertThat(model.getPaths().keySet()).allMatch(name -> name.startsWith("/qqq/v1/sql-workbench/"));
      assertThat(extension.getSpecs()).hasSize(21);
      assertThat(extension.getEffectivePathPrefix()).isEqualTo("/sql-workbench");
      Set<String> operationIds = new HashSet<>();
      Set<String> componentNames = new HashSet<>();
      for(var reference : extension.getSpecs())
      {
         var spec = (com.kingsrook.qqq.middleware.javalin.specs.AbstractEndpointSpec<?, ?, ?>) Class.forName(reference.getName()).getConstructor().newInstance();
         assertThat(spec.isSecured()).isTrue();
         assertThat(spec.defineMethod().getOperationId()).startsWith("sqlWorkbench");
         assertThat(operationIds.add(spec.defineMethod().getOperationId())).isTrue();
         for(String name : spec.defineComponentSchemas().keySet())
         {
            assertThat(componentNames.add(name)).as("Component declared once: " + name).isTrue();
         }
      }
      assertThat(generated).contains("text/csv; charset=utf-8", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "format: \"binary\"");
   }
}
