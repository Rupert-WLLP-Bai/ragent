import { format } from "date-fns";

export interface AdaptiveThinkingDecision {
  shouldUseDeepThinking: boolean;
  mode: "manual" | "adaptive" | "off";
  reason: "user_enabled" | "complex_prompt" | "simple_prompt" | "empty_prompt";
}

const COMPLEX_PROMPT_PATTERNS = [
  /\b(debug|diagnose|investigate|analy[sz]e|compare|design|plan|architect(?:ure)?|refactor|optimi[sz]e|trade[-\s]?off|root cause)\b/i,
  /(调试|排查|分析|对比|比较|设计|规划|架构|重构|优化|根因|取舍)/,
  /\b(how|why|when)\b/i
];

const CODE_PROMPT_PATTERNS = [
  /```/,
  /\b(function|class|interface|type|const|let|var|return|async|await|select|insert|update|delete|from|where|join|python|java|typescript|javascript|tsx|jsx|sql)\b/i,
  /=>|[{}`<>]/
];

export function formatTimestamp(value?: string) {
  if (!value) return "";
  try {
    return format(new Date(value), "MM月dd日 HH:mm");
  } catch {
    return "";
  }
}

export function truncate(text: string, max = 36) {
  if (!text) return "";
  if (text.length <= max) return text;
  return `${text.slice(0, max)}...`;
}

export function buildQuery(params: Record<string, string | number | boolean | undefined | null>) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") return;
    search.set(key, String(value));
  });
  const query = search.toString();
  return query ? `?${query}` : "";
}

export function resolveAdaptiveThinkingDecision({
  question,
  manualEnabled
}: {
  question: string;
  manualEnabled: boolean;
}): AdaptiveThinkingDecision {
  if (manualEnabled) {
    return {
      shouldUseDeepThinking: true,
      mode: "manual",
      reason: "user_enabled"
    };
  }

  const trimmed = question.trim();
  if (!trimmed) {
    return {
      shouldUseDeepThinking: false,
      mode: "off",
      reason: "empty_prompt"
    };
  }

  let score = 0;

  if (trimmed.length >= 120) score += 1;
  if (trimmed.length >= 220) score += 1;
  if (trimmed.split(/\r?\n/).length >= 3) score += 1;
  if (/[1-9][.)]|[-*•]\s/.test(trimmed)) score += 1;
  if ((trimmed.match(/[?？]/g) || []).length >= 2) score += 1;
  if (COMPLEX_PROMPT_PATTERNS.some((pattern) => pattern.test(trimmed))) score += 2;
  if (CODE_PROMPT_PATTERNS.some((pattern) => pattern.test(trimmed))) score += 2;

  if (score >= 3) {
    return {
      shouldUseDeepThinking: true,
      mode: "adaptive",
      reason: "complex_prompt"
    };
  }

  return {
    shouldUseDeepThinking: false,
    mode: "off",
    reason: "simple_prompt"
  };
}
