import { describe, expect, it } from "vitest";

import { buildQuery, formatTimestamp, resolveAdaptiveThinkingDecision, truncate } from "@/utils/helpers";

describe("helpers", () => {
  it("builds a query string and skips empty values", () => {
    expect(
      buildQuery({
        question: "hello",
        conversationId: "",
        deepThinking: true,
        page: 2,
        ignored: undefined
      })
    ).toBe("?question=hello&deepThinking=true&page=2");
  });

  it("formats timestamps and falls back for empty input", () => {
    expect(formatTimestamp("2026-03-06T12:34:00.000Z")).toMatch(/03月06日/);
    expect(formatTimestamp()).toBe("");
  });

  it("truncates long text", () => {
    expect(truncate("abcdefghijklmnopqrstuvwxyz", 10)).toBe("abcdefghij...");
    expect(truncate("short", 10)).toBe("short");
  });

  it("enables adaptive thinking for complex prompts", () => {
    expect(
      resolveAdaptiveThinkingDecision({
        question: "请帮我分析这个 TypeScript 错误的根因，并给出分步骤修复方案。```ts\nconst run = async () => {}\n```",
        manualEnabled: false
      })
    ).toMatchObject({
      shouldUseDeepThinking: true,
      mode: "adaptive",
      reason: "complex_prompt"
    });
  });

  it("keeps simple prompts on the low-cost path", () => {
    expect(resolveAdaptiveThinkingDecision({ question: "你好", manualEnabled: false })).toMatchObject({
      shouldUseDeepThinking: false,
      mode: "off",
      reason: "simple_prompt"
    });
  });

  it("preserves manual deep thinking overrides", () => {
    expect(resolveAdaptiveThinkingDecision({ question: "随便问问", manualEnabled: true })).toMatchObject({
      shouldUseDeepThinking: true,
      mode: "manual",
      reason: "user_enabled"
    });
  });
});
