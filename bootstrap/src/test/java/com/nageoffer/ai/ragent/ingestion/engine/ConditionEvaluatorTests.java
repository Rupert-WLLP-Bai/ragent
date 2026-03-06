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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.ingestion.domain.context.IngestionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionEvaluatorTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ConditionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new ConditionEvaluator(objectMapper);
    }

    @Test
    void shouldHandleNullBooleanAndSpelConditions() {
        IngestionContext context = IngestionContext.builder().mimeType("text/plain").build();

        assertTrue(evaluator.evaluate(context, null));
        assertTrue(evaluator.evaluate(context, objectMapper.getNodeFactory().booleanNode(true)));
        assertFalse(evaluator.evaluate(context, objectMapper.getNodeFactory().booleanNode(false)));
        assertTrue(evaluator.evaluate(context, objectMapper.getNodeFactory().textNode("mimeType == 'text/plain'")));
        assertFalse(evaluator.evaluate(context, objectMapper.getNodeFactory().textNode("mimeType == 'application/pdf'")));
    }

    @Test
    void shouldEvaluateAllAnyAndNotRules() throws Exception {
        IngestionContext context = IngestionContext.builder()
                .mimeType("text/plain")
                .keywords(List.of("rag", "agent"))
                .build();

        JsonNode condition = objectMapper.readTree("""
                {
                  "all": [
                    {"field":"mimeType","operator":"eq","value":"text/plain"},
                    {"any": [
                      {"field":"keywords","operator":"contains","value":"rag"},
                      {"field":"keywords","operator":"contains","value":"missing"}
                    ]},
                    {"not": {"field":"mimeType","operator":"eq","value":"application/json"}}
                  ]
                }
                """);

        assertTrue(evaluator.evaluate(context, condition));
    }

    @Test
    void shouldSupportComparisonOperatorsAndMissingFields() throws Exception {
        IngestionContext context = IngestionContext.builder()
                .rawText("hello world")
                .build();

        assertTrue(evaluator.evaluate(context, objectMapper.readTree("{" +
                "\"field\":\"rawText\",\"operator\":\"contains\",\"value\":\"world\"}")));
        assertTrue(evaluator.evaluate(context, objectMapper.readTree("{" +
                "\"field\":\"taskId\",\"operator\":\"not_exists\"}")));
        assertTrue(evaluator.evaluate(context, objectMapper.readTree("{" +
                "\"field\":\"taskId\",\"operator\":\"exists\"}")) == false);
        assertTrue(evaluator.evaluate(IngestionContext.builder().build(), objectMapper.readTree("{" +
                "\"field\":\"missing\",\"operator\":\"eq\",\"value\":\"x\"}")) == false);
    }

    @Test
    void shouldCompareNumbersAndRegexPatterns() throws Exception {
        IngestionContext context = IngestionContext.builder().build();
        context.setMetadata(java.util.Map.of("size", 5));

        assertTrue(evaluator.evaluate(context, objectMapper.readTree("{" +
                "\"field\":\"metadata[size]\",\"operator\":\"gte\",\"value\":3}")));
        assertFalse(evaluator.evaluate(context, objectMapper.readTree("{" +
                "\"field\":\"metadata[size]\",\"operator\":\"lt\",\"value\":3}")));

        IngestionContext regexContext = IngestionContext.builder().mimeType("text/plain").build();
        assertTrue(evaluator.evaluate(regexContext, objectMapper.readTree("{" +
                "\"field\":\"mimeType\",\"operator\":\"regex\",\"value\":\"text/.*\"}")));
    }
}
