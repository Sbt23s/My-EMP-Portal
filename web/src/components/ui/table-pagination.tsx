import { useEffect, useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/**
 * Client-side pagination helper. Returns the rows for the current page plus the
 * controls state. `deps` (filters/search) reset the view to page 1.
 *
 * The size passed in is the starting size; it can be changed from the controls,
 * which is what {@link TablePagination} does with `onPageSizeChange`. Callers
 * that ignore the extra return values behave exactly as they did.
 */
export function usePagedRows<T>(rows: T[], pageSize = 15, deps: unknown[] = []) {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(pageSize);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { setPage(0); }, deps);

  /** A different page size means a different page 1. */
  const setPageSize = (next: number) => {
    setSize(next);
    setPage(0);
  };

  const totalPages = Math.max(1, Math.ceil(rows.length / size));
  const pageSafe = Math.min(page, totalPages - 1);
  const pageRows = useMemo(
    () => rows.slice(pageSafe * size, pageSafe * size + size),
    [rows, pageSafe, size]
  );
  return {
    pageRows, page: pageSafe, setPage, totalPages,
    pageSize: size, setPageSize, total: rows.length
  };
}

const PAGE_SIZES = [10, 25, 50, 100];

/**
 * Which page numbers to draw. With a handful they all show; beyond that the
 * first and last stay put and a window follows the current page, with a gap
 * either side — so a hundred pages still fit on one line.
 */
function pageWindow(page: number, totalPages: number): (number | "gap")[] {
  if (totalPages <= 7) return Array.from({ length: totalPages }, (_, i) => i);
  const out: (number | "gap")[] = [0];
  const from = Math.max(1, page - 1);
  const to = Math.min(totalPages - 2, page + 1);
  if (from > 1) out.push("gap");
  for (let i = from; i <= to; i++) out.push(i);
  if (to < totalPages - 2) out.push("gap");
  out.push(totalPages - 1);
  return out;
}

export function TablePagination({
  page,
  totalPages,
  onChange,
  always = false,
  pageSize,
  onPageSizeChange,
  total
}: {
  page: number;
  totalPages: number;
  onChange: (p: number) => void;
  /** Keep the controls on screen even when everything fits on one page. */
  always?: boolean;
  /** Current rows-per-page. Pass with `onPageSizeChange` to offer the choice. */
  pageSize?: number;
  onPageSizeChange?: (n: number) => void;
  /** Total rows, so the controls can say which of them are on screen. */
  total?: number;
}) {
  if (totalPages <= 1 && !always && !onPageSizeChange) return null;

  const showing = total != null && pageSize != null;
  const from = showing && total > 0 ? page * pageSize + 1 : 0;
  const to = showing ? Math.min((page + 1) * pageSize, total) : 0;

  return (
    // Buttons sit beside the page count rather than out at the right edge: the
    // floating chat bubble is fixed to the bottom-right of every page and was
    // covering "Next" whenever a table ran to the bottom of the window.
    <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 border-t border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/50 px-4 py-3 text-sm">
      {onPageSizeChange && (
        <div className="flex items-center gap-2">
          <select
            className="h-8 rounded-md border border-slate-200 dark:border-slate-800 bg-background px-2.5 text-xs font-medium text-slate-700 dark:text-slate-200 shadow-sm focus:outline-none focus:ring-1 focus:ring-primary"
            value={pageSize}
            onChange={(e) => onPageSizeChange(Number(e.target.value))}
          >
            {PAGE_SIZES.map((n) => <option key={n} value={n}>{n}</option>)}
          </select>
          <span className="whitespace-nowrap text-xs font-medium text-slate-500 dark:text-slate-400">Rows per page</span>
        </div>
      )}

      <div className="flex items-center gap-4 ml-auto">
        <span className="text-xs font-medium text-slate-600 dark:text-slate-400 tabular-nums">
          {showing
            ? <>{from}–{to} of {total}</>
            : <>{page + 1} of {totalPages}</>}
        </span>

        <div className="flex items-center gap-1">
          <Button
            variant="outline"
            size="sm"
            className="h-8 w-8 p-0"
            disabled={page === 0}
            onClick={() => onChange(0)}
            title="First page"
          >
            «
          </Button>
          <Button
            variant="outline"
            size="sm"
            className="h-8 w-8 p-0"
            disabled={page === 0}
            onClick={() => onChange(page - 1)}
            title="Previous page"
          >
            ‹
          </Button>

          {pageWindow(page, totalPages).map((p, i) =>
            p === "gap" ? (
              <span key={`gap-${i}`} className="px-1 text-xs text-slate-400">…</span>
            ) : (
              <button
                key={p}
                type="button"
                onClick={() => onChange(p)}
                aria-current={p === page ? "page" : undefined}
                className={cn(
                  "h-8 min-w-[2rem] rounded-md border px-2 text-xs font-semibold tabular-nums transition-all shadow-sm",
                  p === page
                    ? "border-primary bg-primary text-primary-foreground font-bold shadow"
                    : "border-slate-200 dark:border-slate-800 bg-background text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800"
                )}
              >
                {p + 1}
              </button>
            )
          )}

          <Button
            variant="outline"
            size="sm"
            className="h-8 w-8 p-0"
            disabled={page >= totalPages - 1}
            onClick={() => onChange(page + 1)}
            title="Next page"
          >
            ›
          </Button>
          <Button
            variant="outline"
            size="sm"
            className="h-8 w-8 p-0"
            disabled={page >= totalPages - 1}
            onClick={() => onChange(totalPages - 1)}
            title="Last page"
          >
            »
          </Button>
        </div>
      </div>
    </div>
  );
}
