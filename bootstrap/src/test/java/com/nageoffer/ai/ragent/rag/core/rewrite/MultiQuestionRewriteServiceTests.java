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

package com.nageoffer.ai.ragent.rag.core.rewrite;

import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.rag.config.RAGConfigProperties;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptTemplateLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultiQuestionRewriteServiceTests {

    @Mock
    private LLMService llmService;
    @Mock
    private RAGConfigProperties ragConfigProperties;
    @Mock
    private QueryTermMappingService queryTermMappingService;
    @Mock
    private PromptTemplateLoader promptTemplateLoader;

    @InjectMocks
    private MultiQuestionRewriteService service;

    @BeforeEach
    void setUp() {
        when(queryTermMappingService.normalize(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldUseRuleBasedFallbackWhenRewriteDisabled() {
        when(ragConfigProperties.getQueryRewriteEnabled()).thenReturn(false);
        when(queryTermMappingService.normalize("你好；介绍 RAG。解释 Agent")).thenReturn("介绍RAG；解释Agent");

        RewriteResult result = service.rewriteWithSplit("你好；介绍 RAG。解释 Agent");

        assertEquals("介绍RAG；解释Agent", result.rewrittenQuestion());
        assertEquals(List.of("介绍RAG？", "解释Agent？"), result.subQuestions());
    }

    @Test
    void shouldParseRewriteResponseFromLlm() {
        when(ragConfigProperties.getQueryRewriteEnabled()).thenReturn(true);
        when(promptTemplateLoader.load(any())).thenReturn("system prompt");
        when(llmService.chat(any(ChatRequest.class))).thenReturn("""
                ```json
                {"rewrite":"规范化问题","sub_questions":["子问题1","子问题2"]}
                ```
                """);

        RewriteResult result = service.rewriteWithSplit("原问题");

        assertEquals("规范化问题", result.rewrittenQuestion());
        assertEquals(List.of("子问题1", "子问题2"), result.subQuestions());
    }

    @Test
    void shouldFallbackToNormalizedQuestionWhenLlmResponseIsInvalid() {
        when(ragConfigProperties.getQueryRewriteEnabled()).thenReturn(true);
        when(promptTemplateLoader.load(any())).thenReturn("system prompt");
        when(queryTermMappingService.normalize("原问题")).thenReturn("归一化问题");
        when(llmService.chat(any(ChatRequest.class))).thenReturn("not-json");

        RewriteResult result = service.rewriteWithSplit("原问题");

        assertEquals("归一化问题", result.rewrittenQuestion());
        assertEquals(List.of("归一化问题"), result.subQuestions());
    }

    @Test
    void shouldIncludeOnlyRecentUserAndAssistantHistoryInLlmRequest() {
        when(ragConfigProperties.getQueryRewriteEnabled()).thenReturn(true);
        when(promptTemplateLoader.load(any())).thenReturn("system prompt");
        when(llmService.chat(any(ChatRequest.class))).thenReturn("{\"rewrite\":\"ok\",\"sub_questions\":[\"ok\"]}");

        List<ChatMessage> history = List.of(
                ChatMessage.system("ignore-system"),
                ChatMessage.user("u1"),
                ChatMessage.assistant("a1"),
                ChatMessage.user("u2"),
                ChatMessage.assistant("a2"),
                ChatMessage.user("u3")
        );

        service.rewriteWithSplit("current", history);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmService).chat(captor.capture());
        List<ChatMessage> messages = captor.getValue().getMessages();

        assertEquals(ChatMessage.Role.SYSTEM, messages.get(0).getRole());
        assertEquals(List.of("u2", "a2", "u3", "current"),
                messages.subList(1, messages.size()).stream().map(ChatMessage::getContent).toList());
        assertFalse(messages.stream().anyMatch(message -> "ignore-system".equals(message.getContent())));
        assertTrue(messages.stream().allMatch(message -> message.getRole() != null));
    }
}
