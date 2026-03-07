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

package com.nageoffer.ai.ragent.rag.core.retrieve;

import com.nageoffer.ai.ragent.infra.embedding.EmbeddingService;
import com.nageoffer.ai.ragent.rag.config.RAGDefaultProperties;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MilvusRetrieverServiceTests {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private MilvusClientV2 milvusClient;

    @Test
    void shouldReuseProvidedQueryVectorWithoutReembedding() {
        RAGDefaultProperties properties = new RAGDefaultProperties();
        properties.setCollectionName("default_collection");
        properties.setMetricType("COSINE");
        MilvusRetrieverService service = new MilvusRetrieverService(embeddingService, milvusClient, properties);
        float[] queryVector = new float[]{0.6f, 0.8f};
        when(milvusClient.search(any(SearchReq.class))).thenReturn(emptyResponse());

        List<RetrievedChunk> result = service.retrieve(RetrieveRequest.builder()
                .query("repeat retrieval")
                .collectionName("kb_repeat")
                .topK(6)
                .queryVector(queryVector)
                .build());

        verify(embeddingService, never()).embed("repeat retrieval");
        ArgumentCaptor<SearchReq> captor = ArgumentCaptor.forClass(SearchReq.class);
        verify(milvusClient).search(captor.capture());
        SearchReq searchReq = captor.getValue();
        assertEquals("kb_repeat", searchReq.getCollectionName());
        assertEquals(6, searchReq.getTopK());
        FloatVec vector = (FloatVec) searchReq.getData().get(0);
        assertEquals(List.of(0.6f, 0.8f), vector.getData());
        assertEquals(List.of(), result);
    }

    @Test
    void shouldNormalizeEmbeddedVectorWhenNoPrecomputedVectorExists() {
        RAGDefaultProperties properties = new RAGDefaultProperties();
        properties.setCollectionName("default_collection");
        properties.setMetricType("COSINE");
        MilvusRetrieverService service = new MilvusRetrieverService(embeddingService, milvusClient, properties);
        when(embeddingService.embed("fresh query")).thenReturn(List.of(3.0f, 4.0f));
        when(milvusClient.search(any(SearchReq.class))).thenReturn(emptyResponse());

        service.retrieve(RetrieveRequest.builder()
                .query("fresh query")
                .topK(5)
                .build());

        verify(embeddingService).embed("fresh query");
        ArgumentCaptor<SearchReq> captor = ArgumentCaptor.forClass(SearchReq.class);
        verify(milvusClient).search(captor.capture());
        FloatVec vector = (FloatVec) captor.getValue().getData().get(0);
        assertEquals(List.of(0.6f, 0.8f), vector.getData());
    }

    @Test
    void shouldAttachCollectionProvenanceToRetrievedChunk() {
        RAGDefaultProperties properties = new RAGDefaultProperties();
        properties.setCollectionName("default_collection");
        properties.setMetricType("COSINE");
        MilvusRetrieverService service = new MilvusRetrieverService(embeddingService, milvusClient, properties);
        when(milvusClient.search(any(SearchReq.class))).thenReturn(searchResponseWithSingleHit());

        List<RetrievedChunk> result = service.retrieve(RetrieveRequest.builder()
                .query("repeat retrieval")
                .collectionName("kb_repeat")
                .topK(1)
                .queryVector(new float[]{0.6f, 0.8f})
                .build());

        assertEquals(1, result.size());
        assertEquals("kb_repeat", result.get(0).getProvenance().get("collection"));
    }

    private static SearchResp emptyResponse() {
        return SearchResp.builder()
                .searchResults(Collections.emptyList())
                .build();
    }

    private static SearchResp searchResponseWithSingleHit() {
        SearchResp.SearchResult hit = SearchResp.SearchResult.builder()
                .entity(Map.of("doc_id", "doc-1", "content", "知识片段"))
                .score(0.91f)
                .build();
        return SearchResp.builder()
                .searchResults(List.of(List.of(hit)))
                .build();
    }
}
