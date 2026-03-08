import * as React from "react";
import { Brain, ChevronDown, GitBranch, Waypoints } from "lucide-react";

import { FeedbackButtons } from "@/components/chat/FeedbackButtons";
import { MarkdownRenderer } from "@/components/chat/MarkdownRenderer";
import { ThinkingIndicator } from "@/components/chat/ThinkingIndicator";
import { cn } from "@/lib/utils";
import type { Message } from "@/types";

interface MessageItemProps {
  message: Message;
  isLast?: boolean;
}

export const MessageItem = React.memo(function MessageItem({ message, isLast }: MessageItemProps) {
  const isUser = message.role === "user";
  const showFeedback =
    message.role === "assistant" &&
    message.status !== "streaming" &&
    message.id &&
    !message.id.startsWith("assistant-");
  const isThinking = Boolean(message.isThinking);
  const [thinkingExpanded, setThinkingExpanded] = React.useState(false);
  const [traceExpanded, setTraceExpanded] = React.useState(false);
  const hasThinking = Boolean(message.thinking && message.thinking.trim().length > 0);
  const hasTrace = Boolean(
    message.trace?.route ||
      message.trace?.summary ||
      message.trace?.requestPlan ||
      message.trace?.retrievalPlan ||
      message.trace?.selectedModel ||
      (message.trace?.decisions && message.trace.decisions.length > 0)
  );
  const hasContent = message.content.trim().length > 0;
  const isWaiting = message.status === "streaming" && !isThinking && !hasContent;

  if (isUser) {
    return (
      <div className="flex">
        <div className="user-message">
          <p className="whitespace-pre-wrap break-words">{message.content}</p>
        </div>
      </div>
    );
  }

  const thinkingDuration = message.thinkingDuration ? `${message.thinkingDuration}秒` : "";
  return (
    <div className="group flex">
      <div className="min-w-0 flex-1 space-y-4">
        {isThinking ? (
          <ThinkingIndicator content={message.thinking} duration={message.thinkingDuration} />
        ) : null}
        {!isThinking && hasThinking ? (
          <div className="overflow-hidden rounded-lg border border-[#BFDBFE] bg-[#DBEAFE]">
            <button
              type="button"
              onClick={() => setThinkingExpanded((prev) => !prev)}
              className="flex w-full items-center gap-2 px-4 py-3 text-left transition-colors hover:bg-[#BFDBFE]/30"
            >
              <div className="flex flex-1 items-center gap-2">
                <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-[#BFDBFE]">
                  <Brain className="h-4 w-4 text-[#2563EB]" />
                </div>
                <span className="text-sm font-medium text-[#2563EB]">深度思考</span>
                {thinkingDuration ? (
                  <span className="rounded-full bg-[#BFDBFE] px-2 py-0.5 text-xs text-[#2563EB]">
                    {thinkingDuration}
                  </span>
                ) : null}
              </div>
              <ChevronDown
                className={cn(
                  "h-4 w-4 text-[#3B82F6] transition-transform",
                  thinkingExpanded && "rotate-180"
                )}
              />
            </button>
            {thinkingExpanded ? (
              <div className="border-t border-[#BFDBFE] px-4 pb-4">
                <div className="mt-3 whitespace-pre-wrap text-sm leading-relaxed text-[#1E40AF]">
                  {message.thinking}
                </div>
              </div>
            ) : null}
          </div>
        ) : null}
        {hasTrace ? (
          <div className="overflow-hidden rounded-lg border border-slate-200 bg-slate-50">
            <button
              type="button"
              onClick={() => setTraceExpanded((prev) => !prev)}
              className="flex w-full items-center gap-2 px-4 py-3 text-left transition-colors hover:bg-slate-100"
            >
              <div className="flex flex-1 items-center gap-2">
                <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-white">
                  <Waypoints className="h-4 w-4 text-slate-700" />
                </div>
                <span className="text-sm font-medium text-slate-800">Decision Trace</span>
                {message.trace?.route ? (
                  <span className="rounded-full bg-white px-2 py-0.5 text-xs text-slate-600">
                    {message.trace.route}
                  </span>
                ) : null}
                {message.trace?.selectedModel ? (
                  <span className="rounded-full bg-white px-2 py-0.5 text-xs text-slate-600">
                    {message.trace.selectedModel}
                  </span>
                ) : null}
              </div>
              <ChevronDown
                className={cn(
                  "h-4 w-4 text-slate-500 transition-transform",
                  traceExpanded && "rotate-180"
                )}
              />
            </button>
            {traceExpanded ? (
              <div className="space-y-3 border-t border-slate-200 px-4 py-4 text-sm text-slate-700">
                {message.trace?.summary ? <p>{message.trace.summary}</p> : null}
                {(message.trace?.thinkingMode || message.trace?.modelMode || message.trace?.selectedModel) ? (
                  <div className="flex flex-wrap gap-2 text-xs">
                    {message.trace?.thinkingMode ? (
                      <span className="rounded-full bg-white px-2.5 py-1 text-slate-600">
                        thinking: {message.trace.thinkingMode}
                      </span>
                    ) : null}
                    {message.trace?.modelMode ? (
                      <span className="rounded-full bg-white px-2.5 py-1 text-slate-600">
                        model: {message.trace.modelMode}
                      </span>
                    ) : null}
                    {message.trace?.selectedModel ? (
                      <span className="rounded-full bg-white px-2.5 py-1 text-slate-600">
                        selected: {message.trace.selectedModel}
                      </span>
                    ) : null}
                  </div>
                ) : null}
                {message.trace?.requestPlan ? (
                  <div className="rounded-lg bg-white p-3">
                    <div className="mb-1 flex items-center gap-2 text-xs font-medium text-slate-500">
                      <GitBranch className="h-3.5 w-3.5" />
                      RequestPlan
                    </div>
                    <p className="whitespace-pre-wrap text-sm text-slate-700">{message.trace.requestPlan}</p>
                  </div>
                ) : null}
                {message.trace?.retrievalPlan ? (
                  <div className="rounded-lg bg-white p-3">
                    <div className="mb-1 flex items-center gap-2 text-xs font-medium text-slate-500">
                      <Waypoints className="h-3.5 w-3.5" />
                      RetrievalPlan
                    </div>
                    <p className="whitespace-pre-wrap text-sm text-slate-700">{message.trace.retrievalPlan}</p>
                  </div>
                ) : null}
                {message.trace?.decisions?.length ? (
                  <div className="space-y-2">
                    {message.trace.decisions.map((decision) => (
                      <div key={decision.key} className="rounded-lg bg-white p-3">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="text-xs font-medium text-slate-500">{decision.label}</span>
                          {decision.value ? (
                            <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-700">
                              {decision.value}
                            </span>
                          ) : null}
                        </div>
                        {decision.reason ? <p className="mt-1 text-sm text-slate-600">{decision.reason}</p> : null}
                      </div>
                    ))}
                  </div>
                ) : null}
              </div>
            ) : null}
          </div>
        ) : null}
        <div className="space-y-2">
          {isWaiting ? (
            <div className="ai-wait" aria-label="思考中">
              <span className="ai-wait-dots" aria-hidden="true">
                <span className="ai-wait-dot" />
                <span className="ai-wait-dot" />
                <span className="ai-wait-dot" />
              </span>
            </div>
          ) : null}
          {hasContent ? <MarkdownRenderer content={message.content} /> : null}
          {message.status === "error" ? (
            <p className="text-xs text-rose-500">生成已中断。</p>
          ) : null}
          {showFeedback ? (
            <FeedbackButtons
              messageId={message.id}
              feedback={message.feedback ?? null}
              content={message.content}
              alwaysVisible={Boolean(isLast)}
            />
          ) : null}
        </div>
      </div>
    </div>
  );
});
