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

package com.nageoffer.ai.ragent.rag.core.prompt;

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import com.nageoffer.ai.ragent.rag.core.mcp.MCPResponse;
import com.nageoffer.ai.ragent.rag.enums.IntentKind;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultContextFormatterTests {

    private final DefaultContextFormatter formatter = new DefaultContextFormatter();

    @Test
    void shouldFormatChunksWithoutIntentAndRespectTopK() {
        String result = formatter.formatKbContext(
                List.of(),
                Map.of("default", List.of(chunk("1", "A"), chunk("2", "B"), chunk("3", "C"))),
                2
        );

        assertEquals("#### 知识库片段\n````text\nA\n\nB\n````", result);
    }

    @Test
    void shouldFormatSingleIntentWithPromptSnippet() {
        NodeScore intent = nodeScore("kb-1", IntentKind.KB, null, "  先回答结论  ");

        String result = formatter.formatKbContext(
                List.of(intent),
                Map.of("kb-1", List.of(chunk("1", "片段一"), chunk("2", "片段二"))),
                1
        );

        assertEquals("#### 回答规则\n先回答结论\n\n#### 知识库片段\n````text\n片段一\n````", result);
        assertTrue(result.contains("片段一"));
    }

    @Test
    void shouldFormatMultiIntentWithDistinctRulesAndDeduplicatedChunks() {
        NodeScore intent1 = nodeScore("kb-1", IntentKind.KB, null, "规则A");
        NodeScore intent2 = nodeScore("kb-2", IntentKind.KB, null, "规则B");
        RetrievedChunk shared = chunk("shared", "共享片段");

        Map<String, List<RetrievedChunk>> reranked = new LinkedHashMap<>();
        reranked.put("kb-1", List.of(shared, chunk("2", "片段二")));
        reranked.put("kb-2", List.of(shared, chunk("3", "片段三")));

        String result = formatter.formatKbContext(List.of(intent1, intent2), reranked, 3);

        assertTrue(result.contains("1. 规则A"));
        assertTrue(result.contains("2. 规则B"));
        assertTrue(result.contains("共享片段"));
        assertTrue(result.contains("片段二"));
        assertTrue(result.contains("片段三"));
    }

    @Test
    void shouldRenderCollectionProvenanceInKbContext() {
        NodeScore intent = nodeScore("kb-1", IntentKind.KB, null, "规则A");

        String result = formatter.formatKbContext(
                List.of(intent),
                Map.of("kb-1", List.of(chunk("1", "片段一", Map.of("collection", "employee_manual")))),
                1
        );

        assertTrue(result.contains("[来源: employee_manual]"));
        assertTrue(result.contains("片段一"));
    }

    @Test
    void shouldReturnMergedMcpTextWhenIntentHintsAreMissing() {
        String result = formatter.formatMcpContext(
                List.of(
                        MCPResponse.success("tool-a", "first"),
                        MCPResponse.error("tool-b", "ERR", "boom")
                ),
                List.of()
        );

        assertEquals("first\n\n【部分查询失败】\n- 工具 tool-b 调用失败: boom", result);
    }

    @Test
    void shouldFormatMcpContextByIntentToolMapping() {
        NodeScore mcpIntent = nodeScore("mcp-1", IntentKind.MCP, "tool-a", "使用动态规则");

        String result = formatter.formatMcpContext(
                List.of(
                        MCPResponse.success("tool-a", "动态结果一"),
                        MCPResponse.success("tool-a", "动态结果二"),
                        MCPResponse.success("tool-b", "应被忽略"),
                        MCPResponse.error("tool-a", "ERR", "失败信息")
                ),
                List.of(mcpIntent)
        );

        assertEquals("#### 意图规则\n使用动态规则\n#### 动态数据片段\n动态结果一\n\n动态结果二", result);
    }

    @Test
    void shouldReturnEmptyWhenNoSuccessfulMcpResponseExists() {
        String result = formatter.formatMcpContext(List.of(MCPResponse.error("tool-a", "ERR", "boom")), List.of());

        assertEquals("", result);
    }

    private static NodeScore nodeScore(String id, IntentKind kind, String toolId, String snippet) {
        return NodeScore.builder()
                .node(IntentNode.builder()
                        .id(id)
                        .kind(kind)
                        .mcpToolId(toolId)
                        .promptSnippet(snippet)
                        .build())
                .score(0.9)
                .build();
    }

    private static RetrievedChunk chunk(String id, String text) {
        return chunk(id, text, Map.of());
    }

    private static RetrievedChunk chunk(String id, String text, Map<String, String> provenance) {
        return RetrievedChunk.builder()
                .id(id)
                .text(text)
                .score(0.8f)
                .provenance(provenance)
                .build();
    }
}
