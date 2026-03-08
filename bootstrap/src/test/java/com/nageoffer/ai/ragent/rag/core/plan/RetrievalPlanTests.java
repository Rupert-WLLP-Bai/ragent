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

import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import com.nageoffer.ai.ragent.rag.dto.IntentGroup;
import com.nageoffer.ai.ragent.rag.enums.IntentKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalPlanTests {

    @Test
    void shouldResolveMixedModeWhenKbAndMcpBothPresent() {
        RetrievalPlan plan = RetrievalPlan.from(new IntentGroup(
                List.of(score("mcp-1", IntentKind.MCP)),
                List.of(score("kb-1", IntentKind.KB))
        ), 6);

        assertEquals(RetrievalPlan.RetrievalMode.MIXED, plan.retrievalMode());
        assertEquals(6, plan.topK());
        assertTrue(plan.requiresRetrieval());
        assertTrue(plan.includesKb());
        assertTrue(plan.includesMcp());
    }

    @Test
    void shouldResolveSingleChannelModesConservatively() {
        RetrievalPlan kbOnly = RetrievalPlan.from(new IntentGroup(List.of(), List.of(score("kb-1", IntentKind.KB))), 4);
        RetrievalPlan mcpOnly = RetrievalPlan.from(new IntentGroup(List.of(score("mcp-1", IntentKind.MCP)), List.of()), 3);
        RetrievalPlan none = RetrievalPlan.from(new IntentGroup(List.of(), List.of()), 5);

        assertEquals(RetrievalPlan.RetrievalMode.KB_ONLY, kbOnly.retrievalMode());
        assertTrue(kbOnly.includesKb());
        assertFalse(kbOnly.includesMcp());

        assertEquals(RetrievalPlan.RetrievalMode.MCP_ONLY, mcpOnly.retrievalMode());
        assertFalse(mcpOnly.includesKb());
        assertTrue(mcpOnly.includesMcp());

        assertEquals(RetrievalPlan.RetrievalMode.NONE, none.retrievalMode());
        assertFalse(none.requiresRetrieval());
    }

    private static NodeScore score(String id, IntentKind kind) {
        return NodeScore.builder()
                .node(IntentNode.builder().id(id).kind(kind).build())
                .score(0.9D)
                .build();
    }
}
