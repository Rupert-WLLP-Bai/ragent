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

package com.nageoffer.ai.ragent.rag.service;

import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.trace.RagTraceContext;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.infra.chat.StreamCallback;
import com.nageoffer.ai.ragent.infra.chat.StreamCancellationHandle;
import com.nageoffer.ai.ragent.rag.core.guidance.GuidanceDecision;
import com.nageoffer.ai.ragent.rag.core.guidance.IntentGuidanceService;
import com.nageoffer.ai.ragent.rag.core.intent.IntentResolver;
import com.nageoffer.ai.ragent.rag.core.memory.ConversationMemoryService;
import com.nageoffer.ai.ragent.rag.core.plan.RequestPlanner;
import com.nageoffer.ai.ragent.rag.core.prompt.RAGPromptService;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrievalEngine;
import com.nageoffer.ai.ragent.rag.core.rewrite.QueryRewriteService;
import com.nageoffer.ai.ragent.rag.core.rewrite.RewriteResult;
import com.nageoffer.ai.ragent.rag.dto.IntentGroup;
import com.nageoffer.ai.ragent.rag.dto.RetrievalContext;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import com.nageoffer.ai.ragent.rag.service.RagTraceRecordService;
import com.nageoffer.ai.ragent.rag.service.handler.StreamCallbackFactory;
import com.nageoffer.ai.ragent.rag.service.handler.StreamTaskManager;
import com.nageoffer.ai.ragent.rag.service.impl.RAGChatServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RAGChatServiceImplTests {

    @BeforeEach
    void setUp() {
        RagTraceContext.clear();
        service = new RAGChatServiceImpl(
                llmService,
                promptBuilder,
                promptTemplateLoader,
                memoryService,
                taskManager,
                guidanceService,
                callbackFactory,
                queryRewriteService,
                intentResolver,
                retrievalEngine,
                requestPlanner,
                ragTraceRecordService
        );
    }

    @Mock
    private LLMService llmService;
    @Mock
    private RAGPromptService promptBuilder;
    @Mock
    private com.nageoffer.ai.ragent.rag.core.prompt.PromptTemplateLoader promptTemplateLoader;
    @Mock
    private ConversationMemoryService memoryService;
    @Mock
    private StreamTaskManager taskManager;
    @Mock
    private IntentGuidanceService guidanceService;
    @Mock
    private StreamCallbackFactory callbackFactory;
    @Mock
    private QueryRewriteService queryRewriteService;
    @Mock
    private IntentResolver intentResolver;
    @Mock
    private RetrievalEngine retrievalEngine;
    @Mock
    private RagTraceRecordService ragTraceRecordService;

    private final RequestPlanner requestPlanner = new RequestPlanner();

    private RAGChatServiceImpl service;

    @Test
    void shouldUseSystemOnlyPlanWithoutRetrieval() {
        StreamCallback callback = new CapturingCallback();
        StreamCancellationHandle handle = () -> { };
        RewriteResult rewriteResult = new RewriteResult("系统问题", List.of("系统问题"));
        SubQuestionIntent systemIntent = new SubQuestionIntent("系统问题", List.of());

        when(callbackFactory.createChatEventHandler(any(SseEmitter.class), any(), any())).thenReturn(callback);
        when(memoryService.loadAndAppend(any(), any(), any())).thenReturn(List.of(ChatMessage.user("history")));
        when(intentResolver.resolve(new RewriteResult("系统问题", List.of("系统问题")))).thenReturn(List.of(systemIntent));
        when(guidanceService.detectAmbiguity("系统问题", List.of(systemIntent))).thenReturn(GuidanceDecision.none());
        when(intentResolver.isSystemOnly(systemIntent.nodeScores())).thenReturn(true);
        when(intentResolver.mergeIntentGroup(List.of(systemIntent))).thenReturn(new IntentGroup(List.of(), List.of()));
        when(promptTemplateLoader.load(any())).thenReturn("system-prompt");
        when(llmService.streamChat(any(ChatRequest.class), any(StreamCallback.class))).thenReturn(handle);
        RagTraceContext.setTraceId("trace-1");
        RagTraceContext.setTaskId("task-1");

        service.streamChat("系统问题", "c-1", true, new SseEmitter());

        verify(queryRewriteService, never()).rewriteWithSplit(any(), anyList());
        verify(retrievalEngine, never()).retrieve(anyList(), anyInt());
        verify(retrievalEngine, never()).retrieve(anyList(), any(com.nageoffer.ai.ragent.rag.core.plan.RetrievalPlan.class));
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmService).streamChat(captor.capture(), any(StreamCallback.class));
        assertFalse(Boolean.TRUE.equals(captor.getValue().getThinking()));
        verify(taskManager).bindHandle(any(), any(StreamCancellationHandle.class));
        verify(ragTraceRecordService).appendRunExtraData(any(), contains("SYSTEM_ONLY"));
        verify(ragTraceRecordService).upsertDecisionNode(any(), any(), contains("SYSTEM_ONLY"));
    }

    @Test
    void shouldPassMixedRetrievalPlanAndDeepThinkingToRagResponse() {
        StreamCallback callback = new CapturingCallback();
        StreamCancellationHandle handle = () -> { };
        RewriteResult rewriteResult = new RewriteResult("帮我查一下请假制度并确认审批状态以及还剩多少年假", List.of("子问题1", "子问题2", "子问题3"));
        SubQuestionIntent first = new SubQuestionIntent("子问题1", List.of());
        SubQuestionIntent second = new SubQuestionIntent("子问题2", List.of());
        RetrievalContext retrievalContext = RetrievalContext.builder()
                .kbContext("kb-context")
                .mcpContext("mcp-context")
                .intentChunks(Map.of())
                .build();
        IntentGroup intentGroup = new IntentGroup(List.of(mockNodeScore()), List.of(mockNodeScore()));
        List<ChatMessage> builtMessages = List.of(ChatMessage.system("system"), ChatMessage.user("answer"));

        when(callbackFactory.createChatEventHandler(any(SseEmitter.class), any(), any())).thenReturn(callback);
        when(memoryService.loadAndAppend(any(), any(), any())).thenReturn(List.of(ChatMessage.user("history")));
        when(queryRewriteService.rewriteWithSplit("帮我查一下请假制度并确认审批状态以及还剩多少年假", List.of(ChatMessage.user("history")))).thenReturn(rewriteResult);
        when(intentResolver.resolve(rewriteResult)).thenReturn(List.of(first, second));
        when(guidanceService.detectAmbiguity("帮我查一下请假制度并确认审批状态以及还剩多少年假", List.of(first, second))).thenReturn(GuidanceDecision.none());
        when(intentResolver.isSystemOnly(anyList())).thenReturn(false);
        when(intentResolver.mergeIntentGroup(List.of(first, second))).thenReturn(intentGroup);
        when(retrievalEngine.retrieve(anyList(), any(com.nageoffer.ai.ragent.rag.core.plan.RetrievalPlan.class))).thenReturn(retrievalContext);
        when(promptBuilder.buildStructuredMessages(any(), anyList(), any(), anyList())).thenReturn(builtMessages);
        when(llmService.streamChat(any(ChatRequest.class), any(StreamCallback.class))).thenReturn(handle);
        RagTraceContext.setTraceId("trace-1");
        RagTraceContext.setTaskId("task-1");

        service.streamChat("帮我查一下请假制度并确认审批状态以及还剩多少年假", "c-1", false, new SseEmitter());

        ArgumentCaptor<com.nageoffer.ai.ragent.rag.core.plan.RetrievalPlan> planCaptor = ArgumentCaptor.forClass(com.nageoffer.ai.ragent.rag.core.plan.RetrievalPlan.class);
        verify(retrievalEngine).retrieve(anyList(), planCaptor.capture());
        assertEquals(com.nageoffer.ai.ragent.rag.core.plan.RetrievalPlan.RetrievalMode.MIXED, planCaptor.getValue().retrievalMode());
        assertEquals(10, planCaptor.getValue().topK());

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmService).streamChat(requestCaptor.capture(), any(StreamCallback.class));
        assertTrue(Boolean.TRUE.equals(requestCaptor.getValue().getThinking()));
        assertEquals(0.3D, requestCaptor.getValue().getTemperature());
        assertEquals(0.8D, requestCaptor.getValue().getTopP());
        verify(ragTraceRecordService).appendRunExtraData(any(), contains("MIXED"));
        verify(ragTraceRecordService).upsertDecisionNode(any(), any(), contains("selectedModelTier"));
    }

    private static com.nageoffer.ai.ragent.rag.core.intent.NodeScore mockNodeScore() {
        return com.nageoffer.ai.ragent.rag.core.intent.NodeScore.builder()
                .node(com.nageoffer.ai.ragent.rag.core.intent.IntentNode.builder().id("n1").build())
                .score(0.9D)
                .build();
    }

    private static final class CapturingCallback implements StreamCallback {
        @Override
        public void onContent(String content) {
        }

        @Override
        public void onComplete() {
        }

        @Override
        public void onError(Throwable error) {
        }
    }
}
