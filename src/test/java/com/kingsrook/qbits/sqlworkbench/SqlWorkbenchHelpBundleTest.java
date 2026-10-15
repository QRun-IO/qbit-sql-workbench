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


import com.kingsrook.qqq.backend.core.actions.help.HelpTargetCatalog;
import com.kingsrook.qqq.backend.core.model.metadata.help.bundles.HelpBundles;
import com.kingsrook.qqq.backend.core.testing.HelpBundleAssertions;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;


/*******************************************************************************
 ** Checks the shipped help against the metadata produced by this QBit.
 *******************************************************************************/
class SqlWorkbenchHelpBundleTest extends BaseSqlWorkbenchTest
{
   /*******************************************************************************
    ** Owned objects and their non-trivial fields must have valid help.
    *******************************************************************************/
   @Test
   void bundleCoversWorkbench()
   {
      HelpBundleAssertions.forInstance(qInstance)
         .bundles("help/qbit-sql-workbench.yaml")
         .owning(target -> target.name().startsWith("sqlWorkbench") || target.name().startsWith("sql_workbench_"))
         .assertComplete();

      // Core requires apps, tables, fields and processes; also cover our widgets and value sources.
      var keys = HelpBundles.of(qInstance).getBundles().get(0).getEntries().stream().map(entry -> entry.getKey()).toList();
      HelpTargetCatalog.collect(qInstance).stream()
         .filter(target -> target.kind().equals("widget") || target.kind().equals("possibleValueSource"))
         .filter(target -> target.name().startsWith("sqlWorkbench") || target.name().startsWith("sql_workbench_"))
         .forEach(target -> assertThat(keys).as(target.key()).contains(target.key()));
   }



   /*******************************************************************************
    ** Registration remains useful and startup sync is harmless without storage.
    *******************************************************************************/
   @Test
   void registersWithoutHelpContentTable()
   {
      assertThat(qInstance.getTable("helpContent")).isNull();
      assertThat(HelpBundles.of(qInstance).getBundles()).singleElement().satisfies(bundle ->
      {
         assertThat(bundle.getId()).isEqualTo("qbit-sql-workbench");
         assertThat(bundle.getVersion()).isEqualTo(SqlWorkbenchQBitProducer.getVersion());
         assertThat(bundle.getLayer().name()).isEqualTo("QBIT");
      });
      assertThatCode(() -> HelpBundles.syncOnStartup(qInstance)).doesNotThrowAnyException();
   }
}
