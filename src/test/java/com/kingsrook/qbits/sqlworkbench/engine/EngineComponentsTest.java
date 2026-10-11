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


import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchCredentials;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchRuntime;
import com.kingsrook.qbits.sqlworkbench.sql.SqlDialect;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.exceptions.QNotFoundException;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Unit tests for the engine's smaller parts: value conversion, the result
 ** cache, write tokens, running statements, session guard (SQLite), and
 ** target resolution.
 *******************************************************************************/
class EngineComponentsTest
{

   /*******************************************************************************
    ** A clock tests can move.
    *******************************************************************************/
   static class MutableClock extends Clock
   {
      Instant now = Instant.parse("2026-10-11T00:00:00Z");



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public java.time.ZoneId getZone()
      {
         return (ZoneOffset.UTC);
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public Clock withZone(java.time.ZoneId zone)
      {
         return (this);
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public Instant instant()
      {
         return (now);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void testValueConversion() throws Exception
   {
      ResultCollector collector = new ResultCollector(10, 1000, 5);
      assertThat(collector.convert(null)).isNull();
      assertThat(collector.convert(5)).isEqualTo(5);
      assertThat(collector.convert(5L)).isEqualTo(5L);
      assertThat(collector.convert(Long.MAX_VALUE)).isEqualTo(String.valueOf(Long.MAX_VALUE));
      assertThat(collector.convert(new BigInteger("123456789012345678901234"))).isEqualTo("123456789012345678901234");
      assertThat(collector.convert(BigInteger.TEN)).isEqualTo(10L);
      assertThat(collector.convert(new BigDecimal("1.25"))).isEqualTo(new BigDecimal("1.25"));
      assertThat(collector.convert(new BigDecimal("12345678901234567.5"))).isEqualTo("12345678901234567.5");
      assertThat(collector.convert(Double.NaN)).isEqualTo("NaN");
      assertThat(collector.convert(1.5d)).isEqualTo(1.5d);
      assertThat(collector.convert(1.5f)).isEqualTo(1.5d);
      assertThat(collector.convert(Float.POSITIVE_INFINITY)).isEqualTo("Infinity");
      assertThat(collector.convert(new java.util.concurrent.atomic.AtomicInteger(3))).isEqualTo("3");
      assertThat(collector.convert("abcdefgh")).isEqualTo("abcde…");
      assertThat(collector.convert(new byte[] { 1, 2 })).isInstanceOf(Map.class);
      assertThat(collector.convert(Timestamp.valueOf("2026-01-01 10:00:00"))).isEqualTo("2026-01-01T10:00");
      assertThat(collector.convert(java.sql.Date.valueOf("2026-01-01"))).isEqualTo("2026-01-01");
      assertThat(collector.convert(java.sql.Time.valueOf("10:11:12"))).isEqualTo("10:11:12");
      assertThat(collector.convert(LocalDate.of(2026, 1, 2))).isEqualTo("2026-01-02");
      assertThat(collector.convert(true)).isEqualTo(true);

      assertThat(ResultCollector.kindOf(Types.OTHER, "jsonb")).isEqualTo("json");
      assertThat(ResultCollector.kindOf(Types.BIT, "bit")).isEqualTo("boolean");
      assertThat(ResultCollector.kindOf(Types.BIT, "bit varying")).isEqualTo("string");
      assertThat(ResultCollector.kindOf(Types.ARRAY, "_int4")).isEqualTo("array");
      assertThat(ResultCollector.kindOf(Types.TIME, "time")).isEqualTo("time");
      assertThat(ResultCollector.kindOf(Types.OTHER, null)).isEqualTo("other");
      assertThat(ResultCollector.isZoned("timestamptz")).isTrue();
      assertThat(ResultCollector.isZoned("TIMESTAMP WITH TIME ZONE")).isTrue();
      assertThat(ResultCollector.isZoned("timestamp")).isFalse();

      assertThat(ResultCollector.estimateBytes(null)).isEqualTo(4);
      assertThat(ResultCollector.estimateBytes(List.of("a", 1))).isGreaterThan(16);
      assertThat(ResultCollector.estimateBytes(Map.of())).isEqualTo(128);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testResultCache()
   {
      MutableClock clock = new MutableClock();
      ResultCache  cache = new ResultCache(2, 60, clock);
      CollectedResult result = new CollectedResult(List.of(new ColumnInfo("n", "n", "int", Types.INTEGER, "number")),
         new ArrayList<>(List.of(Arrays.asList((Object) 3), Arrays.asList((Object) null), Arrays.asList((Object) 1), Arrays.asList((Object) "2"))), false, null, 0);

      cache.put("a", "u1", "db", result);
      cache.put("b", "u1", "db", result);
      cache.get("a", "u1");
      cache.put("c", "u2", "db", result);
      assertThat(cache.keys()).containsExactly("a", "c");
      assertThat(cache.get("a", "u2")).isEmpty();
      assertThat(cache.get("missing", "u1")).isEmpty();

      clock.now = clock.now.plusSeconds(61);
      assertThat(cache.size()).isZero();

      assertThat(ResultCache.page(result, 0, 10, 0, false)).extracting(r -> r.get(0)).containsExactly(1, "2", 3, null);
      assertThat(ResultCache.page(result, 0, 10, 0, true)).extracting(r -> r.get(0)).containsExactly(3, "2", 1, null);
      assertThat(ResultCache.page(result, 1, 2, null, false)).hasSize(2);
      assertThat(ResultCache.page(result, 10, 2, 99, false)).isEmpty();
      assertThat(ResultCache.compareValues(true, false)).isPositive();
      assertThat(ResultCache.compareValues("b", "a")).isPositive();
      assertThat(ResultCache.compareValues("x1", "2")).isPositive();
      cache.clear();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testWritePreviewTokens()
   {
      MutableClock       clock  = new MutableClock();
      WritePreviewTokens tokens = new WritePreviewTokens(60, clock);
      String             token  = tokens.issue("u", "db", "hash", 3L, false);

      assertThat(tokens.find(token, "other", "db", "hash")).isNull();
      assertThat(tokens.find(token, "u", "other", "hash")).isNull();
      assertThat(tokens.find(token, "u", "db", "other")).isNull();
      assertThat(tokens.find(null, "u", "db", "hash")).isNull();
      assertThat(tokens.find(token, "u", "db", "hash").affectedRows()).isEqualTo(3L);
      assertThat(tokens.expiresAt(token)).isAfter(clock.now);
      assertThat(tokens.expiresAt("nope")).isNull();
      assertThat(tokens.consume(token)).isTrue();
      assertThat(tokens.consume(token)).isFalse();
      assertThat(tokens.consume(null)).isFalse();

      String expiring = tokens.issue("u", "db", "hash", null, false);
      clock.now = clock.now.plusSeconds(61);
      assertThat(tokens.find(expiring, "u", "db", "hash")).isNull();
      assertThat(tokens.size()).isZero();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testRunningStatements() throws Exception
   {
      RunningStatements running = new RunningStatements();
      RunningStatements.Running one = running.register("1", "u", 1);
      assertThat(running.register("2", "u", 1)).isNull();
      assertThat(running.size()).isEqualTo(1);

      try(Connection connection = DriverManager.getConnection("jdbc:h2:mem:running"); Statement statement = connection.createStatement())
      {
         one.setStatement(statement);
         assertThat(running.cancel("1", "u")).isTrue();
         assertThat(one.isCancelled()).isTrue();

         ////////////////////////////////////////////////////////////
         // a statement set after the cancel is cancelled at once  //
         ////////////////////////////////////////////////////////////
         one.setStatement(statement);
      }
      one.setStatement(null);
      assertThat(running.cancel("missing", "u")).isFalse();
      running.unregister("1", one);
      assertThat(running.size()).isZero();
   }



   /*******************************************************************************
    ** A re-sent id is refused, never overwrites the original, and the
    ** original's registration is only removed by its own unregister.
    *******************************************************************************/
   @Test
   void testRunningStatementsRefuseDuplicateIds() throws Exception
   {
      RunningStatements running = new RunningStatements();
      RunningStatements.Running original = running.register("dup", "u", 5);
      assertThat(running.isRunning("dup")).isTrue();

      assertThatThrownBy(() -> running.register("dup", "u", 5)).isInstanceOf(DuplicateExecutionException.class);
      assertThatThrownBy(() -> running.register("dup", "someone-else", 5)).isInstanceOf(DuplicateExecutionException.class);
      assertThat(running.size()).isEqualTo(1);

      ///////////////////////////////////////////////////////////////////
      // a stranger's unregister under the same id leaves the original //
      ///////////////////////////////////////////////////////////////////
      RunningStatements.Running other = running.register("other", "u", 5);
      running.unregister("dup", other);
      assertThat(running.isRunning("dup")).isTrue();
      assertThat(running.cancel("dup", "u")).isTrue();
      assertThat(original.isCancelled()).isTrue();

      running.unregister("dup", original);
      running.unregister("other", other);
      assertThat(running.size()).isZero();
      assertThat(running.isRunning("dup")).isFalse();
      assertThat(running.isRunning(null)).isFalse();
   }



   /*******************************************************************************
    ** SQLite: query_only blocks writes in read-only sessions, and is reset so
    ** the same connection writes again afterwards.
    *******************************************************************************/
   @Test
   void testSessionGuardSqlite(@TempDir Path dir) throws Exception
   {
      String url = "jdbc:sqlite:" + dir.resolve("test.db");
      try(Connection setup = DriverManager.getConnection(url); Statement statement = setup.createStatement())
      {
         statement.execute("CREATE TABLE t (id INTEGER)");
      }

      Connection connection = DriverManager.getConnection(url);
      try(SessionGuard guard = new SessionGuard(new NonClosingConnection(connection).proxy(), SqlDialect.SQLITE, true, 5).begin(); Statement statement = guard.getConnection().createStatement())
      {
         assertThatThrownBy(() -> statement.execute("INSERT INTO t VALUES (1)")).isInstanceOf(SQLException.class);
      }

      assertThat(connection.getAutoCommit()).isTrue();
      try(Statement statement = connection.createStatement())
      {
         statement.execute("INSERT INTO t VALUES (1)");
      }

      try(SessionGuard guard = new SessionGuard(new NonClosingConnection(connection).proxy(), SqlDialect.SQLITE, false, 5).begin(); Statement statement = guard.getConnection().createStatement())
      {
         statement.execute("INSERT INTO t VALUES (2)");
         guard.commit();
      }
      try(Statement statement = connection.createStatement(); var rs = statement.executeQuery("SELECT count(*) FROM t"))
      {
         rs.next();
         assertThat(rs.getInt(1)).isEqualTo(2);
      }
      connection.close();
      assertThat(Files.exists(dir.resolve("test.db"))).isTrue();
   }



   /*******************************************************************************
    ** A guard over a closed connection cannot restore it, and aborts it.
    *******************************************************************************/
   @Test
   void testSessionGuardAbortsWhenRestoreFails() throws Exception
   {
      Connection   connection = DriverManager.getConnection("jdbc:h2:mem:abort");
      SessionGuard guard      = new SessionGuard(connection, SqlDialect.H2, true, 5).begin();
      connection.close();
      guard.close();
      assertThat(connection.isClosed()).isTrue();

      Connection   other = DriverManager.getConnection("jdbc:h2:mem:abort2");
      SessionGuard plain = new SessionGuard(other, SqlDialect.H2, false, 5);
      plain.close();
      assertThat(other.isClosed()).isTrue();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testBackendsAndDerivedCredentials() throws Exception
   {
      QInstance            instance = new QInstance();
      RDBMSBackendMetaData db       = new RDBMSBackendMetaData().withName("db").withVendor("h2").withHostName("mem").withDatabaseName("x");
      RDBMSBackendMetaData replica  = new RDBMSBackendMetaData().withName("replica").withVendor("h2").withHostName("mem").withDatabaseName("y");
      db.setReadOnlyBackendMetaData(replica);
      instance.addBackend(db);
      instance.addBackend(new RDBMSBackendMetaData().withName("skip").withVendor("h2").withHostName("mem").withDatabaseName("z"));

      SqlWorkbenchQBitConfig config  = new SqlWorkbenchQBitConfig().withBackendName("db").withExcludedBackendNames(List.of("skip"));
      SqlWorkbenchRuntime    runtime = new SqlWorkbenchRuntime(config);
      WorkbenchBackends      backends = new WorkbenchBackends(instance, runtime);

      assertThat(backends.listTargets()).extracting(WorkbenchBackends.Target::name).containsExactly("db");
      assertThatThrownBy(() -> backends.getTarget("skip")).isInstanceOf(QNotFoundException.class);
      assertThatThrownBy(() -> backends.getTarget(null)).isInstanceOf(QNotFoundException.class);

      WorkbenchBackends.Target target = backends.getTarget("db");
      assertThat(backends.connectionSource(target, true)).isSameAs(replica);
      assertThat(backends.connectionSource(target, false)).isSameAs(db);
      assertThat(backends.hasReadOnlyCredentials(target)).isFalse();

      config.withReadOnlyCredentials("db", new SqlWorkbenchCredentials().withUsername("${env.NO_SUCH_VAR_SQLWB}").withPassword("pw"))
         .withWriteCredentials("db", new SqlWorkbenchCredentials().withUsername("writer").withJdbcUrl("jdbc:h2:mem:custom"));
      RDBMSBackendMetaData readOnly = backends.connectionSource(target, true);
      assertThat(readOnly.getName()).isEqualTo("db" + WorkbenchBackends.READ_ONLY_SUFFIX);
      assertThat(readOnly.getPassword()).isEqualTo("pw");
      assertThat(readOnly.getJdbcUrl()).contains("jdbc:h2:mem:x");
      assertThat(backends.connectionSource(target, true)).isSameAs(readOnly);
      assertThat(backends.hasReadOnlyCredentials(target)).isTrue();
      assertThat(backends.connectionSource(target, false).getJdbcUrl()).isEqualTo("jdbc:h2:mem:custom");

      config.setAllowedBackendNames(List.of("nothing"));
      assertThat(backends.listTargets()).isEmpty();
      assertThatThrownBy(() -> backends.getTarget("db" + WorkbenchBackends.READ_ONLY_SUFFIX)).isInstanceOf(QNotFoundException.class);

      config.setUseBackendReadOnlyReplica(false);
      config.setReadOnlyCredentials(Map.of());
      assertThat(backends.connectionSource(target, true)).isSameAs(db);
      runtime.close();
   }



   /*******************************************************************************
    ** Wraps a connection so close() does not close it (to inspect it after a
    ** guard released it).
    *******************************************************************************/
   static class NonClosingConnection
   {
      private final Connection delegate;



      /*******************************************************************************
       **
       *******************************************************************************/
      NonClosingConnection(Connection delegate)
      {
         this.delegate = delegate;
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      Connection proxy()
      {
         return ((Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] { Connection.class }, (p, method, args) ->
         {
            if("close".equals(method.getName()))
            {
               return (null);
            }
            try
            {
               return (method.invoke(delegate, args));
            }
            catch(java.lang.reflect.InvocationTargetException e)
            {
               throw (e.getCause());
            }
         }));
      }
   }

}
