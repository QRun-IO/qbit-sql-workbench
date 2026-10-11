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

package com.kingsrook.qbits.sqlworkbench.engine;


import java.sql.Statement;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Registry of executions in flight: enforces the per-user concurrency limit
 ** and lets the owner cancel a running statement.
 *******************************************************************************/
public class RunningStatements
{
   private static final QLogger LOG = QLogger.getLogger(RunningStatements.class);

   private final Map<String, Running> running = new ConcurrentHashMap<>();



   /*******************************************************************************
    ** One execution in flight.
    *******************************************************************************/
   public static class Running
   {
      private final    String    userId;
      private volatile Statement statement;
      private volatile boolean   cancelled;



      /*******************************************************************************
       **
       *******************************************************************************/
      Running(String userId)
      {
         this.userId = userId;
      }



      /*******************************************************************************
       ** Whether the owner asked to cancel.
       *******************************************************************************/
      public boolean isCancelled()
      {
         return (cancelled);
      }



      /*******************************************************************************
       ** The statement now running (the engine swaps it per statement). If a
       ** cancel already arrived, the statement is cancelled at once.
       *******************************************************************************/
      public void setStatement(Statement statement)
      {
         this.statement = statement;
         if(cancelled)
         {
            cancelQuietly(statement);
         }
      }
   }



   /*******************************************************************************
    ** Register an execution, unless the user already has maxPerUser running.
    ** An id that is already registered is refused (never overwritten), so a
    ** re-sent id cannot replace - and later unregister - the original run.
    **
    ** @return the registration, or null when the user is at the limit
    ** @throws DuplicateExecutionException when the id is already running
    *******************************************************************************/
   public synchronized Running register(String executionUuid, String userId, int maxPerUser) throws DuplicateExecutionException
   {
      if(running.containsKey(executionUuid))
      {
         throw (new DuplicateExecutionException(executionUuid));
      }
      long mine = running.values().stream().filter(r -> Objects.equals(r.userId, userId)).count();
      if(mine >= maxPerUser)
      {
         return (null);
      }
      Running registration = new Running(userId);
      if(running.putIfAbsent(executionUuid, registration) != null)
      {
         throw (new DuplicateExecutionException(executionUuid));
      }
      return (registration);
   }



   /*******************************************************************************
    ** Remove this registration - only if it is still the one under the id.
    *******************************************************************************/
   public void unregister(String executionUuid, Running registration)
   {
      running.remove(executionUuid, registration);
   }



   /*******************************************************************************
    ** Whether an execution with this id is in flight.
    *******************************************************************************/
   public boolean isRunning(String executionUuid)
   {
      return (executionUuid != null && running.containsKey(executionUuid));
   }



   /*******************************************************************************
    ** Cancel an execution, only for its owner.
    **
    ** @return whether a running execution of this user was found
    *******************************************************************************/
   public boolean cancel(String executionUuid, String userId)
   {
      Running registration = running.get(executionUuid);
      if(registration == null || !Objects.equals(registration.userId, userId))
      {
         return (false);
      }
      registration.cancelled = true;
      cancelQuietly(registration.statement);
      return (true);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public int size()
   {
      return (running.size());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void cancelQuietly(Statement statement)
   {
      if(statement == null)
      {
         return;
      }
      try
      {
         statement.cancel();
      }
      catch(Exception e)
      {
         LOG.info("Could not cancel a SQL Workbench statement", logPair("message", e.getMessage()));
      }
   }

}
