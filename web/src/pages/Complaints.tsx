import { CustomLoader as Loader2 } from "@/components/ui/custom-loader";
import { useState, useMemo, type ReactNode } from "react";
import { useQuery, useMutation, useQueryClient, keepPreviousData } from "@tanstack/react-query";
import { Plus, Inbox, ChevronLeft, ChevronRight, Send,
  Clock, CheckCircle, XCircle
} from "lucide-react";
import toast from "react-hot-toast";
import { api, apiMessage } from "@/lib/api";
import { PageHeader } from "@/components/PageHeader";
import { EmptyState } from "@/components/EmptyState";
import { PageLoader } from "@/components/ui/page-loader";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Label } from "@/components/ui/label";
import { Select } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import { Dialog, DialogHeader } from "@/components/ui/dialog";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow
} from "@/components/ui/table";
import { useAuth } from "@/hooks/useAuth";
import { usePagedRows, TablePagination } from "@/components/ui/table-pagination";
import { StatTile, TILE_FILLS } from "@/components/ui/stat-tile";
import type { ApiEnvelope, PageEnvelope, ComplaintNeed } from "@/types";
import dayjs from "dayjs";

const MONTHS = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December"
];

function statusVariant(status: string): "default" | "secondary" | "destructive" | "outline" {
  switch (status) {
    case "RESOLVED":
      return "default";
    case "REJECTED":
      return "destructive";
    case "IN_REVIEW":
      return "secondary";
    default:
      return "outline";
  }
}

/** A complaint moves forward through these, never back. */
const STATUS_FLOW = [
  { value: "OPEN", label: "Open" },
  { value: "IN_REVIEW", label: "In review" },
  { value: "RESOLVED", label: "Resolved" },
  { value: "REJECTED", label: "Rejected" }
];

const NEXT_STATUS: Record<string, string[]> = {
  OPEN: ["OPEN", "IN_REVIEW"],
  IN_REVIEW: ["IN_REVIEW", "RESOLVED", "REJECTED"],
  RESOLVED: ["RESOLVED"],
  REJECTED: ["REJECTED"]
};

const STEP_HINT: Record<string, string> = {
  OPEN: "Take it into review, or settle it now as resolved or rejected.",
  IN_REVIEW: "In review — settle it as resolved or rejected. It cannot go back to open."
};

function priorityVariant(p: string) {
  switch (p) {
    case "HIGH":
      return "destructive" as const;
    case "MEDIUM":
      return "warning" as const;
    default:
      return "secondary" as const;
  }
}

export default function ComplaintsPage() {
  const { hasPermission } = useAuth();
  // HR reviews through COMPLAINT_MANAGE; admins through USER_MANAGE.
  const canReview = hasPermission("USER_MANAGE", "COMPLAINT_MANAGE");
  const [showSubmit, setShowSubmit] = useState(false);

  return (
    <div>
      <PageHeader
        title="Complaints"
        subtitle={
          canReview
            ? "Review employee complaints and respond."
            : "Raise a complaint. HR and admin will review and respond."
        }
        // Reviewers (HR / Admin) only respond — they don't submit.
        actions={
          !canReview && (
            <Button onClick={() => setShowSubmit(true)}>
              <Plus className="mr-2 h-4 w-4" /> New Submission
            </Button>
          )
        }
      />

      {canReview ? <AllComplaints /> : <MySubmissions />}

      {showSubmit && <SubmitDialog onClose={() => setShowSubmit(false)} />}
    </div>
  );
}

/** The complaint lifecycle as tiles, in the order one moves through it. */
const COMPLAINT_TILES = [
  { key: "ALL", label: "All", icon: Inbox, fill: TILE_FILLS.violet, hint: "Every complaint in this period" },
  { key: "OPEN", label: "Open", icon: Inbox, fill: TILE_FILLS.blue, hint: "Raised, not looked at yet" },
  { key: "IN_REVIEW", label: "In review", icon: Clock, fill: TILE_FILLS.amber, hint: "Being looked into" },
  { key: "RESOLVED", label: "Resolved", icon: CheckCircle, fill: TILE_FILLS.green, hint: "Dealt with" },
  { key: "REJECTED", label: "Rejected", icon: XCircle, fill: TILE_FILLS.red, hint: "Turned down" }
] as const;

/**
 * The employee's / Team Leader's own complaints. Fetched in one go and filtered
 * here, so the tiles, the date pickers and the paging all agree with each other.
 */
function MySubmissions() {
  const [page, setPage] = useState(0);
  const [statusTab, setStatusTab] = useState("ALL");
  const [year, setYear] = useState("all");
  const [month, setMonth] = useState("all");
  const [day, setDay] = useState("");

  const query = useQuery({
    queryKey: ["complaints", "mine"],
    placeholderData: keepPreviousData,
    queryFn: async () => {
      const res = await api.get<ApiEnvelope<PageEnvelope<ComplaintNeed>>>(
        "/complaints/mine?page=0&size=500"
      );
      return res.data.data;
    }
  });

  const all = query.data?.content ?? [];

  const years = useMemo(() => {
    const set = new Set<string>();
    all.forEach((c) => { if (c.createdAt) set.add(dayjs(c.createdAt).format("YYYY")); });
    return [...set].sort((a, b) => b.localeCompare(a));
  }, [all]);

  // Date-filtered but status-agnostic, so the tiles keep counting the period.
  const inPeriod = useMemo(() => all.filter((c) => {
    if (!c.createdAt) return year === "all" && month === "all" && !day;
    const d = dayjs(c.createdAt);
    // An exact date wins over the year/month pickers.
    if (day) return d.format("YYYY-MM-DD") === day;
    if (year !== "all" && d.format("YYYY") !== year) return false;
    if (month !== "all" && d.format("MM") !== month) return false;
    return true;
  }), [all, year, month, day]);

  const counts = useMemo(() => {
    const c: Record<string, number> = { ALL: inPeriod.length, OPEN: 0, IN_REVIEW: 0, RESOLVED: 0, REJECTED: 0 };
    inPeriod.forEach((r) => { if (r.status in c) c[r.status] += 1; });
    return c;
  }, [inPeriod]);

  const filtered = useMemo(() => {
    const list = statusTab === "ALL" ? inPeriod : inPeriod.filter((c) => c.status === statusTab);
    return [...list].sort((a, b) =>
      String(b.createdAt ?? "").localeCompare(String(a.createdAt ?? "")));
  }, [inPeriod, statusTab]);

  // The shared hook, so this table gains the page numbers and the
  // rows-per-page choice every other table has.
  const paged = usePagedRows(filtered, 10, [statusTab, year, month, day, inPeriod]);
  const rows = paged.pageRows;
  const filtersOn = year !== "all" || month !== "all" || !!day || statusTab !== "ALL";

  const filterBar = (
    <>
      <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-3 lg:grid-cols-5">
        {COMPLAINT_TILES.map((t) => (
          <StatTile
            key={t.key}
            compact
            label={t.label}
            value={counts[t.key] ?? 0}
            hint={t.hint}
            icon={t.icon}
            fill={t.fill}
            active={statusTab === t.key}
            onClick={() => { setStatusTab(t.key); setPage(0); }}
          />
        ))}
      </div>

      <div className="flex flex-wrap items-end gap-3">
        <div className="space-y-1">
          <label className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Year</label>
          <Select value={year} onChange={(e) => { setYear(e.target.value); setDay(""); setPage(0); }} className="w-28">
            <option value="all">All years</option>
            {years.map((y) => <option key={y} value={y}>{y}</option>)}
          </Select>
        </div>
        <div className="space-y-1">
          <label className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Month</label>
          <Select value={month} onChange={(e) => { setMonth(e.target.value); setDay(""); setPage(0); }} className="w-36">
            <option value="all">All months</option>
            {MONTHS.map((m, i) => (
              <option key={m} value={String(i + 1).padStart(2, "0")}>{m}</option>
            ))}
          </Select>
        </div>
        <div className="space-y-1">
          <label className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Exact date</label>
          <Input type="date" value={day} onChange={(e) => { setDay(e.target.value); setPage(0); }} className="w-40" />
        </div>
        {filtersOn && (
          <Button
            variant="outline"
            onClick={() => { setYear("all"); setMonth("all"); setDay(""); setStatusTab("ALL"); setPage(0); }}
          >
            Reset
          </Button>
        )}
        <span className="ml-auto text-xs text-muted-foreground">
          {filtered.length} of {all.length} complaint{all.length === 1 ? "" : "s"}
        </span>
      </div>
    </>
  );

  if (query.isLoading) return <PageLoader text="Loading complaints data..." />;

  if (rows.length === 0) {
    return (
      <div className="space-y-3">
        {/* The tiles stay on screen with nothing submitted — they read zero, which
            is the answer, and hiding them made the page look broken. */}
        {filterBar}
        <EmptyState
          icon={Inbox}
          title={filtersOn ? "Nothing matches these filters" : "Nothing submitted yet"}
          description={filtersOn
            ? "Try another status tile, or widen the date range above."
            : "Use “New Submission” to raise a complaint."}
        />
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {filterBar}
      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="pl-6">Reference</TableHead>
                <TableHead>Subject</TableHead>
                <TableHead>Category</TableHead>
                <TableHead>Priority</TableHead>
                <TableHead>Sent to</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="pr-6">Response</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((c) => (
                <TableRow key={c.id}>
                  <TableCell className="pl-6 font-medium code-chip">{c.referenceCode}</TableCell>
                  <TableCell className="max-w-[240px]">
                    <div className="font-medium">{c.subject}</div>
                    {c.description && (
                      <div className="line-clamp-1 text-xs text-muted-foreground">{c.description}</div>
                    )}
                  </TableCell>
                  <TableCell>{c.category || "—"}</TableCell>
                  <TableCell>
                    <Badge variant={priorityVariant(c.priority)}>{c.priority}</Badge>
                  </TableCell>
                  <TableCell className="whitespace-nowrap text-sm">
                    {c.requestedToName || <span className="text-muted-foreground">Any HR</span>}
                  </TableCell>
                  <TableCell>
                    <Badge variant={statusVariant(c.status)}>{c.status.replace("_", " ")}</Badge>
                  </TableCell>
                  <TableCell className="max-w-[220px] pr-6 text-xs text-muted-foreground">
                    {c.hrResponse ? (
                      <span className="line-clamp-2" title={c.hrResponse}>{c.hrResponse}</span>
                    ) : (
                      "—"
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
      <div className="overflow-hidden rounded-lg border">
        <TablePagination
          page={paged.page} totalPages={paged.totalPages} onChange={paged.setPage}
          pageSize={paged.pageSize} onPageSizeChange={paged.setPageSize}
          total={paged.total}
          always
        />
      </div>
    </div>
  );
}

/** Red asterisk shown on every field that must be filled. */
function Req() {
  return <span className="ml-0.5 text-destructive">*</span>;
}

/** HR / Admin view: incoming complaints as a data table with a Respond action. */
function AllComplaints() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState("");
  const [viewScope, setViewScope] = useState<"ALL" | "ASSIGNED" | "MY">("ALL");
  const [searchQuery, setSearchQuery] = useState("");
  const [teamFilter, setTeamFilter] = useState("all");
  const [year, setYear] = useState("all");
  const [month, setMonth] = useState("all");
  const [exactDate, setExactDate] = useState("");
  const [respondTo, setRespondTo] = useState<ComplaintNeed | null>(null);
  const [pageSize, setPageSize] = useState(10);

  const countsQuery = useQuery({
    queryKey: ["complaints", "counts"],
    queryFn: async () => {
      const res = await api.get<ApiEnvelope<PageEnvelope<ComplaintNeed>>>(
        "/complaints?kind=COMPLAINT&size=500"
      );
      return res.data.data.content ?? [];
    }
  });

  const rawComplaints = countsQuery.data ?? [];

  // Apply scope filtering (All vs Assigned to Me vs My Submissions)
  const scopedComplaints = useMemo(() => {
    return rawComplaints.filter((c) => {
      if (viewScope === "ASSIGNED") {
        return c.requestedTo === user?.id || (c.requestedToName || "").toLowerCase().includes((user?.name || "").toLowerCase());
      }
      if (viewScope === "MY") {
        return c.raisedBy === user?.id || c.userId === user?.id;
      }
      return true;
    });
  }, [rawComplaints, viewScope, user]);

  const years = useMemo(() => {
    const set = new Set<string>();
    scopedComplaints.forEach((c) => { if (c.createdAt) set.add(dayjs(c.createdAt).format("YYYY")); });
    return [...set].sort((a, b) => b.localeCompare(a));
  }, [scopedComplaints]);

  const teams = useMemo(() => {
    const set = new Set<string>();
    scopedComplaints.forEach((c) => { if (c.team) set.add(c.team.trim()); });
    return [...set].sort();
  }, [scopedComplaints]);

  // Date, team & search filtered complaints
  const filteredComplaints = useMemo(() => {
    return scopedComplaints.filter((c) => {
      if (status && c.status !== status) return false;
      if (teamFilter !== "all" && (c.team || "").trim() !== teamFilter) return false;
      if (exactDate) {
        if (!c.createdAt || dayjs(c.createdAt).format("YYYY-MM-DD") !== exactDate) return false;
      } else {
        if (year !== "all" && c.createdAt && dayjs(c.createdAt).format("YYYY") !== year) return false;
        if (month !== "all" && c.createdAt && dayjs(c.createdAt).format("MM") !== month) return false;
      }
      if (searchQuery.trim()) {
        const q = searchQuery.trim().toLowerCase();
        const haystack = [c.referenceCode, c.subject, c.description, c.raisedByName, c.raisedByCode, c.team, c.category].filter(Boolean).join(" ").toLowerCase();
        if (!haystack.includes(q)) return false;
      }
      return true;
    });
  }, [scopedComplaints, status, teamFilter, exactDate, year, month, searchQuery]);

  const paged = usePagedRows(filteredComplaints, pageSize, [status, viewScope, searchQuery, teamFilter, year, month, exactDate, scopedComplaints]);

  const openCount = scopedComplaints.filter((c) => c.status === "OPEN").length;
  const reviewCount = scopedComplaints.filter((c) => c.status === "IN_REVIEW").length;
  const resolvedCount = scopedComplaints.filter((c) => c.status === "RESOLVED").length;
  const rejectedCount = scopedComplaints.filter((c) => c.status === "REJECTED").length;

  const exportExcel = async () => {
    const XLSX = await import("xlsx");
    const headers = ["Ref Code", "Employee ID", "Employee Name", "Team", "Subject", "Category", "Priority", "Requested To", "Status", "Created Date", "Response"];
    const body = filteredComplaints.map((c) => [
      c.referenceCode,
      c.raisedByCode || c.employeeCode || "—",
      c.raisedByName || c.employeeName || "—",
      c.team || "—",
      c.subject,
      c.category || "—",
      c.priority,
      c.requestedToName || "Any HR",
      c.status,
      c.createdAt ? dayjs(c.createdAt).format("DD MMM YYYY, hh:mm A") : "—",
      c.hrResponse || "—"
    ]);
    const ws = XLSX.utils.aoa_to_sheet([["Complaints Summary Report"], [`Exported on ${dayjs().format("DD MMM YYYY, h:mm A")}`], [], headers, ...body]);
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, "Complaints");
    XLSX.writeFile(wb, `Complaints_Report_${dayjs().format("YYYY-MM-DD")}.xlsx`);
    toast.success("Complaints report exported successfully");
  };

  return (
    <div className="space-y-4">
      {/* Top Scope View Toggle: All Complaints vs Assigned to Me vs My Submissions */}
      <div className="flex items-center gap-2 bg-muted/30 p-1.5 rounded-lg w-max border">
        <button
          onClick={() => { setViewScope("ALL"); setPage(0); }}
          className={`px-4 py-1.5 text-sm font-semibold rounded-md transition-colors ${
            viewScope === "ALL" ? "bg-card shadow-sm text-primary border" : "text-muted-foreground hover:text-foreground"
          }`}
        >
          All Complaints
        </button>
        <button
          onClick={() => { setViewScope("ASSIGNED"); setPage(0); }}
          className={`px-4 py-1.5 text-sm font-semibold rounded-md transition-colors ${
            viewScope === "ASSIGNED" ? "bg-card shadow-sm text-primary border" : "text-muted-foreground hover:text-foreground"
          }`}
        >
          Assigned to Me
        </button>
        <button
          onClick={() => { setViewScope("MY"); setPage(0); }}
          className={`px-4 py-1.5 text-sm font-semibold rounded-md transition-colors ${
            viewScope === "MY" ? "bg-card shadow-sm text-primary border" : "text-muted-foreground hover:text-foreground"
          }`}
        >
          My Submissions
        </button>
      </div>

      {/* Stat Tiles */}
      <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-3 lg:grid-cols-5">
        {COMPLAINT_TILES.map((t) => (
          <StatTile
            key={t.key}
            compact
            label={t.label}
            value={t.key === "ALL" ? scopedComplaints.length
              : t.key === "OPEN" ? openCount
                : t.key === "IN_REVIEW" ? reviewCount
                  : t.key === "RESOLVED" ? resolvedCount : rejectedCount}
            hint={t.hint}
            icon={t.icon}
            fill={t.fill}
            active={(status || "ALL") === t.key}
            onClick={() => { setStatus(t.key === "ALL" ? "" : t.key); setPage(0); }}
          />
        ))}
      </div>

      {/* Complete Filter Toolbar */}
      <div className="flex flex-wrap items-end gap-3 rounded-xl border bg-card p-4 shadow-sm">
        <div className="flex flex-col">
          <label className="mb-1 text-[10px] font-bold uppercase tracking-wider text-muted-foreground">Search</label>
          <Input
            placeholder="Search subject, employee, code…"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="h-9 w-52 bg-background"
          />
        </div>
        <div className="flex flex-col">
          <label className="mb-1 text-[10px] font-bold uppercase tracking-wider text-muted-foreground">Status</label>
          <select
            value={status}
            onChange={(e) => { setStatus(e.target.value); setPage(0); }}
            className="h-9 w-36 rounded-md border bg-background px-3 text-xs font-semibold"
          >
            <option value="">All statuses</option>
            <option value="OPEN">Open</option>
            <option value="IN_REVIEW">In review</option>
            <option value="RESOLVED">Resolved</option>
            <option value="REJECTED">Rejected</option>
          </select>
        </div>
        {teams.length > 0 && (
          <div className="flex flex-col">
            <label className="mb-1 text-[10px] font-bold uppercase tracking-wider text-muted-foreground">Team</label>
            <select
              value={teamFilter}
              onChange={(e) => setTeamFilter(e.target.value)}
              className="h-9 w-36 rounded-md border bg-background px-3 text-xs font-semibold"
            >
              <option value="all">All teams</option>
              {teams.map((t) => <option key={t} value={t}>{t}</option>)}
            </select>
          </div>
        )}
        <div className="flex flex-col">
          <label className="mb-1 text-[10px] font-bold uppercase tracking-wider text-muted-foreground">Year</label>
          <select
            value={year}
            onChange={(e) => { setYear(e.target.value); setExactDate(""); setPage(0); }}
            className="h-9 w-28 rounded-md border bg-background px-3 text-xs font-semibold"
          >
            <option value="all">All years</option>
            {years.map((y) => <option key={y} value={y}>{y}</option>)}
          </select>
        </div>
        <div className="flex flex-col">
          <label className="mb-1 text-[10px] font-bold uppercase tracking-wider text-muted-foreground">Month</label>
          <select
            value={month}
            onChange={(e) => { setMonth(e.target.value); setExactDate(""); setPage(0); }}
            className="h-9 w-32 rounded-md border bg-background px-3 text-xs font-semibold"
          >
            <option value="all">All months</option>
            {MONTHS.map((m, i) => (
              <option key={m} value={String(i + 1).padStart(2, "0")}>{m}</option>
            ))}
          </select>
        </div>
        <div className="flex flex-col">
          <label className="mb-1 text-[10px] font-bold uppercase tracking-wider text-muted-foreground">Exact Date</label>
          <Input type="date" value={exactDate} onChange={(e) => { setExactDate(e.target.value); setPage(0); }} className="h-9 w-36 bg-background text-xs" />
        </div>
        <div className="ml-auto flex items-center gap-2">
          <Button
            variant="default"
            className="h-9 font-semibold"
            onClick={exportExcel}
            disabled={filteredComplaints.length === 0}
          >
            Export Excel
          </Button>
        </div>
      </div>

      {paged.pageRows.length === 0 ? (
        <EmptyState
          icon={Inbox}
          title="No complaints found"
          description="Employee complaints will appear here."
        />
      ) : (
        <Card>
          <CardContent className="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="pl-6">Reference</TableHead>
                  <TableHead>Employee ID</TableHead>
                  <TableHead>Employee Name</TableHead>
                  <TableHead>Team</TableHead>
                  <TableHead>Subject</TableHead>
                  <TableHead>Category</TableHead>
                  <TableHead>Priority</TableHead>
                  <TableHead>Requested to</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Date</TableHead>
                  <TableHead className="text-right pr-6">Action</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {paged.pageRows.map((c) => (
                  <TableRow key={c.id}>
                    <TableCell className="pl-6 font-medium code-chip">{c.referenceCode}</TableCell>
                    <TableCell className="code-chip text-xs text-muted-foreground">{c.raisedByCode || c.employeeCode || "—"}</TableCell>
                    <TableCell className="font-medium">{c.raisedByName || c.employeeName || "—"}</TableCell>
                    <TableCell>
                      {c.team ? (
                        <Badge className="border-0 bg-indigo-100 text-indigo-700 dark:bg-indigo-500/20 dark:text-indigo-300">
                          {c.team}
                        </Badge>
                      ) : (
                        <span className="text-muted-foreground">—</span>
                      )}
                    </TableCell>
                    <TableCell className="max-w-[220px] truncate">{c.subject}</TableCell>
                    <TableCell>{c.category || "—"}</TableCell>
                    <TableCell>
                      <Badge variant={priorityVariant(c.priority)}>{c.priority}</Badge>
                    </TableCell>
                    <TableCell className="whitespace-nowrap text-sm">
                      {c.requestedToName || <span className="text-muted-foreground">Any HR</span>}
                    </TableCell>
                    <TableCell>
                      <Badge variant={statusVariant(c.status)}>{c.status.replace("_", " ")}</Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {c.createdAt ? dayjs(c.createdAt).format("DD MMM YYYY") : "—"}
                    </TableCell>
                    <TableCell className="text-right pr-6">
                      {c.status === "RESOLVED" || c.status === "REJECTED" ? (
                        <span className="whitespace-nowrap text-xs text-muted-foreground">
                          {c.status === "RESOLVED" ? "Answered" : "Closed"}
                        </span>
                      ) : (
                        <Button size="sm" onClick={() => setRespondTo(c)}>
                          <Send className="mr-2 h-4 w-4" /> Respond
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      <div className="overflow-hidden rounded-lg border">
        <TablePagination
          page={paged.page}
          totalPages={paged.totalPages}
          onChange={paged.setPage}
          pageSize={pageSize}
          onPageSizeChange={(n) => { setPageSize(n); setPage(0); }}
          total={paged.total}
          always
        />
      </div>

      {respondTo && (
        <RespondDialog
          complaint={respondTo}
          onClose={() => setRespondTo(null)}
          onDone={() => {
            queryClient.invalidateQueries({ queryKey: ["complaints"] });
            setRespondTo(null);
          }}
        />
      )}
    </div>
  );
}

function StatCard({
  label,
  value,
  hint,
  icon,
  accent,
  iconWrap
}: {
  label: string;
  value: number;
  hint: string;
  icon: React.ReactNode;
  accent: string;
  iconWrap: string;
}) {
  return (
    <Card className={`border-l-4 ${accent} shadow-sm`}>
      <CardContent className="flex items-center justify-between p-4">
        <div className="space-y-0.5">
          <span className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">
            {label}
          </span>
          <h3 className="text-2xl font-bold">{value}</h3>
          <p className="text-[10px] leading-tight text-muted-foreground">{hint}</p>
        </div>
        <div className={`rounded-full p-2 ${iconWrap}`}>{icon}</div>
      </CardContent>
    </Card>
  );
}

function SubmissionCard({ c }: { c: ComplaintNeed }) {
  return (
    <Card>
      <CardContent className="p-4">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <span className="code-chip text-xs text-muted-foreground">{c.referenceCode}</span>
              {c.category && <Badge variant="outline">{c.category}</Badge>}
              <Badge variant="outline">{c.priority}</Badge>
            </div>
            <h3 className="mt-2 font-medium">{c.subject}</h3>
            <p className="mt-1 whitespace-pre-wrap text-sm text-muted-foreground">
              {c.description}
            </p>
          </div>
          <Badge variant={statusVariant(c.status)}>{c.status.replace("_", " ")}</Badge>
        </div>

        {c.requestedToName && (
          <p className="mt-2 text-xs text-muted-foreground">
            Sent to <span className="font-medium text-foreground">{c.requestedToName}</span>
          </p>
        )}

        {c.hrResponse && (
          <div className="mt-3 rounded-md border bg-muted/40 px-3 py-2 text-sm">
            <div className="mb-0.5 text-xs font-medium text-muted-foreground">
              Response{c.handledByName ? ` · ${c.handledByName}` : ""}
            </div>
            <p className="whitespace-pre-wrap">{c.hrResponse}</p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function Pager({
  page,
  totalPages,
  onPage
}: {
  page: number;
  totalPages: number;
  onPage: (p: number) => void;
}) {
  if (totalPages <= 1) return null;
  return (
    <div className="flex items-center justify-end gap-2">
      <Button variant="outline" size="sm" disabled={page === 0} onClick={() => onPage(Math.max(0, page - 1))}>
        <ChevronLeft className="h-4 w-4" />
      </Button>
      <span className="text-sm">
        {page + 1} / {totalPages}
      </span>
      <Button
        variant="outline"
        size="sm"
        disabled={page + 1 >= totalPages}
        onClick={() => onPage(page + 1)}
      >
        <ChevronRight className="h-4 w-4" />
      </Button>
    </div>
  );
}

function SubmitDialog({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient();
  const { hasRole } = useAuth();
  const isTL = hasRole("IT_TL");
  const [category, setCategory] = useState("");
  const [subject, setSubject] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState("MEDIUM");
  const [requestedTo, setRequestedTo] = useState("");
  const [error, setError] = useState<string | null>(null);

  // HR and admins this complaint can be addressed to. Its own endpoint, because
  // the full user directory is not readable by an employee.
  const recipients = useQuery({
    queryKey: ["complaint-recipients"],
    queryFn: async () =>
      (await api.get<ApiEnvelope<{ id: number; name: string; code?: string; role?: string }[]>>(
        "/complaints/recipients"
      )).data.data
  });

  const filteredRecipients = useMemo(() => {
    let list = recipients.data ?? [];
    if (isTL || !hasRole("SUPER_ADMIN", "COMPANY_ADMIN", "IT_MGR", "IT_HR", "CV_HR")) {
      list = list.filter((u) => {
        const code = (u.code || "").toUpperCase();
        const role = (u.role || "").toUpperCase();
        const name = (u.name || "").toUpperCase();
        const isAdmin = code === "ADM0001" || code.startsWith("ADM") || name.includes("ADMIN") || role.includes("ADMIN");
        const isHR = code === "HR0001" || code.includes("HR") || role.includes("HR") || role.includes("MANAGER") || role.includes("HEAD");
        const isCTO = code === "PIX-E100" || role.includes("CTO");
        return isAdmin || isHR || isCTO;
      });
    }
    return list;
  }, [recipients.data, isTL, hasRole]);

  const mutation = useMutation({
    mutationFn: async () => {
      const res = await api.post<ApiEnvelope<ComplaintNeed>>("/complaints", {
        kind: "COMPLAINT",
        category: category.trim(),
        subject: subject.trim(),
        description: description.trim(),
        priority,
        requestedTo: requestedTo ? Number(requestedTo) : undefined
      });
      return res.data.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["complaints"] });
      toast.success("Submitted — the person you chose has been notified");
      onClose();
    },
    onError: (err) => setError(apiMessage(err, "Could not submit"))
  });

  function submit() {
    setError(null);
    if (!category.trim()) {
      setError("Please say what kind of complaint this is");
      return;
    }
    if (!subject.trim()) {
      setError("Please add a subject");
      return;
    }
    if (!description.trim()) {
      setError("Please describe your complaint");
      return;
    }
    if (!requestedTo) {
      setError("Please choose who this should go to");
      return;
    }
    mutation.mutate();
  }

  return (
    <Dialog open onClose={onClose}>
      <DialogHeader
        title="New Complaint"
        description="Tell HR What's Wrong."
      />

      {error && (
        <div className="mb-4 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </div>
      )}

      <div className="space-y-4">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div className="space-y-1">
            <Label htmlFor="cn-category">Complaint Type<Req /></Label>
            <Input
              id="cn-category"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              placeholder="like facilities,harassment"
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="cn-priority">Priority<Req /></Label>
            <Select id="cn-priority" value={priority} onChange={(e) => setPriority(e.target.value)}>
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
            </Select>
          </div>
        </div>

        <div className="space-y-1">
          <Label htmlFor="cn-to">Request to<Req /></Label>
          <Select id="cn-to" value={requestedTo} onChange={(e) => setRequestedTo(e.target.value)}>
            <option value="">
              {recipients.isLoading ? "Loading…"
                : filteredRecipients.length === 0 ? "Nobody available yet"
                  : "Select"}
            </option>
            {filteredRecipients.map((u) => (
              <option key={u.id} value={u.id}>
                {u.name}{u.role ? ` · ${u.role}` : ""}{u.code ? ` (${u.code})` : ""}
              </option>
            ))}
          </Select>
          <p className="text-[11px] text-muted-foreground">
            Only the person you choose is notified.
          </p>
        </div>

        <div className="space-y-1">
          <Label htmlFor="cn-subject">Subject<Req /></Label>
          <Input
            id="cn-subject"
            value={subject}
            onChange={(e) => setSubject(e.target.value)}
            placeholder="A short summary"
          />
        </div>

        <div className="space-y-1">
          <Label htmlFor="cn-desc">Details<Req /></Label>
          <Textarea
            id="cn-desc"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Describe the complaint…"
            className="min-h-[120px]"
          />
        </div>
      </div>

      <div className="mt-6 flex justify-end gap-2 border-t pt-4">
        <Button variant="ghost" onClick={onClose} disabled={mutation.isPending}>
          Cancel
        </Button>
        <Button onClick={submit} disabled={mutation.isPending}>
          {mutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
          {mutation.isPending ? "Submitting…" : "Submit"}
        </Button>
      </div>
    </Dialog>
  );
}

function RespondDialog({
  complaint,
  onClose,
  onDone
}: {
  complaint: ComplaintNeed;
  onClose: () => void;
  onDone: () => void;
}) {
  const [status, setStatus] = useState(complaint.status);
  const [response, setResponse] = useState(complaint.hrResponse ?? "");
  // Forward only: an open complaint goes to review, a reviewed one is resolved or
  // rejected, and a settled one stays settled.
  const settled = complaint.status === "RESOLVED" || complaint.status === "REJECTED";
  const allowedNext = NEXT_STATUS[complaint.status] ?? [complaint.status];
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: async () => {
      const res = await api.post<ApiEnvelope<ComplaintNeed>>(
        `/complaints/${complaint.id}/respond`,
        { status, response: response.trim() || undefined }
      );
      return res.data.data;
    },
    onSuccess: () => {
      toast.success("Updated — the employee has been notified");
      onDone();
    },
    onError: (err) => setError(apiMessage(err, "Could not update"))
  });

  return (
    <Dialog open onClose={onClose}>
      <DialogHeader
        title={`Respond · ${complaint.referenceCode}`}
        description={complaint.subject}
      />

      {error && (
        <div className="mb-4 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </div>
      )}

      <div className="space-y-4">
        <div className="rounded-md border bg-muted/40 px-3 py-2 text-sm">
          <div className="mb-0.5 text-xs font-medium text-muted-foreground">
            Complaint from {complaint.raisedByName}
            {complaint.raisedByCode ? ` (${complaint.raisedByCode})` : ""}
          </div>
          <p className="whitespace-pre-wrap">{complaint.description}</p>
        </div>

        <div className="space-y-1">
          <Label htmlFor="cn-status">Status</Label>
          {settled ? (
            <div className="flex items-center gap-2 rounded-md border bg-muted/40 px-3 py-2 text-sm">
              <Badge variant={statusVariant(complaint.status)}>
                {complaint.status.replace("_", " ")}
              </Badge>
              <span className="text-xs text-muted-foreground">
                Already {complaint.status.toLowerCase()} — you can still update the response.
              </span>
            </div>
          ) : (
            <>
              <Select id="cn-status" value={status} onChange={(e) => setStatus(e.target.value)}>
                {STATUS_FLOW.filter((o) => allowedNext.includes(o.value)).map((o) => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </Select>
              <p className="text-[11px] text-muted-foreground">
                {STEP_HINT[complaint.status] ?? "Move it one step at a time."}
              </p>
            </>
          )}
        </div>

        <div className="space-y-1">
          <Label htmlFor="cn-response">Response to employee</Label>
          <Textarea
            id="cn-response"
            value={response}
            onChange={(e) => setResponse(e.target.value)}
            placeholder="Optional message back to the employee…"
            className="min-h-[100px]"
          />
        </div>
      </div>

      <div className="mt-6 flex justify-end gap-2 border-t pt-4">
        <Button variant="ghost" onClick={onClose} disabled={mutation.isPending}>
          Cancel
        </Button>
        <Button onClick={() => mutation.mutate()} disabled={mutation.isPending}>
          {mutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
          {mutation.isPending ? "Saving…" : "Save response"}
        </Button>
      </div>
    </Dialog>
  );
}
