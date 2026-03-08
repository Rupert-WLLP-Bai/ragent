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

import com.nageoffer.ai.ragent.rag.controller.vo.RagTraceDetailVO;
import com.nageoffer.ai.ragent.rag.dao.entity.RagTraceNodeDO;
import com.nageoffer.ai.ragent.rag.dao.entity.RagTraceRunDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.RagTraceNodeMapper;
import com.nageoffer.ai.ragent.rag.dao.mapper.RagTraceRunMapper;
import com.nageoffer.ai.ragent.user.dao.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagTraceQueryServiceImplTests {

    @Mock
    private RagTraceRunMapper runMapper;
    @Mock
    private RagTraceNodeMapper nodeMapper;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private RagTraceQueryServiceImpl service;

    @Test
    void shouldExposeStructuredDecisionPayloadInTraceDetail() {
        when(runMapper.selectOne(any())).thenReturn(RagTraceRunDO.builder()
                .traceId("trace-1")
                .traceName("rag-stream-chat")
                .status("SUCCESS")
                .extraData("{\"thinkingMode\":\"DEEP\",\"selectedModelTier\":\"DEEP_THINKING\"}")
                .startTime(new Date())
                .endTime(new Date())
                .build());
        when(nodeMapper.selectList(any())).thenReturn(List.of(RagTraceNodeDO.builder()
                .traceId("trace-1")
                .nodeId("n-1")
                .nodeType("DECISION")
                .nodeName("request-decision")
                .status("SUCCESS")
                .extraData("{\"queryType\":\"MIXED\"}")
                .startTime(new Date())
                .endTime(new Date())
                .build()));

        RagTraceDetailVO detail = service.detail("trace-1");

        assertEquals("{\"thinkingMode\":\"DEEP\",\"selectedModelTier\":\"DEEP_THINKING\"}", detail.getRun().getExtraData());
        assertEquals("{\"queryType\":\"MIXED\"}", detail.getNodes().get(0).getExtraData());
    }
}
