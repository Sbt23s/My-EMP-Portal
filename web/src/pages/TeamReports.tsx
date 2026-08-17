import { CustomLoader as Loader2 } from "@/components/ui/custom-loader";
import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  CalendarCheck, ClipboardList, Clock, Download, ListTodo,
  Map as MapIcon, Plane, UserX, Users
} from "lucide-react";
import dayjs from "dayjs";
import toast from "react-hot-toast";
import * as XLSX from "xlsx";
import { api, apiMessage } from "@/lib/api";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { StatTile, TILE_FILLS } from "@/components/ui/stat-tile";
import { cn } from "@/lib/utils";
import type {
  ApiEnvelope, AttendanceRecord, EmployeeWorkList, EmployeeTaskGroup, LeaveRequest, UserSummary
} from "@/types";

const MONTHS = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December"
];

type Row = (string | number)[];
type Sheet = { headers: Row; rows: Row[]; cols: number[]; name: string };

/** How the range is chosen for a report. */
type Mode = "RANGE" | "MONTH" | "YEAR";

const REPORTS = [
  { key: "attendance", label: "Attendance", icon: CalendarCheck, fill: TILE_FILLS.blue,
    hint: "Punch in / out, hours and late marks" },
  { key: "absentees", label: "Absentees", icon: UserX, fill: TILE_FILLS.red,
    hint: "Who was missing each working day, and why" },
  { key: "leave", label: "Leave", icon: Plane, fill: TILE_FILLS.amber,
    hint: "Requests, decisions and reasons" },
  { key: "permission", label: "Permission", icon: Clock, fill: TILE_FILLS.orange,
    hint: "Hours-wise time off during a work day" },
  { key: "work", label: "Work reports", icon: ClipboardList, fill: TILE_FILLS.green,
    hint: "Daily project hours logged" },
  { key: "tasks", label: "Tasks", icon: ListTodo, fill: TILE_FILLS.pink,
    hint: "Assignments, progress and due dates" },
  { key: "claims", label: "Claims", icon: MapIcon, fill: TILE_FILLS.violet,
    hint: "Expense claims and their outcome" }
] as const;

type ReportKey = typeof REPORTS[number]["key"];

const fmt = (d?: string) => (d ? dayjs(d).format("DD MMM YYYY") : "");
const time = (d?: string) => (d ? dayjs(d).format("h:mm A") : "");

/**
 * A Team Leader's own reports. Every sheet is built from an endpoint already
 * scoped to their team, so nothing here can reach another team's data — unlike
 * the company-wide /reports endpoints, which stay with HR and admins.
 */
export default function TeamReportsPage({ orgWide = false }: { orgWide?: boolean }) {
  const [report, setReport] = useState<ReportKey>("attendance");
  const [mode, setMode] = useState<Mode>("RANGE");
  const [from, setFrom] = useState(dayjs().startOf("month").format("YYYY-MM-DD"));
  const [to, setTo] = useState(dayjs().format("YYYY-MM-DD"));
  const [month, setMonth] = useState(String(dayjs().month() + 1).padStart(2, "0"));
  const [year, setYear] = useState(String(dayjs().year()));
  const [busy, setBusy] = useState(false);

  // The effective window, whichever way it was chosen.
  const range = useMemo(() => {
    if (mode === "MONTH") {
      const start = dayjs(`${year}-${month}-01`);
      return { from: start.format("YYYY-MM-DD"), to: start.endOf("month").format("YYYY-MM-DD") };
    }
    if (mode === "YEAR") {
      return { from: `${year}-01-01`, to: `${year}-12-31` };
    }
    return { from, to };
  }, [mode, from, to, month, year]);

  const label = mode === "MONTH"
    ? `${MONTHS[Number(month) - 1]} ${year}`
    : mode === "YEAR" ? year
      : `${fmt(range.from)} – ${fmt(range.to)}`;

  const years = useMemo(() => {
    const now = dayjs().year();
    return Array.from({ length: 5 }, (_, i) => String(now - i));
  }, []);

  // The team roster — named, so an absentee report can list who was missing.
  const teamQ = useQuery({
    queryKey: ["team-reports", "roster", orgWide],
    queryFn: async () => {
      if (orgWide) {
        const res = await api.get<ApiEnvelope<{ content: UserSummary[] }>>(
          "/users?status=ACTIVE&size=1000"
        );
        return { members: res.data.data.content ?? [] };
      }
      const res = await api.get<ApiEnvelope<{ teamName?: string; members?: UserSummary[] }>>(
        "/users/my-team"
      );
      return res.data.data;
    }
  });
  const roster = teamQ.data?.members ?? [];

  // Org-wide reads pull every team's rows; the team versions stay scoped.
  const WORK_URL = orgWide ? "/work-reports/all" : "/work-reports/team";
  const CLAIMS_URL = orgWide ? "/ta-expenses/all" : "/ta-expenses/team";
  const PERMS_URL = orgWide ? "/leave/permissions/all" : "/leave/permissions/for-me";

  const inWindow = (d?: string) =>
    !!d && d.slice(0, 10) >= range.from && d.slice(0, 10) <= range.to;

  /** True when a start/end pair overlaps the window at all, not just its edges. */
  const overlapsWindow = (start?: string, end?: string) =>
    !!start && !!end
    && start.slice(0, 10) <= range.to && end.slice(0, 10) >= range.from;

  /** Builds the sheet for the chosen report from team-scoped endpoints. */
  async function buildSheet(key: ReportKey): Promise<Sheet> {
    if (key === "attendance") {
      const rows = (await api.get<ApiEnvelope<AttendanceRecord[]>>(
        `/attendance/team-range?from=${range.from}&to=${range.to}`
      )).data.data ?? [];
      return {
        name: "Attendance",
        headers: ["Date", "Employee", "Employee ID", "Status", "Punch In", "Punch Out",
                  "Hours", "Late", "Mode"],
        cols: [14, 22, 13, 12, 11, 11, 8, 8, 12],
        rows: rows
          .slice()
          .sort((a, b) => String(a.workDate).localeCompare(String(b.workDate)))
          .map((a: any) => [
            fmt(a.workDate), a.employeeName ?? "", a.employeeCode ?? "",
            a.status ?? "", time(a.punchInAt), time(a.punchOutAt),
            Number(a.workedHours ?? 0), a.late ? "Yes" : "No", a.mode ?? ""
          ])
      };
    }

    if (key === "leave") {
      const rows = (await api.get<ApiEnvelope<LeaveRequest[]>>("/leave/requests-for-me")).data.data ?? [];
      return {
        name: "Leave",
        headers: ["Applied On", "Employee", "Employee ID", "Team", "Leave Type", "From", "To",
                  "Days", "Status", "Reason", "Requested To", "Decided By", "Decided On", "Remark"],
        cols: [14, 22, 13, 18, 16, 14, 14, 7, 12, 34, 20, 20, 14, 30],
        rows: rows
          // A leave that starts before the window and ends after it still counts.
          .filter((r: any) => overlapsWindow(r.fromDate, r.toDate))
          .sort((a: any, b: any) => String(a.fromDate).localeCompare(String(b.fromDate)))
          .map((r: any) => [
            fmt(r.createdAt), r.employeeName ?? "", r.employeeCode ?? "", r.team ?? "",
            r.leaveTypeName ?? "", fmt(r.fromDate), fmt(r.toDate),
            Number(r.workingDays ?? 0), r.status ?? "", r.reason ?? "",
            r.requestedToName ?? "", r.decidedByName ?? "", fmt(r.decidedAt),
            r.decisionComment ?? ""
          ])
      };
    }

    if (key === "permission") {
      const rows = (await api.get<ApiEnvelope<any[]>>(PERMS_URL)).data.data ?? [];
      const today = dayjs().format("YYYY-MM-DD");
      const status = (r: any) =>
        r.status === "PENDING" && r.requestDate && String(r.requestDate).slice(0, 10) < today
          ? "OVERDUE" : r.status;
      return {
        name: "Permission",
        headers: ["Date", "Employee", "Employee ID", "Team", "From", "To", "Hours",
                  "Reason", "Status", "Requested To", "Decided By", "Decided On", "Remark"],
        cols: [14, 22, 13, 18, 10, 10, 8, 34, 12, 20, 20, 14, 30],
        rows: rows
          .filter((r) => inWindow(r.requestDate))
          .sort((a, b) => String(a.requestDate).localeCompare(String(b.requestDate)))
          .map((r) => [
            fmt(r.requestDate), r.employeeName ?? "", r.employeeCode ?? "", r.team ?? "",
            r.fromTime ?? "", r.toTime ?? "", Number(r.hours ?? 0),
            r.reason ?? "", status(r), r.requestedToName ?? "",
            r.decidedByName ?? "", fmt(r.decidedAt), r.decisionComment ?? ""
          ])
      };
    }

    if (key === "absentees") {
      // Who was missing, and why: no punch-in on a working day, with approved
      // leave and permission pulled in so the reason is on the same row.
      const [att, leaves, perms, holidays] = await Promise.all([
        api.get<ApiEnvelope<AttendanceRecord[]>>(
          `/attendance/team-range?from=${range.from}&to=${range.to}`),
        api.get<ApiEnvelope<LeaveRequest[]>>("/leave/requests-for-me"),
        api.get<ApiEnvelope<any[]>>(PERMS_URL),
        api.get<ApiEnvelope<{ holidayDate: string; name: string }[]>>(
          `/org/holidays?year=${dayjs(range.from).year()}`)
      ]);

      const punched = new Set(
        (att.data.data ?? []).map((a: any) => `${a.userId}|${String(a.workDate).slice(0, 10)}`));
      const holidayByDate = new Map<string, string>();
      (holidays.data.data ?? []).forEach((h) =>
        holidayByDate.set(String(h.holidayDate).slice(0, 10), h.name));

      // date -> userId -> the approved leave covering it.
      const leaveOn = new Map<string, any>();
      (leaves.data.data ?? [])
        .filter((l: any) => l.status === "APPROVED")
        .forEach((l: any) => {
          let d = dayjs(l.fromDate);
          const end = dayjs(l.toDate);
          let guard = 0;
          while ((d.isBefore(end) || d.isSame(end, "day")) && guard < 400) {
            leaveOn.set(`${l.userId}|${d.format("YYYY-MM-DD")}`, l);
            d = d.add(1, "day");
            guard++;
          }
        });

      const permOn = new Map<string, any>();
      (perms.data.data ?? [])
        .filter((p: any) => p.status === "APPROVED")
        .forEach((p: any) => permOn.set(`${p.userId}|${String(p.requestDate).slice(0, 10)}`, p));

      const rows: Row[] = [];
      let d = dayjs(range.from);
      const end = dayjs(range.to);
      let guard = 0;
      while ((d.isBefore(end) || d.isSame(end, "day")) && guard < 400) {
        const key = d.format("YYYY-MM-DD");
        // Saturday and Sunday are both non-working, matching attendance and
        // payroll. Reported as worked, every Saturday produced a page of
        // absences for a day nobody was rostered.
        const isSunday = d.day() === 0 || d.day() === 6;
        const holiday = holidayByDate.get(key);
        if (!isSunday && !holiday && !d.isAfter(dayjs(), "day")) {
          roster.forEach((m: any) => {
            const id = `${m.id}|${key}`;
            if (punched.has(id)) return;
            const leave = leaveOn.get(id);
            const perm = permOn.get(id);
            rows.push([
              fmt(key), d.format("dddd"), m.name ?? "", m.employeeCode ?? "",
              leave ? "On leave" : "Absent",
              leave ? (leave.leaveTypeName ?? "Leave") : perm ? "Permission taken" : "No punch-in",
              leave?.reason ?? perm?.reason ?? ""
            ]);
          });
        }
        d = d.add(1, "day");
        guard++;
      }

      return {
        name: "Absentees",
        headers: ["Date", "Day", "Employee", "Employee ID", "Type", "Reason", "Note"],
        cols: [14, 12, 22, 13, 12, 22, 34],
        rows
      };
    }

    if (key === "work") {
      const groups = (await api.get<ApiEnvelope<EmployeeWorkList[]>>(WORK_URL)).data.data ?? [];
      const flat: any[] = [];
      groups.forEach((g: any) => (g.rows ?? []).forEach((r: any) =>
        flat.push({ ...r, employeeName: g.employeeName, employeeCode: g.employeeCode })));
      return {
        name: "Work Reports",
        headers: ["Date", "Employee", "Employee ID", "Project", "Hours", "Task / Module"],
        cols: [14, 22, 13, 24, 8, 52],
        rows: flat
          .filter((r) => inWindow(r.workDate))
          .sort((a, b) => String(a.workDate).localeCompare(String(b.workDate)))
          .map((r) => [
            fmt(r.workDate), r.employeeName ?? "", r.employeeCode ?? "",
            r.projectName ?? "", Number(r.workHours ?? 0), r.taskDescription ?? ""
          ])
      };
    }

    if (key === "tasks") {
      const groups = (await api.get<ApiEnvelope<EmployeeTaskGroup[]>>("/tasks/all")).data.data ?? [];
      const flat: any[] = [];
      groups.forEach((g: any) => (g.tasks ?? []).forEach((t: any) =>
        flat.push({ ...t, employeeName: g.employeeName, employeeCode: g.employeeCode })));
      const today = dayjs().format("YYYY-MM-DD");
      const status = (t: any) =>
        t.status === "COMPLETED" ? "Completed"
          : t.dueDate && String(t.dueDate).slice(0, 10) < today ? "Overdue"
            : (t.progress ?? 0) > 0 ? "In Progress" : "Pending";
      return {
        name: "Tasks",
        headers: ["Assigned On", "Employee", "Employee ID", "Team", "Task", "Details",
                  "Priority", "Status", "Progress %", "Due Date", "Completed On"],
        cols: [14, 22, 13, 18, 28, 40, 10, 13, 11, 14, 14],
        rows: flat
          // A task belongs to the window by when it was set or when it is due.
          .filter((t) => inWindow(t.createdAt) || inWindow(t.dueDate))
          .sort((a, b) => String(b.createdAt ?? "").localeCompare(String(a.createdAt ?? "")))
          .map((t) => [
            fmt(t.createdAt), t.employeeName ?? "", t.employeeCode ?? "", t.teamName ?? "",
            t.title ?? "", t.description ?? "", (t.priority || "MEDIUM"),
            status(t), Number(t.progress ?? 0), fmt(t.dueDate), fmt(t.completedAt)
          ])
      };
    }

    const rows = (await api.get<ApiEnvelope<any[]>>(CLAIMS_URL)).data.data ?? [];
    return {
      name: "Claims",
      headers: ["Date", "Employee", "Employee ID", "Team", "Category", "Location",
                "Distance (KM)", "Bus Fare", "Others", "Total", "Status",
                "Decided By", "Remark"],
      cols: [14, 22, 13, 18, 20, 18, 13, 10, 10, 12, 12, 20, 30],
      rows: rows
        .filter((r) => inWindow(r.date))
        .sort((a, b) => String(a.date).localeCompare(String(b.date)))
        .map((r) => [
          fmt(r.date), r.userName ?? "", r.employeeCode ?? "", r.team ?? "",
          r.category ?? "", r.location ?? "", Number(r.totalKm ?? 0),
          Number(r.busFare ?? 0), Number(r.others ?? 0), Number(r.grossTotal ?? 0),
          r.status ?? "", r.decidedByName ?? "", r.decisionComment ?? ""
        ])
    };
  }

  const writeBook = (sheets: Sheet[], filename: string) => {
    const wb = XLSX.utils.book_new();
    sheets.forEach((sh) => {
      // A title row above the headers so a printed sheet explains itself.
      const ws = XLSX.utils.aoa_to_sheet([
        [`${sh.name} — ${label}`],
        [],
        sh.headers,
        ...sh.rows
      ]);
      ws["!cols"] = sh.cols.map((wch) => ({ wch }));
      ws["!merges"] = [{ s: { r: 0, c: 0 }, e: { r: 0, c: sh.headers.length - 1 } }];
      XLSX.utils.book_append_sheet(wb, ws, sh.name.slice(0, 31));
    });
    XLSX.writeFile(wb, filename);
  };

  const tag = () => (mode === "MONTH" ? `${year}-${month}` : mode === "YEAR" ? year : `${range.from}_to_${range.to}`);

  const downloadOne = async () => {
    setBusy(true);
    const id = toast.loading("Building your report…");
    try {
      const sheet = await buildSheet(report);
      if (sheet.rows.length === 0) {
        toast.error(`Nothing to report for ${label}.`, { id });
        return;
      }
      writeBook([sheet], `${orgWide ? "All" : "Team"}_${sheet.name.replace(/\s+/g, "_")}_${tag()}.xlsx`);
      toast.success(`Exported ${sheet.rows.length} row${sheet.rows.length === 1 ? "" : "s"}`, { id });
    } catch (err) {
      toast.error(apiMessage(err, "Could not build the report"), { id });
    } finally {
      setBusy(false);
    }
  };

  const active = REPORTS.find((r) => r.key === report)!;

  return (
    <div className="space-y-4">
      {/* Pick the report — one small tile each. */}
      <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-3 lg:grid-cols-5">
        {REPORTS.map((r) => (
          <StatTile
            key={r.key}
            compact
            label={r.label}
            value=""
            hint={r.hint}
            icon={r.icon}
            fill={r.fill}
            active={report === r.key}
            onClick={() => setReport(r.key)}
          />
        ))}
      </div>

      <Card>
        <CardContent className="space-y-5 p-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h3 className="flex items-center gap-2 font-semibold">
                <active.icon className="h-4 w-4 text-muted-foreground" />
                {active.label} report
              </h3>
              <p className="text-[11px] text-muted-foreground">
                {active.hint} · {orgWide ? "every team" : "your team only"}
              </p>
            </div>
            <div className="flex items-center gap-1.5 rounded-full border bg-muted/60 px-3 py-1.5 text-xs">
              <Users className="h-3.5 w-3.5 text-muted-foreground" />
              <span className="font-semibold">{roster.length}</span>
              <span className="text-muted-foreground">
                {orgWide ? "employees" : "in your team"}
              </span>
            </div>
          </div>

          {/* Date range, by month, or a whole year. */}
          <div className="inline-flex rounded-full border bg-muted/60 p-1">
            {([["RANGE", "Date range"], ["MONTH", "By month"], ["YEAR", "By year"]] as const).map(([key, text]) => (
              <button
                key={key}
                type="button"
                onClick={() => setMode(key)}
                className={cn(
                  "rounded-full px-4 py-1.5 text-xs font-semibold transition-colors",
                  mode === key
                    ? "bg-primary text-primary-foreground shadow-sm"
                    : "text-muted-foreground hover:text-foreground"
                )}
              >
                {text}
              </button>
            ))}
          </div>

          <div className="flex flex-wrap items-end gap-3">
            {mode === "RANGE" && (
              <>
                <div className="space-y-1">
                  <label className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">From</label>
                  <Input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} className="w-40" />
                </div>
                <div className="space-y-1">
                  <label className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">To</label>
                  <Input type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} className="w-40" />
                </div>
              </>
            )}
            {mode === "MONTH" && (
              <>
                <div className="space-y-1">
                  <label className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Month</label>
                  <Select value={month} onChange={(e) => setMonth(e.target.value)} className="w-36">
                    {MONTHS.map((m, i) => (
                      <option key={m} value={String(i + 1).padStart(2, "0")}>{m}</option>
                    ))}
                  </Select>
                </div>
                <div className="space-y-1">
                  <label className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Year</label>
                  <Select value={year} onChange={(e) => setYear(e.target.value)} className="w-28">
                    {years.map((y) => <option key={y} value={y}>{y}</option>)}
                  </Select>
                </div>
              </>
            )}
            {mode === "YEAR" && (
              <div className="space-y-1">
                <label className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Year</label>
                <Select value={year} onChange={(e) => setYear(e.target.value)} className="w-28">
                  {years.map((y) => <option key={y} value={y}>{y}</option>)}
                </Select>
              </div>
            )}
            <span className="ml-auto text-xs text-muted-foreground">Covering {label}</span>
          </div>

          <div className="flex flex-wrap justify-end gap-2 border-t pt-4">
            <Button disabled={busy} onClick={downloadOne} className="bg-green-600 text-white hover:bg-green-700">
              {busy ? <Loader2 className="mr-1.5 h-4 w-4 animate-spin" /> : <Download className="mr-1.5 h-4 w-4" />}
              Download {active.label.toLowerCase()}
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
