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
import com.nageoffer.ai.ragent.rag.core.mcp.MCPToolRegistry;
import com.nageoffer.ai.ragent.rag.core.prompt.ContextFormatter;
import com.nageoffer.ai.ragent.rag.dto.RetrievalContext;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
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

    private static NodeScore kbIntent(String id, String collectionName) {
        return NodeScore.builder()
                .node(IntentNode.builder()
                        .id(id)
                        .collectionName(collectionName)
                        .build())
                .score(0.95D)
                .build();
    }

    private static RetrievedChunk chunk(String id, String text, String collection) {
        return RetrievedChunk.builder()
                .id(id)
                .text(text)
                .score(0.8f)
                .provenance(Map.of("collection", collection))
                .build();
    }
}
