import type { LucideIcon } from "lucide-react";
import { cn } from "@/lib/utils";

/** Solid fills for count tiles. Shared so every page's tiles match. */
export const TILE_FILLS = {
  violet: "linear-gradient(135deg, #6d28d9 0%, #8b5cf6 100%)",
  amber:  "linear-gradient(135deg, #b45309 0%, #d97706 100%)",
  green:  "linear-gradient(135deg, #0a9d68 0%, #21a87c 100%)",
  red:    "linear-gradient(135deg, #dc2626 0%, #ef5350 100%)",
  blue:   "linear-gradient(135deg, #1d6fd8 0%, #3f8ce8 100%)",
  orange: "linear-gradient(135deg, #c2410c 0%, #ea7317 100%)",
  slate:  "linear-gradient(135deg, #475569 0%, #64748b 100%)",
  pink:   "linear-gradient(135deg, #db2777 0%, #ec5a9c 100%)",
  yellow: "linear-gradient(135deg, #ca8a04 0%, #eab308 100%)"
} as const;

/**
 * A count tile that doubles as a filter. When `onClick` is given it renders as a
 * button and `active` shows which one the table is currently filtered by.
 */
export function StatTile({
  label, value, hint, icon: Icon, fill, active = false, onClick, compact = false
}: {
  label: string;
  value: number | string;
  hint?: string;
  icon: LucideIcon;
  fill: string;
  active?: boolean;
  onClick?: () => void;
  /** Tighter tile for rows of five or six — number beside the label, no hint. */
  compact?: boolean;
}) {
  const Tag = onClick ? "button" : "div";

  if (compact) {
    return (
      <Tag
        {...(onClick ? { type: "button" as const, onClick } : {})}
        style={{ backgroundImage: fill }}
        title={hint}
        className={cn(
          "flex items-center gap-2.5 rounded-xl px-3 py-2.5 text-left text-white shadow-sm transition-all",
          onclickable(onClick),
          active ? "ring-2 ring-white/70 ring-offset-2 ring-offset-background" : "opacity-90"
        )}
      >
        <span className="grid h-7 w-7 shrink-0 place-items-center rounded-lg bg-white/20">
          <Icon className="h-3.5 w-3.5" />
        </span>
        <span className="min-w-0 flex-1 truncate text-[10px] font-bold uppercase tracking-wider">
          {label}
        </span>
        <span className="text-lg font-bold leading-none tabular-nums">{value}</span>
      </Tag>
    );
  }

  return (
    <Tag
      {...(onClick ? { type: "button" as const, onClick } : {})}
      style={{ backgroundImage: fill }}
      className={cn(
        "rounded-2xl p-4 text-left text-white shadow-sm transition-all",
        onclickable(onClick),
        active ? "ring-2 ring-white/70 ring-offset-2 ring-offset-background" : "opacity-90"
      )}
    >
      <div className="flex items-center gap-2">
        <span className="grid h-8 w-8 place-items-center rounded-lg bg-white/20">
          <Icon className="h-4 w-4" />
        </span>
        <span className="text-[11px] font-bold uppercase tracking-wider">{label}</span>
      </div>
      <div className="mt-2.5 text-3xl font-bold tabular-nums">{value}</div>
      {hint && <div className="text-[11px] text-white/80">{hint}</div>}
    </Tag>
  );
}

const onclickable = (onClick?: () => void) =>
  onClick
    ? "hover:brightness-110 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50"
    : "";
