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


import java.io.Serializable;
import com.fasterxml.jackson.annotation.JsonIgnore;


/*******************************************************************************
 ** Dedicated database credentials the workbench uses for one target backend,
 ** instead of the application's own (for example a role with SELECT grants
 ** only, for read-only sessions). Defence in depth: a read-only transaction
 ** does not stop side-effecting functions, a least-privilege role does.
 **
 ** username and password may be ${env.X} / ${prop.X} references; they are
 ** resolved only when a connection is opened. jdbcUrl is optional (default:
 ** the backend's own URL). Never serialized; toString redacts.
 *******************************************************************************/
public class SqlWorkbenchCredentials implements Serializable
{
   private String username;
   private String password;
   private String jdbcUrl;



   /*******************************************************************************
    ** Redacting toString.
    *******************************************************************************/
   @Override
   public String toString()
   {
      return ("SqlWorkbenchCredentials{username=" + (username == null ? "null" : "***") + ", password=***, jdbcUrl=" + (jdbcUrl == null ? "default" : "custom") + "}");
   }



   /*******************************************************************************
    ** Getter for username
    *******************************************************************************/
   @JsonIgnore
   public String getUsername()
   {
      return (this.username);
   }



   /*******************************************************************************
    ** Setter for username
    *******************************************************************************/
   public void setUsername(String username)
   {
      this.username = username;
   }



   /*******************************************************************************
    ** Fluent setter for username
    *******************************************************************************/
   public SqlWorkbenchCredentials withUsername(String username)
   {
      this.username = username;
      return (this);
   }


   /*******************************************************************************
    ** Getter for password
    *******************************************************************************/
   @JsonIgnore
   public String getPassword()
   {
      return (this.password);
   }



   /*******************************************************************************
    ** Setter for password
    *******************************************************************************/
   public void setPassword(String password)
   {
      this.password = password;
   }



   /*******************************************************************************
    ** Fluent setter for password
    *******************************************************************************/
   public SqlWorkbenchCredentials withPassword(String password)
   {
      this.password = password;
      return (this);
   }


   /*******************************************************************************
    ** Getter for jdbcUrl. Ignored by JSON: a URL can carry a password.
    *******************************************************************************/
   @JsonIgnore
   public String getJdbcUrl()
   {
      return (this.jdbcUrl);
   }



   /*******************************************************************************
    ** Setter for jdbcUrl
    *******************************************************************************/
   public void setJdbcUrl(String jdbcUrl)
   {
      this.jdbcUrl = jdbcUrl;
   }



   /*******************************************************************************
    ** Fluent setter for jdbcUrl
    *******************************************************************************/
   public SqlWorkbenchCredentials withJdbcUrl(String jdbcUrl)
   {
      this.jdbcUrl = jdbcUrl;
      return (this);
   }

}
