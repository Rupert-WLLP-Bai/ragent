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

package com.nageoffer.ai.ragent.rag.core.mcp;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.infra.util.LLMResponseCleaner;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptTemplateLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.nageoffer.ai.ragent.rag.constant.RAGConstant.MCP_PARAMETER_EXTRACT_PROMPT_PATH;

/**
 * 基于 LLM 的 MCP 参数提取器实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMMCPParameterExtractor implements MCPParameterExtractor {

    private final LLMService llmService;
    private final PromptTemplateLoader promptTemplateLoader;
    private final Gson gson = new Gson();

    @Override
    public Map<String, Object> extractParameters(String userQuestion, MCPTool tool) {
        return extractParameters(userQuestion, tool, null);
    }

    @Override
    public Map<String, Object> extractParameters(String userQuestion, MCPTool tool, String customPromptTemplate) {
        if (tool == null || CollUtil.isEmpty(tool.getParameters())) {
            return Collections.emptyMap();
        }

        Map<String, Object> fastPathResult = tryFastPath(userQuestion, tool);
        if (fastPathResult != null) {
            log.info("MCP 参数提取快速路径命中, toolId: {}, 参数: {}", tool.getToolId(), fastPathResult);
            return fastPathResult;
        }

        List<ChatMessage> messages = new ArrayList<>(3);
        String systemPrompt = StrUtil.isNotBlank(customPromptTemplate)
                ? customPromptTemplate
                : promptTemplateLoader.load(MCP_PARAMETER_EXTRACT_PROMPT_PATH);

        messages.add(ChatMessage.system(systemPrompt));
        messages.add(ChatMessage.user("工具定义如下：\n" + buildToolDefinition(tool)));
        messages.add(ChatMessage.user("请根据以上工具定义，从下面的问题中提取参数：\n" + userQuestion));

        String raw = null;
        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(messages)
                    .temperature(0.1D)
                    .topP(0.3D)
                    .thinking(false)
                    .build();
            raw = llmService.chat(request);
            log.info("MCP 参数提取 LLM 响应: {}", raw);

            Map<String, Object> extracted = parseJsonResponse(raw, tool);
            fillDefaults(extracted, tool);

            log.info("MCP 参数提取完成, toolId: {}, 使用自定义提示词: {}, 参数: {}",
                    tool.getToolId(), StrUtil.isNotBlank(customPromptTemplate), extracted);

            return extracted;
        } catch (JsonSyntaxException e) {
            log.warn("MCP 参数提取-JSON解析失败, toolId: {}, 响应: {}", tool.getToolId(), raw, e);
            return buildDefaultParameters(tool);
        } catch (Exception e) {
            log.error("MCP 参数提取异常, toolId: {}", tool.getToolId(), e);
            return buildDefaultParameters(tool);
        }
    }

    private Map<String, Object> buildDefaultParameters(MCPTool tool) {
        Map<String, Object> defaultParams = new HashMap<>();
        fillDefaults(defaultParams, tool);
        return defaultParams;
    }

    private Map<String, Object> tryFastPath(String userQuestion, MCPTool tool) {
        if (!supportsFastPath(tool)) {
            return null;
        }
        String normalizedQuestion = StrUtil.trimToEmpty(userQuestion);
        Map<String, Object> extracted = new LinkedHashMap<>();

        for (Map.Entry<String, MCPTool.ParameterDef> entry : tool.getParameters().entrySet()) {
            String paramName = entry.getKey();
            MCPTool.ParameterDef def = entry.getValue();
            Object value = extractSimpleParameter(normalizedQuestion, tool, paramName, def);
            if (value != null) {
                extracted.put(paramName, value);
            }
        }

        fillDefaults(extracted, tool);
        if (!hasRequiredParameters(extracted, tool)) {
            return null;
        }
        return extracted;
    }

    private boolean supportsFastPath(MCPTool tool) {
        if (tool == null || CollUtil.isEmpty(tool.getParameters()) || tool.getParameters().size() > 3) {
            return false;
        }
        if (countNumericParameters(tool) > 1) {
            return false;
        }
        return tool.getParameters().values().stream().allMatch(this::isSimpleParameterType);
    }

    private long countNumericParameters(MCPTool tool) {
        return tool.getParameters().values().stream()
                .filter(def -> def != null && isNumericType(def.getType()))
                .count();
    }

    private boolean isSimpleParameterType(MCPTool.ParameterDef def) {
        if (def == null) {
            return false;
        }
        String type = normalizeType(def.getType());
        return "string".equals(type) || "number".equals(type) || "integer".equals(type) || "boolean".equals(type);
    }

    private boolean isNumericType(String type) {
        String normalizedType = normalizeType(type);
        return "number".equals(normalizedType) || "integer".equals(normalizedType);
    }

    private Object extractSimpleParameter(String userQuestion, MCPTool tool, String paramName, MCPTool.ParameterDef def) {
        if (StrUtil.isBlank(userQuestion) || def == null) {
            return null;
        }
        String type = normalizeType(def.getType());
        if (CollUtil.isNotEmpty(def.getEnumValues())) {
            Object enumValue = extractEnumValue(userQuestion, def);
            if (enumValue != null) {
                return enumValue;
            }
        }
        return switch (type) {
            case "boolean" -> extractBooleanValue(userQuestion, paramName, def);
            case "number", "integer" -> extractNumericValue(userQuestion, paramName, type);
            case "string" -> extractStringValue(userQuestion, tool, paramName, def);
            default -> null;
        };
    }

    private Object extractEnumValue(String userQuestion, MCPTool.ParameterDef def) {
        String normalizedQuestion = userQuestion.toLowerCase(Locale.ROOT);
        for (String enumValue : def.getEnumValues()) {
            if (StrUtil.isBlank(enumValue)) {
                continue;
            }
            if (normalizedQuestion.contains(enumValue.toLowerCase(Locale.ROOT))) {
                return enumValue;
            }
        }
        return null;
    }

    private Object extractBooleanValue(String userQuestion, String paramName, MCPTool.ParameterDef def) {
        String normalizedQuestion = userQuestion.toLowerCase(Locale.ROOT);
        String normalizedName = paramName.toLowerCase(Locale.ROOT);
        if (normalizedQuestion.contains(normalizedName)) {
            if (containsAny(normalizedQuestion, "true", "是", "开启", "需要", "打开", "启用")) {
                return true;
            }
            if (containsAny(normalizedQuestion, "false", "否", "关闭", "不要", "禁用")) {
                return false;
            }
        }
        if (containsAny(normalizedQuestion, "true", "false")) {
            return normalizedQuestion.contains("true");
        }
        if (CollUtil.isNotEmpty(def.getEnumValues())) {
            return null;
        }
        return null;
    }

    private Object extractNumericValue(String userQuestion, String paramName, String type) {
        Pattern namedNumberPattern = Pattern.compile(Pattern.quote(paramName) + "\\s*[:=：]?\\s*(-?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
        Matcher namedMatcher = namedNumberPattern.matcher(userQuestion);
        if (namedMatcher.find()) {
            return castNumericValue(namedMatcher.group(1), type);
        }
        Pattern bareNumberPattern = Pattern.compile("(-?\\d+(?:\\.\\d+)?)");
        Matcher bareMatcher = bareNumberPattern.matcher(userQuestion);
        if (bareMatcher.find()) {
            return castNumericValue(bareMatcher.group(1), type);
        }
        return null;
    }

    private Object castNumericValue(String rawNumber, String type) {
        if (!NumberUtil.isNumber(rawNumber)) {
            return null;
        }
        double value = NumberUtil.parseDouble(rawNumber);
        if ("integer".equals(type)) {
            return (int) value;
        }
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                return (int) value;
            }
            if (value >= Long.MIN_VALUE && value <= Long.MAX_VALUE) {
                return (long) value;
            }
        }
        return value;
    }

    private Object extractStringValue(String userQuestion, MCPTool tool, String paramName, MCPTool.ParameterDef def) {
        Pattern quotedPattern = Pattern.compile(Pattern.quote(paramName) + "\\s*[:=：]?\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher quotedMatcher = quotedPattern.matcher(userQuestion);
        if (quotedMatcher.find()) {
            return quotedMatcher.group(1).trim();
        }
        Pattern namedPattern = Pattern.compile(Pattern.quote(paramName) + "\\s*[:=：]?\\s*([^,，。;；]+)", Pattern.CASE_INSENSITIVE);
        Matcher namedMatcher = namedPattern.matcher(userQuestion);
        if (namedMatcher.find()) {
            String candidate = trimTrailingParameterTokens(tool, paramName, namedMatcher.group(1).trim());
            if (StrUtil.isNotBlank(candidate)) {
                return candidate;
            }
        }
        if (hasSingleRequiredStringParameter(tool, paramName, def)) {
            return StrUtil.blankToDefault(userQuestion.trim(), null);
        }
        return null;
    }

    private String trimTrailingParameterTokens(MCPTool tool, String currentParamName, String candidate) {
        if (tool == null || tool.getParameters() == null || tool.getParameters().size() <= 1) {
            return candidate;
        }
        String trimmed = candidate;
        for (String paramName : tool.getParameters().keySet()) {
            if (StrUtil.equalsIgnoreCase(paramName, currentParamName)) {
                continue;
            }
            Pattern nextParamPattern = Pattern.compile("(?i)\\s+" + Pattern.quote(paramName) + "\\s*[:=：]?.*$");
            Matcher matcher = nextParamPattern.matcher(trimmed);
            if (matcher.find()) {
                trimmed = trimmed.substring(0, matcher.start()).trim();
            }
        }
        return trimmed;
    }

    private boolean hasSingleRequiredStringParameter(MCPTool tool, String paramName, MCPTool.ParameterDef def) {
        return tool != null
                && tool.getParameters() != null
                && tool.getParameters().size() == 1
                && tool.getParameters().containsKey(paramName)
                && def.isRequired()
                && "string".equals(normalizeType(def.getType()));
    }

    private boolean hasRequiredParameters(Map<String, Object> extracted, MCPTool tool) {
        for (Map.Entry<String, MCPTool.ParameterDef> entry : tool.getParameters().entrySet()) {
            if (entry.getValue() != null && entry.getValue().isRequired() && !extracted.containsKey(entry.getKey())) {
                return false;
            }
        }
        return true;
    }

    private boolean containsAny(String source, String... candidates) {
        for (String candidate : candidates) {
            if (source.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeType(String type) {
        return StrUtil.blankToDefault(type, "string").trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 构建工具定义描述（供 LLM 理解）
     */
    private String buildToolDefinition(MCPTool tool) {
        StringBuilder sb = new StringBuilder();
        sb.append("工具ID: ").append(tool.getToolId()).append("\n");
        sb.append("功能描述: ").append(tool.getDescription()).append("\n");
        sb.append("参数列表:\n");

        for (Map.Entry<String, MCPTool.ParameterDef> entry : tool.getParameters().entrySet()) {
            String paramName = entry.getKey();
            MCPTool.ParameterDef def = entry.getValue();

            sb.append("  - ").append(paramName);
            sb.append(" (类型: ").append(def.getType());
            sb.append(def.isRequired() ? ", 必填" : ", 可选");
            sb.append("): ").append(def.getDescription());

            if (def.getDefaultValue() != null) {
                sb.append(" [默认值: ").append(def.getDefaultValue()).append("]");
            }
            if (CollUtil.isNotEmpty(def.getEnumValues())) {
                sb.append(" [可选值: ").append(String.join(", ", def.getEnumValues())).append("]");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 解析 LLM 返回的 JSON 响应
     */
    private Map<String, Object> parseJsonResponse(String raw, MCPTool tool) {
        if (StrUtil.isBlank(raw)) {
            return new HashMap<>();
        }
        String cleaned = LLMResponseCleaner.stripMarkdownCodeFence(raw);
        JsonElement element = JsonParser.parseString(cleaned);
        if (!element.isJsonObject()) {
            log.warn("LLM 返回的不是 JSON 对象: {}", raw);
            return new HashMap<>();
        }
        JsonObject obj = element.getAsJsonObject();
        Map<String, Object> result = new HashMap<>();
        for (String paramName : tool.getParameters().keySet()) {
            if (obj.has(paramName) && !obj.get(paramName).isJsonNull()) {
                JsonElement value = obj.get(paramName);
                result.put(paramName, convertJsonElement(value));
            }
        }
        return result;
    }

    /**
     * 转换 JsonElement 为普通 Java 对象
     */
    private Object convertJsonElement(JsonElement element) {
        if (element.isJsonPrimitive()) {
            var primitive = element.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                double d = primitive.getAsDouble();

                if (Double.isNaN(d)) {
                    return null;
                }

                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    if (d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                        return (int) d;
                    } else if (d >= Long.MIN_VALUE && d <= Long.MAX_VALUE) {
                        return (long) d;
                    }
                }
                return d;
            } else if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            } else {
                return primitive.getAsString();
            }
        } else if (element.isJsonArray()) {
            return gson.fromJson(element, List.class);
        } else if (element.isJsonObject()) {
            return gson.fromJson(element, LinkedHashMap.class);
        }
        return null;
    }

    /**
     * 填充默认值
     */
    private void fillDefaults(Map<String, Object> params, MCPTool tool) {
        if (tool.getParameters() == null) {
            return;
        }

        for (Map.Entry<String, MCPTool.ParameterDef> entry : tool.getParameters().entrySet()) {
            String paramName = entry.getKey();
            MCPTool.ParameterDef def = entry.getValue();

            if (!params.containsKey(paramName) && def.getDefaultValue() != null) {
                params.put(paramName, def.getDefaultValue());
            }
        }
    }
}
