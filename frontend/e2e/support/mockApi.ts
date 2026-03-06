import { type BrowserContext, type Page } from "@playwright/test";

type ApiEnvelope<T> = {
  code: "0";
  message: string;
  data: T;
};

type Role = "admin" | "user";

type UserRecord = {
  userId: string;
  username: string;
  role: Role;
  avatar?: string;
};

type SessionRecord = {
  conversationId: string;
  title: string;
  lastTime: string;
};

type MessageRecord = {
  id: string;
  conversationId: string;
  role: "user" | "assistant";
  content: string;
  vote: number | null;
  createTime: string;
};

type MockState = {
  token: string;
  currentUser: UserRecord;
  sessions: SessionRecord[];
  messagesByConversation: Map<string, MessageRecord[]>;
  knowledgeBases: Array<{
    id: string;
    name: string;
    embeddingModel: string;
    collectionName: string;
    documentCount: number;
    createTime: string;
    updateTime: string;
  }>;
  knowledgeDocs: Array<{
    id: string;
    kbId: string;
    docName: string;
    kbName: string;
  }>;
  sampleQuestions: Array<{
    id: string;
    title: string;
    description: string;
    question: string;
  }>;
};

const API_PREFIX = "**/api/ragent/**";
const now = "2026-03-06T12:00:00.000Z";

function success<T>(data: T): ApiEnvelope<T> {
  return {
    code: "0",
    message: "success",
    data
  };
}

function createInitialState(role: Role): MockState {
  const user: UserRecord =
    role === "admin"
      ? {
          userId: "u-admin",
          username: "admin",
          role: "admin",
          avatar: ""
        }
      : {
          userId: "u-member",
          username: "demo",
          role: "user",
          avatar: ""
        };

  const sessions: SessionRecord[] = [
    {
      conversationId: "session-existing",
      title: "历史会话",
      lastTime: now
    }
  ];

  const messagesByConversation = new Map<string, MessageRecord[]>([
    [
      "session-existing",
      [
        {
          id: "existing-user-1",
          conversationId: "session-existing",
          role: "user",
          content: "历史问题",
          vote: null,
          createTime: now
        },
        {
          id: "existing-assistant-1",
          conversationId: "session-existing",
          role: "assistant",
          content: "历史回答",
          vote: 1,
          createTime: now
        }
      ]
    ]
  ]);

  return {
    token: `Bearer ${role}-token`,
    currentUser: user,
    sessions,
    messagesByConversation,
    knowledgeBases: [
      {
        id: "kb-1",
        name: "产品知识库",
        embeddingModel: "text-embedding-v3",
        collectionName: "product_kb",
        documentCount: 3,
        createTime: now,
        updateTime: now
      }
    ],
    knowledgeDocs: [
      {
        id: "doc-1",
        kbId: "kb-1",
        docName: "入门手册.pdf",
        kbName: "产品知识库"
      }
    ],
    sampleQuestions: [
      {
        id: "sq-1",
        title: "发布计划",
        description: "查看最近的交付计划",
        question: "请帮我总结最近一次版本发布计划。"
      },
      {
        id: "sq-2",
        title: "风险清单",
        description: "列出当前项目的风险项",
        question: "请列出当前项目风险与应对建议。"
      }
    ]
  };
}

function withJson(body: unknown, status = 200) {
  return {
    status,
    contentType: "application/json; charset=utf-8",
    body: JSON.stringify(body)
  };
}

function getConversationIdFromUrl(url: URL): string | null {
  const match = url.pathname.match(/\/conversations\/([^/]+)/);
  return match?.[1] ?? null;
}

function createSseResponse(conversationId: string, messageId: string) {
  return [
    `event: meta\ndata: ${JSON.stringify({ conversationId, taskId: "task-1" })}\n\n`,
    `event: message\ndata: ${JSON.stringify({ type: "response", delta: "这是一个稳定的模拟回答。" })}\n\n`,
    `event: title\ndata: ${JSON.stringify({ title: "关于发布计划的提问" })}\n\n`,
    `event: finish\ndata: ${JSON.stringify({ messageId, title: "关于发布计划的提问" })}\n\n`,
    "event: done\ndata: [DONE]\n\n"
  ].join("");
}

export async function installMockApi(context: BrowserContext, role: Role = "admin") {
  const state = createInitialState(role);

  await context.route(API_PREFIX, async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const { pathname, searchParams } = url;
    const method = request.method();

    if (pathname.endsWith("/auth/login") && method === "POST") {
      const body = JSON.parse(request.postData() || "{}");
      const username = typeof body.username === "string" && body.username.trim() ? body.username.trim() : state.currentUser.username;
      state.currentUser = { ...state.currentUser, username };
      await route.fulfill(
        withJson(
          success({
            userId: state.currentUser.userId,
            username: state.currentUser.username,
            role: state.currentUser.role,
            token: state.token,
            avatar: state.currentUser.avatar
          })
        )
      );
      return;
    }

    if (pathname.endsWith("/auth/logout") && method === "POST") {
      await route.fulfill(withJson(success(null)));
      return;
    }

    if (pathname.endsWith("/user/me") && method === "GET") {
      await route.fulfill(withJson(success(state.currentUser)));
      return;
    }

    if (pathname.endsWith("/rag/sample-questions") && method === "GET") {
      await route.fulfill(withJson(success(state.sampleQuestions)));
      return;
    }

    if (pathname.endsWith("/conversations") && method === "GET") {
      await route.fulfill(withJson(success(state.sessions)));
      return;
    }

    if (pathname.includes("/conversations/") && pathname.endsWith("/messages") && method === "GET") {
      const conversationId = getConversationIdFromUrl(url) ?? "";
      await route.fulfill(withJson(success(state.messagesByConversation.get(conversationId) ?? [])));
      return;
    }

    if (pathname.includes("/conversations/messages/") && pathname.endsWith("/feedback") && method === "POST") {
      await route.fulfill(withJson(success(null)));
      return;
    }

    if (pathname.includes("/conversations/") && method === "PUT") {
      const conversationId = getConversationIdFromUrl(url);
      const body = JSON.parse(request.postData() || "{}");
      if (conversationId) {
        state.sessions = state.sessions.map((session) =>
          session.conversationId === conversationId
            ? { ...session, title: body.title || session.title, lastTime: now }
            : session
        );
      }
      await route.fulfill(withJson(success(null)));
      return;
    }

    if (pathname.includes("/conversations/") && method === "DELETE") {
      const conversationId = getConversationIdFromUrl(url);
      if (conversationId) {
        state.sessions = state.sessions.filter((session) => session.conversationId !== conversationId);
        state.messagesByConversation.delete(conversationId);
      }
      await route.fulfill(withJson(success(null)));
      return;
    }

    if (pathname.endsWith("/rag/v3/chat") && method === "GET") {
      const question = searchParams.get("question") || "";
      const conversationId = searchParams.get("conversationId") || `session-${Date.now()}`;
      const persistedAssistantId = `msg-${conversationId}-assistant`;
      const existingSession = state.sessions.find((session) => session.conversationId === conversationId);
      if (!existingSession) {
        state.sessions.unshift({
          conversationId,
          title: "关于发布计划的提问",
          lastTime: now
        });
      }
      const currentMessages = state.messagesByConversation.get(conversationId) ?? [];
      state.messagesByConversation.set(conversationId, [
        ...currentMessages,
        {
          id: `user-${conversationId}-${currentMessages.length + 1}`,
          conversationId,
          role: "user",
          content: question,
          vote: null,
          createTime: now
        },
        {
          id: persistedAssistantId,
          conversationId,
          role: "assistant",
          content: "这是一个稳定的模拟回答。",
          vote: null,
          createTime: now
        }
      ]);
      await route.fulfill({
        status: 200,
        contentType: "text/event-stream; charset=utf-8",
        headers: {
          "cache-control": "no-cache",
          connection: "keep-alive"
        },
        body: createSseResponse(conversationId, persistedAssistantId)
      });
      return;
    }

    if (pathname.endsWith("/rag/v3/stop") && method === "POST") {
      await route.fulfill(withJson(success(null)));
      return;
    }

    if (pathname.endsWith("/knowledge-base") && method === "GET") {
      const name = (searchParams.get("name") || "").trim().toLowerCase();
      const records = !name
        ? state.knowledgeBases
        : state.knowledgeBases.filter((item) => item.name.toLowerCase().includes(name));
      await route.fulfill(
        withJson(
          success({
            records,
            total: records.length,
            size: Number(searchParams.get("size") || records.length || 10),
            current: Number(searchParams.get("current") || 1),
            pages: 1
          })
        )
      );
      return;
    }

    if (pathname.endsWith("/knowledge-base/docs/search") && method === "GET") {
      const keyword = (searchParams.get("keyword") || "").trim().toLowerCase();
      const docs = !keyword
        ? []
        : state.knowledgeDocs.filter(
            (item) => item.docName.toLowerCase().includes(keyword) || item.kbName.toLowerCase().includes(keyword)
          );
      await route.fulfill(withJson(success(docs)));
      return;
    }

    if (pathname.endsWith("/admin/dashboard/overview") && method === "GET") {
      await route.fulfill(
        withJson(
          success({
            window: searchParams.get("window") || "24h",
            compareWindow: "上一周期",
            updatedAt: 1_709_726_400_000,
            kpis: {
              totalUsers: { value: 12, delta: 2, deltaPct: 20 },
              activeUsers: { value: 5, delta: 1, deltaPct: 25 },
              totalSessions: { value: 18, delta: 3, deltaPct: 20 },
              sessions24h: { value: 6, delta: 2, deltaPct: 50 },
              totalMessages: { value: 48, delta: 6, deltaPct: 14 },
              messages24h: { value: 9, delta: 3, deltaPct: 50 }
            }
          })
        )
      );
      return;
    }

    if (pathname.endsWith("/admin/dashboard/performance") && method === "GET") {
      await route.fulfill(
        withJson(
          success({
            window: searchParams.get("window") || "24h",
            avgLatencyMs: 4200,
            p95LatencyMs: 9600,
            successRate: 0.97,
            errorRate: 0.02,
            noDocRate: 0.08,
            slowRate: 0.1
          })
        )
      );
      return;
    }

    if (pathname.endsWith("/admin/dashboard/trends") && method === "GET") {
      const metric = searchParams.get("metric") || "sessions";
      const seriesNameMap: Record<string, string> = {
        sessions: "会话数",
        messages: "消息数",
        activeUsers: "活跃用户",
        avgLatency: "平均响应时间",
        quality: "错误率"
      };
      await route.fulfill(
        withJson(
          success({
            metric,
            window: searchParams.get("window") || "24h",
            granularity: searchParams.get("granularity") || "hour",
            series: [
              {
                name: seriesNameMap[metric] || metric,
                data: [
                  { ts: 1_709_726_400_000, value: metric === "quality" ? 0.03 : 2 },
                  { ts: 1_709_730_000_000, value: metric === "quality" ? 0.02 : 4 },
                  { ts: 1_709_733_600_000, value: metric === "quality" ? 0.01 : 6 }
                ]
              }
            ]
          })
        )
      );
      return;
    }

    await route.fulfill(withJson(success(null)));
  });

  await context.route("https://api.github.com/repos/nageoffer/ragent", async (route) => {
    await route.fulfill(withJson({ stargazers_count: 999 }));
  });

  return state;
}

export async function loginThroughUi(page: Page, username = "admin", password = "password123") {
  await page.goto("/login");
  await page.getByPlaceholder("请输入用户名").fill(username);
  await page.getByPlaceholder("请输入密码").fill(password);
  await page.getByRole("button", { name: "登录" }).click();
}
