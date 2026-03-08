/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.rag.core.plan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestPlanTests {

    @Test
    void shouldStartWithConservativeDefaults() {
        RequestPlan plan = RequestPlan.start(false);

        assertEquals(RequestPlan.QueryType.STANDARD, plan.queryType());
        assertEquals(RequestPlan.RewriteMode.REWRITE_WITH_SPLIT, plan.rewriteMode());
        assertEquals(RequestPlan.ResponseMode.RAG, plan.responseMode());
        assertEquals(RequestPlan.ThinkingMode.STANDARD, plan.thinkingMode());
        assertEquals(RetrievalPlan.RetrievalMode.NONE, plan.retrievalPlan().retrievalMode());
        assertFalse(plan.deepThinkingEnabled());
    }

    @Test
    void shouldSwitchToDeepThinkingModeWhenRequested() {
        RequestPlan plan = RequestPlan.start(true);

        assertEquals(RequestPlan.ThinkingMode.DEEP, plan.thinkingMode());
        assertTrue(plan.deepThinkingEnabled());
    }

    @Test
    void shouldUseConservativeResponseTransitions() {
        RequestPlan base = RequestPlan.start(true)
                .withRetrievalPlan(new RetrievalPlan(RetrievalPlan.RetrievalMode.MIXED, 5));

        RequestPlan guidance = base.forGuidancePrompt();
        RequestPlan systemOnly = base.forSystemOnly();
        RequestPlan emptyHit = base.forEmptyHit();

        assertEquals(RequestPlan.ResponseMode.GUIDANCE_PROMPT, guidance.responseMode());
        assertEquals(RetrievalPlan.RetrievalMode.NONE, guidance.retrievalPlan().retrievalMode());
        assertEquals(RequestPlan.ThinkingMode.STANDARD, guidance.thinkingMode());

        assertEquals(RequestPlan.QueryType.SYSTEM_ONLY, systemOnly.queryType());
        assertEquals(RequestPlan.ResponseMode.DIRECT_LLM, systemOnly.responseMode());
        assertEquals(RetrievalPlan.RetrievalMode.NONE, systemOnly.retrievalPlan().retrievalMode());
        assertEquals(RequestPlan.ThinkingMode.STANDARD, systemOnly.thinkingMode());

        assertEquals(RequestPlan.ResponseMode.EMPTY_HIT, emptyHit.responseMode());
        assertEquals(RetrievalPlan.RetrievalMode.MIXED, emptyHit.retrievalPlan().retrievalMode());
        assertEquals(RequestPlan.ThinkingMode.DEEP, emptyHit.thinkingMode());
    }
}
