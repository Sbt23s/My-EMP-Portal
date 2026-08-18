import { CustomLoader as Loader2 } from "@/components/ui/custom-loader";
import { useState, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { Download, Wallet, Eye, Users, Banknote, WalletCards, ReceiptText } from "lucide-react";
import toast from "react-hot-toast";
import dayjs from "dayjs";
import { api, apiMessage } from "@/lib/api";
import { PageHeader } from "@/components/PageHeader";
import { EmptyState } from "@/components/EmptyState";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { formatMoney, monthName } from "@/lib/utils";
import { usePagedRows, TablePagination } from "@/components/ui/table-pagination";
import { cn } from "@/lib/utils";
import type { ApiEnvelope, PayslipSummary } from "@/types";

const MONTHS = Array.from({ length: 12 }, (_, i) => i + 1);
const CUR_MONTH = new Date().getMonth() + 1;
const CUR_YEAR = new Date().getFullYear();
const YEARS = [CUR_YEAR, CUR_YEAR - 1, CUR_YEAR - 2, CUR_YEAR - 3];

export default function PayslipsPage() {
  const [downloading, setDownloading] = useState<number | null>(null);
  const [fMonth, setFMonth] = useState<string>("all");
  const [fYear, setFYear] = useState<string>(String(CUR_YEAR));

  const payslips = useQuery({
    queryKey: ["payslips"],
    queryFn: async () =>
      (await api.get<ApiEnvelope<PayslipSummary[]>>("/payroll/payslip/list")).data.data
  });

  async function download(id: number, label: string) {
    setDownloading(id);
    try {
      const res = await api.get(`/payroll/payslip/${id}/pdf`, { responseType: "blob" });
      const url = URL.createObjectURL(res.data as Blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `payslip-${label}.pdf`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      toast.error(apiMessage(err, "Could not download payslip"));
    } finally {
      setDownloading(null);
    }
  }

  async function viewPdf(id: number) {
    setDownloading(id);
    try {
      const res = await api.get(`/payroll/payslip/${id}/pdf`, { responseType: "blob" });
      const url = URL.createObjectURL(res.data as Blob);
      window.open(url, "_blank");
      setTimeout(() => URL.revokeObjectURL(url), 10000);
    } catch (err) {
      toast.error(apiMessage(err, "Could not view payslip"));
    } finally {
      setDownloading(null);
    }
  }

  async function exportToExcel() {
    try {
      const XLSX = await import("xlsx");
      const list = payslips.data ?? [];
      const headers = ["Month/Year", "Pay Date", "Gross Pay", "Deductions", "Net Pay", "Status"];
      const body = list.map((p) => {
        const payDate = dayjs(`${p.payYear}-${p.payMonth}-01`).endOf("month").format("DD MMM YYYY");
        return [
          `${monthName(p.payMonth)} ${p.payYear}`,
          payDate,
          p.grossSalary,
          p.grossSalary - p.netPay,
          p.netPay,
          "Paid"
        ];
      });

      const ws = XLSX.utils.aoa_to_sheet([headers, ...body]);
      const wb = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(wb, ws, "My Salary Details");
      XLSX.writeFile(wb, "My_Salary_Details.xlsx");
    } catch (err) {
      toast.error("Failed to export Excel");
    }
  }

  // Derived metrics for the top tiles
  const metrics = useMemo(() => {
    const list = payslips.data ?? [];
    if (list.length === 0) {
      return { ctc: 0, net: 0, gross: 0, deductions: 0, ytd: 0, latestLabel: "" };
    }
    
    // Sort to find the latest payslip
    const sorted = [...list].sort((a, b) => {
      if (a.payYear !== b.payYear) return b.payYear - a.payYear;
      return b.payMonth - a.payMonth;
    });
    
    const latest = sorted[0];
    const latestLabel = `${monthName(latest.payMonth).slice(0, 3)} ${latest.payYear}`;
    
    // YTD calculation for the current year
    const ytd = list
      .filter((p) => p.payYear === CUR_YEAR)
      .reduce((sum, p) => sum + p.netPay, 0);

    return {
      ctc: latest.grossSalary * 12, // Extrapolated annual CTC
      net: latest.netPay,
      gross: latest.grossSalary,
      deductions: latest.grossSalary - latest.netPay,
      ytd,
      latestLabel
    };
  }, [payslips.data]);

  const list = (payslips.data ?? []).filter(
    (p) =>
      (fMonth === "all" || p.payMonth === Number(fMonth)) &&
      (fYear === "all" || p.payYear === Number(fYear))
  );
  
  // Sort list newest first for the table
  const sortedList = [...list].sort((a, b) => {
    if (a.payYear !== b.payYear) return b.payYear - a.payYear;
    return b.payMonth - a.payMonth;
  });
  
  const paged = usePagedRows(sortedList, 5, [fMonth, fYear, payslips.data]);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Payslips"
        subtitle="Your monthly payslips — view and download."
        actions={
          <div className="flex items-end gap-2">
            <div className="flex flex-col">
              <label className="mb-0.5 text-[10px] font-semibold uppercase text-muted-foreground">Month</label>
              <select className="h-[38px] rounded-md border bg-background px-3 text-sm" value={fMonth} onChange={(e) => setFMonth(e.target.value)}>
                <option value="all">All months</option>
                {MONTHS.map((m) => <option key={m} value={m}>{monthName(m)}</option>)}
              </select>
            </div>
            <div className="flex flex-col">
              <label className="mb-0.5 text-[10px] font-semibold uppercase text-muted-foreground">Year</label>
              <select className="h-[38px] rounded-md border bg-background px-3 text-sm" value={fYear} onChange={(e) => setFYear(e.target.value)}>
                <option value="all">All years</option>
                {YEARS.map((y) => <option key={y} value={y}>{y}</option>)}
              </select>
            </div>
            <div className="flex flex-col justify-end">
              <Button variant="outline" className="h-[38px] gap-2" onClick={exportToExcel}>
                <Download className="h-4 w-4" />
                Export Excel
              </Button>
            </div>
          </div>
        }
      />

      {payslips.isLoading ? (
        <Skeleton className="h-32 w-full rounded-xl" />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
          <StatCard
            title="Total CTC (Annual)"
            value={formatMoney(metrics.ctc)}
            subtitle="View details →"
            icon={Users}
            color="text-violet-600"
            bg="bg-violet-100"
            titleColor="text-violet-600"
          />
          <StatCard
            title="Net Salary (This Month)"
            value={formatMoney(metrics.net)}
            subtitle={metrics.latestLabel}
            icon={Banknote}
            color="text-green-600"
            bg="bg-green-100"
            titleColor="text-green-600"
          />
          <StatCard
            title="Gross Salary (This Month)"
            value={formatMoney(metrics.gross)}
            subtitle={metrics.latestLabel}
            icon={WalletCards}
            color="text-blue-600"
            bg="bg-blue-100"
            titleColor="text-blue-600"
          />
          <StatCard
            title="Deductions (This Month)"
            value={formatMoney(metrics.deductions)}
            subtitle={metrics.latestLabel}
            icon={ReceiptText}
            color="text-rose-600"
            bg="bg-rose-100"
            titleColor="text-foreground"
          />
          <StatCard
            title="Year to Date Earnings"
            value={formatMoney(metrics.ytd)}
            subtitle={CUR_YEAR.toString()}
            icon={Wallet}
            color="text-orange-600"
            bg="bg-orange-100"
            titleColor="text-foreground"
          />
        </div>
      )}

      {payslips.isLoading ? (
        <Skeleton className="h-64 w-full rounded-xl" />
      ) : list.length === 0 ? (
        <EmptyState
          icon={Wallet}
          title={fMonth !== "all" || fYear !== "all" ? "No payslips for this selection" : "No payslips yet"}
          description={fMonth !== "all" || fYear !== "all" ? "Try a different month or year." : "Your payslips will appear here once admin generates them."}
        />
      ) : (
        <div className="rounded-xl border bg-card overflow-hidden">
          <div className="px-5 py-4 border-b">
            <h3 className="font-semibold">Recent Payslips</h3>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm border-collapse">
              <thead>
                <tr className="border-b border-slate-300 dark:border-slate-700 bg-slate-100/90 dark:bg-slate-800/90 text-left text-xs font-semibold text-slate-800 dark:text-slate-200 [&>th]:whitespace-nowrap [&>th]:px-3.5 [&>th]:py-3 [&>th]:border-r [&>th]:border-slate-300 dark:[&>th]:border-slate-700 last:[&>th]:border-r-0">
                  <th>Month & Year</th>
                  <th>Pay Date</th>
                  <th>Gross Pay</th>
                  <th>Net Pay</th>
                  <th>Status</th>
                  <th className="text-right">Action</th>
                </tr>
              </thead>
              <tbody>
                {paged.pageRows.map((p, idx) => {
                  const label = `${monthName(p.payMonth)}-${p.payYear}`;
                  // Calculate the last day of that month for the Pay Date
                  const payDate = dayjs(`${p.payYear}-${p.payMonth}-01`).endOf('month').format("DD MMM YYYY");
                  
                  return (
                    <tr key={p.id} className="border-b border-slate-200 dark:border-slate-800 align-middle last:border-0 hover:bg-slate-50 dark:hover:bg-slate-800/50 transition-colors [&>td]:px-3.5 [&>td]:py-3 [&>td]:border-r [&>td]:border-b [&>td]:border-slate-200 dark:[&>td]:border-slate-800 last:[&>td]:border-r-0">
                      <td className="font-medium">
                        {monthName(p.payMonth).slice(0, 3)} {p.payYear}
                      </td>
                      <td className="text-muted-foreground font-medium">
                        {payDate}
                      </td>
                      <td className="font-bold tabular-nums">
                        {formatMoney(p.grossSalary)}
                      </td>
                      <td className="font-bold tabular-nums">
                        {formatMoney(p.netPay)}
                      </td>
                      <td>
                        <span className="inline-flex items-center rounded-full bg-emerald-100 px-2.5 py-0.5 text-[11px] font-bold text-emerald-700">
                          Paid
                        </span>
                      </td>
                      <td className="text-right">
                        <div className="flex items-center justify-end gap-2">
                          <button
                            type="button"
                            className="inline-flex h-8 w-8 items-center justify-center rounded-md border text-muted-foreground hover:bg-muted/50 transition-colors"
                            onClick={() => viewPdf(p.id)}
                            title="Preview Payslip"
                          >
                            <Eye className="h-4 w-4" />
                          </button>
                          <button
                            type="button"
                            className="inline-flex h-8 w-8 items-center justify-center rounded-md border text-violet-600 hover:bg-violet-50 transition-colors"
                            onClick={() => download(p.id, label)}
                            disabled={downloading === p.id}
                            title="Download PDF"
                          >
                            {downloading === p.id ? (
                              <Loader2 className="h-4 w-4 animate-spin" />
                            ) : (
                              <Download className="h-4 w-4" />
                            )}
                          </button>
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
              Showing {(paged.page - 1) * paged.pageSize + 1} to {Math.min(paged.page * paged.pageSize, list.length)} of {list.length} entries
            </div>
            <TablePagination
              page={paged.page}
              totalPages={paged.totalPages}
              onChange={paged.setPage}
              pageSize={paged.pageSize}
              onPageSizeChange={paged.setPageSize}
              total={paged.total}
            />
          </div>
        </div>
      )}
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
    <div className="rounded-xl border bg-card p-5 shadow-sm flex items-center justify-between gap-4 transition-all hover:shadow-md">
      <div className="space-y-2">
        <h4 className={cn("text-xs font-bold uppercase tracking-wider", titleColor)}>{title}</h4>
        <div className="text-xl font-bold tabular-nums tracking-tight">{value}</div>
        <div className={cn("text-[11px] font-semibold", titleColor.includes('text-foreground') ? 'text-muted-foreground' : titleColor)}>
          {subtitle}
        </div>
      </div>
      <div className={cn("grid h-12 w-12 shrink-0 place-items-center rounded-full", bg)}>
        <Icon className={cn("h-5 w-5", color)} />
      </div>
    </div>
  );
}
