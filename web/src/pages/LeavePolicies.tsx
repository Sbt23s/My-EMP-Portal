import { CustomLoader as Loader2 } from "@/components/ui/custom-loader";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  Calendar, Settings, Plus, Trash2, CalendarCheck, Users, Pencil, Search
} from "lucide-react";
import toast from "react-hot-toast";
import { api, apiMessage } from "@/lib/api";
import { PageHeader } from "@/components/PageHeader";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Dialog, DialogHeader } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { ApiEnvelope, LeaveType, HolidayResponse } from "@/types";
import { useAuth } from "@/hooks/useAuth";
import { todayIso } from "@/lib/dates";
import { usePagedRows, TablePagination } from "@/components/ui/table-pagination";

export default function LeavePoliciesPage() {
  const queryClient = useQueryClient();
  const { hasPermission } = useAuth();
  const [activeTab, setActiveTab] = useState<"types" | "holidays">("types");
  const [createTypeOpen, setCreateTypeOpen] = useState(false);
  const [editType, setEditType] = useState<LeaveType | null>(null);
  const [createHolidayOpen, setCreateHolidayOpen] = useState(false);
  // Allocation only ever runs forward: this year or a year still to come. A
  // year that has already gone by cannot be allocated.
  const thisYear = new Date().getFullYear();
  const [allocYear, setAllocYear] = useState<number>(thisYear);
  /** Narrows the leave-type table by name or code. */
  const [typeQuery, setTypeQuery] = useState("");
  const allocYearIsPast = Number.isFinite(allocYear) && allocYear < thisYear;

  // Fetch Leave Types
  const leaveTypes = useQuery({
    queryKey: ["leave-types"],
    queryFn: async () => (await api.get<ApiEnvelope<LeaveType[]>>("/leave/types")).data.data
  });

  // Fetch Holidays
  const holidays = useQuery({
    queryKey: ["holidays"],
    queryFn: async () => (await api.get<ApiEnvelope<HolidayResponse[]>>("/org/holidays")).data.data
  });

  const deleteTypeMutation = useMutation({
    mutationFn: async (id: number) => {
      await api.delete(`/leave/types/${id}`);
    },
    onSuccess: () => {
      toast.success("Leave type deleted");
      queryClient.invalidateQueries({ queryKey: ["leave-types"] });
    },
    onError: (err) => toast.error(apiMessage(err, "Failed to delete leave type"))
  });

  const deleteHolidayMutation = useMutation({
    mutationFn: async (id: number) => {
      await api.delete(`/org/holidays/${id}`);
    },
    onSuccess: () => {
      toast.success("Holiday deleted");
      queryClient.invalidateQueries({ queryKey: ["holidays"] });
    },
    onError: (err) => toast.error(apiMessage(err, "Failed to delete holiday"))
  });

  const allocateMutation = useMutation({
    mutationFn: async (year: number) =>
      (await api.post<ApiEnvelope<{ created: number; employees: number; year: number }>>(
        `/leave/allocations/apply-defaults?year=${year}`
      )).data.data,
    onSuccess: (res) => {
      if (res.created > 0) {
        toast.success(`Allocated leave to ${res.employees} employees (${res.created} balances created) for ${res.year}`);
      } else {
        toast.success(`All ${res.employees} employees already have their ${res.year} leave allocated`);
      }
      queryClient.invalidateQueries({ queryKey: ["leave-types"] });
    },
    onError: (err) => toast.error(apiMessage(err, "Failed to allocate leave balances"))
  });

  const canManage = hasPermission("ORG_MANAGE");

  /** Leave types matching the search, by name or by code. */
  /** Leave types matching the search, by name or by code. */
  const filteredTypes = (leaveTypes.data ?? []).filter((t) => {
    const needle = typeQuery.trim().toLowerCase();
    if (!needle) return true;
    return `${t.name ?? ""} ${t.code ?? ""}`.toLowerCase().includes(needle);
  });

  /** Both tables paged, with the numbers and rows-per-page. */
  const typesPaged = usePagedRows(filteredTypes, 10, [typeQuery, leaveTypes.data]);
  const holidaysPaged = usePagedRows(holidays.data ?? [], 10, [holidays.data]);

  return (
    <div>
      <PageHeader 
        title="Leave Policies & Holidays" 
        subtitle="Manage organization leave types and holiday calendar." 
      />

      <div className="flex gap-2 mb-6 border-b pb-2">
        <button
          className={`px-4 py-2 text-sm font-medium rounded-md transition-colors ${
            activeTab === "types" ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:bg-muted"
          }`}
          onClick={() => setActiveTab("types")}
        >
          <Settings className="w-4 h-4 inline mr-2" /> Leave Types
        </button>
        <button
          className={`px-4 py-2 text-sm font-medium rounded-md transition-colors ${
            activeTab === "holidays" ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:bg-muted"
          }`}
          onClick={() => setActiveTab("holidays")}
        >
          <Calendar className="w-4 h-4 inline mr-2" /> Holidays
        </button>
      </div>

      {activeTab === "types" && (
        <div className="space-y-4">
          {canManage && (
            <Card className="border-primary/30 bg-primary/5">
              <CardContent className="p-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div className="flex items-start gap-3">
                  <div className="rounded-lg bg-primary/10 p-2 text-primary">
                    <CalendarCheck className="h-5 w-5" />
                  </div>
                  <div>
                    <h4 className="font-semibold text-sm">Allocate leave to all employees</h4>
                    <p className="text-xs text-muted-foreground max-w-md">
                      Gives every employee their annual balance using each type&apos;s max days
                      (e.g. Casual 12, Sick 12, Earned 18). Employees must have a balance before
                      they can apply. Safe to run again — existing balances are kept.
                    </p>
                  </div>
                </div>
                <div className="flex items-start gap-2 shrink-0">
                  <div className="flex flex-col">
                    <Label htmlFor="allocYear" className="text-[10px] uppercase text-muted-foreground">Year</Label>
                    <Input
                      id="allocYear"
                      type="number"
                      value={allocYear}
                      min={thisYear}
                      max={thisYear + 10}
                      onChange={(e) => setAllocYear(Number(e.target.value))}
                      className={`h-9 w-24 ${allocYearIsPast ? "border-destructive ring-1 ring-destructive" : ""}`}
                    />
                    <p className={`mt-1 text-[10px] ${allocYearIsPast ? "font-medium text-destructive" : "text-muted-foreground"}`}>
                      {allocYearIsPast ? `${thisYear} or later only` : `From ${thisYear} onwards`}
                    </p>
                  </div>
                  <Button
                    className="mt-[18px] bg-green-600 text-white hover:bg-green-700"
                    disabled={allocateMutation.isPending || allocYearIsPast}
                    onClick={() => {
                      if (allocYear < thisYear) {
                        toast.error(`A past year cannot be allocated — pick ${thisYear} or later`);
                        return;
                      }
                      if (confirm(`Allocate default leave balances to all employees for ${allocYear}?`)) {
                        allocateMutation.mutate(allocYear);
                      }
                    }}
                  >
                    {allocateMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : <Users className="h-4 w-4 mr-2" />}
                    Allocate to all
                  </Button>
                </div>
              </CardContent>
            </Card>
          )}
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h3 className="font-semibold text-lg">Leave Types</h3>
            <div className="flex flex-wrap items-center gap-2">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  className="h-9 w-64 pl-9"
                  placeholder="Search by name or code…"
                  value={typeQuery}
                  onChange={(e) => setTypeQuery(e.target.value)}
                />
              </div>
              {canManage && (
                <Button size="sm" onClick={() => setCreateTypeOpen(true)}><Plus className="w-4 h-4 mr-2" /> Add Leave Type</Button>
              )}
            </div>
          </div>
          
          {leaveTypes.isLoading ? (
            <Skeleton className="h-40 w-full" />
          ) : (
            <Card>
              <CardContent className="p-0">
                <table className="w-full text-sm text-left">
                  <thead className="bg-muted text-muted-foreground">
                    <tr>
                      <th className="px-4 py-3 font-medium">Name</th>
                      <th className="px-4 py-3 font-medium">Code</th>
                      <th className="px-4 py-3 font-medium">Max Days/Year</th>
                      <th className="px-4 py-3 font-medium">Pay</th>
                      <th className="px-4 py-3 font-medium text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {typesPaged.pageRows.map(t => (
                      <tr key={t.id} className="hover:bg-muted/50">
                        <td className="px-4 py-3 font-medium">{t.name}</td>
                        <td className="px-4 py-3 text-muted-foreground">{t.code}</td>
                        <td className="px-4 py-3 text-muted-foreground">{t.maxDaysPerYear || "Unlimited"}</td>
                        <td className="px-4 py-3">
                          {t.paid
                            ? <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400">Paid</span>
                            : <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700 dark:bg-amber-900/30 dark:text-amber-400">Unpaid (LOP)</span>}
                        </td>
                        <td className="px-4 py-3 text-right">
                          {canManage && (
                            <>
                              <Button variant="ghost" size="sm" title="Edit leave type"
                                onClick={() => setEditType(t)}>
                                <Pencil className="w-4 h-4" />
                              </Button>
                              <Button
                                variant="ghost"
                                size="sm"
                                className="text-destructive"
                                onClick={() => { if (confirm("Are you sure?")) deleteTypeMutation.mutate(t.id); }}
                              >
                                <Trash2 className="w-4 h-4" />
                              </Button>
                            </>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <TablePagination
                  page={typesPaged.page} totalPages={typesPaged.totalPages} onChange={typesPaged.setPage}
                  pageSize={typesPaged.pageSize} onPageSizeChange={typesPaged.setPageSize}
                  total={typesPaged.total}
                  always
                />
              </CardContent>
            </Card>
          )}
        </div>
      )}

      {activeTab === "holidays" && (
        <div className="space-y-4">
          <div className="flex justify-between items-center">
            <h3 className="font-semibold text-lg">Holidays</h3>
            {canManage && (
              <Button size="sm" onClick={() => setCreateHolidayOpen(true)}><Plus className="w-4 h-4 mr-2" /> Add Holiday</Button>
            )}
          </div>
          
          {holidays.isLoading ? (
            <Skeleton className="h-40 w-full" />
          ) : (
            <Card>
              <CardContent className="p-0">
                <table className="w-full text-sm text-left">
                  <thead className="bg-muted text-muted-foreground">
                    <tr>
                      <th className="px-4 py-3 font-medium">Holiday Name</th>
                      <th className="px-4 py-3 font-medium">Date</th>
                      <th className="px-4 py-3 font-medium text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {holidaysPaged.pageRows.map(h => (
                      <tr key={h.id} className="hover:bg-muted/50">
                        <td className="px-4 py-3 font-medium">{h.name}</td>
                        <td className="px-4 py-3 text-muted-foreground">{h.holidayDate}</td>
                        <td className="px-4 py-3 text-right">
                          {canManage && (
                            <Button 
                              variant="ghost" 
                              size="sm" 
                              className="text-destructive"
                              onClick={() => {
                                if(confirm("Are you sure?")) deleteHolidayMutation.mutate(h.id)
                              }}
                            >
                              <Trash2 className="w-4 h-4" />
                            </Button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <TablePagination
                  page={holidaysPaged.page} totalPages={holidaysPaged.totalPages} onChange={holidaysPaged.setPage}
                  pageSize={holidaysPaged.pageSize} onPageSizeChange={holidaysPaged.setPageSize}
                  total={holidaysPaged.total}
                  always
                />
              </CardContent>
            </Card>
          )}
        </div>
      )}
      {(createTypeOpen || editType) && (
        <CreateLeaveTypeDialog
          edit={editType}
          onClose={() => { setCreateTypeOpen(false); setEditType(null); }}
        />
      )}
      {createHolidayOpen && <CreateHolidayDialog onClose={() => setCreateHolidayOpen(false)} />}
    </div>
  );
}

interface LeaveTypeForm {
  name: string;
  code: string;
  maxDaysPerYear?: number;
  carryForward: boolean;
  encashable: boolean;
  paid: boolean;
}

function CreateLeaveTypeDialog({ onClose, edit }: { onClose: () => void; edit?: LeaveType | null }) {
  const qc = useQueryClient();
  const { register, handleSubmit, formState: { errors } } = useForm<LeaveTypeForm>({
    defaultValues: edit
      ? { name: edit.name, code: edit.code, maxDaysPerYear: edit.maxDaysPerYear,
          carryForward: edit.carryForward, encashable: edit.encashable, paid: !!edit.paid }
      : { carryForward: false, encashable: false, paid: false }
  });

  const create = useMutation({
    mutationFn: async (v: LeaveTypeForm) => {
      const body = {
        ...v,
        maxDaysPerYear: v.maxDaysPerYear ? Number(v.maxDaysPerYear) : null,
        // Preserve the fields the form doesn't edit so an update never wipes them.
        carryForward: edit?.carryForward ?? false,
        encashable: edit?.encashable ?? false,
        genderRestriction: edit?.genderRestriction ?? null,
        allowPastDates: edit?.allowPastDates ?? false,
        accrualType: edit?.accrualType ?? "ANNUAL",
        minNoticeDays: edit?.minNoticeDays ?? 0,
        monthlyLimit: edit?.monthlyLimit ?? null
      };
      return edit ? api.put(`/leave/types/${edit.id}`, body) : api.post("/leave/types", body);
    },
    onSuccess: () => {
      toast.success(edit ? "Leave type updated" : "Leave type created");
      qc.invalidateQueries({ queryKey: ["leave-types"] });
      onClose();
    },
    onError: (err) => toast.error(apiMessage(err, "Could not save leave type"))
  });

  return (
    <Dialog open onClose={onClose}>
      <DialogHeader title={edit ? "Edit Leave Type" : "Add Leave Type"} description="Casual & Sick are paid; other unpaid types drive Loss of Pay." />
      <form onSubmit={handleSubmit((v) => create.mutate(v))} className="space-y-4">
        <div className="space-y-1.5">
          <Label htmlFor="name">Name</Label>
          <Input id="name" placeholder="e.g. Annual Leave" {...register("name", { required: true })} />
          {errors.name && <p className="text-xs text-destructive">Name is required</p>}
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="code">Code</Label>
            <Input id="code" placeholder="e.g. AL" {...register("code", { required: true })} />
            {errors.code && <p className="text-xs text-destructive">Code is required</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="maxDaysPerYear">Max Days / Year</Label>
            <Input id="maxDaysPerYear" type="number" placeholder="Leave empty for unlimited" {...register("maxDaysPerYear")} />
          </div>
        </div>
        <div className="pt-1">
          <label className="flex items-center gap-2 text-sm font-medium">
            <input type="checkbox" className="rounded border-input text-primary focus:ring-primary" {...register("paid")} />
            Paid leave (no salary deduction)
          </label>
          <p className="mt-1 text-xs text-muted-foreground">Untick for unpaid leave — those days are deducted as Loss of Pay on the payslip.</p>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={create.isPending}>
            {create.isPending && <Loader2 className="h-4 w-4 animate-spin mr-2" />}
            Save
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

interface HolidayForm {
  name: string;
  holidayDate: string;
}

function CreateHolidayDialog({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient();
  const { register, handleSubmit, formState: { errors } } = useForm<HolidayForm>();

  const create = useMutation({
    mutationFn: async (v: HolidayForm) => api.post("/org/holidays", v),
    onSuccess: () => {
      toast.success("Holiday created");
      qc.invalidateQueries({ queryKey: ["holidays"] });
      onClose();
    },
    onError: (err) => toast.error(apiMessage(err, "Could not create holiday"))
  });

  return (
    <Dialog open onClose={onClose}>
      <DialogHeader title="Add Holiday" description="Schedule a company-wide holiday." />
      <form onSubmit={handleSubmit((v) => create.mutate(v))} className="space-y-4">
        <div className="space-y-1.5">
          <Label htmlFor="name">Holiday Name</Label>
          <Input id="name" placeholder="e.g. New Year" {...register("name", { required: true })} />
          {errors.name && <p className="text-xs text-destructive">Name is required</p>}
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="holidayDate">Date<span className="ml-0.5 text-destructive">*</span></Label>
          <Input id="holidayDate" type="date" min={todayIso()} {...register("holidayDate", { required: true })} />
          {errors.holidayDate && <p className="text-xs text-destructive">Date is required</p>}
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={create.isPending}>
            {create.isPending && <Loader2 className="h-4 w-4 animate-spin mr-2" />}
            Save
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
