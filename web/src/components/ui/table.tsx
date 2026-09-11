import * as React from "react";
import { cn } from "@/lib/utils";

export const Table = React.forwardRef<HTMLTableElement, React.HTMLAttributes<HTMLTableElement>>(
  ({ className, ...props }, ref) => (
    <div className="relative w-full overflow-x-auto rounded-xl border border-slate-300 dark:border-slate-700 shadow-sm bg-white dark:bg-slate-900">
      <table ref={ref} className={cn("w-full caption-bottom text-sm border-collapse", className)} {...props} />
    </div>
  )
);
Table.displayName = "Table";

export const TableHeader = React.forwardRef<
  HTMLTableSectionElement,
  React.HTMLAttributes<HTMLTableSectionElement>
>(({ className, ...props }, ref) => (
  <thead ref={ref} className={cn("bg-slate-100/90 dark:bg-slate-800/90 border-b border-slate-300 dark:border-slate-700", className)} {...props} />
));
TableHeader.displayName = "TableHeader";

export const TableBody = React.forwardRef<
  HTMLTableSectionElement,
  React.HTMLAttributes<HTMLTableSectionElement>
>(({ className, ...props }, ref) => (
  <tbody ref={ref} className={cn("[&_tr:last-child]:border-0 [&_tr:last-child>td]:border-b-0", className)} {...props} />
));
TableBody.displayName = "TableBody";

export const TableRow = React.forwardRef<
  HTMLTableRowElement,
  React.HTMLAttributes<HTMLTableRowElement>
>(({ className, ...props }, ref) => (
  <tr
    ref={ref}
    className={cn(
      "border-b border-slate-200 dark:border-slate-800 transition-colors hover:bg-slate-50 dark:hover:bg-slate-800/50",
      className
    )}
    {...props}
  />
));
TableRow.displayName = "TableRow";

/**
 * A column heading.
 *
 * <p>Pass {@code sortKey} with the state from {@code useTableSort} to make it
 * sort when clicked. The arrow is always drawn, faint until this is the column
 * in use -- so a reader can see which headings sort without hovering each one,
 * and the row does not shift by a few pixels when a sort turns on.
 *
 * <p>{@code sortable} on its own still works and still draws the old static
 * hint, so the tables that use it keep rendering exactly as before.
 */
export const TableHead = React.forwardRef<
  HTMLTableCellElement,
  React.ThHTMLAttributes<HTMLTableCellElement> & {
    sortable?: boolean;
    /** This column's key, as the sort hook knows it. */
    sortKey?: string;
    /** Which column the table is sorted by right now, if any. */
    activeKey?: string | null;
    sortDir?: "asc" | "desc";
    onSort?: (key: string) => void;
  }
>(({ className, children, sortable, sortKey, activeKey, sortDir = "asc", onSort, ...props }, ref) => {
  const interactive = !!sortKey && !!onSort;
  const active = interactive && activeKey === sortKey;

  return (
    <th
      ref={ref}
      className={cn(
        "h-11 px-3.5 py-3 text-left align-middle text-xs font-semibold text-slate-800 dark:text-slate-200 border-r border-b border-slate-300 dark:border-slate-700 last:border-r-0 bg-slate-100/90 dark:bg-slate-800/90 whitespace-nowrap",
        (sortable || interactive) && "cursor-pointer select-none hover:bg-slate-200/80 dark:hover:bg-slate-700/80",
        className
      )}
      aria-sort={active ? (sortDir === "asc" ? "ascending" : "descending") : undefined}
      onClick={interactive ? () => onSort!(sortKey!) : props.onClick}
      title={
        interactive
          ? active
            ? sortDir === "asc"
              ? "Sorted ascending — click for descending"
              : "Sorted descending — click to clear"
            : "Click to sort"
          : props.title
      }
      {...props}
    >
      <div className="flex items-center gap-1.5">
        <span>{children}</span>
        {interactive ? (
          <span
            className={cn(
              "font-mono text-[10px] leading-none tracking-tighter",
              active ? "text-slate-700 dark:text-slate-200" : "text-slate-400/60"
            )}
          >
            {active ? (sortDir === "asc" ? "↑" : "↓") : "↕"}
          </span>
        ) : sortable ? (
          <span className="font-mono text-[10px] tracking-tighter text-slate-400">↑↓</span>
        ) : null}
      </div>
    </th>
  );
});
TableHead.displayName = "TableHead";

export const TableCell = React.forwardRef<
  HTMLTableCellElement,
  React.TdHTMLAttributes<HTMLTableCellElement>
>(({ className, ...props }, ref) => (
  <td
    ref={ref}
    className={cn(
      "px-3.5 py-3 align-middle text-xs text-slate-700 dark:text-slate-300 border-r border-b border-slate-200 dark:border-slate-800 last:border-r-0",
      className
    )}
    {...props}
  />
));
TableCell.displayName = "TableCell";
