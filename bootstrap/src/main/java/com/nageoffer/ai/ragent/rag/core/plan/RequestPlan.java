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

package com.nageoffer.ai.ragent.rag.core.plan;

/**
 * 请求级决策对象。
 *
 * <p>第一版只承载现有主链路中的保守决策，不主动改变原有业务语义。</p>
 */
public record RequestPlan(QueryType queryType,
                          RewriteMode rewriteMode,
                          RetrievalPlan retrievalPlan,
                          ResponseMode responseMode,
                          ThinkingMode thinkingMode) {

    public enum QueryType {
        STANDARD,
        SYSTEM_ONLY
    }

    public enum RewriteMode {
        REWRITE_WITH_SPLIT
    }

    public enum ResponseMode {
        GUIDANCE_PROMPT,
        DIRECT_LLM,
        RAG,
        EMPTY_HIT
    }

    public enum ThinkingMode {
        STANDARD,
        DEEP
    }

    public static RequestPlan start(boolean deepThinking) {
        return new RequestPlan(
                QueryType.STANDARD,
                RewriteMode.REWRITE_WITH_SPLIT,
                RetrievalPlan.none(),
                ResponseMode.RAG,
                deepThinking ? ThinkingMode.DEEP : ThinkingMode.STANDARD
        );
    }

    public RequestPlan withRetrievalPlan(RetrievalPlan nextRetrievalPlan) {
        return new RequestPlan(queryType, rewriteMode, nextRetrievalPlan, responseMode, thinkingMode);
    }

    public RequestPlan forGuidancePrompt() {
        return new RequestPlan(queryType, rewriteMode, RetrievalPlan.none(), ResponseMode.GUIDANCE_PROMPT, ThinkingMode.STANDARD);
    }

    public RequestPlan forSystemOnly() {
        return new RequestPlan(QueryType.SYSTEM_ONLY, rewriteMode, RetrievalPlan.none(), ResponseMode.DIRECT_LLM, ThinkingMode.STANDARD);
    }

    public RequestPlan forEmptyHit() {
        return new RequestPlan(queryType, rewriteMode, retrievalPlan, ResponseMode.EMPTY_HIT, thinkingMode);
    }

    public boolean deepThinkingEnabled() {
        return thinkingMode == ThinkingMode.DEEP;
    }
}
