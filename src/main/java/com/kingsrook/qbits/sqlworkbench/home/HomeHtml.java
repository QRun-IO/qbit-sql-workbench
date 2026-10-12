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


import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


/*******************************************************************************
 ** Small, theme-neutral HTML pieces for the home widgets: inline styles only
 ** (no host CSS), colors that read in light and dark themes, text that wraps
 ** at any width (no inner horizontal scroll), and every value escaped.
 *******************************************************************************/
public final class HomeHtml
{
   public static final String OK_COLOR   = "#16a34a";
   public static final String BAD_COLOR  = "#dc2626";
   public static final String WARN_COLOR = "#d97706";
   public static final String INFO_COLOR = "#2563eb";
   public static final String OFF_COLOR  = "#6b7280";

   private static final String MUTED = "opacity:.75";
   private static final String WRAP  = "overflow-wrap:anywhere;min-width:0";
   private static final String TILE  = "flex:1 1 170px;min-width:0;border:1px solid rgba(127,127,127,.25);border-radius:10px;padding:12px 14px";

   private static final DateTimeFormatter TIME      = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
   private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US);
   private static final DateTimeFormatter FULL      = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.US);



   /*******************************************************************************
    ** One summary tile: a label, a value (linked when href is set) and an
    ** optional note.
    *******************************************************************************/
   public record Tile(String label, String value, String note, String href)
   {
   }



   /*******************************************************************************
    ** Utility class - no instances.
    *******************************************************************************/
   private HomeHtml()
   {
   }



   /*******************************************************************************
    ** HTML-escape a value (null is empty).
    *******************************************************************************/
   public static String escape(Object value)
   {
      if(value == null)
      {
         return ("");
      }
      String        text    = String.valueOf(value);
      StringBuilder escaped = new StringBuilder(text.length() + 16);
      for(char c : text.toCharArray())
      {
         switch(c)
         {
            case '<' -> escaped.append("&lt;");
            case '>' -> escaped.append("&gt;");
            case '&' -> escaped.append("&amp;");
            case '"' -> escaped.append("&quot;");
            case '\'' -> escaped.append("&#39;");
            default -> escaped.append(c);
         }
      }
      return (escaped.toString());
   }



   /*******************************************************************************
    ** A link in the text color, underlined.
    *******************************************************************************/
   public static String link(String href, Object text)
   {
      return ("<a href=\"" + escape(href) + "\" style=\"color:inherit;text-decoration:underline;text-underline-offset:2px\">" + escape(text) + "</a>");
   }



   /*******************************************************************************
    ** Muted (secondary) text.
    *******************************************************************************/
   public static String muted(Object text)
   {
      return ("<span style=\"" + MUTED + ";font-size:13px\">" + escape(text) + "</span>");
   }



   /*******************************************************************************
    ** A pill with a colored border and text.
    *******************************************************************************/
   public static String badge(String text, String color)
   {
      return ("<span style=\"display:inline-block;white-space:nowrap;padding:1px 8px;border-radius:999px;font-size:12px;font-weight:600;line-height:1.5;"
         + "color:" + color + ";border:1px solid " + color + "\">" + escape(text) + "</span>");
   }



   /*******************************************************************************
    ** A primary line (already HTML) with muted facts (already HTML) after it,
    ** wrapping onto more lines on narrow screens.
    *******************************************************************************/
   public static String card(String primaryHtml, String... factsHtml)
   {
      List<String> facts = new ArrayList<>();
      for(String fact : factsHtml)
      {
         if(fact != null && !fact.isBlank())
         {
            facts.add(fact);
         }
      }
      StringBuilder html = new StringBuilder("<span style=\"display:flex;flex-wrap:wrap;align-items:baseline;column-gap:10px;row-gap:2px;min-width:0\">");
      html.append("<span style=\"").append(WRAP).append("\">").append(primaryHtml == null ? "" : primaryHtml).append("</span>");
      if(!facts.isEmpty())
      {
         html.append("<span style=\"").append(WRAP).append(';').append(MUTED).append(";font-size:13px\">").append(String.join(" &middot; ", facts)).append("</span>");
      }
      return (html.append("</span>").toString());
   }



   /*******************************************************************************
    ** SQL (or any long text) as monospace, on one line, cut at maxChars and
    ** wrapped anywhere; the full text is in the title tooltip.
    *******************************************************************************/
   public static String code(String text, int maxChars)
   {
      String oneLine = text == null ? "" : text.replaceAll("\\s+", " ").trim();
      String shown   = oneLine.length() > maxChars ? oneLine.substring(0, maxChars - 1) + "…" : oneLine;
      return ("<code title=\"" + escape(oneLine.length() > 2000 ? oneLine.substring(0, 2000) : oneLine) + "\" style=\"font-size:12px;" + WRAP + "\">" + escape(shown) + "</code>");
   }



   /*******************************************************************************
    ** Wrapping summary tiles: they grow to fill each row, so there are no holes.
    *******************************************************************************/
   public static String tiles(List<Tile> tiles)
   {
      StringBuilder html = new StringBuilder("<div style=\"display:flex;flex-wrap:wrap;gap:12px\">");
      for(Tile tile : tiles)
      {
         html.append("<div style=\"").append(TILE).append("\">");
         html.append("<div style=\"font-size:12px;").append(MUTED).append(";text-transform:uppercase;letter-spacing:.04em;").append(WRAP).append("\">")
            .append(escape(tile.label())).append("</div>");
         String value = tile.href() == null ? escape(tile.value())
            : "<a href=\"" + escape(tile.href()) + "\" style=\"color:inherit;text-decoration:none\">" + escape(tile.value()) + "</a>";
         html.append("<div style=\"font-size:26px;font-weight:700;line-height:1.3;").append(WRAP).append("\">").append(value).append("</div>");
         if(tile.note() != null)
         {
            html.append("<div style=\"").append(MUTED).append(";font-size:13px;").append(WRAP).append("\">").append(escape(tile.note())).append("</div>");
         }
         html.append("</div>");
      }
      return (html.append("</div>").toString());
   }



   /*******************************************************************************
    ** Link buttons, wrapping.
    *******************************************************************************/
   public static String actions(List<String[]> labelsAndHrefs)
   {
      StringBuilder html = new StringBuilder("<div style=\"display:flex;flex-wrap:wrap;gap:8px;margin-top:12px\">");
      for(String[] action : labelsAndHrefs)
      {
         html.append("<a href=\"").append(escape(action[1]))
            .append("\" style=\"display:inline-flex;align-items:center;padding:8px 12px;border-radius:8px;border:1px solid rgba(127,127,127,.3);")
            .append("background:rgba(127,127,127,.08);color:inherit;text-decoration:none;font-size:13px;font-weight:500\">")
            .append(escape(action[0])).append("</a>");
      }
      return (html.append("</div>").toString());
   }



   /*******************************************************************************
    ** An honest empty or unavailable state.
    *******************************************************************************/
   public static String emptyState(String message)
   {
      return ("<div style=\"padding:18px 4px;" + MUTED + ";font-size:14px;" + WRAP + "\">" + escape(message) + "</div>");
   }



   /*******************************************************************************
    ** A muted explanatory note under a widget's content.
    *******************************************************************************/
   public static String note(String text)
   {
      return ("<div style=\"margin-top:10px;" + MUTED + ";font-size:13px;" + WRAP + "\">" + escape(text) + "</div>");
   }



   /*******************************************************************************
    ** A whole number with thousands separators.
    *******************************************************************************/
   public static String number(long value)
   {
      return (String.format(Locale.US, "%,d", value));
   }



   /*******************************************************************************
    ** "1 run", "2 runs".
    *******************************************************************************/
   public static String plural(long count, String noun)
   {
      return (number(count) + " " + noun + (count == 1 ? "" : "s"));
   }



   /*******************************************************************************
    ** A duration in milliseconds: "850 ms", "4.2 s", "3m 05s".
    *******************************************************************************/
   public static String duration(Integer millis)
   {
      if(millis == null)
      {
         return ("");
      }
      if(millis < 1000)
      {
         return (millis + " ms");
      }
      if(millis < 60_000)
      {
         return (String.format(Locale.US, "%.1f s", millis / 1000.0));
      }
      long seconds = millis / 1000;
      return (String.format(Locale.US, "%dm %02ds", seconds / 60, seconds % 60));
   }



   /*******************************************************************************
    ** An instant, compact: the time today, the date and time this year, the
    ** full date otherwise.
    *******************************************************************************/
   public static String when(Instant instant, Instant now, ZoneId zoneId)
   {
      if(instant == null)
      {
         return ("");
      }
      ZonedDateTime when  = instant.atZone(zoneId);
      ZonedDateTime today = now.atZone(zoneId);
      if(when.toLocalDate().equals(today.toLocalDate()))
      {
         return (TIME.format(when));
      }
      if(when.getYear() == today.getYear())
      {
         return (DATE_TIME.format(when));
      }
      return (FULL.format(when));
   }

}
