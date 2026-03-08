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
        RequestPlan plan = RequestPlan.initial(
                RequestPlan.RewriteMode.NORMALIZE_ONLY,
                RequestPlan.ThinkingMode.STANDARD,
                RequestPlan.ModelTier.LOW_COST,
                java.util.List.of("default low-cost path")
        );

        assertEquals(RequestPlan.QueryType.STANDARD, plan.queryType());
        assertEquals(RequestPlan.RewriteMode.NORMALIZE_ONLY, plan.rewriteMode());
        assertEquals(RequestPlan.ResponseMode.RAG, plan.responseMode());
        assertEquals(RequestPlan.ThinkingMode.STANDARD, plan.thinkingMode());
        assertEquals(RequestPlan.ModelTier.LOW_COST, plan.modelTier());
        assertEquals(RetrievalPlan.RetrievalMode.NONE, plan.retrievalPlan().retrievalMode());
        assertFalse(plan.deepThinkingEnabled());
    }

    @Test
    void shouldSwitchToDeepThinkingModeWhenRequested() {
        RequestPlan plan = RequestPlan.initial(
                RequestPlan.RewriteMode.REWRITE_WITH_SPLIT,
                RequestPlan.ThinkingMode.DEEP,
                RequestPlan.ModelTier.DEEP_THINKING,
                java.util.List.of("user requested deep thinking")
        );

        assertEquals(RequestPlan.ThinkingMode.DEEP, plan.thinkingMode());
        assertEquals(RequestPlan.ModelTier.DEEP_THINKING, plan.modelTier());
        assertTrue(plan.deepThinkingEnabled());
    }

    @Test
    void shouldExposeStructuredTracePayload() {
        RequestPlan plan = RequestPlan.initial(
                        RequestPlan.RewriteMode.NORMALIZE_ONLY,
                        RequestPlan.ThinkingMode.STANDARD,
                        RequestPlan.ModelTier.LOW_COST,
                        java.util.List.of("simple request")
                )
                .withDecision(
                        RequestPlan.QueryType.SIMPLE_KB,
                        new RetrievalPlan(RetrievalPlan.RetrievalMode.KB_ONLY, 5),
                        RequestPlan.ResponseMode.RAG,
                        RequestPlan.ThinkingMode.STANDARD,
                        RequestPlan.ModelTier.LOW_COST,
                        java.util.List.of("simple kb route")
                );

        assertEquals("SIMPLE_KB", plan.toTracePayload().get("queryType"));
        assertEquals("NORMALIZE_ONLY", plan.toTracePayload().get("rewriteMode"));
        assertEquals("KB_ONLY", plan.toTracePayload().get("retrievalMode"));
        assertEquals("RAG", plan.toTracePayload().get("responseMode"));
        assertEquals("STANDARD", plan.toTracePayload().get("thinkingMode"));
        assertEquals("LOW_COST", plan.toTracePayload().get("selectedModelTier"));
    }
}
