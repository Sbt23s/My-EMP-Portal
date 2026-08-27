import { CustomLoader as Loader2 } from "@/components/ui/custom-loader";
import { useMemo, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Check, X, CheckCheck, Inbox, ListTodo, Clock, Eye } from "lucide-react";
import dayjs from "dayjs";
import toast from "react-hot-toast";
import { api, apiMessage } from "@/lib/api";
import { PageHeader } from "@/components/PageHeader";
import { EmptyState } from "@/components/EmptyState";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Avatar } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { Select } from "@/components/ui/select";
import { Input } from "@/components/ui/input";
import { Dialog, DialogHeader } from "@/components/ui/dialog";
import type { ApiEnvelope, LeaveRequest, EmployeeTaskGroup } from "@/types";
import { usePagedRows, TablePagination } from "@/components/ui/table-pagination";
import { useAuth } from "@/hooks/useAuth";
import { StatTile, TILE_FILLS } from "@/components/ui/stat-tile";

export default function LeaveApprovalsPage() {
  const qc = useQueryClient();
  const { user, hasPermission, hasRole } = useAuth();
  const isCto = user?.employeeCode?.toUpperCase() === "PIX-E100";
  const isAdmin = hasPermission("USER_MANAGE") || isCto;
  const isHR = hasRole("IT_MGR") || hasRole("IT_HR");
  const isTL = hasRole("IT_TL") && !isHR && !isAdmin;
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [tab, setTab] = useState<"PENDING" | "APPROVED" | "REJECTED" | "ALL">("ALL");
  const [teamFilter, setTeamFilter] = useState("all");
  const [searchTerm, setSearchTerm] = useState("");
  const [viewMode, setViewMode] = useState<"ALL" | "MY">("ALL");
  const [viewModalData, setViewModalData] = useState<any | null>(null);

  const pending = useQuery({
    queryKey: ["leave", "for-me"],
    queryFn: async () =>
      (await api.get<ApiEnvelope<any[]>>("/leave/requests-for-me")).data.data
  });

  const myQueue = useQuery({
    queryKey: ["leave", "my-queue"],
    queryFn: async () =>
      (await api.get<ApiEnvelope<any[]>>("/leave/my-queue")).data.data
  });

  const taskGroups = useQuery({
    queryKey: ["tasks", "all"],
    retry: false,
    queryFn: async () =>
      (await api.get<ApiEnvelope<EmployeeTaskGroup[]>>("/tasks/all")).data.data
  });

  const tasksByUser = useMemo(() => {
    const map = new Map<number, EmployeeTaskGroup>();
    (taskGroups.data ?? []).forEach((g) => map.set(g.userId, g));
    return map;
  }, [taskGroups.data]);

  const decide = useMutation({
    mutationFn: async ({ id, decision, comment }: { id: number; decision: string; comment?: string }) =>
      api.post(`/leave/${id}/decision`, { decision, comment }),
    onSuccess: (_, v) => {
      toast.success(`Request ${v.decision.toLowerCase()}`);
      qc.invalidateQueries({ queryKey: ["leave"] });
      qc.invalidateQueries({ queryKey: ["dashboard"] });
    },
    onError: (err) => toast.error(apiMessage(err, "Action failed"))
  });

  const bulk = useMutation({
    mutationFn: async ({ decision, comment }: { decision: string; comment?: string }) =>
      api.post("/leave/bulk-decision", { requestIds: Array.from(selected), decision, comment }),
    onSuccess: (_, { decision }) => {
      toast.success(`${selected.size} request(s) ${decision.toLowerCase()}`);
      setSelected(new Set());
      qc.invalidateQueries({ queryKey: ["leave"] });
      qc.invalidateQueries({ queryKey: ["dashboard"] });
    },
    onError: (err) => toast.error(apiMessage(err, "Bulk action failed"))
  });

  const rawRows = viewMode === "ALL" ? (pending.data ?? []) : (myQueue.data ?? []);

  const counts = useMemo(() => ({
    ALL: rawRows.length,
    PENDING: rawRows.filter((r) => r.status === "PENDING").length,
    APPROVED: rawRows.filter((r) => r.status === "APPROVED").length,
    REJECTED: rawRows.filter((r) => r.status === "REJECTED").length
  }), [rawRows]);

  const teams = useMemo(
    () => [...new Set(rawRows.map((r) => (r.team || "").trim()).filter(Boolean))].sort(),
    [rawRows]
  );

  const list = rawRows.filter((r) => {
    if (tab !== "ALL" && r.status !== tab) return false;
    if (teamFilter !== "all" && (r.team || "").trim() !== teamFilter) return false;
    if (searchTerm.trim()) {
      const q = searchTerm.toLowerCase();
      if (!r.employeeName?.toLowerCase().includes(q) &&
          !r.team?.toLowerCase().includes(q) &&
          !r.reason?.toLowerCase().includes(q)) {
        return false;
      }
    }
    return true;
  });
  const { pageRows, page, setPage, totalPages, pageSize, setPageSize, total } =
    usePagedRows(list, 15, [tab, teamFilter, searchTerm, viewMode, pending.data, myQueue.data]);

  function toggle(id: number) {
    setSelected((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }
  const actable = list.filter((r) => r.canAct);
  const allSelected = actable.length > 0 && actable.every((r) => selected.has(r.id));
  const toggleAll = () =>
    setSelected(allSelected ? new Set() : new Set(actable.map((r) => r.id)));

  const promptReason = () => {
    const reason = window.prompt("Reason for rejection (required):");
    if (reason === null) return null;
    if (!reason.trim()) { toast.error("Rejection reason is required"); return null; }
    return reason.trim();
  };
  const rejectOne = (id: number) => {
    const comment = promptReason();
    if (comment === null) return;
    decide.mutate({ id, decision: "REJECTED", comment });
  };
  const rejectBulk = () => {
    const comment = promptReason();
    if (comment === null) return;
    bulk.mutate({ decision: "REJECTED", comment });
  };

  return (
    <div>
      <PageHeader
        title="Team Leave approvals"
        subtitle={
          isTL
            ? "Your team's leave requests — you decide up to 3 days, longer ones go to HR."
            : isHR
              ? "Leave requests across every team, including Team Leaders'."
              : isAdmin
                ? "Every leave request in the company."
                : "Requests waiting on your decision."
        }
        actions={
          selected.size > 0 ? (
            <div className="flex items-center gap-2">
              <Button variant="outline" size="sm" disabled={bulk.isPending} onClick={rejectBulk}>
                <X className="h-4 w-4" /> Reject {selected.size}
              </Button>
              <Button size="sm" disabled={bulk.isPending} onClick={() => bulk.mutate({ decision: "APPROVED" })}>
                {bulk.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <CheckCheck className="h-4 w-4" />}
                Approve {selected.size}
              </Button>
            </div>
          ) : null
        }
      />

      <div className="mb-4 flex items-center gap-2 bg-muted/20 p-1.5 rounded-lg w-max border">
        <button
          onClick={() => setViewMode("ALL")}
          className={`px-4 py-1.5 text-sm font-semibold rounded-md transition-colors ${
            viewMode === "ALL" ? "bg-card shadow-sm text-primary border" : "text-muted-foreground hover:text-foreground"
          }`}
        >
          All Requests
        </button>
        {!isCto && !isAdmin && (
          <button
            onClick={() => setViewMode("MY")}
            className={`px-4 py-1.5 text-sm font-semibold rounded-md transition-colors ${
              viewMode === "MY" ? "bg-card shadow-sm text-primary border" : "text-muted-foreground hover:text-foreground"
            }`}
          >
            My Requests
          </button>
        )}
      </div>

      <div className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatTile
          label="All requests" value={counts.ALL} icon={Inbox} fill={TILE_FILLS.violet}
          hint={isTL ? "From your team" : isHR ? "Across every team" : "In your scope"}
          active={tab === "ALL"} onClick={() => { setTab("ALL"); setSelected(new Set()); }}
        />
        <StatTile
          label="Pending" value={counts.PENDING} icon={Clock} fill={TILE_FILLS.amber}
          hint={counts.PENDING > 0 ? "Waiting on you" : "Nothing waiting"}
          active={tab === "PENDING"} onClick={() => { setTab("PENDING"); setSelected(new Set()); }}
        />
        <StatTile
          label="Approved" value={counts.APPROVED} icon={Check} fill={TILE_FILLS.green}
          hint="Granted" active={tab === "APPROVED"}
          onClick={() => { setTab("APPROVED"); setSelected(new Set()); }}
        />
        <StatTile
          label="Rejected" value={counts.REJECTED} icon={X} fill={TILE_FILLS.red}
          hint="Turned down" active={tab === "REJECTED"}
          onClick={() => { setTab("REJECTED"); setSelected(new Set()); }}
        />
      </div>

      <div className="mb-4 flex flex-wrap items-center gap-4">
        {teams.length > 1 && (
          <div className="flex items-center gap-2">
            <span className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
              Team
            </span>
            <Select
              value={teamFilter}
              onChange={(e) => setTeamFilter(e.target.value)}
              className="w-[180px] h-9 bg-card"
            >
              <option value="all">All teams ({rawRows.length})</option>
              {teams.map((t) => (
                <option key={t} value={t}>
                  {t} ({rawRows.filter((r) => (r.team || "").trim() === t).length})
                </option>
              ))}
            </Select>
          </div>
        )}
        <div className="flex flex-1 items-center gap-2 max-w-sm">
          <Input
            placeholder="Search employee, team, or reason..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="h-9 bg-card"
          />
        </div>
      </div>

      {pending.isLoading ? (
        <Skeleton className="h-64 w-full rounded-lg" />
      ) : list.length === 0 ? (
        <EmptyState
          icon={Inbox}
          title={tab === "ALL" ? "Nothing to approve" : `No ${tab.toLowerCase()} requests`}
          description={
            tab === "ALL"
              ? "When your team applies for leave, requests will land here."
              : teamFilter === "all"
                ? "Try another status tile above."
                : `Nothing ${tab.toLowerCase()} for ${teamFilter}.`
          }
        />
      ) : (
        <div className="rounded-lg border bg-card">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[1400px] table-fixed text-sm">
              <colgroup>
                {/* Action first: on a wide table the decision is the reason
                    somebody opened the page, and it was sitting past ten
                    columns of context where it had to be scrolled to. */}
                <col className="w-[110px]" />
                <col className="w-[190px]" />
                <col className="w-[130px]" />
                <col className="w-[130px]" />
                <col className="w-[60px]" />
                <col className="w-[200px]" />
                <col className="w-[180px]" />
                <col className="w-[130px]" />
                <col className="w-[130px]" />
                <col className="w-[120px]" />
                <col className="w-[100px]" />
              </colgroup>
              <thead>
                <tr className="border-b bg-muted/50 text-left align-middle text-xs font-bold text-muted-foreground uppercase tracking-wider [&>th]:px-3.5 [&>th]:py-3">
                  <th>Action</th>
                  <th>Employee</th>
                  <th>Team</th>
                  <th>Leave Type</th>
                  <th className="text-right">Days</th>
                  <th>Date Range</th>
                  <th>Reason</th>
                  <th>Requested To</th>
                  <th>Decided By</th>
                  <th>Applied On</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {pageRows.map((r) => {
                  return (
                    <tr key={r.id} className="align-top hover:bg-muted/30 transition-colors [&>td]:px-3.5 [&>td]:py-3">
                      {/*
                        One button, not three.

                        A tick and a cross in a table row decide somebody's
                        leave from a list, with no sight of the reason, the
                        dates or anything they attached. View opens the
                        request; the decision is made in there, having read
                        it. A row that cannot be decided still opens, because
                        reading it is not the same right as deciding it.
                      */}
                      <td>
                        <Button
                          variant={r.status === "PENDING" && r.canAct ? "default" : "outline"}
                          size="sm"
                          className="h-8 gap-1.5 px-3"
                          onClick={() => setViewModalData(r)}
                        >
                          <Eye className="h-3.5 w-3.5" />
                          {r.status === "PENDING" && r.canAct ? "Review" : "View"}
                        </Button>
                      </td>
                      <td>
                        <div className="flex items-center gap-2">
                          <Avatar name={r.employeeName} className="shrink-0 h-9 w-9 bg-primary/10 text-primary font-medium" />
                          <div className="flex flex-col">
                            <span className="truncate font-bold text-foreground text-sm" title={r.employeeName}>{r.employeeName}</span>
                            <span className="text-[11px] text-muted-foreground">{r.employeeCode || "—"}</span>
                          </div>
                        </div>
                      </td>
                      <td className="truncate text-sm font-medium text-muted-foreground" title={r.team || ""}>{r.team || "—"}</td>
                      <td>
                        <Badge className={`border-0 whitespace-nowrap ${
                          r.leaveTypeName.toLowerCase().includes('sick') ? 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400' :
                          r.leaveTypeName.toLowerCase().includes('earned') ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400' :
                          'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400'
                        }`}>
                          {r.leaveTypeName.replace(/\s*\(.*\)/, '')}
                        </Badge>
                      </td>
                      <td className="text-right font-bold tabular-nums">{r.workingDays}d</td>
                      <td className="whitespace-nowrap text-xs font-medium">
                        {dayjs(r.fromDate).format("DD MMM YYYY")} – {dayjs(r.toDate).format("DD MMM YYYY")}
                      </td>
                      <td className="max-w-[150px] truncate text-xs text-muted-foreground" title={r.reason}>{r.reason || "—"}</td>
                      <td className="text-xs">{r.requestedToName || "—"}</td>
                      <td className="text-xs">{r.decidedByName || "—"}</td>
                      <td className="whitespace-nowrap text-xs text-muted-foreground">
                        {r.createdAt ? dayjs(r.createdAt).format("DD MMM YYYY") : "—"}
                      </td>
                      <td>
                        <Badge className={`border-0 uppercase tracking-wider text-[10px] font-bold ${
                          r.status === "APPROVED" ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300"
                          : r.status === "REJECTED" ? "bg-rose-100 text-rose-700 dark:bg-rose-950/40 dark:text-rose-300"
                          : "bg-amber-100 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300"
                        }`}>
                          {r.status}
                        </Badge>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <div className="border-t px-4 py-2 text-xs text-muted-foreground">
            Showing {pageRows.length} of {list.length} request{list.length === 1 ? "" : "s"}
          </div>
          <TablePagination
            page={page} totalPages={totalPages} onChange={setPage}
            pageSize={pageSize} onPageSizeChange={setPageSize} total={total}
            always
          />
        </div>
      )}

      {viewModalData && (
        <Dialog open={!!viewModalData} onClose={() => setViewModalData(null)} className="sm:max-w-md">
          <div className="flex items-center gap-3 mb-6">
            <Avatar name={viewModalData.employeeName} className="h-10 w-10 bg-primary/10 text-primary" />
            <div>
              <div className="text-base font-bold">{viewModalData.employeeName}</div>
              <div className="text-xs font-normal text-muted-foreground">{viewModalData.team || viewModalData.employeeCode || "Employee"}</div>
            </div>
          </div>
          <div className="grid gap-4 py-4 text-sm">
            <div className="grid grid-cols-2 gap-2">
              <div className="font-medium text-muted-foreground">Leave Type</div>
              <div className="font-semibold">{viewModalData.leaveTypeName}</div>
              
              <div className="font-medium text-muted-foreground">Date Range</div>
              <div className="font-semibold">{dayjs(viewModalData.fromDate).format("DD MMM YYYY")} – {dayjs(viewModalData.toDate).format("DD MMM YYYY")}</div>
              
              <div className="font-medium text-muted-foreground">Working Days</div>
              <div className="font-semibold">{viewModalData.workingDays} days</div>

              <div className="font-medium text-muted-foreground">Status</div>
              <div>
                <Badge className={`border-0 uppercase tracking-wider text-[10px] font-bold ${
                  viewModalData.status === "APPROVED" ? "bg-emerald-100 text-emerald-700"
                  : viewModalData.status === "REJECTED" ? "bg-rose-100 text-rose-700"
                  : "bg-amber-100 text-amber-700"}`}>
                  {viewModalData.status}
                </Badge>
              </div>

              <div className="font-medium text-muted-foreground">Applied On</div>
              <div className="font-semibold">{dayjs(viewModalData.createdAt).format("DD MMM YYYY, hh:mm A")}</div>

              <div className="font-medium text-muted-foreground">Requested To</div>
              <div className="font-semibold">
                {viewModalData.requestedToName || "—"} 
                {viewModalData.requestedToRole && <span className="text-muted-foreground text-xs block font-normal">{viewModalData.requestedToRole}</span>}
              </div>

              {viewModalData.status !== "PENDING" && (
                <>
                  <div className="font-medium text-muted-foreground">Decided By</div>
                  <div className="font-semibold">
                    {viewModalData.decidedByName || "—"} 
                    {viewModalData.decidedByRole && <span className="text-muted-foreground text-xs block font-normal">{viewModalData.decidedByRole}</span>}
                  </div>
                </>
              )}
            </div>
            
            <div className="mt-2 space-y-1">
              <div className="font-medium text-muted-foreground">Reason for Leave</div>
              <div className="rounded-md bg-muted/30 p-3 text-sm border">
                {viewModalData.reason || <span className="italic text-muted-foreground">No reason provided</span>}
              </div>
            </div>

            {viewModalData.status === "REJECTED" && viewModalData.decisionComment && (
              <div className="mt-2 space-y-1">
                <div className="font-medium text-rose-600">Rejection Remark</div>
                <div className="rounded-md bg-rose-50 p-3 text-sm border border-rose-100 text-rose-800">
                  “{viewModalData.decisionComment}”
                </div>
              </div>
            )}

            {/*
              The decision, made here rather than from the table row.

              This is the point of moving it: the approver has the dates, the
              reason and the balance in front of them before they choose,
              instead of pressing a tick beside a name. A rejection still asks
              for a reason -- the applicant is owed one -- and the buttons are
              only rendered when the server said this person may decide this
              request, so a row somebody may read but not act on shows nothing
              to press.
            */}
            {viewModalData.status === "PENDING" && viewModalData.canAct && (
              <div className="mt-5 border-t pt-4">
                <div className="mb-2.5 text-xs font-bold uppercase tracking-wider text-muted-foreground">
                  Update status
                </div>
                <div className="flex flex-col gap-2 sm:flex-row">
                  <Button
                    className="flex-1 bg-emerald-600 text-white hover:bg-emerald-700"
                    disabled={decide.isPending}
                    onClick={() => {
                      decide.mutate({ id: viewModalData.id, decision: "APPROVED" });
                      setViewModalData(null);
                    }}
                  >
                    {decide.isPending
                      ? <Loader2 className="h-4 w-4 animate-spin" />
                      : "Approve"}
                  </Button>
                  <Button
                    variant="outline"
                    className="flex-1 border-rose-200 text-rose-600 hover:bg-rose-50 hover:text-rose-700"
                    disabled={decide.isPending}
                    onClick={() => {
                      const reason = window.prompt("Reason for rejection (required):");
                      if (!reason || !reason.trim()) return;
                      decide.mutate({
                        id: viewModalData.id,
                        decision: "REJECTED",
                        comment: reason.trim(),
                      });
                      setViewModalData(null);
                    }}
                  >
                    Reject
                  </Button>
                </div>
              </div>
            )}
          </div>
        </Dialog>
      )}
    </div>
  );
}
