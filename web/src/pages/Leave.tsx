import { CustomLoader as Loader2 } from "@/components/ui/custom-loader";
import { useState, useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, Clock, CalendarDays, AlertTriangle, Eye, Download, X, Plus, CalendarX2, FileSpreadsheet } from "lucide-react";
import dayjs from "dayjs";
import * as XLSX from "xlsx";
import toast from "react-hot-toast";
import { api, apiMessage } from "@/lib/api";
import { PageHeader } from "@/components/PageHeader";
import { EmptyState } from "@/components/EmptyState";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge, statusVariant } from "@/components/ui/badge";
import { Dialog, DialogHeader } from "@/components/ui/dialog";
import { Select } from "@/components/ui/select";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow
} from "@/components/ui/table";
import type { ApiEnvelope, PageEnvelope, LeaveType, LeaveBalance, LeaveRequest } from "@/types";
import { useAuth } from "@/hooks/useAuth";
import { usePagedRows, TablePagination } from "@/components/ui/table-pagination";
import { todayIso } from "@/lib/dates";

/** Red asterisk shown on every field that must be filled. */
function Req() {
  return <span className="ml-0.5 text-destructive">*</span>;
}

const schema = z
  .object({
    leaveTypeId: z.string().min(1, "Choose a leave type"),
    fromDate: z.string().min(1, "Start date required"),
    toDate: z.string().min(1, "End date required"),
    reason: z.string().trim().min(1, "Reason is required"),
    requestedTo: z.string().min(1, "Choose an approver")
  })
  .refine((v) => v.toDate >= v.fromDate, {
    message: "End date can't be before start date",
    path: ["toDate"]
  });

type FormValues = z.infer<typeof schema>;

export default function LeavePage() {
  const { hasRole } = useAuth();
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  // Date range (month granularity): default = start of this year → current month.
  const [fromMonth, setFromMonth] = useState(dayjs().startOf("year").format("YYYY-MM"));
  const [toMonth, setToMonth] = useState(dayjs().format("YYYY-MM"));

  const types = useQuery({
    queryKey: ["leave", "types"],
    queryFn: async () =>
      (await api.get<ApiEnvelope<LeaveType[]>>("/leave/types")).data.data
  });

  const balances = useQuery({
    queryKey: ["leave", "balances"],
    queryFn: async () =>
      (await api.get<ApiEnvelope<LeaveBalance[]>>("/leave/balances")).data.data
  });

  const requests = useQuery({
    queryKey: ["leave", "me"],
    queryFn: async () => {
      const res = await api.get<PageEnvelope<LeaveRequest>>("/leave/me?size=50");
      return res.data?.content || [];
    }
  });

  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors }
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  // Days in the selected range (calendar) — used to fetch valid approvers.
  const fromV = watch("fromDate");
  const toV = watch("toDate");
  const dayCount = fromV && toV ? Math.max(1, dayjs(toV).diff(dayjs(fromV), "day") + 1) : 1;
  const approvers = useQuery({
    queryKey: ["leave-approvers", dayCount],
    enabled: open,
    queryFn: async () =>
      (await api.get<ApiEnvelope<{ id: number; name: string; code: string }[]>>(
        `/leave/approvers?days=${dayCount}`)).data.data
  });

  useEffect(() => {
    if (approvers.data && approvers.data.length === 1 && !watch("requestedTo")) {
      reset({ ...watch(), requestedTo: String(approvers.data[0].id) });
    }
  }, [approvers.data, reset, watch]);

  const apply = useMutation({
    mutationFn: async (values: FormValues) =>
      api.post("/leave/apply", {
        leaveTypeId: Number(values.leaveTypeId),
        fromDate: values.fromDate,
        toDate: values.toDate,
        reason: values.reason || undefined,
        requestedTo: values.requestedTo ? Number(values.requestedTo) : undefined
      }),
    onSuccess: () => {
      toast.success("Leave request submitted");
      qc.invalidateQueries({ queryKey: ["leave"] });
      qc.invalidateQueries({ queryKey: ["dashboard"] });
      setOpen(false);
      reset();
    },
    onError: (err) => toast.error(apiMessage(err, "Could not submit leave"))
  });

  const cancel = useMutation({
    mutationFn: async (id: number) => api.post(`/leave/${id}/cancel`),
    onSuccess: () => {
      toast.success("Leave cancelled");
      qc.invalidateQueries({ queryKey: ["leave"] });
    },
    onError: (err) => toast.error(apiMessage(err, "Could not cancel"))
  });

  // A leave falls in range if its start month is within [fromMonth, toMonth].
  const monthInRange = (d?: string) => {
    if (!d) return false;
    const m = String(d).slice(0, 7);
    return m >= fromMonth && m <= toMonth;
  };
  const filteredRequests = (requests.data ?? []).filter((r) => monthInRange(r.fromDate));
  // Paged with the numbers and rows-per-page, like every other table.
  const reqPaged = usePagedRows(filteredRequests, 15, [requests.data, fromMonth, toMonth]);

  function exportExcel() {
    // Leaves whose start date falls within the selected month range.
    const rows = [...filteredRequests].sort((a, b) =>
      String(a.fromDate).localeCompare(String(b.fromDate))
    );
    if (rows.length === 0) {
      toast.error(
        `No leaves found for ${dayjs(fromMonth).format("MMM YYYY")} – ${dayjs(toMonth).format("MMM YYYY")}.`
      );
      return;
    }
    const header = ["S.No", "Leave Type", "From Date", "To Date", "Days", "Reason", "Status", "Applied On"];
    const data = rows.map((r, i) => [
      i + 1,
      r.leaveTypeName,
      dayjs(r.fromDate).format("DD-MMM-YYYY"),
      dayjs(r.toDate).format("DD-MMM-YYYY"),
      r.workingDays,
      r.reason || "",
      r.status,
      r.createdAt ? dayjs(r.createdAt).format("DD-MMM-YYYY") : ""
    ]);
    const ws = XLSX.utils.aoa_to_sheet([header, ...data]);
    // Widths so the reason and the dates open readable rather than truncated.
    ws["!cols"] = [{ wch: 6 }, { wch: 18 }, { wch: 14 }, { wch: 14 },
                   { wch: 7 }, { wch: 40 }, { wch: 12 }, { wch: 14 }];
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, "My Leaves");
    XLSX.writeFile(wb, `My_Leaves_${fromMonth}_to_${toMonth}.xlsx`);
    toast.success("Leave report downloaded");
  }

  // Merge all system leave types and user balance records to ensure every type has a card
  const mergedBalances = types.data?.map(t => {
    const bal = balances.data?.find(b => b.leaveTypeId === t.id);
    if (bal) {
      return {
        leaveTypeId: t.id,
        leaveTypeName: t.name,
        leaveTypeCode: t.code,
        allocated: Number(bal.allocated),
        available: Number(bal.available),
        used: Number(bal.used),
        hasAllocatedBalance: true
      };
    }
    
    // Count used days from request history for types without set balance in DB (like Loss of Pay)
    const usedCount = requests.data
      ?.filter(r => r.leaveTypeName === t.name && r.status === "APPROVED")
      ?.reduce((sum, r) => sum + r.workingDays, 0) || 0;
      
    const maxDays = t.maxDaysPerYear != null ? Number(t.maxDaysPerYear) : null;
      
    return {
      leaveTypeId: t.id,
      leaveTypeName: t.name,
      leaveTypeCode: t.code,
      allocated: maxDays ?? 0,
      available: maxDays != null ? Math.max(0, maxDays - usedCount) : 0,
      used: usedCount,
      hasAllocatedBalance: maxDays != null
    };
  }) || [];

  // Below the hooks, not above them. Sitting above, this return skipped every
  // hook after it — and the signed-in user is not known on the very first
  // render, so a Super Admin's second render had a different number of hooks and
  // React refused it outright.
  if (hasRole("SUPER_ADMIN")) {
    return (
      <div className="mx-auto max-w-md py-20 text-center">
        <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-destructive/10">
          <CalendarX2 className="h-6 w-6 text-destructive" />
        </div>
        <h2 className="font-display text-lg font-semibold text-foreground">Restricted Access</h2>
        <p className="mt-2 text-sm text-muted-foreground leading-relaxed">
          Super Admins do not have personal leave management profiles. Please use the Approvals or Leave Policies sections.
        </p>
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title="Leave"
        subtitle="Check balances, apply, and track your requests."
        actions={
          <div className="flex flex-wrap items-end gap-2">
            <div className="flex flex-col">
              <label className="mb-0.5 text-[10px] font-semibold uppercase text-muted-foreground">From</label>
              <input
                type="month"
                value={fromMonth}
                max={toMonth}
                onChange={(e) => setFromMonth(e.target.value)}
                className="rounded-md border border-input bg-background px-3 py-1.5 text-sm shadow-sm focus:outline-none focus:ring-1 focus:ring-primary"
              />
            </div>
            <div className="flex flex-col">
              <label className="mb-0.5 text-[10px] font-semibold uppercase text-muted-foreground">To</label>
              <input
                type="month"
                value={toMonth}
                min={fromMonth}
                onChange={(e) => setToMonth(e.target.value)}
                className="rounded-md border border-input bg-background px-3 py-1.5 text-sm shadow-sm focus:outline-none focus:ring-1 focus:ring-primary"
              />
            </div>
            <Button variant="outline" onClick={exportExcel} className="bg-green-600 text-white hover:bg-green-700 border-0">
              <FileSpreadsheet className="mr-1.5 h-4 w-4" /> Export Excel
            </Button>
            <Button onClick={() => setOpen(true)}>
              <Plus className="h-4 w-4" /> Apply for leave
            </Button>
          </div>
        }
      />

      {/* Balances */}
      {balances.isLoading || types.isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-24" />
          ))}
        </div>
      ) : mergedBalances.length === 0 ? (
        <EmptyState
          icon={CalendarX2}
          title="No balances allocated"
          description="Leave balances for the year haven't been set up for your account yet."
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {mergedBalances.map((b) => {
            const pct = b.hasAllocatedBalance && b.allocated > 0 
              ? (b.available / b.allocated) * 100 
              : 0;
            return (
              <Card key={b.leaveTypeId}>
                <CardContent className="p-5">
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-medium">{b.leaveTypeName}</span>
                    <Badge variant="secondary" className="code-chip">{b.leaveTypeCode}</Badge>
                  </div>
                  <div className="mt-2 flex items-end gap-1">
                    {b.hasAllocatedBalance ? (
                      <>
                        <span className="font-display text-3xl font-bold">{b.available}</span>
                        <span className="pb-1 text-sm text-muted-foreground">/ {b.allocated} left</span>
                      </>
                    ) : (
                      <>
                        <span className="font-display text-3xl font-bold">—</span>
                        <span className="pb-1 text-sm text-muted-foreground">/ Unlimited</span>
                      </>
                    )}
                  </div>
                  <div className="mt-3 h-1.5 w-full overflow-hidden rounded-full bg-muted">
                    <div
                      className="h-full rounded-full bg-primary"
                      style={{ width: b.hasAllocatedBalance ? `${Math.max(4, Math.min(100, pct))}%` : "0%" }}
                    />
                  </div>
                  <div className="mt-1 text-xs text-muted-foreground">{b.used} used</div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}

      {/* My requests */}
      <Card className="mt-6">
        <CardHeader>
          <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
            <CardTitle>My requests</CardTitle>
            <span className="text-xs text-muted-foreground">
              {dayjs(fromMonth).format("MMM YYYY")} – {dayjs(toMonth).format("MMM YYYY")} ·{" "}
              {filteredRequests.length} {filteredRequests.length === 1 ? "request" : "requests"}
            </span>
          </div>
        </CardHeader>
        <CardContent>
          {requests.isLoading ? (
            <Skeleton className="h-40" />
          ) : filteredRequests.length === 0 ? (
            <EmptyState
              title="No leave requests in this range"
              description="Adjust the From / To range above to see other months."
            />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Leave Type</TableHead>
                  <TableHead>Applied On</TableHead>
                  <TableHead>Leave Dates</TableHead>
                  <TableHead>Days</TableHead>
                  <TableHead>Reason</TableHead>
                  <TableHead>Requested To</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Remark</TableHead>
                  <TableHead className="text-right">Action</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {reqPaged.pageRows.map((r, i) => (
                  <TableRow key={r.id} className="hover:bg-muted/10">
                    <TableCell className="font-medium text-foreground">{r.leaveTypeName}</TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {dayjs(r.createdAt).format("DD MMM YYYY")}
                    </TableCell>
                    <TableCell className="text-sm">
                      {dayjs(r.fromDate).format("DD MMM YYYY")} – {dayjs(r.toDate).format("DD MMM YYYY")}
                    </TableCell>
                    <TableCell className="text-sm">{r.workingDays}</TableCell>
                    <TableCell className="max-w-[150px] truncate text-sm" title={r.reason}>
                      {r.reason || "—"}
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      <span className="block truncate font-medium text-foreground" title={r.requestedToName || ""}>
                        {r.requestedToName || "—"}
                      </span>
                    </TableCell>
                    <TableCell>
                      <Badge className={`border-0 uppercase tracking-wider text-[10px] font-bold ${
                        r.status === 'APPROVED' ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400' :
                        r.status === 'REJECTED' ? 'bg-rose-100 text-rose-700 dark:bg-rose-900/30 dark:text-rose-400' :
                        'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400'
                      }`}>
                        {r.status}
                      </Badge>
                    </TableCell>
                    <TableCell className="max-w-[160px] truncate text-sm text-muted-foreground" title={r.decisionComment}>
                      {r.status === "REJECTED" ? (r.decisionComment || "—") : "—"}
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex items-center justify-end gap-2">
                        {r.status === "PENDING" ? (
                          <Button
                            variant="outline"
                            className="h-8 rounded px-2 text-foreground"
                            disabled={cancel.isPending}
                            onClick={() => {
                              if (window.confirm("Cancel this leave request?")) cancel.mutate(r.id);
                            }}
                          >
                            <X className="mr-1 h-3 w-3" /> Cancel
                          </Button>
                        ) : r.status === "APPROVED" ? (
                          <Button variant="outline" size="icon" className="h-8 w-8 rounded text-primary">
                            <Download className="h-4 w-4" />
                          </Button>
                        ) : null}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
        {filteredRequests.length > 0 && (
          <TablePagination
            page={reqPaged.page} totalPages={reqPaged.totalPages} onChange={reqPaged.setPage}
            pageSize={reqPaged.pageSize} onPageSizeChange={reqPaged.setPageSize}
            total={reqPaged.total}
            always
          />
        )}
      </Card>

      {/* Apply dialog */}
      <Dialog open={open} onClose={() => setOpen(false)}>
        <DialogHeader
          title="Apply for leave"
          description="Weekends and holidays are excluded from the day count automatically."
        />
        <form onSubmit={handleSubmit((v) => apply.mutate(v))} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="leaveTypeId">Leave type<Req /></Label>
            <Select id="leaveTypeId" {...register("leaveTypeId")}>
              <option value="">Select…</option>
              {types.data?.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name} ({t.code})
                </option>
              ))}
            </Select>
            {errors.leaveTypeId && (
              <p className="text-xs text-destructive">{errors.leaveTypeId.message}</p>
            )}
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="fromDate">From<Req /></Label>
              <Input id="fromDate" type="date" min={todayIso()} {...register("fromDate")} />
              {errors.fromDate && (
                <p className="text-xs text-destructive">{errors.fromDate.message}</p>
              )}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="toDate">To<Req /></Label>
              <Input id="toDate" type="date" min={watch("fromDate") || todayIso()} {...register("toDate")} />
              {errors.toDate && (
                <p className="text-xs text-destructive">{errors.toDate.message}</p>
              )}
            </div>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="requestedTo">Request to<Req /></Label>
            <Select id="requestedTo" {...register("requestedTo")}>
              <option value="">{approvers.isLoading ? "Loading…" : "Select approver"}</option>
              {(approvers.data ?? []).map((a) => (
                <option key={a.id} value={a.id}>{a.name} ({a.code})</option>
              ))}
            </Select>
            {errors.requestedTo && (
              <p className="text-xs text-destructive">{errors.requestedTo.message}</p>
            )}
            <p className="text-[11px] text-muted-foreground">
              Up to 3 days goes to your Team Leader; more than 3 days goes to HR.
              The list shows only the approver for the dates chosen.
            </p>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="reason">Reason<Req /></Label>
            <Textarea id="reason" rows={3} placeholder="Why are you taking this leave?" {...register("reason")} />
            {errors.reason && (
              <p className="text-xs text-destructive">{errors.reason.message}</p>
            )}
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={apply.isPending}>
              {apply.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
              Submit request
            </Button>
          </div>
        </form>
      </Dialog>
    </div>
  );
}
