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

package com.nageoffer.ai.ragent.rag.core.mcp;

import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptTemplateLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LLMMCPParameterExtractorTests {

    @Mock
    private LLMService llmService;

    @Mock
    private PromptTemplateLoader promptTemplateLoader;

    @Test
    void shouldUseDeterministicFastPathForSimpleTypedSchema() {
        LLMMCPParameterExtractor extractor = new LLMMCPParameterExtractor(llmService, promptTemplateLoader);
        MCPTool tool = MCPTool.builder()
                .toolId("leave_balance")
                .description("查询假期余额")
                .parameters(Map.of(
                        "employeeId", MCPTool.ParameterDef.builder().type("string").required(true).build(),
                        "includeHistory", MCPTool.ParameterDef.builder().type("boolean").defaultValue(false).build()
                ))
                .build();

        Map<String, Object> result = extractor.extractParameters("employeeId: E-10086 includeHistory true", tool);

        assertEquals("E-10086", result.get("employeeId"));
        assertEquals(Boolean.TRUE, result.get("includeHistory"));
        verify(llmService, never()).chat(org.mockito.ArgumentMatchers.any(ChatRequest.class));
    }

    @Test
    void shouldFallBackToLlmWhenFastPathCannotFillRequiredParameters() {
        LLMMCPParameterExtractor extractor = new LLMMCPParameterExtractor(llmService, promptTemplateLoader);
        MCPTool tool = MCPTool.builder()
                .toolId("attendance_query")
                .description("查询考勤")
                .parameters(Map.of(
                        "employeeId", MCPTool.ParameterDef.builder().type("string").required(true).build(),
                        "days", MCPTool.ParameterDef.builder().type("integer").required(true).build()
                ))
                .build();
        when(promptTemplateLoader.load(anyString())).thenReturn("extract prompt");
        when(llmService.chat(org.mockito.ArgumentMatchers.any(ChatRequest.class))).thenReturn("{\"employeeId\":\"E-10010\",\"days\":7}");

        Map<String, Object> result = extractor.extractParameters("帮我查下最近的考勤", tool);

        assertEquals("E-10010", result.get("employeeId"));
        assertEquals(7, result.get("days"));
        verify(llmService).chat(org.mockito.ArgumentMatchers.any(ChatRequest.class));
    }

    @Test
    void shouldMatchEnumWithoutCallingLlmForSimpleSchema() {
        LLMMCPParameterExtractor extractor = new LLMMCPParameterExtractor(llmService, promptTemplateLoader);
        MCPTool tool = MCPTool.builder()
                .toolId("approval_list")
                .description("查询审批列表")
                .parameters(Map.of(
                        "status", MCPTool.ParameterDef.builder().type("string").required(true).enumValues(java.util.List.of("pending", "approved")).build()
                ))
                .build();

        Map<String, Object> result = extractor.extractParameters("请查询 pending 的审批单", tool);

        assertEquals("pending", result.get("status"));
        assertTrue(result.containsKey("status"));
        verify(llmService, never()).chat(org.mockito.ArgumentMatchers.any(ChatRequest.class));
    }
}
