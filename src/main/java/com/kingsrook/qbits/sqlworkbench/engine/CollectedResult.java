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


import java.util.List;


/*******************************************************************************
 ** A result set, read into memory within the configured limits.
 **
 ** @param columns         the columns
 ** @param rows            the rows (values already converted to JSON-safe
 **                        types by ResultCollector)
 ** @param truncated       whether a limit stopped collection
 ** @param truncatedReason ROWS or BYTES when truncated, else null
 ** @param estimatedBytes  estimated size of the collected values
 *******************************************************************************/
public record CollectedResult(List<ColumnInfo> columns, List<List<Object>> rows, boolean truncated, String truncatedReason, long estimatedBytes)
{
}
