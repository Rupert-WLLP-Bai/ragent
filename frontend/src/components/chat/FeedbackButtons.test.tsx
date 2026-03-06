import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  submitFeedback: vi.fn(),
  successToast: vi.fn(),
  errorToast: vi.fn(),
  writeText: vi.fn()
}));

import { FeedbackButtons } from "@/components/chat/FeedbackButtons";

vi.mock("@/stores/chatStore", () => ({
  useChatStore: (selector: (state: { submitFeedback: typeof mocks.submitFeedback }) => unknown) =>
    selector({ submitFeedback: mocks.submitFeedback })
}));

vi.mock("sonner", () => ({
  toast: {
    success: mocks.successToast,
    error: mocks.errorToast
  }
}));

describe("FeedbackButtons", () => {
  beforeEach(() => {
    mocks.submitFeedback.mockReset();
    mocks.successToast.mockReset();
    mocks.errorToast.mockReset();
    mocks.writeText.mockReset();
    Object.defineProperty(window.navigator, "clipboard", {
      configurable: true,
      value: {
        writeText: mocks.writeText
      }
    });
  });

  it("copies content to the clipboard", async () => {
    mocks.writeText.mockResolvedValue(undefined);

    render(<FeedbackButtons messageId="m1" feedback={null} content="复制内容" alwaysVisible />);

    fireEvent.click(screen.getByRole("button", { name: "复制内容" }));

    expect(mocks.writeText).toHaveBeenCalledWith("复制内容");
    await waitFor(() => {
      expect(mocks.successToast).toHaveBeenCalledWith("复制成功");
    });
  });

  it("toggles like feedback", async () => {
    const user = userEvent.setup();
    mocks.submitFeedback.mockResolvedValue(undefined);

    render(<FeedbackButtons messageId="m1" feedback={null} content="内容" alwaysVisible />);

    await user.click(screen.getByRole("button", { name: "点赞" }));

    expect(mocks.submitFeedback).toHaveBeenCalledWith("m1", "like");
  });
});
