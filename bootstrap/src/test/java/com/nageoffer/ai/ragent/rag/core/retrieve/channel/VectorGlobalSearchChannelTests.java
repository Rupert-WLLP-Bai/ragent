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

package com.nageoffer.ai.ragent.rag.core.retrieve.channel;

import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeBaseDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.rag.config.SearchChannelProperties;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieveRequest;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieverService;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VectorGlobalSearchChannelTests {

    @Mock
    private RetrieverService retrieverService;

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Test
    void shouldBoundFallbackFanOutAndPreferIntentCollections() {
        SearchChannelProperties properties = new SearchChannelProperties();
        properties.getChannels().getVectorGlobal().setMaxCollections(2);
        properties.getChannels().getVectorGlobal().setPreferIntentCollections(true);
        properties.getChannels().getVectorGlobal().setTopKMultiplier(3);
        VectorGlobalSearchChannel channel = new VectorGlobalSearchChannel(
                retrieverService,
                properties,
                knowledgeBaseMapper,
                Runnable::run
        );
        when(retrieverService.retrieve(any(RetrieveRequest.class))).thenReturn(List.of());
        when(knowledgeBaseMapper.selectList(any())).thenReturn(List.of(
                knowledgeBase("intent-b"),
                knowledgeBase("intent-a"),
                knowledgeBase("global-extra")
        ));
        float[] queryVector = new float[]{0.6f, 0.8f};

        channel.search(SearchContext.builder()
                .rewrittenQuestion("where should fallback search go")
                .topK(4)
                .queryVector(queryVector)
                .intents(List.of(new SubQuestionIntent("where should fallback search go", List.of(
                        nodeScore("intent-b", 0.52D),
                        nodeScore("intent-a", 0.41D),
                        nodeScore("global-extra", 0.21D)
                ))))
                .build());

        ArgumentCaptor<RetrieveRequest> captor = ArgumentCaptor.forClass(RetrieveRequest.class);
        verify(retrieverService, times(2)).retrieve(captor.capture());
        List<RetrieveRequest> requests = captor.getAllValues();
        assertEquals(List.of("intent-b", "intent-a"), requests.stream()
                .map(RetrieveRequest::getCollectionName)
                .toList());
        assertEquals(List.of(12, 12), requests.stream().map(RetrieveRequest::getTopK).toList());
        assertArrayEquals(queryVector, requests.get(0).getQueryVector());
        assertArrayEquals(queryVector, requests.get(1).getQueryVector());
    }

    @Test
    void shouldFillRemainingFallbackBudgetFromGlobalCollections() {
        SearchChannelProperties properties = new SearchChannelProperties();
        properties.getChannels().getVectorGlobal().setMaxCollections(3);
        properties.getChannels().getVectorGlobal().setPreferIntentCollections(true);
        VectorGlobalSearchChannel channel = new VectorGlobalSearchChannel(
                retrieverService,
                properties,
                knowledgeBaseMapper,
                Runnable::run
        );
        when(retrieverService.retrieve(any(RetrieveRequest.class))).thenReturn(List.of());
        when(knowledgeBaseMapper.selectList(any())).thenReturn(List.of(
                knowledgeBase("global-b"),
                knowledgeBase("global-c"),
                knowledgeBase("global-d"),
                knowledgeBase("global-e")
        ));

        channel.search(SearchContext.builder()
                .rewrittenQuestion("fallback budget")
                .topK(2)
                .queryVector(new float[]{1.0f})
                .intents(List.of(new SubQuestionIntent("fallback budget", List.of(
                        nodeScore("intent-a", 0.3D),
                        nodeScore("unknown-intent", 0.2D)
                ))))
                .build());

        ArgumentCaptor<RetrieveRequest> captor = ArgumentCaptor.forClass(RetrieveRequest.class);
        verify(retrieverService, times(3)).retrieve(captor.capture());
        assertEquals(List.of("global-b", "global-c", "global-d"), captor.getAllValues().stream()
                .map(RetrieveRequest::getCollectionName)
                .toList());
    }

    private static NodeScore nodeScore(String collectionName, double score) {
        return NodeScore.builder()
                .score(score)
                .node(IntentNode.builder()
                        .id(collectionName)
                        .name(collectionName)
                        .collectionName(collectionName)
                        .build())
                .build();
    }

    private static KnowledgeBaseDO knowledgeBase(String collectionName) {
        KnowledgeBaseDO knowledgeBase = new KnowledgeBaseDO();
        knowledgeBase.setCollectionName(collectionName);
        return knowledgeBase;
    }
}
