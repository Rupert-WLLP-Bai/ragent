import { RouterProvider } from "react-router-dom";

import { ErrorBoundary } from "@/components/common/ErrorBoundary";
import { Loading } from "@/components/common/Loading";
import { Toast } from "@/components/common/Toast";
import { router } from "@/router";

const routerFallback = (
  <div className="flex min-h-screen items-center justify-center bg-white">
    <Loading label="页面加载中..." />
  </div>
);

export default function App() {
  return (
    <ErrorBoundary>
      <RouterProvider router={router} fallbackElement={routerFallback} />
      <Toast />
    </ErrorBoundary>
  );
}
