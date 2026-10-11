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


import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.AbstractWidgetRenderer;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetOutput;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.QWidgetData;


/*******************************************************************************
 ** Renderer for the SQL Workbench widget. The frontend draws the widget from
 ** the (permission-checked) /qqq/v1/sql-workbench endpoints, so the widget's
 ** own data is just its type.
 *******************************************************************************/
public class SqlWorkbenchWidgetRenderer extends AbstractWidgetRenderer
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public RenderWidgetOutput render(RenderWidgetInput input) throws QException
   {
      return (new RenderWidgetOutput(new SqlWorkbenchWidgetData()));
   }



   /*******************************************************************************
    ** Widget data: its type, and nothing else.
    *******************************************************************************/
   public static class SqlWorkbenchWidgetData extends QWidgetData
   {

      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public String getType()
      {
         return (SqlWorkbenchWidgetMetaDataProducer.TYPE);
      }
   }

}
