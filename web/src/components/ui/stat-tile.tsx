import type { LucideIcon } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * The tint each tile is drawn in.
 *
 * <p>These were solid gradients and the tile was filled edge to edge in them,
 * white text on top. A row of six read as six blocks of colour competing with
 * each other and with the table underneath, and the number -- the only thing
 * anybody is actually reading -- was the quietest part of it.
 *
 * <p>Now each entry is a tint: a pale wash for the surface, a matching border,
 * and a saturated value kept for the icon and the accent alone. The card is
 * light, the type is the page's own foreground colour, and the colour does the
 * one job it is good at, which is telling the tiles apart at a glance.
 *
 * <p>The keys are unchanged, so every page that already asks for
 * {@code TILE_FILLS.green} keeps working and simply looks lighter.
 */
export const TILE_FILLS = {
  violet: "violet",
  amber: "amber",
  green: "green",
  red: "red",
  blue: "blue",
  orange: "orange",
  slate: "slate",
  pink: "pink",
  yellow: "yellow"
} as const;

export type TileTone = (typeof TILE_FILLS)[keyof typeof TILE_FILLS];

/**
 * Surface, border and accent per tone, for both themes.
 *
 * <p>Written as literal Tailwind classes rather than composed at runtime,
 * because Tailwind only ships the classes it can see in the source -- a
 * template string like `bg-${tone}-50` produces a tile with no background at
 * all in a production build.
 */
export const TILE_TONE: Record<string, { surface: string; icon: string; value: string }> = {
  violet: {
    surface: "bg-violet-50/70 border-violet-200/70 dark:bg-violet-500/10 dark:border-violet-400/20",
    icon: "bg-violet-100 text-violet-700 dark:bg-violet-500/20 dark:text-violet-300",
    value: "text-violet-900 dark:text-violet-100"
  },
  amber: {
    surface: "bg-amber-50/70 border-amber-200/70 dark:bg-amber-500/10 dark:border-amber-400/20",
    icon: "bg-amber-100 text-amber-700 dark:bg-amber-500/20 dark:text-amber-300",
    value: "text-amber-900 dark:text-amber-100"
  },
  green: {
    surface: "bg-emerald-50/70 border-emerald-200/70 dark:bg-emerald-500/10 dark:border-emerald-400/20",
    icon: "bg-emerald-100 text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-300",
    value: "text-emerald-900 dark:text-emerald-100"
  },
  red: {
    surface: "bg-rose-50/70 border-rose-200/70 dark:bg-rose-500/10 dark:border-rose-400/20",
    icon: "bg-rose-100 text-rose-700 dark:bg-rose-500/20 dark:text-rose-300",
    value: "text-rose-900 dark:text-rose-100"
  },
  blue: {
    surface: "bg-sky-50/70 border-sky-200/70 dark:bg-sky-500/10 dark:border-sky-400/20",
    icon: "bg-sky-100 text-sky-700 dark:bg-sky-500/20 dark:text-sky-300",
    value: "text-sky-900 dark:text-sky-100"
  },
  orange: {
    surface: "bg-orange-50/70 border-orange-200/70 dark:bg-orange-500/10 dark:border-orange-400/20",
    icon: "bg-orange-100 text-orange-700 dark:bg-orange-500/20 dark:text-orange-300",
    value: "text-orange-900 dark:text-orange-100"
  },
  slate: {
    surface: "bg-slate-50 border-slate-200/80 dark:bg-slate-500/10 dark:border-slate-400/20",
    icon: "bg-slate-200 text-slate-700 dark:bg-slate-500/20 dark:text-slate-300",
    value: "text-slate-900 dark:text-slate-100"
  },
  pink: {
    surface: "bg-pink-50/70 border-pink-200/70 dark:bg-pink-500/10 dark:border-pink-400/20",
    icon: "bg-pink-100 text-pink-700 dark:bg-pink-500/20 dark:text-pink-300",
    value: "text-pink-900 dark:text-pink-100"
  },
  yellow: {
    surface: "bg-yellow-50/70 border-yellow-200/70 dark:bg-yellow-500/10 dark:border-yellow-400/20",
    icon: "bg-yellow-100 text-yellow-700 dark:bg-yellow-500/20 dark:text-yellow-300",
    value: "text-yellow-900 dark:text-yellow-100"
  }
};

/** Anything unrecognised falls back to slate rather than rendering untinted. */
const toneOf = (fill: string) => TILE_TONE[fill] ?? TILE_TONE.slate;

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
  const tone = toneOf(fill);

  if (compact) {
    return (
      <Tag
        {...(onClick ? { type: "button" as const, onClick } : {})}
        title={hint}
        className={cn(
          "flex items-center gap-2.5 rounded-xl border px-3 py-2.5 text-left transition-all",
          tone.surface,
          onclickable(onClick),
          // The selected tile is marked by a ring in its own colour rather than
          // by dimming every other one -- six faded tiles read as six disabled
          // tiles, which is not what a filter is saying.
          active && "ring-2 ring-primary/40 ring-offset-1 ring-offset-background"
        )}
      >
        <span className={cn("grid h-7 w-7 shrink-0 place-items-center rounded-lg", tone.icon)}>
          <Icon className="h-3.5 w-3.5" />
        </span>
        <span className="min-w-0 flex-1 truncate text-[10px] font-bold uppercase tracking-wider text-muted-foreground">
          {label}
        </span>
        <span className={cn("text-lg font-bold leading-none tabular-nums", tone.value)}>
          {value}
        </span>
      </Tag>
    );
  }

  return (
    <Tag
      {...(onClick ? { type: "button" as const, onClick } : {})}
      className={cn(
        "rounded-2xl border p-4 text-left transition-all",
        tone.surface,
        onclickable(onClick),
        active && "ring-2 ring-primary/40 ring-offset-1 ring-offset-background"
      )}
    >
      <div className="flex items-center gap-2">
        <span className={cn("grid h-8 w-8 place-items-center rounded-lg", tone.icon)}>
          <Icon className="h-4 w-4" />
        </span>
        <span className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground">
          {label}
        </span>
      </div>
      <div className={cn("mt-2.5 text-3xl font-bold tabular-nums", tone.value)}>{value}</div>
      {hint && <div className="text-[11px] text-muted-foreground">{hint}</div>}
    </Tag>
  );
}

const onclickable = (onClick?: () => void) =>
  onClick
    ? "hover:shadow-sm hover:brightness-[0.98] dark:hover:brightness-110 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50"
    : "";
