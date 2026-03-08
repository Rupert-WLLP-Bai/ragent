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

import com.nageoffer.ai.ragent.rag.core.plan.RetrievalPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SearchContextPlanTests {

    @Test
    void shouldCarryRetrievalPlanForDownstreamStages() {
        RetrievalPlan retrievalPlan = new RetrievalPlan(RetrievalPlan.RetrievalMode.MIXED, 8);
        SearchContext context = SearchContext.builder()
                .originalQuestion("original")
                .rewrittenQuestion("rewritten")
                .topK(8)
                .retrievalPlan(retrievalPlan)
                .build();

        assertEquals("rewritten", context.getMainQuestion());
        assertEquals(8, context.getTopK());
        assertSame(retrievalPlan, context.getRetrievalPlan());
        assertEquals(RetrievalPlan.RetrievalMode.MIXED, context.getRetrievalPlan().retrievalMode());
    }
}
