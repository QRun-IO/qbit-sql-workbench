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


import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


/*******************************************************************************
 ** Single-use confirmation tokens for write mode. A preview issues a token
 ** bound to the user, backend and SQL hash (SQL plus parameters); a commit
 ** must present it before it expires. Tokens live only in memory, so a
 ** restart forces a new preview.
 *******************************************************************************/
public class WritePreviewTokens
{
   private static final SecureRandom RANDOM = new SecureRandom();

   private final Map<String, Preview> previews = new ConcurrentHashMap<>();
   private final long                 ttlMillis;
   private final Clock                clock;



   /*******************************************************************************
    ** What a preview found.
    **
    ** @param userId            who previewed
    ** @param backendName       where
    ** @param sqlHash           hash of SQL plus parameters
    ** @param affectedRows      total rows the dry run affected (null when no
    **                          dry run was possible)
    ** @param rowGuardExceeded  whether affectedRows exceeded the guard
    ** @param expiresAt         when the token stops working
    *******************************************************************************/
   public record Preview(String userId, String backendName, String sqlHash, Long affectedRows, boolean rowGuardExceeded, Instant expiresAt)
   {
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public WritePreviewTokens(int ttlSeconds, Clock clock)
   {
      this.ttlMillis = ttlSeconds * 1000L;
      this.clock = clock;
   }



   /*******************************************************************************
    ** Issue a token for a preview.
    *******************************************************************************/
   public String issue(String userId, String backendName, String sqlHash, Long affectedRows, boolean rowGuardExceeded)
   {
      purgeExpired();
      byte[] bytes = new byte[24];
      RANDOM.nextBytes(bytes);
      String token = HexFormat.of().formatHex(bytes);
      previews.put(token, new Preview(userId, backendName, sqlHash, affectedRows, rowGuardExceeded, clock.instant().plusMillis(ttlMillis)));
      return (token);
   }



   /*******************************************************************************
    ** When the token expires (for responses), or null for an unknown token.
    *******************************************************************************/
   public Instant expiresAt(String token)
   {
      Preview preview = previews.get(token);
      return (preview == null ? null : preview.expiresAt());
   }



   /*******************************************************************************
    ** Look up a token without consuming it.
    **
    ** @return the preview, or null when the token is unknown, expired, or bound
    **         to a different user, backend or SQL
    *******************************************************************************/
   public Preview find(String token, String userId, String backendName, String sqlHash)
   {
      if(token == null)
      {
         return (null);
      }
      Preview preview = previews.get(token);
      if(preview == null || preview.expiresAt().isBefore(clock.instant()))
      {
         return (null);
      }
      if(!Objects.equals(preview.userId(), userId) || !Objects.equals(preview.backendName(), backendName) || !Objects.equals(preview.sqlHash(), sqlHash))
      {
         return (null);
      }
      return (preview);
   }



   /*******************************************************************************
    ** Consume a token: it can never be used again.
    **
    ** @return whether this call removed it (false if another commit won)
    *******************************************************************************/
   public boolean consume(String token)
   {
      return (token != null && previews.remove(token) != null);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void purgeExpired()
   {
      Instant now = clock.instant();
      previews.values().removeIf(p -> p.expiresAt().isBefore(now));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public int size()
   {
      purgeExpired();
      return (previews.size());
   }

}
