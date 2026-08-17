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

export const TableHead = React.forwardRef<
  HTMLTableCellElement,
  React.ThHTMLAttributes<HTMLTableCellElement> & { sortable?: boolean }
>(({ className, children, sortable, ...props }, ref) => (
  <th
    ref={ref}
    className={cn(
      "h-11 px-3.5 py-3 text-left align-middle text-xs font-semibold text-slate-800 dark:text-slate-200 border-r border-b border-slate-300 dark:border-slate-700 last:border-r-0 bg-slate-100/90 dark:bg-slate-800/90 whitespace-nowrap",
      sortable && "cursor-pointer select-none hover:bg-slate-200/80 dark:hover:bg-slate-700/80",
      className
    )}
    {...props}
  >
    <div className="flex items-center gap-1.5">
      <span>{children}</span>
      {sortable && (
        <span className="text-[10px] text-slate-400 font-mono tracking-tighter">↑↓</span>
      )}
    </div>
  </th>
));
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
