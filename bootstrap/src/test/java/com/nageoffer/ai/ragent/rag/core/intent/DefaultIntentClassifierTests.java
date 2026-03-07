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

package com.nageoffer.ai.ragent.rag.core.intent;

import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptTemplateLoader;
import com.nageoffer.ai.ragent.rag.dao.mapper.IntentNodeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.nageoffer.ai.ragent.rag.constant.RAGConstant.INTENT_CLASSIFIER_PROMPT_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultIntentClassifierTests {

    @Mock
    private LLMService llmService;

    @Mock
    private IntentNodeMapper intentNodeMapper;

    @Mock
    private PromptTemplateLoader promptTemplateLoader;

    @Mock
    private IntentTreeCacheManager intentTreeCacheManager;

    @Test
    void shouldDelayPromptRenderingUntilClassificationRuns() {
        DefaultIntentClassifier classifier = new TestableDefaultIntentClassifier(
                llmService,
                intentNodeMapper,
                promptTemplateLoader,
                intentTreeCacheManager,
                List.of(List.of(singleLeaf("leaf-1", "考勤制度")))
        );

        IntentNode node = classifier.getNodeById("leaf-1");

        assertNotNull(node);
        assertEquals("leaf-1", node.getId());
        verify(promptTemplateLoader, never()).render(eq(INTENT_CLASSIFIER_PROMPT_PATH), any());
    }

    @Test
    void shouldReusePromptWhenIntentTreeSnapshotIsUnchanged() {
        DefaultIntentClassifier classifier = new TestableDefaultIntentClassifier(
                llmService,
                intentNodeMapper,
                promptTemplateLoader,
                intentTreeCacheManager,
                List.of(
                        List.of(singleLeaf("leaf-1", "考勤制度")),
                        List.of(singleLeaf("leaf-1", "考勤制度"))
                )
        );
        when(promptTemplateLoader.render(eq(INTENT_CLASSIFIER_PROMPT_PATH), any()))
                .thenReturn("cached-prompt");
        when(llmService.chat(any(ChatRequest.class))).thenReturn("[]");

        classifier.classifyTargets("问题一");
        classifier.classifyTargets("问题二");

        verify(promptTemplateLoader, times(1)).render(eq(INTENT_CLASSIFIER_PROMPT_PATH), any());
        verify(llmService, times(2)).chat(any(ChatRequest.class));
    }

    @Test
    void shouldRebuildPromptWhenIntentTreeSnapshotChanges() {
        DefaultIntentClassifier classifier = new TestableDefaultIntentClassifier(
                llmService,
                intentNodeMapper,
                promptTemplateLoader,
                intentTreeCacheManager,
                List.of(
                        List.of(singleLeaf("leaf-1", "考勤制度")),
                        List.of(singleLeaf("leaf-2", "请假制度"))
                )
        );
        when(promptTemplateLoader.render(eq(INTENT_CLASSIFIER_PROMPT_PATH), any()))
                .thenReturn("prompt-1", "prompt-2");
        when(llmService.chat(any(ChatRequest.class))).thenReturn("[]");

        classifier.classifyTargets("问题一");
        classifier.classifyTargets("问题二");

        verify(promptTemplateLoader, times(2)).render(eq(INTENT_CLASSIFIER_PROMPT_PATH), any());
    }

    private static IntentNode singleLeaf(String id, String name) {
        IntentNode node = IntentNode.builder()
                .id(id)
                .name(name)
                .description(name + "描述")
                .fullPath("根 > " + name)
                .examples(List.of(name + " 示例"))
                .children(List.of())
                .build();
        return node;
    }

    private static final class TestableDefaultIntentClassifier extends DefaultIntentClassifier {

        private final List<List<IntentNode>> rootsSequence;
        private int index = 0;

        private TestableDefaultIntentClassifier(LLMService llmService,
                                                IntentNodeMapper intentNodeMapper,
                                                PromptTemplateLoader promptTemplateLoader,
                                                IntentTreeCacheManager intentTreeCacheManager,
                                                List<List<IntentNode>> rootsSequence) {
            super(llmService, intentNodeMapper, promptTemplateLoader, intentTreeCacheManager);
            this.rootsSequence = rootsSequence;
        }

        @Override
        List<IntentNode> loadIntentTreeRoots() {
            int currentIndex = Math.min(index, rootsSequence.size() - 1);
            index++;
            return rootsSequence.get(currentIndex);
        }
    }
}
