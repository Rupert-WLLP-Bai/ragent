import { describe, expect, it, vi } from "vitest";

import { storage } from "@/utils/storage";

describe("storage", () => {
  it("stores and clears auth data", () => {
    storage.setToken("token-123");
    storage.setUser({ userId: "u1", username: "demo", role: "admin", token: "token-123" });

    expect(storage.getToken()).toBe("token-123");
    expect(storage.getUser()).toMatchObject({ userId: "u1", username: "demo" });

    storage.clearAuth();

    expect(storage.getToken()).toBeNull();
    expect(storage.getUser()).toBeNull();
  });

  it("returns null when user JSON is invalid", () => {
    localStorage.setItem("ragent_user", "not-json");

    expect(storage.getUser()).toBeNull();
  });

  it("swallows storage write failures", () => {
    const setItemSpy = vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("quota exceeded");
    });

    expect(() => storage.setTheme("dark")).not.toThrow();

    setItemSpy.mockRestore();
  });
});
