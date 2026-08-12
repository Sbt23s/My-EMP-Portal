import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  MapPin, LogIn, LogOut, Loader2, Building2, Home, HardHat, Calendar,
  Download, Eye, ScanFace, ShieldCheck, ShieldAlert, Sparkles, AlertTriangle,
  Info, ListTodo, CheckCircle2, UserCog
} from "lucide-react";
import * as XLSX from "xlsx";
import dayjs from "dayjs";
import toast from "react-hot-toast";
import { api, apiMessage } from "@/lib/api";
import { PageHeader } from "@/components/PageHeader";
import { EmptyState } from "@/components/EmptyState";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge, statusVariant } from "@/components/ui/badge";
import { Select } from "@/components/ui/select";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow
} from "@/components/ui/table";
import { minutesToHours } from "@/lib/utils";
import { usePagedRows, TablePagination } from "@/components/ui/table-pagination";
import { useAuth } from "@/hooks/useAuth";
import { Dialog, DialogHeader } from "@/components/ui/dialog";
import { FacePunchDialog } from "@/components/ui/FacePunchDialog";
import { FaceTrainDialog } from "@/components/ui/FaceTrainDialog";
import type { ApiEnvelope, AttendanceRecord } from "@/types";
import { useAttendanceLive } from "@/hooks/useAttendanceLive";

const ANALYTICS_BASE = import.meta.env.VITE_ANALYTICS_URL || "http://localhost:8082";

type AttendanceSummaryType = {
  month: number; year: number; presentDays: number; wfhDays: number;
  lateDays: number; absentDays: number; totalOvertimeMinutes: number;
  totalLateMinutes: number; workingDays: number;
};

const MODES = [
  { value: "OFFICE", label: "Office", icon: Building2 },
  { value: "WFH", label: "Work from home", icon: Home },
  { value: "SITE", label: "Site / field", icon: HardHat }
];

function getPosition(): Promise<GeolocationPosition> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error("Geolocation is not supported on this device"));
      return;
    }
    navigator.geolocation.getCurrentPosition(resolve, reject, {
      enableHighAccuracy: true,
      timeout: 10000
    });
  });
}

interface FaceStatus {
  enrolled: boolean;
  photos: number;
  available: boolean;
  maxPhotos?: number;
  reason?: string;
}

/**
 * Face punching, and whether it is available at all.
 *
 * <p>Three states, and each says what to do about itself: the service is not
 * reachable, the person has not enrolled yet, or they have and can punch. The
 * first two used to be indistinguishable from the feature simply not existing —
 * the dialogs were written and wired to nothing.
 */
function FaceStatusPanel({
  userId, punchedIn, punchedOut, onPunched
}: {
  userId?: number;
  punchedIn: boolean;
  punchedOut: boolean;
  onPunched: () => void;
}) {
  const [punchOpen, setPunchOpen] = useState(false);

  const status = useQuery({
    queryKey: ["face-status", userId],
    enabled: !!userId,
    retry: false,
    queryFn: async (): Promise<FaceStatus> => {
      const res = await fetch(`${ANALYTICS_BASE}/api/face/status/${userId}`);
      if (!res.ok) throw new Error("unavailable");
      return res.json();
    }
  });

  const secure = typeof window !== "undefined" && window.isSecureContext;

  if (!userId) return null;

  // The service is down or was never started. Said plainly, because the rest of
  // attendance works and this is the only part that does not.
  if (status.isError || (status.data && status.data.available === false)) {
    return (
      <div className="flex items-start gap-2 rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-xs">
        <ShieldAlert className="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-600" />
        <span>
          Face verification is unavailable — the analytics service is not reachable.
          Punching still works; the punch is recorded as unverified.
        </span>
      </div>
    );
  }

  if (status.isLoading) return <Skeleton className="h-16" />;

  const enrolled = !!status.data?.enrolled;

  return (
    <div className="space-y-2">
      {!secure && (
        <div className="flex items-start gap-2 rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-xs">
          <ShieldAlert className="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-600" />
          <span>
            The camera needs a secure (https) connection, so face punch cannot run here.
            It works on localhost.
          </span>
        </div>
      )}

      {!enrolled ? (
        /* Registration is not offered here. Somebody has to be able to confirm it
           was the right face in front of the camera, so HR does it — an employee
           enrolling their own face could enrol anybody's. */
        <div className="rounded-lg border border-amber-500/30 bg-amber-500/10 p-3">
          <div className="flex items-start gap-2">
            <ScanFace className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" />
            <div className="min-w-0 flex-1">
              <div className="text-sm font-semibold">Your face is not registered yet</div>
              <p className="mt-0.5 text-xs text-muted-foreground">
                Attendance is marked by face, so you cannot punch until it is.
                Ask HR or your admin to register it — it takes four quick photos
                and is done once.
              </p>
            </div>
          </div>
        </div>
      ) : (
        <>
          <div className="flex items-center gap-1.5 rounded-lg border border-emerald-500/30 bg-emerald-500/5 px-3 py-2 text-xs font-medium text-emerald-700 dark:text-emerald-400">
            <ShieldCheck className="h-3.5 w-3.5" />
            Face registered ({status.data?.photos} photo
            {status.data?.photos === 1 ? "" : "s"})
          </div>

          {!punchedOut && (
            <Button
              className="h-11 w-full"
              disabled={!secure}
              onClick={() => setPunchOpen(true)}
            >
              <ScanFace className="mr-2 h-5 w-5" />
              {punchedIn ? "Punch out with face" : "Punch in with face"}
            </Button>
          )}
        </>
      )}

      <FacePunchDialog
        open={punchOpen}
        onOpenChange={setPunchOpen}
        userId={userId}
        isPunchIn={!punchedIn}
        onDone={onPunched}
      />
    </div>
  );
}

interface InsightFinding {
  code: string;
  tone: "alert" | "warn" | "info";
  title: string;
  detail: string;
  userId?: number;
  employeeCode?: string;
}

/**
 * What the attendance data is trying to say.
 *
 * <p>Every one of these is a question somebody would otherwise have to think to
 * ask: was the whole team late this morning, did anybody forget to punch out, did
 * several people punch from one spot, has somebody quietly stopped coming in. The
 * server computes them from the punches that already exist — nothing here is a
 * guess, and each finding says what it was derived from.
 */
function AttendanceInsights() {
  const insights = useQuery({
    queryKey: ["attendance-insights"],
    queryFn: async () =>
      (await api.get<ApiEnvelope<{
        scope: string; people: number; windowDays: number;
        allClear: boolean; findings: InsightFinding[];
      }>>("/attendance/insights?days=30")).data.data
  });

  if (insights.isLoading) return <Skeleton className="h-28" />;
  if (insights.isError || !insights.data) return null;

  const { findings, scope, windowDays, allClear } = insights.data;
  const icon = (tone: string) =>
    tone === "alert" ? AlertTriangle : tone === "warn" ? ShieldAlert : Info;
  const tint = (tone: string) =>
    tone === "alert"
      ? "border-destructive/40 bg-destructive/5 text-destructive"
      : tone === "warn"
        ? "border-amber-500/40 bg-amber-500/5 text-amber-700 dark:text-amber-400"
        : "border-border bg-muted/30 text-foreground";

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center gap-2 text-base">
          <Sparkles className="h-4 w-4 text-primary" />
          What the attendance says
        </CardTitle>
        <p className="text-xs text-muted-foreground">
          Across {scope}, over the last {windowDays} days.
        </p>
      </CardHeader>
      <CardContent>
        {allClear ? (
          <div className="flex items-center gap-2 rounded-lg border border-emerald-500/30 bg-emerald-500/5 px-3 py-2.5 text-sm">
            <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-600" />
            <span>Nothing unusual. Punches, punch-outs and locations all look ordinary.</span>
          </div>
        ) : (
          <div className="space-y-2">
            {findings.map((f, i) => {
              const Icon = icon(f.tone);
              return (
                <div
                  key={`${f.code}-${i}`}
                  className={`flex items-start gap-2.5 rounded-lg border p-2.5 ${tint(f.tone)}`}
                >
                  <Icon className="mt-0.5 h-4 w-4 shrink-0" />
                  <div className="min-w-0">
                    <div className="text-sm font-semibold">{f.title}</div>
                    <p className="text-xs opacity-80">{f.detail}</p>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

export default function AttendancePage() {
  const qc = useQueryClient();
  const { user } = useAuth();
  const [mode, setMode] = useState("OFFICE");
  const [locating, setLocating] = useState(false);
  // Browsers expose GPS only in a secure context, so on plain HTTP there is
  // nothing to ask for — say so instead of failing quietly at punch time.
  const locationAvailable = typeof window !== "undefined"
    && window.isSecureContext && !!navigator.geolocation;

  /**
   * Which period the history and the summary cover. A month is the default, and
   * an exact date narrows it to one day -- so the same table answers "how was
   * March" and "what happened on the 14th".
   */
  const [period, setPeriod] = useState(dayjs().format("YYYY-MM"));
  const [exactDay, setExactDay] = useState("");

  const periodStart = dayjs(`${period}-01`);
  const from = exactDay || periodStart.startOf("month").format("YYYY-MM-DD");
  const to = exactDay || periodStart.endOf("month").format("YYYY-MM-DD");
  const month = periodStart.month() + 1;
  const year = periodStart.year();

  // A punch made on the phone shows on this page without reloading it, which is
  // the difference between two devices agreeing and two devices arguing.
  useAttendanceLive();

  const today = useQuery({
    queryKey: ["attendance", "today"],
    queryFn: async () =>
      (await api.get<ApiEnvelope<AttendanceRecord | null>>("/attendance/today")).data.data
  });

  const history = useQuery({
    queryKey: ["attendance", "me", from, to],
    queryFn: async () =>
      (await api.get<ApiEnvelope<AttendanceRecord[]>>(`/attendance/me?from=${from}&to=${to}`))
        .data.data
  });

  const summary = useQuery({
    queryKey: ["attendance", "summary", month, year],
    queryFn: async () =>
      (await api.get<ApiEnvelope<AttendanceSummaryType>>(
        `/attendance/me/summary?month=${month}&year=${year}`
      )).data.data
  });

  // Newest day first, then paged — a full month is too long to scroll.
  const historyRows = (history.data ?? [])
    .slice()
    .sort((a, b) => (a.workDate < b.workDate ? 1 : -1));
  // Changing the period is a new list, so it starts back on page one.
  const historyPaged = usePagedRows(historyRows, 10, [history.data, from, to]);

  // The day opened in the details dialog, or null when it is closed.
  const [detail, setDetail] = useState<AttendanceRecord | null>(null);

  /** The month exactly as the table shows it, plus the columns it cannot fit. */
  const exportMonth = () => {
    const headers = ["S.No", "Employee ID", "Employee Name", "Date", "Day",
                     "Punch In", "Punch Out", "Mode", "Status", "Late By",
                     "Hours Worked", "Overtime", "GPS"];
    const data = historyRows.map((r, i) => [
      i + 1,
      user?.employeeCode ?? "",
      user?.name ?? "",
      dayjs(r.workDate).format("DD MMM YYYY"),
      dayjs(r.workDate).format("dddd"),
      r.punchInAt ? dayjs(r.punchInAt).format("h:mm A") : "—",
      r.punchOutAt ? dayjs(r.punchOutAt).format("h:mm A") : "—",
      r.mode,
      r.late ? "LATE" : r.status,
      r.lateMinutes ? minutesToHours(r.lateMinutes) : "—",
      r.workedMinutes ? minutesToHours(r.workedMinutes) : "—",
      r.overtimeMinutes ? minutesToHours(r.overtimeMinutes) : "—",
      [gpsText(r.inLatitude, r.inLongitude) !== "—" ? `In: ${gpsText(r.inLatitude, r.inLongitude)}` : "",
       gpsText(r.outLatitude, r.outLongitude) !== "—" ? `Out: ${gpsText(r.outLatitude, r.outLongitude)}` : ""]
        .filter(Boolean).join("  |  ") || "no GPS"
    ]);
    const ws = XLSX.utils.aoa_to_sheet([headers, ...data]);
    // One width per header, in order. GPS holds two coordinate pairs.
    // One width per header, in order: S.No, Employee ID, Employee Name, Date,
    // Day, Punch In, Punch Out, Mode, Status, Late By, Hours Worked, Overtime,
    // GPS. The GPS column holds two coordinate pairs, so it needs the room.
    ws["!cols"] = [{ wch: 6 }, { wch: 13 }, { wch: 24 }, { wch: 14 }, { wch: 11 },
                   { wch: 11 }, { wch: 11 }, { wch: 9 }, { wch: 11 }, { wch: 10 },
                   { wch: 13 }, { wch: 10 }, { wch: 46 }];
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, "Attendance");
    XLSX.writeFile(wb, exactDay
      ? `My_Attendance_${dayjs(exactDay).format("DD_MMM_YYYY")}.xlsx`
      : `My_Attendance_${periodStart.format("MMM_YYYY")}.xlsx`);
    toast.success(`Exported ${historyRows.length} day${historyRows.length === 1 ? "" : "s"}`);
  };

  const punch = useMutation({
    mutationFn: async (kind: "punch-in" | "punch-out") => {
      setLocating(true);
      let latitude: number | undefined;
      let longitude: number | undefined;
      if (mode !== "WFH") {
        // Best-effort location: capture GPS when the browser allows it, but
        // never block the punch if it's denied or unavailable (e.g. the site is
        // served over plain HTTP, where geolocation is blocked by the browser).
        try {
          const pos = await getPosition();
          latitude = pos.coords.latitude;
          longitude = pos.coords.longitude;
        } catch (e) {
          // proceed without coordinates
        } finally {
          setLocating(false);
        }
      }
      setLocating(false);
      const res = await api.post<ApiEnvelope<AttendanceRecord>>(`/attendance/${kind}`, {
        latitude,
        longitude,
        mode
      });
      return res.data;
    },
    onSuccess: (res) => {
      toast.success(res.message || "Recorded");
      qc.invalidateQueries({ queryKey: ["attendance"] });
      qc.invalidateQueries({ queryKey: ["dashboard"] });
    },
    onError: (err) => {
      setLocating(false);
      toast.error(apiMessage(err, "Could not record attendance"));
    }
  });

  const t = today.data;
  const punchedIn = !!t?.punchInAt;
  const punchedOut = !!t?.punchOutAt;
  const busy = punch.isPending || locating;

  return (
    <div>
      <PageHeader
        title="Attendance"
        subtitle="Punch in and out with location. Field punches are geofence-checked against your site."
      />

      <div className="grid gap-6 lg:grid-cols-3">
        {/* Punch card */}
        <Card className="lg:col-span-1">
          <CardHeader>
            <CardTitle>Today · {dayjs().format("DD MMM")}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-2 gap-3">
              <div className="rounded-lg border p-3">
                <div className="text-xs text-muted-foreground">Punch in</div>
                <div className="font-display text-lg font-semibold">
                  {t?.punchInAt ? dayjs(t.punchInAt).format("h:mm A") : "—"}
                </div>
              </div>
              <div className="rounded-lg border p-3">
                <div className="text-xs text-muted-foreground">Punch out</div>
                <div className="font-display text-lg font-semibold">
                  {t?.punchOutAt ? dayjs(t.punchOutAt).format("h:mm A") : "—"}
                </div>
              </div>
            </div>

            {t && (
              <div className="flex flex-wrap items-center gap-2 text-sm">
                <Badge variant={statusVariant(t.status)}>{t.status}</Badge>
                {t.late && <Badge variant="destructive">Late</Badge>}
                {t.withinGeofence === false && (
                  <Badge variant="warning">Outside geofence</Badge>
                )}
                {t.workedMinutes ? (
                  <span className="text-muted-foreground">
                    {minutesToHours(t.workedMinutes)} worked
                  </span>
                ) : null}
              </div>
            )}

            {!locationAvailable && mode !== "WFH" && (
              <div className="flex items-start gap-2 rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-xs">
                <MapPin className="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-600 dark:text-amber-400" />
                <span>
                  Location cannot be recorded — the browser only shares GPS over a
                  secure (https) connection. Your punch still works and is saved
                  without coordinates.
                </span>
              </div>
            )}

            <div className="space-y-1.5">
              <label className="text-sm font-medium">Mode</label>
              <Select value={mode} onChange={(e) => setMode(e.target.value)} disabled={punchedOut}>
                {MODES.map((m) => (
                  <option key={m.value} value={m.value}>
                    {m.label}
                  </option>
                ))}
              </Select>
              {mode !== "WFH" && (
                <p className="flex items-center gap-1 text-xs text-muted-foreground">
                  <MapPin className="h-3 w-3" /> We'll capture your GPS location on punch.
                </p>
              )}
            </div>

            {/* Face verification, and whether it is available to this person.
                Offered as the first way to punch rather than an extra button:
                a punch nobody can tie to a face is worth less afterwards. */}
            <FaceStatusPanel
              userId={user?.id}
              punchedIn={punchedIn}
              punchedOut={punchedOut}
              onPunched={() => {
                qc.invalidateQueries({ queryKey: ["attendance"] });
                qc.invalidateQueries({ queryKey: ["attendance-insights"] });
              }}
            />

            {/* Punching without a verified face is deliberately not offered. The
                server refuses it too, so this is the rule and not a preference. */}
            {punchedOut && (
              <div className="rounded-lg bg-success/10 p-3 text-center text-sm font-medium text-success">
                Day complete — see you tomorrow.
              </div>
            )}
          </CardContent>
        </Card>

        {/* What the punches add up to, computed rather than guessed */}
        <div className="lg:col-span-2">
          <AttendanceInsights />
        </div>

        {/* Month summary — an employee's own counts, which is exactly who needs them */}
        <Card className="lg:col-span-2">
            <CardHeader>
              <CardTitle>{periodStart.format("MMMM YYYY")} summary</CardTitle>
            </CardHeader>
            <CardContent>
              {summary.isLoading ? (
                <Skeleton className="h-20" />
              ) : summary.data ? (
                <div className="grid grid-cols-2 gap-4 sm:grid-cols-5">
                  {[
                    {
                      label: "Present",
                      value: summary.data.presentDays,
                      note: `of ${summary.data.workingDays} working days`,
                      tone: "text-success"
                    },
                    { label: "WFH", value: summary.data.wfhDays, tone: "text-primary" },
                    {
                      label: "Late",
                      value: summary.data.lateDays,
                      note: summary.data.totalLateMinutes > 0
                        ? `${minutesToHours(summary.data.totalLateMinutes)} late in total`
                        : "on time every day",
                      tone: "text-accent-foreground"
                    },
                    {
                      label: "Absent",
                      value: summary.data.absentDays,
                      note: "working days missed",
                      tone: "text-destructive"
                    },
                    {
                      label: "Overtime",
                      value: minutesToHours(summary.data.totalOvertimeMinutes),
                      note: "worked past 6 PM",
                      tone: "text-foreground"
                    }
                  ].map((s) => (
                    <div key={s.label} className="rounded-lg border p-3 text-center">
                      <div className={`font-display text-2xl font-bold ${s.tone}`}>{s.value}</div>
                      <div className="text-xs text-muted-foreground">{s.label}</div>
                      {"note" in s && s.note && (
                        <div className="mt-0.5 text-[10px] leading-tight text-muted-foreground">{s.note}</div>
                      )}
                    </div>
                  ))}
                </div>
              ) : null}
          </CardContent>
        </Card>
      </div>

      {/* History */}
      <Card className="mt-6">
        <CardHeader className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <CardTitle>
            {exactDay ? dayjs(exactDay).format("dddd, DD MMM YYYY") : periodStart.format("MMMM YYYY")}
          </CardTitle>
          <div className="flex flex-wrap items-end gap-3">
            <div className="space-y-1">
              <label className="text-[10px] font-semibold uppercase text-muted-foreground">Month</label>
              <Input
                type="month"
                className="h-9 w-[10.5rem]"
                max={dayjs().format("YYYY-MM")}
                value={period}
                onChange={(e) => { if (e.target.value) { setPeriod(e.target.value); setExactDay(""); } }}
              />
            </div>
            <div className="space-y-1">
              <label className="text-[10px] font-semibold uppercase text-muted-foreground">Exact date</label>
              <Input
                type="date"
                className="h-9 w-[10.5rem]"
                max={dayjs().format("YYYY-MM-DD")}
                value={exactDay}
                onChange={(e) => {
                  setExactDay(e.target.value);
                  if (e.target.value) setPeriod(dayjs(e.target.value).format("YYYY-MM"));
                }}
              />
            </div>
            {(exactDay || period !== dayjs().format("YYYY-MM")) && (
              <Button variant="ghost" size="sm" className="h-9"
                onClick={() => { setPeriod(dayjs().format("YYYY-MM")); setExactDay(""); }}>
                This month
              </Button>
            )}
            <Button variant="outline" size="sm" className="h-9"
              disabled={historyRows.length === 0} onClick={exportMonth}>
              <Download className="h-4 w-4" /> Export Excel
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          {history.isLoading ? (
            <Skeleton className="h-40" />
          ) : (history.data?.length ?? 0) === 0 ? (
            <EmptyState
              icon={Calendar}
              title="No attendance yet"
              description="Your punches for this month will appear here."
            />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Date</TableHead>
                  <TableHead>Employee ID</TableHead>
                  <TableHead>In</TableHead>
                  <TableHead>Out</TableHead>
                  <TableHead>Mode</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Details</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {historyPaged.pageRows.map((r) => (
                    <TableRow key={r.id}>
                      <TableCell className="font-medium">
                        {dayjs(r.workDate).format("ddd, DD MMM")}
                      </TableCell>
                      <TableCell className="code-chip text-xs">{user?.employeeCode ?? "—"}</TableCell>
                      <TableCell>{r.punchInAt ? dayjs(r.punchInAt).format("h:mm A") : "—"}</TableCell>
                      <TableCell>{r.punchOutAt ? dayjs(r.punchOutAt).format("h:mm A") : "—"}</TableCell>
                      <TableCell>
                        <span className="text-xs text-muted-foreground">{r.mode}</span>
                      </TableCell>
                      <TableCell>
                        <div className="flex items-center gap-1.5">
                          <Badge variant={statusVariant(r.status)}>{r.status}</Badge>
                          {r.late && <Badge variant="destructive">Late</Badge>}
                        </div>
                      </TableCell>
                      <TableCell className="text-right">
                        <Button variant="ghost" size="sm" onClick={() => setDetail(r)}>
                          <Eye className="h-4 w-4" /> View
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
              </TableBody>
            </Table>
          )}
          {(history.data?.length ?? 0) > 0 && (
            <>
              <div className="border-t px-4 py-2 text-xs text-muted-foreground">
                Showing {historyPaged.pageRows.length} of {historyRows.length} day{historyRows.length === 1 ? "" : "s"}
              </div>
              <TablePagination
                page={historyPaged.page}
                totalPages={historyPaged.totalPages}
                onChange={historyPaged.setPage}
                pageSize={historyPaged.pageSize}
                onPageSizeChange={historyPaged.setPageSize}
                total={historyPaged.total}
                always
              />
            </>
          )}
        </CardContent>
      </Card>

      {detail && <DayDetail record={detail} code={user?.employeeCode} onClose={() => setDetail(null)} />}
    </div>
  );
}

/** Everything recorded for one day, including what the table has no room for. */
function DayDetail({ record, code, onClose }: {
  record: AttendanceRecord; code?: string; onClose: () => void;
}) {
  const rows: [string, string][] = [
    ["Employee ID", code || "—"],
    ["Date", dayjs(record.workDate).format("dddd, DD MMM YYYY")],
    ["Status", record.late ? `${record.status} (late)` : record.status],
    ["Mode", record.mode],
    ["Punch in", record.punchInAt ? dayjs(record.punchInAt).format("h:mm A") : "—"],
    ["Punch out", record.punchOutAt ? dayjs(record.punchOutAt).format("h:mm A") : "—"],
    ["Late by", record.lateMinutes ? minutesToHours(record.lateMinutes) : "—"],
    ["Hours worked", record.workedMinutes ? minutesToHours(record.workedMinutes) : "—"],
    ["Overtime", record.overtimeMinutes ? minutesToHours(record.overtimeMinutes) : "—"],
    ["Punch-in location", gpsText(record.inLatitude, record.inLongitude)],
    ["Punch-out location", gpsText(record.outLatitude, record.outLongitude)],
    ["At office site", record.withinGeofence === undefined
      ? "—" : record.withinGeofence ? "Yes" : "No — outside the geofence"]
  ];

  return (
    <Dialog open onClose={onClose} className="max-w-md">
      <DialogHeader
        title={dayjs(record.workDate).format("DD MMM YYYY")}
        description="Everything recorded for this day."
      />
      <dl className="divide-y text-sm">
        {rows.map(([label, value]) => (
          <div key={label} className="flex items-start justify-between gap-4 py-2">
            <dt className="text-muted-foreground">{label}</dt>
            <dd className="text-right font-medium">{value}</dd>
          </div>
        ))}
      </dl>
      <div className="flex justify-end pt-3">
        <Button variant="outline" onClick={onClose}>Close</Button>
      </div>
    </Dialog>
  );
}

function gpsText(lat?: number, lng?: number) {
  return lat != null && lng != null ? `${lat.toFixed(5)}, ${lng.toFixed(5)}` : "—";
}
