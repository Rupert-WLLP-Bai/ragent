import { Suspense, lazy } from "react";
import { Navigate, createBrowserRouter } from "react-router-dom";

import { Loading } from "@/components/common/Loading";
import { LoginPage } from "@/pages/LoginPage";
import { ChatPage } from "@/pages/ChatPage";
import { NotFoundPage } from "@/pages/NotFoundPage";
import { useAuthStore } from "@/stores/authStore";

const AdminLayout = lazy(() => import("@/pages/admin/AdminLayout").then((module) => ({ default: module.AdminLayout })));
const DashboardPage = lazy(() =>
  import("@/pages/admin/dashboard/DashboardPage").then((module) => ({ default: module.DashboardPage }))
);
const KnowledgeListPage = lazy(() =>
  import("@/pages/admin/knowledge/KnowledgeListPage").then((module) => ({ default: module.KnowledgeListPage }))
);
const KnowledgeDocumentsPage = lazy(() =>
  import("@/pages/admin/knowledge/KnowledgeDocumentsPage").then((module) => ({ default: module.KnowledgeDocumentsPage }))
);
const KnowledgeChunksPage = lazy(() =>
  import("@/pages/admin/knowledge/KnowledgeChunksPage").then((module) => ({ default: module.KnowledgeChunksPage }))
);
const IntentTreePage = lazy(() =>
  import("@/pages/admin/intent-tree/IntentTreePage").then((module) => ({ default: module.IntentTreePage }))
);
const IntentListPage = lazy(() =>
  import("@/pages/admin/intent-tree/IntentListPage").then((module) => ({ default: module.IntentListPage }))
);
const IntentEditPage = lazy(() =>
  import("@/pages/admin/intent-tree/IntentEditPage").then((module) => ({ default: module.IntentEditPage }))
);
const IngestionPage = lazy(() =>
  import("@/pages/admin/ingestion/IngestionPage").then((module) => ({ default: module.IngestionPage }))
);
const RagTracePage = lazy(() =>
  import("@/pages/admin/traces/RagTracePage").then((module) => ({ default: module.RagTracePage }))
);
const RagTraceDetailPage = lazy(() =>
  import("@/pages/admin/traces/RagTraceDetailPage").then((module) => ({ default: module.RagTraceDetailPage }))
);
const SystemSettingsPage = lazy(() =>
  import("@/pages/admin/settings/SystemSettingsPage").then((module) => ({ default: module.SystemSettingsPage }))
);
const SampleQuestionPage = lazy(() =>
  import("@/pages/admin/sample-questions/SampleQuestionPage").then((module) => ({ default: module.SampleQuestionPage }))
);
const UserListPage = lazy(() =>
  import("@/pages/admin/users/UserListPage").then((module) => ({ default: module.UserListPage }))
);

const routeFallback = (
  <div className="flex min-h-[240px] items-center justify-center bg-white">
    <Loading label="页面加载中..." />
  </div>
);

function LazyRoute({ children }: { children: JSX.Element }) {
  return <Suspense fallback={routeFallback}>{children}</Suspense>;
}

function RequireAuth({ children }: { children: JSX.Element }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  return children;
}

function RequireAdmin({ children }: { children: JSX.Element }) {
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (user?.role !== "admin") {
    return <Navigate to="/chat" replace />;
  }

  return children;
}

function RedirectIfAuth({ children }: { children: JSX.Element }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  if (isAuthenticated) {
    return <Navigate to="/chat" replace />;
  }
  return children;
}

function HomeRedirect() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  return <Navigate to={isAuthenticated ? "/chat" : "/login"} replace />;
}

export const router = createBrowserRouter([
  {
    path: "/",
    element: <HomeRedirect />
  },
  {
    path: "/login",
    element: (
      <RedirectIfAuth>
        <LoginPage />
      </RedirectIfAuth>
    )
  },
  {
    path: "/chat",
    element: (
      <RequireAuth>
        <ChatPage />
      </RequireAuth>
    )
  },
  {
    path: "/chat/:sessionId",
    element: (
      <RequireAuth>
        <ChatPage />
      </RequireAuth>
    )
  },
  {
    path: "/admin",
    element: (
      <RequireAdmin>
        <LazyRoute>
          <AdminLayout />
        </LazyRoute>
      </RequireAdmin>
    ),
    children: [
      {
        index: true,
        element: <Navigate to="/admin/dashboard" replace />
      },
      {
        path: "dashboard",
        element: (
          <LazyRoute>
            <DashboardPage />
          </LazyRoute>
        )
      },
      {
        path: "knowledge",
        element: (
          <LazyRoute>
            <KnowledgeListPage />
          </LazyRoute>
        )
      },
      {
        path: "knowledge/:kbId",
        element: (
          <LazyRoute>
            <KnowledgeDocumentsPage />
          </LazyRoute>
        )
      },
      {
        path: "knowledge/:kbId/docs/:docId",
        element: (
          <LazyRoute>
            <KnowledgeChunksPage />
          </LazyRoute>
        )
      },
      {
        path: "intent-tree",
        element: (
          <LazyRoute>
            <IntentTreePage />
          </LazyRoute>
        )
      },
      {
        path: "intent-list",
        element: (
          <LazyRoute>
            <IntentListPage />
          </LazyRoute>
        )
      },
      {
        path: "intent-list/:id/edit",
        element: (
          <LazyRoute>
            <IntentEditPage />
          </LazyRoute>
        )
      },
      {
        path: "ingestion",
        element: (
          <LazyRoute>
            <IngestionPage />
          </LazyRoute>
        )
      },
      {
        path: "traces",
        element: (
          <LazyRoute>
            <RagTracePage />
          </LazyRoute>
        )
      },
      {
        path: "traces/:traceId",
        element: (
          <LazyRoute>
            <RagTraceDetailPage />
          </LazyRoute>
        )
      },
      {
        path: "settings",
        element: (
          <LazyRoute>
            <SystemSettingsPage />
          </LazyRoute>
        )
      },
      {
        path: "sample-questions",
        element: (
          <LazyRoute>
            <SampleQuestionPage />
          </LazyRoute>
        )
      },
      {
        path: "users",
        element: (
          <LazyRoute>
            <UserListPage />
          </LazyRoute>
        )
      }
    ]
  },
  {
    path: "*",
    element: <NotFoundPage />
  }
]);
