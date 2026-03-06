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

package com.nageoffer.ai.ragent.ingestion.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.core.chunk.VectorChunk;
import com.nageoffer.ai.ragent.ingestion.domain.context.DocumentSource;
import com.nageoffer.ai.ragent.ingestion.domain.context.IngestionContext;
import com.nageoffer.ai.ragent.ingestion.domain.context.StructuredDocument;
import com.nageoffer.ai.ragent.ingestion.domain.enums.SourceType;
import com.nageoffer.ai.ragent.ingestion.domain.pipeline.NodeConfig;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class NodeOutputExtractorTests {

    private final NodeOutputExtractor extractor = new NodeOutputExtractor();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReturnFetcherSpecificOutput() {
        byte[] raw = "hello".getBytes(StandardCharsets.UTF_8);
        IngestionContext context = IngestionContext.builder()
                .source(DocumentSource.builder()
                        .type(SourceType.FILE)
                        .location("/tmp/demo.txt")
                        .fileName("demo.txt")
                        .build())
                .mimeType("text/plain")
                .rawBytes(raw)
                .build();
        NodeConfig config = NodeConfig.builder().nodeType("fetcher").build();

        Map<String, Object> output = extractor.extract(context, config);

        Map<?, ?> source = assertInstanceOf(Map.class, output.get("source"));
        assertEquals("file", source.get("type"));
        assertEquals("/tmp/demo.txt", source.get("location"));
        assertEquals(raw.length, output.get("rawBytesLength"));
        assertEquals(Base64.getEncoder().encodeToString(raw), output.get("rawBytesBase64"));
    }

    @Test
    void shouldReturnParserEnhancerAndIndexerOutputs() throws Exception {
        IngestionContext context = IngestionContext.builder()
                .mimeType("application/json")
                .rawText("raw-text")
                .enhancedText("enhanced")
                .keywords(List.of("k1"))
                .questions(List.of("q1"))
                .metadata(Map.of("source", "demo"))
                .document(StructuredDocument.builder().text("doc-text").build())
                .chunks(List.of(VectorChunk.builder().chunkId("c1").content("chunk").build()))
                .build();

        Map<String, Object> parserOutput = extractor.extract(context, NodeConfig.builder().nodeType("parser").build());
        assertEquals("raw-text", parserOutput.get("rawText"));
        assertEquals(context.getDocument(), parserOutput.get("document"));

        Map<String, Object> enhancerOutput = extractor.extract(context, NodeConfig.builder().nodeType("enhancer").build());
        assertEquals("enhanced", enhancerOutput.get("enhancedText"));
        assertEquals(List.of("k1"), enhancerOutput.get("keywords"));

        Map<String, Object> indexerOutput = extractor.extract(
                context,
                NodeConfig.builder().nodeType("indexer").settings(objectMapper.readTree("{\"batchSize\":10}")).build()
        );
        assertEquals(1, indexerOutput.get("chunkCount"));
        assertEquals(context.getChunks(), indexerOutput.get("chunks"));
        assertEquals(10, assertInstanceOf(Map.class, objectMapper.convertValue(indexerOutput.get("settings"), Map.class)).get("batchSize"));
    }

    @Test
    void shouldFallbackToGenericOutputForUnknownTypeOrMissingInputs() {
        IngestionContext context = IngestionContext.builder()
                .mimeType("text/plain")
                .rawText("raw")
                .enhancedText("enhanced")
                .keywords(List.of("k"))
                .questions(List.of("q"))
                .chunks(List.of())
                .build();

        Map<String, Object> output = extractor.extract(context, NodeConfig.builder().nodeType("unknown").build());
        assertEquals("raw", output.get("rawText"));
        assertEquals("enhanced", output.get("enhancedText"));
        assertEquals(List.of("k"), output.get("keywords"));

        assertEquals(Map.of(), extractor.extract(null, NodeConfig.builder().nodeType("fetcher").build()));
        assertEquals(Map.of(), extractor.extract(context, null));
    }
}
