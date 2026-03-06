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

package com.nageoffer.ai.ragent.rag.core.intent;

import com.nageoffer.ai.ragent.rag.core.rewrite.RewriteResult;
import com.nageoffer.ai.ragent.rag.dto.IntentGroup;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import com.nageoffer.ai.ragent.rag.enums.IntentKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentResolverTests {

    private final Executor directExecutor = Runnable::run;

    @Test
    void shouldResolveSubQuestionsAndCapTotalIntentCount() {
        IntentResolver resolver = new IntentResolver(new StubIntentClassifier(Map.of(
                "q1", List.of(score("a", IntentKind.KB, null, 0.95), score("b", IntentKind.KB, null, 0.85)),
                "q2", List.of(score("c", IntentKind.KB, null, 0.75), score("d", IntentKind.KB, null, 0.65))
        )), directExecutor);

        List<SubQuestionIntent> result = resolver.resolve(new RewriteResult("ignored", List.of("q1", "q2")));

        assertEquals(2, result.size());
        assertEquals(List.of("a", "b"), result.get(0).nodeScores().stream().map(ns -> ns.getNode().getId()).toList());
        assertEquals(List.of("c"), result.get(1).nodeScores().stream().map(ns -> ns.getNode().getId()).toList());
    }

    @Test
    void shouldFallbackToRewrittenQuestionAndFilterByThresholdAndLimit() {
        IntentResolver resolver = new IntentResolver(new StubIntentClassifier(Map.of(
                "main", List.of(
                        score("a", IntentKind.KB, null, 0.9),
                        score("b", IntentKind.KB, null, 0.8),
                        score("c", IntentKind.KB, null, 0.7),
                        score("d", IntentKind.KB, null, 0.34)
                )
        )), directExecutor);

        List<SubQuestionIntent> result = resolver.resolve(new RewriteResult("main", List.of()));

        assertEquals(1, result.size());
        assertEquals(List.of("a", "b", "c"), result.get(0).nodeScores().stream().map(ns -> ns.getNode().getId()).toList());
    }

    @Test
    void shouldSplitMergedIntentGroupIntoMcpAndKb() {
        IntentResolver resolver = new IntentResolver(new StubIntentClassifier(Map.of()), directExecutor);
        List<SubQuestionIntent> subIntents = List.of(
                new SubQuestionIntent("q1", List.of(
                        score("mcp-1", IntentKind.MCP, "tool-a", 0.9),
                        score("mcp-2", IntentKind.MCP, null, 0.8),
                        score("kb-1", IntentKind.KB, null, 0.7),
                        score("kb-2", null, null, 0.6)
                ))
        );

        IntentGroup group = resolver.mergeIntentGroup(subIntents);

        assertEquals(List.of("mcp-1"), group.mcpIntents().stream().map(ns -> ns.getNode().getId()).toList());
        assertEquals(List.of("kb-1", "kb-2"), group.kbIntents().stream().map(ns -> ns.getNode().getId()).toList());
    }

    @Test
    void shouldRecognizeSystemOnlyIntentGroup() {
        IntentResolver resolver = new IntentResolver(new StubIntentClassifier(Map.of()), directExecutor);

        assertTrue(resolver.isSystemOnly(List.of(score("sys", IntentKind.SYSTEM, null, 0.9))));
        assertFalse(resolver.isSystemOnly(List.of(score("sys", IntentKind.SYSTEM, null, 0.9), score("kb", IntentKind.KB, null, 0.8))));
    }

    private static NodeScore score(String id, IntentKind kind, String toolId, double score) {
        return NodeScore.builder()
                .node(IntentNode.builder()
                        .id(id)
                        .kind(kind)
                        .mcpToolId(toolId)
                        .build())
                .score(score)
                .build();
    }

    private record StubIntentClassifier(Map<String, List<NodeScore>> answers) implements IntentClassifier {
        @Override
        public List<NodeScore> classifyTargets(String question) {
            return answers.getOrDefault(question, List.of());
        }
    }
}
