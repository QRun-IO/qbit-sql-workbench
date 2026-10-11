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


import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Executors;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qbits.sqlworkbench.sql.SqlDialect;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Wraps one workbench session on one connection: puts it in a transaction
 ** (read-only where the database supports it), applies the statement
 ** timeout, and on close always ends the transaction (rollback unless
 ** committed) and restores every connection property it changed, so the
 ** connection goes back to the pool clean. If restoring fails, the
 ** connection is aborted rather than returned dirty.
 **
 ** All engine-side settings are transaction-scoped (SET LOCAL) or reverted
 ** here; user SQL can never change session state (the classifier forbids
 ** SET / RESET / USE / PRAGMA assignments / transaction control).
 *******************************************************************************/
public class SessionGuard implements AutoCloseable
{
   private static final QLogger LOG = QLogger.getLogger(SessionGuard.class);

   private final Connection connection;
   private final SqlDialect dialect;
   private final boolean    readOnly;
   private final int        timeoutSeconds;

   private boolean originalAutoCommit = true;
   private boolean originalReadOnly   = false;
   private boolean sqliteQueryOnly    = false;
   private boolean committed          = false;
   private boolean began              = false;



   /*******************************************************************************
    **
    *******************************************************************************/
   public SessionGuard(Connection connection, SqlDialect dialect, boolean readOnly, int timeoutSeconds)
   {
      this.connection = connection;
      this.dialect = dialect;
      this.readOnly = readOnly;
      this.timeoutSeconds = timeoutSeconds;
   }



   /*******************************************************************************
    ** Start the session's transaction.
    *******************************************************************************/
   public SessionGuard begin() throws SQLException
   {
      originalAutoCommit = connection.getAutoCommit();
      originalReadOnly = connection.isReadOnly();
      began = true;

      if(readOnly && dialect == SqlDialect.SQLITE)
      {
         execute("PRAGMA query_only = 1");
         sqliteQueryOnly = true;
      }

      if(readOnly && dialect != SqlDialect.SQLITE)
      {
         ////////////////////////////////////////////////////////////////////
         // before the transaction starts: pgjdbc then begins it READ ONLY, //
         // Connector/J sets the session's transaction characteristic      //
         // (reverted in close), H2 treats it as a hint.                    //
         ////////////////////////////////////////////////////////////////////
         connection.setReadOnly(true);
      }

      connection.setAutoCommit(false);

      if(dialect == SqlDialect.POSTGRES)
      {
         if(readOnly)
         {
            execute("SET TRANSACTION READ ONLY");
         }
         long millis = timeoutSeconds * 1000L;
         execute("SET LOCAL statement_timeout = " + millis);
         execute("SET LOCAL lock_timeout = " + millis);
         execute("SET LOCAL idle_in_transaction_session_timeout = " + (millis + 60_000L));
      }
      return (this);
   }



   /*******************************************************************************
    ** Apply the session's limits to a statement.
    *******************************************************************************/
   public void configure(Statement statement, int maxRows, int fetchSize) throws SQLException
   {
      statement.setQueryTimeout(timeoutSeconds);
      if(maxRows > 0)
      {
         statement.setMaxRows(maxRows);
      }
      if(fetchSize > 0)
      {
         //////////////////////////////////////////////////////
         // some drivers (H2) refuse a fetch size > max rows //
         //////////////////////////////////////////////////////
         statement.setFetchSize(maxRows > 0 ? Math.min(fetchSize, maxRows) : fetchSize);
      }
   }



   /*******************************************************************************
    ** Commit (confirmed writes only).
    *******************************************************************************/
   public void commit() throws SQLException
   {
      connection.commit();
      committed = true;
   }



   /*******************************************************************************
    ** Roll back now (the guard still restores and closes later).
    *******************************************************************************/
   public void rollback() throws SQLException
   {
      connection.rollback();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public Connection getConnection()
   {
      return (connection);
   }



   /*******************************************************************************
    ** End the transaction, restore the connection, and release it.
    *******************************************************************************/
   @Override
   public void close()
   {
      boolean clean = true;
      try
      {
         if(began && !committed)
         {
            connection.rollback();
         }
         if(sqliteQueryOnly)
         {
            connection.setAutoCommit(true);
            execute("PRAGMA query_only = 0");
         }
         if(began)
         {
            connection.setAutoCommit(originalAutoCommit);
            if(connection.isReadOnly() != originalReadOnly)
            {
               connection.setReadOnly(originalReadOnly);
            }
         }
      }
      catch(Exception e)
      {
         clean = false;
         LOG.warn("SQL Workbench could not restore a connection; aborting it", logPair("dialect", dialect), logPair("message", e.getMessage()));
      }

      try
      {
         if(clean)
         {
            connection.close();
         }
         else
         {
            abort();
         }
      }
      catch(Exception e)
      {
         LOG.warn("SQL Workbench could not release a connection", logPair("message", e.getMessage()));
      }
   }



   /*******************************************************************************
    ** Abort a connection that could not be restored, so a pool never reuses it.
    *******************************************************************************/
   private void abort() throws SQLException
   {
      try
      {
         connection.abort(Executors.newVirtualThreadPerTaskExecutor());
      }
      catch(SQLException | RuntimeException | AbstractMethodError e)
      {
         connection.close();
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void execute(String sql) throws SQLException
   {
      try(Statement statement = connection.createStatement())
      {
         statement.execute(sql);
      }
   }

}
