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

package com.nageoffer.ai.ragent.rag.core.memory;

import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultConversationMemoryServiceTests {

    @Mock
    private ConversationMemoryStore memoryStore;
    @Mock
    private ConversationMemorySummaryService summaryService;

    @InjectMocks
    private DefaultConversationMemoryService service;

    @Test
    void shouldReturnEmptyWhenConversationIdOrUserIdIsBlank() {
        assertTrue(service.load(null, "u-1").isEmpty());
        assertTrue(service.load("c-1", " ").isEmpty());
        verify(memoryStore, never()).loadHistory("c-1", "u-1");
    }

    @Test
    void shouldAttachDecoratedSummaryBeforeHistory() {
        ChatMessage summary = ChatMessage.system("summary");
        ChatMessage decorated = ChatMessage.system("decorated-summary");
        List<ChatMessage> history = List.of(ChatMessage.user("u1"), ChatMessage.assistant("a1"));
        when(summaryService.loadLatestSummary("c-1", "u-1")).thenReturn(summary);
        when(summaryService.decorateIfNeeded(summary)).thenReturn(decorated);
        when(memoryStore.loadHistory("c-1", "u-1")).thenReturn(history);

        List<ChatMessage> result = service.load("c-1", "u-1");

        assertEquals(List.of(decorated, history.get(0), history.get(1)), result);
    }

    @Test
    void shouldSkipSummaryWhenSummaryLoadingFails() {
        List<ChatMessage> history = List.of(ChatMessage.user("u1"));
        when(summaryService.loadLatestSummary("c-1", "u-1")).thenThrow(new IllegalStateException("boom"));
        when(memoryStore.loadHistory("c-1", "u-1")).thenReturn(history);

        List<ChatMessage> result = service.load("c-1", "u-1");

        assertEquals(history, result);
        verify(summaryService, never()).decorateIfNeeded(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldReturnEmptyWhenHistoryLoadingFails() {
        when(summaryService.loadLatestSummary("c-1", "u-1")).thenReturn(ChatMessage.system("summary"));
        when(memoryStore.loadHistory("c-1", "u-1")).thenThrow(new IllegalStateException("boom"));

        assertTrue(service.load("c-1", "u-1").isEmpty());
    }

    @Test
    void shouldAppendMessageAndTriggerCompression() {
        ChatMessage message = ChatMessage.user("hello");
        when(memoryStore.append("c-1", "u-1", message)).thenReturn(123L);

        Long result = service.append("c-1", "u-1", message);

        assertEquals(123L, result);
        verify(summaryService).compressIfNeeded("c-1", "u-1", message);
    }

    @Test
    void shouldReturnNullWhenAppendInputIsBlank() {
        assertNull(service.append(" ", "u-1", ChatMessage.user("hello")));
        verify(memoryStore, never()).append("c-1", "u-1", ChatMessage.user("hello"));
    }
}
