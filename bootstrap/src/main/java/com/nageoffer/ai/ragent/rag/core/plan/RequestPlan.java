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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 请求级决策对象。
 *
 * <p>当前版本用于保守地表达后端主链路的 rewrite / retrieve / response / thinking / model gate 决策。</p>
 */
public record RequestPlan(QueryType queryType,
                          RewriteMode rewriteMode,
                          RetrievalPlan retrievalPlan,
                          ResponseMode responseMode,
                          ThinkingMode thinkingMode,
                          ModelTier modelTier,
                          List<String> decisionReasons) {

    public enum QueryType {
        STANDARD,
        SIMPLE_KB,
        MIXED,
        TOOL_FIRST,
        MULTI_HOP,
        AMBIGUOUS,
        SYSTEM_ONLY
    }

    public enum RewriteMode {
        NORMALIZE_ONLY,
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

    public enum ModelTier {
        LOW_COST,
        DEEP_THINKING
    }

    public static RequestPlan initial(RewriteMode rewriteMode,
                                      ThinkingMode thinkingMode,
                                      ModelTier modelTier,
                                      List<String> decisionReasons) {
        return new RequestPlan(
                QueryType.STANDARD,
                rewriteMode,
                RetrievalPlan.none(),
                ResponseMode.RAG,
                thinkingMode,
                modelTier,
                List.copyOf(decisionReasons)
        );
    }

    public RequestPlan withDecision(QueryType nextQueryType,
                                    RetrievalPlan nextRetrievalPlan,
                                    ResponseMode nextResponseMode,
                                    ThinkingMode nextThinkingMode,
                                    ModelTier nextModelTier,
                                    List<String> nextDecisionReasons) {
        return new RequestPlan(
                nextQueryType,
                rewriteMode,
                nextRetrievalPlan,
                nextResponseMode,
                nextThinkingMode,
                nextModelTier,
                List.copyOf(nextDecisionReasons)
        );
    }

    public boolean deepThinkingEnabled() {
        return thinkingMode == ThinkingMode.DEEP;
    }

    public Map<String, Object> toTracePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("queryType", queryType.name());
        payload.put("rewriteMode", rewriteMode.name());
        payload.put("retrievalMode", retrievalPlan.retrievalMode().name());
        payload.put("responseMode", responseMode.name());
        payload.put("thinkingMode", thinkingMode.name());
        payload.put("selectedModelTier", modelTier.name());
        payload.put("topK", retrievalPlan.topK());
        payload.put("reasons", decisionReasons);
        return payload;
    }
}
