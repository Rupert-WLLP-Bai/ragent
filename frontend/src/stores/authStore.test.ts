import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  loginRequest: vi.fn(),
  logoutRequest: vi.fn(),
  getCurrentUser: vi.fn(),
  setAuthToken: vi.fn(),
  cancelGeneration: vi.fn()
}));

vi.mock("@/services/authService", () => ({
  login: mocks.loginRequest,
  logout: mocks.logoutRequest,
  getCurrentUser: mocks.getCurrentUser
}));

vi.mock("@/services/api", () => ({
  setAuthToken: mocks.setAuthToken
}));

vi.mock("@/stores/chatStore", () => ({
  useChatStore: {
    getState: () => ({ cancelGeneration: mocks.cancelGeneration }),
    setState: vi.fn()
  }
}));

import { storage } from "@/utils/storage";
import { useAuthStore } from "@/stores/authStore";

describe("authStore", () => {
  beforeEach(() => {
    localStorage.clear();
    useAuthStore.setState({
      user: null,
      token: null,
      isAuthenticated: false,
      isLoading: false
    });
    mocks.loginRequest.mockReset();
    mocks.logoutRequest.mockReset();
    mocks.getCurrentUser.mockReset();
    mocks.setAuthToken.mockReset();
    mocks.cancelGeneration.mockReset();
  });

  it("stores auth data after a successful login", async () => {
    mocks.loginRequest.mockResolvedValue({
      userId: "u1",
      username: "demo",
      role: "admin",
      token: "token-123",
      avatar: ""
    });
    mocks.getCurrentUser.mockResolvedValue({
      userId: "u1",
      username: "demo",
      role: "admin",
      avatar: ""
    });

    const user = await useAuthStore.getState().login("demo", "secret");

    expect(user).toMatchObject({ userId: "u1", token: "token-123" });
    expect(storage.getToken()).toBe("token-123");
    expect(useAuthStore.getState().isAuthenticated).toBe(true);
    expect(mocks.setAuthToken).toHaveBeenCalledWith("token-123");
  });

  it("clears auth data on logout", async () => {
    storage.setToken("token-123");
    storage.setUser({ userId: "u1", username: "demo", role: "admin", token: "token-123" });
    useAuthStore.setState({
      user: { userId: "u1", username: "demo", role: "admin", token: "token-123" },
      token: "token-123",
      isAuthenticated: true,
      isLoading: false
    });

    await useAuthStore.getState().logout();

    expect(storage.getToken()).toBeNull();
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(mocks.setAuthToken).toHaveBeenCalledWith(null);
  });

  it("expires the session without UI side effects", () => {
    storage.setToken("token-123");
    storage.setUser({ userId: "u1", username: "demo", role: "admin", token: "token-123" });
    useAuthStore.setState({
      user: { userId: "u1", username: "demo", role: "admin", token: "token-123" },
      token: "token-123",
      isAuthenticated: true,
      isLoading: false,
      authFailureReason: null
    });

    useAuthStore.getState().expireSession();

    expect(storage.getToken()).toBeNull();
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(useAuthStore.getState().consumeAuthFailureReason()).toBe("session_expired");
    expect(useAuthStore.getState().consumeAuthFailureReason()).toBeNull();
    expect(mocks.setAuthToken).toHaveBeenCalledWith(null);
  });
});
