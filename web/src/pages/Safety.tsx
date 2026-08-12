import { useState } from "react";
import { useQuery, useMutation, useQueryClient, keepPreviousData } from "@tanstack/react-query";
import { Loader2, Plus, ShieldCheck, AlertTriangle, ChevronLeft, ChevronRight, CheckCircle2, MapPin, Clock } from "lucide-react";
import toast from "react-hot-toast";
import { api, apiMessage } from "@/lib/api";
import { PageHeader } from "@/components/PageHeader";
import { EmptyState } from "@/components/EmptyState";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Label } from "@/components/ui/label";
import { Select } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import { Dialog, DialogHeader } from "@/components/ui/dialog";
import { useAuth } from "@/hooks/useAuth";
import type { ApiEnvelope, PageEnvelope } from "@/types";
import dayjs from "dayjs";

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

interface SafetyIncident {
  id: number;
  referenceCode: string;
  reportedBy: number;
  reportedByName: string;
  incidentType: string; // NEAR_MISS | MINOR_INJURY | MAJOR_INJURY | PROPERTY_DAMAGE | ENV_HAZARD
  description: string;
  zone?: string;
  anonymous: boolean;
  severity: string; // LOW | MEDIUM | HIGH | CRITICAL
  status: string; // OPEN | INVESTIGATING | RESOLVED | CLOSED
  occurredAt?: string;
  resolutionNotes?: string;
  resolvedBy?: number;
  resolvedByName?: string;
  resolvedAt?: string;
  createdAt: string;
}

/* ------------------------------------------------------------------ */
/*  Badge variant helpers                                              */
/* ------------------------------------------------------------------ */

function incidentTypeVariant(type: string): "default" | "secondary" | "destructive" | "outline" | "warning" {
  switch (type) {
    case "NEAR_MISS":
      return "warning";
    case "MINOR_INJURY":
      return "secondary";
    case "MAJOR_INJURY":
      return "destructive";
    case "PROPERTY_DAMAGE":
      return "outline";
    case "ENV_HAZARD":
      return "destructive";
    default:
      return "outline";
  }
}

function severityVariant(severity: string): "default" | "secondary" | "destructive" | "outline" | "warning" {
  switch (severity) {
    case "LOW":
      return "secondary";
    case "MEDIUM":
      return "outline";
    case "HIGH":
      return "warning";
    case "CRITICAL":
      return "destructive";
    default:
      return "outline";
  }
}

function statusVariant(status: string): "default" | "secondary" | "destructive" | "outline" {
  switch (status) {
    case "OPEN":
      return "outline";
    case "INVESTIGATING":
      return "secondary";
    case "RESOLVED":
      return "default";
    case "CLOSED":
      return "secondary";
    default:
      return "outline";
  }
}

function incidentTypeLabel(type: string): string {
  switch (type) {
    case "NEAR_MISS":
      return "Near Miss";
    case "MINOR_INJURY":
      return "Minor Injury";
    case "MAJOR_INJURY":
      return "Major Injury";
    case "PROPERTY_DAMAGE":
      return "Property Damage";
    case "ENV_HAZARD":
      return "Env. Hazard";
    default:
      return type;
  }
}

/* ------------------------------------------------------------------ */
/*  Main page                                                          */
/* ------------------------------------------------------------------ */

export default function SafetyPage() {
  const { hasPermission } = useAuth();
  const canReview = hasPermission("REPORT_VIEW") || hasPermission("USER_MANAGE");
  const [tab, setTab] = useState<"mine" | "all">("mine");
  const [showReport, setShowReport] = useState(false);
  const [viewId, setViewId] = useState<number | null>(null);

  return (
    <div>
      <PageHeader
        title="Safety & Incidents"
        subtitle="Report and track workplace safety incidents, near-misses, and hazards."
        actions={
          <Button onClick={() => setShowReport(true)}>
            <Plus className="mr-2 h-4 w-4" /> Report Incident
          </Button>
        }
      />

      {canReview && (
        <div className="mb-4 flex gap-2">
          <Button
            variant={tab === "mine" ? "default" : "outline"}
            size="sm"
            onClick={() => setTab("mine")}
          >
            My Reports
          </Button>
          <Button
            variant={tab === "all" ? "default" : "outline"}
            size="sm"
            onClick={() => setTab("all")}
          >
            All Reports
          </Button>
        </div>
      )}

      {tab === "mine" || !canReview ? (
        <MyReports onView={setViewId} />
      ) : (
        <AllReports onView={setViewId} />
      )}

      {showReport && <ReportDialog onClose={() => setShowReport(false)} />}

      {viewId !== null && (
        <IncidentDetailDialog
          id={viewId}
          onClose={() => setViewId(null)}
          canResolve={canReview}
        />
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  My Reports                                                         */
/* ------------------------------------------------------------------ */

function MyReports({ onView }: { onView: (id: number) => void }) {
  const [page, setPage] = useState(0);

  const query = useQuery({
    queryKey: ["safety-incidents", "mine", page],
    placeholderData: keepPreviousData,
    queryFn: async () => {
      const res = await api.get<ApiEnvelope<PageEnvelope<SafetyIncident>>>(
        `/safety-incidents/mine?page=${page}&size=10`
      );
      return res.data.data;
    }
  });

  const rows = query.data?.content ?? [];
  const totalPages = query.data?.totalPages ?? 1;

  if (query.isLoading) return <Skeleton className="h-64" />;
  if (rows.length === 0) {
    return (
      <EmptyState
        icon={ShieldCheck}
        title="No incidents reported"
        description={'Use "Report Incident" to log a workplace safety concern.'}
      />
    );
  }

  return (
    <div className="space-y-3">
      {rows.map((incident) => (
        <IncidentCard key={incident.id} incident={incident} onClick={() => onView(incident.id)} />
      ))}
      <Pager page={page} totalPages={totalPages} onPage={setPage} />
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  All Reports (admin)                                                */
/* ------------------------------------------------------------------ */

function AllReports({ onView }: { onView: (id: number) => void }) {
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState("");
  const [typeFilter, setTypeFilter] = useState("");

  const query = useQuery({
    queryKey: ["safety-incidents", "all", statusFilter, typeFilter, page],
    placeholderData: keepPreviousData,
    queryFn: async () => {
      let url = `/safety-incidents?page=${page}&size=10`;
      if (statusFilter) url += `&status=${statusFilter}`;
      if (typeFilter) url += `&incidentType=${typeFilter}`;
      const res = await api.get<ApiEnvelope<PageEnvelope<SafetyIncident>>>(url);
      return res.data.data;
    }
  });

  const rows = query.data?.content ?? [];
  const totalPages = query.data?.totalPages ?? 1;

  const statuses = ["OPEN", "INVESTIGATING", "RESOLVED", "CLOSED"];

  return (
    <div className="space-y-3">
      {/* Filters */}
      <Card>
        <CardContent className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
          <div className="flex flex-wrap gap-2">
            <Button
              variant={statusFilter === "" ? "default" : "outline"}
              size="sm"
              onClick={() => { setStatusFilter(""); setPage(0); }}
            >
              All
            </Button>
            {statuses.map((s) => (
              <Button
                key={s}
                variant={statusFilter === s ? "default" : "outline"}
                size="sm"
                onClick={() => { setStatusFilter(s); setPage(0); }}
              >
                {s.replace("_", " ")}
              </Button>
            ))}
          </div>

          <Select
            value={typeFilter}
            onChange={(e) => { setTypeFilter(e.target.value); setPage(0); }}
            className="sm:w-52"
          >
            <option value="">All incident types</option>
            <option value="NEAR_MISS">Near Miss</option>
            <option value="MINOR_INJURY">Minor Injury</option>
            <option value="MAJOR_INJURY">Major Injury</option>
            <option value="PROPERTY_DAMAGE">Property Damage</option>
            <option value="ENV_HAZARD">Env. Hazard</option>
          </Select>
        </CardContent>
      </Card>

      {/* Results */}
      {query.isLoading ? (
        <Skeleton className="h-64" />
      ) : rows.length === 0 ? (
        <EmptyState
          icon={ShieldCheck}
          title="No incidents found"
          description="Try a different filter."
        />
      ) : (
        <>
          {rows.map((incident) => (
            <IncidentCard
              key={incident.id}
              incident={incident}
              showReporter
              onClick={() => onView(incident.id)}
            />
          ))}
          <Pager page={page} totalPages={totalPages} onPage={setPage} />
        </>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Incident Card                                                      */
/* ------------------------------------------------------------------ */

function IncidentCard({
  incident,
  showReporter,
  onClick
}: {
  incident: SafetyIncident;
  showReporter?: boolean;
  onClick?: () => void;
}) {
  return (
    <Card
      className="cursor-pointer transition-shadow hover:shadow-md"
      onClick={onClick}
    >
      <CardContent className="p-4">
        {/* Top row: ref code + badges */}
        <div className="flex flex-wrap items-center gap-2">
          <span className="code-chip text-xs text-muted-foreground">{incident.referenceCode}</span>
          <Badge variant={incidentTypeVariant(incident.incidentType)}>
            {incidentTypeLabel(incident.incidentType)}
          </Badge>
          <Badge variant={severityVariant(incident.severity)}>
            {incident.severity}
          </Badge>
          <Badge variant={statusVariant(incident.status)}>
            {incident.status.replace("_", " ")}
          </Badge>
        </div>

        {/* Description */}
        <p className="mt-2 line-clamp-2 text-sm text-muted-foreground">
          {incident.description}
        </p>

        {/* Meta row */}
        <div className="mt-3 flex flex-wrap items-center gap-4 text-xs text-muted-foreground">
          {incident.zone && (
            <span className="flex items-center gap-1">
              <MapPin className="h-3.5 w-3.5" />
              {incident.zone}
            </span>
          )}
          <span className="flex items-center gap-1">
            <Clock className="h-3.5 w-3.5" />
            {dayjs(incident.occurredAt ?? incident.createdAt).format("DD MMM YYYY, h:mm A")}
          </span>
          {showReporter && (
            <span>
              Reporter: {incident.anonymous ? "Anonymous" : incident.reportedByName}
            </span>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

/* ------------------------------------------------------------------ */
/*  Pager                                                              */
/* ------------------------------------------------------------------ */

function Pager({
  page,
  totalPages,
  onPage
}: {
  page: number;
  totalPages: number;
  onPage: (p: number) => void;
}) {
  if (totalPages <= 1) return null;
  return (
    <div className="flex items-center justify-end gap-2">
      <Button variant="outline" size="sm" disabled={page === 0} onClick={() => onPage(Math.max(0, page - 1))}>
        <ChevronLeft className="h-4 w-4" />
      </Button>
      <span className="text-sm">
        {page + 1} / {totalPages}
      </span>
      <Button
        variant="outline"
        size="sm"
        disabled={page + 1 >= totalPages}
        onClick={() => onPage(page + 1)}
      >
        <ChevronRight className="h-4 w-4" />
      </Button>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Report Dialog                                                      */
/* ------------------------------------------------------------------ */

function ReportDialog({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient();
  const [incidentType, setIncidentType] = useState("NEAR_MISS");
  const [severity, setSeverity] = useState("MEDIUM");
  const [zone, setZone] = useState("");
  const [occurredAt, setOccurredAt] = useState("");
  const [description, setDescription] = useState("");
  const [anonymous, setAnonymous] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: async () => {
      await apiMessage(
        api.post("/safety-incidents", {
          incidentType,
          severity,
          zone: zone.trim() || undefined,
          occurredAt: occurredAt || undefined,
          description: description.trim(),
          anonymous
        }),
        "Incident reported"
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["safety-incidents"] });
      onClose();
    },
    onError: (err) => setError(apiMessage(err, "Could not report incident"))
  });

  function submit() {
    setError(null);
    if (!description.trim()) {
      setError("Please describe the incident");
      return;
    }
    mutation.mutate();
  }

  return (
    <Dialog open onClose={onClose}>
      <DialogHeader
        title="Report Safety Incident"
        description="Log an incident, near-miss, or hazard for review."
      />

      {error && (
        <div className="mb-4 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </div>
      )}

      <div className="space-y-4">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div className="space-y-1">
            <Label htmlFor="si-type">Incident Type</Label>
            <Select id="si-type" value={incidentType} onChange={(e) => setIncidentType(e.target.value)}>
              <option value="NEAR_MISS">Near Miss</option>
              <option value="MINOR_INJURY">Minor Injury</option>
              <option value="MAJOR_INJURY">Major Injury</option>
              <option value="PROPERTY_DAMAGE">Property Damage</option>
              <option value="ENV_HAZARD">Env. Hazard</option>
            </Select>
          </div>
          <div className="space-y-1">
            <Label htmlFor="si-severity">Severity</Label>
            <Select id="si-severity" value={severity} onChange={(e) => setSeverity(e.target.value)}>
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
              <option value="CRITICAL">Critical</option>
            </Select>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div className="space-y-1">
            <Label htmlFor="si-zone">Zone / Location</Label>
            <Input
              id="si-zone"
              value={zone}
              onChange={(e) => setZone(e.target.value)}
              placeholder="e.g. Warehouse B, Floor 3"
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="si-date">Date/Time of Incident</Label>
            <Input
              id="si-date"
              type="datetime-local"
              value={occurredAt}
              onChange={(e) => setOccurredAt(e.target.value)}
            />
          </div>
        </div>

        <div className="space-y-1">
          <Label htmlFor="si-desc">Description *</Label>
          <Textarea
            id="si-desc"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Describe what happened, who was involved, and any immediate actions taken…"
            rows={4}
          />
        </div>

        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={anonymous}
            onChange={(e) => setAnonymous(e.target.checked)}
            className="h-4 w-4 rounded border-input"
          />
          Report anonymously
        </label>
      </div>

      <div className="mt-6 flex justify-end gap-2 border-t pt-4">
        <Button variant="ghost" onClick={onClose} disabled={mutation.isPending}>
          Cancel
        </Button>
        <Button onClick={submit} disabled={mutation.isPending}>
          {mutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
          {mutation.isPending ? "Submitting…" : "Report Incident"}
        </Button>
      </div>
    </Dialog>
  );
}

/* ------------------------------------------------------------------ */
/*  Incident Detail Dialog                                             */
/* ------------------------------------------------------------------ */

function IncidentDetailDialog({
  id,
  onClose,
  canResolve
}: {
  id: number;
  onClose: () => void;
  canResolve: boolean;
}) {
  const queryClient = useQueryClient();
  const [resolveStatus, setResolveStatus] = useState("RESOLVED");
  const [resolutionNotes, setResolutionNotes] = useState("");
  const [error, setError] = useState<string | null>(null);

  const query = useQuery({
    queryKey: ["safety-incidents", id],
    queryFn: async () => {
      const res = await api.get<ApiEnvelope<SafetyIncident>>(`/safety-incidents/${id}`);
      return res.data.data;
    }
  });

  const resolveMutation = useMutation({
    mutationFn: async () => {
      await apiMessage(
        api.post(`/safety-incidents/${id}/resolve`, {
          status: resolveStatus,
          resolutionNotes: resolutionNotes.trim() || undefined
        }),
        "Status updated"
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["safety-incidents"] });
      onClose();
    },
    onError: (err) => setError(apiMessage(err, "Could not update"))
  });

  const incident = query.data;

  return (
    <Dialog open onClose={onClose}>
      {query.isLoading ? (
        <Skeleton className="h-40" />
      ) : !incident ? (
        <p className="py-8 text-center text-sm text-muted-foreground">Incident not found.</p>
      ) : (
        <>
          <DialogHeader
            title={`Incident · ${incident.referenceCode}`}
            description={incidentTypeLabel(incident.incidentType)}
          />

          {error && (
            <div className="mb-4 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {error}
            </div>
          )}

          <div className="space-y-4">
            {/* Badges */}
            <div className="flex flex-wrap gap-2">
              <Badge variant={incidentTypeVariant(incident.incidentType)}>
                {incidentTypeLabel(incident.incidentType)}
              </Badge>
              <Badge variant={severityVariant(incident.severity)}>
                {incident.severity}
              </Badge>
              <Badge variant={statusVariant(incident.status)}>
                {incident.status.replace("_", " ")}
              </Badge>
            </div>

            {/* Details */}
            <div className="rounded-md border bg-muted/40 px-3 py-2 text-sm">
              <p className="whitespace-pre-wrap">{incident.description}</p>
            </div>

            <div className="grid grid-cols-1 gap-2 text-sm sm:grid-cols-2">
              {incident.zone && (
                <div className="flex items-center gap-1.5 text-muted-foreground">
                  <MapPin className="h-4 w-4" />
                  <span>{incident.zone}</span>
                </div>
              )}
              <div className="flex items-center gap-1.5 text-muted-foreground">
                <Clock className="h-4 w-4" />
                <span>
                  {dayjs(incident.occurredAt ?? incident.createdAt).format("DD MMM YYYY, h:mm A")}
                </span>
              </div>
              <div className="text-muted-foreground">
                Reporter: {incident.anonymous ? "Anonymous" : incident.reportedByName}
              </div>
              <div className="text-muted-foreground">
                Reported: {dayjs(incident.createdAt).format("DD MMM YYYY, h:mm A")}
              </div>
            </div>

            {/* Resolution notes if already resolved */}
            {incident.resolutionNotes && (
              <div className="rounded-md border bg-muted/40 px-3 py-2 text-sm">
                <div className="mb-0.5 flex items-center gap-1 text-xs font-medium text-muted-foreground">
                  <CheckCircle2 className="h-3.5 w-3.5" />
                  Resolution{incident.resolvedByName ? ` · ${incident.resolvedByName}` : ""}
                  {incident.resolvedAt
                    ? ` · ${dayjs(incident.resolvedAt).format("DD MMM YYYY")}`
                    : ""}
                </div>
                <p className="whitespace-pre-wrap">{incident.resolutionNotes}</p>
              </div>
            )}

            {/* Admin resolve section */}
            {canResolve && (
              <div className="space-y-3 border-t pt-4">
                <h4 className="flex items-center gap-1.5 text-sm font-medium">
                  <AlertTriangle className="h-4 w-4" />
                  Update Status
                </h4>
                <div className="space-y-1">
                  <Label htmlFor="si-resolve-status">Status</Label>
                  <Select
                    id="si-resolve-status"
                    value={resolveStatus}
                    onChange={(e) => setResolveStatus(e.target.value)}
                  >
                    <option value="OPEN">Open</option>
                    <option value="INVESTIGATING">Investigating</option>
                    <option value="RESOLVED">Resolved</option>
                    <option value="CLOSED">Closed</option>
                  </Select>
                </div>
                <div className="space-y-1">
                  <Label htmlFor="si-resolution-notes">Resolution Notes</Label>
                  <Textarea
                    id="si-resolution-notes"
                    value={resolutionNotes}
                    onChange={(e) => setResolutionNotes(e.target.value)}
                    placeholder="Add notes about the investigation or resolution…"
                    rows={3}
                  />
                </div>
              </div>
            )}
          </div>

          <div className="mt-6 flex justify-end gap-2 border-t pt-4">
            <Button variant="ghost" onClick={onClose} disabled={resolveMutation.isPending}>
              Close
            </Button>
            {canResolve && (
              <Button
                onClick={() => resolveMutation.mutate()}
                disabled={resolveMutation.isPending}
              >
                {resolveMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                {resolveMutation.isPending ? "Saving…" : "Update Status"}
              </Button>
            )}
          </div>
        </>
      )}
    </Dialog>
  );
}
