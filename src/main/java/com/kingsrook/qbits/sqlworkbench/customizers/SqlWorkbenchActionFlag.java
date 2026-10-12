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

package com.kingsrook.qbits.sqlworkbench.customizers;


import com.kingsrook.qqq.backend.core.model.actions.tables.ActionFlag;


/*******************************************************************************
 ** Flags the qbit puts on its own table actions, so the customizers know a
 ** write is part of a larger, already-validated operation. Flags are set in
 ** Java only; no API or UI request can carry them.
 *******************************************************************************/
public enum SqlWorkbenchActionFlag implements ActionFlag
{
   /** a whole-notebook save from NotebookService: it writes one version itself */
   NOTEBOOK_SAVE,

   /** pinning a cached result from the notebook UI (snapshot inserts) */
   SNAPSHOT_PIN,

   /** the qbit's own bookkeeping (version bump, visibility sync, cascades) */
   INTERNAL
}
