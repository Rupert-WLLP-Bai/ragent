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

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.rag.dao.entity.RagTraceNodeDO;
import com.nageoffer.ai.ragent.rag.dao.entity.RagTraceRunDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.RagTraceNodeMapper;
import com.nageoffer.ai.ragent.rag.dao.mapper.RagTraceRunMapper;
import com.nageoffer.ai.ragent.rag.service.RagTraceRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * RAG Trace 记录服务实现
 */
@Service
@RequiredArgsConstructor
public class RagTraceRecordServiceImpl implements RagTraceRecordService {

    private final RagTraceRunMapper runMapper;
    private final RagTraceNodeMapper nodeMapper;

    @Override
    public void startRun(RagTraceRunDO run) {
        runMapper.insert(run);
    }

    @Override
    public void finishRun(String traceId, String status, String errorMessage, Date endTime, long durationMs) {
        RagTraceRunDO update = RagTraceRunDO.builder()
                .status(status)
                .errorMessage(errorMessage)
                .endTime(endTime)
                .durationMs(durationMs)
                .build();
        runMapper.update(update, Wrappers.lambdaUpdate(RagTraceRunDO.class)
                .eq(RagTraceRunDO::getTraceId, traceId));
    }

    @Override
    public void startNode(RagTraceNodeDO node) {
        nodeMapper.insert(node);
    }

    @Override
    public void finishNode(String traceId, String nodeId, String status, String errorMessage, Date endTime, long durationMs) {
        RagTraceNodeDO update = RagTraceNodeDO.builder()
                .status(status)
                .errorMessage(errorMessage)
                .endTime(endTime)
                .durationMs(durationMs)
                .build();
        nodeMapper.update(update, Wrappers.lambdaUpdate(RagTraceNodeDO.class)
                .eq(RagTraceNodeDO::getTraceId, traceId)
                .eq(RagTraceNodeDO::getNodeId, nodeId));
    }

    @Override
    public void appendRunExtraData(String traceId, String extraData) {
        if (StrUtil.isBlank(traceId) || StrUtil.isBlank(extraData)) {
            return;
        }
        RagTraceRunDO update = RagTraceRunDO.builder()
                .extraData(extraData)
                .build();
        runMapper.update(update, Wrappers.lambdaUpdate(RagTraceRunDO.class)
                .eq(RagTraceRunDO::getTraceId, traceId));
    }

    @Override
    public void upsertDecisionNode(String traceId, String taskId, String extraData) {
        if (StrUtil.isBlank(traceId) || StrUtil.isBlank(extraData)) {
            return;
        }
        RagTraceNodeDO existing = nodeMapper.selectOne(Wrappers.lambdaQuery(RagTraceNodeDO.class)
                .eq(RagTraceNodeDO::getTraceId, traceId)
                .eq(RagTraceNodeDO::getNodeType, "DECISION")
                .last("limit 1"));
        if (existing != null) {
            RagTraceNodeDO update = RagTraceNodeDO.builder()
                    .extraData(extraData)
                    .status("SUCCESS")
                    .build();
            nodeMapper.update(update, Wrappers.lambdaUpdate(RagTraceNodeDO.class)
                    .eq(RagTraceNodeDO::getTraceId, traceId)
                    .eq(RagTraceNodeDO::getNodeId, existing.getNodeId()));
            return;
        }
        nodeMapper.insert(RagTraceNodeDO.builder()
                .traceId(traceId)
                .nodeId(IdUtil.getSnowflakeNextIdStr())
                .parentNodeId(null)
                .depth(0)
                .nodeType("DECISION")
                .nodeName("request-decision")
                .className(RagTraceRecordServiceImpl.class.getName())
                .methodName("upsertDecisionNode")
                .status("SUCCESS")
                .startTime(new Date())
                .endTime(new Date())
                .durationMs(0L)
                .extraData(extraData)
                .build());
    }
}
