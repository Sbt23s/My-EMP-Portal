import { CustomLoader as Loader2 } from "@/components/ui/custom-loader";
import { useState, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  Plus, LifeBuoy, Send, Star, MessageSquare,
  Ticket as TicketIcon, Clock, AlertTriangle, CheckCircle, Lock, Paperclip, Inbox,
  Eye, Pencil
} from "lucide-react";
import dayjs from "dayjs";
import toast from "react-hot-toast";
import { api, apiMessage } from "@/lib/api";
import { useAuth } from "@/hooks/useAuth";
import { PageHeader } from "@/components/PageHeader";
import { EmptyState } from "@/components/EmptyState";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge, statusVariant } from "@/components/ui/badge";
import { Dialog, DialogHeader } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import { Avatar, resolvePhotoUrl } from "@/components/ui/avatar";
import { PhotoLightbox } from "@/components/PhotoLightbox";
import { cn } from "@/lib/utils";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow
} from "@/components/ui/table";
import { usePagedRows, TablePagination } from "@/components/ui/table-pagination";
import { StatTile, TILE_FILLS } from "@/components/ui/stat-tile";
import type { ApiEnvelope, PageEnvelope, Ticket } from "@/types";

const STATUSES = ["OPEN", "IN_PROGRESS", "AWAITING_PARTS", "RESOLVED", "CLOSED"];

const isImageFile = (path: string) => /\.(png|jpe?g|gif|webp|bmp)$/i.test(path);

/** The ticket lifecycle as tiles, in the order a ticket moves through it. */
const TICKET_TILES = [
  { key: "ALL", label: "All", icon: TicketIcon, fill: TILE_FILLS.violet, hint: "Every ticket in this period" },
  { key: "OPEN", label: "Open", icon: Inbox, fill: TILE_FILLS.blue, hint: "Raised, not picked up yet" },
  { key: "IN_PROGRESS", label: "In progress", icon: Clock, fill: TILE_FILLS.amber, hint: "Being worked on" },
  { key: "AWAITING_PARTS", label: "Awaiting parts", icon: AlertTriangle, fill: TILE_FILLS.orange, hint: "Waiting on something external" },
  { key: "RESOLVED", label: "Resolved", icon: CheckCircle, fill: TILE_FILLS.green, hint: "Fixed, awaiting your rating" },
  { key: "CLOSED", label: "Closed", icon: Lock, fill: TILE_FILLS.slate, hint: "Finished and closed" }
] as const;

const MONTHS = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December"
];

function priorityVariant(p: string) {
  switch (p) {
    case "CRITICAL":
    case "HIGH":
      return "destructive" as const;
    case "MEDIUM":
      return "warning" as const;
    default:
      return "secondary" as const;
  }
}

export default function HelpdeskPage() {
  const { hasPermission, user } = useAuth();
  const navigate = useNavigate();
  const isAgent = hasPermission("HELPDESK_AGENT");
  // HR / Admin / execs can oversee every ticket in the org.
  const canSeeAll = hasPermission("USER_MANAGE") || hasPermission("DASHBOARD_EXEC");
  const [tab, setTab] = useState<"mine" | "queue" | "all">(canSeeAll ? "all" : "mine");
  const [openId, setOpenId] = useState<number | null>(null);
  // The ticket open in the edit form.
  const [editTicket, setEditTicket] = useState<Ticket | null>(null);

  const mine = useQuery({
    queryKey: ["tickets", "mine"],
    queryFn: async () =>
      (await api.get<PageEnvelope<Ticket>>("/tickets?size=50")).data.content
  });

  const queue = useQuery({
    queryKey: ["tickets", "queue"],
    enabled: isAgent && tab === "queue",
    queryFn: async () =>
      (await api.get<PageEnvelope<Ticket>>("/tickets/assigned-to-me?size=50")).data.content
  });

  const all = useQuery({
    queryKey: ["tickets", "all"],
    enabled: canSeeAll && tab === "all",
    queryFn: async () =>
      (await api.get<PageEnvelope<Ticket>>("/tickets/all?size=200")).data.content
  });

  const rawList = tab === "queue" ? queue.data ?? [] : tab === "all" ? all.data ?? [] : mine.data ?? [];
  const loading = tab === "queue" ? queue.isLoading : tab === "all" ? all.isLoading : mine.isLoading;

  // Look back over a year, a month, or a single day.
  const [year, setYear] = useState("all");
  const [month, setMonth] = useState("all");
  const [day, setDay] = useState("");

  // Years that actually have tickets, newest first.
  const years = useMemo(() => {
    const set = new Set<string>();
    rawList.forEach((t) => { if (t.createdAt) set.add(dayjs(t.createdAt).format("YYYY")); });
    return [...set].sort((a, b) => b.localeCompare(a));
  }, [rawList]);

  const [statusTab, setStatusTab] = useState("ALL");

  // Date-filtered but status-agnostic, so the tiles keep counting the whole
  // period while a status tile is selected.
  const inPeriod = useMemo(() => rawList.filter((t) => {
    if (!t.createdAt) return year === "all" && month === "all" && !day;
    const d = dayjs(t.createdAt);
    // An exact date wins over the year/month pickers.
    if (day) return d.format("YYYY-MM-DD") === day;
    if (year !== "all" && d.format("YYYY") !== year) return false;
    if (month !== "all" && d.format("MM") !== month) return false;
    return true;
  }), [rawList, year, month, day]);

  const statusCounts = useMemo(() => {
    const c: Record<string, number> = { ALL: inPeriod.length };
    STATUSES.forEach((s) => { c[s] = 0; });
    inPeriod.forEach((t) => {
      // ON_HOLD is the older name for the same waiting state.
      const key = t.status === "ON_HOLD" ? "AWAITING_PARTS" : t.status;
      if (key in c) c[key] += 1;
    });
    return c;
  }, [inPeriod]);

  const list = useMemo(() => {
    const filtered = statusTab === "ALL"
      ? inPeriod
      : inPeriod.filter((t) =>
          (t.status === "ON_HOLD" ? "AWAITING_PARTS" : t.status) === statusTab);
    return [...filtered].sort((a, b) =>
      String(b.createdAt ?? "").localeCompare(String(a.createdAt ?? "")));
  }, [inPeriod, statusTab]);

  const paged = usePagedRows(list, 15, [tab, year, month, day, statusTab, rawList]);
  const filtersOn = year !== "all" || month !== "all" || !!day;

  const tabs = canSeeAll
    ? [{ id: "all" as const, label: "All tickets" }]
    : [
        { id: "mine" as const, label: "My tickets" },
        ...(isAgent ? [{ id: "queue" as const, label: "Assigned to me" }] : [])
      ];

  const tickets = all.data ?? [];

  return (
    <div>
      <PageHeader
        title="Supports"
        subtitle={canSeeAll ? "Overview of all support requests" : "Raise IT and facility requests, track progress, and rate resolutions."}
        actions={
          !canSeeAll && (
            <Button onClick={() => navigate("/helpdesk/new")}>
              <Plus className="h-4 w-4" /> New ticket
            </Button>
          )
        }
      />

      {/* The five status cards that used to sit here said exactly what the
          status filter row below already says, in the same order, with the
          same numbers. One of them had to go, and the filters are the ones
          you can actually click. */}

      {tabs.length > 1 && (
        <div className="mb-4 inline-flex rounded-lg border bg-card p-1">
          {tabs.map((t) => (
            <button
              key={t.id}
              onClick={() => setTab(t.id)}
              className={cn(
                "rounded-md px-4 py-1.5 text-sm font-medium transition-colors",
                tab === t.id ? "bg-primary text-primary-foreground" : "text-muted-foreground"
              )}
            >
              {t.label}
            </button>
          ))}
        </div>
      )}

      {canSeeAll && (
        <h3 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-3">All Tickets</h3>
      )}

      {/* Status counts over the chosen period — each tile is also its filter. */}
      <div className="mb-4 grid grid-cols-2 gap-2.5 sm:grid-cols-3 lg:grid-cols-6">
        {TICKET_TILES.map((t) => (
          <StatTile
            key={t.key}
            compact
            label={t.label}
            value={statusCounts[t.key] ?? 0}
            hint={t.hint}
            icon={t.icon}
            fill={t.fill}
            active={statusTab === t.key}
            onClick={() => setStatusTab(t.key)}
          />
        ))}
      </div>

      {/* Look back by year, month or an exact date. */}
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div className="space-y-1">
          <label className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Year</label>
          <Select value={year} onChange={(e) => { setYear(e.target.value); setDay(""); }} className="w-28">
            <option value="all">All years</option>
            {years.map((y) => <option key={y} value={y}>{y}</option>)}
          </Select>
        </div>
        <div className="space-y-1">
          <label className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Month</label>
          <Select value={month} onChange={(e) => { setMonth(e.target.value); setDay(""); }} className="w-36">
            <option value="all">All months</option>
            {MONTHS.map((m, i) => (
              <option key={m} value={String(i + 1).padStart(2, "0")}>{m}</option>
            ))}
          </Select>
        </div>
        <div className="space-y-1">
          <label className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Exact date</label>
          <Input type="date" value={day} onChange={(e) => setDay(e.target.value)} className="w-40" />
        </div>
        {filtersOn && (
          <Button
            variant="outline"
            onClick={() => { setYear("all"); setMonth("all"); setDay(""); }}
          >
            Reset
          </Button>
        )}
        <span className="ml-auto text-xs text-muted-foreground">
          {list.length} of {rawList.length} ticket{rawList.length === 1 ? "" : "s"}
        </span>
      </div>

      {loading ? (
        <div className="space-y-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-20" />
          ))}
        </div>
      ) : list.length === 0 ? (
        <EmptyState
          icon={LifeBuoy}
          title={
            statusTab !== "ALL" || filtersOn
              ? "Nothing matches these filters"
              : tab === "queue" ? "Your queue is clear"
                : tab === "all" ? "No tickets in the system"
                  : "No tickets yet"
          }
          description={
            statusTab !== "ALL" || filtersOn
              ? "Try another status tile, or widen the date range above."
              : tab === "queue"
                ? "Tickets assigned to you will show up here."
                : tab === "all"
                  ? "Once employees raise tickets they will all appear here."
                  : "Raise a ticket for IT support or on-site facilities."
          }
        />
      ) : canSeeAll || tab === "queue" ? (
        <Card>
          <CardContent className="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="pl-6">Ticket ID</TableHead>
                  <TableHead>Employee ID</TableHead>
                  <TableHead>Employee Name</TableHead>
                  <TableHead>Subject</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Category</TableHead>
                  <TableHead>Priority</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Approved By</TableHead>
                  <TableHead>Date</TableHead>
                  <TableHead className="text-right pr-6">Action</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {paged.pageRows.map((t) => (
                  <TableRow key={t.id}>
                    <TableCell className="pl-6 font-medium code-chip">{t.ticketCode}</TableCell>
                    <TableCell className="code-chip text-xs text-muted-foreground">{t.raisedByCode || "—"}</TableCell>
                    <TableCell className="font-medium">{t.raisedByName}</TableCell>
                    <TableCell className="max-w-[200px] truncate font-medium">{t.title}</TableCell>
                    <TableCell>
                      <Badge variant="secondary">{t.type}</Badge>
                    </TableCell>
                    <TableCell>{t.category || "—"}</TableCell>
                    <TableCell>
                      <Badge variant={priorityVariant(t.priority)}>{t.priority}</Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant={statusVariant(t.status)}>{t.status}</Badge>
                    </TableCell>
                    <TableCell>{t.assignedToName || "—"}</TableCell>
                    <TableCell className="text-muted-foreground">{dayjs(t.createdAt).format("DD MMM YYYY")}</TableCell>
                    <TableCell className="text-right pr-6">
                      {(t.status === "RESOLVED" || t.status === "CLOSED") ? (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setOpenId(t.id)}
                        >
                          <Eye className="mr-1 h-3.5 w-3.5" /> View
                        </Button>
                      ) : (t.assignedTo === user?.id || (!t.assignedTo && isAgent)) ? (
                        <Button
                          size="sm"
                          onClick={() => setOpenId(t.id)}
                        >
                          Respond
                        </Button>
                      ) : (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setOpenId(t.id)}
                        >
                          <Eye className="mr-1 h-3.5 w-3.5" /> View
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <div className="border-t px-4 py-2 text-xs text-muted-foreground">
              Showing {paged.pageRows.length} of {list.length} ticket{list.length === 1 ? "" : "s"}
            </div>
            <TablePagination
              page={paged.page} totalPages={paged.totalPages} onChange={paged.setPage}
              pageSize={paged.pageSize} onPageSizeChange={paged.setPageSize} total={paged.total}
              always
            />
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardContent className="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="pl-6">Ticket ID</TableHead>
                  <TableHead>Subject</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Category</TableHead>
                  <TableHead>Priority</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Date</TableHead>
                  <TableHead className="text-right pr-6">Action</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {paged.pageRows.map((t) => (
                  <TableRow
                    key={t.id}
                    className="cursor-pointer"
                    onClick={() => setOpenId(t.id)}
                  >
                    <TableCell className="pl-6 font-medium code-chip">{t.ticketCode}</TableCell>
                    <TableCell className="max-w-[220px] truncate font-medium">{t.title}</TableCell>
                    <TableCell><Badge variant="secondary">{t.type}</Badge></TableCell>
                    <TableCell>{t.category || "—"}</TableCell>
                    <TableCell><Badge variant={priorityVariant(t.priority)}>{t.priority}</Badge></TableCell>
                    <TableCell><Badge variant={statusVariant(t.status)}>{t.status}</Badge></TableCell>
                    <TableCell className="text-muted-foreground">{dayjs(t.createdAt).format("DD MMM YYYY")}</TableCell>
                    <TableCell
                      className="text-right pr-6"
                      onClick={(e) => e.stopPropagation()}
                    >
                      <div className="flex items-center justify-end gap-1">
                        <Button variant="outline" size="sm" className="shrink-0"
                          onClick={() => setOpenId(t.id)}>
                          <Eye className="mr-1 h-3.5 w-3.5" /> View
                        </Button>
                        {/* Once an agent has picked the ticket up its details are
                            what they are working from, so editing stops there. */}
                        {t.status === "OPEN" && (
                          <Button variant="outline" size="sm" className="shrink-0"
                            onClick={() => setEditTicket(t)}>
                            <Pencil className="mr-1 h-3.5 w-3.5 text-primary" /> Edit
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <div className="border-t px-4 py-2 text-xs text-muted-foreground">
              Showing {paged.pageRows.length} of {list.length} ticket{list.length === 1 ? "" : "s"}
            </div>
            <TablePagination
              page={paged.page} totalPages={paged.totalPages} onChange={paged.setPage}
              pageSize={paged.pageSize} onPageSizeChange={paged.setPageSize} total={paged.total}
              always
            />
          </CardContent>
        </Card>
      )}

      {openId != null && (
        <TicketDetail id={openId} isAgent={isAgent} onClose={() => setOpenId(null)} />
      )}

      {editTicket && (
        <EditTicketDialog ticket={editTicket} onClose={() => setEditTicket(null)} />
      )}
    </div>
  );
}

const EDIT_TYPES = [{ value: "IT", label: "IT" }, { value: "FACILITY", label: "Facility" }];
const EDIT_PRIORITIES = [
  { value: "LOW", label: "Low — can wait" },
  { value: "MEDIUM", label: "Medium — normal" },
  { value: "HIGH", label: "High — blocking me" },
  { value: "CRITICAL", label: "Critical — work stopped" }
];

/**
 * The raiser corrects a ticket nobody has picked up yet. Attachments are left
 * alone -- they are added on the ticket thread, not replaced here.
 */
function EditTicketDialog({ ticket, onClose }: { ticket: Ticket; onClose: () => void }) {
  const qc = useQueryClient();
  const [title, setTitle] = useState(ticket.title ?? "");
  const [description, setDescription] = useState(ticket.description ?? "");
  const [type, setType] = useState((ticket.type || "IT").toUpperCase());
  const [priority, setPriority] = useState((ticket.priority || "MEDIUM").toUpperCase());
  const [assignedTo, setAssignedTo] = useState(
    ticket.assignedTo != null ? String(ticket.assignedTo) : ""
  );

  const hrUsers = useQuery({
    queryKey: ["helpdesk-agents"],
    queryFn: async () =>
      (await api.get<ApiEnvelope<{ id: number; name: string; code?: string }[]>>(
        "/tickets/agents"
      )).data.data
  });

  const save = useMutation({
    mutationFn: async () =>
      api.put(`/tickets/${ticket.id}`, {
        title: title.trim(),
        description: description.trim() || undefined,
        type,
        priority,
        assignedTo: assignedTo ? Number(assignedTo) : undefined
      }),
    onSuccess: () => {
      toast.success("Ticket updated");
      qc.invalidateQueries({ queryKey: ["tickets"] });
      onClose();
    },
    onError: (err) => toast.error(apiMessage(err, "Could not update the ticket"))
  });

  const star = <span className="ml-0.5 text-destructive">*</span>;

  return (
    <Dialog open onClose={onClose} className="max-w-lg">
      <DialogHeader
        title={`Edit ${ticket.ticketCode}`}
        description="Change what you asked for. Whoever it is addressed to is notified."
      />
      <div className="space-y-3">
        <div className="space-y-1.5">
          <label className="text-sm font-medium">Subject{star}</label>
          <Input value={title} onChange={(e) => setTitle(e.target.value)} />
        </div>
        <div className="space-y-1.5">
          <label className="text-sm font-medium">Description{star}</label>
          <Textarea rows={4} value={description} onChange={(e) => setDescription(e.target.value)} />
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1.5">
            <label className="text-sm font-medium">Type{star}</label>
            <Select value={type} onChange={(e) => setType(e.target.value)}>
              {EDIT_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
            </Select>
          </div>
          <div className="space-y-1.5">
            <label className="text-sm font-medium">Priority{star}</label>
            <Select value={priority} onChange={(e) => setPriority(e.target.value)}>
              {EDIT_PRIORITIES.map((p) => <option key={p.value} value={p.value}>{p.label}</option>)}
            </Select>
          </div>
        </div>
        <div className="space-y-1.5">
          <label className="text-sm font-medium">Requested to{star}</label>
          <Select value={assignedTo} onChange={(e) => setAssignedTo(e.target.value)}>
            <option value="">{hrUsers.isLoading ? "Loading…" : "Select HR"}</option>
            {(hrUsers.data ?? []).map((u) => (
              <option key={u.id} value={u.id}>{u.name}{u.code ? ` (${u.code})` : ""}</option>
            ))}
          </Select>
        </div>
        <p className="text-[11px] text-muted-foreground">
          Attachments stay as they are — add anything new as a reply on the ticket.
        </p>
      </div>
      <div className="mt-5 flex justify-end gap-2 border-t pt-4">
        <Button variant="outline" onClick={onClose} disabled={save.isPending}>Cancel</Button>
        <Button
          disabled={save.isPending}
          onClick={() => {
            if (!title.trim()) { toast.error("A subject is required"); return; }
            if (!description.trim()) { toast.error("A description is required"); return; }
            if (!assignedTo) { toast.error("Choose who this request goes to"); return; }
            save.mutate();
          }}
        >
          {save.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
          Save changes
        </Button>
      </div>
    </Dialog>
  );
}

function TicketDetail({
  id, isAgent, onClose
}: {
  id: number;
  isAgent: boolean;
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const { user } = useAuth();
  const [comment, setComment] = useState("");
  const [lightbox, setLightbox] = useState<string | null>(null);

  const detail = useQuery({
    queryKey: ["ticket", id],
    queryFn: async () => (await api.get<ApiEnvelope<Ticket>>(`/tickets/${id}`)).data.data
  });

  const t = detail.data;

  const isTransitionAllowed = (current: string, target: string) => {
    const currentIdx = STATUSES.indexOf(current);
    const targetIdx = STATUSES.indexOf(target);
    if (currentIdx === -1 || targetIdx === -1) return false;
    if (targetIdx <= currentIdx) return false;
    if (currentIdx === 1) {
      return targetIdx === 2 || targetIdx === 3;
    }
    return targetIdx === currentIdx + 1;
  };

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["ticket", id] });
    qc.invalidateQueries({ queryKey: ["tickets"] });
  };

  const addComment = useMutation({
    mutationFn: async () => api.post(`/tickets/${id}/comments`, { comment }),
    onSuccess: () => {
      setComment("");
      invalidate();
    },
    onError: (err) => toast.error(apiMessage(err, "Could not add comment"))
  });

  const changeStatus = useMutation({
    mutationFn: async (status: string) => api.post(`/tickets/${id}/status`, { status }),
    onSuccess: () => {
      toast.success("Status updated");
      invalidate();
      qc.invalidateQueries({ queryKey: ["dashboard"] });
    },
    onError: (err) => toast.error(apiMessage(err, "Could not update status"))
  });

  const rate = useMutation({
    mutationFn: async (rating: number) => api.post(`/tickets/${id}/rating`, { rating }),
    onSuccess: () => {
      toast.success("Thanks for the feedback");
      invalidate();
    },
    onError: (err) => toast.error(apiMessage(err, "Could not submit rating"))
  });

  const isRequester = user?.id === t?.raisedBy;
  const isAssignedToMe = user?.id === t?.assignedTo;
  const canRespond = isRequester || isAssignedToMe || (!t?.assignedTo && isAgent);
  const canRate = isRequester && (t?.status === "RESOLVED" || t?.status === "CLOSED");
  const attachments = String(t?.attachments || "").split(",").map((p) => p.trim()).filter(Boolean);

  return (
    <Dialog open onClose={onClose} className="max-w-2xl">
      {detail.isLoading || !t ? (
        <Skeleton className="h-72" />
      ) : (
        <>
          <div className="mb-4 pr-8">
            <div className="mb-1 flex flex-wrap items-center gap-2">
              <span className="code-chip text-xs text-muted-foreground">{t.ticketCode}</span>
              <Badge variant={priorityVariant(t.priority)}>{t.priority}</Badge>
              <Badge variant={statusVariant(t.status)}>{t.status}</Badge>
            </div>
            <h2 className="font-display text-xl font-bold">{t.title}</h2>
            <div className="mt-1 text-sm text-muted-foreground">
              Raised by {t.raisedByName} · {dayjs(t.createdAt).format("DD MMM YYYY, h:mm A")}
            </div>
            {t.assignedToName && (
              <div className="mt-0.5 text-sm text-muted-foreground">Requested to: <span className="font-medium text-foreground">{t.assignedToName}</span></div>
            )}
          </div>

          {t.description && (
            <p className="whitespace-pre-wrap rounded-lg bg-muted/50 p-3 text-sm">{t.description}</p>
          )}

          {/* What the employee attached — HR needs to see the problem, not just read it. */}
          {attachments.length > 0 && (
            <div className="mt-4">
              <div className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                Attachments ({attachments.length})
              </div>
              <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-4">
                {attachments.map((p) => (
                  isImageFile(p) ? (
                    <button
                      key={p}
                      type="button"
                      onClick={() => setLightbox(resolvePhotoUrl(p) ?? null)}
                      title="Open full size"
                      className="group overflow-hidden rounded-xl border"
                    >
                      <img
                        src={resolvePhotoUrl(p)}
                        alt="Ticket attachment"
                        className="h-24 w-full cursor-zoom-in object-cover transition-transform group-hover:scale-105"
                      />
                    </button>
                  ) : (
                    <a
                      key={p}
                      href={resolvePhotoUrl(p)}
                      target="_blank"
                      rel="noreferrer"
                      className="flex h-24 flex-col items-center justify-center gap-1.5 rounded-xl border bg-muted/30 text-primary hover:bg-muted/60"
                    >
                      <Paperclip className="h-5 w-5" />
                      <span className="px-2 text-center text-[11px] font-medium">
                        {p.split("/").pop()}
                      </span>
                    </a>
                  )
                ))}
              </div>
            </div>
          )}

          {isAgent && (isAssignedToMe || !t?.assignedTo) && (
            <div className="mt-4 flex flex-wrap items-center gap-2">
              <span className="text-xs font-medium text-muted-foreground">Set status:</span>
              {STATUSES.map((s) => (
                <Button
                  key={s}
                  variant={t.status === s ? "default" : "outline"}
                  size="sm"
                  disabled={changeStatus.isPending || t.status === s || !isTransitionAllowed(t.status, s)}
                  onClick={() => changeStatus.mutate(s)}
                >
                  {s.replace("_", " ")}
                </Button>
              ))}
            </div>
          )}

          {/* Rating */}
          {canRate && (
            <div className="mt-4 flex items-center gap-2">
              <span className="text-sm font-medium">Rate this resolution:</span>
              <div className="flex gap-1">
                {[1, 2, 3, 4, 5].map((n) => (
                  <button
                    key={n}
                    onClick={() => rate.mutate(n)}
                    disabled={rate.isPending}
                    aria-label={`${n} star`}
                  >
                    <Star
                      className={cn(
                        "h-5 w-5 transition-colors",
                        (t.rating ?? 0) >= n
                          ? "fill-accent text-accent"
                          : "text-muted-foreground hover:text-accent"
                      )}
                    />
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Comments */}
          <div className="mt-6">
            <div className="mb-2 flex items-center gap-2 text-sm font-medium">
              <MessageSquare className="h-4 w-4" /> Conversation
            </div>
            <div className="max-h-56 space-y-3 overflow-y-auto pr-1">
              {(t.comments?.length ?? 0) === 0 ? (
                <p className="py-4 text-center text-sm text-muted-foreground">
                  No comments yet.
                </p>
              ) : (
                t.comments!.map((c) => (
                  <div key={c.id} className="flex gap-3">
                    <Avatar name={c.authorName} className="h-8 w-8 text-xs" />
                    <div className="min-w-0 flex-1 rounded-lg bg-muted/50 p-3">
                      <div className="flex items-center justify-between">
                        <span className="text-sm font-medium">{c.authorName}</span>
                        <span className="text-[11px] text-muted-foreground">
                          {dayjs(c.createdAt).format("DD MMM, h:mm A")}
                        </span>
                      </div>
                      <p className="mt-0.5 text-sm">{c.comment}</p>
                    </div>
                  </div>
                ))
              )}
            </div>

            {canRespond && (
              <div className="mt-3 flex gap-2">
                <Input
                  placeholder="Write a reply…"
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" && comment.trim()) addComment.mutate();
                  }}
                />
                <Button
                  disabled={!comment.trim() || addComment.isPending}
                  onClick={() => addComment.mutate()}
                >
                  {addComment.isPending ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <Send className="h-4 w-4" />
                  )}
                </Button>
              </div>
            )}
          </div>
        </>
      )}
      <PhotoLightbox src={lightbox} onClose={() => setLightbox(null)} />
    </Dialog>
  );
}
