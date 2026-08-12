import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Download, Loader2, Wallet, IndianRupee } from "lucide-react";
import toast from "react-hot-toast";
import { api, apiMessage } from "@/lib/api";
import { PageHeader } from "@/components/PageHeader";
import { EmptyState } from "@/components/EmptyState";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { formatMoney, monthName } from "@/lib/utils";
import { usePagedRows, TablePagination } from "@/components/ui/table-pagination";
import type { ApiEnvelope, PayslipSummary } from "@/types";

const MONTHS = Array.from({ length: 12 }, (_, i) => i + 1);
const CUR_YEAR = new Date().getFullYear();
const YEARS = [CUR_YEAR + 1, CUR_YEAR, CUR_YEAR - 1, CUR_YEAR - 2];

export default function PayslipsPage() {
  const [downloading, setDownloading] = useState<number | null>(null);
  const [fMonth, setFMonth] = useState<string>("all");
  const [fYear, setFYear] = useState<string>("all");

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

  const list = (payslips.data ?? []).filter(
    (p) =>
      (fMonth === "all" || p.payMonth === Number(fMonth)) &&
      (fYear === "all" || p.payYear === Number(fYear))
  );
  const paged = usePagedRows(list, 12, [fMonth, fYear, payslips.data]);

  return (
    <div>
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
          </div>
        }
      />

      {payslips.isLoading ? (
        <Skeleton className="h-64 w-full rounded-lg" />
      ) : list.length === 0 ? (
        <EmptyState
          icon={Wallet}
          title={fMonth !== "all" || fYear !== "all" ? "No payslips for this selection" : "No payslips yet"}
          description={fMonth !== "all" || fYear !== "all" ? "Try a different month or year." : "Your payslips will appear here once admin generates them."}
        />
      ) : (
        <div className="overflow-x-auto rounded-lg border">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-muted/30 text-left text-[11px] uppercase tracking-wide text-muted-foreground">
                <th className="px-4 py-3">Month</th>
                <th className="px-4 py-3">Gross</th>
                <th className="px-4 py-3">Net Pay</th>
                <th className="px-4 py-3 text-right">Download</th>
              </tr>
            </thead>
            <tbody>
              {paged.pageRows.map((p) => {
                const label = `${monthName(p.payMonth)}-${p.payYear}`;
                return (
                  <tr key={p.id} className="border-b last:border-0 hover:bg-muted/20">
                    <td className="whitespace-nowrap px-4 py-3 font-medium">
                      {monthName(p.payMonth)} {p.payYear}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 tabular-nums text-muted-foreground">
                      {formatMoney(p.grossSalary)}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 font-semibold tabular-nums text-primary">
                      {formatMoney(p.netPay)}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={downloading === p.id}
                        onClick={() => download(p.id, label)}
                      >
                        {downloading === p.id ? (
                          <Loader2 className="h-4 w-4 animate-spin" />
                        ) : (
                          <Download className="h-4 w-4" />
                        )}
                        Download PDF
                      </Button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <div className="border-t px-4 py-2 text-xs text-muted-foreground">
            Showing {paged.pageRows.length} of {list.length} payslip{list.length === 1 ? "" : "s"}
          </div>
          <TablePagination
            page={paged.page}
            totalPages={paged.totalPages}
            onChange={paged.setPage}
            pageSize={paged.pageSize}
            onPageSizeChange={paged.setPageSize}
            total={paged.total}
            always
          />
        </div>
      )}

      <MySalaryDetails month={fMonth} year={fYear} />
    </div>
  );
}

/**
 * Your own basic salary, month by month. The same figures HR records under
 * Salary details, read-only, and following the month and year already chosen
 * above so one selection drives the whole page.
 */
function MySalaryDetails({ month, year }: { month: string; year: string }) {
  const months = useQuery({
    queryKey: ["my-salary-months"],
    retry: false,
    queryFn: async () =>
      (await api.get<ApiEnvelope<{ userId: number; month: number; year: number; basicSalary: number }[]>>(
        "/payroll/salary-months/me"
      )).data.data
  });

  const rows = (months.data ?? []).filter(
    (m) =>
      (month === "all" || m.month === Number(month)) &&
      (year === "all" || m.year === Number(year))
  );
  const paged = usePagedRows(rows, 12, [month, year, months.data]);

  return (
    <div className="mt-6">
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
        <h3 className="flex items-center gap-1.5 font-semibold">
          <IndianRupee className="h-4 w-4 text-primary" /> Salary details
        </h3>
        <span className="text-xs text-muted-foreground">
          Your basic salary, month by month
        </span>
      </div>

      {months.isLoading ? (
        <Skeleton className="h-32 w-full rounded-lg" />
      ) : rows.length === 0 ? (
        <p className="rounded-lg border border-dashed px-4 py-6 text-center text-sm text-muted-foreground">
          {(months.data?.length ?? 0) > 0
            ? "Nothing recorded for this month or year — try another selection."
            : "No basic salary recorded yet. It appears here once HR enters it."}
        </p>
      ) : (
        <div className="overflow-x-auto rounded-lg border">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-muted/30 text-left text-[11px] uppercase tracking-wide text-muted-foreground">
                <th className="px-4 py-3">Month</th>
                <th className="px-4 py-3">Year</th>
                <th className="px-4 py-3 text-right">Basic salary</th>
              </tr>
            </thead>
            <tbody>
              {paged.pageRows.map((m) => (
                <tr key={`${m.year}-${m.month}`} className="border-b last:border-0 hover:bg-muted/20">
                  <td className="whitespace-nowrap px-4 py-2.5 font-medium">{monthName(m.month)}</td>
                  <td className="whitespace-nowrap px-4 py-2.5 tabular-nums text-muted-foreground">{m.year}</td>
                  <td className="whitespace-nowrap px-4 py-2.5 text-right font-semibold tabular-nums">
                    {formatMoney(Number(m.basicSalary))}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="border-t px-4 py-2 text-xs text-muted-foreground">
            Showing {paged.pageRows.length} of {rows.length} month{rows.length === 1 ? "" : "s"}
          </div>
          <TablePagination
            page={paged.page}
            totalPages={paged.totalPages}
            onChange={paged.setPage}
            pageSize={paged.pageSize}
            onPageSizeChange={paged.setPageSize}
            total={paged.total}
            always
          />
        </div>
      )}
    </div>
  );
}
