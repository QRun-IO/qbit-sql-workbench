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

package com.kingsrook.qbits.sqlworkbench.explain;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/*******************************************************************************
 ** One node of a normalized query plan. Numeric fields are null when the
 ** database does not report them (e.g. actual* without ANALYZE).
 *******************************************************************************/
public class PlanNode
{
   private String              label;
   private String              detail;
   private Double              startupCost;
   private Double              totalCost;
   private Double              planRows;
   private Double              actualTimeMs;
   private Double              actualRows;
   private Double              loops;
   private Map<String, Object> extra    = new LinkedHashMap<>();
   private List<PlanNode>      children = new ArrayList<>();



   /*******************************************************************************
    **
    *******************************************************************************/
   public PlanNode()
   {
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public PlanNode(String label, String detail)
   {
      this.label = label;
      this.detail = detail;
   }



   /*******************************************************************************
    ** JSON view of this node and its children.
    *******************************************************************************/
   public Map<String, Object> toMap()
   {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("label", label);
      map.put("detail", detail);
      map.put("startupCost", startupCost);
      map.put("totalCost", totalCost);
      map.put("planRows", planRows);
      map.put("actualTimeMs", actualTimeMs);
      map.put("actualRows", actualRows);
      map.put("loops", loops);
      map.put("extra", extra);
      map.put("children", children.stream().map(PlanNode::toMap).toList());
      return (map);
   }



   /*******************************************************************************
    ** Fluent child adder.
    *******************************************************************************/
   public PlanNode withChild(PlanNode child)
   {
      children.add(child);
      return (this);
   }



   /*******************************************************************************
    ** Getter for label
    *******************************************************************************/
   public String getLabel()
   {
      return (label);
   }



   /*******************************************************************************
    ** Setter for label
    *******************************************************************************/
   public void setLabel(String label)
   {
      this.label = label;
   }



   /*******************************************************************************
    ** Getter for detail
    *******************************************************************************/
   public String getDetail()
   {
      return (detail);
   }



   /*******************************************************************************
    ** Setter for detail
    *******************************************************************************/
   public void setDetail(String detail)
   {
      this.detail = detail;
   }



   /*******************************************************************************
    ** Getter for startupCost
    *******************************************************************************/
   public Double getStartupCost()
   {
      return (startupCost);
   }



   /*******************************************************************************
    ** Setter for startupCost
    *******************************************************************************/
   public void setStartupCost(Double startupCost)
   {
      this.startupCost = startupCost;
   }



   /*******************************************************************************
    ** Getter for totalCost
    *******************************************************************************/
   public Double getTotalCost()
   {
      return (totalCost);
   }



   /*******************************************************************************
    ** Setter for totalCost
    *******************************************************************************/
   public void setTotalCost(Double totalCost)
   {
      this.totalCost = totalCost;
   }



   /*******************************************************************************
    ** Getter for planRows
    *******************************************************************************/
   public Double getPlanRows()
   {
      return (planRows);
   }



   /*******************************************************************************
    ** Setter for planRows
    *******************************************************************************/
   public void setPlanRows(Double planRows)
   {
      this.planRows = planRows;
   }



   /*******************************************************************************
    ** Getter for actualTimeMs
    *******************************************************************************/
   public Double getActualTimeMs()
   {
      return (actualTimeMs);
   }



   /*******************************************************************************
    ** Setter for actualTimeMs
    *******************************************************************************/
   public void setActualTimeMs(Double actualTimeMs)
   {
      this.actualTimeMs = actualTimeMs;
   }



   /*******************************************************************************
    ** Getter for actualRows
    *******************************************************************************/
   public Double getActualRows()
   {
      return (actualRows);
   }



   /*******************************************************************************
    ** Setter for actualRows
    *******************************************************************************/
   public void setActualRows(Double actualRows)
   {
      this.actualRows = actualRows;
   }



   /*******************************************************************************
    ** Getter for loops
    *******************************************************************************/
   public Double getLoops()
   {
      return (loops);
   }



   /*******************************************************************************
    ** Setter for loops
    *******************************************************************************/
   public void setLoops(Double loops)
   {
      this.loops = loops;
   }



   /*******************************************************************************
    ** Getter for extra
    *******************************************************************************/
   public Map<String, Object> getExtra()
   {
      return (extra);
   }



   /*******************************************************************************
    ** Getter for children
    *******************************************************************************/
   public List<PlanNode> getChildren()
   {
      return (children);
   }

}
