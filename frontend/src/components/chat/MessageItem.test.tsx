import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("@/components/chat/MarkdownRenderer", () => ({
  MarkdownRenderer: ({ content }: { content: string }) => <div>{content}</div>
}));

vi.mock("@/components/chat/ThinkingIndicator", () => ({
  ThinkingIndicator: ({ content }: { content?: string }) => <div>{content || "thinking"}</div>
}));

vi.mock("@/components/chat/FeedbackButtons", () => ({
  FeedbackButtons: ({ messageId }: { messageId: string }) => <div>feedback:{messageId}</div>
}));

import { MessageItem } from "@/components/chat/MessageItem";

describe("MessageItem", () => {
  it("renders user messages plainly", () => {
    render(<MessageItem message={{ id: "u1", role: "user", content: "hello" }} />);

    expect(screen.getByText("hello")).toBeInTheDocument();
    expect(screen.queryByText(/feedback:/)).toBeNull();
  });

  it("shows assistant error state and feedback for persisted messages", () => {
    render(
      <MessageItem
        isLast
        message={{ id: "msg-1", role: "assistant", content: "answer", status: "error", feedback: null }}
      />
    );

    expect(screen.getByText("answer")).toBeInTheDocument();
    expect(screen.getByText("生成已中断。")).toBeInTheDocument();
    expect(screen.getByText("feedback:msg-1")).toBeInTheDocument();
  });

  it("toggles the saved thinking panel", () => {
    render(
      <MessageItem
        message={{
          id: "msg-2",
          role: "assistant",
          content: "",
          thinking: "推理细节",
          thinkingDuration: 3,
          status: "done"
        }}
      />
    );

    const toggle = screen.getByRole("button", { name: /深度思考/ });
    fireEvent.click(toggle);

    expect(screen.getByText("推理细节")).toBeInTheDocument();
  });

  it("renders decision trace details", () => {
    render(
      <MessageItem
        message={{
          id: "msg-3",
          role: "assistant",
          content: "最终答案",
          status: "done",
          trace: {
            route: "adaptive",
            selectedModel: "gpt-lite",
            requestPlan: "先规划再回答",
            retrievalPlan: "仅检索高相关文档",
            decisions: [{ key: "thinking", label: "Thinking Gate", value: "adaptive", reason: "检测到复杂请求" }]
          }
        }}
      />
    );

    fireEvent.click(screen.getByRole("button", { name: /Decision Trace/ }));

    expect(screen.getByText("先规划再回答")).toBeInTheDocument();
    expect(screen.getByText("仅检索高相关文档")).toBeInTheDocument();
    expect(screen.getByText("检测到复杂请求")).toBeInTheDocument();
  });
});
