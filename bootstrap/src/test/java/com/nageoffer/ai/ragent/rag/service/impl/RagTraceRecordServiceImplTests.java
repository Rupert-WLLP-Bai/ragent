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

package com.nageoffer.ai.ragent.rag.service.impl;

import com.nageoffer.ai.ragent.rag.dao.entity.RagTraceNodeDO;
import com.nageoffer.ai.ragent.rag.dao.entity.RagTraceRunDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.RagTraceNodeMapper;
import com.nageoffer.ai.ragent.rag.dao.mapper.RagTraceRunMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagTraceRecordServiceImplTests {

    @Mock
    private RagTraceRunMapper runMapper;
    @Mock
    private RagTraceNodeMapper nodeMapper;

    @InjectMocks
    private RagTraceRecordServiceImpl service;

    @Test
    void shouldAppendDecisionPayloadToRunExtraData() {
        service.appendRunExtraData("trace-1", "{\"thinkingMode\":\"DEEP\"}");

        ArgumentCaptor<RagTraceRunDO> captor = ArgumentCaptor.forClass(RagTraceRunDO.class);
        verify(runMapper).update(captor.capture(), any());
        assertEquals("{\"thinkingMode\":\"DEEP\"}", captor.getValue().getExtraData());
    }

    @Test
    void shouldInsertDecisionNodeWhenAbsent() {
        when(nodeMapper.selectOne(any())).thenReturn(null);

        service.upsertDecisionNode("trace-1", "task-1", "{\"queryType\":\"MIXED\"}");

        ArgumentCaptor<RagTraceNodeDO> captor = ArgumentCaptor.forClass(RagTraceNodeDO.class);
        verify(nodeMapper).insert(captor.capture());
        assertEquals("DECISION", captor.getValue().getNodeType());
        assertEquals("{\"queryType\":\"MIXED\"}", captor.getValue().getExtraData());
        assertEquals("SUCCESS", captor.getValue().getStatus());
        assertTrue(captor.getValue().getDurationMs() >= 0L);
    }
}
