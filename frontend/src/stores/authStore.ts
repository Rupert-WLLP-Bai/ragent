/* eslint-disable */

import { create } from "zustand";

import type { User } from "@/types";
import { getCurrentUser, login as loginRequest, logout as logoutRequest } from "@/services/authService";
import { setAuthToken } from "@/services/api";
import { useChatStore } from "@/stores/chatStore";
import { storage } from "@/utils/storage";

export type AuthFailureReason = "session_expired" | "unauthorized";

interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  authFailureReason: AuthFailureReason | null;
  login: (username: string, password: string) => Promise<User>;
  logout: () => Promise<void>;
  checkAuth: () => Promise<void>;
  fetchCurrentUser: () => Promise<void>;
  consumeAuthFailureReason: () => AuthFailureReason | null;
  expireSession: (reason?: AuthFailureReason) => void;
}

function resetChatState(isCreatingNew: boolean) {
  useChatStore.getState().cancelGeneration();
  useChatStore.setState({
    sessions: [],
    currentSessionId: null,
    messages: [],
    isLoading: false,
    isStreaming: false,
    isCreatingNew,
    deepThinkingEnabled: false,
    thinkingStartAt: null,
    streamTaskId: null,
    streamAbort: null,
    streamingMessageId: null,
    cancelRequested: false
  });
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: storage.getUser(),
  token: storage.getToken(),
  isAuthenticated: Boolean(storage.getToken()),
  isLoading: false,
  authFailureReason: null,
  login: async (username, password) => {
    set({ isLoading: true, authFailureReason: null });
    try {
      const data = await loginRequest(username, password);
      const user = {
        userId: data.userId,
        username: data.username || username,
        role: data.role,
        token: data.token,
        avatar: data.avatar
      };
      storage.setToken(user.token);
      storage.setUser(user);
      setAuthToken(user.token);
      set({ user, token: user.token, isAuthenticated: true, authFailureReason: null });
      get().fetchCurrentUser().catch(() => null);
      resetChatState(true);
      return user;
    } finally {
      set({ isLoading: false });
    }
  },
  logout: async () => {
    try {
      await logoutRequest();
    } catch {
      // Ignore network errors on logout
    }
    resetChatState(false);
    storage.clearAuth();
    setAuthToken(null);
    set({ user: null, token: null, isAuthenticated: false, authFailureReason: null });
  },
  checkAuth: async () => {
    const token = storage.getToken();
    const user = storage.getUser();
    setAuthToken(token);
    set({ token, user, isAuthenticated: Boolean(token) });
    if (token) {
      await get().fetchCurrentUser();
    }
  },
  fetchCurrentUser: async () => {
    const token = get().token || storage.getToken();
    if (!token) return;
    try {
      const data = await getCurrentUser();
      const nextUser = { ...data, token };
      storage.setUser(nextUser);
      set({ user: nextUser, token, isAuthenticated: true, authFailureReason: null });
    } catch {
      return;
    }
  },
  consumeAuthFailureReason: () => {
    const reason = get().authFailureReason;
    if (reason) {
      set({ authFailureReason: null });
    }
    return reason;
  },
  expireSession: (reason = "session_expired") => {
    resetChatState(false);
    storage.clearAuth();
    setAuthToken(null);
    set({ user: null, token: null, isAuthenticated: false, authFailureReason: reason });
  }
}));
