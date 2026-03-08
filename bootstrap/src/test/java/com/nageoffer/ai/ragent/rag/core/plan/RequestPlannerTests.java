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

import com.nageoffer.ai.ragent.rag.core.guidance.GuidanceDecision;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import com.nageoffer.ai.ragent.rag.dto.IntentGroup;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import com.nageoffer.ai.ragent.rag.enums.IntentKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestPlannerTests {

    private final RequestPlanner planner = new RequestPlanner();

    @Test
    void shouldKeepSimpleRequestOnLowCostPath() {
        RequestPlan initialPlan = planner.createInitialPlan("请假政策", false);
        RequestPlan finalPlan = planner.finalizePlan(
                initialPlan,
                "请假政策",
                List.of("请假政策"),
                List.of(new SubQuestionIntent("请假政策", List.of(score("kb-1", IntentKind.KB)))),
                GuidanceDecision.none(),
                new IntentGroup(List.of(), List.of(score("kb-1", IntentKind.KB))),
                false,
                false,
                false
        );

        assertEquals(RequestPlan.RewriteMode.NORMALIZE_ONLY, initialPlan.rewriteMode());
        assertEquals(RequestPlan.QueryType.SIMPLE_KB, finalPlan.queryType());
        assertEquals(RequestPlan.ThinkingMode.STANDARD, finalPlan.thinkingMode());
        assertEquals(RequestPlan.ModelTier.LOW_COST, finalPlan.modelTier());
        assertFalse(finalPlan.decisionReasons().isEmpty());
    }

    @Test
    void shouldEscalateComplexMixedRequestConservatively() {
        RequestPlan initialPlan = planner.createInitialPlan("帮我查一下请假制度并确认审批状态以及还剩多少年假", false);
        RequestPlan finalPlan = planner.finalizePlan(
                initialPlan,
                "帮我查一下请假制度并确认审批状态以及还剩多少年假",
                List.of("请假制度", "审批状态", "年假余额"),
                List.of(
                        new SubQuestionIntent("请假制度", List.of(score("kb-1", IntentKind.KB))),
                        new SubQuestionIntent("审批状态", List.of(score("mcp-1", IntentKind.MCP)))
                ),
                GuidanceDecision.none(),
                new IntentGroup(List.of(score("mcp-1", IntentKind.MCP)), List.of(score("kb-1", IntentKind.KB))),
                false,
                false,
                false
        );

        assertEquals(RequestPlan.QueryType.MIXED, finalPlan.queryType());
        assertEquals(RequestPlan.ThinkingMode.DEEP, finalPlan.thinkingMode());
        assertEquals(RequestPlan.ModelTier.DEEP_THINKING, finalPlan.modelTier());
        assertEquals(RetrievalPlan.RetrievalMode.MIXED, finalPlan.retrievalPlan().retrievalMode());
        assertTrue(finalPlan.decisionReasons().stream().anyMatch(reason -> reason.contains("mixed route") || reason.contains("complexity")));
    }

    @Test
    void shouldEscalateMultiHopRequestWithoutMixedSignals() {
        RequestPlan initialPlan = planner.createInitialPlan("请依次说明入职流程、转正标准、绩效申诉渠道", false);
        RequestPlan finalPlan = planner.finalizePlan(
                initialPlan,
                "请依次说明入职流程、转正标准、绩效申诉渠道",
                List.of("入职流程", "转正标准", "绩效申诉渠道"),
                List.of(
                        new SubQuestionIntent("入职流程", List.of(score("kb-1", IntentKind.KB))),
                        new SubQuestionIntent("转正标准", List.of(score("kb-2", IntentKind.KB))),
                        new SubQuestionIntent("绩效申诉渠道", List.of(score("kb-3", IntentKind.KB)))
                ),
                GuidanceDecision.none(),
                new IntentGroup(List.of(), List.of(
                        score("kb-1", IntentKind.KB),
                        score("kb-2", IntentKind.KB),
                        score("kb-3", IntentKind.KB)
                )),
                false,
                false,
                false
        );

        assertEquals(RequestPlan.QueryType.MULTI_HOP, finalPlan.queryType());
        assertEquals(RequestPlan.ThinkingMode.DEEP, finalPlan.thinkingMode());
        assertEquals(RequestPlan.ModelTier.DEEP_THINKING, finalPlan.modelTier());
        assertTrue(finalPlan.decisionReasons().stream().anyMatch(reason -> reason.contains("multi-hop route")));
    }

    @Test
    void shouldEmitClarifyRouteForAmbiguousRequest() {
        RequestPlan initialPlan = planner.createInitialPlan("帮我看看这个", false);
        RequestPlan finalPlan = planner.finalizePlan(
                initialPlan,
                "帮我看看这个",
                List.of("帮我看看这个"),
                List.of(),
                GuidanceDecision.prompt("你是指审批还是假期余额？"),
                new IntentGroup(List.of(), List.of()),
                false,
                false,
                false
        );

        assertEquals(RequestPlan.QueryType.AMBIGUOUS, finalPlan.queryType());
        assertEquals(RequestPlan.ResponseMode.GUIDANCE_PROMPT, finalPlan.responseMode());
        assertEquals(RequestPlan.ModelTier.LOW_COST, finalPlan.modelTier());
        assertTrue(finalPlan.toTracePayload().containsKey("selectedModelTier"));
    }

    private static NodeScore score(String id, IntentKind kind) {
        return NodeScore.builder()
                .node(IntentNode.builder().id(id).kind(kind).build())
                .score(0.9D)
                .build();
    }
}
