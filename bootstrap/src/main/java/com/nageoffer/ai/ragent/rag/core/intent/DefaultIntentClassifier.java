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

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nageoffer.ai.ragent.infra.util.LLMResponseCleaner;
import com.nageoffer.ai.ragent.rag.dao.entity.IntentNodeDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.IntentNodeMapper;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptTemplateLoader;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.nageoffer.ai.ragent.rag.constant.RAGConstant.INTENT_CLASSIFIER_PROMPT_PATH;

/**
 * LLM 树形意图分类器（串行实现）
 * <p>
 * 将所有意图节点一次性发送给 LLM 进行识别打分，适用于意图数量较少的场景
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultIntentClassifier implements IntentClassifier, IntentNodeRegistry {

    private final LLMService llmService;
    private final IntentNodeMapper intentNodeMapper;
    private final PromptTemplateLoader promptTemplateLoader;
    private final IntentTreeCacheManager intentTreeCacheManager;
    private final AtomicReference<IntentTreeSnapshot> intentTreeSnapshotCache = new AtomicReference<>();

    @PostConstruct
    public void init() {
        // 初始化时确保Redis缓存存在
        ensureIntentTreeCached();
        log.info("意图分类器初始化完成");
    }

    /**
     * 确保Redis缓存中有意图树数据
     * 如果缓存不存在，从数据库加载并保存到Redis
     */
    private void ensureIntentTreeCached() {
        if (!intentTreeCacheManager.isCacheExists()) {
            List<IntentNode> roots = loadIntentTreeFromDB();
            if (!roots.isEmpty()) {
                intentTreeCacheManager.saveIntentTreeToCache(roots);
                log.info("意图树已从数据库加载并缓存到Redis");
            }
        }
    }

    /**
     * 从Redis加载意图树并构建可复用快照
     * 每次调用仍会读取最新树数据，但会复用未变化树的派生结构，减少 flatten 与 prompt 重建成本
     */
    private IntentTreeData loadIntentTreeData() {
        List<IntentNode> roots = loadIntentTreeRoots();
        if (CollUtil.isEmpty(roots)) {
            intentTreeSnapshotCache.set(null);
            return new IntentTreeData(List.of(), List.of(), Map.of(), new CachedValue<>(() -> ""));
        }

        String treeSignature = buildTreeSignature(roots);
        IntentTreeSnapshot cachedSnapshot = intentTreeSnapshotCache.get();
        if (cachedSnapshot != null && treeSignature.equals(cachedSnapshot.signature())) {
            return cachedSnapshot.data();
        }

        List<IntentNode> allNodes = flatten(roots);
        List<IntentNode> leafNodes = allNodes.stream()
                .filter(IntentNode::isLeaf)
                .collect(Collectors.toList());
        Map<String, IntentNode> id2Node = allNodes.stream()
                .collect(Collectors.toMap(IntentNode::getId, n -> n));

        IntentTreeData data = new IntentTreeData(allNodes, leafNodes, id2Node, new CachedValue<>(() -> buildPrompt(leafNodes)));
        intentTreeSnapshotCache.set(new IntentTreeSnapshot(treeSignature, data));
        log.debug("意图树数据加载完成, 总节点数: {}, 叶子节点数: {}", allNodes.size(), leafNodes.size());
        return data;
    }

    @Override
    public IntentNode getNodeById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        IntentTreeData data = loadIntentTreeData();
        return data.id2Node.get(id);
    }

    /**
     * 意图树数据结构（临时对象，不持久化）
     */
    private record IntentTreeData(
            List<IntentNode> allNodes,
            List<IntentNode> leafNodes,
            Map<String, IntentNode> id2Node,
            CachedValue<String> prompt
    ) {
    }

    private static final class CachedValue<T> {

        private final Supplier<T> supplier;
        private volatile T value;

        private CachedValue(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        private T get() {
            T cached = value;
            if (cached != null) {
                return cached;
            }
            synchronized (this) {
                if (value == null) {
                    value = supplier.get();
                }
                return value;
            }
        }
    }

    private record IntentTreeSnapshot(
            String signature,
            IntentTreeData data
    ) {
    }

    List<IntentNode> loadIntentTreeRoots() {
        List<IntentNode> roots = intentTreeCacheManager.getIntentTreeFromCache();
        if (CollUtil.isEmpty(roots)) {
            roots = loadIntentTreeFromDB();
            if (!roots.isEmpty()) {
                intentTreeCacheManager.saveIntentTreeToCache(roots);
            }
        }
        return roots;
    }

    private String buildTreeSignature(List<IntentNode> roots) {
        List<IntentNode> nodes = flatten(roots);
        return nodes.stream()
                .map(node -> String.join("|",
                        node.getId(),
                        node.getParentId() == null ? "" : node.getParentId(),
                        node.getName() == null ? "" : node.getName(),
                        node.getDescription() == null ? "" : node.getDescription(),
                        node.getFullPath() == null ? "" : node.getFullPath(),
                        node.getExamples() == null ? "" : String.join(",", node.getExamples()),
                        node.getKind() == null ? "" : node.getKind().name(),
                        node.getMcpToolId() == null ? "" : node.getMcpToolId()
                ))
                .collect(Collectors.joining("\n"));
    }

    private List<IntentNode> flatten(List<IntentNode> roots) {
        List<IntentNode> result = new ArrayList<>();
        Deque<IntentNode> stack = new ArrayDeque<>(roots);
        while (!stack.isEmpty()) {
            IntentNode n = stack.pop();
            result.add(n);
            if (n.getChildren() != null) {
                for (IntentNode child : n.getChildren()) {
                    stack.push(child);
                }
            }
        }
        return result;
    }

    /**
     * 对所有"叶子分类节点"做意图识别，由 LLM 输出每个分类的 score
     * - 返回结果已按 score 从高到低排序
     */
    @Override
    public List<NodeScore> classifyTargets(String question) {
        // 每次都从Redis读取最新数据
        IntentTreeData data = loadIntentTreeData();

        String systemPrompt = data.prompt().get();
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system(systemPrompt),
                        ChatMessage.user(question)
                ))
                .temperature(0.1D)
                .topP(0.3D)
                .thinking(false)
                .build();

        String raw = llmService.chat(request);

        try {
            // 移除可能的 markdown 代码块标记
            String cleanedRaw = LLMResponseCleaner.stripMarkdownCodeFence(raw);

            JsonElement root = JsonParser.parseString(cleanedRaw);

            JsonArray arr;
            if (root.isJsonArray()) {
                arr = root.getAsJsonArray();
            } else if (root.isJsonObject() && root.getAsJsonObject().has("results")) {
                // 容错：如果模型外面又包了一层 { "results": [...] }
                arr = root.getAsJsonObject().getAsJsonArray("results");
            } else {
                log.warn("LLM 返回了非预期的 JSON 格式, 原始响应: {}", raw);
                return List.of();
            }

            List<NodeScore> scores = new ArrayList<>();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();

                if (!obj.has("id") || !obj.has("score")) continue;

                String id = obj.get("id").getAsString();
                double score = obj.get("score").getAsDouble();

                IntentNode node = data.id2Node.get(id);
                if (node == null) {
                    log.warn("LLM 返回了未知的意图节点 ID: {}, 已跳过", id);
                    continue;
                }

                scores.add(new NodeScore(node, score));
            }

            // 降序排序
            scores.sort(Comparator.comparingDouble(NodeScore::getScore).reversed());

            log.info("当前问题：{}\n意图识别树如下所示：{}\n",
                    question,
                    JSONUtil.toJsonPrettyStr(
                            scores.stream().peek(each -> {
                                IntentNode node = each.getNode();
                                node.setChildren(null);
                            }).collect(Collectors.toList())
                    )
            );
            return scores;
        } catch (Exception e) {
            log.warn("解析 LLM 响应失败, 原始内容: {}", raw, e);
            return List.of();
        }
    }

    /**
     * 方便使用：
     * - 只取前 topN
     * - 过滤掉 score < minScore 的分类
     */
    @Override
    public List<NodeScore> topKAboveThreshold(String question, int topN, double minScore) {
        return classifyTargets(question).stream()
                .filter(ns -> ns.getScore() >= minScore)
                .limit(topN)
                .toList();
    }

    /**
     * 构造给 LLM 的 Prompt：
     * - 列出所有【叶子节点】的 id / 路径 / 描述 / 示例问题
     * - 要求 LLM 只在这些 id 中选择，输出 JSON 数组：[{"id": "...", "score": 0.9, "reason": "..."}]
     * - 特别强调：如果问题里只提到 "OA系统"，不要选 "保险系统" 的分类
     * - 如果存在 MCP 类型节点，使用增强版 Prompt 并添加 type/toolId 标识
     */
    private String buildPrompt(List<IntentNode> leafNodes) {
        StringBuilder sb = new StringBuilder();

        for (IntentNode node : leafNodes) {
            sb.append("- id=").append(node.getId()).append("\n");
            sb.append("  path=").append(node.getFullPath()).append("\n");
            sb.append("  description=").append(node.getDescription()).append("\n");

            // 添加节点类型标识（V3 Enterprise 支持 MCP）
            if (node.isMCP()) {
                sb.append("  type=MCP\n");
                if (node.getMcpToolId() != null) {
                    sb.append("  toolId=").append(node.getMcpToolId()).append("\n");
                }
            } else if (node.isSystem()) {
                sb.append("  type=SYSTEM\n");
            } else {
                sb.append("  type=KB\n");
            }

            if (node.getExamples() != null && !node.getExamples().isEmpty()) {
                sb.append("  examples=");
                sb.append(String.join(" / ", node.getExamples()));
                sb.append("\n");
            }
            sb.append("\n");
        }

        return promptTemplateLoader.render(
                INTENT_CLASSIFIER_PROMPT_PATH,
                Map.of("intent_list", sb.toString())
        );
    }

    private List<IntentNode> loadIntentTreeFromDB() {
        // 1. 查出所有未删除节点（扁平结构）
        List<IntentNodeDO> intentNodeDOList = intentNodeMapper.selectList(
                Wrappers.lambdaQuery(IntentNodeDO.class)
                        .eq(IntentNodeDO::getDeleted, 0)
        );

        if (intentNodeDOList.isEmpty()) {
            return List.of();
        }

        // 2. DO -> IntentNode（第一遍：先把所有节点建出来，放到 map 里）
        Map<String, IntentNode> id2Node = new HashMap<>();
        for (IntentNodeDO each : intentNodeDOList) {
            IntentNode node = BeanUtil.toBean(each, IntentNode.class);
            // 数据库中的 code 映射到 IntentNode 的 id/parentId
            node.setId(each.getIntentCode());
            node.setParentId(each.getParentCode());
            node.setMcpToolId(each.getMcpToolId());
            node.setParamPromptTemplate(each.getParamPromptTemplate());
            // 确保 children 不为 null（避免后面 add NPE）
            if (node.getChildren() == null) {
                node.setChildren(new ArrayList<>());
            }
            id2Node.put(node.getId(), node);
        }

        // 3. 第二遍：根据 parentId 组装 parent -> children
        List<IntentNode> roots = new ArrayList<>();
        for (IntentNode node : id2Node.values()) {
            String parentId = node.getParentId();
            if (parentId == null || parentId.isBlank()) {
                // 没有 parentId，当作根节点
                roots.add(node);
                continue;
            }

            IntentNode parent = id2Node.get(parentId);
            if (parent == null) {
                // 找不到父节点，兜底也当作根节点，避免节点丢失
                roots.add(node);
                continue;
            }

            // 追加到父节点的 children
            if (parent.getChildren() == null) {
                parent.setChildren(new ArrayList<>());
            }
            parent.getChildren().add(node);
        }

        // 4. 填充 fullPath（跟你原来的 fillFullPath 一样的逻辑）
        fillFullPath(roots, null);

        return roots;
    }

    /**
     * 填充 fullPath 字段，效果类似：
     * - 集团信息化
     * - 集团信息化 > 人事
     * - 业务系统 > OA系统 > 系统介绍
     */
    private void fillFullPath(List<IntentNode> nodes, IntentNode parent) {
        if (nodes == null) return;

        for (IntentNode node : nodes) {
            if (parent == null) {
                node.setFullPath(node.getName());
            } else {
                node.setFullPath(parent.getFullPath() + " > " + node.getName());
            }

            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                fillFullPath(node.getChildren(), node);
            }
        }
    }
}
