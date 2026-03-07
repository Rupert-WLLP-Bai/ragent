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

package com.nageoffer.ai.ragent.rag.aop;

import com.nageoffer.ai.ragent.rag.config.RagTraceProperties;
import com.nageoffer.ai.ragent.rag.dao.entity.RagTraceRunDO;
import com.nageoffer.ai.ragent.rag.service.RagTraceRecordService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Method;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.isNull;

@ExtendWith(MockitoExtension.class)
class ChatRateLimitAspectTests {

    @Mock
    private ChatQueueLimiter chatQueueLimiter;

    @Mock
    private RagTraceRecordService traceRecordService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Test
    void shouldFinishTraceWhenEmitterCompletes() throws Throwable {
        RagTraceProperties traceProperties = new RagTraceProperties();
        traceProperties.setEnabled(true);
        SseEmitter emitter = mock(SseEmitter.class);
        Runnable[] queuedRunnable = new Runnable[1];
        Runnable[] completionCallback = new Runnable[1];
        when(joinPoint.getArgs()).thenReturn(new Object[]{"hello", "conv-1", Boolean.FALSE, emitter});
        when(joinPoint.getTarget()).thenReturn(new TestTarget());
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(testMethod());
        doAnswer(invocation -> {
            queuedRunnable[0] = invocation.getArgument(3);
            return null;
        }).when(chatQueueLimiter).enqueue(anyString(), anyString(), any(SseEmitter.class), any(Runnable.class));
        doAnswer(invocation -> {
            completionCallback[0] = invocation.getArgument(0);
            return null;
        }).when(emitter).onCompletion(any(Runnable.class));
        ChatRateLimitAspect aspect = new ChatRateLimitAspect(chatQueueLimiter, traceProperties, traceRecordService);

        Object result = aspect.limitStreamChat(joinPoint);
        assertNull(result);
        assertNotNull(queuedRunnable[0]);

        queuedRunnable[0].run();
        verify(traceRecordService).startRun(any(RagTraceRunDO.class));
        verify(traceRecordService, never()).finishRun(anyString(), anyString(), anyString(), any(), anyLong());

        assertNotNull(completionCallback[0]);
        completionCallback[0].run();

        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(traceRecordService).finishRun(anyString(), statusCaptor.capture(), isNull(), any(), anyLong());
        assertEquals("SUCCESS", statusCaptor.getValue());
    }

    @Test
    void shouldFinishTraceAsErrorWhenEmitterFails() throws Throwable {
        RagTraceProperties traceProperties = new RagTraceProperties();
        traceProperties.setEnabled(true);
        SseEmitter emitter = mock(SseEmitter.class);
        Runnable[] queuedRunnable = new Runnable[1];
        Consumer<Throwable>[] errorCallback = new Consumer[1];
        when(joinPoint.getArgs()).thenReturn(new Object[]{"hello", "conv-1", Boolean.FALSE, emitter});
        when(joinPoint.getTarget()).thenReturn(new TestTarget());
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(testMethod());
        doAnswer(invocation -> {
            queuedRunnable[0] = invocation.getArgument(3);
            return null;
        }).when(chatQueueLimiter).enqueue(anyString(), anyString(), any(SseEmitter.class), any(Runnable.class));
        doAnswer(invocation -> {
            errorCallback[0] = invocation.getArgument(0);
            return null;
        }).when(emitter).onError(any());
        ChatRateLimitAspect aspect = new ChatRateLimitAspect(chatQueueLimiter, traceProperties, traceRecordService);

        aspect.limitStreamChat(joinPoint);
        queuedRunnable[0].run();
        assertNotNull(errorCallback[0]);
        errorCallback[0].accept(new IllegalStateException("boom"));

        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(traceRecordService).finishRun(anyString(), statusCaptor.capture(), errorCaptor.capture(), any(), anyLong());
        assertEquals("ERROR", statusCaptor.getValue());
        assertEquals("IllegalStateException: boom", errorCaptor.getValue());
    }

    private static Method testMethod() throws NoSuchMethodException {
        return TestTarget.class.getDeclaredMethod("stream", String.class, String.class, Boolean.class, SseEmitter.class);
    }

    private static final class TestTarget {
        @SuppressWarnings("unused")
        public void stream(String question, String conversationId, Boolean deepThinking, SseEmitter emitter) {
        }
    }
}
