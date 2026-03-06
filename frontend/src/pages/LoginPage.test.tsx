import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { LoginPage } from "@/pages/LoginPage";

const navigateMock = vi.fn();
const loginMock = vi.fn();

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return {
    ...actual,
    useNavigate: () => navigateMock
  };
});

vi.mock("@/stores/authStore", () => ({
  useAuthStore: () => ({
    login: loginMock,
    isLoading: false
  })
}));

describe("LoginPage", () => {
  beforeEach(() => {
    navigateMock.mockReset();
    loginMock.mockReset();
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
    expect(loginMock).not.toHaveBeenCalled();
    expect(navigateMock).not.toHaveBeenCalled();
  });

  it("submits trimmed credentials and navigates to chat after login", async () => {
    const user = userEvent.setup();
    loginMock.mockResolvedValue(undefined);

    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    await user.type(screen.getByPlaceholderText("请输入用户名"), "  admin  ");
    await user.type(screen.getByPlaceholderText("请输入密码"), "  secret  ");
    await user.click(screen.getByRole("button", { name: "登录" }));

    expect(loginMock).toHaveBeenCalledWith("admin", "secret");
    expect(navigateMock).toHaveBeenCalledWith("/chat");
  });
});
