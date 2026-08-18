import { CustomLoader as Loader2 } from "@/components/ui/custom-loader";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  Calendar, Briefcase, Plus, Trash2, CalendarCheck, Users, Pencil, Search
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

  // Strict Current Year enforcement (e.g., 2026)
  const thisYear = new Date().getFullYear();
  const [allocYearStr, setAllocYearStr] = useState<string>(String(thisYear));
  const [typeQuery, setTypeQuery] = useState("");

  const numYear = Number(allocYearStr);
  const isInvalidYear = allocYearStr.length > 0 && numYear !== thisYear;

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

  const filteredTypes = (leaveTypes.data ?? []).filter((t) => {
    const needle = typeQuery.trim().toLowerCase();
    if (!needle) return true;
    return `${t.name ?? ""} ${t.code ?? ""}`.toLowerCase().includes(needle);
  });

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
          className={`px-4 py-2 text-sm font-semibold rounded-md transition-colors flex items-center ${
            activeTab === "types" ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:bg-muted"
          }`}
          onClick={() => setActiveTab("types")}
        >
          <Briefcase className="w-4 h-4 mr-2 shrink-0" /> Leave Types
        </button>
        <button
          className={`px-4 py-2 text-sm font-semibold rounded-md transition-colors flex items-center ${
            activeTab === "holidays" ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:bg-muted"
          }`}
          onClick={() => setActiveTab("holidays")}
        >
          <Calendar className="w-4 h-4 mr-2 shrink-0" /> Holidays
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
                    <Label htmlFor="allocYear" className="text-[10px] uppercase text-muted-foreground font-bold">Year</Label>
                    <Input
                      id="allocYear"
                      type="text"
                      inputMode="numeric"
                      maxLength={4}
                      placeholder={String(thisYear)}
                      value={allocYearStr}
                      onChange={(e) => setAllocYearStr(e.target.value.replace(/\D/g, "").slice(0, 4))}
                      className={`h-9 w-24 font-bold text-center ${isInvalidYear ? "border-destructive ring-1 ring-destructive" : ""}`}
                    />
                    <p className={`mt-1 text-[10px] ${isInvalidYear ? "font-bold text-destructive" : "text-emerald-600 font-semibold"}`}>
                      {isInvalidYear ? `Only ${thisYear} allowed` : `Current year (${thisYear})`}
                    </p>
                  </div>
                  <Button
                    className="mt-[18px] bg-green-600 text-white hover:bg-green-700"
                    disabled={allocateMutation.isPending || isInvalidYear}
                    onClick={() => {
                      if (numYear !== thisYear) {
                        toast.error(`Only the current year (${thisYear}) can be allocated. Past and future years are not allowed.`);
                        return;
                      }
                      if (confirm(`Allocate default leave balances to all employees for ${thisYear}?`)) {
                        allocateMutation.mutate(thisYear);
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
                <Button size="sm" onClick={() => setCreateTypeOpen(true)}>
                  <Plus className="w-4 h-4 mr-2" /> Add Leave Type
                </Button>
              )}
            </div>
          </div>

          {leaveTypes.isLoading ? (
            <Skeleton className="h-40 w-full" />
          ) : (
            <Card>
              <CardContent className="p-0">
                <table className="w-full text-sm text-left">
                  <thead className="bg-muted text-muted-foreground font-semibold uppercase text-xs">
                    <tr>
                      <th className="px-4 py-3 font-bold">Name</th>
                      <th className="px-4 py-3 font-bold">Code</th>
                      <th className="px-4 py-3 font-bold">Max Days/Year</th>
                      <th className="px-4 py-3 font-bold">Pay</th>
                      <th className="px-4 py-3 font-bold text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {typesPaged.pageRows.map((t) => (
                      <tr key={t.id} className="hover:bg-muted/50">
                        <td className="px-4 py-3 font-medium">{t.name}</td>
                        <td className="px-4 py-3 text-muted-foreground">{t.code}</td>
                        <td className="px-4 py-3 text-muted-foreground">{t.maxDaysPerYear || "Unlimited"}</td>
                        <td className="px-4 py-3">
                          {t.paid ? (
                            <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-semibold text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400">
                              Paid
                            </span>
                          ) : (
                            <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-semibold text-amber-700 dark:bg-amber-900/30 dark:text-amber-400">
                              Unpaid (LOP)
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-right">
                          {canManage && (
                            <div className="flex items-center justify-end gap-1">
                              <Button
                                variant="ghost"
                                size="sm"
                                title="Edit leave type"
                                onClick={() => setEditType(t)}
                              >
                                <Pencil className="w-4 h-4" />
                              </Button>
                              <Button
                                variant="ghost"
                                size="sm"
                                title="Delete leave type"
                                onClick={() => {
                                  if (confirm(`Delete ${t.name}? Existing balances stay, but nobody can be given it again.`)) {
                                    deleteTypeMutation.mutate(t.id);
                                  }
                                }}
                              >
                                <Trash2 className="w-4 h-4 text-destructive" />
                              </Button>
                            </div>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <TablePagination
                  page={typesPaged.page}
                  totalPages={typesPaged.totalPages}
                  onChange={typesPaged.setPage}
                  pageSize={typesPaged.pageSize}
                  onPageSizeChange={typesPaged.setPageSize}
                  total={typesPaged.total}
                />
              </CardContent>
            </Card>
          )}
        </div>
      )}

      {activeTab === "holidays" && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="font-semibold text-lg">Holidays Calendar</h3>
            {canManage && (
              <Button size="sm" onClick={() => setCreateHolidayOpen(true)}>
                <Plus className="w-4 h-4 mr-2" /> Add Holiday
              </Button>
            )}
          </div>
          {holidays.isLoading ? (
            <Skeleton className="h-40 w-full" />
          ) : (
            <Card>
              <CardContent className="p-0">
                <table className="w-full text-sm text-left">
                  <thead className="bg-muted text-muted-foreground font-semibold uppercase text-xs">
                    <tr>
                      <th className="px-4 py-3 font-bold">Date</th>
                      <th className="px-4 py-3 font-bold">Holiday Name</th>
                      <th className="px-4 py-3 font-bold">Type</th>
                      <th className="px-4 py-3 font-bold text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {holidaysPaged.pageRows.map((h) => (
                      <tr key={h.id} className="hover:bg-muted/50">
                        <td className="px-4 py-3 font-medium">{h.date}</td>
                        <td className="px-4 py-3 font-semibold">{h.name}</td>
                        <td className="px-4 py-3 text-muted-foreground">{h.type || "National"}</td>
                        <td className="px-4 py-3 text-right">
                          {canManage && (
                            <Button
                              variant="ghost"
                              size="sm"
                              title="Delete holiday"
                              onClick={() => {
                                if (confirm(`Delete holiday ${h.name}?`)) {
                                  deleteHolidayMutation.mutate(h.id);
                                }
                              }}
                            >
                              <Trash2 className="w-4 h-4 text-destructive" />
                            </Button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <TablePagination
                  page={holidaysPaged.page}
                  totalPages={holidaysPaged.totalPages}
                  onChange={holidaysPaged.setPage}
                  pageSize={holidaysPaged.pageSize}
                  onPageSizeChange={holidaysPaged.setPageSize}
                  total={holidaysPaged.total}
                />
              </CardContent>
            </Card>
          )}
        </div>
      )}

      {/* Dialogs */}
      {createTypeOpen && (
        <CreateTypeDialog onClose={() => setCreateTypeOpen(false)} />
      )}
      {editType && (
        <EditTypeDialog type={editType} onClose={() => setEditType(null)} />
      )}
      {createHolidayOpen && (
        <CreateHolidayDialog onClose={() => setCreateHolidayOpen(false)} />
      )}
    </div>
  );
}

function CreateTypeDialog({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient();
  const { register, handleSubmit } = useForm();
  const createMutation = useMutation({
    mutationFn: async (data: any) => {
      await api.post("/leave/types", data);
    },
    onSuccess: () => {
      toast.success("Leave type created");
      queryClient.invalidateQueries({ queryKey: ["leave-types"] });
      onClose();
    },
    onError: (err) => toast.error(apiMessage(err, "Failed to create leave type"))
  });

  return (
    <Dialog open onClose={onClose}>
      <DialogHeader title="Add Leave Type" />
      <form onSubmit={handleSubmit((d) => createMutation.mutate(d))} className="space-y-4 mt-2">
        <div>
          <Label htmlFor="name">Name</Label>
          <Input id="name" {...register("name", { required: true })} placeholder="e.g. Casual Leave" />
        </div>
        <div>
          <Label htmlFor="code">Code</Label>
          <Input id="code" {...register("code", { required: true })} placeholder="e.g. CL" />
        </div>
        <div>
          <Label htmlFor="maxDaysPerYear">Max Days Per Year</Label>
          <Input id="maxDaysPerYear" type="number" {...register("maxDaysPerYear")} placeholder="e.g. 12 (leave blank for unlimited)" />
        </div>
        <div className="flex items-center gap-2">
          <input type="checkbox" id="paid" {...register("paid")} defaultChecked className="h-4 w-4 rounded border-gray-300 accent-primary" />
          <Label htmlFor="paid">Paid Leave</Label>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={createMutation.isPending}>Save</Button>
        </div>
      </form>
    </Dialog>
  );
}

function EditTypeDialog({ type, onClose }: { type: LeaveType; onClose: () => void }) {
  const queryClient = useQueryClient();
  const { register, handleSubmit } = useForm({
    defaultValues: {
      name: type.name,
      code: type.code,
      maxDaysPerYear: type.maxDaysPerYear,
      paid: type.paid
    }
  });

  const editMutation = useMutation({
    mutationFn: async (data: any) => {
      await api.put(`/leave/types/${type.id}`, data);
    },
    onSuccess: () => {
      toast.success("Leave type updated");
      queryClient.invalidateQueries({ queryKey: ["leave-types"] });
      onClose();
    },
    onError: (err) => toast.error(apiMessage(err, "Failed to update leave type"))
  });

  return (
    <Dialog open onClose={onClose}>
      <DialogHeader title={`Edit ${type.name}`} />
      <form onSubmit={handleSubmit((d) => editMutation.mutate(d))} className="space-y-4 mt-2">
        <div>
          <Label htmlFor="name">Name</Label>
          <Input id="name" {...register("name", { required: true })} />
        </div>
        <div>
          <Label htmlFor="code">Code</Label>
          <Input id="code" {...register("code", { required: true })} />
        </div>
        <div>
          <Label htmlFor="maxDaysPerYear">Max Days Per Year</Label>
          <Input id="maxDaysPerYear" type="number" {...register("maxDaysPerYear")} />
        </div>
        <div className="flex items-center gap-2">
          <input type="checkbox" id="paid" {...register("paid")} className="h-4 w-4 rounded border-gray-300 accent-primary" />
          <Label htmlFor="paid">Paid Leave</Label>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={editMutation.isPending}>Save</Button>
        </div>
      </form>
    </Dialog>
  );
}

function CreateHolidayDialog({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient();
  const { register, handleSubmit } = useForm({
    defaultValues: { date: todayIso(), name: "", type: "National" }
  });

  const createMutation = useMutation({
    mutationFn: async (data: any) => {
      await api.post("/org/holidays", data);
    },
    onSuccess: () => {
      toast.success("Holiday added");
      queryClient.invalidateQueries({ queryKey: ["holidays"] });
      onClose();
    },
    onError: (err) => toast.error(apiMessage(err, "Failed to add holiday"))
  });

  return (
    <Dialog open onClose={onClose}>
      <DialogHeader title="Add Holiday" />
      <form onSubmit={handleSubmit((d) => createMutation.mutate(d))} className="space-y-4 mt-2">
        <div>
          <Label htmlFor="date">Date</Label>
          <Input id="date" type="date" {...register("date", { required: true })} />
        </div>
        <div>
          <Label htmlFor="name">Holiday Name</Label>
          <Input id="name" {...register("name", { required: true })} placeholder="e.g. Gandhi Jayanti" />
        </div>
        <div>
          <Label htmlFor="type">Type</Label>
          <Input id="type" {...register("type")} placeholder="e.g. National, Optional" />
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={createMutation.isPending}>Save</Button>
        </div>
      </form>
    </Dialog>
  );
}
