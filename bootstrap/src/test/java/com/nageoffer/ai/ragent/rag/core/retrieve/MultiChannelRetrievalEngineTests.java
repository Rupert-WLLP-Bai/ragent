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

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.infra.embedding.EmbeddingService;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchChannel;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchChannelResult;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchChannelType;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchContext;
import com.nageoffer.ai.ragent.rag.core.retrieve.postprocessor.SearchResultPostProcessor;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultiChannelRetrievalEngineTests {

    @Mock
    private EmbeddingService embeddingService;

    @Test
    void shouldReuseSingleRequestEmbeddingAcrossRetrievalChannels() {
        SearchChannel firstChannel = new CapturingSearchChannel("first");
        SearchChannel secondChannel = new CapturingSearchChannel("second");
        SearchResultPostProcessor passthrough = new PassthroughPostProcessor();
        Executor directExecutor = Runnable::run;
        MultiChannelRetrievalEngine engine = new MultiChannelRetrievalEngine(
                embeddingService,
                List.of(firstChannel, secondChannel),
                List.of(passthrough),
                directExecutor
        );
        List<Float> rawEmbedding = List.of(3.0f, 4.0f);
        when(embeddingService.embed("how to optimize retrieval"))
                .thenReturn(rawEmbedding);

        List<RetrievedChunk> result = engine.retrieveKnowledgeChannels(
                List.of(subIntent("how to optimize retrieval", 0.55D, "kb-a")),
                5
        );

        verify(embeddingService, times(1)).embed("how to optimize retrieval");
        assertEquals(2, result.size());

        float[] expectedVector = new float[]{0.6f, 0.8f};
        assertArrayEquals(expectedVector, ((CapturingSearchChannel) firstChannel).capturedVector, 0.0001f);
        assertArrayEquals(expectedVector, ((CapturingSearchChannel) secondChannel).capturedVector, 0.0001f);
    }

    @Test
    void shouldSkipEmbeddingWhenQuestionIsBlank() {
        SearchChannel channel = new CapturingSearchChannel("blank");
        MultiChannelRetrievalEngine engine = new MultiChannelRetrievalEngine(
                embeddingService,
                List.of(channel),
                List.of(new PassthroughPostProcessor()),
                Runnable::run
        );

        engine.retrieveKnowledgeChannels(List.of(subIntent("   ", 0.2D, "kb-a")), 3);

        verify(embeddingService, times(0)).embed(anyString());
        assertNull(((CapturingSearchChannel) channel).capturedVector);
    }

    private static SubQuestionIntent subIntent(String question, double score, String collection) {
        IntentNode node = IntentNode.builder()
                .id(collection)
                .name(collection)
                .collectionName(collection)
                .build();
        return new SubQuestionIntent(question, List.of(NodeScore.builder()
                .node(node)
                .score(score)
                .build()));
    }

    private static final class CapturingSearchChannel implements SearchChannel {
        private final String name;
        private float[] capturedVector;

        private CapturingSearchChannel(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getPriority() {
            return 1;
        }

        @Override
        public boolean isEnabled(SearchContext context) {
            return true;
        }

        @Override
        public SearchChannelResult search(SearchContext context) {
            capturedVector = context.getQueryVector();
            return SearchChannelResult.builder()
                    .channelType(SearchChannelType.INTENT_DIRECTED)
                    .channelName(name)
                    .chunks(List.of(RetrievedChunk.builder()
                            .id(name)
                            .text("chunk-" + name)
                            .score(1.0f)
                            .build()))
                    .confidence(1.0D)
                    .build();
        }

        @Override
        public SearchChannelType getType() {
            return SearchChannelType.INTENT_DIRECTED;
        }
    }

    private static final class PassthroughPostProcessor implements SearchResultPostProcessor {

        @Override
        public String getName() {
            return "pass-through";
        }

        @Override
        public int getOrder() {
            return 0;
        }

        @Override
        public boolean isEnabled(SearchContext context) {
            return true;
        }

        @Override
        public List<RetrievedChunk> process(List<RetrievedChunk> chunks,
                                            List<SearchChannelResult> results,
                                            SearchContext context) {
            return chunks;
        }
    }
}
