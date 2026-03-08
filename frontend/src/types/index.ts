export type Role = "user" | "assistant";

export type FeedbackValue = "like" | "dislike" | null;

export type MessageStatus = "streaming" | "done" | "cancelled" | "error";

export interface User {
  userId: string;
  username?: string;
  role: string;
  token: string;
  avatar?: string;
}

export type CurrentUser = Omit<User, "token">;

export interface Session {
  id: string;
  title: string;
  lastTime?: string;
}

export interface TraceDecisionStep {
  key: string;
  label: string;
  value?: string | null;
  reason?: string | null;
}

export interface RequestTracePayload {
  route?: string | null;
  stage?: string | null;
  summary?: string | null;
  selectedModel?: string | null;
  modelMode?: string | null;
  thinkingMode?: string | null;
  requestPlan?: string | null;
  retrievalPlan?: string | null;
  decisions?: TraceDecisionStep[] | null;
}

export interface Message {
  id: string;
  role: Role;
  content: string;
  thinking?: string;
  thinkingDuration?: number;
  isDeepThinking?: boolean;
  isThinking?: boolean;
  trace?: RequestTracePayload;
  createdAt?: string;
  feedback?: FeedbackValue;
  status?: MessageStatus;
}

export interface StreamMetaPayload {
  conversationId: string;
  taskId: string;
}

export interface MessageDeltaPayload {
  type: string;
  delta: string;
  trace?: RequestTracePayload;
}

export interface CompletionPayload {
  messageId?: string | null;
  title?: string | null;
  trace?: RequestTracePayload;
}
