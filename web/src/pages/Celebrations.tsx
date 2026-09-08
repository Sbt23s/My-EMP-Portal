import { useState, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { Cake, PartyPopper, Search, CalendarDays } from "lucide-react";
import dayjs from "dayjs";
import { api } from "@/lib/api";
import { PageHeader } from "@/components/PageHeader";
import { EmptyState } from "@/components/EmptyState";
import { PageLoader } from "@/components/ui/page-loader";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { Avatar } from "@/components/ui/avatar";
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from "@/components/ui/table";
import { usePagedRows, TablePagination } from "@/components/ui/table-pagination";
import { cn } from "@/lib/utils";
import type { ApiEnvelope } from "@/types";

/**
 * Birthdays and work anniversaries, a year at a time.
 *
 * The dashboard already carries a card for these, and it answers a different
 * question: what is coming up in the next sixty days, twelve rows of it. That
 * is the right shape for a widget somebody glances at, and the wrong shape for
 * checking a quarter, planning a year, or finding out when somebody's date
 * actually is — for which the card silently omits both the past and everybody
 * beyond the twelfth name.
 *
 * So this is the register: every date in the chosen year, January to December,
 * dates already past included. A year that hides the months that have happened
 * cannot be checked against anything.
 */

interface Celebration {
  userId: number;
  name: string;
  employeeCode?: string;
  team?: string;
  photoPath?: string;
  type: "BIRTHDAY" | "ANNIVERSARY";
  date: string;
  /** Measured from today, so negative for a date already past. */
  daysUntil: number;
  years?: number;
}

const MONTHS = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December"
];

/**
 * How near the day is, in words.
 *
 * "Today" means today and nothing else. The notification text used to say
 * "Birthday today" and keep saying it for a fortnight, which is the mistake
 * this deliberately does not repeat: a date in the past is named, not
 * described.
 */
function whenLabel(c: Celebration): { text: string; tone: "today" | "soon" | "past" | "future" } {
  if (c.daysUntil === 0) return { text: "Today", tone: "today" };
  if (c.daysUntil === 1) return { text: "Tomorrow", tone: "soon" };
  if (c.daysUntil === -1) return { text: "Yesterday", tone: "past" };
  if (c.daysUntil < 0) return { text: dayjs(c.date).format("D MMM"), tone: "past" };
  if (c.daysUntil <= 30) return { text: `In ${c.daysUntil} days`, tone: "soon" };
  return { text: dayjs(c.date).format("D MMM"), tone: "future" };
}

export default function CelebrationsPage() {
  const thisYear = dayjs().year();
  const [year, setYear] = useState(thisYear);
  const [type, setType] = useState<"ALL" | "BIRTHDAY" | "ANNIVERSARY">("ALL");
  const [month, setMonth] = useState<number | "ALL">("ALL");
  const [q, setQ] = useState("");

  /*
    Keyed on the year, so switching years is a fetch rather than a refilter --
    the server holds the dates and deriving another year's occurrences in the
    browser would mean reimplementing the 29 February rule and the "not yet a
    year" rule in a second place.

    Under the "dashboard" prefix so a CELEBRATION notification invalidates it
    along with the dashboard card. React Query matches keys by prefix on the
    array, so a key of its own would never be reached by that.
  */
  const query = useQuery({
    queryKey: ["dashboard", "celebrations", "year", year],
    queryFn: async () =>
      (await api.get<ApiEnvelope<Celebration[]>>(
        `/dashboard/celebrations/year/${year}`
      )).data.data ?? []
  });

  const rows = useMemo(() => {
    let list = query.data ?? [];
    if (type !== "ALL") list = list.filter((c) => c.type === type);
    if (month !== "ALL") list = list.filter((c) => dayjs(c.date).month() === month);
    const needle = q.trim().toLowerCase();
    if (needle) {
      list = list.filter((c) =>
        [c.name, c.employeeCode, c.team].filter(Boolean).join(" ")
          .toLowerCase().includes(needle));
    }
    return list;
  }, [query.data, type, month, q]);

  const paged = usePagedRows(rows, 20, [year, type, month, q, query.data]);

  const all = query.data ?? [];
  const birthdays = all.filter((c) => c.type === "BIRTHDAY").length;
  const anniversaries = all.filter((c) => c.type === "ANNIVERSARY").length;
  // Only meaningful for the current year: "still to come" in a year that has
  // finished, or one that has not started, is not a number anybody wants.
  const upcoming = year === thisYear ? all.filter((c) => c.daysUntil >= 0).length : null;

  // Five years back and one forward: far enough to check a past year against
  // the records, and next year is the only future one anybody plans for.
  const years = Array.from({ length: 7 }, (_, i) => thisYear + 1 - i);

  return (
    <div className="p-4 sm:p-6">
      <PageHeader
        title="Birthdays & Anniversaries"
        subtitle="Every birthday and work anniversary in the year, in date order."
      />

      <div className="mb-4 grid gap-3 sm:grid-cols-3">
        <SummaryTile
          icon={Cake} label="Birthdays" value={birthdays}
          className="border-red-200 bg-red-50 dark:border-red-900/60 dark:bg-red-950/30"
        />
        <SummaryTile
          icon={PartyPopper} label="Work anniversaries" value={anniversaries}
          className="border-amber-200 bg-amber-50 dark:border-amber-900/60 dark:bg-amber-950/30"
        />
        <SummaryTile
          icon={CalendarDays}
          label={upcoming === null ? `Dates in ${year}` : "Still to come"}
          value={upcoming === null ? all.length : upcoming}
          className="border-violet-200 bg-violet-50 dark:border-violet-900/60 dark:bg-violet-950/30"
        />
      </div>

      <div className="mb-4 flex flex-wrap items-end gap-3">
        <Filter label="Year">
          <Select
            className="w-28"
            value={String(year)}
            onChange={(e) => setYear(Number(e.target.value))}
          >
            {years.map((y) => <option key={y} value={y}>{y}</option>)}
          </Select>
        </Filter>

        <Filter label="Month">
          <Select
            className="w-36"
            value={month === "ALL" ? "ALL" : String(month)}
            onChange={(e) =>
              setMonth(e.target.value === "ALL" ? "ALL" : Number(e.target.value))}
          >
            <option value="ALL">All months</option>
            {MONTHS.map((m, i) => <option key={m} value={i}>{m}</option>)}
          </Select>
        </Filter>

        <Filter label="Type">
          <div className="inline-flex rounded-lg border bg-card p-1">
            {(["ALL", "BIRTHDAY", "ANNIVERSARY"] as const).map((t) => (
              <button
                key={t}
                type="button"
                onClick={() => setType(t)}
                className={cn(
                  "rounded-md px-3 py-1 text-xs font-semibold transition-colors",
                  type === t
                    ? "bg-primary text-primary-foreground"
                    : "text-muted-foreground hover:text-foreground"
                )}
              >
                {t === "ALL" ? "All" : t === "BIRTHDAY" ? "Birthdays" : "Anniversaries"}
              </button>
            ))}
          </div>
        </Filter>

        <Filter label="Search">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="w-64 pl-9"
              placeholder="Name, employee ID or team…"
              value={q}
              onChange={(e) => setQ(e.target.value)}
            />
          </div>
        </Filter>

        {(month !== "ALL" || type !== "ALL" || q.trim() || year !== thisYear) && (
          <Button
            variant="outline"
            onClick={() => { setMonth("ALL"); setType("ALL"); setQ(""); setYear(thisYear); }}
          >
            Reset
          </Button>
        )}

        <span className="ml-auto text-xs text-muted-foreground">
          {rows.length} of {all.length} date{all.length === 1 ? "" : "s"}
        </span>
      </div>

      <Card>
        <CardContent className="p-0">
          {query.isLoading ? (
            <PageLoader />
          ) : rows.length === 0 ? (
            <EmptyState
              icon={Cake}
              title="Nothing in this view"
              description={
                all.length === 0
                  ? `No birthdays or anniversaries recorded for ${year}.`
                  : "No dates match the filters above."
              }
            />
          ) : (
            <>
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="w-[90px]">Date</TableHead>
                      <TableHead>Employee</TableHead>
                      <TableHead className="w-[130px]">Employee ID</TableHead>
                      <TableHead>Team</TableHead>
                      <TableHead className="w-[150px]">Occasion</TableHead>
                      <TableHead className="w-[120px]">When</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {paged.pageRows.map((c) => {
                      const isBirthday = c.type === "BIRTHDAY";
                      const when = whenLabel(c);
                      return (
                        <TableRow
                          key={`${c.userId}-${c.type}`}
                          /* Today gets a tint, so the row that needs a message
                             now is not read off the date column. */
                          className={cn(
                            when.tone === "today" &&
                              "bg-primary/5 ring-1 ring-inset ring-primary/30",
                            when.tone === "past" && "opacity-60"
                          )}
                        >
                          <TableCell className="whitespace-nowrap font-semibold tabular-nums">
                            {dayjs(c.date).format("DD MMM")}
                          </TableCell>
                          <TableCell>
                            <div className="flex items-center gap-2">
                              <Avatar name={c.name} src={c.photoPath} className="h-7 w-7" />
                              <span className="font-medium">{c.name}</span>
                            </div>
                          </TableCell>
                          <TableCell className="text-muted-foreground">
                            {c.employeeCode || "—"}
                          </TableCell>
                          <TableCell className="text-muted-foreground">
                            {c.team || "—"}
                          </TableCell>
                          <TableCell>
                            <span className={cn(
                              "inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-semibold",
                              isBirthday
                                ? "bg-red-500/15 text-red-700 dark:text-red-300"
                                : "bg-amber-500/20 text-amber-800 dark:text-amber-300"
                            )}>
                              {isBirthday
                                ? <><Cake className="h-3 w-3" /> Birthday</>
                                : <><PartyPopper className="h-3 w-3" /> {c.years} year{c.years === 1 ? "" : "s"}</>}
                            </span>
                          </TableCell>
                          <TableCell>
                            <span className={cn(
                              "text-xs font-semibold",
                              when.tone === "today" && "text-primary",
                              when.tone === "soon" && "text-emerald-600 dark:text-emerald-400",
                              when.tone === "past" && "text-muted-foreground",
                              when.tone === "future" && "text-muted-foreground"
                            )}>
                              {when.text}
                            </span>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </div>
              <TablePagination
                page={paged.page}
                totalPages={paged.totalPages}
                onChange={paged.setPage}
                pageSize={paged.pageSize}
                onPageSizeChange={paged.setPageSize}
                total={paged.total}
              />
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function Filter({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <label className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
        {label}
      </label>
      {children}
    </div>
  );
}

function SummaryTile({
  icon: Icon, label, value, className
}: {
  icon: typeof Cake; label: string; value: number; className?: string;
}) {
  return (
    <div className={cn("flex items-center gap-3 rounded-lg border p-3", className)}>
      <Icon className="h-5 w-5 shrink-0 text-foreground/70" />
      <div>
        <div className="text-xl font-bold leading-none tabular-nums">{value}</div>
        <div className="mt-1 text-[11px] font-medium text-muted-foreground">{label}</div>
      </div>
    </div>
  );
}
