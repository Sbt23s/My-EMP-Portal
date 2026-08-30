import { CustomLoader as Loader2 } from "@/components/ui/custom-loader";
import { useMemo, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  Plus, Eye, Home, Inbox, Search, CheckCircle2, XCircle, Clock, FileSpreadsheet,
} from "lucide-react";
import toast from "react-hot-toast";
import dayjs from "dayjs";
import * as XLSX from "xlsx";

import { api, apiMessage } from "@/lib/api";
import { PageHeader } from "@/components/PageHeader";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { ExportExcelButton } from "@/components/ui/export-excel-button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Dialog, DialogHeader } from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import {
  Table, TableHeader, TableBody, TableRow, TableHead, TableCell,
} from "@/components/ui/table";
import { RequestThread } from "@/components/RequestThread";
import { usePagedRows, TablePagination } from "@/components/ui/table-pagination";
import { StatTile, TILE_FILLS } from "@/components/ui/stat-tile";
import { EmptyState } from "@/components/EmptyState";
import { useAuth } from "@/hooks/useAuth";
import { DATE_MAX, todayIso } from "@/lib/dates";
import type { ApiEnvelope } from "@/types";

/**
 * Work From Home.
 *
 * Built to read like Leave and Permission rather than like something new: the
 * same tiles, the same tabs, the same details dialog carrying the same
 * attachment and comment panel. Somebody who can use those two pages should
 * not have to learn a third.
 *
 * The approval ladder is the server's business, not this page's. It says who a
 * request will go to and shows what came back; it never works the rung out for
 * itself, because two clients disagreeing with the server about who approves
 * what is how a request reaches nobody.
 */

interface WfhRow {
  id: number;
  userId: number;
  employeeName: string;
  employeeCode?: string;
  team?: string;
  designation?: string;
  roleLabel?: string;
  fromDate: string;
  toDate: string;
  workingDays: number;
  reason?: string;
  remarks?: string;
  /** PENDING | APPROVED | REJECTED | CANCELLED | COMPLETED */
  status: string;
  requestedTo?: number;
  requestedToName?: string;
  requestedToRole?: string;
  decidedBy?: number;
  decidedByName?: string;
  decidedAt?: string;
  decisionComment?: string;
  createdAt?: string;
  canAct: boolean;
  canCancel: boolean;
}

/** How long a list may sit before it is asked for again. */
const LIVE = 15000;

function StatusBadge({ status }: { status: string }) {
  const tone =
    status === "APPROVED"
      ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300"
      : status === "REJECTED"
        ? "bg-rose-100 text-rose-700 dark:bg-rose-950/40 dark:text-rose-300"
        : status === "CANCELLED"
          ? "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300"
          : status === "COMPLETED"
            ? "bg-sky-100 text-sky-700 dark:bg-sky-950/40 dark:text-sky-300"
            : "bg-amber-100 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300";
  return (
    <Badge className={`border-0 text-[10px] font-bold uppercase tracking-wider ${tone}`}>
      {status}
    </Badge>
  );
}

function dateRange(r: WfhRow) {
  return r.fromDate === r.toDate
    ? dayjs(r.fromDate).format("DD MMM YYYY")
    : `${dayjs(r.fromDate).format("DD MMM")} – ${dayjs(r.toDate).format("DD MMM YYYY")}`;
}

export default function WorkFromHomePage() {
  const qc = useQueryClient();
  const { user, hasPermission } = useAuth();

  /*
    Who sees the inbox tab.

    Anybody can be sent a WFH request -- a Team Leader from their team, HR from
    a Team Leader, the CTO from HR -- and none of those is a permission. So the
    tab is shown when the server actually has something addressed to this
    person, rather than guessed at from a role.
  */
  const [tab, setTab] = useState<"mine" | "inbox" | "all" | "today">("mine");
  const [applyOpen, setApplyOpen] = useState(false);
  const [viewRow, setViewRow] = useState<WfhRow | null>(null);
  const [decideOn, setDecideOn] = useState<{ row: WfhRow; approve: boolean } | null>(null);
  const [decisionNote, setDecisionNote] = useState("");
  const [q, setQ] = useState("");

  const mine = useQuery({
    queryKey: ["wfh", "mine"],
    queryFn: async () =>
      (await api.get<ApiEnvelope<WfhRow[]>>("/wfh/me")).data.data ?? [],
    refetchInterval: LIVE,
    refetchOnWindowFocus: true,
  });

  const inbox = useQuery({
    queryKey: ["wfh", "for-me"],
    queryFn: async () =>
      (await api.get<ApiEnvelope<WfhRow[]>>("/wfh/for-me")).data.data ?? [],
    refetchInterval: LIVE,
    refetchOnWindowFocus: true,
  });

  /*
    Everything, and who is at home right now.

    Both are gated on the server -- USER_MANAGE or DASHBOARD_EXEC for the full
    list, plus ATTENDANCE_TEAM for the day view -- so asking for them without
    the authority returns 403. `enabled` keeps a Team Leader's page from firing
    two requests it will only be refused, rather than deciding here who is
    allowed: the server is still the one that says no.
  */
  const canSeeAll = hasPermission("USER_MANAGE", "DASHBOARD_EXEC");
  const canSeeToday = canSeeAll || hasPermission("ATTENDANCE_TEAM");

  const all = useQuery({
    queryKey: ["wfh", "all"],
    queryFn: async () =>
      (await api.get<ApiEnvelope<WfhRow[]>>("/wfh/all")).data.data ?? [],
    enabled: canSeeAll,
    refetchInterval: LIVE,
    refetchOnWindowFocus: true,
  });

  const [boardDate, setBoardDate] = useState(todayIso());
  const [boardTo, setBoardTo] = useState(todayIso());

  /*
    The months a list covers.

    Ends a year out rather than at the current month: work from home is asked
    for ahead of time, so a range ending today hides the request the moment it
    is made -- the same fault the Leave page had. The pickers narrow it to
    whatever somebody actually wants.
  */
  const [fromMonth, setFromMonth] = useState(dayjs().startOf("year").format("YYYY-MM"));
  const [toMonth, setToMonth] = useState(dayjs().add(1, "year").format("YYYY-MM"));

  const today = useQuery({
    queryKey: ["wfh", "active", boardDate, boardTo],
    queryFn: async () =>
      (await api.get<ApiEnvelope<WfhRow[]>>(
        `/wfh/active-range?from=${boardDate}&to=${boardTo}`)).data.data ?? [],
    enabled: canSeeToday,
    // Shorter than the rest: this is the board somebody leaves open to see who
    // is where, so a decision made elsewhere should show without a refresh.
    refetchInterval: 10000,
    refetchOnWindowFocus: true,
  });

  const inboxRows = inbox.data ?? [];
  const myRows = mine.data ?? [];
  const showInbox = inboxRows.length > 0 || hasPermission("LEAVE_APPROVE");

  const rows =
    tab === "inbox" ? inboxRows
    : tab === "all" ? (all.data ?? [])
    : tab === "today" ? (today.data ?? [])
    : myRows;

  const filtered = useMemo(() => {
    const needle = q.trim().toLowerCase();
    /*
      The day board answers "who is at home on this date" and already has its
      own date, so a month range would be a second filter fighting the first.
      Only the three list tabs are ranged.
    */
    const ranged = tab === "today"
      ? rows
      : rows.filter((r) => {
          const m = String(r.fromDate).slice(0, 7);
          return m >= fromMonth && m <= toMonth;
        });
    if (!needle) return ranged;
    return ranged.filter((r) =>
      [r.employeeName, r.employeeCode, r.team, r.reason, r.remarks, r.requestedToName]
        .filter(Boolean)
        .some((v) => String(v).toLowerCase().includes(needle)));
  }, [rows, q, tab, fromMonth, toMonth]);

  const paged = usePagedRows(filtered, 15, [tab, q, rows, fromMonth, toMonth]);

  const counts = useMemo(() => {
    const c = { ALL: rows.length, PENDING: 0, APPROVED: 0, REJECTED: 0 };
    rows.forEach((r) => {
      // COMPLETED is an approved request whose days have passed, so it counts
      // as approved -- a separate tile for it would split one fact in two.
      if (r.status === "PENDING") c.PENDING += 1;
      else if (r.status === "APPROVED" || r.status === "COMPLETED") c.APPROVED += 1;
      else if (r.status === "REJECTED") c.REJECTED += 1;
    });
    return c;
  }, [rows]);

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ["wfh"] });
    // An approval writes an attendance row, so the figures that read from it
    // are stale the moment a decision is made.
    qc.invalidateQueries({ queryKey: ["attendance"] });
    qc.invalidateQueries({ queryKey: ["dashboard"] });
  };

  const decide = useMutation({
    mutationFn: async (v: { id: number; approve: boolean; comment?: string }) =>
      api.post(`/wfh/${v.id}/decision`, { approve: v.approve, comment: v.comment }),
    onSuccess: (_r, v) => {
      toast.success(v.approve ? "Approved" : "Rejected");
      refresh();
    },
    onError: (e) => toast.error(apiMessage(e, "Could not update that request")),
  });

  const cancel = useMutation({
    mutationFn: async (id: number) => api.post(`/wfh/${id}/cancel`),
    onSuccess: () => {
      toast.success("Request withdrawn");
      refresh();
    },
    onError: (e) => toast.error(apiMessage(e, "Could not withdraw that request")),
  });

  /*
    What is on screen, as a spreadsheet.

    Exports the filtered rows rather than everything fetched: the sheet should
    be the list somebody is looking at, or the filters they set were pointless.
  */
  const exportExcel = () => {
    if (filtered.length === 0) {
      toast.error("Nothing to export.");
      return;
    }
    const sheet = filtered.map((r, i) => ({
      "#": i + 1,
      Employee: r.employeeName,
      "Employee ID": r.employeeCode || "",
      Role: r.roleLabel || "",
      Designation: r.designation || "",
      Team: r.team || "",
      From: dayjs(r.fromDate).format("DD MMM YYYY"),
      To: dayjs(r.toDate).format("DD MMM YYYY"),
      "Working days": r.workingDays,
      Reason: r.reason || "",
      Remarks: r.remarks || "",
      Status: r.status,
      "Pending with": r.status === "PENDING" ? (r.requestedToName || "") : "",
      "Sent to": r.requestedToName || "",
      "Decided by": r.decidedByName || "",
      "Decided at": r.decidedAt ? dayjs(r.decidedAt).format("DD MMM YYYY, hh:mm A") : "",
      Remark: r.decisionComment || "",
      "Applied on": r.createdAt ? dayjs(r.createdAt).format("DD MMM YYYY, hh:mm A") : "",
    }));
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(sheet), "Work From Home");
    const tag = tab === "today"
      ? (boardDate === boardTo ? boardDate : `${boardDate}_to_${boardTo}`)
      : `${fromMonth}_to_${toMonth}`;
    XLSX.writeFile(wb, `Work_From_Home_${tab}_${tag}.xlsx`);
  };

  const loading =
    tab === "inbox" ? inbox.isLoading
    : tab === "all" ? all.isLoading
    : tab === "today" ? today.isLoading
    : mine.isLoading;

  return (
    <div>
      <PageHeader
        title="Work From Home"
        subtitle="Ask to work from home, and track where the request is."
        actions={
          <Button onClick={() => setApplyOpen(true)}>
            <Plus className="mr-1.5 h-4 w-4" /> Apply for WFH
          </Button>
        }
      />

      {/*
        The tabs a person actually has.

        Everybody has their own requests. An approver gains an inbox. HR and
        the CTO gain the whole organisation and the day board. Built as a list
        rather than four conditionals so the bar never renders with one lonely
        tab in it.
      */}
      {(() => {
        const tabs: Array<[typeof tab, string]> = [
          ["mine", `My requests (${myRows.length})`],
        ];
        if (showInbox) {
          tabs.push(["inbox",
            `Pending my approval (${inboxRows.filter((r) => r.canAct).length})`]);
        }
        if (canSeeAll) tabs.push(["all", `All requests (${(all.data ?? []).length})`]);
        if (canSeeToday) tabs.push(["today", `Working from home (${(today.data ?? []).length})`]);
        if (tabs.length < 2) return null;
        return (
          <div className="mb-4 flex w-fit flex-wrap gap-1 rounded-lg border bg-muted/60 p-1">
            {tabs.map(([key, label]) => (
              <button
                key={key}
                type="button"
                onClick={() => { setTab(key); setQ(""); }}
                className={
                  "rounded-md px-3 py-1.5 text-sm font-medium transition-colors " +
                  (tab === key
                    ? "bg-background text-foreground shadow-sm"
                    : "text-muted-foreground hover:text-foreground")
                }
              >
                {label}
              </button>
            ))}
          </div>
        );
      })()}

      <div className="mb-4 grid grid-cols-2 gap-2.5 lg:grid-cols-4">
        <StatTile
          label={tab === "today" ? "At home" : "All"}
          value={counts.ALL}
          icon={tab === "today" ? Home : Inbox}
          fill={TILE_FILLS.violet}
          hint={
            tab === "inbox" ? "Sent to you"
            : tab === "all" ? "Across the organisation"
            : tab === "today"
              ? (boardDate === boardTo
                  ? dayjs(boardDate).format("DD MMM YYYY")
                  : `${dayjs(boardDate).format("DD MMM")} – ${dayjs(boardTo).format("DD MMM YYYY")}`)
            : "Requests you raised"
          } />
        <StatTile label="Pending" value={counts.PENDING} icon={Clock} fill={TILE_FILLS.amber}
          hint="Waiting on a decision" />
        <StatTile label="Approved" value={counts.APPROVED} icon={CheckCircle2} fill={TILE_FILLS.green}
          hint="Counted as present" />
        <StatTile label="Rejected" value={counts.REJECTED} icon={XCircle} fill={TILE_FILLS.red}
          hint="Turned down" />
      </div>

      <div className="mb-3 flex flex-wrap items-end gap-3">
        <div className="max-w-sm flex-1">
          <div className="relative">
            <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="pl-8"
              placeholder="Name, employee ID, team or reason…"
              value={q}
              onChange={(e) => setQ(e.target.value)}
            />
          </div>
        </div>
        {/*
          The board answers "who is at home", and the day is part of the
          question -- yesterday and next Monday are both worth asking. Only
          shown on that tab, because the other three are not about one day.
        */}
        {tab === "today" && (
          <>
            <div className="space-y-1">
              <Label htmlFor="wfh-day" className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
                From
              </Label>
              <Input
                id="wfh-day"
                type="date"
                className="w-40"
                max={DATE_MAX}
                value={boardDate}
                onChange={(e) => {
                  const v = e.target.value || todayIso();
                  setBoardDate(v);
                  // Keep the end on or after the start rather than letting an
                  // impossible window be typed and silently corrected later.
                  if (v > boardTo) setBoardTo(v);
                }}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="wfh-day-to" className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
                To
              </Label>
              <Input
                id="wfh-day-to"
                type="date"
                className="w-40"
                min={boardDate}
                max={DATE_MAX}
                value={boardTo}
                onChange={(e) => setBoardTo(e.target.value || boardDate)}
              />
            </div>
            {(boardDate !== todayIso() || boardTo !== todayIso()) && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => { setBoardDate(todayIso()); setBoardTo(todayIso()); }}
              >
                Today
              </Button>
            )}
          </>
        )}

        {/* A month range on the lists, so a period can be looked at and exported. */}
        {tab !== "today" && (
          <>
            <div className="space-y-1">
              <Label htmlFor="wfh-from-m" className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
                From
              </Label>
              <Input
                id="wfh-from-m"
                type="month"
                className="w-40"
                value={fromMonth}
                onChange={(e) => {
                  const v = e.target.value;
                  if (!v) return;
                  setFromMonth(v);
                  if (v > toMonth) setToMonth(v);
                }}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="wfh-to-m" className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
                To
              </Label>
              <Input
                id="wfh-to-m"
                type="month"
                className="w-40"
                min={fromMonth}
                value={toMonth}
                onChange={(e) => e.target.value && setToMonth(e.target.value)}
              />
            </div>
          </>
        )}

        <ExportExcelButton onClick={exportExcel} />
      </div>

      <Card>
        <CardContent className="p-0">
          {loading ? (
            <Skeleton className="m-4 h-32" />
          ) : filtered.length === 0 ? (
            <EmptyState
              icon={Home}
              title={
                tab === "inbox" ? "Nothing waiting on you"
                : tab === "all" ? "No requests in the organisation yet"
                : tab === "today" ? "Nobody is working from home"
                : "No requests yet"
              }
              description={
                tab === "inbox"
                  ? "Requests sent to you for approval appear here."
                  : tab === "all"
                    ? "Every request across the organisation appears here."
                    : tab === "today"
                      ? `No approved request covers ${boardDate === boardTo
                            ? dayjs(boardDate).format("DD MMM YYYY")
                            : `${dayjs(boardDate).format("DD MMM")} to ${dayjs(boardTo).format("DD MMM YYYY")}`}.`
                      : "Use “Apply for WFH” to ask to work from home."
              }
            />
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Action</TableHead>
                    {/*
                      Whose request it is matters on every list except the one
                      showing only your own.
                    */}
                    {tab !== "mine" && <TableHead>Employee</TableHead>}
                    {tab !== "mine" && <TableHead>Role</TableHead>}
                    <TableHead>Dates</TableHead>
                    <TableHead>Days</TableHead>
                    <TableHead>Reason</TableHead>
                    <TableHead>{tab === "mine" ? "Sent to" : "Team"}</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>{tab === "today" ? "Approved by" : "Decided by"}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {paged.pageRows.map((r) => (
                    <TableRow key={r.id} className="align-top [&>td]:px-3 [&>td]:py-3.5">
                      <TableCell>
                        <div className="flex items-center gap-1.5">
                          <Button size="sm" variant="outline" onClick={() => setViewRow(r)}>
                            <Eye className="h-4 w-4" /> View
                          </Button>
                          {/* canAct is the server's answer, not a role guess. */}
                          {r.canAct && (
                            <>
                              <Button
                                size="sm"
                                variant="outline"
                                disabled={decide.isPending}
                                onClick={() => { setDecisionNote(""); setDecideOn({ row: r, approve: false }); }}
                              >
                                Reject
                              </Button>
                              <Button
                                size="sm"
                                disabled={decide.isPending}
                                onClick={() => { setDecisionNote(""); setDecideOn({ row: r, approve: true }); }}
                              >
                                Approve
                              </Button>
                            </>
                          )}
                          {r.canCancel && (
                            <Button
                              size="sm"
                              variant="outline"
                              disabled={cancel.isPending}
                              onClick={() => {
                                if (window.confirm("Withdraw this request?")) cancel.mutate(r.id);
                              }}
                            >
                              Withdraw
                            </Button>
                          )}
                        </div>
                      </TableCell>
                      {tab !== "mine" && (
                        <TableCell className="font-medium">
                          {r.employeeName}
                          <div className="code-chip text-xs text-muted-foreground">
                            {r.employeeCode || "—"}
                          </div>
                        </TableCell>
                      )}
                      {tab !== "mine" && (
                        <TableCell className="text-xs">
                          {r.roleLabel || "Employee"}
                          {r.designation && (
                            <div className="text-[11px] text-muted-foreground">
                              {r.designation}
                            </div>
                          )}
                        </TableCell>
                      )}
                      <TableCell className="whitespace-nowrap font-medium">{dateRange(r)}</TableCell>
                      <TableCell className="tabular-nums">{r.workingDays}</TableCell>
                      <TableCell className="max-w-[220px] truncate text-xs" title={r.reason}>
                        {r.reason || "—"}
                      </TableCell>
                      <TableCell className="text-xs">
                        {tab === "mine"
                          ? (r.requestedToName
                              ? `${r.requestedToRole ? r.requestedToRole + " · " : ""}${r.requestedToName}`
                              : "—")
                          : (r.team || "—")}
                      </TableCell>
                      <TableCell><StatusBadge status={r.status} /></TableCell>
                      <TableCell className="text-xs">
                        {r.decidedByName || "—"}
                        {r.decidedAt && (
                          <div className="text-[11px] text-muted-foreground">
                            {dayjs(r.decidedAt).format("DD MMM, hh:mm A")}
                          </div>
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
            </>
          )}
        </CardContent>
      </Card>

      {applyOpen && (
        <ApplyDialog
          onClose={() => setApplyOpen(false)}
          onDone={() => { setApplyOpen(false); refresh(); }}
        />
      )}

      {viewRow && (
        <DetailsDialog
          row={viewRow}
          onClose={() => setViewRow(null)}
          onDecide={(approve) => {
            setDecisionNote("");
            setDecideOn({ row: viewRow, approve });
            setViewRow(null);
          }}
        />
      )}

      {decideOn && (
        <Dialog open onClose={() => setDecideOn(null)} className="max-w-md">
          <DialogHeader
            title={decideOn.approve ? "Approve this request?" : "Reject this request"}
            description={`${decideOn.row.employeeName} · ${dateRange(decideOn.row)} · ${decideOn.row.workingDays} day(s)`}
          />
          <div className="space-y-3">
            {decideOn.row.reason && (
              <div className="rounded-md border bg-muted/30 p-3 text-xs">
                Reason given: “{decideOn.row.reason}”
              </div>
            )}
            <div className="space-y-1.5">
              <Label htmlFor="wfh-note">
                {decideOn.approve ? (
                  <>Comment <span className="font-normal text-muted-foreground">(optional)</span></>
                ) : (
                  <>Reason for rejection <span className="text-destructive">*</span></>
                )}
              </Label>
              <Textarea
                id="wfh-note"
                rows={2}
                autoFocus
                value={decisionNote}
                onChange={(e) => setDecisionNote(e.target.value)}
                placeholder={
                  decideOn.approve
                    ? "Anything they should know — sent with the approval"
                    : "Tell them why — this is sent to them"
                }
              />
            </div>
            {decideOn.approve && (
              <p className="text-[11px] text-muted-foreground">
                Approving marks those days present on their attendance, so they
                are paid for them.
              </p>
            )}
            <div className="flex justify-end gap-2 border-t pt-3">
              <Button variant="outline" onClick={() => setDecideOn(null)}>Cancel</Button>
              <Button
                variant={decideOn.approve ? "default" : "destructive"}
                disabled={decide.isPending || (!decideOn.approve && !decisionNote.trim())}
                onClick={() => {
                  decide.mutate({
                    id: decideOn.row.id,
                    approve: decideOn.approve,
                    comment: decisionNote.trim() || undefined,
                  });
                  setDecideOn(null);
                  setDecisionNote("");
                }}
              >
                {decide.isPending && <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />}
                {decideOn.approve ? "Yes, approve" : "Reject request"}
              </Button>
            </div>
          </div>
        </Dialog>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ apply */

function ApplyDialog({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [reason, setReason] = useState("");
  const [remarks, setRemarks] = useState("");

  /*
    Who this will go to.

    Asked of the server rather than worked out here: the rung depends on the
    applicant's own role, and a page that guessed would eventually disagree
    with the server about who approves what.
  */
  const approvers = useQuery({
    queryKey: ["wfh", "approvers"],
    queryFn: async () =>
      (await api.get<ApiEnvelope<Array<{ id: number; name: string; code?: string; role?: string }>>>(
        "/wfh/approvers")).data.data ?? [],
  });
  const approver = approvers.data?.[0];

  const apply = useMutation({
    mutationFn: async () =>
      api.post("/wfh", {
        fromDate,
        toDate,
        reason: reason.trim() || undefined,
        remarks: remarks.trim() || undefined,
      }),
    onSuccess: () => {
      toast.success("Work from home request submitted");
      onDone();
    },
    onError: (e) => toast.error(apiMessage(e, "Could not submit that request")),
  });

  const valid = !!fromDate && !!toDate && toDate >= fromDate && !!reason.trim();

  return (
    <Dialog open onClose={onClose} className="max-w-md">
      <DialogHeader
        title="Apply to work from home"
        description="Weekends and public holidays are left out of the day count automatically."
      />
      <div className="mt-3 space-y-3">
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label htmlFor="wfh-from">From <span className="text-destructive">*</span></Label>
            <Input
              id="wfh-from"
              type="date"
              min={todayIso()}
              max={DATE_MAX}
              value={fromDate}
              onChange={(e) => {
                setFromDate(e.target.value);
                // Keep the end on or after the start rather than letting an
                // impossible range be typed and refused on submit.
                if (toDate && e.target.value > toDate) setToDate(e.target.value);
              }}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="wfh-to">To <span className="text-destructive">*</span></Label>
            <Input
              id="wfh-to"
              type="date"
              min={fromDate || todayIso()}
              max={DATE_MAX}
              value={toDate}
              onChange={(e) => setToDate(e.target.value)}
            />
          </div>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="wfh-reason">Reason <span className="text-destructive">*</span></Label>
          <Textarea
            id="wfh-reason"
            rows={3}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Why do you need to work from home?"
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="wfh-remarks">
            Remarks <span className="font-normal text-muted-foreground">(optional)</span>
          </Label>
          <Textarea
            id="wfh-remarks"
            rows={2}
            value={remarks}
            onChange={(e) => setRemarks(e.target.value)}
            placeholder="Anything else your approver should know"
          />
        </div>

        <div className="rounded-md border bg-muted/30 px-3 py-2 text-xs">
          {approvers.isLoading ? (
            "Finding your approver…"
          ) : approver ? (
            <>
              This goes to{" "}
              <span className="font-semibold">
                {approver.role ? `${approver.role} · ` : ""}{approver.name}
              </span>
              {approver.code ? ` (${approver.code})` : ""}.
            </>
          ) : (
            <span className="text-destructive">
              There is nobody set up to approve your requests yet. Ask HR to
              assign an approver.
            </span>
          )}
        </div>

        <div className="flex justify-end gap-2 border-t pt-3">
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button disabled={!valid || apply.isPending} onClick={() => apply.mutate()}>
            {apply.isPending && <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />}
            Submit request
          </Button>
        </div>
      </div>
    </Dialog>
  );
}

/* ---------------------------------------------------------------- details */

function DetailsDialog({
  row,
  onClose,
  onDecide,
}: {
  row: WfhRow;
  onClose: () => void;
  onDecide: (approve: boolean) => void;
}) {
  return (
    <Dialog open onClose={onClose} className="max-w-2xl">
      <DialogHeader
        title="Work from home request"
        description={`${row.employeeName}${row.employeeCode ? ` · ${row.employeeCode}` : ""}`}
      />
      <div className="space-y-4">
        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge status={row.status} />
          {row.status === "PENDING" && row.requestedToName && (
            <span className="text-xs text-muted-foreground">
              Pending with {row.requestedToName}
            </span>
          )}
        </div>

        <div className="grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2">
          <Field label="Employee">{row.employeeName}</Field>
          <Field label="Employee ID">
            <span className="code-chip">{row.employeeCode || "—"}</span>
          </Field>
          <Field label="Team">{row.team || "—"}</Field>
          <Field label="Designation">{row.designation || "—"}</Field>
          <Field label="From">{dayjs(row.fromDate).format("dddd, DD MMM YYYY")}</Field>
          <Field label="To">{dayjs(row.toDate).format("dddd, DD MMM YYYY")}</Field>
          <Field label="Working days">{row.workingDays}</Field>
          <Field label="Applied on">
            {row.createdAt ? dayjs(row.createdAt).format("DD MMM YYYY, hh:mm A") : "—"}
          </Field>
          <Field label="Current approver">
            {row.requestedToName
              ? `${row.requestedToRole ? row.requestedToRole + " · " : ""}${row.requestedToName}`
              : "—"}
          </Field>
          {row.status !== "PENDING" && (
            <>
              <Field label="Decided by">{row.decidedByName || "—"}</Field>
              <Field label="Decided at">
                {row.decidedAt ? dayjs(row.decidedAt).format("DD MMM YYYY, hh:mm A") : "—"}
              </Field>
            </>
          )}
        </div>

        <div className="space-y-1.5">
          <div className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground">
            Reason
          </div>
          <div className="whitespace-pre-wrap rounded-md border bg-muted/30 p-3 text-sm">
            {row.reason || <span className="italic text-muted-foreground">No reason given</span>}
          </div>
        </div>

        {row.remarks && (
          <div className="space-y-1.5">
            <div className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground">
              Remarks
            </div>
            <div className="whitespace-pre-wrap rounded-md border bg-muted/30 p-3 text-sm">
              {row.remarks}
            </div>
          </div>
        )}

        {row.decisionComment && (
          <div className="space-y-1.5">
            <div className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground">
              {row.status === "REJECTED" ? "Rejection reason" : "Approver's comment"}
            </div>
            <div
              className={
                "whitespace-pre-wrap rounded-md border p-3 text-sm " +
                (row.status === "REJECTED"
                  ? "border-rose-100 bg-rose-50 text-rose-800 dark:border-rose-900/40 dark:bg-rose-950/30 dark:text-rose-200"
                  : "border-emerald-100 bg-emerald-50 text-emerald-800 dark:border-emerald-900/40 dark:bg-emerald-950/30 dark:text-emerald-200")
              }
            >
              {row.decisionComment}
            </div>
          </div>
        )}

        {/*
          The same files and conversation the other pages carry. The server
          keys these on (request_type, request_id), so WFH needed nothing added
          for it to work here.
        */}
        <div className="border-t pt-4">
          <RequestThread type="WFH" requestId={row.id} canAttach={row.status === "PENDING"} canComment={row.status === "PENDING"} />
        </div>

        <div className="flex flex-wrap justify-end gap-2 border-t pt-3">
          <Button variant="outline" onClick={onClose}>Close</Button>
          {row.canAct && (
            <>
              <Button variant="destructive" onClick={() => onDecide(false)}>Reject</Button>
              <Button onClick={() => onDecide(true)}>Approve</Button>
            </>
          )}
        </div>
      </div>
    </Dialog>
  );
}

/**
 * One labelled fact.
 *
 * A dash rather than a blank when there is nothing: an empty space looks like
 * the page failed to load the value, where a dash says there isn't one.
 */
function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <div className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground">
        {label}
      </div>
      <div className="mt-0.5 break-words text-sm font-medium">{children ?? "—"}</div>
    </div>
  );
}
