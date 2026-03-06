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

import com.nageoffer.ai.ragent.framework.context.LoginUser;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.rag.controller.vo.ConversationMessageVO;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationDO;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationMessageDO;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationSummaryDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.ConversationMapper;
import com.nageoffer.ai.ragent.rag.dao.mapper.ConversationMessageMapper;
import com.nageoffer.ai.ragent.rag.dao.mapper.ConversationSummaryMapper;
import com.nageoffer.ai.ragent.rag.enums.ConversationMessageOrder;
import com.nageoffer.ai.ragent.rag.service.bo.ConversationMessageBO;
import com.nageoffer.ai.ragent.rag.service.bo.ConversationSummaryBO;
import com.nageoffer.ai.ragent.rag.service.impl.ConversationMessageServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationMessageServiceImplTests {

    @Mock
    private ConversationMessageMapper conversationMessageMapper;
    @Mock
    private ConversationSummaryMapper conversationSummaryMapper;
    @Mock
    private ConversationMapper conversationMapper;
    @Mock
    private MessageFeedbackService feedbackService;

    @InjectMocks
    private ConversationMessageServiceImpl service;

    @BeforeEach
    void setUp() {
        UserContext.set(LoginUser.builder().userId("u-1").username("tester").build());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldReturnEmptyWhenConversationIdOrUserIsMissing() {
        assertTrue(service.listMessages(null, 10, ConversationMessageOrder.ASC).isEmpty());

        UserContext.clear();
        assertTrue(service.listMessages("c-1", 10, ConversationMessageOrder.ASC).isEmpty());
        verify(conversationMapper, never()).selectOne(any());
    }

    @Test
    void shouldReturnEmptyWhenConversationDoesNotExist() {
        when(conversationMapper.selectOne(any())).thenReturn(null);

        assertTrue(service.listMessages("c-1", 10, ConversationMessageOrder.ASC).isEmpty());
        verify(conversationMessageMapper, never()).selectList(any());
    }

    @Test
    void shouldReturnAscendingMessagesWithVotes() {
        Date now = new Date();
        when(conversationMapper.selectOne(any())).thenReturn(ConversationDO.builder()
                .conversationId("c-1")
                .userId("u-1")
                .deleted(0)
                .build());
        when(conversationMessageMapper.selectList(any())).thenReturn(List.of(
                ConversationMessageDO.builder().id(1L).conversationId("c-1").userId("u-1").role("user").content("hi").createTime(now).deleted(0).build(),
                ConversationMessageDO.builder().id(2L).conversationId("c-1").userId("u-1").role("assistant").content("hello").createTime(now).deleted(0).build()
        ));
        when(feedbackService.getUserVotes("u-1", List.of(2L))).thenReturn(Map.of(2L, 1));

        List<ConversationMessageVO> messages = service.listMessages("c-1", 10, ConversationMessageOrder.ASC);

        assertEquals(2, messages.size());
        assertEquals("1", messages.get(0).getId());
        assertNull(messages.get(0).getVote());
        assertEquals(1, messages.get(1).getVote());
        verify(feedbackService).getUserVotes("u-1", List.of(2L));
    }

    @Test
    void shouldReverseDescendingMessagesBeforeReturning() {
        Date now = new Date();
        when(conversationMapper.selectOne(any())).thenReturn(ConversationDO.builder()
                .conversationId("c-1")
                .userId("u-1")
                .deleted(0)
                .build());
        when(conversationMessageMapper.selectList(any())).thenReturn(List.of(
                ConversationMessageDO.builder().id(2L).conversationId("c-1").userId("u-1").role("assistant").content("second").createTime(now).deleted(0).build(),
                ConversationMessageDO.builder().id(1L).conversationId("c-1").userId("u-1").role("user").content("first").createTime(now).deleted(0).build()
        ));
        when(feedbackService.getUserVotes("u-1", List.of(2L))).thenReturn(Map.of());

        List<ConversationMessageVO> messages = service.listMessages("c-1", 2, ConversationMessageOrder.DESC);

        assertEquals(List.of("1", "2"), messages.stream().map(ConversationMessageVO::getId).toList());
    }

    @Test
    void shouldPersistMessageAndReturnGeneratedId() {
        doAnswer(invocation -> {
            ConversationMessageDO entity = invocation.getArgument(0);
            entity.setId(99L);
            return 1;
        }).when(conversationMessageMapper).insert(any(ConversationMessageDO.class));

        Long id = service.addMessage(ConversationMessageBO.builder()
                .conversationId("c-1")
                .userId("u-1")
                .role("user")
                .content("hello")
                .build());

        assertEquals(99L, id);
    }

    @Test
    void shouldPersistMessageSummary() {
        service.addMessageSummary(ConversationSummaryBO.builder()
                .conversationId("c-1")
                .userId("u-1")
                .content("summary")
                .lastMessageId(8L)
                .build());

        ArgumentCaptor<ConversationSummaryDO> captor = ArgumentCaptor.forClass(ConversationSummaryDO.class);
        verify(conversationSummaryMapper).insert(captor.capture());
        assertEquals("summary", captor.getValue().getContent());
        assertEquals(8L, captor.getValue().getLastMessageId());
    }
}
