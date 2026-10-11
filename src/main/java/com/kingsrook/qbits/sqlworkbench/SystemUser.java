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


import com.kingsrook.qqq.backend.core.context.CapturedContext;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;


/*******************************************************************************
 ** Runs a block as the QQQ system user, in the current instance.
 **
 ** The qbit's tables are guarded by the admin permission, which ordinary
 ** workbench users do not hold. The workbench's own services (notebooks,
 ** history, the audit) first check the caller - owner, visibility, share
 ** permission - in code, and only then read or write those tables through
 ** this. Never call it before those checks.
 *******************************************************************************/
public final class SystemUser
{

   /*******************************************************************************
    **
    *******************************************************************************/
   private SystemUser()
   {
   }



   /*******************************************************************************
    ** Run a block as the system user.
    *******************************************************************************/
   public static void run(Block block) throws QException
   {
      call(() ->
      {
         block.run();
         return (null);
      });
   }



   /*******************************************************************************
    ** Run a block as the system user and return its value.
    *******************************************************************************/
   public static <T> T call(Supplier<T> supplier) throws QException
   {
      QInstance qInstance = QContext.getQInstance();
      Object[]  result    = new Object[1];
      QContext.<QException>withTemporaryContext(new CapturedContext(qInstance, new QSystemUserSession()), () -> result[0] = supplier.get());

      @SuppressWarnings("unchecked")
      T value = (T) result[0];
      return (value);
   }



   /*******************************************************************************
    ** A block that may throw QException.
    *******************************************************************************/
   @FunctionalInterface
   public interface Block
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      void run() throws QException;
   }



   /*******************************************************************************
    ** A value-returning block that may throw QException.
    *******************************************************************************/
   @FunctionalInterface
   public interface Supplier<T>
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      T get() throws QException;
   }

}
