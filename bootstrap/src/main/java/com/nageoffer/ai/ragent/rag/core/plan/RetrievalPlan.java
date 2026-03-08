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
import com.nageoffer.ai.ragent.rag.dto.IntentGroup;

/**
 * 检索阶段决策对象。
 *
 * <p>第一版仅做保守分流：不改变现有语义，只显式表达本次请求是否需要 KB / MCP 检索。</p>
 */
public record RetrievalPlan(RetrievalMode retrievalMode, int topK) {

    public enum RetrievalMode {
        NONE,
        KB_ONLY,
        MCP_ONLY,
        MIXED
    }

    public static RetrievalPlan none() {
        return new RetrievalPlan(RetrievalMode.NONE, 0);
    }

    public static RetrievalPlan from(IntentGroup intentGroup, int topK) {
        boolean hasMcp = intentGroup != null && CollUtil.isNotEmpty(intentGroup.mcpIntents());
        boolean hasKb = intentGroup != null && CollUtil.isNotEmpty(intentGroup.kbIntents());
        if (hasMcp && hasKb) {
            return new RetrievalPlan(RetrievalMode.MIXED, topK);
        }
        if (hasKb) {
            return new RetrievalPlan(RetrievalMode.KB_ONLY, topK);
        }
        if (hasMcp) {
            return new RetrievalPlan(RetrievalMode.MCP_ONLY, topK);
        }
        return none();
    }

    public boolean requiresRetrieval() {
        return retrievalMode != RetrievalMode.NONE;
    }

    public boolean includesKb() {
        return retrievalMode == RetrievalMode.KB_ONLY || retrievalMode == RetrievalMode.MIXED;
    }

    public boolean includesMcp() {
        return retrievalMode == RetrievalMode.MCP_ONLY || retrievalMode == RetrievalMode.MIXED;
    }
}
