import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { LoginPage } from "@/pages/LoginPage";

const mocks = vi.hoisted(() => ({
  navigate: vi.fn(),
  login: vi.fn(),
  consumeAuthFailureReason: vi.fn(),
  toastSuccess: vi.fn(),
  toastError: vi.fn()
}));

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return {
    ...actual,
    useNavigate: () => mocks.navigate
  };
});

vi.mock("@/stores/authStore", () => ({
  useAuthStore: () => ({
    login: mocks.login,
    isLoading: false,
    consumeAuthFailureReason: mocks.consumeAuthFailureReason
  })
}));

vi.mock("sonner", () => ({
  toast: {
    success: mocks.toastSuccess,
    error: mocks.toastError
  }
}));

describe("LoginPage", () => {
  beforeEach(() => {
    mocks.navigate.mockReset();
    mocks.login.mockReset();
    mocks.consumeAuthFailureReason.mockReset();
    mocks.consumeAuthFailureReason.mockReturnValue(null);
    mocks.toastSuccess.mockReset();
    mocks.toastError.mockReset();
  });

  it("shows a validation error when credentials are blank", async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    await user.click(screen.getByRole("button", { name: "登录" }));

    expect(screen.getByText("请输入用户名和密码。")).toBeInTheDocument();
    expect(mocks.login).not.toHaveBeenCalled();
    expect(mocks.navigate).not.toHaveBeenCalled();
  });

  it("submits trimmed credentials and navigates to chat after login", async () => {
    const user = userEvent.setup();
    mocks.login.mockResolvedValue(undefined);

    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    await user.type(screen.getByPlaceholderText("请输入用户名"), "  admin  ");
    await user.type(screen.getByPlaceholderText("请输入密码"), "  secret  ");
    await user.click(screen.getByRole("button", { name: "登录" }));

    expect(mocks.login).toHaveBeenCalledWith("admin", "secret");
    expect(mocks.toastSuccess).toHaveBeenCalledWith("登录成功");
    expect(mocks.navigate).toHaveBeenCalledWith("/chat");
  });

  it("shows a session-expired toast from app boundary state", async () => {
    mocks.consumeAuthFailureReason.mockReturnValue("session_expired");

    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(mocks.toastError).toHaveBeenCalledWith("登录状态已过期，请重新登录");
    });
  });

  it("shows an error toast when login fails", async () => {
    const user = userEvent.setup();
    mocks.login.mockRejectedValue(new Error("认证失败"));

    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    await user.type(screen.getByPlaceholderText("请输入用户名"), "admin");
    await user.type(screen.getByPlaceholderText("请输入密码"), "secret");
    await user.click(screen.getByRole("button", { name: "登录" }));

    await waitFor(() => {
      expect(mocks.toastError).toHaveBeenCalledWith("认证失败");
    });
    expect(screen.getByText("认证失败")).toBeInTheDocument();
  });
});
