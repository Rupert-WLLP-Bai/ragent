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

package com.nageoffer.ai.ragent.rag.core.retrieve.postprocessor;

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchChannelResult;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchChannelType;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeduplicationPostProcessorTests {

    private final DeduplicationPostProcessor processor = new DeduplicationPostProcessor();

    @Test
    void shouldExposeStableProcessorMetadata() {
        assertEquals("Deduplication", processor.getName());
        assertEquals(1, processor.getOrder());
        assertTrue(processor.isEnabled(SearchContext.builder().build()));
    }

    @Test
    void shouldKeepHighestScoreForDuplicateChunksAcrossChannels() {
        RetrievedChunk vectorDuplicate = chunk("shared", "same text", 0.5f);
        RetrievedChunk intentDuplicate = chunk("shared", "same text", 0.9f);
        RetrievedChunk keywordOnly = chunk("keyword-only", "keyword text", 0.7f);

        List<RetrievedChunk> result = processor.process(
                List.of(),
                List.of(
                        channel(SearchChannelType.VECTOR_GLOBAL, List.of(vectorDuplicate)),
                        channel(SearchChannelType.KEYWORD_ES, List.of(keywordOnly)),
                        channel(SearchChannelType.INTENT_DIRECTED, List.of(intentDuplicate))
                ),
                SearchContext.builder().build()
        );

        assertEquals(2, result.size());
        assertEquals(List.of("shared", "keyword-only"), result.stream().map(RetrievedChunk::getId).toList());
        assertEquals(0.9f, result.get(0).getScore());
    }

    @Test
    void shouldDeduplicateChunksWithoutIdsByTextHash() {
        RetrievedChunk first = chunk(null, "same text", 0.4f);
        RetrievedChunk second = chunk(null, "same text", 0.8f);
        RetrievedChunk different = chunk(null, "different text", 0.3f);

        List<RetrievedChunk> result = processor.process(
                List.of(),
                List.of(channel(SearchChannelType.VECTOR_GLOBAL, List.of(first, second, different))),
                SearchContext.builder().build()
        );

        assertEquals(2, result.size());
        assertEquals(List.of(0.8f, 0.3f), result.stream().map(RetrievedChunk::getScore).toList());
    }

    private static SearchChannelResult channel(SearchChannelType type, List<RetrievedChunk> chunks) {
        return SearchChannelResult.builder()
                .channelType(type)
                .channelName(type.name())
                .chunks(chunks)
                .build();
    }

    private static RetrievedChunk chunk(String id, String text, float score) {
        return RetrievedChunk.builder()
                .id(id)
                .text(text)
                .score(score)
                .build();
    }
}
