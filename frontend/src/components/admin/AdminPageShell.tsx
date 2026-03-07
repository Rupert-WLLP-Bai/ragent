import type { ReactNode } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";

type AdminPageShellProps = {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
};

type AdminListCardProps = {
  loading: boolean;
  isEmpty: boolean;
  loadingLabel?: string;
  emptyLabel: ReactNode;
  children?: ReactNode;
  className?: string;
};

type AdminPaginationProps = {
  total: number;
  current: number;
  pages: number;
  onPrev: () => void;
  onNext: () => void;
  className?: string;
};

export function AdminPageShell({ title, subtitle, actions, children, className }: AdminPageShellProps) {
  return (
    <div className={cn("admin-page", className)}>
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">{title}</h1>
          {subtitle ? <p className="admin-page-subtitle">{subtitle}</p> : null}
        </div>
        {actions ? <div className="admin-page-actions">{actions}</div> : null}
      </div>
      {children}
    </div>
  );
}

export function AdminListCard({
  loading,
  isEmpty,
  loadingLabel = "加载中...",
  emptyLabel,
  children,
  className
}: AdminListCardProps) {
  return (
    <Card className={className}>
      <CardContent className="pt-6">
        {loading ? (
          <div className="py-8 text-center text-muted-foreground">{loadingLabel}</div>
        ) : isEmpty ? (
          <div className="py-8 text-center text-muted-foreground">{emptyLabel}</div>
        ) : (
          children
        )}
      </CardContent>
    </Card>
  );
}

export function AdminPagination({
  total,
  current,
  pages,
  onPrev,
  onNext,
  className
}: AdminPaginationProps) {
  return (
    <div className={cn("mt-4 flex flex-wrap items-center justify-between gap-2 text-sm text-slate-500", className)}>
      <span>共 {total} 条</span>
      <div className="flex items-center gap-2">
        <Button type="button" variant="outline" size="sm" onClick={onPrev} disabled={current <= 1}>
          上一页
        </Button>
        <span>
          {current} / {pages}
        </span>
        <Button type="button" variant="outline" size="sm" onClick={onNext} disabled={current >= pages}>
          下一页
        </Button>
      </div>
    </div>
  );
}
