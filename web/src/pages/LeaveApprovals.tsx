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
  const { hasPermission, hasRole } = useAuth();
  const isAdmin = hasPermission("USER_MANAGE");
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

  // Counts come from everything in scope, so the tiles stay meaningful whichever
  // tab or team is being looked at.
  const counts = useMemo(() => ({
    ALL: rawRows.length,
    PENDING: rawRows.filter((r) => r.status === "PENDING").length,
    APPROVED: rawRows.filter((r) => r.status === "APPROVED").length,
    REJECTED: rawRows.filter((r) => r.status === "REJECTED").length
  }), [rawRows]);

  // HR and admin look across teams, so they get a team picker.
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

            <thead>
              <tr className="border-b border-slate-300 dark:border-slate-700 bg-slate-100/90 dark:bg-slate-800/90 text-left align-middle text-xs font-semibold text-slate-800 dark:text-slate-200 [&>th]:whitespace-nowrap [&>th]:px-3.5 [&>th]:py-3 [&>th]:border-r [&>th]:border-slate-300 dark:[&>th]:border-slate-700 last:[&>th]:border-r-0">
                <th>
                  <div className="flex items-center gap-1.5">
                    <span>Employee</span>
                    <span className="text-[10px] text-slate-400 font-mono tracking-tighter">↑↓</span>
                  </div>
                </th>
                <th>
                  <div className="flex items-center gap-1.5">
                    <span>Team</span>
                    <span className="text-[10px] text-slate-400 font-mono tracking-tighter">↑↓</span>
                  </div>
                </th>
                <th>
                  <div className="flex items-center gap-1.5">
                    <span>Leave Type</span>
                    <span className="text-[10px] text-slate-400 font-mono tracking-tighter">↑↓</span>
                  </div>
                </th>
                <th className="text-right">
                  <div className="flex items-center justify-end gap-1.5">
                    <span>Days</span>
                    <span className="text-[10px] text-slate-400 font-mono tracking-tighter">↑↓</span>
                  </div>
                </th>
                <th>
                  <div className="flex items-center gap-1.5">
                    <span>Date Range</span>
                    <span className="text-[10px] text-slate-400 font-mono tracking-tighter">↑↓</span>
                  </div>
                </th>
                <th>
                  <div className="flex items-center gap-1.5">
                    <span>Reason</span>
                    <span className="text-[10px] text-slate-400 font-mono tracking-tighter">↑↓</span>
                  </div>
                </th>
                <th>
                  <div className="flex items-center gap-1.5">
                    <span>Requested To</span>
                    <span className="text-[10px] text-slate-400 font-mono tracking-tighter">↑↓</span>
                  </div>
                </th>
                <th>
                  <div className="flex items-center gap-1.5">
                    <span>Decided By</span>
                    <span className="text-[10px] text-slate-400 font-mono tracking-tighter">↑↓</span>
                  </div>
                </th>
                <th>
                  <div className="flex items-center gap-1.5">
                    <span>Applied On</span>
                    <span className="text-[10px] text-slate-400 font-mono tracking-tighter">↑↓</span>
                  </div>
                </th>
                <th>
                  <div className="flex items-center gap-1.5">
                    <span>Status</span>
                    <span className="text-[10px] text-slate-400 font-mono tracking-tighter">↑↓</span>
                  </div>
                </th>
                <th className="text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {pageRows.map((r) => {
                return (
                  <tr key={r.id} className="border-b border-slate-200 dark:border-slate-800 align-top last:border-0 hover:bg-slate-50 dark:hover:bg-slate-800/50 transition-colors [&>td]:px-3.5 [&>td]:py-3 [&>td]:border-r [&>td]:border-b [&>td]:border-slate-200 dark:[&>td]:border-slate-800 last:[&>td]:border-r-0">
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
                        {r.status === "PENDING" && r.canAct && viewMode === "ALL" ? (
                          <>
                            <Button
                              variant="outline"
                              size="icon"
                              className="h-8 w-8 rounded text-emerald-600 border-emerald-200 hover:bg-emerald-50 hover:text-emerald-700 shadow-sm"
                              disabled={decide.isPending}
                              onClick={() => decide.mutate({ id: r.id, decision: "APPROVED" })}
                            >
                              <Check className="h-4 w-4" />
                            </Button>
                            <Button
                              variant="outline"
                              size="icon"
                              className="h-8 w-8 rounded text-rose-600 border-rose-200 hover:bg-rose-50 hover:text-rose-700 shadow-sm"
                              disabled={decide.isPending}
                              onClick={() => rejectOne(r.id)}
                            >
                              <X className="h-4 w-4" />
                            </Button>
                          </>
                        ) : null}
                        <Button 
                          variant="outline" 
                          size="icon" 
                          className="h-8 w-8 rounded text-muted-foreground hover:text-foreground hover:bg-muted shadow-sm"
                          onClick={() => setViewModalData(r)}
                        >
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

      {/* Leave Details Modal */}
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
            </div>
        </Dialog>
      )}
    </div>
  );
}
