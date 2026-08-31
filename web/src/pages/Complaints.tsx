import { CustomLoader as Loader2 } from "@/components/ui/custom-loader";
import { useState, useMemo, type ReactNode } from "react";
import { useQuery, useMutation, useQueryClient, keepPreviousData } from "@tanstack/react-query";
import { Plus, Inbox, ChevronLeft, ChevronRight, Send,
  Clock, CheckCircle, XCircle, X
} from "lucide-react";
import toast from "react-hot-toast";
import { api, apiMessage } from "@/lib/api";
import { ViewButton } from "@/components/ui/view-button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { ExportExcelButton } from "@/components/ui/export-excel-button";
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
import { DATE_MIN, DATE_MAX } from "@/lib/dates";

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

/*
  Where a complaint may go next -- and never where it already is.

  Each step used to list itself, so the current one stayed selectable and
  "Save Response" could be pressed on a status that changes nothing. A step
  is a move forward or it is not offered.
*/
const NEXT_STATUS: Record<string, string[]> = {
  OPEN: ["IN_REVIEW", "RESOLVED", "REJECTED"],
  IN_REVIEW: ["RESOLVED", "REJECTED"],
  RESOLVED: [],
  REJECTED: []
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
  const { hasPermission, hasRole, user } = useAuth();
  // HR reviews through COMPLAINT_MANAGE; admins through USER_MANAGE.
  const canReview = hasPermission("USER_MANAGE", "COMPLAINT_MANAGE");
  /* The top of the chain; PIX-E100 is the CTO. */
  const isSystemAdminOrCto =
    hasRole("SUPER_ADMIN") || hasRole("COMPANY_ADMIN") ||
    user?.employeeCode?.toUpperCase() === "PIX-E100";
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
        /*
          Reviewing complaints and having one are different things.

          The button was hidden from reviewers on the reasoning that HR
          responds rather than submits. But HR is an employee too, and the one
          complaint they cannot raise anywhere else is the one about their own
          situation -- which is exactly the case the recipient list below is
          for, since it lets them address it upward.
        */
        actions={
          /*
            The CTO and the system administrators receive complaints rather
            than raise them: every recipient dropdown offers them, and there is
            nobody above them to address one to. HR keeps the button, because
            HR can address theirs to the CTO.
          */
          !isSystemAdminOrCto && (
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
  const [statusTab, setStatusTab] = useState("ALL");
  const [year, setYear] = useState("all");
  const [month, setMonth] = useState("all");
  const [day, setDay] = useState("");
  /** The complaint open for reading, or null. */
  const [viewRow, setViewRow] = useState<ComplaintNeed | null>(null);
  /** The complaint the cancel confirmation is asking about, or null. */
  const [confirmCancel, setConfirmCancel] = useState<ComplaintNeed | null>(null);

  const mineQc = useQueryClient();

  /*
    Withdrawing a complaint raised by mistake.

    A cancel, not a delete: the row stays and its status becomes CANCELLED, so
    a reviewer who has already seen it finds out what became of it rather than
    finding it gone. The server allows it only to the raiser and only while it
    is still open.
  */
  const cancelComplaint = useMutation({
    mutationFn: async (id: number) => { await api.post(`/complaints/${id}/cancel`); },
    onSuccess: () => {
      mineQc.invalidateQueries({ queryKey: ["complaints"] });
      setConfirmCancel(null);
      toast.success("Complaint cancelled");
    },
    onError: (e) => toast.error(apiMessage(e, "Could not cancel that complaint")),
  });

  const query = useQuery({
    queryKey: ["complaints", "mine"],
    placeholderData: keepPreviousData,
    /*
      Kept live. A complaint arrives from somebody else's screen, so this
      list is stale the moment it loads. Refetched on an interval and
      whenever the tab is looked at again, which is when a stale count is
      actually noticed.
    */
    refetchInterval: 15000,
    refetchOnWindowFocus: true,
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

  const paged = usePagedRows(filtered, 10, [statusTab, year, month, day, inPeriod]);
  const rows = paged.pageRows;

  /** The complaints the filters leave, as a spreadsheet. */
  const exportComplaints = async () => {
    if (filtered.length === 0) { toast.error("Nothing to export."); return; }
    const XLSX = await import("xlsx");
    const headers = ["#", "Ticket ID", "Subject", "Category", "Priority",
                     "Sent to", "Status", "Response", "Raised on"];
    const body = filtered.map((c, i) => [
      i + 1,
      c.referenceCode ?? "",
      c.subject ?? "",
      c.category ?? "",
      c.priority ?? "",
      c.requestedToName || "HR & Admin",
      (c.status ?? "").replace("_", " "),
      c.hrResponse ?? "",
      c.createdAt ? dayjs(c.createdAt).format("DD MMM YYYY") : "",
    ]);
    const ws = XLSX.utils.aoa_to_sheet([headers, ...body]);
    ws["!cols"] = [{ wch: 5 }, { wch: 18 }, { wch: 34 }, { wch: 18 }, { wch: 10 },
                   { wch: 20 }, { wch: 14 }, { wch: 40 }, { wch: 14 }];
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, "Complaints");
    const tag = [year === "all" ? "" : year, month === "all" ? "" : month, day || ""]
      .filter(Boolean).join("_") || "All";
    XLSX.writeFile(wb, `My_Complaints_${tag}.xlsx`);
    toast.success(`Exported ${filtered.length} complaint${filtered.length === 1 ? "" : "s"}`);
  };
  const filtersOn = year !== "all" || month !== "all" || !!day || statusTab !== "ALL";

  const filterBar = (
    <>
      <div className="mb-4 grid grid-cols-2 gap-2.5 sm:grid-cols-3 lg:grid-cols-5">
        {COMPLAINT_TILES.map((t) => (
          <StatTile
            key={t.key}
            label={t.label}
            value={counts[t.key] ?? 0}
            hint={t.hint}
            icon={t.icon}
            fill={t.fill}
            active={statusTab === t.key}
            onClick={() => { setStatusTab(t.key); paged.setPage(0); }}
          />
        ))}
      </div>

      <div className="flex flex-wrap items-end gap-3">
        <div className="space-y-1">
          <label className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Year</label>
          <Select value={year} onChange={(e) => { setYear(e.target.value); setDay(""); paged.setPage(0); }} className="w-28">
            <option value="all">All years</option>
            {years.map((y) => <option key={y} value={y}>{y}</option>)}
          </Select>
        </div>
        <div className="space-y-1">
          <label className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Month</label>
          <Select value={month} onChange={(e) => { setMonth(e.target.value); setDay(""); paged.setPage(0); }} className="w-36">
            <option value="all">All months</option>
            {MONTHS.map((m, i) => (
              <option key={m} value={String(i + 1).padStart(2, "0")}>{m}</option>
            ))}
          </Select>
        </div>
        <div className="space-y-1">
          <label className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Date</label>
          <Input type="date" min={DATE_MIN} max={DATE_MAX} value={day} onChange={(e) => { setDay(e.target.value); paged.setPage(0); }} className="w-40" />
        </div>
        {filtersOn && (
          <Button
            variant="outline"
            onClick={() => { setYear("all"); setMonth("all"); setDay(""); setStatusTab("ALL"); paged.setPage(0); }}
          >
            Reset
          </Button>
        )}
        <span className="ml-auto text-xs text-muted-foreground">
          {filtered.length} of {all.length} complaint{all.length === 1 ? "" : "s"}
        </span>
        {/* Exports what the filters leave, so the file matches the page. */}
        <ExportExcelButton
          disabled={filtered.length === 0}
          title={filtered.length ? "Download these complaints as a spreadsheet" : "Nothing to export"}
          onClick={exportComplaints}
        />
      </div>
    </>
  );

  if (query.isLoading) return <PageLoader text="Loading complaints data..." />;

  if (rows.length === 0) {
    return (
      <div className="space-y-3">
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
                <TableHead className="w-px whitespace-nowrap pl-6">Action</TableHead>
                <TableHead>Ticket ID</TableHead>
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
                  <TableCell className="w-px whitespace-nowrap py-1 pl-6">
                    <div className="flex items-center gap-1">
                      <ViewButton onClick={() => setViewRow(c)} />
                      {/* Once HR has taken it into review the handling is
                          theirs, so withdrawing stops there. */}
                      {c.status === "OPEN" && (
                        <Button variant="outline" size="sm" className="shrink-0"
                          disabled={cancelComplaint.isPending}
                          onClick={() => setConfirmCancel(c)}>
                          <X className="mr-1 h-3.5 w-3.5" /> Cancel
                        </Button>
                      )}
                    </div>
                  </TableCell>
                  <TableCell className="font-medium code-chip">{c.referenceCode}</TableCell>
                  <TableCell className="max-w-[240px]">
                    <div className="font-medium">{c.subject}</div>
                    {c.description && (
                      <div className="line-clamp-1 text-xs text-muted-foreground">{c.description}</div>
                    )}
                  </TableCell>
                  <TableCell>{c.category}</TableCell>
                  <TableCell>
                    <Badge variant={priorityVariant(c.priority)}>{c.priority}</Badge>
                  </TableCell>
                  <TableCell>
                    <div>{c.requestedToName || "HR & Admin"}</div>
                    {c.handledByName && (
                      <div className="text-xs text-muted-foreground">
                        Answered by {c.handledByName}
                      </div>
                    )}
                  </TableCell>
                  <TableCell>
                    <Badge variant={statusVariant(c.status)}>{c.status.replace("_", " ")}</Badge>
                  </TableCell>
                  <TableCell className="pr-6 max-w-[260px]">
                    {c.hrResponse ? (
                      <div>
                        <div className="text-xs font-medium">{c.hrResponse}</div>
                        {c.resolvedAt && (
                          <div className="text-[10px] text-muted-foreground">
                            {dayjs(c.resolvedAt).format("DD MMM YYYY, HH:mm")}
                          </div>
                        )}
                      </div>
                    ) : (
                      <span className="text-xs text-muted-foreground italic">Awaiting review</span>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <TablePagination
            page={paged.page}
            totalPages={paged.totalPages}
            onChange={paged.setPage}
            pageSize={paged.pageSize}
            onPageSizeChange={paged.setPageSize}
            total={paged.total}
          />
        </CardContent>
      </Card>

      {/* Reading your own complaint. The same dialog HR uses, in the read-only
          mode it already supports -- there is nothing here to decide. */}
      {viewRow && (
        <RespondDialog
          complaint={viewRow}
          readOnly
          onClose={() => setViewRow(null)}
          onSaved={() => setViewRow(null)}
        />
      )}

      {/* Cancelling cannot be undone, so it is asked in the application's own
          dialog with the complaint named in it. */}
      <ConfirmDialog
        open={!!confirmCancel}
        title="Cancel this complaint?"
        description="The complaint is withdrawn and nobody is asked to review it. This cannot be undone -- raising it again means a new submission."
        detail={confirmCancel ? [
          ["Ticket", confirmCancel.referenceCode || "—"],
          ["Subject", confirmCancel.subject || "—"],
        ] : undefined}
        confirmLabel="Yes, cancel it"
        cancelLabel="No, keep it"
        busy={cancelComplaint.isPending}
        onCancel={() => setConfirmCancel(null)}
        onConfirm={() => { if (confirmCancel?.id) cancelComplaint.mutate(confirmCancel.id); }}
      />
    </div>
  );
}

/**
 * All complaints in the company — visible only to HR and Super Admin.
 * Supports status stepping and resolution response.
 */
function AllComplaints() {
  const qc = useQueryClient();
  const [statusTab, setStatusTab] = useState("ALL");
  const [year, setYear] = useState("all");
  const [month, setMonth] = useState("all");
  const [day, setDay] = useState("");
  const [actingOn, setActingOn] = useState<ComplaintNeed | null>(null);
  const { user: me } = useAuth();
  /*
    Whose complaints to show.

    A complaint names the person it was addressed to, and every reviewer saw
    all of them regardless -- so one sent to the CTO sat in a list beside every
    complaint sent to HR, with nothing to say which was which. The person
    actually asked had no way to find theirs.

    Defaults to what was addressed to me, because that is the work. The whole
    list stays one tap away for anyone overseeing the lot.
  */
  const [scope, setScope] = useState<"me" | "mine" | "all">("me");

  const query = useQuery({
    queryKey: ["complaints", "all"],
    placeholderData: keepPreviousData,
    /*
      Kept live. A complaint arrives from somebody else's screen, so this
      list is stale the moment it loads. Refetched on an interval and
      whenever the tab is looked at again, which is when a stale count is
      actually noticed.
    */
    refetchInterval: 15000,
    refetchOnWindowFocus: true,
    queryFn: async () => {
      const res = await api.get<ApiEnvelope<PageEnvelope<ComplaintNeed>>>(
        "/complaints?page=0&size=500"
      );
      return res.data.data;
    }
  });

  const everything = query.data?.content ?? [];
  /*
    Three different things, which were all one list before.

    A reviewer is also an employee. HR raising a complaint about their own
    situation is the case the recipient dropdown exists for -- and those
    belong with their other submissions, not in the queue of work waiting on
    them. Judging one's own complaint is not review, so those are read-only
    here and the decision is left to whoever it was actually addressed to.
  */
  const addressedToMe = everything.filter((c) => c.requestedTo === me?.id);
  const raisedByMe = everything.filter((c) => c.raisedBy === me?.id);
  const all =
    scope === "me" ? addressedToMe : scope === "mine" ? raisedByMe : everything;

  /*
    Who may actually decide a complaint: the person it was addressed to, and
    never its author, and not once it has been settled. A complaint that is
    resolved or rejected is finished -- reopening it by a second response
    would erase the answer the submitter has already been given.
  */
  const canDecide = (c: ComplaintNeed) =>
    c.requestedTo === me?.id &&
    c.raisedBy !== me?.id &&
    c.status !== "RESOLVED" &&
    c.status !== "REJECTED";

  const years = useMemo(() => {
    const set = new Set<string>();
    all.forEach((c) => { if (c.createdAt) set.add(dayjs(c.createdAt).format("YYYY")); });
    return [...set].sort((a, b) => b.localeCompare(a));
  }, [all]);

  const inPeriod = useMemo(() => all.filter((c) => {
    if (!c.createdAt) return year === "all" && month === "all" && !day;
    const d = dayjs(c.createdAt);
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

  const paged = usePagedRows(filtered, 10, [statusTab, year, month, day, inPeriod]);
  const rows = paged.pageRows;

  /*
    The complaints the filters leave, as a spreadsheet.

    Carries the columns HR sees and the employee's own export does not -- who
    raised it and who answered -- so the file matches this table rather than
    the other one.
  */
  const exportAllComplaints = async () => {
    if (filtered.length === 0) { toast.error("Nothing to export."); return; }
    const XLSX = await import("xlsx");
    const headers = ["#", "Ticket ID", "Raised by", "Employee ID", "Sent to", "Subject",
                     "Category", "Priority", "Status", "Response", "Answered by",
                     "Raised on", "Resolved on"];
    const body = filtered.map((c, i) => [
      i + 1,
      c.referenceCode ?? "",
      c.raisedByName ?? "",
      c.raisedByCode ?? "",
      c.requestedToName || "HR & Admin",
      c.subject ?? "",
      c.category ?? "",
      c.priority ?? "",
      (c.status ?? "").replace("_", " "),
      c.hrResponse ?? "",
      c.handledByName ?? "",
      c.createdAt ? dayjs(c.createdAt).format("DD MMM YYYY") : "",
      c.resolvedAt ? dayjs(c.resolvedAt).format("DD MMM YYYY") : "",
    ]);
    const ws = XLSX.utils.aoa_to_sheet([headers, ...body]);
    ws["!cols"] = [{ wch: 5 }, { wch: 18 }, { wch: 22 }, { wch: 13 }, { wch: 18 },
                   { wch: 34 }, { wch: 18 }, { wch: 10 }, { wch: 14 }, { wch: 40 },
                   { wch: 20 }, { wch: 14 }, { wch: 14 }];
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, "Complaints");
    const tag = [year === "all" ? "" : year, month === "all" ? "" : month, day || ""]
      .filter(Boolean).join("_") || "All";
    XLSX.writeFile(wb, `All_Complaints_${tag}.xlsx`);
    toast.success(`Exported ${filtered.length} complaint${filtered.length === 1 ? "" : "s"}`);
  };
  const filtersOn = year !== "all" || month !== "all" || !!day || statusTab !== "ALL";

  const filterBar = (
    <>
      <div className="mb-4 grid grid-cols-2 gap-2.5 sm:grid-cols-3 lg:grid-cols-5">
        {COMPLAINT_TILES.map((t) => (
          <StatTile
            key={t.key}
            label={t.label}
            value={counts[t.key] ?? 0}
            hint={t.hint}
            icon={t.icon}
            fill={t.fill}
            active={statusTab === t.key}
            onClick={() => { setStatusTab(t.key); paged.setPage(0); }}
          />
        ))}
      </div>

      {/*
        Mine, or everybody's.

        Shown as a count so the number of complaints actually waiting on this
        person is visible without switching to find out.
      */}
      <div className="flex gap-1 rounded-lg border bg-muted/60 p-1 w-fit">
        {([
          ["me", `Addressed to me (${addressedToMe.length})`],
          ["mine", `My requests (${raisedByMe.length})`],
          ["all", `All complaints (${everything.length})`],
        ] as const).map(([key, label]) => (
          <button
            key={key}
            type="button"
            onClick={() => { setScope(key); paged.setPage(0); }}
            className={
              "rounded-md px-3 py-1.5 text-sm font-medium transition-colors " +
              (scope === key
                ? "bg-background shadow-sm text-foreground"
                : "text-muted-foreground hover:text-foreground")
            }
          >
            {label}
          </button>
        ))}
      </div>

      <div className="flex flex-wrap items-end gap-3">
        <div className="space-y-1">
          <label className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Year</label>
          <Select value={year} onChange={(e) => { setYear(e.target.value); setDay(""); paged.setPage(0); }} className="w-28">
            <option value="all">All years</option>
            {years.map((y) => <option key={y} value={y}>{y}</option>)}
          </Select>
        </div>
        <div className="space-y-1">
          <label className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Month</label>
          <Select value={month} onChange={(e) => { setMonth(e.target.value); setDay(""); paged.setPage(0); }} className="w-36">
            <option value="all">All months</option>
            {MONTHS.map((m, i) => (
              <option key={m} value={String(i + 1).padStart(2, "0")}>{m}</option>
            ))}
          </Select>
        </div>
        <div className="space-y-1">
          <label className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Date</label>
          <Input type="date" min={DATE_MIN} max={DATE_MAX} value={day} onChange={(e) => { setDay(e.target.value); paged.setPage(0); }} className="w-40" />
        </div>
        {filtersOn && (
          <Button
            variant="outline"
            onClick={() => { setYear("all"); setMonth("all"); setDay(""); setStatusTab("ALL"); paged.setPage(0); }}
          >
            Reset
          </Button>
        )}
        <span className="ml-auto text-xs text-muted-foreground">
          {filtered.length} of {all.length} complaint{all.length === 1 ? "" : "s"}
        </span>
        {/* Exports what the filters leave, so the file matches the page. */}
        <ExportExcelButton
          disabled={filtered.length === 0}
          title={filtered.length ? "Download these complaints as a spreadsheet" : "Nothing to export"}
          onClick={exportAllComplaints}
        />
      </div>
    </>
  );

  if (query.isLoading) return <PageLoader text="Loading complaints queue..." />;

  return (
    <div className="space-y-3">
      {filterBar}
      {rows.length === 0 ? (
        <EmptyState
          icon={Inbox}
          title={filtersOn ? "Nothing matches these filters" : "No complaints to review"}
          description={filtersOn
            ? "Try another status tile, or widen the date range above."
            : "When employees submit complaints, they will appear here for review."}
        />
      ) : (
        <Card>
          <CardContent className="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="pr-6 text-right">Action</TableHead>
                  <TableHead className="pl-6">Reference</TableHead>
                  <TableHead>Raised by</TableHead>
                  <TableHead>Sent to</TableHead>
                  <TableHead>Subject</TableHead>
                  <TableHead>Category</TableHead>
                  <TableHead>Priority</TableHead>
                  <TableHead>Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((c) => (
                  <TableRow key={c.id}>
                    <TableCell className="pr-6 text-right">
                      {/*
                        Only the person it was addressed to decides it, and
                        only while it is still open for a decision. Everyone
                        else -- including whoever raised it -- reads it.
                      */}
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => setActingOn(c)}
                      >
                        {canDecide(c) ? "Respond" : "View"}
                      </Button>
                    </TableCell>
                    <TableCell className="pl-6 font-medium code-chip">{c.referenceCode}</TableCell>
                    <TableCell>
                      <div className="font-medium">{c.raisedByName || "Employee"}</div>
                      <div className="text-xs text-muted-foreground">{c.raisedByCode}</div>
                    </TableCell>
                    {/*
                      Who is expected to answer, and who did.

                      Three people can review, so a list that shows only who
                      raised a complaint leaves everyone guessing whose it is.
                      Once somebody has responded the handler is named too --
                      that is the answer to "who dealt with this".
                    */}
                    <TableCell>
                      <div className="font-medium">{c.requestedToName || "HR & Admin"}</div>
                      {c.handledByName && (
                        <div className="text-xs text-muted-foreground">
                          Answered by {c.handledByName}
                        </div>
                      )}
                    </TableCell>
                    <TableCell className="max-w-[240px]">
                      <div className="font-medium">{c.subject}</div>
                      {c.description && (
                        <div className="line-clamp-1 text-xs text-muted-foreground">{c.description}</div>
                      )}
                    </TableCell>
                    <TableCell>{c.category}</TableCell>
                    <TableCell>
                      <Badge variant={priorityVariant(c.priority)}>{c.priority}</Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant={statusVariant(c.status)}>{c.status.replace("_", " ")}</Badge>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <TablePagination
              page={paged.page}
              totalPages={paged.totalPages}
              onChange={paged.setPage}
              pageSize={paged.pageSize}
              onPageSizeChange={paged.setPageSize}
              total={paged.total}
            />
          </CardContent>
        </Card>
      )}

      {actingOn && (
        <RespondDialog
          complaint={actingOn}
          readOnly={!canDecide(actingOn)}
          onClose={() => setActingOn(null)}
          onSaved={() => {
            qc.invalidateQueries({ queryKey: ["complaints"] });
            setActingOn(null);
          }}
        />
      )}
    </div>
  );
}

/** Submit a new complaint modal for employees / TLs. */
function SubmitDialog({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient();
  const [subject, setSubject] = useState("");
  const [category, setCategory] = useState("WORKPLACE");
  const [priority, setPriority] = useState("MEDIUM");
  const [targetRoleId, setTargetRoleId] = useState("");
  const [description, setDescription] = useState("");
  /*
    The anonymous option is no longer offered, so this stays false. The field
    is still sent: the server and the existing complaints both understand it,
    and older anonymous complaints keep working exactly as they did. What was
    removed is the choice on this form, not the concept behind it.
  */
  const anonymous = false;

  const recs = useQuery({
    queryKey: ["complaints", "recipients"],
    queryFn: async () => {
      const res = await api.get<ApiEnvelope<Array<{ id: number; name: string; role: string }>>>(
        "/complaints/recipients"
      );
      return res.data.data;
    }
  });

  const selectedTargetRoleId = targetRoleId || (recs.data && recs.data.length > 0 ? String(recs.data[0].id) : "");

  const submit = useMutation({
    mutationFn: async () => {
      const body = {
        subject,
        category,
        priority,
        requestedTo: selectedTargetRoleId ? Number(selectedTargetRoleId) : undefined,
        description,
        isAnonymous: anonymous
      };
      await api.post("/complaints", body);
    },
    onSuccess: () => {
      toast.success("Complaint submitted successfully.");
      qc.invalidateQueries({ queryKey: ["complaints"] });
      onClose();
    },
    onError: (err) => {
      toast.error(apiMessage(err, "Failed to submit complaint."));
    }
  });

  return (
    <Dialog open onClose={onClose} className="max-w-md">
      <DialogHeader
        title="Raise a Complaint"
        description="Your submission is confidential and will be reviewed by HR & Admin."
      />
      <form onSubmit={(e) => { e.preventDefault(); submit.mutate(); }} className="mt-3 space-y-3">
        <div className="space-y-1">
          <Label htmlFor="sub">Subject</Label>
          <Input id="sub" required value={subject} onChange={(e) => setSubject(e.target.value)} placeholder="Short title for your complaint" />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1">
            <Label htmlFor="cat">Category</Label>
            <Select id="cat" value={category} onChange={(e) => setCategory(e.target.value)}>
              <option value="WORKPLACE">Workplace Environment</option>
              <option value="BEHAVIOR">Behavioral Issue</option>
              <option value="PAYROLL">Payroll / Compensation</option>
              <option value="OTHER">Other Issue</option>
            </Select>
          </div>
          <div className="space-y-1">
            <Label htmlFor="prio">Priority</Label>
            <Select id="prio" value={priority} onChange={(e) => setPriority(e.target.value)}>
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High (Urgent)</option>
            </Select>
          </div>
        </div>
        <div className="space-y-1">
          <Label htmlFor="tgt">Send to</Label>
          <Select id="tgt" value={selectedTargetRoleId} onChange={(e) => setTargetRoleId(e.target.value)}>
            {/* The server labels these "CTO (PIX-E100)". The list is one person
                per role, so the code identifies nothing the role does not. */}
            {(recs.data ?? []).map((r) => (
              <option key={r.id} value={r.id}>
                {(r.name || "").replace(/\s*\([^)]*\)\s*$/, "").trim() || r.name}
              </option>
            ))}
          </Select>
        </div>
        <div className="space-y-1">
          <Label htmlFor="desc">Description</Label>
          <Textarea
            id="desc"
            required
            rows={4}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Describe the complaint in detail..."
          />
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={submit.isPending}>
            {submit.isPending ? "Submitting..." : "Submit Complaint"}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

/** Respond to a complaint modal for HR & Admin reviewers. */
function RespondDialog({
  complaint,
  readOnly,
  onClose,
  onSaved
}: {
  complaint: ComplaintNeed;
  /** Open to read: not addressed to this person, or already settled. */
  readOnly: boolean;
  onClose: () => void;
  onSaved: () => void;
}) {
  /*
    A complaint only moves forward. Once it is in review it cannot be put
    back to open, and once it is settled it cannot be moved at all -- so the
    step it has passed is offered as a disabled option rather than removed,
    which shows where it has been instead of silently dropping it.
  */
  const allowed = NEXT_STATUS[complaint.status] ?? [];
  // Start on the first step actually available, never on the current one.
  const [status, setStatus] = useState(allowed[0] ?? complaint.status);
  const [notes, setNotes] = useState(complaint.hrResponse || "");

  const update = useMutation({
    mutationFn: async () => {
      await api.post(`/complaints/${complaint.id}/respond`, {
        status,
        response: notes
      });
    },
    onSuccess: () => {
      toast.success("Complaint updated successfully.");
      onSaved();
    },
    onError: (err) => {
      toast.error(apiMessage(err, "Failed to update complaint."));
    }
  });

  return (
    <Dialog open onClose={onClose} className="max-w-md">
      <DialogHeader
        title={`${readOnly ? "Complaint" : "Respond to Complaint"} #${complaint.referenceCode}`}
        description={complaint.subject}
      />
      <div className="mt-3 space-y-3">
        {/*
          Both ends of the complaint, not just the one it came from: opening
          it should answer "whose is this" without going back to the table.
        */}
        <div className="rounded-md border bg-muted/40 p-3 text-xs space-y-1">
          <div className="font-semibold text-foreground">
            Raised by: {complaint.raisedByName || "Employee"}
            {complaint.raisedByCode ? ` (${complaint.raisedByCode})` : ""}
          </div>
          <div className="font-semibold text-foreground">
            Sent to: {complaint.requestedToName || "HR & Admin"}
          </div>
          {complaint.handledByName && (
            <div className="font-semibold text-foreground">
              Answered by: {complaint.handledByName}
              {complaint.resolvedAt
                ? ` on ${dayjs(complaint.resolvedAt).format("DD MMM YYYY, HH:mm")}`
                : ""}
            </div>
          )}
          <div className="text-muted-foreground">{complaint.description}</div>
        </div>

        {readOnly ? (
          <>
            <div className="space-y-1">
              <Label>Status</Label>
              <div>
                <Badge variant={statusVariant(complaint.status)}>
                  {complaint.status.replace("_", " ")}
                </Badge>
              </div>
            </div>
            <div className="space-y-1">
              <Label>Response</Label>
              {complaint.hrResponse ? (
                <p className="rounded-md border bg-muted/40 p-3 text-xs whitespace-pre-wrap">
                  {complaint.hrResponse}
                </p>
              ) : (
                <p className="text-xs italic text-muted-foreground">
                  Awaiting a response from {complaint.requestedToName || "the reviewer"}.
                </p>
              )}
            </div>
            <div className="flex justify-end pt-2">
              <Button type="button" variant="outline" onClick={onClose}>Close</Button>
            </div>
          </>
        ) : (
          <>
            <div className="space-y-1">
              <Label htmlFor="st">Update Status</Label>
              {/*
                Every step is listed so the path is visible, but the ones
                already passed are disabled -- a complaint in review cannot go
                back to open.
              */}
              <Select id="st" value={status} onChange={(e) => setStatus(e.target.value)}>
                {STATUS_FLOW.map((f) => {
                  const here = f.value === complaint.status;
                  return (
                    <option
                      key={f.value}
                      value={f.value}
                      disabled={!allowed.includes(f.value)}
                    >
                      {f.label}{here ? " (current)" : ""}
                    </option>
                  );
                })}
              </Select>
              {STEP_HINT[complaint.status] && (
                <p className="text-[11px] text-muted-foreground">{STEP_HINT[complaint.status]}</p>
              )}
            </div>

            <div className="space-y-1">
              <Label htmlFor="nts">Resolution Notes / Response</Label>
              <Textarea
                id="nts"
                rows={3}
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                placeholder="Enter response or resolution details for the employee..."
              />
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
              <Button
                type="button"
                disabled={update.isPending || !allowed.includes(status)}
                onClick={() => update.mutate()}
              >
                {update.isPending ? "Saving..." : "Save Response"}
              </Button>
            </div>
          </>
        )}
      </div>
    </Dialog>
  );
}
