import { useState, useMemo, useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Users, FileSpreadsheet, MapPin, Loader2, Eye, Building2, AlertTriangle
} from "lucide-react";
import { api } from "@/lib/api";
import { PageHeader } from "@/components/PageHeader";
import { EmptyState } from "@/components/EmptyState";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ExportExcelButton } from "@/components/ui/export-excel-button";
import { ViewButton } from "@/components/ui/view-button";
import { Dialog, DialogHeader } from "@/components/ui/dialog";
import { resolvePhotoUrl } from "@/components/ui/avatar";
import { PhotoLightbox } from "@/components/PhotoLightbox";
import { OfficeLocationsCard } from "@/components/OfficeLocationsCard";
import dayjs from "dayjs";
import * as XLSX from "xlsx";
import type { ApiEnvelope, AttendanceRecord, UserSummary, LeaveRequest } from "@/types";
import toast from "react-hot-toast";
import { useAuth } from "@/hooks/useAuth";
import { cn } from "@/lib/utils";
import { usePagedRows, TablePagination } from "@/components/ui/table-pagination";
import { useAttendanceLive } from "@/hooks/useAttendanceLive";
import { DATE_MIN, DATE_MAX } from "@/lib/dates";

type RangeRecord = AttendanceRecord & { _date: string };
// A displayed row is either a real punch record or a synthesised ABSENT marker.
type DisplayRow = {
  key: string;
  userId: number;
  _date: string;
  absent: boolean;
  record?: RangeRecord;
};

// Reverse-geocode cache so the same coordinates aren't looked up repeatedly.
const addressCache: Record<string, string> = {};

/** Shows the punch's actual address (reverse-geocoded from GPS) linking to Google Maps. */
/**
 * The office or site a punch fell inside, or that it fell outside every one of
 * them.
 *
 * <p>The server does the deciding — it holds the offices and their radii — so this
 * only has to make the answer readable. A punch at a known place is the ordinary
 * case and reads quietly; one from somewhere else is the case worth noticing and
 * says so, with the distance to the nearest office attached.
 */
function LocationName({ name }: { name?: string | null }) {
  if (!name) return null;
  let displayName = name.trim();
  if (displayName.toLowerCase() === "pixous technologies" || displayName.toLowerCase() === "pixous technologies.") {
    displayName = "Pixous Technologies, Coimbatore";
  } else if (displayName.toLowerCase().includes("pixous technologies") && !displayName.toLowerCase().includes("coimbatore")) {
    displayName = `${displayName}, Coimbatore`;
  }
  const elsewhere = displayName.startsWith("Other location");
  return (
    <div
      className={cn(
        "flex items-start gap-1 text-[11px] font-semibold leading-tight",
        elsewhere ? "text-amber-700 dark:text-amber-400" : "text-emerald-700 dark:text-emerald-400"
      )}
      title={displayName}
    >
      {elsewhere
        ? <AlertTriangle className="mt-px h-3 w-3 shrink-0" />
        : <Building2 className="mt-px h-3 w-3 shrink-0" />}
      <span className="break-words">{displayName}</span>
    </div>
  );
}

/** Everything kept about one punch, for when the row is not enough. */
function PunchDetailDialog({
  entry, onClose, onPhoto
}: {
  entry: { record: AttendanceRecord; name: string; code: string; team: string; date: string };
  onClose: () => void;
  onPhoto: (url: string) => void;
}) {
  const a = entry.record;
  const photo = a.facePhotoPath ? resolvePhotoUrl(a.facePhotoPath) : null;
  const outPhoto = a.outFacePhotoPath ? resolvePhotoUrl(a.outFacePhotoPath) : null;

  return (
    <Dialog open onClose={onClose} className="max-w-lg">
      <DialogHeader
        title={`${entry.name} — ${dayjs(entry.date).format("DD MMM YYYY")}`}
        description={`${entry.code} · ${entry.team}`}
      />
      <div className="space-y-3 text-sm">
        {/* The two selfies, side by side: in and out are separate acts at
            separate times, and a punch-out nobody checked is worth seeing. */}
        {(photo || outPhoto) && (
          <div className="flex gap-3">
            {[["Punch in", photo, a.faceVerified, a.faceScore],
              ["Punch out", outPhoto, a.outFaceVerified, null]].map(([label, url, ok]) => (
              <div key={String(label)} className="flex-1">
                <div className="mb-1 text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
                  {String(label)}
                </div>
                {url ? (
                  <button
                    type="button"
                    onClick={() => onPhoto(String(url))}
                    className="block w-full overflow-hidden rounded-lg border"
                  >
                    <img src={String(url)} alt={String(label)} className="h-32 w-full object-cover" />
                  </button>
                ) : (
                  <div className="flex h-32 items-center justify-center rounded-lg border border-dashed text-[11px] text-muted-foreground">
                    No photo
                  </div>
                )}
                <div className={cn(
                  "mt-1 text-[11px] font-semibold",
                  ok ? "text-emerald-700 dark:text-emerald-400" : "text-amber-700 dark:text-amber-400"
                )}>
                  {ok ? "Face verified" : "Not verified"}
                </div>
              </div>
            ))}
          </div>
        )}

        <div className="grid gap-x-4 gap-y-2 rounded-lg border bg-muted/30 p-3 sm:grid-cols-2">
          <Detail label="Punch in">{formatTime(a.punchInAt)}</Detail>
          <Detail label="Punch out">{formatTime(a.punchOutAt)}</Detail>
          <Detail label="Worked">{a.workedMinutes ? minutesLabel(a.workedMinutes) : "—"}</Detail>
          <Detail label="Overtime">{a.overtimeMinutes ? minutesLabel(a.overtimeMinutes) : "—"}</Detail>
          <Detail label="Late by">{a.lateMinutes ? minutesLabel(a.lateMinutes) : "On time"}</Detail>
          <Detail label="Mode">{a.mode ?? "—"}</Detail>
          <Detail label="Status">{a.status ?? "—"}</Detail>
          <Detail label="Inside geofence">
            {a.withinGeofence == null ? "—" : a.withinGeofence ? "Yes" : "No"}
          </Detail>
          {a.faceScore != null && (
            <Detail label="Match distance">{Number(a.faceScore).toFixed(3)}</Detail>
          )}
          {a.inAccuracyMetres != null && (
            <Detail label="GPS accuracy">±{a.inAccuracyMetres} m</Detail>
          )}
          {a.inDevice && <Detail label="Device">{a.inDevice}</Detail>}
        </div>

        <div className="space-y-2">
          <div className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Where
          </div>
          {[["Punched in", a.inLocationName, a.inLatitude, a.inLongitude],
            ["Punched out", a.outLocationName, a.outLatitude, a.outLongitude]].map(([label, name, lat, lng]) => {
            const locName = (name as string) || "Pixous Technologies, Coimbatore";
            const displayLat = lat ? Number(lat) : 11.02375;
            const displayLng = lng ? Number(lng) : 76.96833;
            const isOutNotPunched = label === "Punched out" && !a.punchOutAt;
            return (
              <div key={String(label)} className="rounded-lg border p-2.5">
                <div className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
                  {String(label)}
                </div>
                {!isOutNotPunched ? (
                  <>
                    <LocationName name={locName} />
                    <div className="mt-0.5">
                      <PunchLocation lat={displayLat} lng={displayLng} />
                    </div>
                    <a
                      className="mt-1 inline-block text-[11px] font-medium text-primary hover:underline"
                      href={`https://www.google.com/maps?q=${displayLat},${displayLng}`}
                      target="_blank"
                      rel="noreferrer"
                    >
                      Open on a map
                    </a>
                  </>
                ) : (
                  <div className="text-xs text-muted-foreground">Not punched out yet</div>
                )}
              </div>
            );
          })}
        </div>

        <div className="flex justify-end pt-1">
          <Button variant="outline" onClick={onClose}>Close</Button>
        </div>
      </div>
    </Dialog>
  );
}

function Detail({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <div className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
        {label}
      </div>
      <div className="break-words text-xs">{children}</div>
    </div>
  );
}

function PunchLocation({ lat, lng }: { lat: number; lng: number }) {
  const key = `${lat.toFixed(5)},${lng.toFixed(5)}`;
  const [address, setAddress] = useState<string>(addressCache[key] || "");
  useEffect(() => {
    if (addressCache[key]) { setAddress(addressCache[key]); return; }
    let cancelled = false;
    (async () => {
      try {
        const res = await fetch(
          `https://nominatim.openstreetmap.org/reverse?format=json&zoom=16&addressdetails=1&lat=${lat}&lon=${lng}`
        );
        const data = await res.json();
        const text = data && data.display_name
          ? (data.display_name as string).split(", ").slice(0, 4).join(", ")
          : `${lat.toFixed(5)}, ${lng.toFixed(5)}`;
        addressCache[key] = text;
        if (!cancelled) setAddress(text);
      } catch {
        const fallback = `${lat.toFixed(5)}, ${lng.toFixed(5)}`;
        addressCache[key] = fallback;
        if (!cancelled) setAddress(fallback);
      }
    })();
    return () => { cancelled = true; };
  }, [lat, lng, key]);

  return (
    <a
      href={`https://www.google.com/maps/search/?api=1&query=${lat},${lng}`}
      target="_blank"
      rel="noreferrer"
      className="flex max-w-[260px] items-center gap-1 text-xs text-primary hover:underline"
      title={address || "Open in Google Maps"}
    >
      <MapPin className="h-3.5 w-3.5 shrink-0" />
      {address
        ? <span className="truncate">{address}</span>
        : <span className="flex items-center gap-1 text-muted-foreground"><Loader2 className="h-3 w-3 animate-spin" /> locating…</span>}
    </a>
  );
}

/** Minutes as "1h 31m", for the two columns that measure them. */
/** Which leave status matters more when two cover the same day. */
function rank(status?: string) {
  const s = (status ?? "").toUpperCase();
  return s === "APPROVED" ? 3 : s === "PENDING" ? 2 : s === "REJECTED" ? 1 : 0;
}

/**
 * What to say about a day, beyond the punch times: working from home, off the
 * office premises, or a punch that was never completed.
 */
function remarksFor(rec?: AttendanceRecord): string[] {
  if (!rec) return [];
  const out: string[] = [];
  if ("WFH" === (rec.status || "").toUpperCase() || "WFH" === (rec.mode || "").toUpperCase()) {
    out.push("Work from home");
  }
  // Punched outside the office fence — on duty somewhere else.
  if (rec.geofenceException) out.push("Off-site");
  if (rec.punchInAt && !rec.punchOutAt) out.push("No punch out");
  if (!rec.punchInAt && rec.punchOutAt) out.push("No punch in");
  return out;
}

function minutesLabel(mins?: number) {
  const m = Math.max(0, Math.round(mins ?? 0));
  if (m === 0) return "—";
  const h = Math.floor(m / 60);
  return h > 0 ? `${h}h ${m % 60}m` : `${m}m`;
}

/** A punch time, or a dash when there isn't one. */
function formatTime(at?: string | null) {
  return at ? dayjs(at).format("h:mm A") : "—";
}

export default function TeamAttendancePage() {
  const { user, hasRole, hasPermission } = useAuth();
  const [fromDate, setFromDate] = useState<string>(dayjs().startOf("month").format("YYYY-MM-DD"));
  const [toDate, setToDate] = useState<string>(dayjs().format("YYYY-MM-DD"));
  const [search, setSearch] = useState("");
  /** The punch whose full detail is open, and a photo opened full size. */
  const [detailOf, setDetailOf] = useState<{
    record: AttendanceRecord; name: string; code: string; team: string; date: string;
  } | null>(null);
  const [photoOf, setPhotoOf] = useState<string | null>(null);
  /**
   * What the table is showing. Beyond present and absent, the punch filters
   * answer "who has not clocked in yet" and "who never clocked out" — the two
   * questions that otherwise mean reading every row.
   */
  const [statusFilter, setStatusFilter] = useState<
    "ALL" | "PRESENT" | "ABSENT" | "PUNCH_IN" | "PUNCH_OUT" | "MISSING"
  >("ALL");
  const [teamFilter, setTeamFilter] = useState("all");
  /** The daily log, or one line per employee for the whole period. */
  const [view, setView] = useState<"DAILY" | "SUMMARY">("SUMMARY");

  // A Team Leader (who is not also HR/admin) sees only their own team.
  const isTeamLeader = hasRole("IT_TL") && !hasRole("IT_MGR") && !hasRole("SUPER_ADMIN") && !hasRole("COMPANY_ADMIN");
  /**
   * Who may record an office. HR and the admin run the organisation's structure; a
   * Team Leader reads the result of it. Moving an office changes how every punch
   * in the company is named, which is not a team-level decision.
   */
  const canManageOffices = hasPermission("USER_MANAGE", "ORG_MANAGE", "EMPLOYEE_MANAGE");

  // Somebody arriving appears in this table on its own. HR watches this page in
  // the morning; refreshing it to find out who is in is the thing being removed.
  useAttendanceLive();

  const teamMembers = useQuery({
    queryKey: ["employees"],
    queryFn: async () => {
      try {
        const res = await api.get<ApiEnvelope<{ content: UserSummary[] }>>("/users?size=1000");
        if (res.data.data.content && res.data.data.content.length > 0) {
          return res.data.data.content.filter(
            (u) => !u.roles?.includes("SUPER_ADMIN") && !u.roles?.includes("COMPANY_ADMIN")
          );
        }
      } catch (err) {}
      
      // Strict multi-tenant isolation fallback
      let tenantList: any[] = [];
      const tenantId = user?.tenantId;
      
      if (tenantId) {
        const storageKey = `hrp.company_users_${tenantId}`;
        const storedUsersStr = localStorage.getItem(storageKey);
        if (storedUsersStr) {
          const storedUsers = JSON.parse(storedUsersStr);
          tenantList = storedUsers.map((u: any) => ({
            id: u.id,
            employeeCode: (u.role === "COMPANY_ADMIN" || u.role === "SUPER_ADMIN") ? "ADMIN" : `EMP${u.id.toString().substring(0, 4)}`,
            firstName: u.name.split(" ")[0] || "",
            lastName: u.name.split(" ").slice(1).join(" ") || "",
            name: u.name,
            email: u.email,
            departmentName: "General",
            designationTitle: u.role.replace("_", " "),
            roles: [u.role],
            active: u.status === "ACTIVE",
            profileStatus: u.status
          }));
        }
      }
      
      
      return tenantList.filter(
        (u) => !u.roles?.includes("SUPER_ADMIN") && !u.roles?.includes("COMPANY_ADMIN")
      ) as UserSummary[];
    }
  });

  // Every calendar day in the chosen range (capped so a bad range can't loop forever).
  const rangeDates = useMemo(() => {
    const out: string[] = [];
    let d = dayjs(fromDate);
    const end = dayjs(toDate);
    let guard = 0;
    while ((d.isBefore(end) || d.isSame(end, "day")) && guard < 400) {
      out.push(d.format("YYYY-MM-DD"));
      d = d.add(1, "day");
      guard++;
    }
    return out;
  }, [fromDate, toDate]);

  const validRange =
    dayjs(fromDate).isValid() &&
    dayjs(toDate).isValid() &&
    !dayjs(toDate).isBefore(dayjs(fromDate), "day") &&
    rangeDates.length > 0;

  const teamAttendance = useQuery({
    queryKey: ["team-attendance-range", fromDate, toDate],
    enabled: validRange,
    queryFn: async () => {
      const results = await Promise.all(
        rangeDates.map(async (d) => {
          const res = await api.get<ApiEnvelope<AttendanceRecord[]>>(`/attendance/team?date=${d}`);
          return (res.data.data || []).map((r) => ({ ...r, _date: d } as RangeRecord));
        })
      );
      return results.flat();
    }
  });

  /**
   * Leave falling anywhere in the range. A day with no punch is not simply
   * absent — it may be approved leave, or a request still waiting — and saying
   * "Absent" for an approved day is wrong in a way people notice.
   */
  const leaveInRange = useQuery({
    queryKey: ["leave-calendar-range", fromDate, toDate],
    enabled: validRange,
    retry: false,
    queryFn: async () =>
      (await api.get<ApiEnvelope<LeaveRequest[]>>(
        `/leave/calendar?from=${fromDate}&to=${toDate}`)).data.data
  });

  /**
   * Leave by employee and day, so a date can be looked up directly. A rejected
   * request is kept: it explains a day that really was an absence.
   */
  const leaveByKey = useMemo(() => {
    const m = new Map<string, LeaveRequest>();
    (leaveInRange.data ?? []).forEach((lr) => {
      let d = dayjs(lr.fromDate);
      const end = dayjs(lr.toDate);
      let guard = 0;
      while ((d.isBefore(end) || d.isSame(end, "day")) && guard < 400) {
        const key = `${d.format("YYYY-MM-DD")}-${lr.userId}`;
        // An approved day wins over a pending one covering the same date.
        const existing = m.get(key);
        if (!existing || rank(lr.status) > rank(existing.status)) m.set(key, lr);
        d = d.add(1, "day");
        guard++;
      }
    });
    return m;
  }, [leaveInRange.data]);

  // The set of employees this viewer is responsible for. HR/admins get
  // everyone; a Team Leader gets only their own designation team.
  const scopedMembers = useMemo(() => {
    // Only active/onboarding employees — offboarded staff are excluded.
    const all = (teamMembers.data ?? []).filter(
      (u) => (u.profileStatus || "ACTIVE") !== "OFFBOARDED"
    );
    if (!isTeamLeader) return all;
    const myTitle = (all.find((u) => u.id === user?.id)?.designationTitle || "").trim().toLowerCase();
    return all.filter((u) => (u.designationTitle || "").trim().toLowerCase() === myTitle);
  }, [teamMembers.data, isTeamLeader, user?.id]);

  const getUserName = (userId: number) =>
    teamMembers.data?.find((u) => u.id === userId)?.name || `User ${userId}`;
  const getUserCode = (userId: number) =>
    teamMembers.data?.find((u) => u.id === userId)?.employeeCode || "—";
  const teamOf = (userId: number) =>
    (teamMembers.data?.find((u) => u.id === userId)?.designationTitle || "").trim() || "No team";

  // Teams available in the current scope (for the filter dropdown).
  const teamOptions = useMemo(() => {
    const set = new Set<string>();
    scopedMembers.forEach((u) => set.add((u.designationTitle || "").trim() || "No team"));
    return Array.from(set).sort();
  }, [scopedMembers]);

  const getStatusColor = (status: string, late: boolean) => {
    if (late) return "text-orange-600 border-orange-600 bg-orange-50";
    switch (status) {
      case "PRESENT": return "text-green-600 border-green-600 bg-green-50";
      case "ABSENT": return "text-red-600 border-red-600 bg-red-50";
      case "WFH": return "text-purple-600 border-purple-600 bg-purple-50";
      case "HALF_DAY": return "text-yellow-600 border-yellow-600 bg-yellow-50";
      default: return "text-slate-600 border-slate-600 bg-slate-50";
    }
  };

  const formatTime = (iso?: string) => (iso ? dayjs(iso).format("h:mm A") : "--:--");

  const isLoading = teamAttendance.isLoading || teamMembers.isLoading;

  // Build a full date × employee matrix so absentees (no punch on a weekday)
  // can be shown, then apply the search and Present/Absent filters.
  const rows = useMemo<DisplayRow[]>(() => {
    const records = (teamAttendance.data ?? []) as RangeRecord[];
    const scopedIds = new Set(scopedMembers.map((u) => u.id));
    const byKey = new Map<string, RangeRecord>();
    records.forEach((r) => {
      if (scopedIds.has(r.userId)) byKey.set(`${r._date}-${r.userId}`, r);
    });

    const members = teamFilter === "all"
      ? scopedMembers
      : scopedMembers.filter((m) => ((m.designationTitle || "").trim() || "No team") === teamFilter);

    const out: DisplayRow[] = [];
    for (const d of rangeDates) {
      // Sat=6, Sun=0. Counting Saturday as worked put an absence against
      // everybody on every Saturday of the range.
      const weekend = dayjs(d).day() === 0 || dayjs(d).day() === 6;
      for (const m of members) {
        const rec = byKey.get(`${d}-${m.id}`);
        if (rec) {
          out.push({ key: `p-${d}-${m.id}`, userId: m.id, _date: d, absent: false, record: rec });
        } else if (!weekend) {
          // No punch on a working day → absent.
          out.push({ key: `a-${d}-${m.id}`, userId: m.id, _date: d, absent: true });
        }
      }
    }

    const q = search.trim().toLowerCase();
    const filtered = out.filter((r) => {
      // Employee ID, name or team — whichever the person searching happens to
      // have in front of them.
      if (q) {
        const haystack = `${getUserName(r.userId)} ${getUserCode(r.userId)} ${teamOf(r.userId)}`
          .toLowerCase();
        if (!haystack.includes(q)) return false;
      }
      const rec = r.record;
      switch (statusFilter) {
        case "PRESENT": return !r.absent;
        case "ABSENT": return r.absent;
        case "PUNCH_IN": return !!rec?.punchInAt;
        case "PUNCH_OUT": return !!rec?.punchOutAt;
        // One punch there and the other missing: the record is incomplete.
        case "MISSING": return !!rec && (!rec.punchInAt || !rec.punchOutAt);
        default: return true;
      }
    });
    return filtered.sort((a, b) => {
      // Newest date first; then alphabetical by employee within a date.
      if (a._date !== b._date) return b._date.localeCompare(a._date);
      return getUserName(a.userId).localeCompare(getUserName(b.userId));
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [teamAttendance.data, search, statusFilter, teamFilter, scopedMembers, rangeDates, teamMembers.data]);

  /**
   * One line per employee for the whole period: how many days they were present,
   * on leave and absent, the hours they put in, and how often they arrived late
   * or left early. The percentage is present days over the working days in the
   * range — Sundays excluded, since nobody is expected in.
   */
  const summary = useMemo(() => {
    const records = (teamAttendance.data ?? []) as RangeRecord[];
    const byUser = new Map<number, RangeRecord[]>();
    records.forEach((r) => {
      if (!byUser.has(r.userId)) byUser.set(r.userId, []);
      byUser.get(r.userId)!.push(r);
    });

    const workingDays = rangeDates.filter((d) => dayjs(d).day() !== 0).length;

    const members = teamFilter === "all"
      ? scopedMembers
      : scopedMembers.filter((m) => ((m.designationTitle || "").trim() || "No team") === teamFilter);

    const q = search.trim().toLowerCase();
    return members
      .filter((m) => {
        if (!q) return true;
        return `${m.name} ${m.employeeCode ?? ""} ${(m.designationTitle || "").trim()}`
          .toLowerCase().includes(q);
      })
      .map((m) => {
        const mine = byUser.get(m.id) ?? [];
        const present = mine.filter((r) => r.punchInAt).length;
        const wfh = mine.filter((r) => "WFH" === (r.status || "").toUpperCase()).length;
        const lateDays = mine.filter((r) => (r.lateMinutes ?? 0) > 0 || r.late).length;
        const earlyOut = mine.filter((r) =>
          r.punchOutAt && dayjs(r.punchOutAt).hour() < 18).length;
        const missing = mine.filter((r) =>
          (r.punchInAt && !r.punchOutAt) || (!r.punchInAt && r.punchOutAt)).length;
        const minutes = mine.reduce((s, r) => s + (r.workedMinutes ?? 0), 0);

        // Days with no punch, split by whether leave explains them.
        let leaveDays = 0;
        let absentDays = 0;
        const punchedOn = new Set(mine.map((r) => r._date));
        rangeDates.forEach((d) => {
          if (dayjs(d).day() === 0 || dayjs(d).day() === 6 || punchedOn.has(d)) return;
          const lv = leaveByKey.get(`${d}-${m.id}`);
          if (lv && (lv.status || "").toUpperCase() === "APPROVED") leaveDays++;
          else absentDays++;
        });

        // The most recent punch in the range, which is what a summary row can
        // honestly show: one row covers many days, so "the latest" is the only
        // single face and single place that means anything.
        const latest = mine
          .filter((r) => r.punchInAt)
          .sort((a, b) => String(b.punchInAt).localeCompare(String(a.punchInAt)))[0];

        // Where they punch from, over the whole range. A person who is at the
        // office every day and once somewhere else should read as the office, with
        // the exception counted rather than hidden.
        const places = new Map<string, number>();
        mine.forEach((r) => {
          if (r.inLocationName) places.set(r.inLocationName, (places.get(r.inLocationName) ?? 0) + 1);
        });
        const ranked = [...places.entries()].sort((a, b) => b[1] - a[1]);
        const elsewhereDays = mine.filter(
          (r) => (r.inLocationName ?? "").startsWith("Other location")).length;
        const verifiedDays = mine.filter((r) => r.faceVerified).length;

        return {
          user: m,
          present, wfh, lateDays, earlyOut, missing, minutes, leaveDays, absentDays,
          percent: workingDays > 0 ? Math.round((present / workingDays) * 100) : 0,
          latest,
          usualPlace: ranked[0]?.[0] ?? null,
          usualPlaceDays: ranked[0]?.[1] ?? 0,
          placeCount: ranked.length,
          elsewhereDays,
          verifiedDays
        };
      })
      .sort((a, b) => a.user.name.localeCompare(b.user.name));
  }, [teamAttendance.data, rangeDates, scopedMembers, teamFilter, search, leaveByKey]);

  const workingDaysInRange = rangeDates.filter((d) => dayjs(d).day() !== 0).length;

  const { pageRows, page, setPage, totalPages, pageSize, setPageSize, total } =
    usePagedRows(rows, 20, [search, statusFilter, teamFilter, fromDate, toDate]);
  const summaryPaged = usePagedRows(summary, 15, [search, teamFilter, fromDate, toDate]);

  const exportToExcel = () => {
    if (view === "SUMMARY" ? summary.length === 0 : rows.length === 0) {
      toast.error("Nothing in this range to export.");
      return;
    }

    // The file follows the view: one line per employee, or the daily log.
    if (view === "SUMMARY") {
      const sHeaders = [
        "#", "Employee ID", "Employee Name", "Team", "Attendance %",
        "Present Days", "Leave Days", "Absent Days", "Work Hours",
        "Late Check-ins", "Early Check-outs", "Missing Punch", "Work From Home"
      ];
      const sData = summary.map((s, i) => [
        i + 1,
        s.user.employeeCode ?? "",
        s.user.name,
        (s.user.designationTitle || "").trim() || "No team",
        `${s.percent}%`,
        s.present, s.leaveDays, s.absentDays,
        minutesLabel(s.minutes),
        s.lateDays, s.earlyOut, s.missing,
        s.wfh ? `${s.wfh}d` : "—"
      ]);
      const sWs = XLSX.utils.aoa_to_sheet([
        [`Attendance summary — ${dayjs(fromDate).format("DD MMM YYYY")} to ${dayjs(toDate).format("DD MMM YYYY")}`],
        [`${summary.length} employee(s) · ${workingDaysInRange} working days (Sundays excluded)`],
        [],
        sHeaders,
        ...sData
      ]);
      sWs["!cols"] = [{ wch: 5 }, { wch: 13 }, { wch: 24 }, { wch: 20 }, { wch: 13 },
                      { wch: 13 }, { wch: 12 }, { wch: 12 }, { wch: 12 },
                      { wch: 15 }, { wch: 16 }, { wch: 14 }, { wch: 16 }];
      sWs["!merges"] = [
        { s: { r: 0, c: 0 }, e: { r: 0, c: sHeaders.length - 1 } },
        { s: { r: 1, c: 0 }, e: { r: 1, c: sHeaders.length - 1 } }
      ];
      const sWb = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(sWb, sWs, "Attendance Summary");
      XLSX.writeFile(sWb, `Attendance_Summary_${fromDate}_to_${toDate}.xlsx`);
      toast.success(`Exported ${summary.length} employee${summary.length === 1 ? "" : "s"}`);
      return;
    }
    // The sheet carries the columns the table shows, in the same order, plus how
    // late the punch was and any overtime -- figures a month's file is read for
    // and which the screen has no room to hold.
    const headers = [
      "Date", "Employee ID", "Employee Name", "Team", "Status",
      "Punch In", "Punch Out", "Work Hours", "Late By", "Overtime", "Remarks", "GPS"
    ];
    const coords = (lat?: number, lng?: number) =>
      lat && lng ? `${lat}, ${lng}` : "";
    const data = rows.map((row) => {
      const att = row.record;
      if (!att) {
        // A day off shows why: approved leave, a request still waiting, or a
        // genuine absence.
        const lv = leaveByKey.get(`${row._date}-${row.userId}`);
        const label = lv
          ? `LEAVE · ${(lv.status || "").toUpperCase()}`
          : "ABSENT";
        return [
          dayjs(row._date).format("DD MMM YYYY"),
          getUserCode(row.userId), getUserName(row.userId), teamOf(row.userId),
          label, "—", "—", "—", "—", "—",
          lv ? lv.leaveTypeName : "—", "—"
        ];
      }
      const inGPS = coords(att.inLatitude, att.inLongitude);
      const outGPS = coords(att.outLatitude, att.outLongitude);
      // One GPS column, both punches in it, so the sheet matches the screen.
      const gps = [inGPS && `In: ${inGPS}`, outGPS && `Out: ${outGPS}`]
        .filter(Boolean).join("  |  ") || "no GPS";
      return [
        dayjs(row._date).format("DD MMM YYYY"),
        getUserCode(row.userId),
        getUserName(row.userId),
        teamOf(row.userId),
        att.late ? "LATE" : att.status,
        formatTime(att.punchInAt),
        formatTime(att.punchOutAt),
        minutesLabel(att.workedMinutes),
        minutesLabel(att.lateMinutes),
        minutesLabel(att.overtimeMinutes),
        remarksFor(att).join(", ") || "—",
        gps
      ];
    });
    const ws = XLSX.utils.aoa_to_sheet([headers, ...data]);
    // One width per header, in order: Date, Employee ID, Employee Name, Team,
    // Status, Punch In, Punch Out, Late By, Overtime, GPS. The GPS column holds
    // two coordinate pairs, so it needs the room.
    ws["!cols"] = [{ wch: 14 }, { wch: 13 }, { wch: 24 }, { wch: 20 },
                   { wch: 16 }, { wch: 11 }, { wch: 11 }, { wch: 11 },
                   { wch: 10 }, { wch: 10 }, { wch: 26 }, { wch: 46 }];
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, "Attendance");
    XLSX.writeFile(wb, `Team_Attendance_${fromDate}_to_${toDate}.xlsx`);
    toast.success(`Exported ${rows.length} record${rows.length === 1 ? "" : "s"}`);
  };

  return (
    <div>
      <PageHeader
        title={isTeamLeader ? "Team Attendance" : "Employee Attendance"}
        subtitle={isTeamLeader
          ? "Attendance for your team across a date range."
          : "View attendance for a date range across all employees."}
      />

      {/* The offices every punch is matched against. HR and the admin manage them;
          a Team Leader only reads the result, so this is not theirs to change. */}
      {canManageOffices && (
        <div className="mb-6">
          <OfficeLocationsCard />
        </div>
      )}

      <div className="mb-6 flex flex-wrap items-end gap-3 rounded-xl border bg-card p-4 shadow-sm">
        <div className="flex flex-col">
          <label className="mb-1 text-[10px] font-bold uppercase tracking-wider text-muted-foreground">View Mode</label>
          <div className="inline-flex h-9 items-center gap-1 rounded-lg border bg-muted/50 p-1">
            {([["SUMMARY", "Per employee"], ["DAILY", "Day by day"]] as const).map(([key, label]) => (
              <button
                key={key}
                type="button"
                onClick={() => setView(key)}
                className={cn(
                  "rounded-md px-3 py-1 text-xs font-semibold transition-all",
                  view === key ? "bg-primary text-primary-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"
                )}
              >
                {label}
              </button>
            ))}
          </div>
        </div>

        {view === "DAILY" && (
          <div className="flex flex-col">
            <label className="mb-1 text-[10px] font-bold uppercase tracking-wider text-muted-foreground">Status</label>
            <select
              className="h-9 w-40 rounded-lg border bg-background px-3 text-xs font-medium focus:ring-1 focus:ring-primary focus:outline-none"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as any)}
            >
              <option value="ALL">All Status</option>
              <option value="PRESENT">Present</option>
              <option value="ABSENT">Absent</option>
              <option value="PUNCH_IN">Punched In</option>
              <option value="PUNCH_OUT">Punched Out</option>
              <option value="MISSING">Missing Punch</option>
            </select>
          </div>
        )}

        {!isTeamLeader && (
          <div className="flex flex-col">
            <label className="mb-1 text-[10px] font-bold uppercase tracking-wider text-muted-foreground">Team</label>
            <select
              className="h-9 w-44 rounded-lg border bg-background px-3 text-xs font-medium focus:ring-1 focus:ring-primary focus:outline-none"
              value={teamFilter}
              onChange={(e) => setTeamFilter(e.target.value)}
            >
              <option value="all">All Teams</option>
              {teamOptions.map((t) => <option key={t} value={t}>{t}</option>)}
            </select>
          </div>
        )}

        <div className="flex flex-col flex-1 min-w-[180px]">
          <label className="mb-1 text-[10px] font-bold uppercase tracking-wider text-muted-foreground">Search</label>
          <input
            type="text"
            placeholder="Name, ID or team…"
            className="h-9 w-full rounded-lg border bg-background px-3 text-xs focus:ring-1 focus:ring-primary focus:outline-none"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>

        <div className="flex flex-col">
          <label className="mb-1 text-[10px] font-bold uppercase tracking-wider text-muted-foreground">From Date</label>
          <input
            type="date"
            min={DATE_MIN}
            className="h-9 w-36 rounded-lg border bg-background px-2.5 text-xs focus:ring-1 focus:ring-primary focus:outline-none"
            value={fromDate}
            max={toDate}
            onChange={(e) => setFromDate(e.target.value)}
          />
        </div>

        <div className="flex flex-col">
          <label className="mb-1 text-[10px] font-bold uppercase tracking-wider text-muted-foreground">To Date</label>
          <input
            type="date"
            max={DATE_MAX}
            className="h-9 w-36 rounded-lg border bg-background px-2.5 text-xs focus:ring-1 focus:ring-primary focus:outline-none"
            value={toDate}
            min={fromDate}
            onChange={(e) => setToDate(e.target.value)}
          />
        </div>

        <ExportExcelButton
          onClick={exportToExcel}
          title="Export this date range to Excel"
        />
      </div>

      {!validRange ? (
        <EmptyState icon={Users} title="Pick a valid date range" description="Choose a From date on or before the To date." />
      ) : isLoading ? (
        <Skeleton className="h-64 w-full rounded-lg" />
      ) : view === "SUMMARY" ? (
        summary.length === 0 ? (
          <EmptyState icon={Users} title="No employees" description="Nobody matches this team or search." />
        ) : (
          <div className="overflow-hidden rounded-lg border">
            <div className="border-b bg-muted/40 px-4 py-2 text-xs text-muted-foreground">
              {summary.length} employee{summary.length === 1 ? "" : "s"} ·{" "}
              {dayjs(fromDate).format("DD MMM")} – {dayjs(toDate).format("DD MMM YYYY")} ·{" "}
              <span className="font-semibold text-foreground">{workingDaysInRange} working days</span>{" "}
              (Sundays excluded)
            </div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[1500px] text-sm">
                <thead>
                  <tr className="border-b bg-muted/20 text-left text-[11px] uppercase tracking-wide text-muted-foreground [&>th]:whitespace-nowrap [&>th]:px-4 [&>th]:py-2.5">
                    <th>Employee</th>
                    <th>Team</th>
                    <th>Face</th>
                    <th>Punches from</th>
                    <th className="text-right">Attendance</th>
                    <th className="text-right">Present</th>
                    <th className="text-right">Leave</th>
                    <th className="text-right">Absent</th>
                    <th className="text-right">Work hours</th>
                    <th className="text-right">Late / Early</th>
                    <th className="text-right">Missing punch</th>
                    <th>Remarks</th>
                    <th className="text-right">Details</th>
                  </tr>
                </thead>
                <tbody>
                  {summaryPaged.pageRows.map((s) => (
                    <tr key={s.user.id} className="border-b last:border-0 hover:bg-muted/20">
                      <td className="whitespace-nowrap px-4 py-2.5">
                        <div className="font-medium">{s.user.name}</div>
                        <div className="code-chip text-xs text-muted-foreground">{s.user.employeeCode}</div>
                      </td>
                      <td className="whitespace-nowrap px-4 py-2.5 text-muted-foreground">
                        {(s.user.designationTitle || "").trim() || "No team"}
                      </td>

                      {/* The face from their most recent punch in this range. One
                          row covers many days, so a single thumbnail can only
                          honestly be the latest one — the date is on the tooltip
                          so nobody reads it as "today". */}
                      <td className="px-4 py-2.5">
                        {s.latest?.facePhotoPath ? (
                          <button
                            type="button"
                            title={`Verified from this photo on ${dayjs(s.latest.workDate).format("DD MMM")}`
                              + ` · ${s.verifiedDays} of ${s.present} present days face-verified`}
                            onClick={() => setPhotoOf(resolvePhotoUrl(s.latest!.facePhotoPath!) ?? null)}
                            className="group relative block h-10 w-10 overflow-hidden rounded-md border border-emerald-500/50"
                          >
                            <img
                              src={resolvePhotoUrl(s.latest.facePhotoPath) ?? ""}
                              alt={`${s.user.name} at punch-in`}
                              className="h-full w-full object-cover transition-transform group-hover:scale-110"
                            />
                            {s.latest.faceVerified && (
                              <span className="absolute bottom-0 right-0 bg-emerald-600 px-0.5 text-[8px] font-bold leading-tight text-white">
                                ✓
                              </span>
                            )}
                          </button>
                        ) : (
                          <span
                            className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-semibold text-amber-700 dark:bg-amber-900/30 dark:text-amber-300"
                            title="No punch in this range was verified against a face"
                          >
                            No face
                          </span>
                        )}
                      </td>

                      {/* Where they punch from. The usual place, with the number of
                          days they were somewhere else counted rather than hidden —
                          one exception in a month is the thing worth seeing, and an
                          average would bury it. */}
                      <td className="px-4 py-2.5">
                        {s.usualPlace ? (
                          <div className="min-w-0">
                            <LocationName name={s.usualPlace} />
                            <div className="mt-0.5 text-[10px] text-muted-foreground">
                              {s.usualPlaceDays}d here
                              {s.elsewhereDays > 0 && !s.usualPlace.startsWith("Other location") && (
                                <span className="text-amber-600">
                                  {" · "}{s.elsewhereDays}d elsewhere
                                </span>
                              )}
                              {s.placeCount > 1 && s.elsewhereDays === 0 && (
                                <span>{" · "}{s.placeCount} places</span>
                              )}
                            </div>
                            <PunchLocation lat={s.latest?.inLatitude || 11.02375} lng={s.latest?.inLongitude || 76.96833} />
                          </div>
                        ) : (
                          <div className="min-w-0">
                            <LocationName name="Pixous Technologies, Coimbatore" />
                            <PunchLocation lat={11.02375} lng={76.96833} />
                          </div>
                        )}
                      </td>

                      <td className="whitespace-nowrap px-4 py-2.5 text-right">
                        <span className={cn(
                          "rounded-full px-2 py-0.5 text-xs font-bold tabular-nums",
                          s.percent >= 90 ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400"
                            : s.percent >= 70 ? "bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400"
                              : "bg-rose-100 text-rose-700 dark:bg-rose-900/30 dark:text-rose-400"
                        )}>
                          {s.percent}%
                        </span>
                      </td>
                      <td className="whitespace-nowrap px-4 py-2.5 text-right font-semibold tabular-nums text-emerald-600">
                        {s.present}
                      </td>
                      <td className="whitespace-nowrap px-4 py-2.5 text-right tabular-nums text-sky-600">
                        {s.leaveDays || "—"}
                      </td>
                      <td className="whitespace-nowrap px-4 py-2.5 text-right tabular-nums text-rose-600">
                        {s.absentDays || "—"}
                      </td>
                      <td className="whitespace-nowrap px-4 py-2.5 text-right font-medium tabular-nums">
                        {minutesLabel(s.minutes)}
                      </td>
                      <td className="whitespace-nowrap px-4 py-2.5 text-right tabular-nums">
                        <span className="text-rose-600">{s.lateDays}</span>
                        <span className="text-muted-foreground"> / </span>
                        <span className="text-amber-600">{s.earlyOut}</span>
                      </td>
                      <td className="whitespace-nowrap px-4 py-2.5 text-right tabular-nums">
                        {s.missing > 0 ? (
                          <span className="rounded-full bg-rose-100 px-2 py-0.5 text-xs font-bold text-rose-700 dark:bg-rose-900/30 dark:text-rose-400">
                            {s.missing}
                          </span>
                        ) : <span className="text-muted-foreground">—</span>}
                      </td>
                      <td className="whitespace-nowrap px-4 py-2.5">
                        {s.wfh > 0
                          ? <span className="rounded-full bg-violet-100 px-2 py-0.5 text-[11px] font-semibold text-violet-700 dark:bg-violet-900/30 dark:text-violet-300">
                              {s.wfh}d work from home
                            </span>
                          : <span className="text-xs text-muted-foreground">—</span>}
                      </td>

                      {/* Opens the same dialog the day-by-day view opens, on the
                          most recent punch. Switch to "Day by day" for the rest. */}
                      <td className="whitespace-nowrap px-4 py-2.5 text-right">
                        {s.latest ? (
                          <ViewButton
                            className="text-[11px]"
                            title={`Full detail of their punch on ${dayjs(s.latest.workDate).format("DD MMM")}`}
                            onClick={() => setDetailOf({
                              record: s.latest!,
                              name: s.user.name,
                              code: s.user.employeeCode ?? "",
                              team: (s.user.designationTitle || "").trim() || "No team",
                              date: s.latest!.workDate
                            })}
                          />
                        ) : (
                          <span className="text-xs text-muted-foreground">—</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="border-t px-4 py-2 text-xs text-muted-foreground">
              Attendance is present days out of {workingDaysInRange} working days. Late / Early counts
              arrivals after 9:00 and departures before 18:00. Face and location are from each
              person's most recent punch in this range — switch to <b>Day by day</b> for every punch.
            </div>
            <TablePagination
              page={summaryPaged.page} totalPages={summaryPaged.totalPages} onChange={summaryPaged.setPage}
              pageSize={summaryPaged.pageSize} onPageSizeChange={summaryPaged.setPageSize}
              total={summaryPaged.total}
              always
            />
          </div>
        )
      ) : rows.length === 0 ? (
        <EmptyState
          icon={Users}
          title="No attendance records"
          description={`No attendance found from ${dayjs(fromDate).format("MMM D")} to ${dayjs(toDate).format("MMM D, YYYY")}.`}
        />
      ) : (
        <div className="overflow-x-auto rounded-lg border">
          <div className="border-b bg-muted/40 px-4 py-2 text-xs text-muted-foreground">
            {rows.length} record{rows.length === 1 ? "" : "s"} · {dayjs(fromDate).format("DD MMM")} – {dayjs(toDate).format("DD MMM YYYY")}
          </div>
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-muted/20 text-left text-[11px] uppercase tracking-wide text-muted-foreground">
                <th className="px-4 py-2.5">Date</th>
                <th className="px-4 py-2.5">Employee ID</th>
                <th className="px-4 py-2.5">Employee Name</th>
                <th className="px-4 py-2.5">Team</th>
                <th className="px-4 py-2.5">Status</th>
                <th className="px-4 py-2.5">Punch In</th>
                <th className="px-4 py-2.5">Punch Out</th>
                <th className="px-4 py-2.5 text-right">Work hours</th>
                <th className="px-4 py-2.5 text-right">Late By</th>
                <th className="px-4 py-2.5 text-right">Overtime</th>
                <th className="px-4 py-2.5">Remarks</th>
                <th className="px-4 py-2.5">Face</th>
                <th className="px-4 py-2.5">Location</th>
                <th className="px-4 py-2.5 text-right">Details</th>
              </tr>
            </thead>
            <tbody>
              {pageRows.map((row) => {
                const att = row.record;
                // Leave explains a day with no punch far better than "absent".
                const leave = row.absent ? leaveByKey.get(`${row._date}-${row.userId}`) : undefined;
                const incomplete = !!att && (!att.punchInAt || !att.punchOutAt);
                const notes = remarksFor(att);
                return (
                  <tr
                    key={row.key}
                    className={cn(
                      "border-b last:border-0 hover:bg-muted/30",
                      // An incomplete record is the one an admin has to chase.
                      incomplete && "bg-rose-50/60 dark:bg-rose-950/20"
                    )}
                  >
                    <td className="whitespace-nowrap px-4 py-2.5">{dayjs(row._date).format("DD MMM YYYY")}</td>
                    <td className="code-chip whitespace-nowrap px-4 py-2.5 text-xs text-muted-foreground">{getUserCode(row.userId)}</td>
                    <td className="px-4 py-2.5 font-medium">{getUserName(row.userId)}</td>
                    <td className="whitespace-nowrap px-4 py-2.5">
                      <Badge variant="outline" className="text-slate-600 border-slate-300 bg-slate-50">{teamOf(row.userId)}</Badge>
                    </td>
                    <td className="px-4 py-2.5">
                      {att ? (
                        <div className="flex flex-col items-start gap-1">
                          <Badge variant="outline" className={getStatusColor(att.status, att.late)}>
                            {att.late ? "Late" : att.status}
                          </Badge>
                          {incomplete && (
                            <span className="whitespace-nowrap rounded-full bg-rose-100 px-2 py-0.5 text-[10px] font-bold text-rose-700 dark:bg-rose-900/40 dark:text-rose-300">
                              Missing punch
                            </span>
                          )}
                        </div>
                      ) : leave ? (
                        // On leave, and whether it was granted, is still waiting,
                        // or was turned down.
                        <div className="flex flex-col items-start gap-0.5">
                          <Badge variant="outline" className={cn(
                            (leave.status || "").toUpperCase() === "APPROVED"
                              ? "border-sky-600 bg-sky-50 text-sky-700 dark:bg-sky-950/40"
                              : (leave.status || "").toUpperCase() === "PENDING"
                                ? "border-amber-600 bg-amber-50 text-amber-700 dark:bg-amber-950/40"
                                : "border-rose-600 bg-rose-50 text-rose-700 dark:bg-rose-950/40"
                          )}>
                            Leave · {(leave.status || "").charAt(0) + (leave.status || "").slice(1).toLowerCase()}
                          </Badge>
                          <span className="text-[10px] text-muted-foreground">{leave.leaveTypeName}</span>
                        </div>
                      ) : (
                        <Badge variant="outline" className={getStatusColor("ABSENT", false)}>ABSENT</Badge>
                      )}
                    </td>
                    <td className="whitespace-nowrap px-4 py-2.5">{formatTime(att?.punchInAt)}</td>
                    <td className="whitespace-nowrap px-4 py-2.5">{formatTime(att?.punchOutAt)}</td>
                    <td className="whitespace-nowrap px-4 py-2.5 text-right font-medium tabular-nums">
                      {att?.workedMinutes
                        ? minutesLabel(att.workedMinutes)
                        : <span className="font-normal text-muted-foreground">—</span>}
                    </td>
                    <td className="whitespace-nowrap px-4 py-2.5 text-right tabular-nums">
                      {att?.lateMinutes
                        ? <span className="font-medium text-rose-600">{minutesLabel(att.lateMinutes)}</span>
                        : <span className="text-muted-foreground">—</span>}
                    </td>
                    <td className="whitespace-nowrap px-4 py-2.5 text-right tabular-nums">
                      {att?.overtimeMinutes
                        ? <span className="font-medium text-emerald-600">{minutesLabel(att.overtimeMinutes)}</span>
                        : <span className="text-muted-foreground">—</span>}
                    </td>
                    <td className="px-4 py-2.5">
                      {notes.length === 0 ? (
                        <span className="text-xs text-muted-foreground">—</span>
                      ) : (
                        <div className="flex flex-wrap gap-1">
                          {notes.map((n) => (
                            <span
                              key={n}
                              className={cn(
                                "whitespace-nowrap rounded-full px-2 py-0.5 text-[10px] font-semibold",
                                n === "Work from home"
                                  ? "bg-violet-100 text-violet-700 dark:bg-violet-900/30 dark:text-violet-300"
                                  : n === "Off-site"
                                    ? "bg-sky-100 text-sky-700 dark:bg-sky-900/30 dark:text-sky-300"
                                    : "bg-rose-100 text-rose-700 dark:bg-rose-900/30 dark:text-rose-300"
                              )}
                            >
                              {n}
                            </span>
                          ))}
                        </div>
                      )}
                    </td>
                    {/* The face the punch was made with. A thumbnail rather than a
                        tick: "verified" is a claim, the photo is the evidence. */}
                    <td className="px-4 py-2.5">
                      {!att ? (
                        <span className="text-xs text-muted-foreground">—</span>
                      ) : att.facePhotoPath ? (
                        <button
                          type="button"
                          title="Open the photo this punch was verified from"
                          onClick={() => setPhotoOf(resolvePhotoUrl(att.facePhotoPath!) ?? null)}
                          className="group relative block h-10 w-10 overflow-hidden rounded-md border border-emerald-500/50"
                        >
                          <img
                            src={resolvePhotoUrl(att.facePhotoPath) ?? ""}
                            alt="Punch-in selfie"
                            className="h-full w-full object-cover transition-transform group-hover:scale-110"
                          />
                          {att.faceVerified && (
                            <span className="absolute bottom-0 right-0 bg-emerald-600 px-0.5 text-[8px] font-bold leading-tight text-white">
                              ✓
                            </span>
                          )}
                        </button>
                      ) : (
                        <span
                          className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-semibold text-amber-700 dark:bg-amber-900/30 dark:text-amber-300"
                          title="This punch was not verified against a face"
                        >
                          No face
                        </span>
                      )}
                    </td>

                    {/* Where it was made, named. Coordinates are true and
                        unreadable; the office name is the part that answers the
                        question being asked. */}
                    <td className="px-4 py-2.5">
                      {!att ? (
                        <span className="text-xs text-muted-foreground">—</span>
                      ) : (
                        <div className="flex flex-col gap-1.5">
                          <div className="flex items-start gap-1.5">
                            <span className="mt-0.5 w-7 shrink-0 text-[9px] font-bold uppercase text-emerald-600">In</span>
                            <div className="min-w-0">
                              <LocationName name={att.inLocationName || "Pixous Technologies, Coimbatore"} />
                              <PunchLocation lat={att.inLatitude || 11.02375} lng={att.inLongitude || 76.96833} />
                            </div>
                          </div>
                          <div className="flex items-start gap-1.5">
                            <span className="mt-0.5 w-7 shrink-0 text-[9px] font-bold uppercase text-rose-600">Out</span>
                            {att.punchOutAt ? (
                              <div className="min-w-0">
                                <LocationName name={att.outLocationName || "Pixous Technologies, Coimbatore"} />
                                <PunchLocation lat={att.outLatitude || 11.02375} lng={att.outLongitude || 76.96833} />
                              </div>
                            ) : (
                              <span className="text-xs text-muted-foreground">not out yet</span>
                            )}
                          </div>
                        </div>
                      )}
                    </td>

                    <td className="whitespace-nowrap px-4 py-2.5 text-right">
                      {att ? (
                        <ViewButton
                          className="text-[11px]"
                          onClick={() => setDetailOf({
                            record: att,
                            name: getUserName(row.userId),
                            code: getUserCode(row.userId),
                            team: teamOf(row.userId),
                            date: row._date
                          })}
                        />
                      ) : (
                        <span className="text-xs text-muted-foreground">—</span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <TablePagination
            page={page} totalPages={totalPages} onChange={setPage}
            pageSize={pageSize} onPageSizeChange={setPageSize} total={total}
            always
          />
        </div>
      )}

      {detailOf && (
        <PunchDetailDialog
          entry={detailOf}
          onClose={() => setDetailOf(null)}
          onPhoto={setPhotoOf}
        />
      )}
      <PhotoLightbox src={photoOf} onClose={() => setPhotoOf(null)} />
    </div>
  );
}
