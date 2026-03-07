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

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeBaseDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.rag.config.SearchChannelProperties;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieverService;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.strategy.CollectionParallelRetriever;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * 向量全局检索通道
 * <p>
 * 在所有知识库中进行向量检索，作为兜底策略
 * 当意图识别失败或置信度低时启用
 */
@Slf4j
@Component
public class VectorGlobalSearchChannel implements SearchChannel {

    private final SearchChannelProperties properties;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final CollectionParallelRetriever parallelRetriever;

    public VectorGlobalSearchChannel(RetrieverService retrieverService,
                                     SearchChannelProperties properties,
                                     KnowledgeBaseMapper knowledgeBaseMapper,
                                     @Qualifier("ragInnerRetrievalThreadPoolExecutor") Executor innerRetrievalExecutor) {
        this.properties = properties;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.parallelRetriever = new CollectionParallelRetriever(retrieverService, innerRetrievalExecutor);
    }

    @Override
    public String getName() {
        return "VectorGlobalSearch";
    }

    @Override
    public int getPriority() {
        return 10;  // 较低优先级
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        // 检查配置是否启用
        if (!properties.getChannels().getVectorGlobal().isEnabled()) {
            return false;
        }

        // 条件1：没有识别出任何意图
        List<NodeScore> allScores = context.getIntents().stream()
                .flatMap(si -> si.nodeScores().stream())
                .toList();
        if (CollUtil.isEmpty(allScores)) {
            log.info("未识别出任何意图，启用全局检索");
            return true;
        }

        // 条件2：意图置信度都很低
        double maxScore = allScores.stream()
                .mapToDouble(NodeScore::getScore)
                .max()
                .orElse(0.0);

        double threshold = properties.getChannels().getVectorGlobal().getConfidenceThreshold();
        if (maxScore < threshold) {
            log.info("意图置信度过低（{}），启用全局检索", maxScore);
            return true;
        }

        return false;
    }

    @Override
    public SearchChannelResult search(SearchContext context) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("执行向量全局检索，问题：{}", context.getMainQuestion());

            // 获取受限的全局兜底 collection 列表
            List<String> collections = resolveFallbackCollections(context);

            if (collections.isEmpty()) {
                log.warn("未找到任何 KB collection，跳过全局检索");
                return SearchChannelResult.builder()
                        .channelType(SearchChannelType.VECTOR_GLOBAL)
                        .channelName(getName())
                        .chunks(List.of())
                        .confidence(0.0)
                        .latencyMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            // 并行在所有 collection 中检索
            int topKMultiplier = properties.getChannels().getVectorGlobal().getTopKMultiplier();
            List<RetrievedChunk> allChunks = retrieveFromAllCollections(
                    context.getMainQuestion(),
                    context.getQueryVector(),
                    collections,
                    context.getTopK() * topKMultiplier
            );

            long latency = System.currentTimeMillis() - startTime;

            log.info("向量全局检索完成，检索到 {} 个 Chunk，耗时 {}ms", allChunks.size(), latency);

            return SearchChannelResult.builder()
                    .channelType(SearchChannelType.VECTOR_GLOBAL)
                    .channelName(getName())
                    .chunks(allChunks)
                    .confidence(0.7)  // 全局检索置信度中等
                    .latencyMs(latency)
                    .build();

        } catch (Exception e) {
            log.error("向量全局检索失败", e);
            return SearchChannelResult.builder()
                    .channelType(SearchChannelType.VECTOR_GLOBAL)
                    .channelName(getName())
                    .chunks(List.of())
                    .confidence(0.0)
                    .latencyMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    /**
     * 解析全局兜底 collection 列表
     */
    private List<String> resolveFallbackCollections(SearchContext context) {
        SearchChannelProperties.VectorGlobal vectorGlobal = properties.getChannels().getVectorGlobal();
        int maxCollections = Math.max(1, vectorGlobal.getMaxCollections());

        LinkedHashSet<String> prioritizedCollections = new LinkedHashSet<>();
        List<String> intentCollections = vectorGlobal.isPreferIntentCollections()
                ? extractIntentCollections(context).stream().limit(maxCollections).toList()
                : List.of();

        List<String> availableCollections = getAllKBCollections();
        Set<String> availableSet = new LinkedHashSet<>(availableCollections);

        for (String intentCollection : intentCollections) {
            if (availableSet.contains(intentCollection)) {
                prioritizedCollections.add(intentCollection);
            }
        }

        if (prioritizedCollections.size() < maxCollections) {
            availableCollections.stream()
                    .filter(Objects::nonNull)
                    .filter(collection -> !collection.isBlank())
                    .filter(collection -> !prioritizedCollections.contains(collection))
                    .forEach(prioritizedCollections::add);
        }

        List<String> boundedCollections = prioritizedCollections.stream()
                .limit(maxCollections)
                .toList();
        log.info("全局检索 fan-out 已限制为 {}/{} 个 collection: {}",
                boundedCollections.size(), maxCollections, boundedCollections);
        return boundedCollections;
    }

    /**
     * 获取所有 KB 类型的 collection
     */
    private List<String> getAllKBCollections() {
        Set<String> collections = new LinkedHashSet<>();

        // 从知识库表获取全量 collection（全局检索兜底）
        List<KnowledgeBaseDO> kbList = knowledgeBaseMapper.selectList(
                Wrappers.query(new KnowledgeBaseDO())
                        .select("collection_name")
                        .eq("deleted", 0)
        );
        for (KnowledgeBaseDO kb : kbList) {
            String collectionName = kb.getCollectionName();
            if (collectionName != null && !collectionName.isBlank()) {
                collections.add(collectionName);
            }
        }

        return new ArrayList<>(collections);
    }

    private List<String> extractIntentCollections(SearchContext context) {
        if (context == null || CollUtil.isEmpty(context.getIntents())) {
            return List.of();
        }

        return context.getIntents().stream()
                .flatMap(intent -> intent.nodeScores().stream())
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(NodeScore::getScore).reversed())
                .map(NodeScore::getNode)
                .filter(Objects::nonNull)
                .filter(node -> node.isKB())
                .map(node -> node.getCollectionName())
                .filter(Objects::nonNull)
                .filter(collection -> !collection.isBlank())
                .distinct()
                .toList();
    }

    /**
     * 并行在所有 collection 中检索
     */
    private List<RetrievedChunk> retrieveFromAllCollections(String question,
                                                            float[] queryVector,
                                                            List<String> collections,
                                                            int topK) {
        // 使用模板方法执行并行检索
        return parallelRetriever.executeParallelRetrieval(question, collections, topK, queryVector);
    }

    @Override
    public SearchChannelType getType() {
        return SearchChannelType.VECTOR_GLOBAL;
    }
}
