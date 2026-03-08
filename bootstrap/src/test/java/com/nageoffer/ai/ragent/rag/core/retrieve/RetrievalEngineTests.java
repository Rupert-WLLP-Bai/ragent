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

package com.nageoffer.ai.ragent.rag.core.retrieve;

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import com.nageoffer.ai.ragent.rag.core.mcp.MCPParameterExtractor;
import com.nageoffer.ai.ragent.rag.core.mcp.MCPRequest;
import com.nageoffer.ai.ragent.rag.core.mcp.MCPResponse;
import com.nageoffer.ai.ragent.rag.core.mcp.MCPTool;
import com.nageoffer.ai.ragent.rag.core.mcp.MCPToolExecutor;
import com.nageoffer.ai.ragent.rag.core.mcp.MCPToolRegistry;
import com.nageoffer.ai.ragent.rag.core.plan.RetrievalPlan;
import com.nageoffer.ai.ragent.rag.core.prompt.ContextFormatter;
import com.nageoffer.ai.ragent.rag.dto.RetrievalContext;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import com.nageoffer.ai.ragent.rag.enums.IntentKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrievalEngineTests {

    @Mock
    private ContextFormatter contextFormatter;

    @Mock
    private MCPParameterExtractor mcpParameterExtractor;

    @Mock
    private MCPToolRegistry mcpToolRegistry;

    @Mock
    private MultiChannelRetrievalEngine multiChannelRetrievalEngine;

    @Test
    void shouldGroupChunksByCollectionProvenanceForMatchedKbIntent() {
        Executor directExecutor = Runnable::run;
        RetrievalEngine engine = new RetrievalEngine(
                contextFormatter,
                mcpParameterExtractor,
                mcpToolRegistry,
                multiChannelRetrievalEngine,
                directExecutor,
                directExecutor
        );
        NodeScore employeeIntent = kbIntent("intent-employee", "employee_manual");
        NodeScore policyIntent = kbIntent("intent-policy", "policy_handbook");
        RetrievedChunk employeeChunk = chunk("c1", "员工手册片段", "employee_manual");
        RetrievedChunk policyChunk = chunk("c2", "制度手册片段", "policy_handbook");
        SubQuestionIntent subQuestionIntent = new SubQuestionIntent("请介绍请假制度", List.of(employeeIntent, policyIntent));
        when(multiChannelRetrievalEngine.retrieveKnowledgeChannels(List.of(subQuestionIntent), 5))
                .thenReturn(List.of(employeeChunk, policyChunk));
        when(contextFormatter.formatKbContext(anyList(), anyMap(), anyInt())).thenReturn("formatted-kb");

        RetrievalContext result = engine.retrieve(List.of(subQuestionIntent), 5);

        verify(contextFormatter).formatKbContext(anyList(), anyMap(), anyInt());
        assertEquals("---\n**子问题**：请介绍请假制度\n\n**相关文档**：\nformatted-kb", result.getKbContext());
        assertSame(employeeChunk, result.getIntentChunks().get("intent-employee").get(0));
        assertSame(policyChunk, result.getIntentChunks().get("intent-policy").get(0));
    }

    @Test
    void shouldNotFallBackToAllChunksWhenIntentCollectionHasNoProvenanceMatch() {
        Executor directExecutor = Runnable::run;
        RetrievalEngine engine = new RetrievalEngine(
                contextFormatter,
                mcpParameterExtractor,
                mcpToolRegistry,
                multiChannelRetrievalEngine,
                directExecutor,
                directExecutor
        );
        NodeScore employeeIntent = kbIntent("intent-employee", "employee_manual");
        RetrievedChunk unrelatedChunk = chunk("c1", "别的手册片段", "policy_handbook");
        SubQuestionIntent subQuestionIntent = new SubQuestionIntent("请介绍请假制度", List.of(employeeIntent));
        when(multiChannelRetrievalEngine.retrieveKnowledgeChannels(List.of(subQuestionIntent), 5))
                .thenReturn(List.of(unrelatedChunk));
        when(contextFormatter.formatKbContext(anyList(), anyMap(), anyInt())).thenReturn("formatted-kb");

        RetrievalContext result = engine.retrieve(List.of(subQuestionIntent), 5);

        assertTrue(result.getIntentChunks().get("intent-employee").isEmpty());
    }

    @Test
    void shouldMergeKbAndMcpContextsAcrossSubQuestionsByStage() {
        Executor directExecutor = Runnable::run;
        RetrievalEngine engine = new RetrievalEngine(
                contextFormatter,
                mcpParameterExtractor,
                mcpToolRegistry,
                multiChannelRetrievalEngine,
                directExecutor,
                directExecutor
        );
        NodeScore employeeIntent = kbIntent("intent-employee", "employee_manual");
        SubQuestionIntent first = new SubQuestionIntent("子问题一", List.of(employeeIntent));
        SubQuestionIntent second = new SubQuestionIntent("子问题二", List.of(employeeIntent));
        RetrievedChunk firstChunk = chunk("c1", "片段一", "employee_manual");
        RetrievedChunk secondChunk = chunk("c2", "片段二", "employee_manual");
        when(multiChannelRetrievalEngine.retrieveKnowledgeChannels(List.of(first), 5))
                .thenReturn(List.of(firstChunk));
        when(multiChannelRetrievalEngine.retrieveKnowledgeChannels(List.of(second), 5))
                .thenReturn(List.of(secondChunk));
        when(contextFormatter.formatKbContext(anyList(), anyMap(), anyInt()))
                .thenReturn("formatted-kb-1", "formatted-kb-2");

        RetrievalContext result = engine.retrieve(List.of(first, second), 5);

        assertTrue(result.getKbContext().contains("**子问题**：子问题一"));
        assertTrue(result.getKbContext().contains("formatted-kb-1"));
        assertTrue(result.getKbContext().contains("**子问题**：子问题二"));
        assertTrue(result.getKbContext().contains("formatted-kb-2"));
        assertEquals(List.of("c1", "c2"), result.getIntentChunks().get("intent-employee").stream().map(RetrievedChunk::getId).toList());
    }

    @Test
    void shouldMergeDuplicateIntentChunksAcrossSubQuestionsWithoutDroppingProvenance() {
        Executor directExecutor = Runnable::run;
        RetrievalEngine engine = new RetrievalEngine(
                contextFormatter,
                mcpParameterExtractor,
                mcpToolRegistry,
                multiChannelRetrievalEngine,
                directExecutor,
                directExecutor
        );
        NodeScore employeeIntent = kbIntent("intent-employee", "employee_manual");
        SubQuestionIntent first = new SubQuestionIntent("子问题一", List.of(employeeIntent));
        SubQuestionIntent second = new SubQuestionIntent("子问题二", List.of(employeeIntent));
        RetrievedChunk firstChunk = chunk("shared", "片段一", Map.of("collection", "employee_manual", "origin-1", "subq-1"));
        RetrievedChunk secondChunk = chunk("shared", "片段一", Map.of("collection", "employee_manual", "origin-2", "subq-2"));
        when(multiChannelRetrievalEngine.retrieveKnowledgeChannels(List.of(first), 5))
                .thenReturn(List.of(firstChunk));
        when(multiChannelRetrievalEngine.retrieveKnowledgeChannels(List.of(second), 5))
                .thenReturn(List.of(secondChunk));
        when(contextFormatter.formatKbContext(anyList(), anyMap(), anyInt()))
                .thenReturn("formatted-kb-1", "formatted-kb-2");

        RetrievalContext result = engine.retrieve(List.of(first, second), 5);

        assertEquals(1, result.getIntentChunks().get("intent-employee").size());
        RetrievedChunk merged = result.getIntentChunks().get("intent-employee").get(0);
        assertEquals("employee_manual", merged.getProvenance().get("collection"));
        assertEquals("subq-1", merged.getProvenance().get("origin-1"));
        assertEquals("subq-2", merged.getProvenance().get("origin-2"));
    }

    @Test
    void shouldSkipKbStageWhenRetrievalPlanIsMcpOnly() {
        Executor directExecutor = Runnable::run;
        RetrievalEngine engine = new RetrievalEngine(
                contextFormatter,
                mcpParameterExtractor,
                mcpToolRegistry,
                multiChannelRetrievalEngine,
                directExecutor,
                directExecutor
        );
        NodeScore mcpIntent = mcpIntent("tool-weather");
        SubQuestionIntent subQuestionIntent = new SubQuestionIntent("今天天气怎么样", List.of(mcpIntent));
        MCPToolExecutor toolExecutor = new StubMcpToolExecutor();
        when(mcpToolRegistry.getExecutor("tool-weather")).thenReturn(Optional.of(toolExecutor));
        when(mcpParameterExtractor.extractParameters(anyString(), any(MCPTool.class), nullable(String.class))).thenReturn(Map.of());
        when(contextFormatter.formatMcpContext(anyList(), anyList())).thenReturn("formatted-mcp");

        RetrievalContext result = engine.retrieve(
                List.of(subQuestionIntent),
                new RetrievalPlan(RetrievalPlan.RetrievalMode.MCP_ONLY, 4)
        );

        verify(multiChannelRetrievalEngine, never()).retrieveKnowledgeChannels(anyList(), anyInt());
        verify(contextFormatter).formatMcpContext(anyList(), anyList());
        assertEquals("---\n**子问题**：今天天气怎么样\n\n**相关文档**：\nformatted-mcp", result.getMcpContext());
        assertTrue(result.getKbContext().isBlank());
    }

    @Test
    void shouldReturnEmptyContextWhenRetrievalPlanIsNone() {
        Executor directExecutor = Runnable::run;
        RetrievalEngine engine = new RetrievalEngine(
                contextFormatter,
                mcpParameterExtractor,
                mcpToolRegistry,
                multiChannelRetrievalEngine,
                directExecutor,
                directExecutor
        );

        RetrievalContext result = engine.retrieve(
                List.of(new SubQuestionIntent("任意问题", List.of())),
                RetrievalPlan.none()
        );

        verify(multiChannelRetrievalEngine, never()).retrieveKnowledgeChannels(anyList(), anyInt());
        verify(contextFormatter, never()).formatKbContext(anyList(), anyMap(), anyInt());
        verify(contextFormatter, never()).formatMcpContext(anyList(), anyList());
        assertTrue(result.isEmpty());
    }

    private static NodeScore kbIntent(String id, String collectionName) {
        return NodeScore.builder()
                .node(IntentNode.builder()
                        .id(id)
                        .kind(IntentKind.KB)
                        .collectionName(collectionName)
                        .build())
                .score(0.95D)
                .build();
    }

    private static NodeScore mcpIntent(String toolId) {
        return NodeScore.builder()
                .node(IntentNode.builder()
                        .id(toolId)
                        .kind(IntentKind.MCP)
                        .mcpToolId(toolId)
                        .build())
                .score(0.95D)
                .build();
    }

    private static RetrievedChunk chunk(String id, String text, String collection) {
        return chunk(id, text, Map.of("collection", collection));
    }

    private static RetrievedChunk chunk(String id, String text, Map<String, String> provenance) {
        return RetrievedChunk.builder()
                .id(id)
                .text(text)
                .score(0.8f)
                .provenance(provenance)
                .build();
    }

    private static final class StubMcpToolExecutor implements MCPToolExecutor {
        @Override
        public MCPTool getToolDefinition() {
            return MCPTool.builder()
                    .toolId("tool-weather")
                    .description("weather tool")
                    .parameters(Map.of())
                    .build();
        }

        @Override
        public MCPResponse execute(MCPRequest request) {
            return MCPResponse.success(request.getToolId(), "sunny", Map.of("answer", "sunny"));
        }
    }
}
