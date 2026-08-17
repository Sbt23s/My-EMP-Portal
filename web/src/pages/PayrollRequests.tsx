import { CustomLoader as Loader2 } from "@/components/ui/custom-loader";
import { useState, useMemo, useEffect } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Wallet, FileText, Download, IndianRupee, Eye, Users, Clock, Banknote, WalletCards, ReceiptText, CheckCircle2 } from "lucide-react";
import { usePagedRows, TablePagination } from "@/components/ui/table-pagination";
import toast from "react-hot-toast";
import dayjs from "dayjs";
import { api, apiMessage } from "@/lib/api";
import { PageHeader } from "@/components/PageHeader";
import { EmptyState } from "@/components/EmptyState";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Dialog, DialogHeader } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { ApiEnvelope, PageEnvelope, UserSummary } from "@/types";
import { useAuth } from "@/hooks/useAuth";
import { cn } from "@/lib/utils";

interface Salary {
  userId: number;
  basicSalary: number;
  hra: number;
  allowances: number;
  pfPercentage: number;
  esiApplicable: boolean;
  ptAmount: number;
  grossSalary: number;
}
interface PayslipSum {
  id: number;
  payMonth: number;
  payYear: number;
  netPay?: number;
  grossSalary?: number;
}
/** Basic pay recorded for one employee in one month. */
interface SalaryMonth {
  userId: number;
  month: number;
  year: number;
  basicSalary: number;
}

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
const inr = (n?: number) => (n == null ? "—" : "₹" + Number(n).toLocaleString("en-IN"));

/**
 * Where a payslip stands for the chosen month. Generated is done, pending is
 * waiting to be run, failed is a run that did not complete, and not generated
 * covers an employee with no salary configured yet -- a different problem, and
 * the salary column says so in its own right.
 */
function PayrollStatus({ state }: { state: "GENERATED" | "PENDING" | "FAILED" | "NOT_GENERATED" }) {
  const look: Record<string, [string, string, string]> = {
    GENERATED: ["Generated", "border-emerald-300 bg-emerald-50 text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/30 dark:text-emerald-300", "bg-emerald-500"],
    PENDING: ["Pending", "border-amber-300 bg-amber-50 text-amber-700 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-300", "bg-amber-500"],
    FAILED: ["Failed", "border-rose-300 bg-rose-50 text-rose-700 dark:border-rose-800 dark:bg-rose-950/30 dark:text-rose-300", "bg-rose-500"],
    NOT_GENERATED: ["Not generated", "border-slate-300 bg-slate-50 text-slate-600 dark:border-slate-700 dark:bg-slate-900/40 dark:text-slate-300", "bg-slate-400"]
  };
  const [label, tone, dot] = look[state];
  return (
    <span className={cn(
      "inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border px-2.5 py-0.5 text-[11px] font-bold",
      tone
    )}>
      <span className={cn("h-1.5 w-1.5 rounded-full", dot)} /> {label}
    </span>
  );
}
const industryLabel = (i?: string) => (i === "IT" ? "Digital" : i === "CIVIL" ? "Infra" : i || "—");

// Open the payslip PDF inline in a new tab (view only — no download prompt).
async function viewPayslipPdf(id: number) {
  const toastId = toast.loading("Opening payslip…");
  try {
    const res = await api.get(`/payroll/payslip/${id}/pdf`, { responseType: "blob" });
    const url = URL.createObjectURL(res.data as Blob);
    window.open(url, "_blank", "noopener,noreferrer");
    toast.dismiss(toastId);
    // Revoke a little later so the new tab has time to load it.
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  } catch (e) {
    toast.error(apiMessage(e, "Could not open payslip"), { id: toastId });
  }
}

async function downloadPayslipPdf(id: number, name: string) {
  const toastId = toast.loading("Downloading payslip…");
  try {
    const res = await api.get(`/payroll/payslip/${id}/pdf`, { responseType: "blob" });
    const url = URL.createObjectURL(res.data as Blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `Payslip_${name.replace(/\s+/g, "_")}.pdf`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    toast.success("Downloaded", { id: toastId });
  } catch (e) {
    toast.error(apiMessage(e, "Could not download payslip"), { id: toastId });
  }
}

export default function PayrollPage() {
  const { user, hasRole, hasPermission } = useAuth();
  // Everyone who runs payroll gets the same page: HR, the admin, and the company
  // head. Setting salary, generating and downloading all sit behind PAYROLL_RUN,
  // which the admin already holds — the page used to hide them anyway.
  const canRun = hasRole("IT_MGR", "IT_HR", "CV_HR", "SUPER_ADMIN", "COMPANY_ADMIN")
    || hasPermission("PAYROLL_RUN", "USER_MANAGE")
    || user?.employeeCode === "PIX-E100";
  const [tab, setTab] = useState<"payslips" | "salary">("payslips");
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState<"all" | "IT" | "CIVIL">("all");
  const [month, setMonth] = useState(dayjs().month() + 1);
  const [year, setYear] = useState(dayjs().year());
  const [salaryFor, setSalaryFor] = useState<UserSummary | null>(null);
  const [genFor, setGenFor] = useState<UserSummary | null>(null);
  const [payslipsFor, setPayslipsFor] = useState<UserSummary | null>(null);

  const employees = useQuery({
    queryKey: ["payroll-employees"],
    queryFn: async () =>
      (await api.get<ApiEnvelope<PageEnvelope<UserSummary>>>("/users?status=ACTIVE&size=1000")).data.data.content
  });

  const salaries = useQuery({
    queryKey: ["payroll-salaries"],
    queryFn: async () => (await api.get<ApiEnvelope<Salary[]>>("/payroll/salaries")).data.data
  });

  // Payslips generated for the selected month, keyed by userId.
  const monthPayslips = useQuery({
    queryKey: ["payroll-month-payslips", month, year],
    queryFn: async () =>
      (await api.get<ApiEnvelope<Record<string, PayslipSum>>>(`/payroll/payslips/month?month=${month}&year=${year}`)).data.data
  });

  // Basic pay recorded against the chosen month, which is what a payslip for
  // that month is built on.
  const monthSalaries = useQuery({
    queryKey: ["payroll-salary-months", month, year],
    queryFn: async () =>
      (await api.get<ApiEnvelope<SalaryMonth[]>>(`/payroll/salary-months?month=${month}&year=${year}`)).data.data
  });

  const salaryMap = useMemo(() => {
    const m = new Map<number, Salary>();
    (salaries.data ?? []).forEach((s) => m.set(s.userId, s));
    return m;
  }, [salaries.data]);

  const monthBasicMap = useMemo(() => {
    const m = new Map<number, number>();
    (monthSalaries.data ?? []).forEach((s) => m.set(s.userId, Number(s.basicSalary)));
    return m;
  }, [monthSalaries.data]);

  const rows = (employees.data ?? []).filter((e) => {
    const q = search.trim().toLowerCase();
    const matchesSearch = !q || e.name.toLowerCase().includes(q) || (e.employeeCode || "").toLowerCase().includes(q);
    const matchesCat = category === "all" || e.industry === category;
    return matchesSearch && matchesCat;
  });

  const rowsPaged = usePagedRows(rows, 15, [search, category, month, year, employees.data]);

  const payrollCounts = useMemo(() => {
    const configured = rows.filter((e) => salaryMap.has(e.id)).length;
    const generated = rows.filter((e) => !!monthPayslips.data?.[String(e.id)]).length;
    
    let totalNet = 0;
    let totalGross = 0;
    
    if (monthPayslips.data) {
      Object.values(monthPayslips.data).forEach(p => {
        totalNet += (p.netPay || 0);
        totalGross += (p.grossSalary || 0);
      });
    }

    return {
      total: rows.length,
      configured,
      missing: rows.length - configured,
      generated,
      pending: rows.length - generated,
      totalNet,
      totalGross,
      totalDeductions: totalGross - totalNet
    };
  }, [rows, salaryMap, monthPayslips.data]);

  const loading = employees.isLoading || salaries.isLoading || monthPayslips.isLoading;

  async function exportSalaryDetails() {
    const XLSX = await import("xlsx");
    const headers = ["#", "Employee ID", "Employee", "Category", "Team",
                     `Basic ${MONTHS[month - 1]} ${year}`, "Standing basic", "Monthly salary"];
    const body = rows.map((e, i) => {
      const s = salaryMap.get(e.id);
      const monthly = monthBasicMap.get(e.id);
      return [
        i + 1,
        e.employeeCode ?? "",
        e.name ?? "",
        industryLabel(e.industry),
        e.designationTitle ?? "",
        monthly ?? "",
        s ? Number(s.basicSalary) : "",
        s ? Number(s.grossSalary) : ""
      ];
    });
    const ws = XLSX.utils.aoa_to_sheet([
      [`Salary details — ${MONTHS[month - 1]} ${year}`],
      [`${rows.length} employee(s) · exported ${dayjs().format("DD MMM YYYY, h:mm A")}`],
      [],
      headers,
      ...body
    ]);
    ws["!cols"] = [{ wch: 5 }, { wch: 14 }, { wch: 26 }, { wch: 12 }, { wch: 24 },
                   { wch: 18 }, { wch: 16 }, { wch: 16 }];
    ws["!merges"] = [
      { s: { r: 0, c: 0 }, e: { r: 0, c: headers.length - 1 } },
      { s: { r: 1, c: 0 }, e: { r: 1, c: headers.length - 1 } }
    ];
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, "Salary details");
    XLSX.writeFile(wb, `Salary_Details_${MONTHS[month - 1]}_${year}.xlsx`);
    toast.success("Exported");
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Payroll Runs"
        subtitle="Process employee payroll, configure salaries, and generate payslips."
      />

      <div className="flex flex-wrap items-end gap-3">
        <div className="flex flex-col">
          <label className="mb-0.5 text-[10px] font-semibold uppercase text-muted-foreground">Search</label>
          <Input
            placeholder="Search employee by name or code…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="h-[38px] w-[18rem]"
          />
        </div>
        <div className="flex flex-col">
          <label className="mb-0.5 text-[10px] font-semibold uppercase text-muted-foreground">Department</label>
          <div className="flex h-[38px] items-center gap-1.5 rounded-full border bg-muted/60 p-1">
          {([["all", "All Departments"], ["IT", "Digital"], ["CIVIL", "Infra"]] as const).map(([val, label]) => (
            <button
              key={val}
              type="button"
              onClick={() => setCategory(val)}
              className={`rounded-full px-3.5 py-1.5 text-xs font-semibold transition-all ${
                category === val ? "bg-primary text-primary-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"
              }`}
            >
              {label}
            </button>
          ))}
          </div>
        </div>
        <div className="flex flex-col">
          <label className="mb-0.5 text-[10px] font-semibold uppercase text-muted-foreground">Month</label>
          <select className="h-[38px] w-[8rem] rounded-md border bg-background px-3 text-sm" value={month} onChange={(e) => setMonth(Number(e.target.value))}>
            {MONTHS.map((m, i) => <option key={i + 1} value={i + 1}>{m}</option>)}
          </select>
        </div>
        <div className="flex flex-col">
          <label className="mb-0.5 text-[10px] font-semibold uppercase text-muted-foreground">Year</label>
          <select className="h-[38px] w-[7rem] rounded-md border bg-background px-3 text-sm" value={year} onChange={(e) => setYear(Number(e.target.value))}>
            {[dayjs().year(), dayjs().year() - 1, dayjs().year() - 2].map((y) => <option key={y} value={y}>{y}</option>)}
          </select>
        </div>
        <div className="flex flex-col ml-auto flex-row gap-2">
          {/* Run Payroll button removed as per design */}
          <Button
            variant="outline"
            className="h-[38px]"
            onClick={() => exportSalaryDetails()}
            disabled={rows.length === 0}
          >
            <Download className="mr-1.5 h-4 w-4" /> Export
          </Button>
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-6">
        <StatCard
          title="Total Employees"
          value={payrollCounts.total.toString()}
          subtitle="Active Employees"
          icon={Users}
          color="text-violet-600"
          bg="bg-violet-100"
          titleColor="text-violet-600"
        />
        <StatCard
          title="This Month Payroll"
          value={inr(payrollCounts.totalNet)}
          subtitle={`${MONTHS[month - 1]} ${year}`}
          icon={Banknote}
          color="text-green-600"
          bg="bg-green-100"
          titleColor="text-foreground"
        />
        <StatCard
          title="Total Gross Pay"
          value={inr(payrollCounts.totalGross)}
          subtitle={`${MONTHS[month - 1]} ${year}`}
          icon={WalletCards}
          color="text-blue-600"
          bg="bg-blue-100"
          titleColor="text-blue-600"
        />
        <StatCard
          title="Total Deductions"
          value={inr(payrollCounts.totalDeductions)}
          subtitle={`${MONTHS[month - 1]} ${year}`}
          icon={ReceiptText}
          color="text-rose-600"
          bg="bg-rose-100"
          titleColor="text-rose-600"
        />
        <StatCard
          title="Processed"
          value={payrollCounts.generated.toString()}
          subtitle="Paid Employees"
          icon={CheckCircle2}
          color="text-emerald-600"
          bg="bg-emerald-100"
          titleColor="text-emerald-600"
        />
        <StatCard
          title="Pending"
          value={payrollCounts.pending.toString()}
          subtitle="Not Processed"
          icon={Clock}
          color="text-orange-600"
          bg="bg-orange-100"
          titleColor="text-orange-600"
        />
      </div>

      {loading ? (
        <Skeleton className="h-64 w-full rounded-xl" />
      ) : rows.length === 0 ? (
        <EmptyState icon={Wallet} title="No employees" description="Active employees will appear here to set salary and generate payslips." />
      ) : (
        <div className="rounded-xl border bg-card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm border-collapse">
              <thead>
                <tr className="border-b border-slate-300 dark:border-slate-700 bg-slate-100/90 dark:bg-slate-800/90 text-left text-xs font-semibold text-slate-800 dark:text-slate-200 [&>th]:whitespace-nowrap [&>th]:px-3.5 [&>th]:py-3 [&>th]:border-r [&>th]:border-slate-300 dark:[&>th]:border-slate-700 last:[&>th]:border-r-0">
                  <th>Employee</th>
                  <th>Employee ID</th>
                  <th>Department</th>
                  <th>Gross Pay</th>
                  <th>Deductions</th>
                  <th>Net Pay</th>
                  <th>Status</th>
                  <th>Pay Date</th>
                  <th className="text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {rowsPaged.pageRows.map((e, idx) => {
                  const s = salaryMap.get(e.id);
                  const payslip = monthPayslips.data?.[String(e.id)];
                  const isPaid = !!payslip;
                  
                  let gross = s?.grossSalary || 0;
                  let net = 0;
                  let deds = 0;
                  
                  if (isPaid) {
                    gross = payslip.grossSalary || 0;
                    net = payslip.netPay || 0;
                    deds = gross - net;
                  }
                  
                  const payDate = dayjs(`${year}-${month}-01`).endOf('month').format("DD MMM YYYY");

                  return (
                    <tr key={e.id} className="border-b border-slate-200 dark:border-slate-800 align-middle last:border-0 hover:bg-slate-50 dark:hover:bg-slate-800/50 transition-colors [&>td]:px-3.5 [&>td]:py-3 [&>td]:border-r [&>td]:border-b [&>td]:border-slate-200 dark:[&>td]:border-slate-800 last:[&>td]:border-r-0">
                      <td className="font-medium whitespace-nowrap">
                        {e.name}
                      </td>
                      <td className="text-muted-foreground">
                        {e.employeeCode || "—"}
                      </td>
                      <td className="whitespace-nowrap">
                        {e.designationTitle || industryLabel(e.industry)}
                      </td>
                      <td className="font-bold tabular-nums">
                        {inr(gross)}
                      </td>
                      <td className="font-bold tabular-nums text-muted-foreground">
                        {isPaid ? inr(deds) : "—"}
                      </td>
                      <td className="font-bold tabular-nums">
                        {isPaid ? inr(net) : "—"}
                      </td>
                      <td>
                        {isPaid ? (
                          <span className="inline-flex items-center rounded-full bg-emerald-100 px-2.5 py-0.5 text-[11px] font-bold text-emerald-700">
                            Paid
                          </span>
                        ) : (
                          <span className="inline-flex items-center rounded-full bg-orange-100 px-2.5 py-0.5 text-[11px] font-bold text-orange-700">
                            Pending
                          </span>
                        )}
                      </td>
                      <td className="text-muted-foreground font-medium whitespace-nowrap">
                        {isPaid ? payDate : "—"}
                      </td>
                      <td className="text-right">
                        <div className="flex items-center justify-end gap-2 whitespace-nowrap">
                          {isPaid ? (
                            <>
                              <button
                                type="button"
                                className="inline-flex h-8 w-8 items-center justify-center rounded-md border text-muted-foreground hover:bg-muted/50 transition-colors"
                                onClick={() => viewPayslipPdf(payslip.id)}
                                title="View Payslip"
                              >
                                <Eye className="h-4 w-4" />
                              </button>
                              <button
                                type="button"
                                className="inline-flex h-8 w-8 items-center justify-center rounded-md border text-violet-600 hover:bg-violet-50 transition-colors"
                                onClick={() => downloadPayslipPdf(payslip.id, e.name)}
                                title="Download PDF"
                              >
                                <Download className="h-4 w-4" />
                              </button>
                            </>
                          ) : (
                            canRun && (
                              <>
                                <Button variant="outline" size="sm" onClick={() => setSalaryFor(e)} title={s ? "Edit Salary" : "Set Salary"}>
                                  <IndianRupee className="h-3.5 w-3.5" />
                                </Button>
                                <Button size="sm" disabled={!s} onClick={() => setGenFor(e)} title={s ? "Generate Payslip" : "Set salary first"}>
                                  Generate
                                </Button>
                              </>
                            )
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <div className="flex flex-wrap items-center justify-between gap-4 border-t px-5 py-3 text-sm text-muted-foreground bg-muted/10">
            <div>
              Showing {(rowsPaged.page - 1) * rowsPaged.pageSize + 1} to {Math.min(rowsPaged.page * rowsPaged.pageSize, rows.length)} of {rows.length} entries
            </div>
            <TablePagination
              page={rowsPaged.page} totalPages={rowsPaged.totalPages} onChange={rowsPaged.setPage}
              pageSize={rowsPaged.pageSize} onPageSizeChange={rowsPaged.setPageSize}
              total={rowsPaged.total}
            />
          </div>
        </div>
      )}

      {salaryFor && (
        <SalaryDialog
          employee={salaryFor}
          current={salaryMap.get(salaryFor.id)}
          monthBasic={monthBasicMap.get(salaryFor.id)}
          periodLabel={`${MONTHS[month - 1]} ${year}`}
          onClose={() => setSalaryFor(null)}
        />
      )}
      {genFor && (
        <GenerateDialog
          employee={genFor}
          grossMonthly={salaryMap.get(genFor.id)?.grossSalary ?? 0}
          standingBasic={Number(salaryMap.get(genFor.id)?.basicSalary ?? 0)}
          salary={salaryMap.get(genFor.id) ?? null}
          defaultMonth={month}
          defaultYear={year}
          onClose={() => setGenFor(null)}
        />
      )}
      {payslipsFor && <PayslipsDialog employee={payslipsFor} canDownload={canRun} onClose={() => setPayslipsFor(null)} />}
    </div>
  );
}

function StatCard({
  title, value, subtitle, icon: Icon, color, bg, titleColor
}: {
  title: string;
  value: string;
  subtitle: string;
  icon: any;
  color: string;
  bg: string;
  titleColor: string;
}) {
  return (
    <div className="rounded-xl border bg-card p-5 shadow-sm flex flex-col justify-center gap-3 transition-all hover:shadow-md">
      <div className="flex items-start justify-between gap-2">
        <div className="space-y-1.5">
          <h4 className={cn("text-[11px] font-bold uppercase tracking-wider", titleColor)}>{title}</h4>
          <div className="text-xl font-bold tabular-nums tracking-tight">{value}</div>
        </div>
        <div className={cn("grid h-9 w-9 shrink-0 place-items-center rounded-full", bg)}>
          <Icon className={cn("h-4 w-4", color)} />
        </div>
      </div>
      <div className={cn("text-[11px] font-semibold", titleColor.includes('text-foreground') ? 'text-muted-foreground' : titleColor)}>
        {subtitle}
      </div>
    </div>
  );
}

function SalaryDialog({ employee, current, monthBasic, periodLabel, onClose }: {
  employee: UserSummary;
  current?: Salary;
  /** Basic recorded under Salary details for the month on screen, if any. */
  monthBasic?: number;
  periodLabel: string;
  onClose: () => void;
}) {
  const qc = useQueryClient();
  // What Salary details says for this month wins the box, so the figure entered
  // there is the one being worked from rather than an older standing number.
  const [basic, setBasic] = useState(
    String(monthBasic != null ? monthBasic : current?.basicSalary ?? "")
  );
  const [hra, setHra] = useState(String(current?.hra ?? ""));
  const [allowances, setAllowances] = useState(String(current?.allowances ?? ""));
  const [pf, setPf] = useState(String(current?.pfPercentage ?? ""));
  const [esi, setEsi] = useState(current?.esiApplicable ?? true);
  const [pt, setPt] = useState(String(current?.ptAmount ?? ""));

  const num = (v: string) => (v.trim() === "" ? 0 : Number(v));
  const gross = num(basic) + num(hra) + num(allowances);

  const save = useMutation({
    mutationFn: async () =>
      api.post("/payroll/salary", {
        userId: employee.id,
        basicSalary: num(basic),
        hra: num(hra),
        allowances: num(allowances),
        pfPercentage: num(pf),
        esiApplicable: esi,
        ptAmount: num(pt)
      }),
    onSuccess: () => {
      toast.success("Salary saved");
      qc.invalidateQueries({ queryKey: ["payroll-salaries"] });
      onClose();
    },
    onError: (e) => toast.error(apiMessage(e, "Could not save salary"))
  });

  return (
    <Dialog open onClose={onClose} className="max-w-md">
      <DialogHeader title={`Salary — ${employee.name}`} />
      <form
        className="mt-3 space-y-3"
        onSubmit={(ev) => {
          ev.preventDefault();
          if (num(basic) <= 0) { toast.error("Enter a basic salary"); return; }
          save.mutate();
        }}
      >
        <div className="grid grid-cols-2 gap-3">
          <Field label="Basic salary *">
            <Input type="number" min="0" value={basic} onChange={(e) => setBasic(e.target.value)} placeholder="e.g. 25000" />
            {monthBasic != null && (
              <p className="mt-1 text-[11px] text-primary">
                Filled from Salary details — {periodLabel}
              </p>
            )}
          </Field>
          <Field label="HRA"><Input type="number" min="0" value={hra} onChange={(e) => setHra(e.target.value)} placeholder="e.g. 8000" /></Field>
          <Field label="Allowances"><Input type="number" min="0" value={allowances} onChange={(e) => setAllowances(e.target.value)} placeholder="e.g. 3000" /></Field>
          <Field label="PF (₹)"><Input type="number" min="0" value={pf} onChange={(e) => setPf(e.target.value)} placeholder="e.g. 1800" /></Field>
          <Field label="Professional Tax (₹)"><Input type="number" min="0" value={pt} onChange={(e) => setPt(e.target.value)} placeholder="e.g. 200" /></Field>
          <label className="flex items-center gap-2 self-end pb-2 text-sm">
            <input type="checkbox" checked={esi} onChange={(e) => setEsi(e.target.checked)} className="h-4 w-4 accent-[hsl(var(--primary))]" />
            ESI applicable
          </label>
        </div>
        <div className="rounded-md bg-muted/40 px-3 py-2 text-sm">
          Monthly Gross: <span className="font-semibold">{inr(gross)}</span>
          <span className="text-muted-foreground"> (Basic + HRA + Allowances)</span>
        </div>
        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={save.isPending}>
            {save.isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            Save Salary
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

function GenerateDialog({ employee, grossMonthly, standingBasic, salary, defaultMonth, defaultYear, onClose }: { employee: UserSummary; grossMonthly: number; standingBasic: number; salary: Salary | null; defaultMonth: number; defaultYear: number; onClose: () => void }) {
  const now = dayjs();
  const qc = useQueryClient();
  const [month, setMonth] = useState(defaultMonth);
  const [year, setYear] = useState(defaultYear);
  const [overtime, setOvertime] = useState("");
  const [tds, setTds] = useState("");
  const [otherDed, setOtherDed] = useState("");
  const [advance, setAdvance] = useState("");
  const [lopAmt, setLopAmt] = useState("");
  const [performancePay, setPerformancePay] = useState("");
  const [generatedId, setGeneratedId] = useState<number | null>(null);

  const num = (v: string) => (v.trim() === "" ? 0 : Number(v));

  // The basic recorded for whichever month is chosen here — the same figure the
  // server will build the payslip on. Follows the month picker.
  const basicPreview = useQuery({
    queryKey: ["payroll-salary-months", month, year],
    queryFn: async () =>
      (await api.get<ApiEnvelope<SalaryMonth[]>>(`/payroll/salary-months?month=${month}&year=${year}`)).data.data
  });
  const monthBasic = (basicPreview.data ?? []).find((s) => s.userId === employee.id)?.basicSalary;
  const effectiveBasic = monthBasic != null ? Number(monthBasic) : standingBasic;

  // Auto Loss of Pay: (monthly salary / working days in month) × unpaid leave days.
  const lopPreview = useQuery({
    queryKey: ["lop-preview", employee.id, year, month],
    queryFn: async () =>
      (await api.get<ApiEnvelope<LopPreview>>(
        `/leave/lop-preview?userId=${employee.id}&year=${year}&month=${month}`)).data.data
  });
  const unpaidDays = Number(lopPreview.data?.unpaidLeaveDays ?? 0);
  const workingDays = Number(lopPreview.data?.workingDaysInMonth ?? 0);
  const paidLeaveDays = Number(lopPreview.data?.paidLeaveDays ?? 0);
  const totalLeaveDays = Number(lopPreview.data?.totalLeaveDays ?? 0);
  const absentDays = Number(lopPreview.data?.absentDays ?? 0);
  const presentDays = Number(lopPreview.data?.presentDays ?? 0);
  const perDay = grossMonthly > 0 && workingDays > 0 ? grossMonthly / workingDays : 0;
  /**
   * Days not paid for: unpaid leave, and absence.
   *
   * A day neither worked nor covered by approved leave is a day not paid for.
   * Counting only unpaid leave meant somebody absent for a whole month with no
   * leave applied showed a Loss of Pay of zero, and that is what the payslip
   * deducted. Casual and Sick leave stay paid; loss-of-pay leave can be taken in
   * any quantity and every day of it comes off.
   */
  const deductibleDays = Number(lopPreview.data?.deductibleDays ?? (unpaidDays + absentDays));
  const autoLop = perDay > 0 ? Math.round(perDay * deductibleDays) : 0;

  /**
   * What the payslip will say, worked out from the same figures the server uses.
   *
   * The rates are the ones in PayslipService: overtime at gross over 240 hours,
   * ESI at 0.75% of gross while gross is within the ceiling, PF and PT as the flat
   * amounts held on the salary structure. Kept in step deliberately — if those
   * change, this has to change with them, and a mismatch here is visible
   * immediately rather than after a payslip is issued.
   */
  const OT_DIVISOR = 240;
  const ESI_CEILING = 21000;
  const ESI_RATE = 0.0075;

  const salaryParts = {
    hra: Number(salary?.hra ?? 0),
    allowances: Number(salary?.allowances ?? 0),
    pf: Number(salary?.pfPercentage ?? 0),   // a flat rupee amount, despite the name
    pt: Number(salary?.ptAmount ?? 0),
    esiApplicable: salary?.esiApplicable !== false
  };

  const monthlyForOt = effectiveBasic + salaryParts.hra + salaryParts.allowances;
  const overtimePay = Math.round((monthlyForOt / OT_DIVISOR) * num(overtime));
  const grossPreview = Math.round(
    effectiveBasic + salaryParts.hra + salaryParts.allowances
    + overtimePay + num(performancePay)
  );
  const esiPreview = salaryParts.esiApplicable && grossPreview <= ESI_CEILING
    ? Math.round(grossPreview * ESI_RATE) : 0;
  const netPreview = grossPreview
    - salaryParts.pf - esiPreview - salaryParts.pt
    - num(tds) - num(advance) - num(lopAmt) - num(otherDed);

  // Prefill the Loss of Pay field from the computed amount whenever it changes.
  useEffect(() => {
    if (lopPreview.data) setLopAmt(autoLop > 0 ? String(autoLop) : "");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lopPreview.data, autoLop]);

  const gen = useMutation({
    mutationFn: async () =>
      (await api.post<ApiEnvelope<{ id: number }>>("/payroll/payslip/generate", {
        userId: employee.id,
        month,
        year,
        overtimeHours: num(overtime),
        tds: num(tds),
        otherDeductions: num(otherDed),
        advanceDeduction: num(advance),
        lopDeduction: num(lopAmt),
        performancePay: num(performancePay)
      })).data.data,
    onSuccess: (p) => {
      toast.success("Payslip generated");
      setGeneratedId(p.id);
      qc.invalidateQueries({ queryKey: ["payroll-month-payslips"] });
    },
    onError: (e) => toast.error(apiMessage(e, "Could not generate payslip"))
  });

  const years = [now.year(), now.year() - 1, now.year() - 2];

  return (
    <Dialog open onClose={onClose} className="max-w-md">
      <DialogHeader title={`Generate payslip — ${employee.name}`} />
      <div className="mt-3 space-y-3">
        {/* Which basic this payslip will be built on, so the month picked above
            visibly decides it rather than being taken on trust. */}
        <div className="flex items-center justify-between rounded-md border bg-muted/30 px-3 py-2 text-sm">
          <span className="text-muted-foreground">
            Basic for {MONTHS[month - 1]} {year}
          </span>
          <span className="text-right">
            {basicPreview.isLoading ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <>
                <span className="font-semibold tabular-nums">{inr(effectiveBasic)}</span>
                <span className="ml-1.5 text-[11px] text-muted-foreground">
                  {monthBasic != null ? "from Salary details" : "standing basic"}
                </span>
              </>
            )}
          </span>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Month">
            <select className="w-full rounded-md border bg-background px-3 py-2 text-sm" value={month} onChange={(e) => setMonth(Number(e.target.value))}>
              {MONTHS.map((m, i) => <option key={m} value={i + 1}>{m}</option>)}
            </select>
          </Field>
          <Field label="Year">
            <select className="w-full rounded-md border bg-background px-3 py-2 text-sm" value={year} onChange={(e) => setYear(Number(e.target.value))}>
              {years.map((y) => <option key={y} value={y}>{y}</option>)}
            </select>
          </Field>
          <Field label="Performance Pay (₹)"><Input type="number" min="0" value={performancePay} onChange={(e) => setPerformancePay(e.target.value)} placeholder="0" /></Field>
          <Field label="Overtime hours"><Input type="number" min="0" value={overtime} onChange={(e) => setOvertime(e.target.value)} placeholder="0" /></Field>
          <Field label="TDS (₹)"><Input type="number" min="0" value={tds} onChange={(e) => setTds(e.target.value)} placeholder="0" /></Field>
          <Field label="Advance (₹)"><Input type="number" min="0" value={advance} onChange={(e) => setAdvance(e.target.value)} placeholder="0" /></Field>
          <Field label="Loss of Pay (₹)"><Input type="number" min="0" value={lopAmt} onChange={(e) => setLopAmt(e.target.value)} placeholder="0" /></Field>
          <Field label="Other deductions (₹)"><Input type="number" min="0" value={otherDed} onChange={(e) => setOtherDed(e.target.value)} placeholder="0" /></Field>
        </div>
        {lopPreview.data && (
          <div className="grid grid-cols-2 gap-x-4 gap-y-1.5 rounded-md border bg-muted/30 px-3 py-2.5 text-xs sm:grid-cols-4">
            <Count label="Leaves taken" value={`${totalLeaveDays}d`} />
            <Count label="Paid / unpaid" value={`${paidLeaveDays}d / ${unpaidDays}d`} />
            <Count label="Absent" value={`${absentDays}d`} tone={absentDays > 0 ? "bad" : undefined} />
            <Count label="Present" value={`${presentDays} of ${workingDays}d`} />
          </div>
        )}
        {lopPreview.data && (
          <div className="rounded-md border bg-muted/20 px-3 py-2.5 text-xs">
            {deductibleDays > 0 ? (
              <>
                <div className="font-semibold">
                  Loss of Pay: {deductibleDays} day{deductibleDays === 1 ? "" : "s"} not paid for
                  {" "}× {inr(Math.round(perDay))}/day = <span className="text-destructive">{inr(autoLop)}</span>
                </div>
                <div className="mt-1 space-y-0.5 text-muted-foreground">
                  {unpaidDays > 0 && (
                    <div>· {unpaidDays} unpaid leave day{unpaidDays === 1 ? "" : "s"}</div>
                  )}
                  {absentDays > 0 && (
                    <div>· {absentDays} absent day{absentDays === 1 ? "" : "s"} — no punch and no approved leave</div>
                  )}
                  <div>
                    Per day = {inr(grossMonthly)} ÷ {workingDays} working days.
                    Casual and Sick leave are paid and never deducted.
                  </div>
                </div>
                <div className="mt-1 text-muted-foreground">You can edit the amount above.</div>
              </>
            ) : (
              <span className="text-muted-foreground">
                Nothing to deduct for {MONTHS[month - 1]} {year} — every working day was
                either worked or covered by paid leave.
              </span>
            )}
          </div>
        )}

        {/* What the payslip will actually say, before it is generated. Getting a
            figure wrong used to be visible only afterwards. */}
        <div className="rounded-md border border-primary/30 bg-primary/5 px-3 py-2.5 text-xs">
          <div className="mb-1.5 font-semibold uppercase tracking-wide text-muted-foreground">
            This payslip
          </div>
          <div className="space-y-0.5">
            <Line label="Basic" value={effectiveBasic} />
            {salaryParts.hra > 0 && <Line label="HRA" value={salaryParts.hra} />}
            {salaryParts.allowances > 0 && <Line label="Allowances" value={salaryParts.allowances} />}
            {num(performancePay) > 0 && <Line label="Performance pay" value={num(performancePay)} />}
            {overtimePay > 0 && (
              <Line label={`Overtime (${num(overtime)} hrs)`} value={overtimePay} />
            )}
            <Line label="Gross" value={grossPreview} bold />
            {salaryParts.pf > 0 && <Line label="PF" value={-salaryParts.pf} />}
            {esiPreview > 0 && <Line label="ESI" value={-esiPreview} />}
            {salaryParts.pt > 0 && <Line label="Professional tax" value={-salaryParts.pt} />}
            {num(tds) > 0 && <Line label="TDS" value={-num(tds)} />}
            {num(advance) > 0 && <Line label="Advance" value={-num(advance)} />}
            {num(lopAmt) > 0 && (
              <Line label={`Loss of Pay (${deductibleDays}d)`} value={-num(lopAmt)} />
            )}
            {num(otherDed) > 0 && <Line label="Other deductions" value={-num(otherDed)} />}
            <div className="mt-1 flex items-baseline justify-between border-t pt-1.5">
              <span className="font-bold">Net pay</span>
              <span className={cn(
                "font-display text-base font-bold tabular-nums",
                netPreview < 0 ? "text-destructive" : "text-emerald-700 dark:text-emerald-400"
              )}>
                {inr(netPreview)}
              </span>
            </div>
          </div>
          {netPreview < 0 && (
            <p className="mt-1.5 font-medium text-destructive">
              The deductions come to more than the pay. Check the figures before generating.
            </p>
          )}
        </div>

        {generatedId ? (
          <div className="rounded-md border border-emerald-300 bg-emerald-50 px-3 py-2 text-sm dark:border-emerald-800 dark:bg-emerald-950/30">
            ✅ Payslip for {MONTHS[month - 1]} {year} is ready — <span className="font-medium">{employee.name}</span> can now view &amp; download it from their Payslips page.
            <div className="mt-2 flex gap-2">
              <Button size="sm" variant="outline" onClick={onClose}>Done</Button>
            </div>
          </div>
        ) : (
          <div className="flex justify-end gap-2 pt-1">
            <Button variant="outline" onClick={onClose}>Cancel</Button>
            <Button disabled={gen.isPending} onClick={() => gen.mutate()}>
              {gen.isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <FileText className="mr-2 h-4 w-4" />}
              Generate
            </Button>
          </div>
        )}
      </div>
    </Dialog>
  );
}

function PayslipsDialog({ employee, canDownload, onClose }: { employee: UserSummary; canDownload: boolean; onClose: () => void }) {
  const list = useQuery({
    queryKey: ["payslips-for", employee.id],
    queryFn: async () =>
      (await api.get<ApiEnvelope<PayslipSum[]>>(`/payroll/payslip/list/${employee.id}`)).data.data
  });

  return (
    <Dialog open onClose={onClose} className="max-w-md">
      <DialogHeader title={`Payslips — ${employee.name}`} />
      <div className="mt-3">
        {list.isLoading ? (
          <Skeleton className="h-32 w-full" />
        ) : (list.data?.length ?? 0) === 0 ? (
          <p className="py-6 text-center text-sm text-muted-foreground">No payslips generated yet.</p>
        ) : (
          <div className="divide-y rounded-md border">
            {list.data!.map((p) => (
              <div key={p.id} className="flex items-center justify-between px-3 py-2.5">
                <div>
                  <div className="text-sm font-medium">{MONTHS[p.payMonth - 1]} {p.payYear}</div>
                  <div className="text-xs text-muted-foreground">
                    Net {inr(p.netPay)}{p.grossSalary != null ? ` · Gross ${inr(p.grossSalary)}` : ""}
                  </div>
                  <MonthAbsenceLine userId={employee.id} month={p.payMonth} year={p.payYear} />
                </div>
                <div className="flex items-center gap-2">
                  <Button variant="outline" size="sm" onClick={() => viewPayslipPdf(p.id)}>
                    <Eye className="h-4 w-4" /> View
                  </Button>
                  {canDownload && (
                    <Button variant="outline" size="sm" onClick={() => downloadPayslipPdf(p.id, employee.name)}>
                      <Download className="h-4 w-4" /> Download
                    </Button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </Dialog>
  );
}

/** What the leave preview reports for one employee and one month. */
interface LopPreview {
  unpaidLeaveDays: number;
  paidLeaveDays: number;
  totalLeaveDays: number;
  leaveRequestCount: number;
  presentDays: number;
  absentDays: number;
  workingDaysInMonth: number;
  /** Unpaid leave plus absence — every day that is not paid for. */
  deductibleDays: number;
  lopFromUnpaidLeave: number;
  lopFromAbsence: number;
}

/** One figure with its label, for the small counts strip. */
/** One line of the payslip preview. A negative value reads as a deduction. */
function Line({ label, value, bold }: { label: string; value: number; bold?: boolean }) {
  const negative = value < 0;
  return (
    <div className="flex items-baseline justify-between gap-3">
      <span className={cn("text-muted-foreground", bold && "font-semibold text-foreground")}>
        {label}
      </span>
      <span className={cn(
        "tabular-nums",
        bold && "font-semibold",
        negative && "text-destructive"
      )}>
        {negative ? "− " : ""}{inr(Math.abs(value))}
      </span>
    </div>
  );
}

function Count({ label, value, tone }: { label: string; value: string; tone?: "bad" }) {
  return (
    <div>
      <div className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">{label}</div>
      <div className={tone === "bad" ? "font-semibold text-destructive" : "font-semibold"}>{value}</div>
    </div>
  );
}

/**
 * Leave and absence for the month a payslip covers. Read on the payslip list so
 * whoever is checking a slip can see what was behind the number.
 */
function MonthAbsenceLine({ userId, month, year }: { userId: number; month: number; year: number }) {
  const q = useQuery({
    queryKey: ["lop-preview", userId, year, month],
    retry: false,
    queryFn: async () =>
      (await api.get<ApiEnvelope<LopPreview>>(
        `/leave/lop-preview?userId=${userId}&year=${year}&month=${month}`)).data.data
  });
  if (!q.data) return null;
  return (
    <div className="mt-0.5 text-xs text-muted-foreground">
      Leave {Number(q.data.totalLeaveDays ?? 0)}d · Absent{" "}
      <span className={Number(q.data.absentDays ?? 0) > 0 ? "font-semibold text-destructive" : ""}>
        {Number(q.data.absentDays ?? 0)}d
      </span>{" "}
      · Present {Number(q.data.presentDays ?? 0)} of {Number(q.data.workingDaysInMonth ?? 0)}d
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <Label>{label}</Label>
      {children}
    </div>
  );
}
