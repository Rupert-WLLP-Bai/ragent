import { beforeEach, describe, expect, it, vi } from "vitest";

import { createStreamResponse } from "@/hooks/useStreamResponse";

function createSseResponse(body: string) {
  return new Response(body, {
    status: 200,
    headers: {
      "Content-Type": "text/event-stream"
    }
  });
}

describe("createStreamResponse", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("routes SSE events to the matching handlers", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      createSseResponse([
        'event: meta\ndata: {"conversationId":"c1","taskId":"t1"}\n\n',
        'event: message\ndata: {"type":"think","delta":"分析中"}\n\n',
        'event: message\ndata: {"type":"response","delta":"答案"}\n\n',
        'event: title\ndata: {"title":"新的标题"}\n\n',
        'event: finish\ndata: {"messageId":"m1","title":"完成标题"}\n\n',
        "event: done\ndata: [DONE]\n\n"
      ].join(""))
    );

    const onMeta = vi.fn();
    const onThinking = vi.fn();
    const onMessage = vi.fn();
    const onTitle = vi.fn();
    const onFinish = vi.fn();
    const onDone = vi.fn();

    const stream = createStreamResponse({ url: "http://example.test/sse", retryCount: 0 }, {
      onMeta,
      onThinking,
      onMessage,
      onTitle,
      onFinish,
      onDone
    });

    await stream.start();

    expect(fetchMock).toHaveBeenCalledWith(
      "http://example.test/sse",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({ Accept: "text/event-stream" })
      })
    );
    expect(onMeta).toHaveBeenCalledWith({ conversationId: "c1", taskId: "t1" });
    expect(onThinking).toHaveBeenCalledWith({ type: "think", delta: "分析中" });
    expect(onMessage).toHaveBeenCalledTimes(2);
    expect(onTitle).toHaveBeenCalledWith({ title: "新的标题" });
    expect(onFinish).toHaveBeenCalledWith({ messageId: "m1", title: "完成标题" });
    expect(onDone).toHaveBeenCalled();
  });

  it("aborts the in-flight request when cancel is called", async () => {
    let aborted = false;
    vi.spyOn(globalThis, "fetch").mockImplementation(
      (_input, init) =>
        new Promise((_resolve, reject) => {
          init?.signal?.addEventListener("abort", () => {
            aborted = true;
            reject(new DOMException("Aborted", "AbortError"));
          });
        })
    );

    const stream = createStreamResponse({ url: "http://example.test/sse", retryCount: 0 }, {});
    const startPromise = stream.start().catch((error) => error);

    stream.cancel();

    const error = await startPromise;
    expect(aborted).toBe(true);
    expect(error).toBeTruthy();
    expect((error as { name?: string }).name).toBe("AbortError");
  });
});
