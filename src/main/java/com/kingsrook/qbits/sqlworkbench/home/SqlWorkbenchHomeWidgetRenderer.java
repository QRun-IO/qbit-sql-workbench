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
package com.kingsrook.qbits.sqlworkbench.home;


import java.io.Serializable;
import java.util.Map;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchRuntime;
import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.AbstractWidgetRenderer;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetOutput;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;


/*******************************************************************************
 ** Renders the SQL Workbench home widgets (and the notebook's "Open in
 ** Runner" widget). Which one is in the widget's "kind" default value; the
 ** data is read in the viewer's session, so permissions and record locks
 ** apply (see SqlWorkbenchHome). The widgets' own permission rules (the
 ** middleware checks them, and the instance metadata hides denied ones)
 ** decide who gets each widget at all.
 *******************************************************************************/
public class SqlWorkbenchHomeWidgetRenderer extends AbstractWidgetRenderer
{
   public static final String KIND = "kind";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public RenderWidgetOutput render(RenderWidgetInput input) throws QException
   {
      SqlWorkbenchRuntime runtime = SqlWorkbenchRuntime.get();
      String              kind    = null;
      if(input.getWidgetMetaData() instanceof QWidgetMetaData widget)
      {
         Map<String, Serializable> defaults = widget.getDefaultValues();
         kind = defaults == null || defaults.get(KIND) == null ? null : String.valueOf(defaults.get(KIND));
      }
      SqlWorkbenchHome.Viewer viewer = SqlWorkbenchHome.viewer(runtime.getConfig(), runtime.getClock().instant());
      return (new RenderWidgetOutput(SqlWorkbenchHome.load(kind, viewer, input)));
   }

}
