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

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.rag.core.guidance.GuidanceDecision;
import com.nageoffer.ai.ragent.rag.dto.IntentGroup;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.nageoffer.ai.ragent.rag.constant.RAGConstant.DEFAULT_TOP_K;

/**
 * 后端请求规划器。
 *
 * <p>目标是以保守规则决定 rewrite / retrieval / response / thinking / model tier，
 * 避免把这部分判断留在前端。</p>
 */
@Component
public class RequestPlanner {

    private static final int SIMPLE_QUERY_LENGTH_THRESHOLD = 24;
    private static final int COMPLEX_QUERY_LENGTH_THRESHOLD = 48;
    private static final int COMPLEX_SUBQUESTION_THRESHOLD = 2;

    public RequestPlan createInitialPlan(String question, boolean userRequestedDeepThinking) {
        List<String> reasons = new ArrayList<>();
        boolean simpleQuestion = isSimpleQuestion(question);
        RequestPlan.RewriteMode rewriteMode = simpleQuestion
                ? RequestPlan.RewriteMode.NORMALIZE_ONLY
                : RequestPlan.RewriteMode.REWRITE_WITH_SPLIT;
        reasons.add(simpleQuestion
                ? "simple question uses normalize-only rewrite to keep a low-cost path"
                : "question looks complex enough to justify rewrite-and-split");

        RequestPlan.ThinkingMode thinkingMode = userRequestedDeepThinking
                ? RequestPlan.ThinkingMode.DEEP
                : RequestPlan.ThinkingMode.STANDARD;
        RequestPlan.ModelTier modelTier = userRequestedDeepThinking
                ? RequestPlan.ModelTier.DEEP_THINKING
                : RequestPlan.ModelTier.LOW_COST;
        reasons.add(userRequestedDeepThinking
                ? "user explicitly requested deep thinking"
                : "default to low-cost tier until backend complexity signals require escalation");

        return RequestPlan.initial(rewriteMode, thinkingMode, modelTier, reasons);
    }

    public RequestPlan finalizePlan(RequestPlan initialPlan,
                                    String originalQuestion,
                                    List<String> subQuestions,
                                    List<SubQuestionIntent> subIntents,
                                    GuidanceDecision guidanceDecision,
                                    IntentGroup intentGroup,
                                    boolean allSystemOnly,
                                    boolean retrievalEmpty,
                                    boolean userRequestedDeepThinking) {
        if (guidanceDecision != null && guidanceDecision.isPrompt()) {
            return initialPlan.withDecision(
                    RequestPlan.QueryType.AMBIGUOUS,
                    RetrievalPlan.none(),
                    RequestPlan.ResponseMode.GUIDANCE_PROMPT,
                    RequestPlan.ThinkingMode.STANDARD,
                    RequestPlan.ModelTier.LOW_COST,
                    List.of(
                            "ambiguity guidance is required, so the backend keeps a clarify-first low-cost route",
                            "retrieval is skipped until the user clarifies the request"
                    )
            );
        }

        if (allSystemOnly) {
            return initialPlan.withDecision(
                    RequestPlan.QueryType.SYSTEM_ONLY,
                    RetrievalPlan.none(),
                    RequestPlan.ResponseMode.DIRECT_LLM,
                    RequestPlan.ThinkingMode.STANDARD,
                    RequestPlan.ModelTier.LOW_COST,
                    List.of(
                            "all resolved intents are system-only, so knowledge retrieval is skipped",
                            "system-only requests stay on the direct low-cost path"
                    )
            );
        }

        RetrievalPlan retrievalPlan = RetrievalPlan.from(intentGroup, DEFAULT_TOP_K);
        RequestPlan.QueryType queryType = resolveQueryType(initialPlan, originalQuestion, subQuestions, retrievalPlan);
        boolean shouldEscalateThinking = shouldUseDeepThinking(
                originalQuestion,
                subQuestions,
                retrievalPlan,
                queryType,
                userRequestedDeepThinking
        );
        RequestPlan.ThinkingMode thinkingMode = shouldEscalateThinking
                ? RequestPlan.ThinkingMode.DEEP
                : RequestPlan.ThinkingMode.STANDARD;
        RequestPlan.ModelTier modelTier = shouldEscalateThinking
                ? RequestPlan.ModelTier.DEEP_THINKING
                : RequestPlan.ModelTier.LOW_COST;
        RequestPlan.ResponseMode responseMode = retrievalEmpty
                ? RequestPlan.ResponseMode.EMPTY_HIT
                : RequestPlan.ResponseMode.RAG;

        return initialPlan.withDecision(
                queryType,
                retrievalPlan,
                responseMode,
                thinkingMode,
                modelTier,
                buildReasons(queryType, retrievalPlan, thinkingMode, responseMode, userRequestedDeepThinking, subIntents)
        );
    }

    private RequestPlan.QueryType resolveQueryType(RequestPlan initialPlan,
                                                   String originalQuestion,
                                                   List<String> subQuestions,
                                                   RetrievalPlan retrievalPlan) {
        if (retrievalPlan.retrievalMode() == RetrievalPlan.RetrievalMode.MIXED) {
            return RequestPlan.QueryType.MIXED;
        }
        if (retrievalPlan.retrievalMode() == RetrievalPlan.RetrievalMode.MCP_ONLY) {
            return RequestPlan.QueryType.TOOL_FIRST;
        }
        if (CollUtil.size(subQuestions) > COMPLEX_SUBQUESTION_THRESHOLD) {
            return RequestPlan.QueryType.MULTI_HOP;
        }
        if (retrievalPlan.retrievalMode() == RetrievalPlan.RetrievalMode.KB_ONLY
                && CollUtil.size(subQuestions) <= 1
                && initialPlan.rewriteMode() == RequestPlan.RewriteMode.NORMALIZE_ONLY
                && StrUtil.length(StrUtil.blankToDefault(originalQuestion, "")) <= SIMPLE_QUERY_LENGTH_THRESHOLD) {
            return RequestPlan.QueryType.SIMPLE_KB;
        }
        return RequestPlan.QueryType.STANDARD;
    }

    private boolean shouldUseDeepThinking(String originalQuestion,
                                          List<String> subQuestions,
                                          RetrievalPlan retrievalPlan,
                                          RequestPlan.QueryType queryType,
                                          boolean userRequestedDeepThinking) {
        if (userRequestedDeepThinking) {
            return true;
        }
        if (queryType == RequestPlan.QueryType.MIXED
                || queryType == RequestPlan.QueryType.TOOL_FIRST
                || queryType == RequestPlan.QueryType.MULTI_HOP) {
            return true;
        }
        if (CollUtil.size(subQuestions) > COMPLEX_SUBQUESTION_THRESHOLD) {
            return true;
        }
        return StrUtil.length(StrUtil.blankToDefault(originalQuestion, "")) >= COMPLEX_QUERY_LENGTH_THRESHOLD
                && retrievalPlan.requiresRetrieval();
    }

    private List<String> buildReasons(RequestPlan.QueryType queryType,
                                      RetrievalPlan retrievalPlan,
                                      RequestPlan.ThinkingMode thinkingMode,
                                      RequestPlan.ResponseMode responseMode,
                                      boolean userRequestedDeepThinking,
                                      List<SubQuestionIntent> subIntents) {
        List<String> reasons = new ArrayList<>();
        switch (queryType) {
            case SIMPLE_KB -> reasons.add("single-question KB lookup keeps the route on the simple retrieval path");
            case MIXED -> reasons.add("request mixes KB and MCP signals, so the backend uses a mixed route");
            case TOOL_FIRST -> reasons.add("resolved intents are tool-oriented, so the route prefers MCP retrieval");
            case MULTI_HOP -> reasons.add("request fans out into multiple sub-questions, so the backend treats it as a multi-hop route");
            default -> reasons.add("request stays on the standard backend route");
        }
        reasons.add(retrievalPlan.requiresRetrieval()
                ? "retrieval mode is derived from resolved intents instead of frontend hints"
                : "retrieval is skipped because the backend decided no external context is needed");
        reasons.add(thinkingMode == RequestPlan.ThinkingMode.DEEP
                ? (userRequestedDeepThinking
                ? "deep thinking is enabled because the user requested it"
                : "deep thinking is cautiously enabled because backend complexity signals crossed the threshold")
                : "thinking stays standard to preserve the low-cost path");
        reasons.add(responseMode == RequestPlan.ResponseMode.EMPTY_HIT
                ? "response mode switches to empty-hit because retrieval returned no context"
                : "response mode remains answer-oriented");
        reasons.add("sub-question count observed by backend: " + CollUtil.size(subIntents));
        return reasons;
    }

    private boolean isSimpleQuestion(String question) {
        String normalized = StrUtil.blankToDefault(question, "").trim();
        if (normalized.isEmpty()) {
            return true;
        }
        if (normalized.length() > SIMPLE_QUERY_LENGTH_THRESHOLD) {
            return false;
        }
        return !normalized.contains("？")
                && !normalized.contains("?")
                && !normalized.contains("；")
                && !normalized.contains(";")
                && !normalized.contains("\n")
                && !normalized.contains("以及")
                && !normalized.contains("并且")
                && !normalized.contains(" and ");
    }
}
