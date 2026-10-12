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

package com.kingsrook.qbits.sqlworkbench.metadata;


import java.io.Serializable;
import java.util.List;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchRuntime;
import com.kingsrook.qbits.sqlworkbench.engine.WorkbenchBackends;
import com.kingsrook.qqq.backend.core.actions.values.BasicCustomPossibleValueProvider;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValue;


/*******************************************************************************
 ** Backend choices use the same live allow/deny filtering as the Runner.
 *******************************************************************************/
public class WorkbenchBackendPossibleValueProvider extends BasicCustomPossibleValueProvider<WorkbenchBackends.Target, String>
{
   public static final String NAME = "sql_workbench_backend";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   protected QPossibleValue<String> makePossibleValue(WorkbenchBackends.Target target)
   {
      return (new QPossibleValue<>(target.name(), target.name()));
   }



   /*******************************************************************************
    ** Unknown or excluded stored values have no selectable translation.
    *******************************************************************************/
   @Override
   protected WorkbenchBackends.Target getSourceObject(Serializable id) throws QException
   {
      return (getAllSourceObjects().stream().filter(target -> target.name().equals(id)).findFirst().orElse(null));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   protected List<WorkbenchBackends.Target> getAllSourceObjects() throws QException
   {
      return (new WorkbenchBackends(QContext.getQInstance(), SqlWorkbenchRuntime.get()).listTargets());
   }
}
