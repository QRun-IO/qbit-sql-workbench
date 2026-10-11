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
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;


/*******************************************************************************
 ** In-memory, owner-scoped cache of collected results, so the UI can page,
 ** sort and export without re-running SQL. Bounded by entry count (LRU) and
 ** TTL. Entries belong to the user who ran the SQL; any other user gets
 ** nothing (and the API turns that into a 404).
 *******************************************************************************/
public class ResultCache
{
   private final int   maxEntries;
   private final long  ttlMillis;
   private final Clock clock;

   private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);



   /*******************************************************************************
    ** One cached result.
    **
    ** @param executionUuid the execution's id
    ** @param userId        owner
    ** @param backendName   where it ran
    ** @param result        the collected result
    ** @param createdAt     when it was cached
    *******************************************************************************/
   public record Entry(String executionUuid, String userId, String backendName, CollectedResult result, Instant createdAt)
   {
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public ResultCache(int maxEntries, int ttlSeconds, Clock clock)
   {
      this.maxEntries = maxEntries;
      this.ttlMillis = ttlSeconds * 1000L;
      this.clock = clock;
   }



   /*******************************************************************************
    ** Store a result, evicting expired and least recently used entries.
    *******************************************************************************/
   public synchronized void put(String executionUuid, String userId, String backendName, CollectedResult result)
   {
      purgeExpired();
      entries.put(executionUuid, new Entry(executionUuid, userId, backendName, result, clock.instant()));
      while(entries.size() > maxEntries)
      {
         String eldest = entries.keySet().iterator().next();
         entries.remove(eldest);
      }
   }



   /*******************************************************************************
    ** Get a result, only for its owner and only while it has not expired.
    *******************************************************************************/
   public synchronized Optional<Entry> get(String executionUuid, String userId)
   {
      purgeExpired();
      Entry entry = entries.get(executionUuid);
      if(entry == null || !Objects.equals(entry.userId(), userId))
      {
         return (Optional.empty());
      }
      return (Optional.of(entry));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public synchronized int size()
   {
      purgeExpired();
      return (entries.size());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public synchronized void clear()
   {
      entries.clear();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void purgeExpired()
   {
      Instant cutoff = clock.instant().minusMillis(ttlMillis);
      entries.values().removeIf(e -> e.createdAt().isBefore(cutoff));
   }



   /*******************************************************************************
    ** Rows of a result, optionally sorted by one column (type-aware, nulls
    ** last), then sliced.
    *******************************************************************************/
   public static List<List<Object>> page(CollectedResult result, int offset, int limit, Integer sortColumn, boolean descending)
   {
      List<List<Object>> rows = result.rows();
      if(sortColumn != null && sortColumn >= 0 && sortColumn < result.columns().size())
      {
         rows = new ArrayList<>(rows);
         int column = sortColumn;
         rows.sort((a, b) -> compareNullsLast(a.get(column), b.get(column), descending));
      }

      int from = Math.max(0, Math.min(offset, rows.size()));
      int to   = Math.max(from, Math.min(rows.size(), from + Math.max(0, limit)));
      return (new ArrayList<>(rows.subList(from, to)));
   }



   /*******************************************************************************
    ** Compare two values, nulls last in either direction.
    *******************************************************************************/
   static int compareNullsLast(Object a, Object b, boolean descending)
   {
      if(a == null || b == null)
      {
         return (a == null ? (b == null ? 0 : 1) : -1);
      }
      return (descending ? compareValues(b, a) : compareValues(a, b));
   }



   /*******************************************************************************
    ** Compare two non-null converted values: numbers numerically (numeric
    ** strings too), booleans, else as strings.
    *******************************************************************************/
   static int compareValues(Object a, Object b)
   {
      BigDecimal na = asNumber(a);
      BigDecimal nb = asNumber(b);
      if(na != null && nb != null)
      {
         return (na.compareTo(nb));
      }
      if(a instanceof Boolean ba && b instanceof Boolean bb)
      {
         return (ba.compareTo(bb));
      }
      return (String.valueOf(a).compareTo(String.valueOf(b)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static BigDecimal asNumber(Object value)
   {
      if(value instanceof BigDecimal bd)
      {
         return (bd);
      }
      if(value instanceof Number n)
      {
         return (new BigDecimal(n.toString()));
      }
      if(value instanceof String s && !s.isEmpty() && s.length() < 64)
      {
         char c = s.charAt(0);
         if(Character.isDigit(c) || c == '-' || c == '+' || c == '.')
         {
            try
            {
               return (new BigDecimal(s));
            }
            catch(NumberFormatException e)
            {
               return (null);
            }
         }
      }
      return (null);
   }



   /*******************************************************************************
    ** For tests and diagnostics: the cached execution ids, oldest first.
    *******************************************************************************/
   synchronized List<String> keys()
   {
      return (new ArrayList<>(entries.keySet()));
   }

}
