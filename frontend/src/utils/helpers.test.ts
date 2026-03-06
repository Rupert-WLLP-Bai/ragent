import { describe, expect, it } from "vitest";

import { buildQuery, formatTimestamp, truncate } from "@/utils/helpers";

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
});
