import { useMemo, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Check, X, Loader2, CheckCheck, Inbox, ListTodo, Clock, Eye } from "lucide-react";
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
import type { ApiEnvelope, LeaveRequest, EmployeeTaskGroup } from "@/types";
import { usePagedRows, TablePagination } from "@/components/ui/table-pagination";
import { useAuth } from "@/hooks/useAuth";
import { StatTile, TILE_FILLS } from "@/components/ui/stat-tile";

export default function LeaveApprovalsPage() {
  const qc = useQueryClient();
  const { hasPermission, hasRole } = useAuth();
  const isAdmin = hasPermission("USER_MANAGE");
  const isHR = hasRole("IT_MGR") || hasRole("IT_HR");
  const isTL = hasRole("IT_TL") && !isHR && !isAdmin;
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [tab, setTab] = useState<"PENDING" | "APPROVED" | "REJECTED" | "ALL">("ALL");
  const [teamFilter, setTeamFilter] = useState("all");

  const pending = useQuery({
    queryKey: ["leave", "for-me"],
    queryFn: async () =>
      (await api.get<ApiEnvelope<LeaveRequest[]>>("/leave/requests-for-me")).data.data
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

  const rows = pending.data ?? [];

  // Counts come from everything in scope, so the tiles stay meaningful whichever
  // tab or team is being looked at.
  const counts = useMemo(() => ({
    ALL: rows.length,
    PENDING: rows.filter((r) => r.status === "PENDING").length,
    APPROVED: rows.filter((r) => r.status === "APPROVED").length,
    REJECTED: rows.filter((r) => r.status === "REJECTED").length
  }), [rows]);

  // HR and admin look across teams, so they get a team picker.
  const teams = useMemo(
    () => [...new Set(rows.map((r) => (r.team || "").trim()).filter(Boolean))].sort(),
    [rows]
  );

  const list = rows.filter((r) => {
    if (tab !== "ALL" && r.status !== tab) return false;
    if (teamFilter !== "all" && (r.team || "").trim() !== teamFilter) return false;
    return true;
  });
  const { pageRows, page, setPage, totalPages, pageSize, setPageSize, total } =
    usePagedRows(list, 15, [tab, teamFilter, pending.data]);

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
    if (reason === null) return null;                 // cancelled
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

      {/* Counts as tiles — each one is also the filter for that status. */}
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

      {/* Team picker — only useful when more than one team is in scope. */}
      {teams.length > 1 && (
        <div className="mb-4 flex items-center gap-2">
          <span className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
            Team
          </span>
          <Select
            value={teamFilter}
            onChange={(e) => setTeamFilter(e.target.value)}
            className="max-w-[220px]"
          >
            <option value="all">All teams ({rows.length})</option>
            {teams.map((t) => (
              <option key={t} value={t}>
                {t} ({rows.filter((r) => (r.team || "").trim() === t).length})
              </option>
            ))}
          </Select>
        </div>
      )}

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
        <div className="rounded-lg border">
          {/* Only the table itself scrolls sideways, so the count and the pager
              below stay where they are put. */}
          <div className="overflow-x-auto">
            <table className="w-full min-w-[1240px] table-fixed text-sm">
            <colgroup>
              <col className="w-[180px]" />
              <col className="w-[140px]" />
              <col className="w-[120px]" />
              <col className="w-14" />
              <col className="w-[140px]" />
              <col className="w-[150px]" />
              <col className="w-[140px]" />
              <col className="w-[140px]" />
              <col className="w-[120px]" />
              <col className="w-[100px]" />
              <col className="w-[150px]" />
            </colgroup>
            <thead>
              <tr className="border-b bg-muted/10 text-left align-middle text-[11px] font-bold text-muted-foreground [&>th]:whitespace-nowrap [&>th]:px-3 [&>th]:py-3">
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
                <th className="text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {pageRows.map((r) => {
                return (
                  <tr key={r.id} className="border-b align-top last:border-0 hover:bg-muted/10 [&>td]:px-3 [&>td]:py-4">
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
                    <td className="text-right text-sm font-bold text-muted-foreground tabular-nums">{r.workingDays}d</td>
                    <td className="text-sm font-medium text-muted-foreground">
                      <div className="flex flex-col">
                        <span>{dayjs(r.fromDate).format("DD MMM YYYY")}</span>
                        <span>– {dayjs(r.toDate).format("DD MMM YYYY")}</span>
                      </div>
                    </td>
                    <td>
                      <span className="line-clamp-2 block break-words text-sm font-medium text-muted-foreground" title={r.reason || ""}>
                        {r.reason ? `“${r.reason}”` : "—"}
                      </span>
                    </td>
                    <td className="text-sm">
                      <span className="block truncate text-foreground font-medium" title={r.requestedToName || ""}>
                        {r.requestedToName || "—"}
                      </span>
                      {r.requestedToName && r.requestedToRole && (
                        <div className="text-[11px] font-bold text-primary">{r.requestedToRole}</div>
                      )}
                    </td>
                    <td className="text-sm">
                      <span className="block truncate font-medium text-foreground" title={r.decidedByName || ""}>
                        {r.decidedByName || "—"}
                      </span>
                      {r.decidedByName && r.decidedByRole && (
                        <div className="text-[11px] font-bold text-primary">{r.decidedByRole}</div>
                      )}
                      {r.decidedAt && <div className="text-[11px] text-muted-foreground">{dayjs(r.decidedAt).format("DD MMM YYYY")}</div>}
                    </td>
                    <td className="text-sm font-medium text-muted-foreground">
                      <div className="flex flex-col">
                        <span>{dayjs(r.createdAt).format("DD MMM YYYY")}</span>
                        <span>{dayjs(r.createdAt).format("hh:mm A")}</span>
                      </div>
                    </td>
                    <td>
                      <Badge className={`border-0 text-[11px] font-bold tracking-wider ${
                        r.status === "APPROVED" ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400"
                        : r.status === "REJECTED" ? "bg-rose-100 text-rose-700 dark:bg-rose-900/30 dark:text-rose-400"
                        : "bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400"}`}>
                        {r.status.charAt(0) + r.status.slice(1).toLowerCase()}
                      </Badge>
                    </td>
                    <td>
                      <div className="flex items-center justify-end gap-1.5">
                        {r.status === "PENDING" && r.canAct ? (
                          <>
                            <Button
                              variant="outline"
                              size="icon"
                              className="h-8 w-8 rounded text-emerald-600 border-emerald-200 hover:bg-emerald-50 hover:text-emerald-700"
                              disabled={decide.isPending}
                              onClick={() => decide.mutate({ id: r.id, decision: "APPROVED" })}
                            >
                              <Check className="h-4 w-4" />
                            </Button>
                            <Button
                              variant="outline"
                              size="icon"
                              className="h-8 w-8 rounded text-rose-600 border-rose-200 hover:bg-rose-50 hover:text-rose-700"
                              disabled={decide.isPending}
                              onClick={() => rejectOne(r.id)}
                            >
                              <X className="h-4 w-4" />
                            </Button>
                          </>
                        ) : null}
                        <Button variant="outline" size="icon" className="h-8 w-8 rounded text-muted-foreground hover:text-foreground">
                          <Eye className="h-4 w-4" />
                        </Button>
                      </div>
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
    </div>
  );
}
